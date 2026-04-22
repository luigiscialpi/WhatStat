package com.whatstat.reborn.ui.screen.media

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.Mic
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.Info
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.whatstat.reborn.data.db.model.DomainStat
import com.whatstat.reborn.data.db.model.LinkStat
import com.whatstat.reborn.data.db.model.MediaStat
import com.whatstat.reborn.data.db.model.TimeRange
import com.whatstat.reborn.data.db.model.UrlFrequency
import com.whatstat.reborn.ui.component.ContactNameWithPhone
import com.whatstat.reborn.ui.component.DateRangePicker
import com.whatstat.reborn.ui.component.SimpleScreenHeader
import com.whatstat.reborn.ui.component.TimeRangeFilterRow
import com.whatstat.reborn.ui.component.LoadingSection
import com.whatstat.reborn.ui.component.ZoomableImageViewer
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import coil.ImageLoader
import coil.decode.VideoFrameDecoder

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
    val searchQuery by viewModel.searchQuery.collectAsState()
    val topDomains by viewModel.topDomains.collectAsState()
    val contactDomainStats by viewModel.contactDomainStats.collectAsState()
    val isDomainLoading by viewModel.isDomainLoading.collectAsState()
    val progress by viewModel.loadingProgress.collectAsState()
    val progressDomains by viewModel.loadingProgressDomains.collectAsState()
    val progressMediaGallery by viewModel.loadingProgressMediaGallery.collectAsState()
    val mediaGalleryState by viewModel.mediaGalleryState.collectAsState()
    val isMediaGalleryLoading by viewModel.isMediaGalleryLoading.collectAsState()

    val sheetState = rememberModalBottomSheetState()

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
    
    val globalListState = rememberLazyListState()
    val breakdownListState = rememberLazyListState()
    val currentListState = if (isShowingBreakdown) breakdownListState else globalListState

    Box(modifier = Modifier.fillMaxSize()) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {

            // ── HEADER ─────────────────────────────────────
            var showInfoDialog by remember { mutableStateOf(false) }
            SimpleScreenHeader(
                title = if (isChatDetail) "Media & Link nella chat" else "Media & Link",
                onBack = onNavigateBack
            ) {
                IconButton(onClick = { showInfoDialog = true }) {
                    Icon(
                        Icons.Outlined.Info,
                        contentDescription = "Informazioni sui media",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                if (showInfoDialog) {
                    AlertDialog(
                        onDismissRequest = { showInfoDialog = false },
                        title = { Text("Informazioni sui Media") },
                        text = { 
                            Text("Questi contatori includono solo Foto e Video scambiati storicamente.\n\nNota bene: i bottoni esterni mostrano il totale assoluto di sempre. Tuttavia, entrando nella galleria i numeri saranno quasi sempre inferiori poiché verranno caricati e visualizzati solo i media ancora fisicamente presenti nel telefono e non eliminati nel corso del tempo.")
                        },
                        confirmButton = {
                            TextButton(onClick = { showInfoDialog = false }) {
                                Text("Ho capito")
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
            ) {
                if (isLoading) {
                    val p = progress
                    if (p != null) {
                        LinearProgressIndicator(
                            progress = { p / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            }

            // ── FILTER CHIPS ─────────────────────────────
            TimeRangeFilterRow(
                selectedRange = selectedRange,
                onRangeSelected = { viewModel.setTimeRange(it) },
                customDateRange = customDateRange,
                onDateRangeChanged = { viewModel.setCustomDateRange(it) },
                onSubmitClicked = { viewModel.applyCustomDateRange() }
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
                    MediaStatsSkeletonContent(modifier = Modifier.fillMaxSize())
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
                        state = currentListState,
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
                                onClick = if (isChatDetail || isShowingBreakdown) null else {
                                    { viewModel.loadLinkStatsBySenderForJid(stat.jid) }
                                },
                                onLinksClick = {
                                    viewModel.loadDomainStatsForContact(stat.jid, stat.displayName)
                                },
                                onMediaClick = {
                                    viewModel.loadMediaGalleryForContact(stat.jid, stat.displayName)
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
                    avatarPath = contactDomainStats?.second,
                    domains = contactDomainStats?.third ?: emptyList(),
                    isLoading = isDomainLoading,
                    progress = progressDomains
                )
            }
        }

        // ── MEDIA GALLERY BOTTOM SHEET ──────────────
        if (mediaGalleryState != null) {
            ModalBottomSheet(
                onDismissRequest = { viewModel.dismissMediaGallery() },
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
                MediaGallerySheet(
                    contactName = mediaGalleryState!!.first,
                    avatarPath = mediaGalleryState!!.second,
                    mediaFiles = mediaGalleryState!!.third,
                    isLoading = isMediaGalleryLoading,
                    progress = progressMediaGallery
                )
            }
        }

    } // fine Box
} // fine MediaStatsScreen

@Composable
private fun MediaGallerySheet(
    contactName: String,
    avatarPath: String?,
    mediaFiles: List<MediaStat>,
    isLoading: Boolean,
    progress: Int?
) {
    var displayedCount by remember { mutableIntStateOf(100) }
    var sortAscending by remember { mutableStateOf(false) }
    var selectedImageForPreview by remember { mutableStateOf<MediaStat?>(null) }
    val context = LocalContext.current

    val imageLoader = remember(context) {
        ImageLoader.Builder(context)
            .components {
                add(VideoFrameDecoder.Factory())
                add(com.whatstat.reborn.ui.component.RootFileFetcher.Factory())
            }
            .build()
    }

    val sortedAll = remember(mediaFiles, sortAscending) {
        if (sortAscending) mediaFiles.sortedBy { it.timestamp }
        else mediaFiles.sortedByDescending { it.timestamp }
    }
    val visibleItems = sortedAll.take(displayedCount)
    val hasMore = displayedCount < sortedAll.size

    val photoCount = mediaFiles.count { it.mimeType.startsWith("image/") }
    val videoCount = mediaFiles.count { it.mimeType.startsWith("video/") }
    val audioCount = mediaFiles.count { it.mimeType.startsWith("audio/") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp)
    ) {
        // Header con avatar
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (avatarPath != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(java.io.File(avatarPath))
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.width(12.dp))
            }
            Column {
                Text(
                    "Analisi Media",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    contactName,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        Spacer(Modifier.height(16.dp))

        // Contatori e toggle ordinamento
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${photoCount} foto · $videoCount video" + if(audioCount > 0) " · $audioCount audio" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                var showInfoDialog by remember { mutableStateOf(false) }
                IconButton(onClick = { showInfoDialog = true }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Outlined.Info,
                        contentDescription = "Informazioni",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp).padding(start = 4.dp)
                    )
                }

                if (showInfoDialog) {
                    AlertDialog(
                        onDismissRequest = { showInfoDialog = false },
                        title = { Text("Informazioni sui Media") },
                        text = { 
                            Text("Questi contatori si riferiscono solo ai file attuali.\n\nNota bene: i bottoni nella schermata precedente e nei breakdown mostrano il totale assoluto dei file scambiati per sempre. Tuttavia la galleria calcola e ti permette di vedere solo i numeri fisicamente presenti adesso nel telefono, escludendo i file cancellati nel corso del tempo (da te o da pulitori automatici).")
                        },
                        confirmButton = {
                            TextButton(onClick = { showInfoDialog = false }) {
                                Text("Ho capito")
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            val rotation by animateFloatAsState(if (sortAscending) 180f else 0f)
            TextButton(onClick = { sortAscending = !sortAscending }) {
                Icon(
                    Icons.Outlined.SwapVert,
                    contentDescription = "Ordina data",
                    modifier = Modifier.rotate(rotation)
                )
                Spacer(Modifier.width(4.dp))
                Text(if (sortAscending) "Meno recenti" else "Più recenti")
            }
        }
        Spacer(Modifier.height(12.dp))

        // Contenuto
        when {
            isLoading && mediaFiles.isEmpty() -> {
                LoadingSection(progress = progress)
            }
            mediaFiles.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Nessun media trovato",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            else -> {
                val dateFormatter = remember { SimpleDateFormat("dd/MM/yy", Locale.ITALIAN) }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(visibleItems) { media ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable {
                                        if (media.mimeType.startsWith("video/") || media.mimeType.startsWith("audio/")) {
                                            try {
                                                val builder = android.os.StrictMode.VmPolicy.Builder()
                                                android.os.StrictMode.setVmPolicy(builder.build())
                                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW)
                                                intent.setDataAndType(
                                                    android.net.Uri.fromFile(java.io.File(media.filePath)),
                                                    media.mimeType
                                                )
                                                intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                android.widget.Toast.makeText(context, "Impossibile aprire il file", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        } else {
                                            selectedImageForPreview = media
                                        }
                                    }
                            ) {
                                if (media.mimeType.startsWith("audio/")) {
                                    Box(
                                        modifier = Modifier.matchParentSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Outlined.Mic,
                                            contentDescription = "Audio",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(32.dp).align(Alignment.Center)
                                        )
                                        MediaDurationBadge(media.filePath, Modifier.align(Alignment.BottomEnd).padding(end = 4.dp, bottom = 4.dp).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 2.dp), color = MaterialTheme.colorScheme.surface)
                                    }
                                } else {
                                    AsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(java.io.File(media.filePath))
                                            .crossfade(true)
                                            .build(),
                                        imageLoader = imageLoader,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.matchParentSize()
                                    )
                                    if (media.mimeType.startsWith("video/")) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.Center)
                                                .size(40.dp)
                                                .background(Color.Black.copy(alpha = 0.6f), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Outlined.PlayArrow,
                                                contentDescription = "Video",
                                                tint = Color.White,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                        MediaDurationBadge(media.filePath, Modifier.align(Alignment.BottomEnd).padding(end = 4.dp, bottom = 4.dp).background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 2.dp), color = Color.White)
                                    }
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = dateFormatter.format(Date(media.timestamp)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (hasMore) {
                        item(span = { GridItemSpan(3) }) {
                            OutlinedButton(
                                onClick = { displayedCount += 100 },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Text("Carica altri (${sortedAll.size - displayedCount} rimasti)")
                            }
                        }
                    }
                }
            }
        }
        
        if (selectedImageForPreview != null) {
            ZoomableImageViewer(
                filePath = selectedImageForPreview!!.filePath,
                onDismiss = { selectedImageForPreview = null }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MediaDurationBadge(filePath: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    var duration by remember(filePath) { mutableStateOf<String?>(null) }
    LaunchedEffect(filePath) {
        duration = withContext(Dispatchers.IO) {
            try {
                val mmr = android.media.MediaMetadataRetriever()
                mmr.setDataSource(filePath)
                val durationMs = mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                mmr.release()
                if (durationMs != null && durationMs > 0) {
                    val sec = (durationMs / 1000) % 60
                    val min = (durationMs / 1000) / 60
                    String.format(Locale.getDefault(), "%d:%02d", min, sec)
                } else null
            } catch (e: Exception) { null }
        }
    }
    
    duration?.let {
        Text(text = it, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium), color = color, modifier = modifier)
    }
}

@Composable
private fun LinkStatCard(
    stat: LinkStat,
    showTotal: Boolean,
    onClick: (() -> Unit)? = null,
    onLinksClick: () -> Unit,
    onMediaClick: (() -> Unit)? = null
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
            LinkStatCardContent(stat, onLinksClick, onMediaClick)
        }
    } else {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = cardColors,
            shape = RoundedCornerShape(12.dp)
        ) {
            LinkStatCardContent(stat, onLinksClick, onMediaClick)
        }
    }
}

@Composable
private fun LinkStatCardContent(
    stat: LinkStat,
    onLinksClick: () -> Unit,
    onMediaClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar + nome
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
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
                val isStories = stat.jid == "status@broadcast"
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            if (isStories) MaterialTheme.colorScheme.tertiaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isStories) Icons.Outlined.Visibility else Icons.Outlined.Person,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = if (isStories) MaterialTheme.colorScheme.onTertiaryContainer
                               else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            val isStories = stat.jid == "status@broadcast"
            ContactNameWithPhone(
                name = stat.displayName ?: stat.jid.substringBefore("@"),
                jid = stat.jid,
                nameStyle = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = if (isStories) androidx.compose.ui.text.font.FontWeight.Bold else null
                )
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(64.dp)
                    .then(
                        if (onMediaClick != null && stat.mediaCount > 0) {
                            Modifier.clickable { onMediaClick() }
                        } else Modifier
                    )
                    .background(
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.05f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(vertical = 4.dp)
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
    isLoading: Boolean,
    avatarPath: String? = null,
    progress: Int? = null
) {
    var expandedDomain by remember { mutableIntStateOf(-1) }
    val uriHandler = LocalUriHandler.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp)
    ) {
        // Header con avatar
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (avatarPath != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(java.io.File(avatarPath))
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.width(12.dp))
            }
            Column {
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
            }
        }
        Spacer(Modifier.height(24.dp))
        when {
            isLoading && domains.isEmpty() -> {
                LoadingSection(progress = progress)
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
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    domains.take(10).forEachIndexed { index, stat ->
                        val isExpanded = expandedDomain == index
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isExpanded) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    else Color.Transparent
                                )
                                .animateContentSize()
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            // Riga dominio (cliccabile)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        expandedDomain = if (isExpanded) -1 else index
                                    }
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        stat.domain,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (stat.topUrls.isNotEmpty()) {
                                        Icon(
                                            if (isExpanded) Icons.Outlined.KeyboardArrowUp
                                            else Icons.Outlined.KeyboardArrowDown,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Text(
                                    "${stat.count}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            // Barra progresso
                            LinearProgressIndicator(
                                progress = stat.count.toFloat() / maxCount,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                            )

                            // Lista URL espandibile
                            if (isExpanded && stat.topUrls.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                val dateFormatter = remember { SimpleDateFormat("dd/MM/yy", Locale.ITALIAN) }
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 240.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    items(stat.topUrls) { urlFreq ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable {
                                                    try {
                                                        uriHandler.openUri(urlFreq.url)
                                                    } catch (_: Exception) {}
                                                }
                                                .padding(horizontal = 8.dp, vertical = 6.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                urlFreq.url,
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    textDecoration = TextDecoration.Underline
                                                ),
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.weight(1f),
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Spacer(Modifier.width(4.dp))
                                                Text(
                                                    "(${urlFreq.count})",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Spacer(Modifier.width(4.dp))
                                                Text(
                                                    urlFreq.lastTimestamp?.let { dateFormatter.format(Date(it)) } ?: "",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
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