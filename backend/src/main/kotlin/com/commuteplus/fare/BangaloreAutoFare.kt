package com.commuteplus.fare

import com.commuteplus.domain.Fare
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * Auto-rickshaw fare for Bengaluru, computed from the official Karnataka RTA fare card.
 *
 * These are REAL government-regulated rates, not invented numbers. The distance fed in must come
 * from a real routing source (OSM road graph via the routing engine) — never a guessed distance.
 *
 * Source: Bangalore Urban RTA revised auto fare, effective 2025-08-01.
 *   - Minimum fare: Rs 36 for the first 2 km
 *   - Rs 18 per km beyond 2 km
 *   - Night fare (22:00–05:00): 1.5x
 * Refs:
 *   https://www.thehindu.com/news/national/karnataka/auto-fare-hike-effective-from-today-bengaluru-autorickshaws-must-upgrade-fare-meters-by-oct-31/article69879937.ece
 *   https://www.moneycontrol.com/news/india/bengaluru-auto-rickshaw-fare-hiked-minimum-fare-now-rs-36-13277777.html
 *
 * Update this object (and EFFECTIVE_FROM) whenever the RTA revises fares.
 */
object BangaloreAutoFare {

    const val EFFECTIVE_FROM = "2025-08-01"

    private const val MIN_FARE_RUPEES = 36.0
    private const val BASE_DISTANCE_KM = 2.0
    private const val PER_KM_RUPEES = 18.0
    private const val NIGHT_MULTIPLIER = 1.5

    private val NIGHT_START = LocalTime.of(22, 0) // 22:00 inclusive
    private val NIGHT_END = LocalTime.of(5, 0)    // 05:00 exclusive
    private val ZONE = ZoneId.of("Asia/Kolkata")

    /**
     * Compute the metered auto fare for a given real road distance.
     *
     * @param distanceMeters real road distance from the routing engine
     * @param at time of travel (defaults to now); used to apply the night multiplier
     * @return an exact metered fare. Marked estimated=true only because real trips vary with
     *         waiting time and traffic, which the meter charges but we cannot know in advance.
     */
    fun compute(distanceMeters: Int, at: Instant = Instant.now()): Fare {
        require(distanceMeters >= 0) { "distanceMeters must be non-negative" }

        val km = distanceMeters / 1000.0
        val meteredBeforeNight = if (km <= BASE_DISTANCE_KM) {
            MIN_FARE_RUPEES
        } else {
            MIN_FARE_RUPEES + (km - BASE_DISTANCE_KM) * PER_KM_RUPEES
        }

        val fare = if (isNight(at)) meteredBeforeNight * NIGHT_MULTIPLIER else meteredBeforeNight
        val rounded = Math.round(fare).toDouble()

        // Metered fare is deterministic for distance, but real trips add waiting-time charges we
        // cannot predict. Present a small upward band and label as estimate rather than a firm quote.
        return Fare(minRupees = rounded, maxRupees = Math.round(rounded * 1.1).toDouble(), estimated = true)
    }

    private fun isNight(at: Instant): Boolean {
        val t = at.atZone(ZONE).toLocalTime()
        // Night window wraps midnight: 22:00..24:00 and 00:00..05:00
        return t >= NIGHT_START || t < NIGHT_END
    }
}
