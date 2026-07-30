package com.commuteplus.routing

import com.commuteplus.domain.LatLng
import com.graphhopper.GHRequest
import com.graphhopper.GraphHopper
import com.graphhopper.config.Profile
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Computes real road distance and travel time between two points using GraphHopper + OSM data.
 *
 * This is the real distance used for auto/bike/cab fare calculation — never a straight-line estimate.
 * It uses the same OSM file that OTP2 uses for walk routing.
 *
 * Required: the Bangalore .osm.pbf file must exist at [osmFile].
 */
class RoadDistanceService(private val osmFile: File) {

    private val log = LoggerFactory.getLogger(RoadDistanceService::class.java)
    private lateinit var hopper: GraphHopper

    data class RouteResult(
        val distanceMeters: Int,
        val durationSeconds: Int,
    )

    fun initialize() {
        require(osmFile.exists()) {
            "OSM file not found: ${osmFile.absolutePath}. " +
                "Download the Bangalore OSM extract and place it there."
        }

        log.info("Initializing GraphHopper with OSM: ${osmFile.name}")

        val cacheDir = File(osmFile.parentFile, "gh-cache")

        hopper = GraphHopper()
        hopper.setOSMFile(osmFile.absolutePath)
        hopper.graphHopperLocation = cacheDir.absolutePath
        hopper.setProfiles(
            Profile("car").setVehicle("car").setWeighting("fastest"),
            Profile("bike").setVehicle("bike").setWeighting("fastest"),
        )
        hopper.importOrLoad()

        log.info("GraphHopper ready. Road routing available.")
    }

    /**
     * Compute real road distance/time for a car/auto route.
     * Returns null if no route exists (e.g., points not on the road network).
     */
    fun route(origin: LatLng, destination: LatLng, profile: String = "car"): RouteResult? {
        val req = GHRequest(origin.lat, origin.lng, destination.lat, destination.lng)
            .setProfile(profile)

        val resp = hopper.route(req)

        if (resp.hasErrors()) {
            log.warn("GraphHopper routing error: ${resp.errors.firstOrNull()?.message}")
            return null
        }

        val best = resp.best
        return RouteResult(
            distanceMeters = best.distance.toInt(),
            durationSeconds = (best.time / 1000).toInt(),
        )
    }
}
