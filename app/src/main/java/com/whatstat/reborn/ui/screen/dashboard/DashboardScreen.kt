package com.whatstat.reborn.ui.screen.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.hilt.navigation.compose.hiltViewModel
import com.whatstat.reborn.ui.component.LoadingSection
import com.whatstat.reborn.data.db.model.TimeRange
import com.whatstat.reborn.ui.component.DateRangePicker
import com.whatstat.reborn.ui.component.InteractiveHourlyHeatmap
import com.whatstat.reborn.ui.component.ActivityRecapCard
import com.whatstat.reborn.ui.component.ChartTooltip
import com.whatstat.reborn.ui.component.StatCard
import com.whatstat.reborn.ui.component.InteractiveEmojiCloud
import com.whatstat.reborn.ui.component.InteractiveStickerCloud
import com.whatstat.reborn.ui.component.InteractiveWordCloud

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = hiltViewModel(),
    onNavigateToChatList: () -> Unit,
    onNavigateToWordSearch: () -> Unit = {},
    onNavigateToCalls: () -> Unit = {},
    onNavigateToMedia: () -> Unit = {},
    onNavigateBack: () -> Unit
) {
    val stats by viewModel.globalStats.collectAsState()
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
    val selectedRange by viewModel.selectedTimeRange.collectAsState()
    val customDateRange by viewModel.customDateRange.collectAsState()

    val slowStatsRequested by viewModel.slowStatsRequested.collectAsState()

    var tooltipTitle by remember { mutableStateOf("") }
    var tooltipValue by remember { mutableStateOf("") }
    var showTooltip by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Header ───────────────────────────────────────────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Panoramica",
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            "Le tue statistiche WhatsApp",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Torna indietro"
                        )
                    }
                }
            }

            // ── Sticky Filter Chips ───────────────────────────────────
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(TimeRange.entries.filter { it != TimeRange.CUSTOM }) { range ->
                        FilterChip(
                            selected = selectedRange == range,
                            onClick = { viewModel.setTimeRange(range) },
                            label = { Text(range.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }
            }

            // ── Custom Date Range Picker ─────────────────────────────
            item {
                DateRangePicker(
                    dateRange = customDateRange,
                    onDateRangeChanged = { viewModel.setCustomDateRange(it) }
                )
            }

            if (isLoading) {
                item {
                    LoadingSection(progress = progress)
                }
            } else if (error != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                "ERRORE",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                error ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = { viewModel.retry() }) {
                                Text("Riprova")
                            }
                        }
                    }
                }
            }

            // ── Stat Cards Grid (2x3) ───────────────────────────────
            stats?.let { s ->
                item {
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn() + slideInVertically { it / 2 }
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                StatCard(
                                    modifier = Modifier.weight(1f),
                                    label = "Messaggi totali",
                                    value = "%,d".format(s.totalMessages),
                                    icon = Icons.AutoMirrored.Outlined.Chat
                                )
                                StatCard(
                                    modifier = Modifier.weight(1f),
                                    label = "Chat attive",
                                    value = s.totalChats.toString(),
                                    icon = Icons.Outlined.People
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                StatCard(
                                    modifier = Modifier.weight(1f),
                                    label = "Media/giorno",
                                    value = "%.1f".format(s.averageMessagesPerDay),
                                    icon = Icons.AutoMirrored.Outlined.TrendingUp
                                )
                                StatCard(
                                    modifier = Modifier.weight(1f),
                                    label = "Striscia più lunga",
                                    value = "${s.longestStreak} giorni",
                                    icon = Icons.Outlined.LocalFireDepartment
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                StatCard(
                                    modifier = Modifier.weight(1f),
                                    label = "File media",
                                    value = "%,d".format(s.totalMediaFiles),
                                    icon = Icons.Outlined.PhotoLibrary,
                                    isSmallText = true
                                )
                                StatCard(
                                    modifier = Modifier.weight(1f),
                                    label = "Chat più attiva",
                                    value = s.mostActiveChat,
                                    icon = Icons.AutoMirrored.Outlined.TrendingUp,
                                    isSmallText = true
                                )
                            }
                        }
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
                        onPointClick = { title, value ->
                            tooltipTitle = title
                            tooltipValue = value
                            showTooltip = true
                        }
                    )
                }

                // ── Analisi Lessicale (opt-in) ───────────────────────
                item {
                    if (!slowStatsRequested) {
                        SectionCard(
                            title = "Analisi Lessicale",
                            subtitle = "Parole ed emoji più frequenti"
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(bottom = 12.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "L'analisi potrebbe richiedere diversi secondi",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Button(
                                onClick = { viewModel.loadSlowStats() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    Icons.Outlined.Search,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Avvia analisi")
                            }
                        }
                    } else if (isLoadingSlow) {
                        SectionCard(
                            title = "Analisi Lessicale",
                            subtitle = "Elaborazione in corso..."
                        ) {
                            LoadingSection(progress = progressSlow)
                        }
                    }
                }

                if (slowStatsRequested && !isLoadingSlow) {
                    // ── Analisi Testi (Parole / Emoji / Sticker) ──────
                    item {
                        val tabs = listOf("Parole", "Emoji", "Sticker")
                        var selectedTab by remember { mutableIntStateOf(0) }

                        SectionCard(
                            title = "Analisi testi",
                            subtitle = "Parole, emoji e sticker più frequenti"
                        ) {
                            ScrollableTabRow(
                                selectedTabIndex = selectedTab,
                                containerColor = Color.Transparent,
                                contentColor = MaterialTheme.colorScheme.primary,
                                edgePadding = 0.dp,
                                divider = {},
                                modifier = Modifier.padding(bottom = 12.dp),
                                indicator = { tabPositions ->
                                    if (selectedTab < tabPositions.size) {
                                        TabRowDefaults.SecondaryIndicator(
                                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            ) {
                                tabs.forEachIndexed { index, title ->
                                    Tab(
                                        selected = selectedTab == index,
                                        onClick = { selectedTab = index },
                                        text = { Text(title, fontSize = 12.sp) }
                                    )
                                }
                            }

                            when (selectedTab) {
                                0 -> {
                                    if (s.topWords.isEmpty()) {
                                        Text(
                                            "Nessuna parola trovata",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(vertical = 24.dp)
                                        )
                                    } else {
                                        InteractiveWordCloud(
                                            words = s.topWords.take(20)
                                        )
                                    }
                                }
                                1 -> {
                                    if (s.topEmojis.isEmpty()) {
                                        Text(
                                            "Nessuna emoji trovata",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(vertical = 24.dp)
                                        )
                                    } else {
                                        InteractiveEmojiCloud(
                                            emojis = s.topEmojis.take(20)
                                        )
                                    }
                                }
                                2 -> {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    ) {
                                        Icon(
                                            Icons.Outlined.Info,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            "Sticker simili possono comparire più volte perché WhatsApp li salva come file separati.",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (s.topStickers.isEmpty()) {
                                        Text(
                                            "Nessuno sticker trovato",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(vertical = 24.dp)
                                        )
                                    } else {
                                        InteractiveStickerCloud(
                                            stickers = s.topStickers.take(20)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // ── Action Grid (2x2) ────────────────────────────────
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ActionCard(
                                modifier = Modifier.weight(1f),
                                title = "Radar Parole",
                                subtitle = "Cerca parole frequenti",
                                icon = Icons.Outlined.Search,
                                onClick = onNavigateToWordSearch
                            )
                            ActionCard(
                                modifier = Modifier.weight(1f),
                                title = "Log Chiamate",
                                subtitle = "Audio, video, perse",
                                icon = Icons.Outlined.Call,
                                onClick = onNavigateToCalls
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ActionCard(
                                modifier = Modifier.weight(1f),
                                title = "Media & Link",
                                subtitle = "Documenti e URL",
                                icon = Icons.Outlined.Link,
                                onClick = onNavigateToMedia
                            )
                            ActionCard(
                                modifier = Modifier.weight(1f),
                                title = "Analisi Chat",
                                subtitle = "Singole conversazioni",
                                icon = Icons.AutoMirrored.Outlined.Chat,
                                onClick = onNavigateToChatList
                            )
                        }
                    }
                }

                // Spacer finale per navigation bar
                item { Spacer(Modifier.height(32.dp)) }
            }
        } // fine LazyColumn

        // ── Tooltip overlay ──────────────────────────────────────
        if (showTooltip) {
            ChartTooltip(
                title = tooltipTitle,
                value = tooltipValue,
                onDismiss = { showTooltip = false }
            )
        }
    } // fine Box
}

@Composable
private fun ActionCard(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun DashboardStickerItem(fileName: String, count: Int) {
    val context = LocalContext.current
    val stickerPaths = listOf(
        "/sdcard/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Stickers",
        "/sdcard/WhatsApp/Media/WhatsApp Stickers"
    )
    val stickerFile = remember(fileName) {
        stickerPaths.map { java.io.File(it, fileName) }.firstOrNull { it.exists() }
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(72.dp)
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (stickerFile != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(stickerFile).crossfade(true).build(),
                    contentDescription = fileName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            } else {
                Icon(
                    Icons.Outlined.PhotoLibrary,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "(%d)".format(count),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SectionCard(
    title: String,
    subtitle: String? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    if (subtitle != null) {
                        Text(subtitle, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                    }
                }
                if (trailingContent != null) { trailingContent() }
            }
            Spacer(modifier = Modifier.height(16.dp))
            content()
        }
    }
}