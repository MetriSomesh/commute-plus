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
        // OTP expects epoch seconds converted to date/time; omit to use "now".
        return """
            {
              plan(
                from: { lat: $fromLat, lon: $fromLon }
                to: { lat: $toLat, lon: $toLon }
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
                  }
                }
              }
            }
        """.trimIndent()
    }

    private fun parseItineraries(body: String): List<Journey> {
        val root = json.parseToJsonElement(body).jsonObject
        val itineraries = root["data"]?.jsonObject
            ?.get("plan")?.jsonObject
            ?.get("itineraries")?.jsonArray
            ?: return emptyList()

        return itineraries.mapNotNull { itin ->
            val itinObj = itin.jsonObject
            val legsJson = itinObj["legs"]?.jsonArray ?: return@mapNotNull null

            val legs = legsJson.mapNotNull { parseLeg(it.jsonObject) }
            if (legs.isEmpty()) return@mapNotNull null

            val totalDurationSec = itinObj["duration"]?.jsonPrimitive?.longOrNull ?: 0L
            val walkDistance = itinObj["walkDistance"]?.jsonPrimitive?.doubleOrNull ?: 0.0
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
        val otpMode = leg["mode"]?.jsonPrimitive?.contentOrNull ?: return null
        val mode = mapOtpMode(otpMode)

        val fromObj = leg["from"]?.jsonObject
        val toObj = leg["to"]?.jsonObject ?: return null
        val fromName = fromObj?.get("name")?.jsonPrimitive?.contentOrNull ?: "Start"
        val toName = toObj["name"]?.jsonPrimitive?.contentOrNull ?: "End"
        val fromLat = fromObj?.get("lat")?.jsonPrimitive?.doubleOrNull ?: 0.0
        val fromLon = fromObj?.get("lon")?.jsonPrimitive?.doubleOrNull ?: 0.0
        val toLat = toObj["lat"]?.jsonPrimitive?.doubleOrNull ?: 0.0
        val toLon = toObj["lon"]?.jsonPrimitive?.doubleOrNull ?: 0.0

        val startMillis = leg["startTime"]?.jsonPrimitive?.longOrNull
        val endMillis = leg["endTime"]?.jsonPrimitive?.longOrNull
        val durationSec = leg["duration"]?.jsonPrimitive?.doubleOrNull?.toLong() ?: 0L
        val distance = leg["distance"]?.jsonPrimitive?.doubleOrNull ?: 0.0

        val routeName = leg["route"]?.jsonObject?.get("shortName")?.jsonPrimitive?.contentOrNull
        val headsign = leg["trip"]?.jsonObject?.get("tripHeadsign")?.jsonPrimitive?.contentOrNull
        val numStops = leg["intermediateStops"]?.jsonArray?.size

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
