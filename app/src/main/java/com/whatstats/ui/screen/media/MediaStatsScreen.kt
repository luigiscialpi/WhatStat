package com.whatstats.ui.screen.media

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.whatstats.R
import com.whatstats.data.db.model.DomainStat
import com.whatstats.data.db.model.LinkStat
import com.whatstats.data.db.model.MediaStat
import com.whatstats.ui.MainViewModel
import com.whatstats.ui.component.DynamicLoadingPill
import com.whatstats.ui.component.LoadingProgressSlot
import com.whatstats.ui.component.ScrollToEdgeFab
import com.whatstats.ui.component.TimeRangeFilterRow
import com.whatstats.ui.component.ads.BottomBannerAd
import com.whatstats.ui.component.ads.NativeAdCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URLDecoder
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
    val isLiteMode by viewModel.isLiteMode.collectAsState()
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
            MediaStatsHeader(
                isChatDetail = isChatDetail,
                onNavigateBack = onNavigateBack,
                chatContextName = chatContextName,
                chatContextAvatarPath = chatContextAvatarPath,
                appLang = appLang,
            )

            LoadingProgressSlot(isLoading = isLoading, progress = progress)

            // ── FILTER CHIPS ─────────────────────────────
            TimeRangeFilterRow(
                selectedRange = selectedRange,
                onRangeSelected = { viewModel.setTimeRange(it) },
                customDateRange = customDateRange,
                onDateRangeChanged = { viewModel.setCustomDateRange(it) },
                onSubmitClicked = { viewModel.applyCustomDateRange() },
                minDate = firstMessageTimestamp,
            )

            MediaStatsSearchBar(
                isLoading = isLoading,
                hasData = allDisplayStats.isNotEmpty(),
                searchQuery = searchQuery,
                onSearchQueryChange = { viewModel.setSearchQuery(it) },
                onClearSearch = { viewModel.setSearchQuery("") },
            )

            MediaStatsContentHost(
                isLoading = isLoading,
                displayStats = displayStats,
                currentListState = currentListState,
                isShowingBreakdown = isShowingBreakdown,
                topDomains = topDomains,
                isChatDetail = isChatDetail,
                showNativeAds = showNativeAds,
                searchQuery = searchQuery,
                onClearSelection = { viewModel.clearSelection() },
                onOpenBreakdown = { jid -> viewModel.loadLinkStatsBySenderForJid(jid) },
                onOpenDomainStats = { stat -> viewModel.loadDomainStatsForContact(stat.jid, stat.displayName) },
                onOpenMediaGallery = { stat -> viewModel.loadMediaGalleryForContact(stat.jid, stat.displayName) },
            )
        } // fine Column

        MediaDomainBottomSheetHost(
            contactDomainStats = contactDomainStats,
            appLang = appLang,
            sheetState = domainSheetState,
            isDomainLoading = isDomainLoading,
            progressDomains = progressDomains,
            showNativeAds = showNativeAds,
            onDismiss = { viewModel.dismissContactDomains() },
        )

        MediaGalleryBottomSheetHost(
            mediaGalleryState = mediaGalleryState,
            appLang = appLang,
            sheetState = mediaSheetState,
            isMediaGalleryLoading = isMediaGalleryLoading,
            progressMediaGallery = progressMediaGallery,
            isLiteMode = isLiteMode,
            showNativeAds = showNativeAds,
            onDismiss = { viewModel.dismissMediaGallery() },
        )

        MediaStatsScrollToEdgeFab(listState = currentListState)
        MediaStatsLoadingPills(
            isDomainLoading = isDomainLoading,
            progressDomains = progressDomains,
            onCancelDomainLoad = { viewModel.cancelDomainStatsLoad() },
            isMediaGalleryLoading = isMediaGalleryLoading,
            progressMediaGallery = progressMediaGallery,
            onCancelMediaGalleryLoad = { viewModel.cancelMediaGalleryLoad() },
        )
    } // fine Box
} // fine MediaStatsScreen

@Composable
@Suppress("FunctionName")
private fun ColumnScope.MediaStatsContentHost(
    isLoading: Boolean,
    displayStats: List<LinkStat>,
    currentListState: androidx.compose.foundation.lazy.LazyListState,
    isShowingBreakdown: Boolean,
    topDomains: List<DomainStat>,
    isChatDetail: Boolean,
    showNativeAds: Boolean,
    searchQuery: String,
    onClearSelection: () -> Unit,
    onOpenBreakdown: (String) -> Unit,
    onOpenDomainStats: (LinkStat) -> Unit,
    onOpenMediaGallery: (LinkStat) -> Unit,
) {
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
            MediaStatsLinksList(
                currentListState = currentListState,
                isShowingBreakdown = isShowingBreakdown,
                topDomains = topDomains,
                displayStats = displayStats,
                isChatDetail = isChatDetail,
                showNativeAds = showNativeAds,
                searchQuery = searchQuery,
                onClearSelection = onClearSelection,
                onOpenBreakdown = onOpenBreakdown,
                onOpenDomainStats = onOpenDomainStats,
                onOpenMediaGallery = onOpenMediaGallery,
            )
        }
    }
}

@Composable
@Suppress("FunctionName")
private fun androidx.compose.foundation.layout.BoxScope.MediaStatsScrollToEdgeFab(
    listState: androidx.compose.foundation.lazy.LazyListState,
) {
    ScrollToEdgeFab(
        listState = listState,
        modifier =
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 24.dp),
    )
}

@Composable
@Suppress("FunctionName")
private fun androidx.compose.foundation.layout.BoxScope.MediaStatsLoadingPills(
    isDomainLoading: Boolean,
    progressDomains: Int?,
    onCancelDomainLoad: () -> Unit,
    isMediaGalleryLoading: Boolean,
    progressMediaGallery: Int?,
    onCancelMediaGalleryLoad: () -> Unit,
) {
    DynamicLoadingPill(
        isVisible = isDomainLoading,
        message = stringResource(R.string.loading_domains),
        progress = progressDomains,
        onCancel = onCancelDomainLoad,
        modifier =
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp),
    )

    DynamicLoadingPill(
        isVisible = isMediaGalleryLoading,
        message = stringResource(R.string.loading_media_gallery),
        progress = progressMediaGallery,
        onCancel = onCancelMediaGalleryLoad,
        modifier =
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("FunctionName")
private fun MediaDomainBottomSheetHost(
    contactDomainStats: Triple<String, String?, List<DomainStat>>?,
    appLang: String,
    sheetState: androidx.compose.material3.SheetState,
    isDomainLoading: Boolean,
    progressDomains: Int?,
    showNativeAds: Boolean,
    onDismiss: () -> Unit,
) {
    if (contactDomainStats != null) {
        key(appLang) {
            ModalBottomSheet(
                onDismissRequest = onDismiss,
                sheetState = sheetState,
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
                Column(modifier = Modifier.fillMaxHeight(0.80f)) {
                    Box(modifier = Modifier.weight(1f)) {
                        DomainBreakdownSheet(
                            contactName = contactDomainStats.first,
                            avatarPath = contactDomainStats.second,
                            domains = contactDomainStats.third,
                            isLoading = isDomainLoading,
                            progress = progressDomains,
                        )
                    }
                    if (showNativeAds) {
                        BottomBannerAd()
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("FunctionName")
private fun MediaGalleryBottomSheetHost(
    mediaGalleryState: Triple<String, String?, List<MediaStat>>?,
    appLang: String,
    sheetState: androidx.compose.material3.SheetState,
    isMediaGalleryLoading: Boolean,
    progressMediaGallery: Int?,
    isLiteMode: Boolean,
    showNativeAds: Boolean,
    onDismiss: () -> Unit,
) {
    if (mediaGalleryState != null) {
        key(appLang) {
            ModalBottomSheet(
                onDismissRequest = onDismiss,
                sheetState = sheetState,
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
                Column(modifier = Modifier.fillMaxHeight(0.85f)) {
                    Box(modifier = Modifier.weight(1f)) {
                        MediaGallerySheet(
                            contactName = mediaGalleryState.first,
                            avatarPath = mediaGalleryState.second,
                            mediaFiles = mediaGalleryState.third,
                            isLoading = isMediaGalleryLoading,
                            progress = progressMediaGallery,
                            isLiteMode = isLiteMode,
                            showNativeAds = showNativeAds,
                        )
                    }
                    if (showNativeAds) {
                        BottomBannerAd()
                    }
                }
            }
        }
    }
}

@Composable
@Suppress("FunctionName")
private fun ColumnScope.MediaStatsLinksList(
    currentListState: androidx.compose.foundation.lazy.LazyListState,
    isShowingBreakdown: Boolean,
    topDomains: List<DomainStat>,
    displayStats: List<LinkStat>,
    isChatDetail: Boolean,
    showNativeAds: Boolean,
    searchQuery: String,
    onClearSelection: () -> Unit,
    onOpenBreakdown: (String) -> Unit,
    onOpenDomainStats: (LinkStat) -> Unit,
    onOpenMediaGallery: (LinkStat) -> Unit,
) {
    LazyColumn(
        state = currentListState,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.weight(1f),
    ) {
        if (isShowingBreakdown) {
            item {
                Card(
                    onClick = onClearSelection,
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
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )

                        Spacer(modifier = Modifier.width(4.dp))

                        Text(
                            stringResource(R.string.back_to_all_links),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
        }

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
                            { onOpenBreakdown(stat.jid) }
                        },
                    onLinksClick = { onOpenDomainStats(stat) },
                    onMediaClick = { onOpenMediaGallery(stat) },
                )
            }
            if (showNativeAds && index == 2 && searchQuery.isBlank()) {
                item(key = "native_ad_links") {
                    NativeAdCard()
                }
            }
        }

        item { Spacer(Modifier.height(48.dp)) }
    }
}

internal suspend fun getShareableUri(
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
