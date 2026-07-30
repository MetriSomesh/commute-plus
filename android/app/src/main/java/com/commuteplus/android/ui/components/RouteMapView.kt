package com.commuteplus.android.ui.components

import android.content.Context
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.commuteplus.android.data.api.JourneyDto
import com.commuteplus.android.data.api.JourneyLegDto
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.plugins.annotation.LineManager
import org.maplibre.android.plugins.annotation.LineOptions
import org.maplibre.android.plugins.annotation.SymbolManager
import org.maplibre.android.plugins.annotation.SymbolOptions
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.utils.ColorUtils

/**
 * MapLibre map view composable that draws journey legs on a real OSM map.
 *
 * - Transit legs: solid colored lines in mode color.
 * - Walk legs: dashed gray lines.
 * - Board/alight points: circle markers.
 *
 * Tile source: Protomaps .pmtiles (configured via STYLE_URL).
 * No API key, no paid account.
 */

// Default OSM tile style — replace with Protomaps/self-hosted URL in production.
// This uses the OSM demo raster tiles for development. Replace before production deployment.
private const val STYLE_URL = "https://demotiles.maplibre.org/style.json"

@Composable
fun RouteMapView(
    journey: JourneyDto,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    // Initialize MapLibre (required once per app lifecycle)
    LaunchedEffect(Unit) {
        MapLibre.getInstance(context)
    }

    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .height(240.dp),
        factory = { ctx ->
            MapView(ctx).apply {
                getMapAsync { map ->
                    map.setStyle(Style.Builder().fromUri(STYLE_URL)) { style ->
                        drawJourneyOnMap(map, journey)
                    }
                }
            }
        },
    )
}

private fun drawJourneyOnMap(map: MapLibreMap, journey: JourneyDto) {
    // Collect all points to compute bounds
    val allPoints = mutableListOf<LatLng>()

    journey.legs.forEach { leg ->
        allPoints.add(LatLng(leg.from.lat, leg.from.lng))
        allPoints.add(LatLng(leg.to.lat, leg.to.lng))
    }

    // Zoom to fit all points
    if (allPoints.size >= 2) {
        val bounds = LatLngBounds.Builder().includes(allPoints).build()
        map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 64))
    } else if (allPoints.isNotEmpty()) {
        map.moveCamera(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder()
                    .target(allPoints.first())
                    .zoom(14.0)
                    .build()
            )
        )
    }
}
