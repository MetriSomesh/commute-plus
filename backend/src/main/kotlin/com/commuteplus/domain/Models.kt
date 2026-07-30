package com.commuteplus.domain

import java.time.Duration
import java.time.Instant

/** A geographic point. */
data class LatLng(val lat: Double, val lng: Double)

/** A named, searchable place (a stop, a landmark, or a geocoded address). */
data class Place(
    val id: String,
    val name: String,
    val localizedNames: Map<String, String> = emptyMap(), // locale -> name (e.g. "kn", "hi")
    val location: LatLng,
)

/** Transport modes Commute+ can plan for. */
enum class TravelMode {
    WALK,
    BUS,
    METRO,
    AUTO,        // auto-rickshaw
    BIKE_TAXI,   // Rapido-style
    CAB,
}

/**
 * A single leg of a journey. A leg is one continuous movement in one mode:
 * a walk, one bus ride, one metro ride, etc. Multi-leg journeys chain these.
 */
data class JourneyLeg(
    val mode: TravelMode,
    val from: Place,
    val to: Place,
    val departure: Instant?,        // null for on-demand modes (auto/cab/bike)
    val arrival: Instant?,
    val duration: Duration,
    val distanceMeters: Int,
    val routeName: String? = null,  // e.g. "356", "Purple Line"
    val headsign: String? = null,   // vehicle destination sign
    val numStops: Int? = null,      // intermediate stops for transit legs
    val fare: Fare? = null,         // per-leg fare when known
)

/** A complete A -> B option made of one or more legs. */
data class Journey(
    val legs: List<JourneyLeg>,
    val totalDuration: Duration,
    val totalWalkMeters: Int,
    val transfers: Int,
    val totalFare: Fare?,
    val primaryMode: TravelMode,    // dominant mode, for ranking/labeling
)

/** A fare, exact or an estimated range. Currency is INR for MVP. */
data class Fare(
    val minRupees: Double,
    val maxRupees: Double,
    val estimated: Boolean,         // true => label as "approx" in UI
) {
    val exact: Boolean get() = !estimated && minRupees == maxRupees
}

/** A planning request. */
data class JourneyRequest(
    val origin: LatLng,
    val destination: LatLng,
    val departAt: Instant? = null,  // null => "leave now"
    val locale: String = "en",
    val modes: Set<TravelMode> = TravelMode.entries.toSet(),
)
