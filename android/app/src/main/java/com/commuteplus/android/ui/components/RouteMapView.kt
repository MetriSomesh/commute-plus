package com.commuteplus.android.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.commuteplus.android.data.api.JourneyDto
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/**
 * MapLibre map that draws a journey's legs on a real OSM basemap.
 *
 * Each leg is drawn as a line segment between its stops in that mode's color; walk legs are dashed.
 * (Leg geometry from the backend is stop-to-stop, so segments are straight lines between stops —
 * accurate endpoints, approximate path. Full shape geometry can be added when the API exposes it.)
 *
 * Basemap: OpenFreeMap (https://openfreemap.org) — free, no API key, no signup, full street-level
 * OSM vector tiles + styles. The "positron" style is a light, neutral canvas so the colored route
 * lines stand out (per the UI taste spec). Swap to a self-hosted Protomaps/OpenFreeMap instance for
 * production scale. The previous MapLibre demo style only had low-zoom world outlines (no streets),
 * which rendered as a blank fill at city zoom.
 */

private const val STYLE_URL = "https://tiles.openfreemap.org/styles/positron"

@Composable
fun RouteMapView(
    journey: JourneyDto,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // MapLibre must be initialized before inflating a MapView.
    remember { MapLibre.getInstance(context) }

    // Create the MapView once and forward lifecycle events to it (required by MapLibre).
    val mapView = remember { MapView(context) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> mapView.onCreate(null)
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        mapView.onCreate(null)
        mapView.onStart()
        mapView.onResume()

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { mapView },
        update = { view ->
            view.getMapAsync { map ->
                map.setStyle(Style.Builder().fromUri(STYLE_URL)) { style ->
                    drawJourney(style, map, journey)
                }
            }
        },
    )
}

private fun drawJourney(style: Style, map: MapLibreMap, journey: JourneyDto) {
    val allPoints = mutableListOf<LatLng>()

    journey.legs.forEachIndexed { index, leg ->
        val start = Point.fromLngLat(leg.from.lng, leg.from.lat)
        val end = Point.fromLngLat(leg.to.lng, leg.to.lat)
        val line = LineString.fromLngLats(listOf(start, end))

        val sourceId = "leg-source-$index"
        val layerId = "leg-layer-$index"

        // Avoid duplicate source/layer ids if the style reloads.
        if (style.getSource(sourceId) == null) {
            style.addSource(GeoJsonSource(sourceId, FeatureCollection.fromFeature(Feature.fromGeometry(line))))

            val isWalk = leg.mode.equals("WALK", ignoreCase = true)
            val colorInt = modeColor(leg.mode).toArgbInt()

            val layer = LineLayer(layerId, sourceId).withProperties(
                PropertyFactory.lineColor(colorInt),
                PropertyFactory.lineWidth(if (isWalk) 3f else 5f),
                PropertyFactory.lineDasharray(if (isWalk) arrayOf(2f, 2f) else arrayOf(1f)),
            )
            style.addLayer(layer)
        }

        allPoints.add(LatLng(leg.from.lat, leg.from.lng))
        allPoints.add(LatLng(leg.to.lat, leg.to.lng))
    }

    // Fit the camera to the full route.
    if (allPoints.size >= 2) {
        val bounds = LatLngBounds.Builder().includes(allPoints).build()
        map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 80))
    } else if (allPoints.isNotEmpty()) {
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(allPoints.first(), 14.0))
    }
}

/** Convert a Compose Color to a packed ARGB int for MapLibre. */
private fun androidx.compose.ui.graphics.Color.toArgbInt(): Int {
    return android.graphics.Color.argb(
        (alpha * 255).toInt(),
        (red * 255).toInt(),
        (green * 255).toInt(),
        (blue * 255).toInt(),
    )
}
