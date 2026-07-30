package com.commuteplus.routing

import com.commuteplus.domain.*
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant

/**
 * Transit routing via a real OpenTripPlanner 2 server, called over HTTP (GTFS GraphQL API).
 *
 * OTP2 is NOT embedded as a library — it runs as a separate process built from the real
 * Bangalore GTFS (BMTC + BMRCL) + OSM graph, and exposes a GraphQL endpoint. This service
 * queries that endpoint and maps OTP itineraries into our domain [Journey] model.
 *
 * This returns REAL multi-leg itineraries (no mock data) whenever the OTP server is running
 * with the Bangalore graph loaded.
 *
 * Running OTP2 (see docs/SETUP.md):
 *   java -Xmx4g -jar otp-shaded.jar --build --serve /path/to/backend/data
 *   → GraphQL endpoint: http://localhost:8080/otp/routers/default/index/graphql
 */
class OtpRouterService(
    private val otpGraphQlUrl: String,
    private val httpClient: HttpClient,
) {
    private val log = LoggerFactory.getLogger(OtpRouterService::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Plan transit journeys from origin to destination using OTP.
     * Returns real multi-leg itineraries. Empty list if OTP is unreachable or finds no route
     * (caller then falls back to direct modes only).
     */
    suspend fun planTransit(request: JourneyRequest): List<Journey> {
        val query = buildPlanQuery(request)
        return try {
            val response = httpClient.post(otpGraphQlUrl) {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(JsonObject.serializer(), buildJsonObject {
                    put("query", query)
                }))
            }
            val bodyText = response.bodyAsText()
            parseItineraries(bodyText)
        } catch (e: Exception) {
            log.warn("OTP transit query failed (is the OTP server running at $otpGraphQlUrl?): ${e.message}")
            emptyList()
        }
    }

    /**
     * Build the GTFS GraphQL `plan` query. Includes bus + all rail-like metro modes + walk.
     * Namma Metro appears as SUBWAY/RAIL depending on the feed's route_type.
     */
    private fun buildPlanQuery(request: JourneyRequest): String {
        val fromLat = request.origin.lat
        val fromLon = request.origin.lng
        val toLat = request.destination.lat
        val toLon = request.destination.lng

        // Honor the requested departure time. OTP's GTFS GraphQL API takes separate `date`
        // (YYYY-MM-DD) and `time` (HH:mm) fields, interpreted in the feed's timezone (IST here).
        // If no time was requested, fall back to "now". Without this, transit results would be
        // wrong outside the current moment (and empty at night when no service runs).
        val instant = request.departAt ?: Instant.now()
        val ist = instant.atZone(java.time.ZoneId.of("Asia/Kolkata"))
        val date = ist.toLocalDate().toString() // YYYY-MM-DD
        val time = "%02d:%02d".format(ist.hour, ist.minute)

        return """
            {
              plan(
                from: { lat: $fromLat, lon: $fromLon }
                to: { lat: $toLat, lon: $toLon }
                date: "$date"
                time: "$time"
                transportModes: [{mode: BUS}, {mode: RAIL}, {mode: SUBWAY}, {mode: TRAM}, {mode: WALK}]
                numItineraries: 5
              ) {
                itineraries {
                  duration
                  walkDistance
                  legs {
                    mode
                    distance
                    duration
                    startTime
                    endTime
                    route { shortName longName }
                    trip { tripHeadsign }
                    from { name lat lon }
                    to { name lat lon }
                    intermediateStops { name }
                    legGeometry { points }
                  }
                }
              }
            }
        """.trimIndent()
    }

    // Null-safe accessors. In kotlinx-serialization a JSON `null` is `JsonNull` (a non-null
    // JsonElement), so `?.jsonObject` does NOT guard it and would throw. These safe casts return
    // Kotlin null for both missing keys and explicit JSON nulls — essential because transit legs
    // have `route`/`trip` = null on walk legs.
    private fun JsonElement?.obj(): JsonObject? = this as? JsonObject
    private fun JsonElement?.arr(): JsonArray? = this as? JsonArray
    private fun JsonElement?.str(): String? = (this as? JsonPrimitive)?.contentOrNull
    private fun JsonElement?.dbl(): Double? = (this as? JsonPrimitive)?.doubleOrNull
    private fun JsonElement?.lng(): Long? = (this as? JsonPrimitive)?.longOrNull

    private fun parseItineraries(body: String): List<Journey> {
        val root = json.parseToJsonElement(body).obj() ?: return emptyList()
        val itineraries = root["data"].obj()
            ?.get("plan").obj()
            ?.get("itineraries").arr()
            ?: return emptyList()

        return itineraries.mapNotNull { itin ->
            val itinObj = itin.obj() ?: return@mapNotNull null
            val legsJson = itinObj["legs"].arr() ?: return@mapNotNull null

            val legs = legsJson.mapNotNull { legEl -> legEl.obj()?.let { parseLeg(it) } }
            if (legs.isEmpty()) return@mapNotNull null

            val totalDurationSec = itinObj["duration"].lng() ?: 0L
            val walkDistance = itinObj["walkDistance"].dbl() ?: 0.0
            val transitLegs = legs.count { it.mode != TravelMode.WALK }

            Journey(
                legs = legs,
                totalDuration = Duration.ofSeconds(totalDurationSec),
                totalWalkMeters = walkDistance.toInt(),
                transfers = (transitLegs - 1).coerceAtLeast(0),
                totalFare = null, // OTP feeds rarely include reliable BMTC fares; shown as unavailable
                primaryMode = legs.firstOrNull { it.mode != TravelMode.WALK }?.mode
                    ?: TravelMode.WALK,
            )
        }
    }

    private fun parseLeg(leg: JsonObject): JourneyLeg? {
        val otpMode = leg["mode"].str() ?: return null
        val mode = mapOtpMode(otpMode)

        val fromObj = leg["from"].obj()
        val toObj = leg["to"].obj() ?: return null
        val fromName = fromObj?.get("name").str() ?: "Start"
        val toName = toObj["name"].str() ?: "End"
        val fromLat = fromObj?.get("lat").dbl() ?: 0.0
        val fromLon = fromObj?.get("lon").dbl() ?: 0.0
        val toLat = toObj["lat"].dbl() ?: 0.0
        val toLon = toObj["lon"].dbl() ?: 0.0

        val startMillis = leg["startTime"].lng()
        val endMillis = leg["endTime"].lng()
        val durationSec = leg["duration"].dbl()?.toLong() ?: 0L
        val distance = leg["distance"].dbl() ?: 0.0

        val routeName = leg["route"].obj()?.get("shortName").str()
        val headsign = leg["trip"].obj()?.get("tripHeadsign").str()
        val numStops = leg["intermediateStops"].arr()?.size
        // OTP's legGeometry.points is a Google-encoded polyline (precision 5) along the real path.
        val geometry = leg["legGeometry"].obj()?.get("points").str()

        return JourneyLeg(
            mode = mode,
            from = Place(id = "otp:from", name = fromName, location = LatLng(fromLat, fromLon)),
            to = Place(id = "otp:to", name = toName, location = LatLng(toLat, toLon)),
            departure = startMillis?.let { Instant.ofEpochMilli(it) },
            arrival = endMillis?.let { Instant.ofEpochMilli(it) },
            duration = Duration.ofSeconds(durationSec),
            distanceMeters = distance.toInt(),
            routeName = routeName,
            headsign = headsign,
            numStops = numStops,
            fare = null,
            geometry = geometry,
        )
    }

    /** Map OTP transport modes to our domain modes. Metro-like rail modes collapse to METRO. */
    private fun mapOtpMode(otpMode: String): TravelMode = when (otpMode.uppercase()) {
        "BUS" -> TravelMode.BUS
        "RAIL", "SUBWAY", "TRAM", "MONORAIL", "FUNICULAR" -> TravelMode.METRO
        "WALK" -> TravelMode.WALK
        else -> TravelMode.WALK
    }
}
