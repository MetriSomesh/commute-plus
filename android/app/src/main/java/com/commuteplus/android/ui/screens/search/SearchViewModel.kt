package com.commuteplus.android.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.commuteplus.android.data.api.PlaceDto
import com.commuteplus.android.data.local.RecentSearchEntity
import com.commuteplus.android.data.repository.JourneyRepository
import com.commuteplus.android.util.LocationProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchState(
    val originQuery: String = "",
    val destinationQuery: String = "",
    val suggestions: List<PlaceDto> = emptyList(),
    val selectedOrigin: PlaceDto? = null,
    val selectedDestination: PlaceDto? = null,
    val activeField: ActiveField = ActiveField.NONE,
    val isSearching: Boolean = false,
    val departure: DeparturePreset = DeparturePreset.NOW,
    val recents: List<RecentSearchEntity> = emptyList(),
)

enum class ActiveField { ORIGIN, DESTINATION, NONE }

/**
 * When to depart. "Now" leaves the time unset (backend uses current time). The fixed-hour presets
 * resolve to the next occurrence of that hour in India time — useful because buses/metro only run
 * during service hours, and a traveler often plans ahead. A full date/time picker is a fast-follow.
 */
enum class DeparturePreset(val label: String, val hour: Int?) {
    NOW("Now", null),
    MORNING("9 AM", 9),
    NOON("12 PM", 12),
    EVENING("6 PM", 18);

    /** Resolve to epoch seconds, or null for NOW. Picks today if the hour is still ahead, else tomorrow. */
    fun toEpochSeconds(): Long? {
        val h = hour ?: return null
        val ist = java.time.ZoneId.of("Asia/Kolkata")
        val now = java.time.ZonedDateTime.now(ist)
        var target = now.withHour(h).withMinute(0).withSecond(0).withNano(0)
        if (!target.isAfter(now)) target = target.plusDays(1)
        return target.toEpochSecond()
    }
}

/**
 * Search screen ViewModel. Debounces autocomplete queries (300ms), calls real Photon geocoder.
 */
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: JourneyRepository,
    private val locationProvider: LocationProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state.asStateFlow()

    private var searchJob: Job? = null

    init {
        loadRecents()
    }

    private fun loadRecents() {
        viewModelScope.launch {
            _state.update { it.copy(recents = repository.getRecentSearches()) }
        }
    }

    /** Persist the current origin/destination as a recent trip (called when a search is run). */
    fun saveCurrentAsRecent() {
        val origin = _state.value.selectedOrigin ?: return
        val dest = _state.value.selectedDestination ?: return
        viewModelScope.launch {
            repository.saveRecentSearch(
                originName = origin.name,
                originLat = origin.lat,
                originLng = origin.lng,
                destName = dest.name,
                destLat = dest.lat,
                destLng = dest.lng,
            )
            loadRecents()
        }
    }

    fun onDepartureSelected(preset: DeparturePreset) {
        _state.update { it.copy(departure = preset) }
    }

    fun onOriginQueryChanged(query: String) {
        _state.update {
            it.copy(originQuery = query, activeField = ActiveField.ORIGIN, selectedOrigin = null)
        }
        debounceSearch(query)
    }

    fun onDestinationQueryChanged(query: String) {
        _state.update {
            it.copy(destinationQuery = query, activeField = ActiveField.DESTINATION, selectedDestination = null)
        }
        debounceSearch(query)
    }

    fun onPlaceSelected(place: PlaceDto) {
        _state.update { current ->
            when (current.activeField) {
                ActiveField.ORIGIN -> current.copy(
                    selectedOrigin = place,
                    originQuery = place.name,
                    suggestions = emptyList(),
                    activeField = ActiveField.NONE,
                )
                ActiveField.DESTINATION -> current.copy(
                    selectedDestination = place,
                    destinationQuery = place.name,
                    suggestions = emptyList(),
                    activeField = ActiveField.NONE,
                )
                ActiveField.NONE -> current
            }
        }
    }

    /**
     * Fetch the device's real GPS location and set it as the origin.
     * The caller (screen) must ensure location permission is granted first; if it isn't,
     * [LocationProvider.getCurrentLocation] returns null and this is a no-op.
     */
    fun useCurrentLocationAsOrigin() {
        viewModelScope.launch {
            val loc = locationProvider.getCurrentLocation() ?: return@launch
            val place = PlaceDto(
                id = "current-location",
                name = "Current location",
                lat = loc.lat,
                lng = loc.lng,
            )
            _state.update {
                it.copy(
                    selectedOrigin = place,
                    originQuery = place.name,
                    suggestions = emptyList(),
                    activeField = ActiveField.NONE,
                )
            }
        }
    }

    /**
     * Debounce 300ms before searching (prevents spamming the geocoder on every keystroke).
     * This is a common UX pattern for autocomplete.
     */
    private fun debounceSearch(query: String) {
        searchJob?.cancel()
        if (query.length < 2) {
            _state.update { it.copy(suggestions = emptyList(), isSearching = false) }
            return
        }

        searchJob = viewModelScope.launch {
            delay(300) // debounce
            _state.update { it.copy(isSearching = true) }
            val result = repository.searchPlaces(query)
            result.onSuccess { places ->
                _state.update { it.copy(suggestions = places, isSearching = false) }
            }.onFailure {
                _state.update { it.copy(suggestions = emptyList(), isSearching = false) }
            }
        }
    }
}
