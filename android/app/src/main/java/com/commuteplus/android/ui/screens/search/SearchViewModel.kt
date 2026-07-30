package com.commuteplus.android.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.commuteplus.android.data.api.PlaceDto
import com.commuteplus.android.data.repository.JourneyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
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
)

enum class ActiveField { ORIGIN, DESTINATION, NONE }

/**
 * Search screen ViewModel. Debounces autocomplete queries (300ms), calls real Photon geocoder.
 */
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: JourneyRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state.asStateFlow()

    private var searchJob: Job? = null

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

    fun useCurrentLocationAsOrigin() {
        // TODO: Integrate with FusedLocationProvider to get real GPS coordinates
        // For now this is a placeholder for the location permission flow.
        // When wired, it reverse-geocodes the user's position via Photon and fills the origin.
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
