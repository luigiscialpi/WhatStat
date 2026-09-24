package com.whatstats.ui.screen.dashboard

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.whatstats.R
import com.whatstats.data.db.model.TimeRange
import com.whatstats.ui.MainViewModel
import com.whatstats.ui.component.ActionCard
import com.whatstats.ui.component.ActivityRecapCard
import com.whatstats.ui.component.LoadingProgressSlot
import com.whatstats.ui.component.LoadingSection
import com.whatstats.ui.component.MilestonePredictionCard
import com.whatstats.ui.component.SectionCard
import com.whatstats.ui.component.TimeRangeFilterRow
import com.whatstats.ui.component.ads.NativeAdCard
import com.whatstats.util.findActivity

@Composable
@Suppress("FunctionName")
fun DashboardScreen(
    viewModel: DashboardViewModel = hiltViewModel(),
    showNativeAds: Boolean = false,
    onNavigateToChatList: () -> Unit = {},
    onNavigateToWordSearch: () -> Unit = {},
    onNavigateToCalls: () -> Unit = {},
    onNavigateToMedia: () -> Unit = {},
    onNavigateToChatDetail: (Long) -> Unit = {},
    onNavigateToHighlights: () -> Unit = {},
    onNavigateToBadges: () -> Unit = {},
    onNavigateBack: () -> Unit,
) {
    val mainVm: MainViewModel = hiltViewModel()
    val adsUiState by mainVm.adsUiState.collectAsState()
    val context = LocalContext.current
    val stats by viewModel.globalStats.collectAsState()

    // Intercetta il back di sistema per usare la stessa logica del pulsante toolbar
    BackHandler { onNavigateBack() }

    val reviewPrompted = remember { mutableStateOf(false) }
    LaunchedEffect(stats) {
        if (stats != null && !reviewPrompted.value) {
            val activity = context.findActivity()
            if (activity != null) {
                reviewPrompted.value = true
                viewModel.launchReviewIfEligible(activity)
            }
        }
    }

    val hourlyData by viewModel.hourlyData.collectAsState()
    val weeklyData by viewModel.weeklyData.collectAsState()
    val dailyData by viewModel.dayOfMonthData.collectAsState()
    val monthlyData by viewModel.monthOfYearData.collectAsState()
    val yearlyData by viewModel.yearTrendData.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val progress by viewModel.loadingProgress.collectAsState()
    val isLoadingSlow by viewModel.isLoadingSlow.collectAsState()
    val progressSlow by viewModel.loadingProgressSlow.collectAsState()
    val error by viewModel.error.collectAsState()
    val isLiteMode by viewModel.isLiteMode.collectAsState()

    val selectedRange by viewModel.selectedTimeRange.collectAsState()
    val customDateRange by viewModel.customDateRange.collectAsState()
    val firstMessageTimestamp by viewModel.firstMessageTimestamp.collectAsState()

    LifecycleResumeEffect(Unit) {
        viewModel.syncFilterState()
        viewModel.onResume()
        mainVm.refreshRewardedInsightsLockState()
        onPauseOrDispose {
            viewModel.onPause()
        }
    }
    val selectedLimit by viewModel.selectedLimit.collectAsState()

    val slowStatsRequested by viewModel.slowStatsRequested.collectAsState()
    val tabsRewardGateLocked = !adsUiState.rewardedTabsUnlocked
    val predictionRewardGateLocked = !adsUiState.rewardedPredictionUnlocked

    fun requestTabsUnlock(source: String) {
        val activity = context.findActivity() ?: return
        mainVm.requestRewardedTabsUnlock(activity = activity, source = source) { unlocked ->
            val msgRes =
                if (unlocked) {
                    R.string.rewarded_unlock_tabs_success_2h_toast
                } else {
                    R.string.rewarded_unlock_not_available_toast
                }
            Toast.makeText(context, context.getString(msgRes), Toast.LENGTH_LONG).show()
        }
    }

    fun requestPredictionUnlock(source: String) {
        val activity = context.findActivity() ?: return
        mainVm.requestRewardedPredictionUnlock(activity = activity, source = source) { unlocked ->
            val msgRes =
                if (unlocked) {
                    R.string.rewarded_unlock_prediction_success_2h_toast
                } else {
                    R.string.rewarded_unlock_not_available_toast
                }
            Toast.makeText(context, context.getString(msgRes), Toast.LENGTH_LONG).show()
        }
    }

    // ── FIX: estrai 's' fuori dal let, così è accessibile in tutto il LazyColumn ──
    val s = stats

    var previewEmoji by remember { mutableStateOf<String?>(null) }
    var previewEmojiCount by remember { mutableIntStateOf(0) }
    var previewSticker by remember { mutableStateOf<String?>(null) }
    var previewStickerCount by remember { mutableIntStateOf(0) }
    var chartDismissSignal by remember { mutableIntStateOf(0) }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Final)
                            // changedToUp() (senza IgnoreConsumed) è true solo se l'evento
                            // NON è stato consumato da un figlio (es. clickable della barra)
                            if (event.changes.any { it.changedToUp() }) {
                                chartDismissSignal++
                            }
                        }
                    }
                },
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
        ) {
            DashboardHeader(
                isLiteMode = isLiteMode,
                onNavigateBack = onNavigateBack,
            )

            // ── Progress bar ─────────────────────────────────────────
            LoadingProgressSlot(isLoading = isLoading, progress = progress)

            // ── Sticky Filter Chips ───────────────────────────────────
            TimeRangeFilterRow(
                selectedRange = selectedRange,
                onRangeSelected = { viewModel.setTimeRange(it) },
                customDateRange = customDateRange,
                onDateRangeChanged = { viewModel.setCustomDateRange(it) },
                onSubmitClicked = { viewModel.retry() },
                isSearching = isLoading,
                onCancelClicked = { viewModel.cancelLoad() },
                minDate = firstMessageTimestamp,
            )

            // ── Scrollable Content ───────────────────────────────────
            if (isLoading) {
                DashboardSkeletonContent(modifier = Modifier.fillMaxSize())
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (error != null) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors =
                                    CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer,
                                    ),
                            ) {
                                Column(Modifier.padding(16.dp)) {
                                    Text(
                                        stringResource(R.string.error_title),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                    )
                                    Text(
                                        error ?: "",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Button(onClick = { viewModel.retry() }) {
                                        Text(stringResource(R.string.retry))
                                    }
                                }
                            }
                        }
                    }

                    // ── Stat Cards Grid (2x3) ───────────────────────────────
                    if (s != null) {
                        item {
                            DashboardStatsGrid(
                                stats = s,
                                onNavigateToMedia = onNavigateToMedia,
                                onNavigateToChatDetail = onNavigateToChatDetail,
                            )
                        }
                    }

                    if (showNativeAds) {
                        item {
                            NativeAdCard()
                        }
                    }

                    // ── Activity Recap Card (Tabbed) ──────────────────────────
                    item {
                        ActivityRecapCard(
                            hourlyData = hourlyData,
                            weeklyData = weeklyData,
                            dailyData = dailyData,
                            monthlyData = monthlyData,
                            yearlyData = yearlyData,
                            dismissSignal = chartDismissSignal,
                            rewardGateEnabled = true,
                            rewardedInsightsUnlocked = !tabsRewardGateLocked,
                            onRequestUnlockInsights = { requestTabsUnlock("dashboard_activity_tabs") },
                        )
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            ActionCard(
                                modifier = Modifier.weight(1f),
                                title = stringResource(R.string.highlights_entry_title),
                                subtitle = stringResource(R.string.highlights_entry_sub),
                                icon = Icons.Outlined.AutoAwesome,
                                onClick = {
                                    mainVm.trackClick("dashboard", "open_highlights", "engagement_entry")
                                    onNavigateToHighlights()
                                },
                            )
                            ActionCard(
                                modifier = Modifier.weight(1f),
                                title = stringResource(R.string.badges_entry_title),
                                subtitle = stringResource(R.string.badges_entry_sub),
                                icon = Icons.Outlined.EmojiEvents,
                                onClick = {
                                    mainVm.trackClick("dashboard", "open_badges", "engagement_entry")
                                    onNavigateToBadges()
                                },
                            )
                        }
                    }

                    // ── Milestone Prediction (Root Only) ──────────────────────
                    if (s != null && !isLiteMode) {
                        item {
                            MilestonePredictionCard(
                                state = s.milestonePredictionState,
                                periodLabel =
                                    if (selectedRange != TimeRange.ALL_TIME) {
                                        stringResource(R.string.prediction_period_filtered_label)
                                    } else {
                                        null
                                    },
                                isLocked = predictionRewardGateLocked,
                                onRequestUnlockInsights = { requestPredictionUnlock("dashboard_prediction_card") },
                            )
                        }
                    }

                    // ── Analisi Lessicale (opt-in) ───────────────────────
                    item {
                        if (!slowStatsRequested) {
                            SectionCard(
                                title = stringResource(R.string.lexical_analysis_title),
                                subtitle = stringResource(R.string.lexical_analysis_subtitle),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(bottom = 12.dp),
                                ) {
                                    Icon(
                                        Icons.Outlined.Warning,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        stringResource(R.string.lexical_analysis_info),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Button(
                                    onClick = { viewModel.loadSlowStats() },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Icon(
                                        Icons.Outlined.Search,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.run_analysis_button))
                                }
                            }
                        } else if (isLoadingSlow) {
                            SectionCard(
                                title = stringResource(R.string.lexical_analysis_title),
                                subtitle = stringResource(R.string.processing),
                            ) {
                                LoadingSection(progress = progressSlow)
                            }
                        }
                    }

                    if (slowStatsRequested && !isLoadingSlow && s != null) {
                        item {
                            DashboardTextAnalysisSection(
                                stats = s,
                                isLiteMode = isLiteMode,
                                selectedLimit = selectedLimit,
                                onLimitSelected = { viewModel.setSelectedLimit(it) },
                                onEmojiClick = { emoji, count ->
                                    previewEmoji = emoji
                                    previewEmojiCount = count
                                },
                                onStickerClick = { fileName, count ->
                                    previewSticker = fileName
                                    previewStickerCount = count
                                },
                            )
                        }
                    }

                    // Spacer finale per navigation bar
                    item { Spacer(Modifier.height(32.dp)) }
                }
            } // fine else (!isLoading)
        } // fine Column

        DashboardPreviewHost(
            previewEmoji = previewEmoji,
            previewEmojiCount = previewEmojiCount,
            onDismissEmoji = { previewEmoji = null },
            previewSticker = previewSticker,
            previewStickerCount = previewStickerCount,
            onDismissSticker = { previewSticker = null },
            isLiteMode = isLiteMode,
            previewStickerLitePath = s?.topStickers?.find { it.fileName == previewSticker }?.filePath,
        )
    } // fine Box
}
