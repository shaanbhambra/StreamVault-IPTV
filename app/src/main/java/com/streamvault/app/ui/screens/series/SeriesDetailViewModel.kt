package com.streamvault.app.ui.screens.series

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamvault.domain.model.Result
import com.streamvault.domain.model.Season
import com.streamvault.domain.model.Series
import com.streamvault.domain.repository.PlaybackHistoryRepository
import com.streamvault.domain.repository.SeriesRepository
import com.streamvault.domain.repository.ProviderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SeriesDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val seriesRepository: SeriesRepository,
    private val providerRepository: ProviderRepository,
    private val playbackHistoryRepository: PlaybackHistoryRepository
) : ViewModel() {

    private val seriesId: Long = checkNotNull(
        savedStateHandle.get<Long>("seriesId")
            ?: savedStateHandle.get<String>("seriesId")?.toLongOrNull()
    )

    private val _uiState = MutableStateFlow(SeriesDetailUiState())
    val uiState: StateFlow<SeriesDetailUiState> = _uiState.asStateFlow()

    init {
        loadSeriesDetails()
    }

    private fun loadSeriesDetails() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, error = null) }

                val provider = providerRepository.getActiveProvider().first()
                if (provider == null) {
                    _uiState.update { it.copy(isLoading = false, error = "No active provider") }
                    return@launch
                }

                observeUnwatchedCount(provider.id)

                when (val result = seriesRepository.getSeriesDetails(provider.id, seriesId)) {
                    is Result.Success -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                series = result.data,
                                selectedSeason = result.data.seasons.firstOrNull(),
                                error = null
                            )
                        }
                    }
                    is Result.Error -> {
                        _uiState.update {
                            it.copy(isLoading = false, error = result.message)
                        }
                    }
                    is Result.Loading -> {
                        _uiState.update { it.copy(isLoading = true) }
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load series details"
                    )
                }
            }
        }
    }

    private fun observeUnwatchedCount(providerId: Long) {
        viewModelScope.launch {
            playbackHistoryRepository.getUnwatchedCount(providerId = providerId, seriesId = seriesId).collect { count ->
                _uiState.update { it.copy(unwatchedEpisodeCount = count) }
            }
        }
    }

    fun selectSeason(season: Season) {
        _uiState.update { it.copy(selectedSeason = season) }
    }
}

data class SeriesDetailUiState(
    val isLoading: Boolean = false,
    val series: Series? = null,
    val selectedSeason: Season? = null,
    val unwatchedEpisodeCount: Int = 0,
    val error: String? = null
)
