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
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Path
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
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

// The highlighted route line + stop rings use this blue.
private val ROUTE_BLUE = AndroidColor.parseColor("#1A73E8")

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
    val legPointLists = mutableListOf<List<Point>>()

    // 1) Draw each leg: a white casing underneath + the mode-colored line on top, so the route
    //    stands out clearly against the basemap.
    journey.legs.forEachIndexed { index, leg ->
        val points: List<Point> = decodePolyline(leg.geometry).ifEmpty {
            listOf(
                Point.fromLngLat(leg.from.lng, leg.from.lat),
                Point.fromLngLat(leg.to.lng, leg.to.lat),
            )
        }
        legPointLists.add(points)
        val line = LineString.fromLngLats(points)
        val isWalk = leg.mode.equals("WALK", ignoreCase = true)

        val sourceId = "leg-source-$index"
        style.addSource(GeoJsonSource(sourceId, FeatureCollection.fromFeature(Feature.fromGeometry(line))))

        // Thin white casing under transit legs so the blue route stands out on the basemap.
        if (!isWalk) {
            style.addLayer(
                LineLayer("leg-casing-$index", sourceId).withProperties(
                    PropertyFactory.lineColor(AndroidColor.WHITE),
                    PropertyFactory.lineWidth(9f),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                )
            )
        }
        // The highlighted route line itself is blue.
        style.addLayer(
            LineLayer("leg-line-$index", sourceId).withProperties(
                PropertyFactory.lineColor(ROUTE_BLUE),
                PropertyFactory.lineWidth(if (isWalk) 4f else 6f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                PropertyFactory.lineDasharray(if (isWalk) arrayOf(1.5f, 1.5f) else arrayOf(1f)),
            )
        )

        points.forEach { allPoints.add(LatLng(it.latitude(), it.longitude())) }
    }

    // 2) Stop dots at each transit board/alight point (white fill, mode-colored ring).
    val stopFeatures = mutableListOf<Feature>()
    journey.legs.forEach { leg ->
        if (!leg.mode.equals("WALK", ignoreCase = true)) {
            stopFeatures.add(pointFeature(leg.from.lng, leg.from.lat))
            stopFeatures.add(pointFeature(leg.to.lng, leg.to.lat))
        }
    }
    if (stopFeatures.isNotEmpty()) {
        style.addSource(GeoJsonSource("stops-source", FeatureCollection.fromFeatures(stopFeatures)))
        style.addLayer(
            CircleLayer("stops-layer", "stops-source").withProperties(
                PropertyFactory.circleRadius(5f),
                PropertyFactory.circleColor(AndroidColor.WHITE),
                PropertyFactory.circleStrokeColor(ROUTE_BLUE),
                PropertyFactory.circleStrokeWidth(3f),
            )
        )
    }

    // 3) Origin + destination pins (from = green, to = red), anchored at the true endpoints.
    val originPt = legPointLists.firstOrNull()?.firstOrNull()
    val destPt = legPointLists.lastOrNull()?.lastOrNull()
    style.addImage("pin-origin", pinBitmap(AndroidColor.parseColor("#0D7377")))
    style.addImage("pin-dest", pinBitmap(AndroidColor.parseColor("#C62828")))
    if (originPt != null) {
        style.addSource(GeoJsonSource("origin-source", FeatureCollection.fromFeature(Feature.fromGeometry(originPt))))
        style.addLayer(pinLayer("origin-layer", "origin-source", "pin-origin"))
    }
    if (destPt != null) {
        style.addSource(GeoJsonSource("dest-source", FeatureCollection.fromFeature(Feature.fromGeometry(destPt))))
        style.addLayer(pinLayer("dest-layer", "dest-source", "pin-dest"))
    }

    // 4) Fit the camera to the whole route (with padding for the pins).
    if (allPoints.size >= 2) {
        val bounds = LatLngBounds.Builder().includes(allPoints).build()
        map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 110))
    } else if (allPoints.isNotEmpty()) {
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(allPoints.first(), 14.0))
    }
}

private fun pointFeature(lng: Double, lat: Double): Feature =
    Feature.fromGeometry(Point.fromLngLat(lng, lat))

private fun pinLayer(id: String, sourceId: String, imageId: String): SymbolLayer =
    SymbolLayer(id, sourceId).withProperties(
        PropertyFactory.iconImage(imageId),
        PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM),
        PropertyFactory.iconAllowOverlap(true),
        PropertyFactory.iconIgnorePlacement(true),
    )

/** Draw a simple teardrop map pin of the given color with a white inner dot. */
private fun pinBitmap(color: Int): Bitmap {
    val w = 54
    val h = 72
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val cx = w / 2f
    val cy = w / 2f
    val r = w / 2f - 4f

    // White outline (draw the pin slightly larger in white first).
    paint.color = AndroidColor.WHITE
    canvas.drawCircle(cx, cy, r + 2f, paint)
    val outlinePath = Path().apply {
        moveTo(cx - (r + 2f) * 0.62f, cy + (r + 2f) * 0.45f)
        lineTo(cx + (r + 2f) * 0.62f, cy + (r + 2f) * 0.45f)
        lineTo(cx, h.toFloat())
        close()
    }
    canvas.drawPath(outlinePath, paint)

    // Colored body.
    paint.color = color
    canvas.drawCircle(cx, cy, r, paint)
    val bodyPath = Path().apply {
        moveTo(cx - r * 0.6f, cy + r * 0.45f)
        lineTo(cx + r * 0.6f, cy + r * 0.45f)
        lineTo(cx, h.toFloat() - 3f)
        close()
    }
    canvas.drawPath(bodyPath, paint)

    // White inner dot.
    paint.color = AndroidColor.WHITE
    canvas.drawCircle(cx, cy, r * 0.42f, paint)
    return bmp
}

/**
 * Decode a Google-encoded polyline (precision 5) into points. Matches the encoding produced by
 * OTP (transit legs) and our GraphHopper encoder (auto/cab legs). Returns empty list for null/blank.
 */
private fun decodePolyline(encoded: String?): List<Point> {
    if (encoded.isNullOrBlank()) return emptyList()
    val points = mutableListOf<Point>()
    var index = 0
    var lat = 0
    var lng = 0
    while (index < encoded.length) {
        var result = 1
        var shift = 0
        var b: Int
        do {
            b = encoded[index++].code - 63 - 1
            result += b shl shift
            shift += 5
        } while (b >= 0x1f && index < encoded.length)
        lat += if (result and 1 != 0) (result shr 1).inv() else result shr 1

        result = 1
        shift = 0
        do {
            b = encoded[index++].code - 63 - 1
            result += b shl shift
            shift += 5
        } while (b >= 0x1f && index < encoded.length)
        lng += if (result and 1 != 0) (result shr 1).inv() else result shr 1

        points.add(Point.fromLngLat(lng / 1e5, lat / 1e5))
    }
    return points
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
