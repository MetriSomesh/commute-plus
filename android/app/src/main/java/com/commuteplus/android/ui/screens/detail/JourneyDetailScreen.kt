package com.commuteplus.android.ui.screens.detail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.commuteplus.android.R
import com.commuteplus.android.data.api.JourneyLegDto
import com.commuteplus.android.ui.components.ModeIndicator
import com.commuteplus.android.ui.components.RouteMapView
import com.commuteplus.android.ui.components.formatFare
import com.commuteplus.android.ui.components.modeColor
import com.commuteplus.android.util.DeepLinks

/**
 * Journey detail screen — vertical timeline of legs, with a route map and (for on-demand modes)
 * aggregator booking links.
 *
 * Design (taste spec): precise and calm; content only; mode color as the timeline spine.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JourneyDetailScreen(
    journeyIndex: Int,
    onBack: () -> Unit,
    viewModel: JourneyDetailViewModel = hiltViewModel(),
) {
    val journey = remember(journeyIndex) { viewModel.journeyAt(journeyIndex) }
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        TopAppBar(
            title = { Text(stringResource(R.string.detail_title)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        )

        if (journey == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.detail_not_found),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            // Route map
            item {
                RouteMapView(
                    journey = journey,
                    modifier = Modifier.fillMaxWidth().height(220.dp),
                )
            }

            // Summary header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ModeIndicator(mode = journey.primaryMode)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${journey.totalDurationMinutes} min",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = formatFare(journey.totalFare),
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            }

            // Booking links for on-demand modes (no in-app price — open the aggregator app)
            val bookingButtons = bookingLinksFor(journey.primaryMode, viewModel.deepLinks)
            if (bookingButtons.isNotEmpty()) {
                item {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.detail_check_price),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            bookingButtons.forEach { (label, url) ->
                                OutlinedButton(onClick = { DeepLinks.open(context, url) }) {
                                    Icon(
                                        Icons.Filled.OpenInNew,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(label, style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                }
            }

            // Vertical timeline of legs
            item { Spacer(modifier = Modifier.height(16.dp)) }
            itemsIndexed(journey.legs) { index, leg ->
                Row(modifier = Modifier.padding(horizontal = 16.dp)) {
                    LegTimelineItem(leg = leg, isLast = index == journey.legs.lastIndex)
                }
            }
        }
    }
}

/**
 * Which aggregator links to show for a given mode. Cab → Uber + Ola; bike-taxi → Rapido.
 * Returns label → url pairs, filtered to links the backend actually provided.
 */
private fun bookingLinksFor(primaryMode: String, deepLinks: Map<String, String>): List<Pair<String, String>> {
    return when (primaryMode.uppercase()) {
        "CAB" -> listOfNotNull(
            deepLinks["uber"]?.let { "Uber" to it },
            deepLinks["ola"]?.let { "Ola" to it },
        )
        "BIKE_TAXI" -> listOfNotNull(
            deepLinks["rapido"]?.let { "Rapido" to it },
        )
        else -> emptyList()
    }
}

/**
 * Single leg in the vertical timeline: board stop, route info, alight stop. Walk legs are dashed.
 */
@Composable
private fun LegTimelineItem(leg: JourneyLegDto, isLast: Boolean) {
    val color = modeColor(leg.mode)
    val isWalk = leg.mode == "WALK"

    Row(modifier = Modifier.fillMaxWidth()) {
        // Timeline spine
        Box(modifier = Modifier.width(24.dp)) {
            Canvas(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(2.dp)
                    .align(Alignment.Center)
            ) {
                val pathEffect = if (isWalk) {
                    androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
                } else null

                drawLine(
                    color = color,
                    start = Offset(size.width / 2, 0f),
                    end = Offset(size.width / 2, size.height),
                    strokeWidth = 3f,
                    pathEffect = pathEffect,
                )
            }
            Canvas(modifier = Modifier.size(10.dp).align(Alignment.TopCenter)) {
                drawCircle(color = color, radius = size.width / 2)
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.padding(bottom = if (isLast) 0.dp else 24.dp)) {
            if (isWalk) {
                Text(
                    text = "Walk ${leg.distanceMeters}m (${leg.durationMinutes} min)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = "Board at ${leg.from.name}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(4.dp))
                val routeInfo = buildString {
                    append(leg.mode.lowercase().replaceFirstChar { it.uppercase() })
                    leg.routeName?.let { append(" $it") }
                    leg.headsign?.let { append(" → $it") }
                    leg.numStops?.let { append(" · $it stops") }
                }
                Text(text = routeInfo, style = MaterialTheme.typography.labelMedium, color = color)
                leg.fare?.let { fare ->
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = formatFare(fare),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Alight at ${leg.to.name}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
