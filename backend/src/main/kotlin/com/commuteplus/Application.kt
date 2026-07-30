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

private val appLog = LoggerFactory.getLogger("CommutePlusApp")

fun main() {
    // Our API defaults to 9090 to avoid clashing with the OTP server (which uses 8080).
    val port = System.getenv("PORT")?.toIntOrNull() ?: 9090
    val dataDir = File(System.getenv("OTP_DATA_DIR") ?: "data")

    appLog.info("Starting Commute+ backend on port $port")
    appLog.info("Data directory: ${dataDir.absolutePath}")

    // --- Initialize routing services (real data, not mocks) ---

    // 1. GraphHopper — real road distances from OSM
    val osmFile = dataDir.listFiles { f -> f.name.endsWith(".osm.pbf") }?.firstOrNull()
    val roadDistanceService = RoadDistanceService(
        osmFile ?: File(dataDir, "bangalore.osm.pbf")
    )

    // Shared HTTP client for outbound calls (OTP + Photon)
    val httpClient = HttpClient(CIO)

    // 2. OpenTripPlanner — real transit routing, called over HTTP (OTP runs as a separate process)
    val otpUrl = System.getenv("OTP_GRAPHQL_URL")
        ?: "http://localhost:8080/otp/routers/default/index/graphql"
    val otpRouter = OtpRouterService(otpGraphQlUrl = otpUrl, httpClient = httpClient)

    // 3. Photon geocoder — real OSM-based place search
    val photonUrl = System.getenv("PHOTON_URL") ?: "https://photon.komoot.io"
    val geocoder = PhotonGeocoder(baseUrl = photonUrl, httpClient = httpClient)

    // 4. Bangalore city provider — wires routing + fares together
    val bangaloreProvider = BangaloreTransitProvider(otpRouter, roadDistanceService)

    // Initialize GraphHopper (builds/loads the road graph — takes time on first run).
    // OTP routing is remote (HTTP), so it needs no local init here.
    try {
        roadDistanceService.initialize()
        appLog.info("GraphHopper road routing initialized successfully.")
    } catch (e: IllegalArgumentException) {
        appLog.error("OSM DATA FILE MISSING: ${e.message}")
        appLog.error("Auto/bike/cab distance estimates need the Bangalore .osm.pbf. See docs/SETUP.md.")
        // Start anyway in degraded mode: transit (OTP) may still work; direct-mode estimates won't.
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
                appLog.error("Unhandled error: ${cause.message}", cause)
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
