package com.commuteplus.android.ui.screens.search

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.outlined.TripOrigin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.commuteplus.android.R
import com.commuteplus.android.data.api.PlaceDto

/**
 * Search screen — two fields (from/to) with debounced autocomplete.
 *
 * Design (taste spec):
 * - Content is the UI. Two fields + results, no decoration.
 * - Tap targets ≥ 48dp. Debounced search. "Use my location" shortcut.
 * - No hero, no filler microcopy, no gradient.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onPlanJourney: (originLat: Double, originLng: Double, destLat: Double, destLng: Double, departAtEpochSec: Long?) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    // Location permission launcher — on grant, fetch GPS and set it as the origin.
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.useCurrentLocationAsOrigin()
    }

    fun requestCurrentLocation() {
        val alreadyGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (alreadyGranted) {
            viewModel.useCurrentLocationAsOrigin()
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .statusBarsPadding()
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        // Title — plain, no filler
        Text(
            text = stringResource(R.string.search_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(modifier = Modifier.height(24.dp))

        // --- Origin field ---
        PlaceInputField(
            value = state.originQuery,
            onValueChange = { viewModel.onOriginQueryChanged(it) },
            label = stringResource(R.string.search_from),
            leadingIcon = {
                Icon(
                    Icons.Outlined.TripOrigin,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            },
            trailingIcon = {
                IconButton(onClick = { requestCurrentLocation() }) {
                    Icon(
                        Icons.Filled.MyLocation,
                        contentDescription = stringResource(R.string.use_my_location),
                    )
                }
            },
        )

        Spacer(modifier = Modifier.height(12.dp))

        // --- Destination field ---
        PlaceInputField(
            value = state.destinationQuery,
            onValueChange = { viewModel.onDestinationQueryChanged(it) },
            label = stringResource(R.string.search_to),
            leadingIcon = {
                Icon(
                    Icons.Filled.Place,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
            },
        )

        Spacer(modifier = Modifier.height(16.dp))

        // --- Departure time presets ---
        Text(
            text = stringResource(R.string.search_depart),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DeparturePreset.entries.forEach { preset ->
                FilterChip(
                    selected = state.departure == preset,
                    onClick = { viewModel.onDepartureSelected(preset) },
                    label = { Text(preset.label, style = MaterialTheme.typography.labelMedium) },
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- Autocomplete suggestions ---
        if (state.suggestions.isNotEmpty()) {
            Divider(color = MaterialTheme.colorScheme.outline)
            LazyColumn(
                modifier = Modifier.weight(1f, fill = false),
            ) {
                items(state.suggestions) { place ->
                    PlaceSuggestionItem(
                        place = place,
                        onClick = { viewModel.onPlaceSelected(place) },
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // --- Plan button (bottom-anchored, thumb-reachable) ---
        Button(
            onClick = {
                val origin = state.selectedOrigin
                val dest = state.selectedDestination
                if (origin != null && dest != null) {
                    onPlanJourney(origin.lat, origin.lng, dest.lat, dest.lng, state.departure.toEpochSeconds())
                }
            },
            enabled = state.selectedOrigin != null && state.selectedDestination != null,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = MaterialTheme.shapes.medium,
        ) {
            Text(
                text = stringResource(R.string.search_go),
                style = MaterialTheme.typography.titleSmall,
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun PlaceInputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
    )
}

@Composable
private fun PlaceSuggestionItem(
    place: PlaceDto,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Place,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = place.name,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
