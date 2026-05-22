package com.whatstat.ui.screen.media

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import com.whatstat.R
import com.whatstat.data.db.model.DomainStat
import com.whatstat.data.db.model.LinkStat
import com.whatstat.data.db.model.MediaStat
import com.whatstat.ui.MainViewModel
import com.whatstat.ui.component.AnimatedInfoButton
import com.whatstat.ui.component.AutoMarquee
import com.whatstat.ui.component.ContactNameWithPhone
import com.whatstat.ui.component.DynamicLoadingPill
import com.whatstat.ui.component.LoadingSection
import com.whatstat.ui.component.ScrollToEdgeFab
import com.whatstat.ui.component.SimpleScreenHeader
import com.whatstat.ui.component.TimeRangeFilterRow
import com.whatstat.ui.component.ZoomableImageGallery
import com.whatstat.ui.component.ads.NativeAdCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipFile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun mediaStatsScreen(
    chatId: Long? = null,
    viewModel: MediaStatsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    showNativeAds: Boolean = false,
) {
    val linkStats by viewModel.linkStats.collectAsState()
    val linkStatsBySender by viewModel.linkStatsBySender.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val selectedRange by viewModel.selectedTimeRange.collectAsState()
    val customDateRange by viewModel.customDateRange.collectAsState()
    val firstMessageTimestamp by viewModel.firstMessageTimestamp.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val topDomains by viewModel.topDomains.collectAsState()
    val contactDomainStats by viewModel.contactDomainStats.collectAsState()
    val isDomainLoading by viewModel.isDomainLoading.collectAsState()
    val progress by viewModel.loadingProgress.collectAsState()
    val progressDomains by viewModel.loadingProgressDomains.collectAsState()
    val progressMediaGallery by viewModel.loadingProgressMediaGallery.collectAsState()
    val mediaGalleryState by viewModel.mediaGalleryState.collectAsState()
    val isMediaGalleryLoading by viewModel.isMediaGalleryLoading.collectAsState()
    val chatContextName by viewModel.chatContextName.collectAsState()
    val chatContextAvatarPath by viewModel.chatContextAvatarPath.collectAsState()

    val mainVm: MainViewModel = hiltViewModel()
    val appLang by mainVm.appLanguage.collectAsState()

    val domainSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val mediaSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(chatId) {
        viewModel.initialize(chatId)
    }

    LifecycleResumeEffect(Unit) {
        viewModel.syncFilterState()
        onPauseOrDispose { }
    }

    val isChatDetail = chatId != null
    val allDisplayStats = if (isChatDetail) linkStats else linkStatsBySender.ifEmpty { linkStats }
    val displayStats =
        remember(allDisplayStats, searchQuery) {
            if (searchQuery.isBlank()) {
                allDisplayStats
            } else {
                allDisplayStats.filter {
                    (it.displayName ?: it.jid).contains(searchQuery, ignoreCase = true)
                }
            }
        }
    val isShowingBreakdown = !isChatDetail && linkStatsBySender.isNotEmpty()

    val globalListState = rememberLazyListState()
    val breakdownListState = rememberLazyListState()
    val currentListState = if (isShowingBreakdown) breakdownListState else globalListState

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
        ) {
            // ── HEADER ─────────────────────────────────────
            var showInfoDialog by remember { mutableStateOf(false) }
            SimpleScreenHeader(
                title = stringResource(R.string.media_link_title),
                onBack = if (isChatDetail) onNavigateBack else null,
                subtitle = chatContextName,
                avatarPath = chatContextAvatarPath,
            ) {
                AnimatedInfoButton(
                    onClick = { showInfoDialog = true },
                    contentDescription = stringResource(R.string.info_dialog_title),
                    tint = MaterialTheme.colorScheme.onBackground,
                    glowColor = MaterialTheme.colorScheme.primary,
                )
                if (showInfoDialog) {
                    key(appLang) {
                        AlertDialog(
                            onDismissRequest = { showInfoDialog = false },
                            title = { Text(stringResource(R.string.media_info_title)) },
                            text = {
                                Text(stringResource(R.string.media_info_text_global))
                            },
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
            }

            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(4.dp),
            ) {
                if (isLoading) {
                    val p = progress
                    if (p != null) {
                        LinearProgressIndicator(
                            progress = { p / 100f },
                            modifier = Modifier.fillMaxWidth(),
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
                onSubmitClicked = { viewModel.applyCustomDateRange() },
                minDate = firstMessageTimestamp,
            )

            // ── SEARCH BAR ───────────────────────────────
            // Slot fisso per evitare layout shift quando il campo appare a fine loading
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(64.dp),
            ) {
                if (!isLoading && allDisplayStats.isNotEmpty()) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        placeholder = { Text(stringResource(R.string.chatlist_search_hint)) },
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                    Icon(Icons.Outlined.Clear, contentDescription = stringResource(R.string.cancel))
                                }
                            }
                        },
                        singleLine = true,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors =
                            OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                            ),
                    )
                }
            }

            // ── LOADING / EMPTY / LIST ───────────────────
            when {
                isLoading -> {
                    MediaStatsSkeletonContent(modifier = Modifier.fillMaxSize())
                }

                displayStats.isEmpty() -> {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            stringResource(R.string.no_results_word),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                else -> {
                    val isLite = viewModel.isLiteMode.collectAsState().value
                    LazyColumn(
                        state = currentListState,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        // Torna indietro (solo breakdown attivo)
                        if (isShowingBreakdown) {
                            item {
                                Card(
                                    onClick = { viewModel.clearSelection() },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors =
                                        CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        ),
                                    shape = RoundedCornerShape(12.dp),
                                ) {
                                    Row(
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            stringResource(R.string.back_to_all_links),
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        )
                                    }
                                }
                            }
                        }

                        // Top domains
                        if (topDomains.isNotEmpty() && !isShowingBreakdown) {
                            item {
                                Text(
                                    stringResource(R.string.links_grouped_domain),
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(vertical = 8.dp),
                                )
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    contentPadding = PaddingValues(bottom = 16.dp),
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
                                    else -> stringResource(R.string.media_link_title)
                                },
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                        }

                        displayStats.forEachIndexed { index, stat ->
                            item(key = stat.jid) {
                                LinkStatCard(
                                    stat = stat,
                                    showTotal = true,
                                    onClick =
                                        if (isChatDetail || isShowingBreakdown) {
                                            null
                                        } else {
                                            { viewModel.loadLinkStatsBySenderForJid(stat.jid) }
                                        },
                                    onLinksClick = {
                                        viewModel.loadDomainStatsForContact(stat.jid, stat.displayName)
                                    },
                                    onMediaClick = {
                                        viewModel.loadMediaGalleryForContact(stat.jid, stat.displayName)
                                    },
                                )
                            }
                            // Native ad dopo il 3° item (index 2), solo utente Free
                            if (showNativeAds && index == 2 && searchQuery.isBlank()) {
                                item(key = "native_ad_links") {
                                    NativeAdCard()
                                }
                            }
                        }

                        item { Spacer(Modifier.height(48.dp)) }
                    }
                }
            }
        } // fine Column

        // ── DOMAIN ANALYSIS BOTTOM SHEET ──────────────
        if (contactDomainStats != null) {
            key(appLang) {
                ModalBottomSheet(
                    onDismissRequest = { viewModel.dismissContactDomains() },
                    sheetState = domainSheetState,
                    containerColor = MaterialTheme.colorScheme.surface,
                    dragHandle = {
                        Box(
                            modifier =
                                Modifier
                                    .padding(vertical = 12.dp)
                                    .width(32.dp)
                                    .height(4.dp)
                                    .background(
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                        RoundedCornerShape(2.dp),
                                    ),
                        )
                    },
                ) {
                    Box(modifier = Modifier.fillMaxHeight(0.80f)) {
                        DomainBreakdownSheet(
                            contactName = contactDomainStats?.first ?: "",
                            avatarPath = contactDomainStats?.second,
                            domains = contactDomainStats?.third ?: emptyList(),
                            isLoading = isDomainLoading,
                            progress = progressDomains,
                        )
                    }
                }
            }
        }

        // ── MEDIA GALLERY BOTTOM SHEET ──────────────
        if (mediaGalleryState != null) {
            key(appLang) {
                ModalBottomSheet(
                    onDismissRequest = { viewModel.dismissMediaGallery() },
                    sheetState = mediaSheetState,
                    containerColor = MaterialTheme.colorScheme.surface,
                    dragHandle = {
                        Box(
                            modifier =
                                Modifier
                                    .padding(vertical = 12.dp)
                                    .width(32.dp)
                                    .height(4.dp)
                                    .background(
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                        RoundedCornerShape(2.dp),
                                    ),
                        )
                    },
                ) {
                    Box(modifier = Modifier.fillMaxHeight(0.85f)) {
                        MediaGallerySheet(
                            contactName = mediaGalleryState!!.first,
                            avatarPath = mediaGalleryState!!.second,
                            mediaFiles = mediaGalleryState!!.third,
                            isLoading = isMediaGalleryLoading,
                            progress = progressMediaGallery,
                            isLiteMode = viewModel.isLiteMode.collectAsState().value,
                        )
                    }
                }
            }
        }

        // ── Scroll to Top FAB ──────────────────
        ScrollToEdgeFab(
            listState = currentListState,
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 24.dp),
        )
        // ── LOADER PILLS ─────────────────────────
        DynamicLoadingPill(
            isVisible = isDomainLoading,
            message = stringResource(R.string.loading_domains),
            progress = progressDomains,
            onCancel = { viewModel.cancelDomainStatsLoad() },
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp),
        )

        DynamicLoadingPill(
            isVisible = isMediaGalleryLoading,
            message = stringResource(R.string.loading_media_gallery),
            progress = progressMediaGallery,
            onCancel = { viewModel.cancelMediaGalleryLoad() },
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp),
        )
    } // fine Box
} // fine MediaStatsScreen

@Composable
@Suppress("FunctionName")
private fun MediaGallerySheet(
    contactName: String,
    avatarPath: String?,
    mediaFiles: List<MediaStat>,
    isLoading: Boolean,
    progress: Int?,
    isLiteMode: Boolean = false,
) {
    val mainVm: MainViewModel = hiltViewModel()
    val appLang by mainVm.appLanguage.collectAsState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val gridState = rememberLazyGridState()

    // In Lite mode separiamo sticker (STK-*.webp) dal resto.
    // Il tab è visibile solo se ci sono effettivamente sticker.
    val stickers =
        remember(mediaFiles, isLiteMode) {
            if (isLiteMode) mediaFiles.filter { it.isLiteSticker() } else emptyList()
        }
    val galleryFiles =
        remember(mediaFiles, isLiteMode) {
            if (isLiteMode) mediaFiles.filterNot { it.isLiteSticker() } else mediaFiles
        }
    val showStickerTab = isLiteMode && stickers.isNotEmpty()
    // 0 = Galleria, 1 = Sticker
    var activeTab by remember(showStickerTab) { mutableIntStateOf(0) }
    val currentFiles = if (activeTab == 1) stickers else galleryFiles

    LaunchedEffect(activeTab) {
        gridState.scrollToItem(0)
    }

    var displayedCount by remember(activeTab) { mutableIntStateOf(100) }
    var sortAscending by remember { mutableStateOf(false) }
    var selectedImageForPreview by remember { mutableStateOf<MediaStat?>(null) }
    val selectedMedia = remember { mutableStateListOf<MediaStat>() }
    val context = LocalContext.current

    // Debounce per evitare multipli click rapidi sul tasto share (previene menu di condivisione duplicati)
    var lastShareClickTime by remember { mutableStateOf(0L) }

    val imageLoader =
        remember(context) {
            ImageLoader.Builder(context)
                .components {
                    add(VideoFrameDecoder.Factory())
                    add(com.whatstat.ui.component.RootFileFetcher.Factory())
                    add(com.whatstat.ui.component.ZipFileFetcher.Factory())
                }
                .build()
        }

    val sortedAll =
        remember(currentFiles, sortAscending) {
            if (sortAscending) {
                currentFiles.sortedBy { it.timestamp }
            } else {
                currentFiles.sortedByDescending { it.timestamp }
            }
        }
    val visibleItems = sortedAll.take(displayedCount)
    val hasMore = displayedCount < sortedAll.size

    // Contatori basati sui file della tab attiva
    val photoCount = currentFiles.count { it.mimeType.startsWith("image/") }
    val videoCount = currentFiles.count { it.mimeType.startsWith("video/") }
    val audioCount = currentFiles.count { it.mimeType.startsWith("audio/") }
    val stickerCount = stickers.size

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
    ) {
// Header con avatar e info button sulla destra
        if (selectedMedia.isNotEmpty()) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { selectedMedia.clear() }) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Annulla",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "${selectedMedia.size} selezionati",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = {
                        val currentTime = System.currentTimeMillis()
                        // Soglia di 1.5 secondi per evitare l'apertura di molteplici dialog di condivisione
                        if (currentTime - lastShareClickTime > 1500L) {
                            lastShareClickTime = currentTime
                            scope.launch {
                                val uris = selectedMedia.mapNotNull { getShareableUri(context, it.filePath) }
                                if (uris.isNotEmpty()) {
                                    val shareIntent =
                                        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                                            type = "*/*"
                                            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                                            // Aggiunta ClipData per concedere permessi lettura per l'anteprima di sistema
                                            val clip = ClipData.newRawUri("", uris.first())
                                            for (i in 1 until uris.size) {
                                                clip.addItem(ClipData.Item(uris[i]))
                                            }
                                            clipData = clip
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                    context.startActivity(Intent.createChooser(shareIntent, "Condividi media"))
                                    selectedMedia.clear()
                                }
                            }
                        }
                    },
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Share,
                        contentDescription = "Condividi",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
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
                        stringResource(R.string.media_link_title),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        contactName,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                var showInfoDialog by remember { mutableStateOf(false) }
                AnimatedInfoButton(
                    onClick = { showInfoDialog = true },
                    modifier = Modifier.size(32.dp),
                    contentDescription = stringResource(R.string.info_dialog_title),
                    iconSize = 18.dp,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    glowColor = MaterialTheme.colorScheme.primary,
                )
                if (showInfoDialog) {
                    key(appLang) {
                        AlertDialog(
                            onDismissRequest = { showInfoDialog = false },
                            title = { Text(stringResource(R.string.media_info_title)) },
                            text = {
                                Text(stringResource(R.string.media_info_text_gallery))
                            },
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
            }
        }
        Spacer(Modifier.height(16.dp))

        // ── Tab Galleria / Sticker (solo Lite mode quando ci sono sticker) ────
        if (showStickerTab) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                        .background(androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                listOf(
                    0 to "${stringResource(R.string.tab_media)} (${galleryFiles.size})",
                    1 to "${stringResource(R.string.tab_stickers)} ($stickerCount)",
                ).forEach {
                        (idx, label) ->
                    Box(
                        modifier =
                            Modifier
                                .weight(1f)
                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                                .background(
                                    if (activeTab == idx) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        Color.Transparent
                                    },
                                )
                                .clickable {
                                    activeTab = idx
                                    displayedCount = 100
                                }
                                .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelMedium,
                            color =
                                if (activeTab == idx) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

// Contatori e toggle ordinamento
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                val mediaCountText =
                    if (activeTab == 1) {
                        "$stickerCount ${stringResource(R.string.tab_stickers)}"
                    } else {
                        val base =
                            "$photoCount ${stringResource(R.string.type_images)} · " +
                                "$videoCount ${stringResource(R.string.type_videos)}"
                        if (audioCount > 0) {
                            "$base · $audioCount ${stringResource(R.string.type_audio)}"
                        } else {
                            base
                        }
                    }
                AutoMarquee {
                    Text(
                        text = mediaCountText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            val rotation by animateFloatAsState(if (sortAscending) 180f else 0f)
            TextButton(onClick = { sortAscending = !sortAscending }) {
                Icon(
                    Icons.Outlined.SwapVert,
                    contentDescription = stringResource(R.string.radar_parole_title),
                    modifier = Modifier.rotate(rotation),
                )
                Spacer(Modifier.width(4.dp))
                Text(if (sortAscending) stringResource(R.string.sort_oldest) else stringResource(R.string.sort_newest))
            }
        }
        Spacer(Modifier.height(12.dp))

        // Contenuto
        when {
            isLoading && currentFiles.isEmpty() -> {
                LoadingSection(progress = progress)
            }
            currentFiles.isEmpty() -> {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(R.string.no_stickers_found),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            else -> {
                val dateFormatter = remember { SimpleDateFormat("dd/MM/yy", Locale.ITALIAN) }
                Box(modifier = Modifier.fillMaxSize()) {
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(visibleItems) { media ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(4.dp),
                            ) {
                                Box(
                                    modifier =
                                        Modifier
                                            .aspectRatio(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .pointerInput(media) {
                                                detectTapGestures(
                                                    onLongPress = {
                                                        if (selectedMedia.isEmpty()) {
                                                            selectedMedia.add(media)
                                                        }
                                                    },
                                                    onTap = {
                                                        if (selectedMedia.isNotEmpty()) {
                                                            if (selectedMedia.contains(media)) {
                                                                selectedMedia.remove(media)
                                                            } else {
                                                                selectedMedia.add(media)
                                                            }
                                                        } else {
                                                            val isPlayable =
                                                                media.mimeType.startsWith("video/") ||
                                                                    media.mimeType.startsWith("audio/")
                                                            if (isPlayable) {
                                                                scope.launch {
                                                                    try {
                                                                        val contentUri = getShareableUri(context, media.filePath)
                                                                        if (contentUri != null) {
                                                                            val intent =
                                                                                Intent(Intent.ACTION_VIEW).apply {
                                                                                    setDataAndType(contentUri, media.mimeType)
                                                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                                                }
                                                                            context.startActivity(intent)
                                                                        } else {
                                                                            Toast.makeText(
                                                                                context,
                                                                                context.getString(R.string.error_open_file),
                                                                                Toast.LENGTH_SHORT,
                                                                            ).show()
                                                                        }
                                                                    } catch (e: Exception) {
                                                                        Toast.makeText(
                                                                            context,
                                                                            context.getString(R.string.error_open_file),
                                                                            Toast.LENGTH_SHORT,
                                                                        ).show()
                                                                    }
                                                                }
                                                            } else {
                                                                selectedImageForPreview = media
                                                            }
                                                        }
                                                    },
                                                )
                                            },
                                ) {
                                    if (media.mimeType.startsWith("audio/")) {
                                        Box(
                                            modifier = Modifier.matchParentSize(),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Icon(
                                                Icons.Outlined.Mic,
                                                contentDescription = stringResource(R.string.type_audio),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(32.dp).align(Alignment.Center),
                                            )
                                            MediaDurationBadge(
                                                media.filePath,
                                                Modifier.align(
                                                    Alignment.BottomEnd,
                                                ).padding(
                                                    end = 4.dp,
                                                    bottom = 4.dp,
                                                ).background(
                                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                                    RoundedCornerShape(4.dp),
                                                ).padding(horizontal = 4.dp, vertical = 2.dp),
                                                color = MaterialTheme.colorScheme.surface,
                                            )
                                        }
                                    } else {
                                        val imgData = if (media.filePath.startsWith("zip://")) media.filePath else File(media.filePath)
                                        AsyncImage(
                                            model =
                                                ImageRequest.Builder(LocalContext.current)
                                                    .data(imgData)
                                                    .crossfade(true)
                                                    .build(),
                                            imageLoader = imageLoader,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.matchParentSize(),
                                        )
                                        if (media.mimeType.startsWith("video/")) {
                                            Box(
                                                modifier =
                                                    Modifier
                                                        .align(Alignment.Center)
                                                        .size(40.dp)
                                                        .background(Color.Black.copy(alpha = 0.6f), CircleShape),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Icon(
                                                    Icons.Outlined.PlayArrow,
                                                    contentDescription = stringResource(R.string.type_videos),
                                                    tint = Color.White,
                                                    modifier = Modifier.size(24.dp),
                                                )
                                            }
                                            MediaDurationBadge(
                                                media.filePath,
                                                Modifier.align(
                                                    Alignment.BottomEnd,
                                                ).padding(
                                                    end = 4.dp,
                                                    bottom = 4.dp,
                                                ).background(
                                                    Color.Black.copy(alpha = 0.6f),
                                                    RoundedCornerShape(4.dp),
                                                ).padding(horizontal = 4.dp, vertical = 2.dp),
                                                color = Color.White,
                                            )
                                        }
                                    }

                                    val isSelected = selectedMedia.contains(media)
                                    if (isSelected) {
                                        Box(
                                            modifier =
                                                Modifier
                                                    .matchParentSize()
                                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                                        )
                                        Icon(
                                            imageVector = Icons.Outlined.Check,
                                            contentDescription = "Selezionato",
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier =
                                                Modifier
                                                    .align(Alignment.TopEnd)
                                                    .padding(4.dp)
                                                    .size(24.dp)
                                                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                                                    .padding(4.dp),
                                        )
                                    }
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = dateFormatter.format(Date(media.timestamp)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }

                        if (hasMore) {
                            item(span = { GridItemSpan(3) }) {
                                OutlinedButton(
                                    onClick = { displayedCount += 100 },
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                ) {
                                    Text(
                                        "${stringResource(
                                            R.string.load_more,
                                        )} (${sortedAll.size - displayedCount} ${stringResource(R.string.show_less)})",
                                    )
                                }
                            }
                        }
                    }
                    // ── Scroll to Top FAB nella galleria ──────────────────
                    ScrollToEdgeFab(
                        gridState = gridState,
                        modifier =
                            Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 16.dp, bottom = 16.dp),
                    )
                }
            }
        }

        if (selectedImageForPreview != null) {
            val imageList =
                currentFiles.filter {
                    !it.mimeType.startsWith("video/") && !it.mimeType.startsWith("audio/")
                }.map { it.filePath }
            val index = imageList.indexOf(selectedImageForPreview!!.filePath)

            com.whatstat.ui.component.ZoomableImageGallery(
                filePaths = imageList,
                initialIndex = if (index >= 0) index else 0,
                onDismiss = { selectedImageForPreview = null },
                onShare = { pathToShare ->
                    val currentTime = System.currentTimeMillis()
                    // Soglia di 1.5 secondi per evitare l'apertura di molteplici dialog di condivisione
                    if (currentTime - lastShareClickTime > 1500L) {
                        lastShareClickTime = currentTime
                        scope.launch {
                            val uri = getShareableUri(context, pathToShare)
                            if (uri != null) {
                                val mimeType = currentFiles.find { it.filePath == pathToShare }?.mimeType ?: "image/*"
                                val shareIntent =
                                    Intent(Intent.ACTION_SEND).apply {
                                        type = mimeType
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        // Aggiunta ClipData per concedere permessi lettura per l'anteprima di sistema
                                        clipData = ClipData.newRawUri("", uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                context.startActivity(Intent.createChooser(shareIntent, null))
                            }
                        }
                    }
                },
            )
        }
    }
}

private fun MediaStat.isLiteSticker(): Boolean {
    if (!mimeType.equals("image/webp", ignoreCase = true)) return false
    val fileName =
        if (filePath.contains("|")) {
            filePath.substringAfterLast('|').substringAfterLast('/')
        } else {
            filePath.substringAfterLast('/')
        }
    return fileName.startsWith("STK-", ignoreCase = true)
}

// ─────────────────────────────────────────────────────────────────────────────

@Composable
@Suppress("FunctionName")
private fun MediaDurationBadge(
    filePath: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    var duration by remember(filePath) { mutableStateOf<String?>(null) }
    LaunchedEffect(filePath) {
        duration =
            withContext(Dispatchers.IO) {
                try {
                    val mmr = android.media.MediaMetadataRetriever()
                    mmr.setDataSource(filePath)
                    val durationMs = mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                    mmr.release()
                    if (durationMs != null && durationMs > 0) {
                        val sec = (durationMs / 1000) % 60
                        val min = (durationMs / 1000) / 60
                        String.format(Locale.getDefault(), "%d:%02d", min, sec)
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    null
                }
            }
    }

    duration?.let {
        Text(
            text = it,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
            color = color,
            modifier = modifier,
        )
    }
}

@Composable
@Suppress("FunctionName")
private fun LinkStatCard(
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
                    "media",
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
                    "link",
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
private fun DomainChip(stat: DomainStat) {
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
private fun DomainBreakdownSheet(
    contactName: String,
    domains: List<DomainStat>,
    isLoading: Boolean,
    avatarPath: String? = null,
    progress: Int? = null,
) {
    var expandedDomain by remember { mutableIntStateOf(-1) }
    val uriHandler = LocalUriHandler.current

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
    ) {
        // Header con avatar
        Row(verticalAlignment = Alignment.CenterVertically) {
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
            Column {
                Text(
                    "Analisi Link",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    contactName,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> {
                val maxCount = domains.maxOf { it.count }.coerceAtLeast(1)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    domains.take(10).forEachIndexed { index, stat ->
                        val isExpanded = expandedDomain == index
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
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            // Riga dominio (cliccabile)
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            expandedDomain = if (isExpanded) -1 else index
                                        }
                                        .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(
                                        stat.domain,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    if (stat.topUrls.isNotEmpty()) {
                                        Icon(
                                            if (isExpanded) {
                                                Icons.Outlined.KeyboardArrowUp
                                            } else {
                                                Icons.Outlined.KeyboardArrowDown
                                            },
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                Text(
                                    "${stat.count}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }

                            // Barra progresso
                            LinearProgressIndicator(
                                progress = { stat.count.toFloat() / maxCount },
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .height(8.dp),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                            )

                            // Lista URL espandibile
                            if (isExpanded && stat.topUrls.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                val dateFormatter = remember { SimpleDateFormat("dd/MM/yy", Locale.ITALIAN) }
                                LazyColumn(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 240.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    items(stat.topUrls) { urlFreq ->
                                        Row(
                                            modifier =
                                                Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .clickable {
                                                        try {
                                                            uriHandler.openUri(urlFreq.url)
                                                        } catch (_: Exception) {
                                                        }
                                                    }
                                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                urlFreq.url,
                                                style =
                                                    MaterialTheme.typography.bodySmall.copy(
                                                        textDecoration = TextDecoration.Underline,
                                                    ),
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.weight(1f),
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Spacer(Modifier.width(4.dp))
                                                Text(
                                                    "(${urlFreq.count})",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                                Spacer(Modifier.width(4.dp))
                                                Text(
                                                    urlFreq.lastTimestamp?.let { dateFormatter.format(Date(it)) } ?: "",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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

private suspend fun getShareableUri(
    context: Context,
    filePath: String,
): Uri? {
    return withContext(Dispatchers.IO) {
        try {
            val fileToShare =
                if (filePath.startsWith("zip://")) {
                    val decodedUri = URLDecoder.decode(filePath, "UTF-8")
                    val parts = decodedUri.removePrefix("zip://").split("|")
                    if (parts.size < 2) return@withContext null
                    val baseFolder = File(context.filesDir, "lite_media")
                    val zipFileObj = File(baseFolder, parts[0])
                    if (!zipFileObj.exists()) return@withContext null
                    val zipFile = ZipFile(zipFileObj)
                    val entry =
                        zipFile.getEntry(parts[1]) ?: run {
                            zipFile.close()
                            return@withContext null
                        }

                    val tempFile = File(context.cacheDir, File(parts[1]).name)
                    zipFile.getInputStream(entry).use { input ->
                        tempFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    zipFile.close()
                    tempFile
                } else {
                    val srcFile = File(filePath)
                    if (!srcFile.exists()) return@withContext null
                    val tempFile = File(context.cacheDir, srcFile.name)
                    srcFile.inputStream().use { input ->
                        tempFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    tempFile
                }

            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                fileToShare,
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
