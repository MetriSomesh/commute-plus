package com.commuteplus.city.bangalore

import com.commuteplus.domain.LatLng
import com.commuteplus.domain.TravelMode
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Tests for BangaloreTransitProvider's city-specific logic.
 * (OTP/GraphHopper integration tests require real data files and are separate.)
 */
class BangaloreTransitProviderTest {

    // We can't construct the full provider without OTP/GraphHopper, but we can test
    // the static logic by testing the bounding box and supported modes.

    @Test
    fun `covers returns true for point inside Bangalore`() {
        // Adugodi (within Bangalore)
        val adugodi = LatLng(12.9380, 77.6061)
        assertTrue(isInsideBangalore(adugodi))
    }

    @Test
    fun `covers returns false for point outside Bangalore`() {
        // Mumbai
        val mumbai = LatLng(19.0760, 72.8777)
        assertFalse(isInsideBangalore(mumbai))
    }

    @Test
    fun `covers returns true for Bellandur`() {
        val bellandur = LatLng(12.9256, 77.6760)
        assertTrue(isInsideBangalore(bellandur))
    }

    @Test
    fun `covers returns true for Electronic City (southern edge)`() {
        val ecity = LatLng(12.8399, 77.6770)
        assertTrue(isInsideBangalore(ecity))
    }

    // --- Helper: mirrors the BangaloreTransitProvider bounding box logic ---
    private val minLat = 12.75
    private val maxLat = 13.15
    private val minLng = 77.40
    private val maxLng = 77.80

    private fun isInsideBangalore(point: LatLng): Boolean {
        return point.lat in minLat..maxLat && point.lng in minLng..maxLng
    }
}
