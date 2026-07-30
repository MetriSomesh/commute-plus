package com.commuteplus.fare

import com.commuteplus.domain.Fare

/**
 * Cab (Ola/Uber) fare for Bengaluru.
 *
 * IMPORTANT: There is NO publicly available pricing API from Ola or Uber, and cab pricing is
 * dynamic (surge, demand-based). We do NOT fabricate a price.
 *
 * This service only provides deep-link URLs to the aggregator apps.
 * The fare field is null / absent for this mode in API responses.
 *
 * If an official API becomes accessible in the future, add the computation here.
 */
object BangaloreCabFare {

    /**
     * Returns null — no authoritative price source exists.
     * The app must deep-link to the aggregator for the real price.
     */
    fun compute(distanceMeters: Int): Fare? = null

    /** Deep-link into Uber app for this origin/destination. */
    fun uberDeepLink(originLat: Double, originLng: Double, destLat: Double, destLng: Double): String {
        return "https://m.uber.com/ul/?action=setPickup" +
            "&pickup[latitude]=$originLat&pickup[longitude]=$originLng" +
            "&dropoff[latitude]=$destLat&dropoff[longitude]=$destLng"
    }

    /** Deep-link into Ola app for this origin/destination. */
    fun olaDeepLink(originLat: Double, originLng: Double, destLat: Double, destLng: Double): String {
        return "https://book.olacabs.com/?pickup_lat=$originLat&pickup_lng=$originLng" +
            "&drop_lat=$destLat&drop_lng=$destLng"
    }
}
