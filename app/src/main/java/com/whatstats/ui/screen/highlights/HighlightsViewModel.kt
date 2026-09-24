package com.whatstats.ui.screen.highlights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whatstats.data.db.model.TimeRange
import com.whatstats.data.repository.AnalyticsRepository
import com.whatstats.domain.engagement.HighlightEngine
import com.whatstats.domain.model.Highlight
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HighlightsViewModel
    @Inject
    constructor(
        private val analyticsRepository: AnalyticsRepository,
    ) : ViewModel() {
        sealed interface UiState {
            data object Loading : UiState

            data class Error(val message: String?) : UiState

            data class Success(val highlights: List<Highlight>) : UiState
        }

        private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
        val uiState: StateFlow<UiState> = _uiState.asStateFlow()

        init {
            viewModelScope.launch {
                _uiState.value =
                    try {
                        val stats = analyticsRepository.getQuickGlobalStats(timeRange = TimeRange.ALL_TIME)
                        UiState.Success(HighlightEngine.build(stats))
                    } catch (e: Exception) {
                        UiState.Error(e.message)
                    }
            }
        }
    }
