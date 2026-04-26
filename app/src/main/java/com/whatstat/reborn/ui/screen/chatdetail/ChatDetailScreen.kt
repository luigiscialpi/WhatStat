package com.whatstat.reborn.ui.screen.chatdetail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.whatstat.reborn.ui.component.ActivityRecapCard
import com.whatstat.reborn.ui.component.ContactNameWithPhone
import com.whatstat.reborn.ui.component.EmojiPreviewModal
import com.whatstat.reborn.ui.component.InteractiveEmojiCloud
import com.whatstat.reborn.ui.component.InteractiveStickerCloud
import com.whatstat.reborn.ui.component.InteractiveWordCloud
import com.whatstat.reborn.ui.component.SimpleScreenHeader
import com.whatstat.reborn.ui.component.StatCard
import com.whatstat.reborn.ui.component.StickerPreviewModal
import com.whatstat.reborn.ui.component.TimeRangeFilterRow
import com.whatstat.reborn.ui.tutorial.LocalTutorialManager
import com.whatstat.reborn.ui.tutorial.TutorialOverlay
import java.io.File

@Composable
fun ChatDetailScreen(
    chatId: Long,
    viewModel: ChatDetailViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToWordSearch: () -> Unit = {},
    onNavigateToCalls: () -> Unit = {},
    onNavigateToMedia: () -> Unit = {},
    onNavigateToHistory: () -> Unit = {}
) {
    val stats by viewModel.chatStats.collectAsState()
    val headerInfo by viewModel.headerInfo.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val progress by viewModel.loadingProgress.collectAsState()
    val isLoadingSlow by viewModel.isLoadingSlow.collectAsState()
    val progressSlow by viewModel.loadingProgressSlow.collectAsState()
    val error by viewModel.error.collectAsState()
    val selectedRange by viewModel.selectedTimeRange.collectAsState()
    val customDateRange by viewModel.customDateRange.collectAsState()
    val topStickers by viewModel.topStickers.collectAsState()

    LifecycleResumeEffect(Unit) {
        viewModel.syncFilterState()
        onPauseOrDispose { }
    }

    val tutorialManager = LocalTutorialManager.current
    val tutorialState by tutorialManager.uiState.collectAsState()

    var previewEmoji by remember { mutableStateOf<String?>(null) }
    var previewEmojiCount by remember { mutableIntStateOf(0) }
    var previewSticker by remember { mutableStateOf<String?>(null) }
    var previewStickerCount by remember { mutableIntStateOf(0) }
    var chartDismissSignal by remember { mutableIntStateOf(0) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            // ── Header ───────────────────────────────────────────────
            SimpleScreenHeader(
                title = headerInfo?.name ?: "Dettaglio Chat",
                onBack = onNavigateBack,
                subtitle = headerInfo?.jid,
                avatarPath = headerInfo?.avatarPath
            )

            // ── Progress ─────────────────────────────────────────────
            if (isLoading) {
                LinearProgressIndicator(
                    progress = { (progress ?: 0) / 100f },
                    modifier = Modifier.fillMaxWidth().height(4.dp)
                )
            }

            // ── Filter ───────────────────────────────────────────────
            TimeRangeFilterRow(
                selectedRange = selectedRange,
                onRangeSelected = { viewModel.setTimeRange(it) },
                customDateRange = customDateRange,
                onDateRangeChanged = { viewModel.setCustomDateRange(it) },
                onSubmitClicked = { viewModel.loadChatStats() },
                isSearching = isLoading
            )

            // ── Content ──────────────────────────────────────────────
            if (isLoading && stats == null) {
                ChatDetailSkeletonContent(modifier = Modifier.fillMaxSize())
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    error?.let { err ->
                        item {
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                                Text(err, modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                    }

                    stats?.let { s ->
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    StatCard(modifier = Modifier.weight(1f), label = "Messaggi", value = "%,d".format(s.totalMessages), icon = Icons.AutoMirrored.Outlined.Chat)
                                    StatCard(modifier = Modifier.weight(1f), label = "Sent/Recv", value = "${s.sentByMe} / ${s.receivedFromThem}", icon = Icons.AutoMirrored.Outlined.TrendingUp)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    StatCard(modifier = Modifier.weight(1f), label = "File media", value = "%,d".format(s.mediaCount), icon = Icons.Outlined.PhotoLibrary)
                                    StatCard(modifier = Modifier.weight(1f), label = "Tempo risposta", value = formatResponseTime(s.averageResponseTimeMs), icon = Icons.Outlined.Timer)
                                }
                            }
                        }

                        item {
                            ActivityRecapCard(
                                hourlyData = s.hourlyDistribution,
                                weeklyData = s.weeklyDistribution,
                                dailyData = s.dayOfMonthDistribution,
                                monthlyData = s.monthOfYearDistribution,
                                yearlyData = s.yearTrendDistribution,
                                dismissSignal = chartDismissSignal
                            )
                        }

                        // Analisi Lessicale
                        if (isLoadingSlow) {
                            item {
                                Card(modifier = Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(16.dp)) {
                                        Text("Analisi testi in corso...", style = MaterialTheme.typography.titleSmall)
                                        Spacer(Modifier.height(8.dp))
                                        LinearProgressIndicator(progress = { (progressSlow ?: 0) / 100f }, modifier = Modifier.fillMaxWidth())
                                    }
                                }
                            }
                        } else {
                            item {
                                var selectedTab by remember { mutableIntStateOf(0) }
                                val tabs = listOf("Parole", "Emoji", "Sticker")
                                Column {
                                    ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 0.dp, divider = {}, containerColor = Color.Transparent) {
                                        tabs.forEachIndexed { i, t ->
                                            Tab(selected = selectedTab == i, onClick = { selectedTab = i }, text = { Text(t, fontSize = 12.sp) })
                                        }
                                    }
                                    Spacer(Modifier.height(12.dp))
                                    when (selectedTab) {
                                        0 -> InteractiveWordCloud(words = s.topWords.take(30))
                                        1 -> InteractiveEmojiCloud(emojis = s.topEmojis.take(30), onEmojiClick = { e, c -> previewEmoji = e; previewEmojiCount = c })
                                        2 -> InteractiveStickerCloud(stickers = topStickers.take(10), onStickerClick = { f, c -> previewSticker = f; previewStickerCount = c })
                                    }
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(32.dp)) }
                }
            }
        }

        // Overlays
        if (tutorialState.isTutorialActive && tutorialState.currentScreen == "chatdetail") {
            tutorialManager.currentStep?.let { step ->
                tutorialManager.getTargetBounds(step.targetKey)?.let { bounds ->
                    TutorialOverlay(
                        step = step, targetBounds = bounds, arrowDirection = step.arrowDirection,
                        isVisible = true, isLastStep = tutorialState.currentStepIndex >= tutorialState.steps.size - 1,
                        onNext = { tutorialManager.nextStep() }, onPrevious = { tutorialManager.previousStep() },
                        onClose = { tutorialManager.closeTutorial() }, onComplete = { tutorialManager.completeTutorial("chatdetail") }
                    )
                }
            }
        }
        previewEmoji?.let { e -> EmojiPreviewModal(emoji = e, count = previewEmojiCount, onDismiss = { previewEmoji = null }) }
        previewSticker?.let { f -> StickerPreviewModal(fileName = f, count = previewStickerCount, onDismiss = { previewSticker = null }) }
    }
}
private fun formatResponseTime(ms: Long?): String {
    if (ms == null || ms == 0L) return "N/D"
    val minutes = ms / 60000
    if (minutes < 60) return "${minutes}m"
    val hours = minutes / 60
    if (hours < 24) return "${hours}h"
    return "${hours / 24}g"
}