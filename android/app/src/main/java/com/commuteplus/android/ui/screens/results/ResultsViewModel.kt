package com.commuteplus.android.ui.screens.results

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.commuteplus.android.data.api.JourneyDto
import com.commuteplus.android.data.api.JourneyPlanResponse
import com.commuteplus.android.data.repository.JourneyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ResultsUiState {
    data object Loading : ResultsUiState
    data class Success(
        val response: JourneyPlanResponse,
        val sortedJourneys: List<JourneyDto>,
        val sortBy: SortOption = SortOption.FASTEST,
    ) : ResultsUiState
    data class Error(val message: String) : ResultsUiState
    data object Empty : ResultsUiState  // No routes found
}

enum class SortOption { FASTEST, CHEAPEST, FEWEST_TRANSFERS }

@HiltViewModel
class ResultsViewModel @Inject constructor(
    private val repository: JourneyRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<ResultsUiState>(ResultsUiState.Loading)
    val state: StateFlow<ResultsUiState> = _state.asStateFlow()

    private var fullResponse: JourneyPlanResponse? = null

    fun planJourney(originLat: Double, originLng: Double, destLat: Double, destLng: Double) {
        viewModelScope.launch {
            _state.value = ResultsUiState.Loading

            val result = repository.planJourney(originLat, originLng, destLat, destLng)

            result.onSuccess { response ->
                fullResponse = response
                if (response.journeys.isEmpty()) {
                    _state.value = ResultsUiState.Empty
                } else {
                    _state.value = ResultsUiState.Success(
                        response = response,
                        sortedJourneys = sortJourneys(response.journeys, SortOption.FASTEST),
                    )
                }
            }.onFailure { error ->
                _state.value = ResultsUiState.Error(
                    error.message ?: "Failed to plan journey. Check your connection."
                )
            }
        }
    }

    fun setSortOption(option: SortOption) {
        val current = _state.value
        if (current is ResultsUiState.Success) {
            _state.value = current.copy(
                sortBy = option,
                sortedJourneys = sortJourneys(current.response.journeys, option),
            )
        }
    }

    private fun sortJourneys(journeys: List<JourneyDto>, by: SortOption): List<JourneyDto> {
        return when (by) {
            SortOption.FASTEST -> journeys.sortedBy { it.totalDurationMinutes }
            SortOption.CHEAPEST -> journeys.sortedBy { it.totalFare?.minRupees ?: Double.MAX_VALUE }
            SortOption.FEWEST_TRANSFERS -> journeys.sortedBy { it.transfers }
        }
    }
}
