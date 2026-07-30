package com.commuteplus.android.data.session

import com.commuteplus.android.data.api.JourneyDto
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory holder for the currently displayed journey results.
 *
 * Why this exists: the results screen and the detail screen are separate navigation
 * destinations with separate ViewModels. Rather than re-planning the journey on the detail
 * screen (a wasted network call) or fragile cross-destination ViewModel scoping, both screens
 * read the current result set from this singleton.
 *
 * The results ViewModel writes here whenever the sorted list changes; the detail screen reads
 * a journey by index. Lifetime is the app process — cleared implicitly on a new plan.
 */
@Singleton
class JourneySessionStore @Inject constructor() {

    /** The sorted journeys currently shown on the results screen (index-aligned with the UI). */
    @Volatile
    var currentJourneys: List<JourneyDto> = emptyList()
        private set

    /** Aggregator deep-links (rapido, uber, ola) for the current origin/destination. */
    @Volatile
    var currentDeepLinks: Map<String, String> = emptyMap()
        private set

    /** Origin/destination coordinates for the current plan (used for deep-links). */
    @Volatile
    var originLat: Double = 0.0
        private set
    @Volatile
    var originLng: Double = 0.0
        private set
    @Volatile
    var destLat: Double = 0.0
        private set
    @Volatile
    var destLng: Double = 0.0
        private set

    fun setPlan(
        journeys: List<JourneyDto>,
        deepLinks: Map<String, String>,
        originLat: Double,
        originLng: Double,
        destLat: Double,
        destLng: Double,
    ) {
        this.currentJourneys = journeys
        this.currentDeepLinks = deepLinks
        this.originLat = originLat
        this.originLng = originLng
        this.destLat = destLat
        this.destLng = destLng
    }

    /** Update just the ordering when the user re-sorts. */
    fun updateOrder(journeys: List<JourneyDto>) {
        this.currentJourneys = journeys
    }

    fun journeyAt(index: Int): JourneyDto? = currentJourneys.getOrNull(index)
}
