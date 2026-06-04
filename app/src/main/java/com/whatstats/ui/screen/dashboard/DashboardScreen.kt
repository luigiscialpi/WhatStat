package com.whatstats.ui.screen.dashboard

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.whatstats.R
import com.whatstats.data.db.model.TimeRange
import com.whatstats.ui.component.ActivityRecapCard
import com.whatstats.ui.component.EmojiPreviewModal
import com.whatstats.ui.component.InteractiveEmojiCloud
import com.whatstats.ui.component.InteractiveStickerCloud
import com.whatstats.ui.component.InteractiveWordCloud
import com.whatstats.ui.component.LoadingSection
import com.whatstats.ui.component.MilestonePredictionCard
import com.whatstats.ui.component.StatCard
import com.whatstats.ui.component.StickerPreviewModal
import com.whatstats.ui.component.TimeRangeFilterRow
import com.whatstats.ui.component.ads.NativeAdCard
import kotlinx.coroutines.launch
import java.io.File

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
    onNavigateBack: () -> Unit,
) {
    val stats by viewModel.globalStats.collectAsState()

    // Intercetta il back di sistema per usare la stessa logica del pulsante toolbar
    androidx.activity.compose.BackHandler { onNavigateBack() }

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
        onPauseOrDispose {
            viewModel.onPause()
        }
    }
    val selectedLimit by viewModel.selectedLimit.collectAsState()

    val slowStatsRequested by viewModel.slowStatsRequested.collectAsState()

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
            // ── Header ───────────────────────────────────────────────
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val drawerState = com.whatstats.ui.component.LocalDrawerState.current
                val scope = rememberCoroutineScope()
                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                    Icon(
                        Icons.Default.Menu,
                        contentDescription = stringResource(R.string.menu_desc),
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.dashboard_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    if (isLiteMode) {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.padding(top = 2.dp),
                        ) {
                            Text(
                                stringResource(R.string.lite_mode_badge),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }

                // Back button: torna a SetupScreen in entrambi i modi
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back_button_desc),
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }

            // ── Progress bar ─────────────────────────────────────────
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(4.dp),
            ) {
                if (isLoading) {
                    val p = progress
                    if (p != null) {
                        LinearProgressIndicator(
                            progress = { p / 100f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            }

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
                            AnimatedVisibility(
                                visible = true,
                                enter = fadeIn() + slideInVertically { it / 2 },
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        StatCard(
                                            modifier = Modifier.weight(1f),
                                            label = stringResource(R.string.stat_total_messages),
                                            value = "%,d".format(s.totalMessages),
                                            icon = Icons.AutoMirrored.Outlined.Chat,
                                        )
                                        StatCard(
                                            modifier = Modifier.weight(1f),
                                            label = stringResource(R.string.stat_avg_per_day),
                                            value = "%.1f".format(s.averageMessagesPerDay),
                                            icon = Icons.AutoMirrored.Outlined.TrendingUp,
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        StatCard(
                                            modifier = Modifier.weight(1f),
                                            label = stringResource(R.string.stat_total_media),
                                            value = "%,d".format(s.totalMediaFiles),
                                            icon = Icons.Outlined.PhotoLibrary,
                                            isSmallText = true,
                                            onClick = onNavigateToMedia,
                                        )
                                        StatCard(
                                            modifier = Modifier.weight(1f),
                                            label = stringResource(R.string.stat_most_active_chat),
                                            value = s.mostActiveChat,
                                            icon = Icons.AutoMirrored.Outlined.TrendingUp,
                                            isSmallText = true,
                                            onClick = s.mostActiveChatId?.let { id -> { onNavigateToChatDetail(id) } },
                                        )
                                    }
                                }
                            }
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
                        )
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

                    // ── Analisi Testi (Parole / Emoji / Sticker) ──────────
                    // In Lite mode lo Sticker tab viene nascosto: non possiamo aggregare
                    // gli sticker per file identico quindi i conteggi sarebbero fuorvianti.
                    if (slowStatsRequested && !isLoadingSlow && s != null) {
                        item {
                            val tabs =
                                if (isLiteMode) {
                                    listOf(
                                        stringResource(R.string.tab_words),
                                        stringResource(R.string.tab_emojis),
                                    )
                                } else {
                                    listOf(
                                        stringResource(R.string.tab_words),
                                        stringResource(R.string.tab_emojis),
                                        stringResource(R.string.tab_stickers),
                                    )
                                }
                            var selectedTab by remember { mutableIntStateOf(0) }
                            // Clamp per evitare indice out-of-bounds se si cambia modalità
                            if (selectedTab >= tabs.size) selectedTab = 0

                            SectionCard(
                                title = stringResource(R.string.text_analysis_title),
                                subtitle = stringResource(R.string.text_analysis_subtitle),
                                trailingContent = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        val limits = if (isLiteMode) listOf(10, 20, 30, 50) else listOf(5, 10, 20)
                                        limits.forEach { limit ->
                                            Box(
                                                modifier =
                                                    Modifier
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(
                                                            if (selectedLimit == limit) {
                                                                MaterialTheme.colorScheme.primary
                                                            } else {
                                                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                                            },
                                                        )
                                                        .clickable { viewModel.setSelectedLimit(limit) }
                                                        .padding(horizontal = 8.dp, vertical = 2.dp),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Text(
                                                    limit.toString(),
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color =
                                                        if (selectedLimit == limit) {
                                                            MaterialTheme.colorScheme.onPrimary
                                                        } else {
                                                            MaterialTheme.colorScheme.onSurfaceVariant
                                                        },
                                                )
                                            }
                                        }
                                    }
                                },
                            ) {
                                val sharedIndicator: @Composable (List<androidx.compose.material3.TabPosition>) -> Unit = { tabPositions ->
                                    if (selectedTab < tabPositions.size) {
                                        TabRowDefaults.SecondaryIndicator(
                                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                                if (isLiteMode) {
                                    TabRow(
                                        selectedTabIndex = selectedTab,
                                        containerColor = Color.Transparent,
                                        contentColor = MaterialTheme.colorScheme.primary,
                                        divider = {},
                                        modifier = Modifier.padding(bottom = 12.dp),
                                        indicator = sharedIndicator,
                                    ) {
                                        tabs.forEachIndexed { index, title ->
                                            Tab(
                                                selected = selectedTab == index,
                                                onClick = { selectedTab = index },
                                                text = { Text(title, fontSize = 12.sp) },
                                            )
                                        }
                                    }
                                } else {
                                    ScrollableTabRow(
                                        selectedTabIndex = selectedTab,
                                        containerColor = Color.Transparent,
                                        contentColor = MaterialTheme.colorScheme.primary,
                                        edgePadding = 0.dp,
                                        divider = {},
                                        modifier = Modifier.padding(bottom = 12.dp),
                                        indicator = sharedIndicator,
                                    ) {
                                        tabs.forEachIndexed { index, title ->
                                            Tab(
                                                selected = selectedTab == index,
                                                onClick = { selectedTab = index },
                                                text = { Text(title, fontSize = 12.sp) },
                                            )
                                        }
                                    }
                                }

                                when (selectedTab) {
                                    0 -> {
                                        if (s.topWords.isEmpty()) {
                                            Text(
                                                stringResource(R.string.no_words_found),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(vertical = 24.dp),
                                            )
                                        } else {
                                            InteractiveWordCloud(
                                                words = s.topWords.take(selectedLimit),
                                            )
                                        }
                                    }
                                    1 -> {
                                        if (s.topEmojis.isEmpty()) {
                                            Text(
                                                stringResource(R.string.no_emojis_found),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(vertical = 24.dp),
                                            )
                                        } else {
                                            InteractiveEmojiCloud(
                                                emojis = s.topEmojis.take(selectedLimit),
                                                onEmojiClick = { emoji, count ->
                                                    previewEmoji = emoji
                                                    previewEmojiCount = count
                                                },
                                            )
                                        }
                                    }
                                    2 -> {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(bottom = 8.dp),
                                        ) {
                                            Icon(
                                                Icons.Outlined.Info,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                                modifier = Modifier.size(14.dp),
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text(
                                                stringResource(R.string.sticker_info_hint),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        if (s.topStickers.isEmpty()) {
                                            Text(
                                                stringResource(R.string.no_stickers_found),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(vertical = 24.dp),
                                            )
                                        } else {
                                            InteractiveStickerCloud(
                                                stickers = s.topStickers.take(selectedLimit),
                                                onStickerClick = { fileName, count ->
                                                    previewSticker = fileName
                                                    previewStickerCount = count
                                                },
                                                isLiteMode = isLiteMode,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Spacer finale per navigation bar
                    item { Spacer(Modifier.height(32.dp)) }
                }
            } // fine else (!isLoading)
        } // fine Column

        // ── Emoji Preview Modal ──────────────────────────────────
        if (previewEmoji != null) {
            EmojiPreviewModal(
                emoji = previewEmoji!!,
                count = previewEmojiCount,
                onDismiss = { previewEmoji = null },
            )
        }

        // ── Sticker Preview Modal ─────────────────────────────────
        if (previewSticker != null) {
            StickerPreviewModal(
                fileName = previewSticker!!,
                count = previewStickerCount,
                onDismiss = { previewSticker = null },
                isLiteMode = isLiteMode,
                litePath = s?.topStickers?.find { it.fileName == previewSticker }?.filePath,
            )
        }
    } // fine Box
}

@Composable
@Suppress("FunctionName")
private fun DashboardStickerItem(
    fileName: String,
    count: Int,
    isLiteMode: Boolean = false,
    litePath: String? = null,
) {
    val context = LocalContext.current
    val stickerPaths =
        listOf(
            "/sdcard/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Stickers",
            "/sdcard/WhatsApp/Media/WhatsApp Stickers",
        )
    val stickerFile =
        remember(fileName, isLiteMode, litePath) {
            if (isLiteMode && litePath != null) {
                File(litePath)
            } else {
                stickerPaths.map { java.io.File(it, fileName) }.firstOrNull { it.exists() }
            }
        }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(72.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (stickerFile != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(stickerFile).crossfade(true).build(),
                    contentDescription = fileName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Icon(
                    Icons.Outlined.PhotoLibrary,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(32.dp),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "(%d)".format(count),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
@Suppress("FunctionName")
private fun SectionCard(
    title: String,
    subtitle: String? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    if (subtitle != null) {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
                if (trailingContent != null) {
                    trailingContent()
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            content()
        }
    }
}
