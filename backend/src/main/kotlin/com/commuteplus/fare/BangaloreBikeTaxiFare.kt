package com.commuteplus.fare

import com.commuteplus.domain.Fare

/**
 * Bike-taxi (Rapido-style) fare estimate for Bengaluru.
 *
 * IMPORTANT: There is NO government-regulated fare card for bike-taxi. Rapido/Uber Moto pricing
 * is proprietary and dynamic. We do NOT fabricate a price.
 *
 * This service only provides the deep-link URL to the aggregator app.
 * The fare field is null / absent for this mode in API responses.
 *
 * If a regulatory fare card is published in the future, add the computation here.
 */
object BangaloreBikeTaxiFare {

    /**
     * Returns null — no authoritative price source exists.
     * The app must deep-link to the aggregator for the real price.
     */
    fun compute(distanceMeters: Int): Fare? = null

    /** Deep-link into Rapido app for this origin/destination. */
    fun rapidoDeepLink(originLat: Double, originLng: Double, destLat: Double, destLng: Double): String {
        return "https://rapido.bike/book?pickup_lat=$originLat&pickup_lng=$originLng" +
            "&drop_lat=$destLat&drop_lng=$destLng"
    }
}
