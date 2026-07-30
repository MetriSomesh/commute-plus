package com.commuteplus.fare

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Tests for the Bangalore auto-rickshaw fare calculator.
 *
 * These verify that the REAL Karnataka RTA fare card (effective 2025-08-01) is
 * correctly implemented:
 *   - Rs 36 minimum for first 2 km
 *   - Rs 18 per km beyond 2 km
 *   - 1.5x night fare (22:00–05:00)
 */
class BangaloreAutoFareTest {

    @Test
    fun `minimum fare for short trips under 2km`() {
        val fare = BangaloreAutoFare.compute(distanceMeters = 500, at = dayTime())
        assertEquals(36.0, fare.minRupees) // minimum ₹36 even for <2km
        assertTrue(fare.estimated)
    }

    @Test
    fun `minimum fare at exactly 2km`() {
        val fare = BangaloreAutoFare.compute(distanceMeters = 2000, at = dayTime())
        assertEquals(36.0, fare.minRupees)
    }

    @Test
    fun `correct fare for 5km trip`() {
        // 36 + (5 - 2) * 18 = 36 + 54 = 90
        val fare = BangaloreAutoFare.compute(distanceMeters = 5000, at = dayTime())
        assertEquals(90.0, fare.minRupees)
    }

    @Test
    fun `correct fare for 10km trip`() {
        // 36 + (10 - 2) * 18 = 36 + 144 = 180
        val fare = BangaloreAutoFare.compute(distanceMeters = 10000, at = dayTime())
        assertEquals(180.0, fare.minRupees)
    }

    @Test
    fun `night fare applies 1_5x multiplier`() {
        // 5km at night: (36 + 54) * 1.5 = 135
        val fare = BangaloreAutoFare.compute(distanceMeters = 5000, at = nightTime())
        assertEquals(135.0, fare.minRupees)
    }

    @Test
    fun `night fare at 11pm`() {
        val elevenPm = LocalDateTime.of(2025, 9, 15, 23, 0)
            .atZone(ZoneId.of("Asia/Kolkata"))
            .toInstant()
        val fare = BangaloreAutoFare.compute(distanceMeters = 2000, at = elevenPm)
        // 36 * 1.5 = 54
        assertEquals(54.0, fare.minRupees)
    }

    @Test
    fun `night fare at 4am`() {
        val fourAm = LocalDateTime.of(2025, 9, 15, 4, 0)
            .atZone(ZoneId.of("Asia/Kolkata"))
            .toInstant()
        val fare = BangaloreAutoFare.compute(distanceMeters = 2000, at = fourAm)
        // 36 * 1.5 = 54
        assertEquals(54.0, fare.minRupees)
    }

    @Test
    fun `day fare at 6am (just after night ends)`() {
        val sixAm = LocalDateTime.of(2025, 9, 15, 6, 0)
            .atZone(ZoneId.of("Asia/Kolkata"))
            .toInstant()
        val fare = BangaloreAutoFare.compute(distanceMeters = 2000, at = sixAm)
        assertEquals(36.0, fare.minRupees) // no night multiplier
    }

    @Test
    fun `max rupees includes 10 percent band for waiting time uncertainty`() {
        val fare = BangaloreAutoFare.compute(distanceMeters = 5000, at = dayTime())
        // min = 90, max = 90 * 1.1 = 99
        assertEquals(90.0, fare.minRupees)
        assertEquals(99.0, fare.maxRupees)
    }

    @Test
    fun `zero distance returns minimum fare`() {
        val fare = BangaloreAutoFare.compute(distanceMeters = 0, at = dayTime())
        assertEquals(36.0, fare.minRupees)
    }

    @Test
    fun `negative distance throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            BangaloreAutoFare.compute(distanceMeters = -100)
        }
    }

    // --- Helpers ---

    private fun dayTime(): Instant {
        return LocalDateTime.of(2025, 9, 15, 14, 0) // 2 PM
            .atZone(ZoneId.of("Asia/Kolkata"))
            .toInstant()
    }

    private fun nightTime(): Instant {
        return LocalDateTime.of(2025, 9, 15, 23, 30) // 11:30 PM
            .atZone(ZoneId.of("Asia/Kolkata"))
            .toInstant()
    }
}
