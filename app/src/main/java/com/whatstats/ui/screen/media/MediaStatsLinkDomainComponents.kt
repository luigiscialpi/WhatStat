package com.whatstats.ui.screen.media

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.whatstats.R
import com.whatstats.data.db.model.DomainStat
import com.whatstats.data.db.model.LinkStat
import com.whatstats.telemetry.TrackOverlayView
import com.whatstats.ui.component.AnimatedInfoButton
import com.whatstats.ui.component.ContactNameWithPhone
import com.whatstats.ui.component.LoadingSection
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
@Suppress("FunctionName")
internal fun LinkStatCard(
    stat: LinkStat,
    showTotal: Boolean,
    onClick: (() -> Unit)? = null,
    onLinksClick: () -> Unit,
    onMediaClick: (() -> Unit)? = null,
) {
    val cardColors =
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        )
    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            colors = cardColors,
            shape = RoundedCornerShape(12.dp),
        ) {
            LinkStatCardContent(stat, onLinksClick, onMediaClick)
        }
    } else {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = cardColors,
            shape = RoundedCornerShape(12.dp),
        ) {
            LinkStatCardContent(stat, onLinksClick, onMediaClick)
        }
    }
}

@Composable
@Suppress("FunctionName")
private fun LinkStatCardContent(
    stat: LinkStat,
    onLinksClick: () -> Unit,
    onMediaClick: (() -> Unit)? = null,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Avatar + nome
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (stat.avatarPath != null) {
                AsyncImage(
                    model =
                        ImageRequest.Builder(LocalContext.current)
                            .data(java.io.File(stat.avatarPath))
                            .crossfade(true)
                            .build(),
                    contentDescription = null,
                    modifier =
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
            } else {
                val isStories = stat.jid == "status@broadcast"
                Box(
                    modifier =
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                if (isStories) {
                                    MaterialTheme.colorScheme.tertiaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (isStories) Icons.Outlined.Visibility else Icons.Outlined.Person,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint =
                            if (isStories) {
                                MaterialTheme.colorScheme.onTertiaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            val isStories = stat.jid == "status@broadcast"
            ContactNameWithPhone(
                name = stat.displayName ?: stat.jid.substringBefore("@"),
                jid = stat.jid,
                nameStyle =
                    MaterialTheme.typography.titleMedium.copy(
                        fontWeight = if (isStories) androidx.compose.ui.text.font.FontWeight.Bold else null,
                    ),
            )
        }
        Spacer(Modifier.width(12.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier =
                    Modifier
                        .width(64.dp)
                        .then(
                            if (onMediaClick != null && stat.mediaCount > 0) {
                                Modifier.clickable { onMediaClick() }
                            } else {
                                Modifier
                            },
                        )
                        .background(
                            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.05f),
                            shape = RoundedCornerShape(8.dp),
                        )
                        .padding(vertical = 4.dp),
            ) {
                Icon(
                    Icons.Outlined.PhotoLibrary,
                    contentDescription = null,
                    tint =
                        if (stat.mediaCount > 0) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        },
                )
                Text(
                    "${stat.mediaCount}",
                    style = MaterialTheme.typography.headlineSmall,
                    color =
                        if (stat.mediaCount > 0) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        },
                )
                Text(
                    stringResource(R.string.media_label),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier =
                    Modifier
                        .width(64.dp)
                        .clickable { onLinksClick() }
                        .background(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
                            shape = RoundedCornerShape(8.dp),
                        )
                        .padding(vertical = 4.dp),
            ) {
                Icon(
                    Icons.Outlined.Link,
                    contentDescription = null,
                    tint =
                        if (stat.totalLinks > 0) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        },
                )
                Text(
                    "${stat.totalLinks}",
                    style = MaterialTheme.typography.headlineSmall,
                    color =
                        if (stat.totalLinks > 0) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        },
                )
                Text(
                    stringResource(R.string.link_label),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────

@Composable
@Suppress("FunctionName")
internal fun DomainChip(stat: DomainStat) {
    Box(
        modifier =
            Modifier
                .background(
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                    RoundedCornerShape(16.dp),
                )
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
                    RoundedCornerShape(16.dp),
                )
                .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stat.domain,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "${stat.count}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────

@Composable
@Suppress("FunctionName")
internal fun DomainBreakdownSheet(
    contactName: String,
    domains: List<DomainStat>,
    isLoading: Boolean,
    avatarPath: String? = null,
    progress: Int? = null,
) {
    TrackOverlayView("media_domains")
    var expandedDomain by remember { mutableIntStateOf(-1) }
    var expandedPreviewUrl by remember { mutableStateOf<String?>(null) }
    val previewCache = remember { mutableStateMapOf<String, LinkPreviewCacheEntry>() }
    val uriHandler = LocalUriHandler.current

    val dateFormatter = remember { SimpleDateFormat("dd/MM/yy", Locale.ITALIAN) }

    // Stato globale
    var globalSortMode by remember { mutableIntStateOf(0) } // 0 = Più recenti, 1 = Meno recenti

    val availableTimestamps =
        remember(domains) {
            domains
                .flatMap { it.topUrls }
                .mapNotNull { it.lastTimestamp }
                .distinct()
                .sorted()
        }

    val maxTimestampIndex = remember(availableTimestamps) { availableTimestamps.lastIndex.coerceAtLeast(0) }
    val hasRangeSlider = availableTimestamps.size >= 2
    var dateRangeIndices by remember(availableTimestamps) {
        mutableStateOf(0f..maxTimestampIndex.toFloat())
    }

    val selectedStartTimestamp =
        remember(availableTimestamps, dateRangeIndices, maxTimestampIndex) {
            if (availableTimestamps.isEmpty()) {
                0L
            } else {
                val idx = dateRangeIndices.start.roundToInt().coerceIn(0, maxTimestampIndex)
                availableTimestamps[idx]
            }
        }
    val selectedEndTimestamp =
        remember(availableTimestamps, dateRangeIndices, maxTimestampIndex) {
            if (availableTimestamps.isEmpty()) {
                0L
            } else {
                val idx = dateRangeIndices.endInclusive.roundToInt().coerceIn(0, maxTimestampIndex)
                availableTimestamps[idx]
            }
        }

    val filteredDomains =
        remember(domains, selectedStartTimestamp, selectedEndTimestamp) {
            if (availableTimestamps.isEmpty()) return@remember domains
            domains.mapNotNull { stat ->
                val filteredUrls =
                    stat.topUrls.filter { urlFreq ->
                        val ts = urlFreq.lastTimestamp ?: 0L
                        ts in selectedStartTimestamp..selectedEndTimestamp
                    }
                if (filteredUrls.isEmpty()) {
                    null
                } else {
                    stat.copy(
                        topUrls = filteredUrls,
                        count = filteredUrls.sumOf { it.count },
                    )
                }
            }.sortedByDescending { it.count }
        }

    val scrollState = rememberScrollState()

    LaunchedEffect(globalSortMode) {
        scrollState.animateScrollTo(0)
    }

    LaunchedEffect(expandedDomain) {
        expandedPreviewUrl = null
    }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(scrollState),
    ) {
        var showInfoDialog by remember { mutableStateOf(false) }

        // Header con avatar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (avatarPath != null) {
                AsyncImage(
                    model =
                        ImageRequest.Builder(LocalContext.current)
                            .data(java.io.File(avatarPath))
                            .crossfade(true)
                            .build(),
                    contentDescription = null,
                    modifier =
                        Modifier
                            .size(48.dp)
                            .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
                Spacer(Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(id = R.string.domain_analysis_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    contactName,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            AnimatedInfoButton(
                onClick = { showInfoDialog = true },
                modifier = Modifier.size(32.dp),
                contentDescription = stringResource(R.string.link_info_title),
                iconSize = 18.dp,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                glowColor = MaterialTheme.colorScheme.primary,
            )

            if (showInfoDialog) {
                AlertDialog(
                    onDismissRequest = { showInfoDialog = false },
                    title = { Text(stringResource(R.string.link_info_title)) },
                    text = { Text(stringResource(R.string.link_info_text)) },
                    confirmButton = {
                        TextButton(onClick = { showInfoDialog = false }) {
                            Text(stringResource(R.string.ho_capito))
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        when {
            isLoading && domains.isEmpty() -> {
                LoadingSection(progress = progress)
            }
            filteredDomains.isEmpty() -> {
                Text(
                    stringResource(R.string.domain_no_stats),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> {
                val maxCount = filteredDomains.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1
                val totalLinksCount = filteredDomains.sumOf { it.count }

                // Controlli globali: slider a sinistra, pulsante ordinamento a destra
                Spacer(Modifier.height(8.dp))
                if (hasRangeSlider) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                        ) {
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    dateFormatter.format(Date(selectedStartTimestamp)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    dateFormatter.format(Date(selectedEndTimestamp)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            androidx.compose.material3.RangeSlider(
                                value = dateRangeIndices,
                                onValueChange = { newRange ->
                                    val oldRange = dateRangeIndices
                                    val startDelta = abs(newRange.start - oldRange.start)
                                    val endDelta = abs(newRange.endInclusive - oldRange.endInclusive)
                                    val movingStartThumb = startDelta >= endDelta

                                    if (movingStartThumb) {
                                        val snappedStart =
                                            newRange.start
                                                .coerceIn(0f, oldRange.endInclusive)
                                                .roundToInt()
                                                .coerceIn(0, maxTimestampIndex)
                                                .toFloat()
                                        dateRangeIndices = snappedStart..oldRange.endInclusive
                                    } else {
                                        val snappedEnd =
                                            newRange.endInclusive
                                                .coerceIn(oldRange.start, maxTimestampIndex.toFloat())
                                                .roundToInt()
                                                .coerceIn(0, maxTimestampIndex)
                                                .toFloat()
                                        dateRangeIndices = oldRange.start..snappedEnd
                                    }

                                    if (dateRangeIndices.start > dateRangeIndices.endInclusive) {
                                        dateRangeIndices = oldRange
                                    }
                                },
                                valueRange = 0f..maxTimestampIndex.toFloat(),
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        // Consuma eventi pointer DOPO che il slider (figlio) li elabora,
                                        // cosi il slider funziona normalmente ma gli eventi non si propagano al
                                        // gesture handler AnchoredDraggable del ModalBottomSheet.
                                        .pointerInput(Unit) {
                                            awaitEachGesture {
                                                awaitFirstDown(requireUnconsumed = false)
                                                do {
                                                    val event = awaitPointerEvent()
                                                    event.changes.forEach { it.consume() }
                                                } while (event.changes.any { it.pressed })
                                            }
                                        },
                            )
                        }

                        Spacer(Modifier.width(8.dp))

                        // Pulsante ordinamento (destra) - allineato con l'altezza della barra slider
                        val sortRotation by animateFloatAsState(
                            targetValue = if (globalSortMode == 1) 180f else 0f,
                            label = "sortRotation",
                        )
                        // Allinea con l'altezza della barra slider
                        Box(
                            modifier = Modifier.padding(bottom = 4.dp),
                        ) {
                            TextButton(
                                onClick = { globalSortMode = if (globalSortMode == 0) 1 else 0 },
                                enabled = expandedDomain != -1,
                            ) {
                                Icon(
                                    Icons.Outlined.SwapVert,
                                    contentDescription = null,
                                    modifier = Modifier.rotate(sortRotation),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    if (globalSortMode == 1) {
                                        stringResource(R.string.sort_oldest)
                                    } else {
                                        stringResource(R.string.sort_newest)
                                    },
                                )
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Pulsante ordinamento (destra)
                        val sortRotation by animateFloatAsState(
                            targetValue = if (globalSortMode == 1) 180f else 0f,
                            label = "sortRotation",
                        )
                        TextButton(
                            onClick = { globalSortMode = if (globalSortMode == 0) 1 else 0 },
                            enabled = expandedDomain != -1,
                        ) {
                            Icon(
                                Icons.Outlined.SwapVert,
                                contentDescription = null,
                                modifier = Modifier.rotate(sortRotation),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                if (globalSortMode == 1) {
                                    stringResource(R.string.sort_oldest)
                                } else {
                                    stringResource(R.string.sort_newest)
                                },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    filteredDomains.forEachIndexed { index, stat ->
                        val isExpanded = expandedDomain == index
                        val percentage = if (totalLinksCount > 0) (stat.count.toFloat() / totalLinksCount * 100).toInt() else 0

                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isExpanded) {
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                        } else {
                                            Color.Transparent
                                        },
                                    )
                                    .animateContentSize()
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                        ) {
                            // Riga per dominio (cliccabile)
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            expandedDomain = if (expandedDomain == index) -1 else index
                                        }
                                        .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Box(
                                        modifier =
                                            Modifier
                                                .size(20.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        AsyncImage(
                                            model =
                                                ImageRequest.Builder(LocalContext.current)
                                                    .data("https://www.google.com/s2/favicons?sz=64&domain=${stat.domain}")
                                                    .crossfade(true)
                                                    .build(),
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            contentScale = ContentScale.Fit,
                                            error = androidx.compose.ui.res.painterResource(id = android.R.drawable.ic_menu_compass),
                                        )
                                    }

                                    Spacer(Modifier.width(8.dp))

                                    Text(
                                        stat.domain,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f, fill = false),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )

                                    if (stat.topUrls.isNotEmpty()) {
                                        Icon(
                                            if (isExpanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        "${stat.count}",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    Text(
                                        "$percentage%",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }

                            Spacer(Modifier.height(4.dp))

                            LinearProgressIndicator(
                                progress = { stat.count.toFloat() / maxCount },
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .height(6.dp),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                            )

                            // Se è espanso, mostriamo i link internamente
                            if (isExpanded && stat.topUrls.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))

                                val sortedUrls =
                                    when (globalSortMode) {
                                        1 -> stat.topUrls.sortedBy { it.lastTimestamp ?: 0L }
                                        else -> stat.topUrls.sortedByDescending { it.lastTimestamp ?: 0L }
                                    }

                                val accordionListState = rememberLazyListState()

                                // Scroll in cima quando cambia la modalita di ordinamento globale
                                LaunchedEffect(globalSortMode) {
                                    accordionListState.scrollToItem(0)
                                }

                                LazyColumn(
                                    state = accordionListState,
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 240.dp)
                                            .nestedScroll(
                                                // Consuma scroll e fling cosi il BottomSheet
                                                // non puo trascinare mentre l'utente e dentro la lista accordion
                                                object : NestedScrollConnection {
                                                    override fun onPreScroll(
                                                        available: Offset,
                                                        source: NestedScrollSource,
                                                    ): Offset = Offset.Zero

                                                    override fun onPostScroll(
                                                        consumed: Offset,
                                                        available: Offset,
                                                        source: NestedScrollSource,
                                                    ): Offset = available

                                                    // Consuma la velocita di fling residua cosi non si
                                                    // propaga al BottomSheet dopo che la lista raggiunge il confine
                                                    override suspend fun onPostFling(
                                                        consumed: Velocity,
                                                        available: Velocity,
                                                    ): Velocity = available
                                                },
                                            ),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    items(sortedUrls, key = { "${it.url}_${it.count}" }) { urlFreq ->
                                        val isPreviewExpanded = expandedPreviewUrl == urlFreq.url
                                        Column(
                                            modifier =
                                                Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(
                                                        if (isPreviewExpanded) {
                                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                                                        } else {
                                                            Color.Transparent
                                                        },
                                                    )
                                                    .animateContentSize()
                                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Row(
                                                    modifier =
                                                        Modifier
                                                            .weight(1f)
                                                            .clip(RoundedCornerShape(6.dp))
                                                            .clickable {
                                                                try {
                                                                    uriHandler.openUri(urlFreq.url)
                                                                } catch (_: Exception) {
                                                                }
                                                            }
                                                            .padding(vertical = 2.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                ) {
                                                    Icon(
                                                        painter =
                                                            androidx.compose.ui.res.painterResource(
                                                                id = android.R.drawable.ic_menu_share,
                                                            ),
                                                        contentDescription = null,
                                                        modifier = Modifier.size(12.dp),
                                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                                    )
                                                    Spacer(Modifier.width(4.dp))
                                                    Text(
                                                        urlFreq.url,
                                                        style =
                                                            MaterialTheme.typography.bodySmall.copy(
                                                                textDecoration = TextDecoration.Underline,
                                                            ),
                                                        color = MaterialTheme.colorScheme.primary,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                    )
                                                }
                                                Spacer(Modifier.width(8.dp))
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        urlFreq.lastTimestamp?.let { dateFormatter.format(Date(it)) } ?: "",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                    Spacer(Modifier.width(4.dp))
                                                    Icon(
                                                        if (isPreviewExpanded) {
                                                            Icons.Outlined.KeyboardArrowUp
                                                        } else {
                                                            Icons.Outlined.KeyboardArrowDown
                                                        },
                                                        contentDescription = stringResource(R.string.link_preview_toggle),
                                                        modifier =
                                                            Modifier
                                                                .size(18.dp)
                                                                .clip(CircleShape)
                                                                .clickable {
                                                                    expandedPreviewUrl =
                                                                        if (isPreviewExpanded) {
                                                                            null
                                                                        } else {
                                                                            urlFreq.url
                                                                        }
                                                                }
                                                                .padding(1.dp),
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                }
                                            }

                                            if (isPreviewExpanded) {
                                                Spacer(Modifier.height(6.dp))
                                                LinkPreviewSection(
                                                    url = urlFreq.url,
                                                    cache = previewCache,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── END OF FILE ─────────────────────────
