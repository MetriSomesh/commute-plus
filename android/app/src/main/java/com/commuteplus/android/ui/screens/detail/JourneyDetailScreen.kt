package com.commuteplus.android.ui.screens.detail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.commuteplus.android.R
import com.commuteplus.android.data.api.JourneyLegDto
import com.commuteplus.android.ui.components.ModeIndicator
import com.commuteplus.android.ui.components.formatFare
import com.commuteplus.android.ui.components.modeColor
import com.commuteplus.android.ui.screens.results.ResultsViewModel

/**
 * Journey detail screen — vertical timeline of legs.
 *
 * Each leg: board stop, route, ride, alight stop, walk.
 * Mode color as the timeline spine. Walk legs dashed.
 *
 * Design (taste spec):
 * - Precise and calm. Content only. The emotional core of the app.
 * - No decoration beyond the colored spine and clear typography.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JourneyDetailScreen(
    journeyIndex: Int,
    onBack: () -> Unit,
    resultsViewModel: ResultsViewModel = hiltViewModel(),
) {
    val state by resultsViewModel.state.collectAsState()

    val journey = when (val s = state) {
        is com.commuteplus.android.ui.screens.results.ResultsUiState.Success ->
            s.sortedJourneys.getOrNull(journeyIndex)
        else -> null
    }

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

        // Summary header
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

        // Vertical timeline of legs
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp),
        ) {
            itemsIndexed(journey.legs) { index, leg ->
                LegTimelineItem(
                    leg = leg,
                    isLast = index == journey.legs.lastIndex,
                )
            }
        }
    }
}

/**
 * Single leg in the vertical timeline.
 *
 * Structure:
 *   [colored line/spine]  [Board at: stop name]
 *                         [Route info: "Bus 356 → 12 stops"]
 *                         [Alight at: stop name]
 *
 * Walk legs use a dashed spine and simpler copy.
 */
@Composable
private fun LegTimelineItem(leg: JourneyLegDto, isLast: Boolean) {
    val color = modeColor(leg.mode)
    val isWalk = leg.mode == "WALK"

    Row(modifier = Modifier.fillMaxWidth()) {
        // Timeline spine (colored vertical line)
        Box(modifier = Modifier.width(24.dp)) {
            Canvas(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(2.dp)
                    .align(Alignment.Center)
            ) {
                val pathEffect = if (isWalk) {
                    PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
                } else null

                drawLine(
                    color = color,
                    start = Offset(size.width / 2, 0f),
                    end = Offset(size.width / 2, size.height),
                    strokeWidth = 3f,
                    pathEffect = pathEffect,
                )
            }

            // Dot at the top of the leg
            Canvas(modifier = Modifier.size(10.dp).align(Alignment.TopCenter)) {
                drawCircle(color = color, radius = size.width / 2)
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Leg content
        Column(modifier = Modifier.padding(bottom = if (isLast) 0.dp else 24.dp)) {
            if (isWalk) {
                Text(
                    text = "Walk ${leg.distanceMeters}m (${leg.durationMinutes} min)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                // Board
                Text(
                    text = "Board at ${leg.from.name}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Route info
                val routeInfo = buildString {
                    append(leg.mode.lowercase().replaceFirstChar { it.uppercase() })
                    leg.routeName?.let { append(" $it") }
                    leg.headsign?.let { append(" → $it") }
                    leg.numStops?.let { append(" · $it stops") }
                }
                Text(
                    text = routeInfo,
                    style = MaterialTheme.typography.labelMedium,
                    color = color,
                )

                // Fare per leg (if available)
                leg.fare?.let { fare ->
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = formatFare(fare),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Alight
                Text(
                    text = "Alight at ${leg.to.name}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
