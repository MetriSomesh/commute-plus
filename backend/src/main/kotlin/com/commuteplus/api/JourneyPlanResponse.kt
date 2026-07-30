package com.commuteplus.api

import kotlinx.serialization.Serializable

/**
 * API response models. These are the JSON shapes the Android client consumes.
 * Keeping them separate from domain models allows versioning/evolution without breaking the app.
 */

@Serializable
data class JourneyPlanResponse(
    val origin: PlaceDto,
    val destination: PlaceDto,
    val journeys: List<JourneyDto>,
    val deepLinks: Map<String, String>, // aggregator deep-links (rapido, uber, ola)
)

@Serializable
data class JourneyDto(
    val legs: List<JourneyLegDto>,
    val totalDurationMinutes: Int,
    val totalWalkMeters: Int,
    val transfers: Int,
    val totalFare: FareDto?,
    val primaryMode: String, // BUS, METRO, AUTO, BIKE_TAXI, CAB, WALK
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
    val routeName: String? = null,  // "356", "Purple Line"
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

@Serializable
data class JourneyPlanRequest(
    val originLat: Double,
    val originLng: Double,
    val destinationLat: Double,
    val destinationLng: Double,
    val departAtEpochSec: Long? = null, // null = "leave now"
    val locale: String = "en",
)

@Serializable
data class ErrorResponse(
    val error: String,
    val details: String? = null,
)
