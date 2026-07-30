package com.commuteplus.android.data.repository

import com.commuteplus.android.data.api.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for journey data.
 * Mediates between the API and the UI layer.
 *
 * All data flows through here — future offline caching (Room) will be added in this layer.
 */
@Singleton
class JourneyRepository @Inject constructor(
    private val api: CommutePlusApi,
) {

    /**
     * Search for places (autocomplete). Real OSM data via Photon.
     */
    suspend fun searchPlaces(query: String, locale: String = "en"): Result<List<PlaceDto>> {
        return try {
            val response = api.searchPlaces(query, locale)
            Result.success(response.places)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Plan a journey from origin to destination. Returns real multi-modal options.
     */
    suspend fun planJourney(
        originLat: Double,
        originLng: Double,
        destLat: Double,
        destLng: Double,
        departAtEpochSec: Long? = null,
        locale: String = "en",
    ): Result<JourneyPlanResponse> {
        return try {
            val response = api.planJourney(
                JourneyPlanRequest(
                    originLat = originLat,
                    originLng = originLng,
                    destinationLat = destLat,
                    destinationLng = destLng,
                    departAtEpochSec = departAtEpochSec,
                    locale = locale,
                )
            )
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
