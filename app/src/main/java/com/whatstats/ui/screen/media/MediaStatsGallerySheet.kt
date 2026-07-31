package com.whatstats.ui.screen.media

import android.content.ClipData
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import com.whatstats.R
import com.whatstats.data.db.model.MediaStat
import com.whatstats.telemetry.OverlayTrackingEntryPoint
import com.whatstats.telemetry.TrackOverlayView
import com.whatstats.ui.MainViewModel
import com.whatstats.ui.component.AnimatedInfoButton
import com.whatstats.ui.component.AutoMarquee
import com.whatstats.ui.component.DbThumbnailFetcher
import com.whatstats.ui.component.DbVideoThumbnail
import com.whatstats.ui.component.LoadingSection
import com.whatstats.ui.component.RootFileFetcher
import com.whatstats.ui.component.ScrollToEdgeFab
import com.whatstats.ui.component.ZoomableImageGallery
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
@Suppress("FunctionName")
internal fun MediaGallerySheet(
    contactName: String,
    avatarPath: String?,
    mediaFiles: List<MediaStat>,
    isLoading: Boolean,
    progress: Int?,
    isLiteMode: Boolean = false,
    showNativeAds: Boolean = false,
) {
    TrackOverlayView("media_gallery")
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
                    add(DbThumbnailFetcher.Factory())
                    add(com.whatstats.ui.component.RootFileFetcher.Factory())
                    add(com.whatstats.ui.component.ZipFileFetcher.Factory())
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
                        contentDescription = stringResource(R.string.cancel),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(id = R.string.selected_count, selectedMedia.size),
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
                            val telemetry =
                                EntryPointAccessors.fromApplication(
                                    context.applicationContext,
                                    OverlayTrackingEntryPoint::class.java,
                                ).telemetryRepository()
                            telemetry.trackMediaShare("multiple")
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
                                    context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.share_media)))
                                    selectedMedia.clear()
                                }
                            }
                        }
                    },
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Share,
                        contentDescription = stringResource(R.string.share),
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
                        AutoMarquee {
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
                                                media.mediaDuration,
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
                                    } else if (media.mimeType.startsWith("video/")) {
                                        // Anteprima video dalla thumbnail nel DB (message_thumbnail):
                                        // nessuna copia del file video per la griglia. Se la thumbnail
                                        // manca, resta lo sfondo scuro con il pulsante play.
                                        Box(
                                            modifier =
                                                Modifier
                                                    .matchParentSize()
                                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                        )
                                        if (media.messageRowId > 0L) {
                                            AsyncImage(
                                                model =
                                                    ImageRequest.Builder(LocalContext.current)
                                                        .data(DbVideoThumbnail(media.messageRowId))
                                                        .crossfade(true)
                                                        .build(),
                                                imageLoader = imageLoader,
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.matchParentSize(),
                                            )
                                        }
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
                                            media.mediaDuration,
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
                                            contentDescription = stringResource(R.string.selected),
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
                                        )} (${sortedAll.size - displayedCount})",
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

            com.whatstats.ui.component.ZoomableImageGallery(
                filePaths = imageList,
                initialIndex = if (index >= 0) index else 0,
                onDismiss = { selectedImageForPreview = null },
                showNativeAds = showNativeAds,
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
    // Riconosce sia il prefisso Android "STK-" sia il pattern iOS "00000XXX-STICKER-..."
    return fileName.startsWith("STK-", ignoreCase = true) ||
        fileName.contains("STICKER", ignoreCase = true)
}

// ─────────────────────────────────────────────────────────────────────────────

@Composable
@Suppress("FunctionName")
private fun MediaDurationBadge(
    filePath: String,
    durationSec: Long,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val context = LocalContext.current
    // Durata gia nota dal DB (message_media.media_duration, in secondi): formatta
    // subito senza toccare il file. Evita la copia root di video/audio.
    val precomputed =
        remember(durationSec) {
            if (durationSec > 0) {
                val sec = durationSec % 60
                val min = durationSec / 60
                String.format(Locale.getDefault(), "%d:%02d", min, sec)
            } else {
                null
            }
        }
    var duration by remember(filePath, precomputed) { mutableStateOf(precomputed) }
    LaunchedEffect(filePath, precomputed) {
        // Fallback solo se il DB non ha la durata (raro): legge dal file via
        // MediaMetadataRetriever, con copia root se il path non e accessibile.
        if (precomputed != null) return@LaunchedEffect
        duration =
            withContext(Dispatchers.IO) {
                // Estrae la durata da un path locale leggibile.
                fun extractFrom(path: String): String? {
                    val mmr = android.media.MediaMetadataRetriever()
                    return try {
                        mmr.setDataSource(path)
                        val durationMs =
                            mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                        if (durationMs != null && durationMs > 0) {
                            val sec = (durationMs / 1000) % 60
                            val min = (durationMs / 1000) / 60
                            String.format(Locale.getDefault(), "%d:%02d", min, sec)
                        } else {
                            null
                        }
                    } finally {
                        mmr.release()
                    }
                }
                // Prova lettura diretta; se fallisce (Scoped Storage WhatsApp senza
                // MANAGE_EXTERNAL_STORAGE) copia via root in cacheDir e riprova.
                try {
                    extractFrom(filePath)
                } catch (e: Exception) {
                    val local = RootFileFetcher.copyToCache(context, filePath)
                    if (local != null) {
                        try {
                            extractFrom(local.absolutePath)
                        } catch (e2: Exception) {
                            null
                        }
                    } else {
                        null
                    }
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
