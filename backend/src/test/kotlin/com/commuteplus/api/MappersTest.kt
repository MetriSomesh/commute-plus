package com.commuteplus.api

import com.commuteplus.domain.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

/**
 * Tests that domain → DTO mapping is correct.
 * Verifies the contract between backend and Android client.
 */
class MappersTest {

    @Test
    fun `Place maps to PlaceDto with correct coordinates`() {
        val place = Place(
            id = "stop:123",
            name = "Adugodi Bus Stop",
            localizedNames = mapOf("kn" to "ಅಡುಗೋಡಿ ಬಸ್ ನಿಲ್ದಾಣ"),
            location = LatLng(12.9352, 77.6079),
        )

        val dto = place.toDto()
        assertEquals("stop:123", dto.id)
        assertEquals("Adugodi Bus Stop", dto.name)
        assertEquals(12.9352, dto.lat)
        assertEquals(77.6079, dto.lng)
        assertEquals("ಅಡುಗೋಡಿ ಬಸ್ ನಿಲ್ದಾಣ", dto.localizedNames["kn"])
    }

    @Test
    fun `Fare maps correctly with estimated flag`() {
        val fare = Fare(minRupees = 36.0, maxRupees = 40.0, estimated = true)
        val dto = fare.toDto()
        assertEquals(36.0, dto.minRupees)
        assertEquals(40.0, dto.maxRupees)
        assertTrue(dto.estimated)
    }

    @Test
    fun `JourneyLeg duration converts to minutes`() {
        val leg = JourneyLeg(
            mode = TravelMode.BUS,
            from = Place("a", "A", emptyMap(), LatLng(12.93, 77.60)),
            to = Place("b", "B", emptyMap(), LatLng(12.94, 77.61)),
            departure = Instant.ofEpochSecond(1000000),
            arrival = Instant.ofEpochSecond(1001500), // 25 minutes later
            duration = Duration.ofMinutes(25),
            distanceMeters = 8000,
            routeName = "356",
            numStops = 12,
            fare = Fare(25.0, 25.0, false),
        )

        val dto = leg.toDto()
        assertEquals("BUS", dto.mode)
        assertEquals(25, dto.durationMinutes)
        assertEquals(8000, dto.distanceMeters)
        assertEquals("356", dto.routeName)
        assertEquals(12, dto.numStops)
        assertNotNull(dto.fare)
        assertFalse(dto.fare!!.estimated)
    }

    @Test
    fun `Journey with null fare maps to null totalFare`() {
        val journey = Journey(
            legs = emptyList(),
            totalDuration = Duration.ofMinutes(15),
            totalWalkMeters = 200,
            transfers = 0,
            totalFare = null,
            primaryMode = TravelMode.BIKE_TAXI,
        )

        val dto = journey.toDto()
        assertNull(dto.totalFare)
        assertEquals("BIKE_TAXI", dto.primaryMode)
    }
}
