package com.whatstat.reborn.ui.screen.media

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import com.whatstat.reborn.data.db.model.DomainStat
import com.whatstat.reborn.data.db.model.LinkStat
import com.whatstat.reborn.data.db.model.MediaStat
import com.whatstat.reborn.ui.component.*
import com.whatstat.reborn.ui.tutorial.LocalTutorialManager
import com.whatstat.reborn.ui.tutorial.TutorialOverlay
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    val chatContextName by viewModel.chatContextName.collectAsState()
    val chatContextAvatarPath by viewModel.chatContextAvatarPath.collectAsState()

    val tutorialManager = LocalTutorialManager.current
    val tutorialState by tutorialManager.uiState.collectAsState()

    val sheetState = rememberModalBottomSheetState()

    LaunchedEffect(chatId) {
        viewModel.initialize(chatId)
    }

    LifecycleResumeEffect(Unit) {
        viewModel.syncFilterState()
        onPauseOrDispose { }
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
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            // Header
            SimpleScreenHeader(
                title = "Media & Link",
                onBack = if (isChatDetail) onNavigateBack else null,
                subtitle = chatContextName,
                avatarPath = chatContextAvatarPath
            )

            // Progress
            Box(modifier = Modifier.fillMaxWidth().height(4.dp)) {
                if (isLoading) {
                    val p = progress
                    if (p != null) LinearProgressIndicator(progress = { p / 100f }, modifier = Modifier.fillMaxWidth())
                    else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }

            // Filter
            TimeRangeFilterRow(
                modifier = Modifier.onGloballyPositioned {
                    tutorialManager.setTargetBounds("media_time_filter", it.boundsInRoot())
                },
                selectedRange = selectedRange,
                onRangeSelected = { viewModel.setTimeRange(it) },
                customDateRange = customDateRange,
                onDateRangeChanged = { viewModel.setCustomDateRange(it) },
                onSubmitClicked = { viewModel.loadStats() }
            )

            // Search
            Box(modifier = Modifier.fillMaxWidth().height(64.dp)) {
                if (!isLoading && allDisplayStats.isNotEmpty()) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        placeholder = { Text("Cerca contatto...") },
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                    Icon(Icons.Outlined.Clear, contentDescription = "Cancella")
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    )
                }
            }

            // Content
            if (isLoading && allDisplayStats.isEmpty()) {
                MediaStatsSkeletonContent(modifier = Modifier.fillMaxSize())
            } else if (displayStats.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Nessun link o media trovato", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    state = currentListState,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f).onGloballyPositioned {
                        tutorialManager.setTargetBounds("media_grid", it.boundsInRoot())
                    }
                ) {
                    if (isShowingBreakdown) {
                        item {
                            Card(
                                onClick = { viewModel.clearSelection() },
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                    Text("← Torna a tutti i link", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                }
                            }
                        }
                    }

                    if (topDomains.isNotEmpty() && !isShowingBreakdown) {
                        item {
                            Text("Domini più condivisi", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 8.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                                items(topDomains.take(8)) { DomainChip(it) }
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
                            onClick = if (isChatDetail || isShowingBreakdown) null else { { viewModel.loadLinkStatsBySenderForJid(stat.jid) } },
                            onLinksClick = { viewModel.loadDomainStatsForContact(stat.jid, stat.displayName) },
                            onMediaClick = { viewModel.loadMediaGalleryForContact(stat.jid, stat.displayName) }
                        )
                    }
                    item { Spacer(Modifier.height(48.dp)) }
                }
            }
        }

        // Bottom Sheets
        if (contactDomainStats != null) {
            ModalBottomSheet(
                onDismissRequest = { viewModel.dismissContactDomains() },
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surface,
                dragHandle = {
                    Box(modifier = Modifier.padding(vertical = 12.dp).width(32.dp).height(4.dp).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(2.dp)))
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

        if (mediaGalleryState != null) {
            ModalBottomSheet(
                onDismissRequest = { viewModel.dismissMediaGallery() },
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surface,
                dragHandle = {
                    Box(modifier = Modifier.padding(vertical = 12.dp).width(32.dp).height(4.dp).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(2.dp)))
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

        // Overlay Tutorial
        if (tutorialState.isTutorialActive && tutorialState.currentScreen == "media") {
            tutorialManager.currentStep?.let { step ->
                tutorialManager.getTargetBounds(step.targetKey)?.let { bounds ->
                    TutorialOverlay(
                        step = step, targetBounds = bounds, arrowDirection = step.arrowDirection,
                        isVisible = true, isLastStep = tutorialState.currentStepIndex >= tutorialState.steps.size - 1,
                        currentStepIndex = tutorialState.currentStepIndex,
                        onNext = { tutorialManager.nextStep() }, onPrevious = { tutorialManager.previousStep() },
                        onClose = { tutorialManager.closeTutorial() }, onComplete = { tutorialManager.completeTutorial("media") }
                    )
                }
            }
        }
    }
}

@Composable
private fun DomainChip(stat: DomainStat) {
    Box(modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f), RoundedCornerShape(16.dp)).border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f), RoundedCornerShape(16.dp)).padding(horizontal = 12.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stat.domain, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(6.dp))
            Text("${stat.count}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun LinkStatCard(
    stat: LinkStat,
    onClick: (() -> Unit)? = null,
    onLinksClick: () -> Unit,
    onMediaClick: (() -> Unit)? = null
) {
    val cardModifier = if (onClick != null) Modifier.fillMaxWidth().clickable { onClick() } else Modifier.fillMaxWidth()
    Card(modifier = cardModifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)), shape = RoundedCornerShape(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                if (stat.avatarPath != null) {
                    AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(File(stat.avatarPath)).crossfade(true).build(), contentDescription = null, modifier = Modifier.size(36.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                } else {
                    val isStories = stat.jid == "status@broadcast"
                    Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(if (isStories) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                        Icon(if (isStories) Icons.Outlined.Visibility else Icons.Outlined.Person, contentDescription = null, modifier = Modifier.size(20.dp), tint = if (isStories) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.width(10.dp))
                ContactNameWithPhone(name = stat.displayName ?: stat.jid.substringBefore("@"), jid = stat.jid, nameStyle = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.width(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(64.dp).then(if (stat.mediaCount > 0) Modifier.clickable { onMediaClick?.invoke() } else Modifier).background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.05f), RoundedCornerShape(8.dp)).padding(vertical = 4.dp)) {
                    Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, tint = if (stat.mediaCount > 0) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                    Text("${stat.mediaCount}", style = MaterialTheme.typography.headlineSmall, color = if (stat.mediaCount > 0) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                    Text("media", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(64.dp).clickable { onLinksClick() }.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.05f), RoundedCornerShape(8.dp)).padding(vertical = 4.dp)) {
                    Icon(Icons.Outlined.Link, contentDescription = null, tint = if (stat.totalLinks > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                    Text("${stat.totalLinks}", style = MaterialTheme.typography.headlineSmall, color = if (stat.totalLinks > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                    Text("link", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun DomainBreakdownSheet(contactName: String, domains: List<DomainStat>, isLoading: Boolean, avatarPath: String? = null, progress: Int? = null) {
    var expandedDomain by remember { mutableIntStateOf(-1) }
    val uriHandler = LocalUriHandler.current
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (avatarPath != null) AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(File(avatarPath)).crossfade(true).build(), contentDescription = null, modifier = Modifier.size(48.dp).clip(CircleShape), contentScale = ContentScale.Crop)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Analisi Link", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(contactName, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
            }
        }
        Spacer(Modifier.height(24.dp))
        if (isLoading && domains.isEmpty()) LoadingSection(progress = progress)
        else if (domains.isEmpty()) Text("Nessuna statistica disponibile", style = MaterialTheme.typography.bodyMedium)
        else {
            val maxCount = domains.maxOf { it.count }.coerceAtLeast(1)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                domains.take(10).forEachIndexed { index, stat ->
                    val isExpanded = expandedDomain == index
                    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (isExpanded) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else Color.Transparent).animateContentSize().padding(horizontal = 8.dp, vertical = 4.dp)) {
                        Row(modifier = Modifier.fillMaxWidth().clickable { expandedDomain = if (isExpanded) -1 else index }.padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Text(stat.domain, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                                if (stat.topUrls.isNotEmpty()) Icon(if (isExpanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                            Text("${stat.count}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        }
                        LinearProgressIndicator(progress = { stat.count.toFloat() / maxCount }, modifier = Modifier.fillMaxWidth().height(8.dp), strokeCap = androidx.compose.ui.graphics.StrokeCap.Round)
                        if (isExpanded && stat.topUrls.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            val df = remember { SimpleDateFormat("dd/MM/yy", Locale.ITALIAN) }
                            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp)) {
                                stat.topUrls.forEach { urlFreq ->
                                    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { try { uriHandler.openUri(urlFreq.url) } catch (_: Exception) {} }.padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text(urlFreq.url, style = MaterialTheme.typography.bodySmall.copy(textDecoration = TextDecoration.Underline), color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("(${urlFreq.count})", style = MaterialTheme.typography.labelSmall)
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

@Composable
private fun MediaGallerySheet(contactName: String, avatarPath: String?, mediaFiles: List<MediaStat>, isLoading: Boolean, progress: Int?) {
    var displayedCount by remember { mutableIntStateOf(100) }
    var selectedImageForPreview by remember { mutableStateOf<MediaStat?>(null) }
    val context = LocalContext.current
    val imageLoader = remember(context) { ImageLoader.Builder(context).components { add(VideoFrameDecoder.Factory()) }.build() }
    val visibleItems = mediaFiles.take(displayedCount)

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp)) {
        Text(contactName, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        if (isLoading && mediaFiles.isEmpty()) LoadingSection(progress = progress)
        else {
            LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.heightIn(max = 500.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(visibleItems) { media ->
                    Box(modifier = Modifier.aspectRatio(1f).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant).clickable { selectedImageForPreview = media }) {
                        AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(File(media.filePath)).crossfade(true).build(), imageLoader = imageLoader, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
                    }
                }
            }
        }
        if (selectedImageForPreview != null) ZoomableImageViewer(filePath = selectedImageForPreview!!.filePath, onDismiss = { selectedImageForPreview = null })
    }
}