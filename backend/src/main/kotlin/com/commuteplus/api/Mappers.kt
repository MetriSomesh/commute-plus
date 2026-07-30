package com.commuteplus.api

import com.commuteplus.domain.*

/**
 * Maps domain objects to API DTOs.
 * This is the boundary between the domain layer and the HTTP layer.
 */

fun Place.toDto() = PlaceDto(
    id = id,
    name = name,
    localizedNames = localizedNames,
    lat = location.lat,
    lng = location.lng,
)

fun Fare.toDto() = FareDto(
    minRupees = minRupees,
    maxRupees = maxRupees,
    estimated = estimated,
)

fun JourneyLeg.toDto() = JourneyLegDto(
    mode = mode.name,
    from = from.toDto(),
    to = to.toDto(),
    departureEpochSec = departure?.epochSecond,
    arrivalEpochSec = arrival?.epochSecond,
    durationMinutes = duration.toMinutes().toInt(),
    distanceMeters = distanceMeters,
    routeName = routeName,
    headsign = headsign,
    numStops = numStops,
    fare = fare?.toDto(),
)

fun Journey.toDto() = JourneyDto(
    legs = legs.map { it.toDto() },
    totalDurationMinutes = totalDuration.toMinutes().toInt(),
    totalWalkMeters = totalWalkMeters,
    transfers = transfers,
    totalFare = totalFare?.toDto(),
    primaryMode = primaryMode.name,
)
