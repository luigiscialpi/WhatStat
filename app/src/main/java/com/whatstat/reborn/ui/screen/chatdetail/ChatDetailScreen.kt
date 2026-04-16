package com.whatstat.reborn.ui.screen.chatdetail

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.whatstat.reborn.data.db.model.TimeRange
import com.whatstat.reborn.ui.component.ActivityRecapCard
import com.whatstat.reborn.ui.component.ChartTooltip
import com.whatstat.reborn.ui.component.DateRangePicker
import com.whatstat.reborn.ui.component.LoadingSection
import com.whatstat.reborn.ui.component.InteractiveHourlyHeatmap
import com.whatstat.reborn.ui.component.StatCard
import com.whatstat.reborn.ui.component.WordCloudView
import com.whatstat.reborn.ui.component.InteractiveWordCloud
import com.whatstat.reborn.ui.component.LoadingSection
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

private val dateFormatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

@Composable
fun ChatDetailScreen(
    chatId: Long,
    viewModel: ChatDetailViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToWordSearch: () -> Unit = {},
    onNavigateToCalls: () -> Unit = {},
    onNavigateToMedia: () -> Unit = {}
) {
    val stats by viewModel.chatStats.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isLoadingSlow by viewModel.isLoadingSlow.collectAsState()
    val progress by viewModel.loadingProgress.collectAsState()
    val progressSlow by viewModel.loadingProgressSlow.collectAsState()
    val selectedRange by viewModel.selectedTimeRange.collectAsState()
    val customDateRange by viewModel.customDateRange.collectAsState()
    val wordViewMode by viewModel.wordViewMode.collectAsState()
    val selectedSenderWords by viewModel.selectedSenderForWords.collectAsState()
    val topWordsLimit by viewModel.topWordsLimit.collectAsState()
    val senderTopWords by viewModel.senderTopWords.collectAsState()
    val isSenderWordsLoading by viewModel.isSenderWordsLoading.collectAsState()

    var tooltipTitle by remember { mutableStateOf("") }
    var tooltipValue by remember { mutableStateOf("") }
    var showTooltip by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // ── Header ───────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Indietro",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }

                stats?.let { s ->
                    Icon(
                        if (s.isGroup) Icons.Outlined.Group else Icons.Outlined.Person,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            s.contactName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            "%,d messaggi".format(s.totalMessages),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // ── Sticky Filter Chips ───────────────────────────────────
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
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

            // ── Custom Date Range Picker ─────────────────────────────
            DateRangePicker(
                dateRange = customDateRange,
                onDateRangeChanged = { viewModel.setCustomDateRange(it) },
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            if (isLoading) {
                LoadingSection(
                    progress = progress,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                stats?.let { s ->
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // ── Sent / Received ──────────────────────────────────
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                StatCard(
                                    modifier = Modifier.weight(1f),
                                    label = "Inviati",
                                    value = "%,d".format(s.sentByMe),
                                    icon = Icons.AutoMirrored.Filled.Send
                                )
                                StatCard(
                                    modifier = Modifier.weight(1f),
                                    label = "Ricevuti",
                                    value = "%,d".format(s.receivedFromThem),
                                    icon = Icons.Filled.CallReceived
                                )
                            }
                        }

                        // ── Distribuzione messaggi (Pie Chart) ───────────
                        item {
                            SectionCard(title = "Chi scrive di più") {
                                if (s.senderBreakdown.isNotEmpty()) {
                                    MessagePieChart(
                                        senders = s.senderBreakdown,
                                        totalMessages = s.totalMessages
                                    )
                                } else if (isLoadingSlow) {
                                    LoadingSection(progress = progressSlow)
                                } else {
                                    val otherName = if (!s.isGroup) s.contactName else "Loro"
                                    MessagePieChart(
                                        senders = listOf(
                                            com.whatstat.reborn.data.db.model.SenderStat("me", "Tu", s.sentByMe),
                                            com.whatstat.reborn.data.db.model.SenderStat("other", otherName, s.receivedFromThem)
                                        ),
                                        totalMessages = s.totalMessages
                                    )
                                }
                            }
                        }

                        // ── Info Cards ───────────────────────────────────────
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                StatCard(
                                    modifier = Modifier.weight(1f),
                                    label = "Primo messaggio",
                                    value = if (s.firstMessageDate > 0) dateFormatter.format(Date(s.firstMessageDate)) else "—",
                                    icon = Icons.Outlined.CalendarMonth,
                                    isSmallText = true
                                )
                                StatCard(
                                    modifier = Modifier.weight(1f),
                                    label = "Tempo medio risposta",
                                    value = formatDuration(s.averageResponseTimeMs),
                                    icon = Icons.Outlined.AccessTime,
                                    isSmallText = true
                                )
                            }
                        }

                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                StatCard(
                                    modifier = Modifier.weight(1f),
                                    label = "File media",
                                    value = "%,d".format(s.mediaCount),
                                    icon = Icons.Outlined.PhotoLibrary,
                                    isSmallText = true
                                )
                                StatCard(
                                    modifier = Modifier.weight(1f),
                                    label = "Lungh. media msg",
                                    value = "%.0f car.".format(s.averageMessageLength),
                                    icon = Icons.Outlined.TextFields,
                                    isSmallText = true
                                )
                            }
                        }

                        // ── Conversation Starters ────────────────────────────
                        item {
                            SectionCard(
                                title = "Chi inizia le conversazioni",
                                onInfoClick = {
                                    tooltipTitle = "Inizio Conversazione"
                                    tooltipValue = "Una nuova conversazione viene contata quando un messaggio viene inviato dopo almeno 3 ore di silenzio."
                                    showTooltip = true
                                }
                            ) {
                                if (s.conversationStarterBreakdown.isNotEmpty()) {
                                    val breakdownTotal = s.conversationStarterBreakdown.sumOf { it.count }.coerceAtLeast(1)
                                    MessagePieChart(
                                        senders = s.conversationStarterBreakdown,
                                        totalMessages = breakdownTotal,
                                        centerLabel = "conversazioni"
                                    )
                                } else if (isLoadingSlow) {
                                    LoadingSection(progress = progressSlow)
                                } else {
                                    val starterTotal = (s.iStartedConversations + s.theyStartedConversations)
                                        .coerceAtLeast(1)
                                    val otherName = if (!s.isGroup) s.contactName else "Loro"
                                    MessagePieChart(
                                        senders = listOf(
                                            com.whatstat.reborn.data.db.model.SenderStat("me", "Tu", s.iStartedConversations),
                                            com.whatstat.reborn.data.db.model.SenderStat("other", otherName, s.theyStartedConversations)
                                        ),
                                        totalMessages = starterTotal,
                                        centerLabel = "conversazioni"
                                    )
                                }
                            }
                        }

                        // ── Activity Recap Card (Tabbed) ──────────────────────────
                        item {
                            ActivityRecapCard(
                                hourlyData = s.hourlyDistribution,
                                weeklyData = s.weeklyDistribution,
                                dailyData = s.dayOfMonthDistribution,
                                monthlyData = s.monthOfYearDistribution,
                                yearlyData = s.yearTrendDistribution,
                                onPointClick = { title, value ->
                                    tooltipTitle = title
                                    tooltipValue = value
                                    showTooltip = true
                                }
                            )
                        }

                        // ── Top Words ────────────────────────────────────────
                        item {
                            SectionCard(
                                title = "Parole più usate",
                                trailingContent = {
                                    // Selettore limite (chips 5, 10, 20, 30)
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        listOf(5, 10, 20, 30).forEach { limit ->
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(
                                                        if (topWordsLimit == limit) MaterialTheme.colorScheme.primary
                                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                                    )
                                                    .clickable { viewModel.setTopWordsLimit(limit) }
                                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    limit.toString(),
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = if (topWordsLimit == limit) MaterialTheme.colorScheme.onPrimary
                                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            ) {
                                // Switcher Tutti / Per Persona
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 12.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                                        .padding(4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    val modes = listOf(
                                        ChatDetailViewModel.WordViewMode.AGGREGATED to "Tutti",
                                        ChatDetailViewModel.WordViewMode.INDIVIDUAL to "Per Persona"
                                    )
                                    modes.forEach { (mode, label) ->
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(
                                                    if (wordViewMode == mode) MaterialTheme.colorScheme.primary
                                                    else Color.Transparent
                                                )
                                                .clickable { viewModel.setWordViewMode(mode) }
                                                .padding(vertical = 6.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                label,
                                                style = MaterialTheme.typography.labelLarge,
                                                color = if (wordViewMode == mode) MaterialTheme.colorScheme.onPrimary
                                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                // Selettore partecipanti (solo se indiviuale)
                                if (wordViewMode == ChatDetailViewModel.WordViewMode.INDIVIDUAL) {
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.padding(bottom = 16.dp)
                                    ) {
                                        // Aggiungiamo 'me' esplicitamente se non c'è già? 
                                        // In realtà senderBreakdown include tutti.
                                        items(s.senderBreakdown) { sender ->
                                            FilterChip(
                                                selected = selectedSenderWords?.jid == sender.jid,
                                                onClick = { viewModel.setSelectedSenderForWords(sender) },
                                                label = { Text(sender.displayName) },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                            )
                                        }
                                    }
                                }

                                // Visualizzazione Word Cloud
                                val currentWords = if (wordViewMode == ChatDetailViewModel.WordViewMode.AGGREGATED) {
                                    s.topWords.take(topWordsLimit.toInt())
                                } else {
                                    senderTopWords
                                }

                                when {
                                    (wordViewMode == ChatDetailViewModel.WordViewMode.INDIVIDUAL && isSenderWordsLoading) ||
                                    (wordViewMode == ChatDetailViewModel.WordViewMode.AGGREGATED && isLoadingSlow && currentWords.isEmpty()) -> {
                                        LoadingSection(progress = if (wordViewMode == ChatDetailViewModel.WordViewMode.AGGREGATED) progressSlow else null)
                                    }
                                    currentWords.isEmpty() -> {
                                        Text(
                                            if (wordViewMode == ChatDetailViewModel.WordViewMode.AGGREGATED) "Nessuna parola trovata"
                                            else "Questo contatto non ha ancora scritto nulla con questi filtri",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(vertical = 24.dp)
                                        )
                                    }
                                    else -> {
                                        InteractiveWordCloud(
                                            words = currentWords,
                                            onWordClick = { word, count ->
                                                tooltipTitle = word
                                                tooltipValue = "%,d volte".format(count)
                                                showTooltip = true
                                            }
                                        )
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
                                        title = "Tutte le Chat",
                                        subtitle = "Altre conversazioni",
                                        icon = Icons.Outlined.Group,
                                        onClick = onNavigateBack
                                    )
                                }
                            }
                        }

                        // ── Bottom spacer ────────────────────────────────────
                        item { Spacer(Modifier.height(32.dp)) }

                    } // end LazyColumn
                } // end stats?.let
            } // end else
        } // end Column

        // ── Tooltip overlay (fuori dalla LazyColumn) ─────────────
        if (showTooltip) {
            ChartTooltip(
                title = tooltipTitle,
                value = tooltipValue,
                onDismiss = { showTooltip = false }
            )
        }

    } // end Box
}

@Composable
private fun SectionCard(
    title: String,
    trailingContent: (@Composable () -> Unit)? = null,
    onInfoClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (onInfoClick != null) {
                        IconButton(
                            onClick = onInfoClick,
                            modifier = Modifier.size(24.dp).padding(start = 4.dp)
                        ) {
                            Icon(
                                Icons.Outlined.Info,
                                contentDescription = "Informazioni",
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
                trailingContent?.invoke()
            }
            Spacer(Modifier.height(16.dp))
            content()
        }
    }
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

private fun formatDuration(ms: Long): String {
    if (ms <= 0) return "—"
    val hours = TimeUnit.MILLISECONDS.toHours(ms)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes} min"
        else -> "<1 min"
    }
}

// ── Pie Chart messaggi per mittente ──────────────────────────────────────────

private val pieColors = listOf(
    Color(0xFF6750A4), // primary-like
    Color(0xFF625B71), // secondary-like
    Color(0xFF7D5260), // tertiary-like
    Color(0xFF006D40),
    Color(0xFFBA1A1A),
    Color(0xFF3F51B5),
    Color(0xFFFF9800),
    Color(0xFF009688),
    Color(0xFFE91E63),
    Color(0xFF8BC34A),
    Color(0xFF795548),
    Color(0xFF607D8B)
)

@Composable
private fun MessagePieChart(
    senders: List<com.whatstat.reborn.data.db.model.SenderStat>,
    totalMessages: Int,
    centerLabel: String = "messaggi"
) {
    val total = totalMessages.coerceAtLeast(1)
    var expanded by remember { mutableStateOf(false) }
    val showExpand = senders.size > 10

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ── Donut Chart ──
            Box(
                modifier = Modifier.size(140.dp),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.foundation.Canvas(
                    modifier = Modifier.size(140.dp)
                ) {
                    val strokeWidth = 28.dp.toPx()
                    val arcSize = size.minDimension - strokeWidth
                    val topLeft = androidx.compose.ui.geometry.Offset(
                        (size.width - arcSize) / 2f,
                        (size.height - arcSize) / 2f
                    )
                    val arcRect = androidx.compose.ui.geometry.Size(arcSize, arcSize)
                    var startAngle = -90f

                    senders.forEachIndexed { index, sender ->
                        val sweep = (sender.count.toFloat() / total) * 360f
                        drawArc(
                            color = pieColors[index % pieColors.size],
                            startAngle = startAngle,
                            sweepAngle = sweep,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcRect,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(
                                width = strokeWidth,
                                cap = androidx.compose.ui.graphics.StrokeCap.Butt
                            )
                        )
                        startAngle += sweep
                    }
                }
                // Totale al centro
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "%,d".format(totalMessages),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        centerLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.width(16.dp))

            // ── Legenda ──
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val displayCount = if (expanded) senders.size else minOf(senders.size, 10)
                senders.take(displayCount).forEachIndexed { index, sender ->
                    val pct = (sender.count.toFloat() / total * 100)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(pieColors[index % pieColors.size])
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            sender.displayName,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        Text(
                            "%,d".format(sender.count),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "(%.0f%%)".format(pct),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // ── Toggle "Mostra tutti" / "Mostra meno" ──
        if (showExpand) {
            Text(
                text = if (expanded) "Mostra meno" else "Mostra tutti (${senders.size})",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(top = 8.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}