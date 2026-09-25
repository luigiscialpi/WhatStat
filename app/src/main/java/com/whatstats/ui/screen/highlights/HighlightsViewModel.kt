package com.whatstats.ui.screen.highlights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whatstats.data.db.model.TimeRange
import com.whatstats.data.repository.AnalyticsRepository
import com.whatstats.data.repository.EngagementRepository
import com.whatstats.domain.engagement.HighlightEngine
import com.whatstats.domain.model.Highlight
import com.whatstats.telemetry.TelemetryRepository
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
        private val engagementRepository: EngagementRepository,
        private val telemetryRepository: TelemetryRepository,
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
                        val isLite = analyticsRepository.isLiteMode.value
                        val people =
                            try {
                                engagementRepository.getOneToOneReplyStats()
                            } catch (e: Exception) {
                                emptyList()
                            }
                        UiState.Success(
                            HighlightEngine.build(stats) +
                                HighlightEngine.buildPersonHighlights(people, includeReplyTimes = !isLite),
                        )
                    } catch (e: Exception) {
                        UiState.Error(e.message)
                    }
            }
        }

        fun onHighlightShared(cardKey: String) {
            telemetryRepository.trackHighlightShared(cardId = cardKey)
        }
    }
