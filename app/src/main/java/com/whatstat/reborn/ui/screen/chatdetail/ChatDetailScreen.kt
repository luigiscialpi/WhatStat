package com.whatstat.reborn.ui.screen.chatdetail

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.whatstat.reborn.domain.model.ChatStats
import com.whatstat.reborn.ui.component.HourlyHeatmap
import com.whatstat.reborn.ui.component.StatCard
import com.whatstat.reborn.ui.component.WordCloudView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@Composable
fun ChatDetailScreen(
    chatId: Long,
    viewModel: ChatDetailViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val stats by viewModel.chatStats.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

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

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
            stats?.let { s ->
                ChatDetailContent(s)
            }
        }
    }
}

@Composable
private fun ChatDetailContent(stats: ChatStats) {
    val dateFormatter = SimpleDateFormat("d MMM yyyy", Locale.ITALIAN)

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── Overview Cards ───────────────────────────────────────
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    label = "Inviati",
                    value = "%,d".format(stats.sentByMe),
                    icon = Icons.AutoMirrored.Filled.Send
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    label = "Ricevuti",
                    value = "%,d".format(stats.receivedFromThem),
                    icon = Icons.Filled.CallReceived
                )
            }
        }

        // ── Sent Ratio Bar ───────────────────────────────────────
        item {
            SectionCard(title = "Rapporto invio/ricezione") {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Tu: %.0f%%".format(stats.sentRatio * 100),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            "Loro: %.0f%%".format((1 - stats.sentRatio) * 100),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { stats.sentRatio },
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

        // ── Info Cards ───────────────────────────────────────────
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    label = "Primo messaggio",
                    value = if (stats.firstMessageDate > 0) dateFormatter.format(Date(stats.firstMessageDate)) else "—",
                    icon = Icons.Outlined.CalendarMonth,
                    isSmallText = true
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    label = "Tempo medio risposta",
                    value = formatDuration(stats.averageResponseTimeMs),
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
                    value = "%,d".format(stats.mediaCount),
                    icon = Icons.Outlined.PhotoLibrary
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    label = "Lungh. media msg",
                    value = "%.0f car.".format(stats.averageMessageLength),
                    icon = Icons.Outlined.TextFields,
                    isSmallText = true
                )
            }
        }

        // ── Conversation Starters ────────────────────────────────
        item {
            SectionCard(title = "Chi inizia le conversazioni") {
                val total = (stats.iStartedConversations + stats.theyStartedConversations)
                    .coerceAtLeast(1)
                val myRatio = stats.iStartedConversations.toFloat() / total

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Tu: ${stats.iStartedConversations}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "Loro: ${stats.theyStartedConversations}",
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

        // ── Hourly Heatmap ───────────────────────────────────────
        item {
            SectionCard(title = "Attività oraria") {
                HourlyHeatmap(hourlyData = stats.hourlyDistribution)
            }
        }

        // ── Weekly Distribution ──────────────────────────────────
        item {
            SectionCard(title = "Attività settimanale") {
                WeeklyDistribution(stats.weeklyDistribution)
            }
        }

        // ── Top Words ────────────────────────────────────────────
        if (stats.topWords.isNotEmpty()) {
            item {
                SectionCard(title = "Parole più usate") {
                    WordCloudView(words = stats.topWords.take(30))
                }
            }
        }

        // Spacer finale
        item { Spacer(Modifier.height(32.dp)) }
    }
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

@Composable
private fun WeeklyDistribution(
    weeklyData: List<com.whatstat.reborn.data.db.model.DayCount>
) {
    val dayNames = listOf("Dom", "Lun", "Mar", "Mer", "Gio", "Ven", "Sab")
    val maxCount = weeklyData.maxOfOrNull { it.count }?.toFloat() ?: 1f

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        dayNames.forEachIndexed { index, dayName ->
            val count = weeklyData.find { it.dayOfWeek == index }?.count ?: 0
            val ratio = count / maxCount

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Bar
                Box(
                    modifier = Modifier
                        .width(28.dp)
                        .height(60.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    // Background
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                    )
                    // Fill
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height((60 * ratio).dp.coerceAtLeast(4.dp))
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f + ratio * 0.7f))
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    dayName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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
