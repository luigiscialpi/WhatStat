package com.whatstat.reborn.ui.screen.chatdetail

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
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.whatstat.reborn.data.db.model.TimeRange
import com.whatstat.reborn.ui.component.ChartTooltip
import com.whatstat.reborn.ui.component.DateRangePicker
import com.whatstat.reborn.ui.component.InteractiveDailyBarChart
import com.whatstat.reborn.ui.component.InteractiveHourlyHeatmap
import com.whatstat.reborn.ui.component.StatCard
import com.whatstat.reborn.ui.component.WordCloudView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

private val dateFormatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

@Composable
fun ChatDetailScreen(
    chatId: Long,
    viewModel: ChatDetailViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val stats by viewModel.chatStats.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val selectedRange by viewModel.selectedTimeRange.collectAsState()
    val customDateRange by viewModel.customDateRange.collectAsState()

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
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
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

                        // ── Send/Receive Ratio ───────────────────────────────
                        item {
                            SectionCard(title = "Rapporto invio/ricezione") {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "Tu: %.0f%%".format(s.sentRatio * 100),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        "Loro: %.0f%%".format((1 - s.sentRatio) * 100),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.secondary,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { s.sentRatio },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(10.dp)
                                        .clip(RoundedCornerShape(5.dp)),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)
                                )
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
                                    icon = Icons.Outlined.PhotoLibrary
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
                            SectionCard(title = "Chi inizia le conversazioni") {
                                val total = (s.iStartedConversations + s.theyStartedConversations)
                                    .coerceAtLeast(1)
                                val myRatio = s.iStartedConversations.toFloat() / total

                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            "Tu: ${s.iStartedConversations}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            "Loro: ${s.theyStartedConversations}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    LinearProgressIndicator(
                                        progress = { myRatio },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(10.dp)
                                            .clip(RoundedCornerShape(5.dp)),
                                        color = MaterialTheme.colorScheme.primary,
                                        trackColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)
                                    )
                                }
                            }
                        }

                        // ── Hourly Heatmap ───────────────────────────────────
                        item {
                            SectionCard(title = "Attività oraria") {
                                InteractiveHourlyHeatmap(
                                    hourlyData = s.hourlyDistribution,
                                    onHourClick = { hour, count ->
                                        tooltipTitle = "Ore $hour:00 - $hour:59"
                                        tooltipValue = "%,d messaggi".format(count)
                                        showTooltip = true
                                    }
                                )
                            }
                        }

                        // ── Weekly Distribution ──────────────────────────────
                        item {
                            SectionCard(title = "Attività settimanale") {
                                InteractiveDailyBarChart(
                                    data = s.weeklyDistribution,
                                    onDayClick = { day, count ->
                                        val dayNames = listOf(
                                            "Domenica", "Lunedì", "Martedì",
                                            "Mercoledì", "Giovedì", "Venerdì", "Sabato"
                                        )
                                        tooltipTitle = dayNames.getOrElse(day) { "Giorno $day" }
                                        tooltipValue = "%,d messaggi".format(count)
                                        showTooltip = true
                                    }
                                )
                            }
                        }

                        // ── Top Words ────────────────────────────────────────
                        if (s.topWords.isNotEmpty()) {
                            item {
                                SectionCard(title = "Parole più usate") {
                                    WordCloudView(words = s.topWords.take(30))
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
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(16.dp))
            content()
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