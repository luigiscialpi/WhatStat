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
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.whatstat.reborn.data.db.model.DomainStat
import com.whatstat.reborn.data.db.model.LinkStat
import com.whatstat.reborn.data.db.model.TimeRange
import com.whatstat.reborn.ui.component.DateRangePicker

@OptIn(ExperimentalMaterial3Api::class)
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
    val searchQuery by viewModel.searchQuery.collectAsState()
    val topDomains by viewModel.topDomains.collectAsState()
    val contactDomainStats by viewModel.contactDomainStats.collectAsState()
    val isDomainLoading by viewModel.isDomainLoading.collectAsState()

    val sheetState = rememberModalBottomSheetState()
    var showDebug by remember { mutableStateOf(false) }

    LaunchedEffect(chatId) {
        viewModel.initialize(chatId)
    }

    val isChatDetail = chatId != null
    val allDisplayStats = if (isChatDetail) linkStats else linkStatsBySender.ifEmpty { linkStats }
    val displayStats = remember(allDisplayStats, searchQuery) {
        if (searchQuery.isBlank()) allDisplayStats
        else allDisplayStats.filter {
            (it.displayName ?: it.jid).contains(searchQuery, ignoreCase = true)
        }
    }
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

            // ── SEARCH BAR ───────────────────────────────
            if (!isLoading && allDisplayStats.isNotEmpty()) {
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
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        // Torna indietro (solo breakdown attivo)
                        if (isShowingBreakdown) {
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

                        // Top domains
                        if (topDomains.isNotEmpty() && !isShowingBreakdown) {
                            item {
                                Text(
                                    "Domini più condivisi",
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    contentPadding = PaddingValues(bottom = 16.dp)
                                ) {
                                    items(topDomains.take(8)) { domain ->
                                        DomainChip(domain)
                                    }
                                }
                            }
                        }

                        item {
                            Text(
                                when {
                                    isShowingBreakdown -> "Dettaglio per contatto"
                                    isChatDetail -> "Classifica link per persona"
                                    else -> "Re dei Link"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }

                        items(displayStats) { stat ->
                            LinkStatCard(
                                stat = stat,
                                showTotal = true,
                                onClick = if (isChatDetail) null else {
                                    { viewModel.loadLinkStatsBySenderForJid(stat.jid) }
                                },
                                onLinksClick = {
                                    viewModel.loadDomainStatsForContact(stat.jid, stat.displayName)
                                }
                            )
                        }

                        item { Spacer(Modifier.height(48.dp)) }
                    }
                }
            }
        } // fine Column

        // ── DOMAIN ANALYSIS BOTTOM SHEET ──────────────
        if (contactDomainStats != null) {
            ModalBottomSheet(
                onDismissRequest = { viewModel.dismissContactDomains() },
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                dragHandle = {
                    Box(
                        modifier = Modifier
                            .padding(vertical = 12.dp)
                            .width(32.dp)
                            .height(4.dp)
                            .background(
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                RoundedCornerShape(2.dp)
                            )
                    )
                }
            ) {
                DomainBreakdownSheet(
                    contactName = contactDomainStats?.first ?: "",
                    domains = contactDomainStats?.second ?: emptyList(),
                    isLoading = isDomainLoading
                )
            }
        }

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
    onClick: (() -> Unit)? = null,
    onLinksClick: () -> Unit
) {
    val cardColors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    )
    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            colors = cardColors,
            shape = RoundedCornerShape(12.dp)
        ) {
            LinkStatCardContent(stat, onLinksClick)
        }
    } else {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = cardColors,
            shape = RoundedCornerShape(12.dp)
        ) {
            LinkStatCardContent(stat, onLinksClick)
        }
    }
}

@Composable
private fun LinkStatCardContent(stat: LinkStat, onLinksClick: () -> Unit) {
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
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(56.dp)
            ) {
                Icon(
                    Icons.Outlined.PhotoLibrary,
                    contentDescription = null,
                    tint = if (stat.mediaCount > 0) MaterialTheme.colorScheme.secondary
                           else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                )
                Text(
                    "${stat.mediaCount}",
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (stat.mediaCount > 0) MaterialTheme.colorScheme.secondary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                )
                Text(
                    "media",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(64.dp)
                    .clickable { onLinksClick() }
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(vertical = 4.dp)
            ) {
                Icon(
                    Icons.Outlined.Link,
                    contentDescription = null,
                    tint = if (stat.totalLinks > 0) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                )
                Text(
                    "${stat.totalLinks}",
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (stat.totalLinks > 0) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
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

// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DomainChip(stat: DomainStat) {
    Box(
        modifier = Modifier
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                RoundedCornerShape(16.dp)
            )
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
                RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stat.domain,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "${stat.count}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DomainBreakdownSheet(
    contactName: String,
    domains: List<DomainStat>,
    isLoading: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp)
    ) {
        Text(
            "Analisi Link",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            contactName,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(24.dp))
        when {
            isLoading && domains.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            domains.isEmpty() -> {
                Text(
                    "Nessuna statistica di dominio disponibile.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            else -> {
                val maxCount = domains.maxOf { it.count }.coerceAtLeast(1)
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    domains.take(10).forEach { stat ->
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    stat.domain,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    "${stat.count}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { stat.count.toFloat() / maxCount },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                            )
                        }
                    }
                }
            }
        }
    }
}