package com.commuteplus.routing

import com.commuteplus.domain.LatLng
import com.graphhopper.GHRequest
import com.graphhopper.GraphHopper
import com.graphhopper.config.Profile
import com.graphhopper.util.CustomModel
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
        // Google-encoded polyline (precision 5) of the road path, for drawing on the map.
        val geometry: String?,
    )

    fun initialize() {
        require(osmFile.exists()) {
            "OSM file not found: ${osmFile.absolutePath}. " +
                "Download the Bangalore OSM extract and place it there."
        }

        log.info("Initializing GraphHopper with OSM: ${osmFile.name}")

        val cacheDir = File(osmFile.parentFile, "gh-cache")

        // GraphHopper 8 removed the "fastest" weighting; profiles now use "custom" with a
        // CustomModel. An empty CustomModel yields fastest-by-default behavior from the vehicle's
        // speeds, which is exactly what we want for distance/time estimates.
        hopper = GraphHopper()
        hopper.setOSMFile(osmFile.absolutePath)
        hopper.graphHopperLocation = cacheDir.absolutePath
        hopper.setProfiles(
            Profile("car").setVehicle("car").setWeighting("custom").setCustomModel(CustomModel()),
            Profile("bike").setVehicle("bike").setWeighting("custom").setCustomModel(CustomModel()),
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
            geometry = encodePolyline(best.points),
        )
    }

    /**
     * Encode a GraphHopper PointList into a Google-encoded polyline (precision 5), matching the
     * format OTP uses so the client can decode both with one algorithm.
     */
    private fun encodePolyline(points: com.graphhopper.util.PointList): String {
        val sb = StringBuilder()
        var prevLat = 0L
        var prevLng = 0L
        for (i in 0 until points.size()) {
            val lat = Math.round(points.getLat(i) * 1e5)
            val lng = Math.round(points.getLon(i) * 1e5)
            encodeSignedNumber(lat - prevLat, sb)
            encodeSignedNumber(lng - prevLng, sb)
            prevLat = lat
            prevLng = lng
        }
        return sb.toString()
    }

    private fun encodeSignedNumber(num: Long, sb: StringBuilder) {
        var sgn = num shl 1
        if (num < 0) sgn = sgn.inv()
        while (sgn >= 0x20) {
            sb.append(((0x20 or (sgn and 0x1f).toInt()) + 63).toChar())
            sgn = sgn shr 5
        }
        sb.append((sgn.toInt() + 63).toChar())
    }
}
