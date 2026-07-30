package com.commuteplus.android.ui.screens.results

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.commuteplus.android.R
import com.commuteplus.android.data.api.JourneyDto
import com.commuteplus.android.ui.components.ModeIndicator
import com.commuteplus.android.ui.components.formatFare

/**
 * Results screen — ranked list of journey options.
 *
 * Design (taste spec):
 * - Journey card: one glance → mode + total time + fare + transfers.
 * - Sort chips (fastest / cheapest / fewest changes).
 * - Designed empty/error states with specific copy.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsScreen(
    originLat: Double,
    originLng: Double,
    destLat: Double,
    destLng: Double,
    onJourneySelected: (Int) -> Unit,
    onBack: () -> Unit,
    viewModel: ResultsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    // Trigger planning on first composition
    LaunchedEffect(originLat, originLng, destLat, destLng) {
        viewModel.planJourney(originLat, originLng, destLat, destLng)
    }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        // Top bar
        TopAppBar(
            title = { Text(stringResource(R.string.results_title)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        )

        when (val s = state) {
            is ResultsUiState.Loading -> {
                // Designed loading state — not a blank screen with spinner
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp),
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.results_loading),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            is ResultsUiState.Empty -> {
                // Designed empty state — specific, helpful copy
                Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.results_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            is ResultsUiState.Error -> {
                // Designed error state
                Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.results_error),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = s.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            is ResultsUiState.Success -> {
                // Sort chips
                SortChipsRow(
                    selected = s.sortBy,
                    onSelect = { viewModel.setSortOption(it) },
                )

                // Journey cards
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    itemsIndexed(s.sortedJourneys) { index, journey ->
                        JourneyCard(
                            journey = journey,
                            onClick = { onJourneySelected(index) },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortChipsRow(selected: SortOption, onSelect: (SortOption) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SortOption.entries.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = {
                    Text(
                        text = when (option) {
                            SortOption.FASTEST -> stringResource(R.string.sort_fastest)
                            SortOption.CHEAPEST -> stringResource(R.string.sort_cheapest)
                            SortOption.FEWEST_TRANSFERS -> stringResource(R.string.sort_fewest_transfers)
                        },
                        style = MaterialTheme.typography.labelMedium,
                    )
                },
            )
        }
    }
}

/**
 * Journey card — one glance answers "how, how long, how much, how many changes."
 *
 * Layout:
 *   [mode icon] [total time]                     [fare]
 *   [leg strip: Bus 356 → walk → Bus 500]   [transfers]
 */
@Composable
private fun JourneyCard(journey: JourneyDto, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium, // 12dp radius
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp), // near-flat
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Top row: mode + duration ... fare
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ModeIndicator(mode = journey.primaryMode)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${journey.totalDurationMinutes} min",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.weight(1f))
                // Fare (or "Check in app" for modes without price)
                Text(
                    text = formatFare(journey.totalFare),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (journey.totalFare != null)
                        MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Leg strip (compact text: "Bus 356 → Walk → Bus 500")
            Text(
                text = journey.legs.joinToString(" → ") { leg ->
                    when {
                        leg.mode == "WALK" -> "Walk ${leg.distanceMeters}m"
                        leg.routeName != null -> "${leg.mode.lowercase().replaceFirstChar { it.uppercase() }} ${leg.routeName}"
                        else -> leg.mode.lowercase().replaceFirstChar { it.uppercase() }
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            // Transfers indicator (only if > 0)
            if (journey.transfers > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${journey.transfers} transfer${if (journey.transfers > 1) "s" else ""}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
