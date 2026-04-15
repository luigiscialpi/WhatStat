package com.whatstat.reborn.ui.screen.media

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.VideoFile
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
import com.whatstat.reborn.ui.component.ChartTooltip
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.whatstat.reborn.data.db.model.TimeRange
import com.whatstat.reborn.ui.component.DateRangePicker

@Composable
fun MediaStatsScreen(
    chatId: Long? = null,
    viewModel: MediaStatsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val linkStats by viewModel.linkStats.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val selectedRange by viewModel.selectedTimeRange.collectAsState()
    val customDateRange by viewModel.customDateRange.collectAsState()

    var showTooltip by remember { mutableStateOf(false) }
    var tooltipTitle by remember { mutableStateOf("") }
    var tooltipValue by remember { mutableStateOf("") }

    LaunchedEffect(chatId) {
        chatId?.let { viewModel.setChatId(it) }
    }

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
                    "Media & Link",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            // ── FILTER CHIPS ─────────────────────────────
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
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
                onDateRangeChanged = { viewModel.setCustomDateRange(it) }
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

                linkStats.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Nessun link trovato",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                else -> {

                    Text(
                        "Re dei Link",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )

                    LazyColumn(
                        contentPadding = PaddingValues(
                            horizontal = 16.dp,
                            vertical = 8.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(linkStats) { stat ->
                            Card(
                                onClick = {
                                    tooltipTitle =
                                        stat.displayName ?: stat.jid.substringBefore("@")
                                    tooltipValue =
                                        "%,d link (%,d domini)".format(
                                            stat.totalLinks,
                                            stat.uniqueDomains
                                        )
                                    showTooltip = true
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor =
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            stat.displayName
                                                ?: stat.jid.substringBefore("@"),
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Text(
                                            "${stat.uniqueDomains} domini unici",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
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
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── TOOLTIP ───────────────────────────────────
            if (showTooltip) {
                ChartTooltip(
                    title = tooltipTitle,
                    value = tooltipValue,
                    onDismiss = { showTooltip = false }
                )
            }
        }
    }
}