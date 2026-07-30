package com.commuteplus.city.bangalore

import com.commuteplus.domain.*
import com.commuteplus.fare.BangaloreAutoFare
import com.commuteplus.fare.BangaloreBikeTaxiFare
import com.commuteplus.fare.BangaloreCabFare
import com.commuteplus.routing.OtpRouterService
import com.commuteplus.routing.RoadDistanceService
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant

/**
 * Transit data provider for Bengaluru (Bangalore).
 *
 * Data sources (all real, no mocks):
 * - BMTC bus routes + schedules: official GTFS feed via OTP2
 * - Namma Metro: official BMRCL GTFS feed via OTP2
 * - Auto fares: Karnataka RTA fare card (BangaloreAutoFare), computed against real road distance
 * - Bike-taxi/Cab: deep-link only (no fabricated price — no authoritative source)
 * - Walking + road distance: OSM extract via OTP2 and GraphHopper
 *
 * This class is the single per-city adapter for Bangalore. Adding another city means
 * creating a new implementation of TransitDataProvider, not modifying this one.
 */
class BangaloreTransitProvider(
    private val otpRouter: OtpRouterService,
    private val roadDistance: RoadDistanceService,
) : TransitDataProvider {

    private val log = LoggerFactory.getLogger(BangaloreTransitProvider::class.java)

    override val cityId: String = "bangalore"
    override val cityName: String = "Bengaluru"

    // Bangalore bounding box (approximate metro area)
    private val minLat = 12.75
    private val maxLat = 13.15
    private val minLng = 77.40
    private val maxLng = 77.80

    override fun covers(point: LatLng): Boolean {
        return point.lat in minLat..maxLat && point.lng in minLng..maxLng
    }

    override fun supportedModes(): Set<TravelMode> {
        return setOf(
            TravelMode.BUS,
            TravelMode.METRO,
            TravelMode.WALK,
            TravelMode.AUTO,
            TravelMode.BIKE_TAXI,
            TravelMode.CAB,
        )
    }

    override fun searchPlaces(query: String, locale: String, limit: Int): List<Place> {
        // Photon geocoder integration — searches against real OSM data
        // This will be wired to the Photon instance running on the same OSM extract
        log.info("searchPlaces: query='$query', locale=$locale")
        // TODO: wire to Photon once self-hosted instance is running
        return emptyList()
    }

    override fun planTransit(request: JourneyRequest): List<Journey> {
        return otpRouter.planTransit(request)
    }

    override fun estimateDirectModes(request: JourneyRequest): List<Journey> {
        val results = mutableListOf<Journey>()

        // Get real road distance from GraphHopper (OSM-based, not straight line)
        val routeResult = roadDistance.route(request.origin, request.destination)

        if (routeResult == null) {
            log.warn("No road route found between ${request.origin} and ${request.destination}")
            return emptyList()
        }

        val distMeters = routeResult.distanceMeters
        val durationSec = routeResult.durationSeconds
        val now = request.departAt ?: Instant.now()

        // --- Auto-rickshaw (real RTA fare) ---
        if (TravelMode.AUTO in request.modes) {
            val fare = BangaloreAutoFare.compute(distMeters, now)
            results.add(
                Journey(
                    legs = listOf(
                        JourneyLeg(
                            mode = TravelMode.AUTO,
                            from = Place(id = "origin", name = "Start", location = request.origin),
                            to = Place(id = "dest", name = "Destination", location = request.destination),
                            departure = now,
                            arrival = now.plusSeconds(durationSec.toLong()),
                            duration = Duration.ofSeconds(durationSec.toLong()),
                            distanceMeters = distMeters,
                            fare = fare,
                        )
                    ),
                    totalDuration = Duration.ofSeconds(durationSec.toLong()),
                    totalWalkMeters = 0,
                    transfers = 0,
                    totalFare = fare,
                    primaryMode = TravelMode.AUTO,
                )
            )
        }

        // --- Bike-taxi (no price — deep-link only) ---
        if (TravelMode.BIKE_TAXI in request.modes) {
            val bikeFare = BangaloreBikeTaxiFare.compute(distMeters)
            // Approximate bike time as ~80% of car time (weaves through traffic)
            val bikeDuration = (durationSec * 0.8).toLong()
            results.add(
                Journey(
                    legs = listOf(
                        JourneyLeg(
                            mode = TravelMode.BIKE_TAXI,
                            from = Place(id = "origin", name = "Start", location = request.origin),
                            to = Place(id = "dest", name = "Destination", location = request.destination),
                            departure = now,
                            arrival = now.plusSeconds(bikeDuration),
                            duration = Duration.ofSeconds(bikeDuration),
                            distanceMeters = distMeters,
                            fare = bikeFare, // null — no real source
                        )
                    ),
                    totalDuration = Duration.ofSeconds(bikeDuration),
                    totalWalkMeters = 0,
                    transfers = 0,
                    totalFare = bikeFare, // null
                    primaryMode = TravelMode.BIKE_TAXI,
                )
            )
        }

        // --- Cab (no price — deep-link only) ---
        if (TravelMode.CAB in request.modes) {
            val cabFare = BangaloreCabFare.compute(distMeters)
            results.add(
                Journey(
                    legs = listOf(
                        JourneyLeg(
                            mode = TravelMode.CAB,
                            from = Place(id = "origin", name = "Start", location = request.origin),
                            to = Place(id = "dest", name = "Destination", location = request.destination),
                            departure = now,
                            arrival = now.plusSeconds(durationSec.toLong()),
                            duration = Duration.ofSeconds(durationSec.toLong()),
                            distanceMeters = distMeters,
                            fare = cabFare, // null — no real source
                        )
                    ),
                    totalDuration = Duration.ofSeconds(durationSec.toLong()),
                    totalWalkMeters = 0,
                    transfers = 0,
                    totalFare = cabFare, // null
                    primaryMode = TravelMode.CAB,
                )
            )
        }

        return results
    }

    /** Generate aggregator deep-links for a given origin/destination. */
    fun getDeepLinks(origin: LatLng, destination: LatLng): Map<String, String> {
        return mapOf(
            "rapido" to BangaloreBikeTaxiFare.rapidoDeepLink(
                origin.lat, origin.lng, destination.lat, destination.lng
            ),
            "uber" to BangaloreCabFare.uberDeepLink(
                origin.lat, origin.lng, destination.lat, destination.lng
            ),
            "ola" to BangaloreCabFare.olaDeepLink(
                origin.lat, origin.lng, destination.lat, destination.lng
            ),
        )
    }
}
