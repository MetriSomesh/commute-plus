package com.commuteplus.android.util

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Deep-link utilities for opening aggregator apps (Rapido, Uber, Ola).
 *
 * For bike-taxi and cab: we don't fabricate a price (no real API source exists).
 * Instead we open the aggregator app where the user can see the real, live price and book.
 */
object DeepLinks {

    /**
     * Open a deep-link URL. Falls back to browser if the app isn't installed.
     */
    fun open(context: Context, url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            // If no handler, open in browser
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(browserIntent)
        }
    }

    fun openRapido(context: Context, originLat: Double, originLng: Double, destLat: Double, destLng: Double) {
        val url = "https://rapido.bike/book?pickup_lat=$originLat&pickup_lng=$originLng" +
            "&drop_lat=$destLat&drop_lng=$destLng"
        open(context, url)
    }

    fun openUber(context: Context, originLat: Double, originLng: Double, destLat: Double, destLng: Double) {
        val url = "https://m.uber.com/ul/?action=setPickup" +
            "&pickup[latitude]=$originLat&pickup[longitude]=$originLng" +
            "&dropoff[latitude]=$destLat&dropoff[longitude]=$destLng"
        open(context, url)
    }

    fun openOla(context: Context, originLat: Double, originLng: Double, destLat: Double, destLng: Double) {
        val url = "https://book.olacabs.com/?pickup_lat=$originLat&pickup_lng=$originLng" +
            "&drop_lat=$destLat&drop_lng=$destLng"
        open(context, url)
    }
}
