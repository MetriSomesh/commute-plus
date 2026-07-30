package com.commuteplus.api

import com.commuteplus.city.bangalore.BangaloreTransitProvider
import com.commuteplus.domain.JourneyRequest
import com.commuteplus.domain.LatLng
import com.commuteplus.geocoding.PhotonGeocoder
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory
import java.time.Instant

private val routeLog = LoggerFactory.getLogger("CommutePlusRoutes")

/**
 * HTTP API routes for Commute+.
 *
 * Endpoints:
 *   POST /api/v1/plan       — Plan journeys from origin to destination (all modes)
 *   GET  /api/v1/search     — Autocomplete place search
 *   GET  /api/v1/health     — Health check
 */
fun Route.journeyRoutes(
    bangaloreProvider: BangaloreTransitProvider,
    geocoder: PhotonGeocoder,
) {

    route("/api/v1") {

        // --- Health check ---
        get("/health") {
            call.respond(mapOf("status" to "ok", "city" to bangaloreProvider.cityName))
        }

        // --- Place search / autocomplete ---
        get("/search") {
            val query = call.request.queryParameters["q"]
            val locale = call.request.queryParameters["locale"] ?: "en"
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 8
            routeLog.info("GET /search q='$query' from ${call.request.local.remoteHost}")

            if (query.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing 'q' parameter"))
                return@get
            }

            val places = geocoder.search(query, limit, locale)
            call.respond(PlaceSearchResponse(places = places.map { it.toDto() }))
        }

        // --- Journey planning ---
        post("/plan") {
            val request = try {
                call.receive<JourneyPlanRequest>()
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("Invalid request body", e.message)
                )
                return@post
            }

            val origin = LatLng(request.originLat, request.originLng)
            val destination = LatLng(request.destinationLat, request.destinationLng)

            // Validate that the points are within Bangalore
            if (!bangaloreProvider.covers(origin) || !bangaloreProvider.covers(destination)) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(
                        "Points outside Bangalore coverage area",
                        "Currently only Bengaluru is supported. Both origin and destination must be within the city."
                    )
                )
                return@post
            }

            val journeyRequest = JourneyRequest(
                origin = origin,
                destination = destination,
                departAt = request.departAtEpochSec?.let { Instant.ofEpochSecond(it) },
                locale = request.locale,
            )

            // Plan transit (bus, metro, walk) via OTP
            val transitJourneys = bangaloreProvider.planTransit(journeyRequest)

            // Estimate direct modes (auto, bike-taxi, cab) via real road distance
            val directJourneys = bangaloreProvider.estimateDirectModes(journeyRequest)

            // Merge, sort by total duration
            val allJourneys = (transitJourneys + directJourneys).sortedBy { it.totalDuration }

            // Get deep-links for aggregator apps
            val deepLinks = bangaloreProvider.getDeepLinks(origin, destination)

            // Resolve place names for origin/destination via reverse geocoding
            val originPlace = geocoder.reverse(origin.lat, origin.lng)
            val destPlace = geocoder.reverse(destination.lat, destination.lng)

            call.respond(
                JourneyPlanResponse(
                    origin = (originPlace ?: com.commuteplus.domain.Place(
                        "origin", "Origin", emptyMap(), origin
                    )).toDto(),
                    destination = (destPlace ?: com.commuteplus.domain.Place(
                        "dest", "Destination", emptyMap(), destination
                    )).toDto(),
                    journeys = allJourneys.map { it.toDto() },
                    deepLinks = deepLinks,
                )
            )
        }
    }
}
