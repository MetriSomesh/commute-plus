package com.commuteplus

import com.commuteplus.api.ErrorResponse
import com.commuteplus.api.journeyRoutes
import com.commuteplus.city.bangalore.BangaloreTransitProvider
import com.commuteplus.geocoding.PhotonGeocoder
import com.commuteplus.routing.OtpRouterService
import com.commuteplus.routing.RoadDistanceService
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.File

private val log = LoggerFactory.getLogger("Application")

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    val dataDir = File(System.getenv("OTP_DATA_DIR") ?: "data")

    log.info("Starting Commute+ backend on port $port")
    log.info("Data directory: ${dataDir.absolutePath}")

    // --- Initialize routing services (real data, not mocks) ---

    // 1. GraphHopper — real road distances from OSM
    val osmFile = dataDir.listFiles { f -> f.name.endsWith(".osm.pbf") }?.firstOrNull()
    val roadDistanceService = RoadDistanceService(
        osmFile ?: File(dataDir, "bangalore.osm.pbf")
    )

    // 2. OpenTripPlanner — real transit routing from GTFS + OSM
    val otpRouter = OtpRouterService(dataDir)

    // 3. Photon geocoder — real OSM-based place search
    val photonUrl = System.getenv("PHOTON_URL") ?: "https://photon.komoot.io"
    val httpClient = HttpClient(CIO)
    val geocoder = PhotonGeocoder(baseUrl = photonUrl, httpClient = httpClient)

    // 4. Bangalore city provider — wires routing + fares together
    val bangaloreProvider = BangaloreTransitProvider(otpRouter, roadDistanceService)

    // Initialize services (this builds/loads the routing graph — takes time on first run)
    try {
        roadDistanceService.initialize()
        otpRouter.initialize()
        log.info("All routing services initialized successfully.")
    } catch (e: IllegalArgumentException) {
        log.error("DATA FILES MISSING: ${e.message}")
        log.error("The backend cannot start without real data. See docs/PLAN.md §12 for required files.")
        log.error("Place the files in: ${dataDir.absolutePath}")
        // Start the server anyway in "degraded mode" — transit routing won't work but the API
        // will return useful error messages. Direct mode estimates require only GraphHopper.
    }

    // --- Start Ktor server ---
    embeddedServer(Netty, port = port) {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                encodeDefaults = true
                ignoreUnknownKeys = true
            })
        }

        install(CORS) {
            anyHost() // Dev convenience; restrict in production
            allowHeader(HttpHeaders.ContentType)
            allowMethod(HttpMethod.Post)
        }

        install(StatusPages) {
            exception<Throwable> { call, cause ->
                log.error("Unhandled error: ${cause.message}", cause)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse("Internal server error", cause.message)
                )
            }
        }

        routing {
            journeyRoutes(bangaloreProvider, geocoder)
        }
    }.start(wait = true)
}
