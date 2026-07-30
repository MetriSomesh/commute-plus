package com.commuteplus.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.commuteplus.android.data.api.FareDto
import com.commuteplus.android.ui.theme.*

/**
 * Returns the semantic color for a transport mode string.
 * Colors are defined once in the theme and used everywhere (list, detail, map).
 */
fun modeColor(mode: String): Color {
    return when (mode.uppercase()) {
        "BUS" -> ModeBus
        "METRO" -> ModeMetroPurple // Default to purple line; could use route info to pick green
        "AUTO" -> ModeAuto
        "BIKE_TAXI" -> ModeBikeTaxi
        "CAB" -> ModeCab
        "WALK" -> ModeWalk
        else -> ModeWalk
    }
}

/**
 * Returns an icon for the transport mode.
 */
fun modeIcon(mode: String): ImageVector {
    return when (mode.uppercase()) {
        "BUS" -> Icons.Filled.DirectionsBus
        "METRO" -> Icons.Filled.Train
        "AUTO" -> Icons.Filled.ElectricRickshaw
        "BIKE_TAXI" -> Icons.Filled.TwoWheeler
        "CAB" -> Icons.Filled.LocalTaxi
        "WALK" -> Icons.Filled.DirectionsWalk
        else -> Icons.Filled.DirectionsWalk
    }
}

/**
 * Compact mode indicator — colored icon chip.
 * Used in journey cards and detail headers.
 */
@Composable
fun ModeIndicator(mode: String) {
    val color = modeColor(mode)
    val icon = modeIcon(mode)

    Box(
        modifier = Modifier
            .size(32.dp)
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = mode.lowercase(),
            tint = color,
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * Format a fare for display.
 *
 * Rules (from taste spec + no-mock constraint):
 * - Real fares: "₹36" or "₹36–40 approx"
 * - No fare data (bike-taxi/cab): "Check in app"
 * - Never an invented number
 */
fun formatFare(fare: FareDto?): String {
    if (fare == null) return "Check in app"

    return if (fare.minRupees == fare.maxRupees) {
        if (fare.estimated) "~₹${fare.minRupees.toInt()}" else "₹${fare.minRupees.toInt()}"
    } else {
        "₹${fare.minRupees.toInt()}–${fare.maxRupees.toInt()} approx"
    }
}
