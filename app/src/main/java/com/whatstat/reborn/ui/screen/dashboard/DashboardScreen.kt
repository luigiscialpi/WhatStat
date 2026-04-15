package com.whatstat.reborn.ui.screen.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Call
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.whatstat.reborn.data.db.model.TimeRange
import com.whatstat.reborn.ui.component.DateRangePicker
import com.whatstat.reborn.ui.component.HourlyHeatmap
import com.whatstat.reborn.ui.component.InteractiveHourlyHeatmap
import com.whatstat.reborn.ui.component.InteractiveDailyBarChart
import com.whatstat.reborn.ui.component.ChartTooltip
import com.whatstat.reborn.ui.component.StatCard

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
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val selectedRange by viewModel.selectedTimeRange.collectAsState()
    val customDateRange by viewModel.customDateRange.collectAsState()

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
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "Caricamento statistiche...",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            error?.let { err ->
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
                                err,
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

                // ── Heatmap oraria ───────────────────────────────────
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(Modifier.padding(20.dp)) {
                            Text(
                                "Quando sei più attivo",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Distribuzione oraria dei messaggi",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(16.dp))
                            InteractiveHourlyHeatmap(
                                hourlyData = hourlyData,
                                onHourClick = { hour, count ->
                                    tooltipTitle = "Ore $hour:00 - $hour:59"
                                    tooltipValue = "%,d messaggi".format(count)
                                    showTooltip = true
                                }
                            )
                        }
                    }
                }

                // ── Heatmap settimanale ─────────────────────────────────
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(Modifier.padding(20.dp)) {
                            Text(
                                "Attività settimanale",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Distribuzione dei messaggi per giorno della settimana",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(16.dp))
                            InteractiveDailyBarChart(
                                data = weeklyData,
                                onDayClick = { day, count ->
                                    val dayNames = listOf("Dom", "Lun", "Mar", "Mer", "Gio", "Ven", "Sab")
                                    tooltipTitle = dayNames.getOrElse(day) { "Giorno $day" }
                                    tooltipValue = "%,d messaggi".format(count)
                                    showTooltip = true
                                }
                            )
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