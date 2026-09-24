package com.whatstats.ui.screen.badges

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whatstats.data.db.model.TimeRange
import com.whatstats.data.preferences.BadgeUnlockStore
import com.whatstats.data.repository.AnalyticsRepository
import com.whatstats.domain.engagement.BadgeEngine
import com.whatstats.domain.model.BadgeProgress
import com.whatstats.telemetry.TelemetryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BadgesViewModel
    @Inject
    constructor(
        private val analyticsRepository: AnalyticsRepository,
        private val badgeUnlockStore: BadgeUnlockStore,
        private val telemetryRepository: TelemetryRepository,
    ) : ViewModel() {
        sealed interface UiState {
            data object Loading : UiState

            data class Error(val message: String?) : UiState

            data class Success(val badges: List<BadgeProgress>, val newlyUnlocked: Set<String>) : UiState
        }

        private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
        val uiState: StateFlow<UiState> = _uiState.asStateFlow()

        init {
            viewModelScope.launch {
                _uiState.value =
                    try {
                        val isLite = analyticsRepository.isLiteMode.value
                        val stats = analyticsRepository.getQuickGlobalStats(timeRange = TimeRange.ALL_TIME)
                        val stored = badgeUnlockStore.read(isLiteMode = isLite)
                        val badges = BadgeEngine.evaluate(stats, stored.unlockedIds)
                        val unlockedNow = badges.filter { it.unlocked }.map { it.id.key }.toSet()
                        val newlyUnlocked = unlockedNow - stored.unlockedIds
                        if (newlyUnlocked.isNotEmpty() || !stored.initialized) {
                            badgeUnlockStore.markUnlocked(isLiteMode = isLite, ids = newlyUnlocked)
                            newlyUnlocked.forEach { key ->
                                telemetryRepository.trackBadgeUnlocked(key, !stored.initialized)
                            }
                        }
                        UiState.Success(badges, newlyUnlocked)
                    } catch (e: Exception) {
                        UiState.Error(e.message)
                    }
            }
        }
    }
