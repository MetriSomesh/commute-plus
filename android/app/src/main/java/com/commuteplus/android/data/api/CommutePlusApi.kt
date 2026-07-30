package com.commuteplus.android.data.api

import kotlinx.serialization.Serializable
import retrofit2.http.*

/**
 * Retrofit interface to the Commute+ backend.
 * All data returned is REAL — sourced from GTFS + OSM + government fare rules.
 */
interface CommutePlusApi {

    @GET("/api/v1/health")
    suspend fun health(): Map<String, String>

    @GET("/api/v1/search")
    suspend fun searchPlaces(
        @Query("q") query: String,
        @Query("locale") locale: String = "en",
        @Query("limit") limit: Int = 8,
    ): PlaceSearchResponse

    @POST("/api/v1/plan")
    suspend fun planJourney(@Body request: JourneyPlanRequest): JourneyPlanResponse
}

// --- Request/Response DTOs (mirror backend API models) ---

@Serializable
data class JourneyPlanRequest(
    val originLat: Double,
    val originLng: Double,
    val destinationLat: Double,
    val destinationLng: Double,
    val departAtEpochSec: Long? = null,
    val locale: String = "en",
)

@Serializable
data class JourneyPlanResponse(
    val origin: PlaceDto,
    val destination: PlaceDto,
    val journeys: List<JourneyDto>,
    val deepLinks: Map<String, String>,
)

@Serializable
data class JourneyDto(
    val legs: List<JourneyLegDto>,
    val totalDurationMinutes: Int,
    val totalWalkMeters: Int,
    val transfers: Int,
    val totalFare: FareDto?,
    val primaryMode: String,
)

@Serializable
data class JourneyLegDto(
    val mode: String,
    val from: PlaceDto,
    val to: PlaceDto,
    val departureEpochSec: Long?,
    val arrivalEpochSec: Long?,
    val durationMinutes: Int,
    val distanceMeters: Int,
    val routeName: String? = null,
    val headsign: String? = null,
    val numStops: Int? = null,
    val fare: FareDto? = null,
)

@Serializable
data class PlaceDto(
    val id: String,
    val name: String,
    val localizedNames: Map<String, String> = emptyMap(),
    val lat: Double,
    val lng: Double,
)

@Serializable
data class FareDto(
    val minRupees: Double,
    val maxRupees: Double,
    val estimated: Boolean,
)

@Serializable
data class PlaceSearchResponse(
    val places: List<PlaceDto>,
)
