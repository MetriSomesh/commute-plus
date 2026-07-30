package com.commuteplus.android.data.repository

import com.commuteplus.android.data.api.*
import com.commuteplus.android.data.local.CachedJourneyDao
import com.commuteplus.android.data.local.CachedJourneyEntity
import com.commuteplus.android.data.local.RecentSearchDao
import com.commuteplus.android.data.local.RecentSearchEntity
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for journey data.
 * Strategy: network-first, cached-fallback for offline.
 *
 * - On success: returns fresh data AND caches it locally.
 * - On failure (no network): falls back to cached result if available.
 * - Recent searches are persisted for the search screen.
 */
@Singleton
class JourneyRepository @Inject constructor(
    private val api: CommutePlusApi,
    private val cachedJourneyDao: CachedJourneyDao,
    private val recentSearchDao: RecentSearchDao,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

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
     * Plan a journey from origin to destination.
     * Network-first: tries API, caches on success. Falls back to cache on failure.
     */
    suspend fun planJourney(
        originLat: Double,
        originLng: Double,
        destLat: Double,
        destLng: Double,
        departAtEpochSec: Long? = null,
        locale: String = "en",
    ): Result<JourneyPlanResponse> {
        // Try network first
        try {
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

            // Cache the successful response
            cacheResponse(originLat, originLng, destLat, destLng, response)

            return Result.success(response)
        } catch (e: Exception) {
            // Network failed — try cache
            val cached = cachedJourneyDao.findCached(originLat, originLng, destLat, destLng)
            if (cached != null) {
                val cachedResponse = json.decodeFromString<JourneyPlanResponse>(cached.responseJson)
                return Result.success(cachedResponse)
            }
            return Result.failure(e)
        }
    }

    /**
     * Save a search to recent history.
     */
    suspend fun saveRecentSearch(
        originName: String,
        originLat: Double,
        originLng: Double,
        destName: String,
        destLat: Double,
        destLng: Double,
    ) {
        recentSearchDao.insert(
            RecentSearchEntity(
                originName = originName,
                originLat = originLat,
                originLng = originLng,
                destinationName = destName,
                destinationLat = destLat,
                destinationLng = destLng,
                timestamp = System.currentTimeMillis(),
            )
        )
        recentSearchDao.trimOld()
    }

    /**
     * Get recent searches for the search screen shortcuts.
     */
    suspend fun getRecentSearches(): List<RecentSearchEntity> {
        return recentSearchDao.getRecent()
    }

    private suspend fun cacheResponse(
        originLat: Double,
        originLng: Double,
        destLat: Double,
        destLng: Double,
        response: JourneyPlanResponse,
    ) {
        val responseJson = json.encodeToString(response)
        cachedJourneyDao.insert(
            CachedJourneyEntity(
                originLat = originLat,
                originLng = originLng,
                destinationLat = destLat,
                destinationLng = destLng,
                responseJson = responseJson,
                cachedAt = System.currentTimeMillis(),
            )
        )
        // Clean cache older than 24 hours
        val oneDayAgo = System.currentTimeMillis() - 24 * 60 * 60 * 1000
        cachedJourneyDao.deleteOlderThan(oneDayAgo)
    }
}
