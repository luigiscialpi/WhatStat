package com.whatstat.reborn.ui.screen.calls

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.automirrored.outlined.PhoneMissed
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.whatstat.reborn.data.db.model.CallStat
import com.whatstat.reborn.data.db.model.TimeRange
import com.whatstat.reborn.ui.component.ContactNameWithPhone
import com.whatstat.reborn.ui.component.DateRangePicker
import com.whatstat.reborn.ui.component.LoadingSection

private fun formatDuration(seconds: Long): String {
    val hours = seconds / 3600
    val mins = (seconds % 3600) / 60
    return if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
}

@Composable
fun CallsStatsScreen(
    chatId: Long? = null,
    viewModel: CallsStatsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val callStats by viewModel.callStats.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val selectedRange by viewModel.selectedTimeRange.collectAsState()
    val customDateRange by viewModel.customDateRange.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    LaunchedEffect(chatId) {
        viewModel.initialize(chatId)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
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
            Text(
                "Log Chiamate",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        // Filter Chips
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

        // Custom Date Range
        DateRangePicker(
            dateRange = customDateRange,
            onDateRangeChanged = { viewModel.setCustomDateRange(it) },
            onSubmitClicked = { viewModel.applyCustomDateRange() },
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
        )

        // Search bar (solo se ci sono risultati)
        if (!isLoading && callStats.isNotEmpty()) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text("Cerca contatto…") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(Icons.Outlined.Clear, contentDescription = "Cancella")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                )
            )
        }

        if (isLoading) {
            CallsStatsSkeletonContent(modifier = Modifier.fillMaxSize())
        } else if (callStats.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Nessuna chiamata trovata",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            // Calcola i totali
            val totals = remember(callStats) {
                CallStat(
                    jid = "",
                    displayName = "Riepilogo",
                    totalCalls = callStats.sumOf { it.totalCalls },
                    videoCalls = callStats.sumOf { it.videoCalls },
                    missedCalls = callStats.sumOf { it.missedCalls },
                    totalDurationSec = callStats.sumOf { it.totalDurationSec },
                    audioDurationSec = callStats.sumOf { it.audioDurationSec },
                    videoDurationSec = callStats.sumOf { it.videoDurationSec }
                )
            }

            val filteredStats = remember(callStats, searchQuery) {
                if (searchQuery.isBlank()) callStats
                else callStats.filter {
                    (it.displayName ?: it.jid).contains(searchQuery, ignoreCase = true)
                }
            }

            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // ── Riepilogo totale ──────────────────────────
                item {
                    CallSummaryCard(totals)
                }

                item {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Dettaglio",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // ── Dettaglio per persona ─────────────────────
                items(filteredStats) { stat ->
                    CallDetailCard(stat)
                }

                // Bottom spacer
                item { Spacer(Modifier.height(48.dp)) }
            }
        }
    }
}

@Composable
private fun CallSummaryCard(totals: CallStat) {
    val audioCalls = totals.totalCalls - totals.videoCalls
    val totalDur = totals.totalDurationSec.coerceAtLeast(1)
    val audioRatio = totals.audioDurationSec.toFloat() / totalDur

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                "Riepilogo",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(Modifier.height(12.dp))

            // Contatori
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Outlined.Assessment,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "${totals.totalCalls}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text("Totali", style = MaterialTheme.typography.bodySmall)
                }

                VerticalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    thickness = 2.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                )

                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.Call, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("$audioCalls", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("Audio", style = MaterialTheme.typography.bodySmall)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.Videocam, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                        Text("${totals.videoCalls}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("Video", style = MaterialTheme.typography.bodySmall)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.AutoMirrored.Outlined.PhoneMissed, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Text("${totals.missedCalls}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("Perse", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            Spacer(Modifier.height(12.dp))

            // Durate
            Text(
                "Durata totale: ${formatDuration(totals.totalDurationSec)}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(Modifier.height(8.dp))

            // Barra audio vs video
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Call, contentDescription = null, modifier = Modifier.padding(end = 4.dp), tint = MaterialTheme.colorScheme.primary)
                Text("Audio", style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(44.dp))
                LinearProgressIndicator(
                    progress = { audioRatio },
                    modifier = Modifier
                        .weight(1f)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                Text(
                    formatDuration(totals.audioDurationSec),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 8.dp).width(52.dp)
                )
            }

            Spacer(Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Videocam, contentDescription = null, modifier = Modifier.padding(end = 4.dp), tint = MaterialTheme.colorScheme.secondary)
                Text("Video", style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(44.dp))
                LinearProgressIndicator(
                    progress = { 1f - audioRatio },
                    modifier = Modifier
                        .weight(1f)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = MaterialTheme.colorScheme.secondary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                Text(
                    formatDuration(totals.videoDurationSec),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 8.dp).width(52.dp)
                )
            }
        }
    }
}

@Composable
private fun CallDetailCard(stat: CallStat) {
    val audioCalls = stat.totalCalls - stat.videoCalls

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Nome con avatar
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (stat.avatarPath != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(java.io.File(stat.avatarPath))
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Outlined.Person,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                ContactNameWithPhone(
                    name = stat.displayName ?: stat.jid.substringBefore("@"),
                    jid = stat.jid,
                    nameStyle = MaterialTheme.typography.titleMedium
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Outlined.Assessment,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "${stat.totalCalls}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text("Totali", style = MaterialTheme.typography.bodySmall)
                }

                VerticalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    thickness = 2.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                )

                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.Call, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("$audioCalls", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Audio", style = MaterialTheme.typography.bodySmall)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.Videocam, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                        Text("${stat.videoCalls}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Video", style = MaterialTheme.typography.bodySmall)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.AutoMirrored.Outlined.PhoneMissed, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Text("${stat.missedCalls}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Perse", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            Spacer(Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 4.dp))
                    Text(formatDuration(stat.totalDurationSec), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                }

                VerticalDivider(
                    modifier = Modifier.height(24.dp),
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Call, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 4.dp))
                    Text(formatDuration(stat.audioDurationSec), style = MaterialTheme.typography.bodySmall)
                }

                VerticalDivider(
                    modifier = Modifier.height(24.dp),
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Videocam, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(end = 4.dp))
                    Text(formatDuration(stat.videoDurationSec), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}