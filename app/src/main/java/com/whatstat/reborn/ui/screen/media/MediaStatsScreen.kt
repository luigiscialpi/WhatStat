package com.whatstat.reborn.ui.screen.media

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.PhotoLibrary
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.whatstat.reborn.data.db.model.LinkStat
import com.whatstat.reborn.data.db.model.TimeRange
import com.whatstat.reborn.ui.component.DateRangePicker

@Composable
fun MediaStatsScreen(
    chatId: Long? = null,
    viewModel: MediaStatsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val linkStats by viewModel.linkStats.collectAsState()
    val linkStatsBySender by viewModel.linkStatsBySender.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val selectedRange by viewModel.selectedTimeRange.collectAsState()
    val customDateRange by viewModel.customDateRange.collectAsState()
    val debugInfo by viewModel.debugInfo.collectAsState()

    var showTooltip by remember { mutableStateOf(false) }
    var tooltipTitle by remember { mutableStateOf("") }
    var tooltipStats by remember { mutableStateOf<List<Pair<String, Int>>>(emptyList()) }
    var tooltipTotal by remember { mutableStateOf(0) }
    var showDebug by remember { mutableStateOf(false) }

    LaunchedEffect(chatId) {
        if (chatId != null) {
            viewModel.setChatId(chatId)
        }
    }

    val isChatDetail = chatId != null
    // Per chat detail, linkStats contiene già lo split per mittente
    // Per vista globale, mostra linkStats (per chat) e linkStatsBySender (dopo click)
    val displayStats = if (isChatDetail) linkStats else linkStatsBySender.ifEmpty { linkStats }
    val isShowingBreakdown = !isChatDetail && linkStatsBySender.isNotEmpty()

    Box(modifier = Modifier.fillMaxSize()) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {

            // ── HEADER ─────────────────────────────────────
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
                    if (isChatDetail) "Media & Link nella chat" else "Media & Link",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { showDebug = !showDebug }) {
                    Icon(
                        Icons.Outlined.BugReport,
                        contentDescription = "Debug",
                        tint = if (showDebug) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            // ── FILTER CHIPS ─────────────────────────────
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

            // ── DATE RANGE ───────────────────────────────
            DateRangePicker(
                dateRange = customDateRange,
                onDateRangeChanged = { viewModel.setCustomDateRange(it) },
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            // ── LOADING / EMPTY / LIST ───────────────────
            when {
                isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                displayStats.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Nessun link o media trovato",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                else -> {
                    Text(
                        if (isChatDetail) "Media & Link per persona" else "Re dei Link",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )

                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (isShowingBreakdown) {
                            // Show back button row (solo vista globale)
                            item {
                                Card(
                                    onClick = { viewModel.clearSelection() },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "← Torna a tutti i link",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }
                        }
                        items(displayStats) { stat ->
                            LinkStatCard(
                                stat = stat,
                                showTotal = true,
                                onClick = if (isChatDetail) null else {{
                                    viewModel.loadLinkStatsBySenderForJid(stat.jid)
                                }}
                            )
                        }
                        // Bottom spacer
                        item { Spacer(Modifier.height(48.dp)) }
                    }
                }
            }
        } // fine Column

        // ── DEBUG PANEL overlay ───────────────────────
        if (showDebug) {
            DebugPanel(
                linkStats = linkStats,
                linkStatsBySender = linkStatsBySender,
                chatId = chatId,
                debugInfo = debugInfo,
                onDismiss = { showDebug = false }
            )
        }

    } // fine Box
}

// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LinkStatCard(
    stat: LinkStat,
    showTotal: Boolean,
    onClick: (() -> Unit)? = null
) {
    val cardModifier = Modifier.fillMaxWidth()
    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = cardModifier,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            LinkStatCardContent(stat)
        }
    } else {
        Card(
            modifier = cardModifier,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            LinkStatCardContent(stat)
        }
    }
}

@Composable
private fun LinkStatCardContent(stat: LinkStat) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                stat.displayName ?: stat.jid.substringBefore("@"),
                style = MaterialTheme.typography.titleMedium
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (stat.mediaCount > 0) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Outlined.PhotoLibrary,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        "${stat.mediaCount}",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        "media",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (stat.totalLinks > 0) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Outlined.Link,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "${stat.totalLinks}",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "link",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TooltipWithBreakdown(
    title: String,
    total: Int,
    stats: List<Pair<String, Int>>,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(onClick = onDismiss)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Totale: $total link",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Dettaglio:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (stats.isEmpty()) {
                    Text(
                        "Caricamento...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    stats.forEach { (name, count) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(name, style = MaterialTheme.typography.bodySmall)
                            Text(
                                "$count",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Tap per chiudere",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DebugPanel(
    linkStats: List<LinkStat>,
    linkStatsBySender: List<LinkStat>,
    chatId: Long?,
    debugInfo: String,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.95f))
            .statusBarsPadding()
            .padding(16.dp)
    ) {
        SelectionContainer {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("DEBUG INFO", style = MaterialTheme.typography.titleMedium, color = Color.Green)
                    Text(
                        "✕ CHIUDI",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.Red,
                        modifier = Modifier
                            .clickable(onClick = onDismiss)
                            .padding(8.dp)
                    )
                }
            Spacer(modifier = Modifier.height(8.dp))
            Text("chatId: $chatId", style = MaterialTheme.typography.bodySmall, color = Color.Yellow)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "=== linkStats (${linkStats.size}) ===",
                style = MaterialTheme.typography.labelMedium,
                color = Color.Cyan
            )
            if (linkStats.isEmpty()) {
                Text("EMPTY", style = MaterialTheme.typography.bodySmall, color = Color.Red)
            } else {
                linkStats.take(5).forEach { stat ->
                    Text(
                        "${stat.jid} -> ${stat.totalLinks}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White
                    )
                }
                if (linkStats.size > 5) {
                    Text(
                        "... e altri ${linkStats.size - 5}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "=== linkStatsBySender (${linkStatsBySender.size}) ===",
                style = MaterialTheme.typography.labelMedium,
                color = Color.Cyan
            )
            if (linkStatsBySender.isEmpty()) {
                Text("EMPTY", style = MaterialTheme.typography.bodySmall, color = Color.Red)
            } else {
                linkStatsBySender.forEach { stat ->
                    Text(
                        "${stat.displayName ?: stat.jid} -> ${stat.totalLinks}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "=== DEBUG INFO ===",
                style = MaterialTheme.typography.labelMedium,
                color = Color.Cyan
            )
            Text(
                debugInfo.ifEmpty { "(empty - tap a card to load breakdown)" },
                style = MaterialTheme.typography.bodySmall,
                color = Color.Yellow
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Testo selezionabile - long press per copiare",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            }
        }
    }
}