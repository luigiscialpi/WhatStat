package com.whatstat.ui.screen.chatdetail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.whatstat.R
import com.whatstat.data.db.model.SenderStat
import com.whatstat.data.db.model.TimeRange
import com.whatstat.ui.MainViewModel
import com.whatstat.ui.component.ActivityRecapCard
import com.whatstat.ui.component.AnimatedInfoButton
import com.whatstat.ui.component.ContactNameWithPhone
import com.whatstat.ui.component.DateRangeCard
import com.whatstat.ui.component.EmojiPreviewModal
import com.whatstat.ui.component.InteractiveEmojiCloud
import com.whatstat.ui.component.InteractiveStickerCloud
import com.whatstat.ui.component.InteractiveWordCloud
import com.whatstat.ui.component.LoadingSection
import com.whatstat.ui.component.MilestonePredictionCard
import com.whatstat.ui.component.SenderColorPickerDialog
import com.whatstat.ui.component.ShimmerBox
import com.whatstat.ui.component.StatCard
import com.whatstat.ui.component.StickerPreviewModal
import com.whatstat.ui.component.TimeRangeFilterRow
import com.whatstat.ui.component.ZoomableImageGallery
import com.whatstat.ui.component.ads.NativeAdCard
import com.whatstat.ui.component.rememberShimmerBrush
import com.whatstat.ui.theme.getSenderColor
import com.whatstat.ui.theme.senderColorOverrideKey
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

private val dateFormatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

@Composable
@Suppress("FunctionName")
fun ChatDetailScreen(
    chatId: Long,
    viewModel: ChatDetailViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToWordSearch: (Long) -> Unit = { _ -> },
    onNavigateToCalls: (Long) -> Unit = { _ -> },
    onNavigateToMedia: (Long) -> Unit = { _ -> },
    onNavigateToHistory: (Long) -> Unit = { _ -> },
    showNativeAds: Boolean = false,
) {
    val mainVm: MainViewModel = hiltViewModel()
    val appLang by mainVm.appLanguage.collectAsState()
    val stats by viewModel.chatStats.collectAsState()
    val headerInfo by viewModel.headerInfo.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isLoadingSlow by viewModel.isLoadingSlow.collectAsState()
    val progress by viewModel.loadingProgress.collectAsState()
    val progressSlow by viewModel.loadingProgressSlow.collectAsState()
    val selectedRange by viewModel.selectedTimeRange.collectAsState()
    val customDateRange by viewModel.customDateRange.collectAsState()
    val firstMessageTimestamp by viewModel.firstMessageTimestamp.collectAsState()
    val errorMsg by viewModel.error.collectAsState()

    LifecycleResumeEffect(Unit) {
        viewModel.syncFilterState()
        onPauseOrDispose { }
    }
    val isLiteMode by viewModel.isLiteMode.collectAsState()
    val wordViewMode by viewModel.wordViewMode.collectAsState()
    val selectedSenderWords by viewModel.selectedSenderForWords.collectAsState()
    val topWordsLimit by viewModel.topWordsLimit.collectAsState()
    val senderTopWords by viewModel.senderTopWords.collectAsState()
    val isSenderWordsLoading by viewModel.isSenderWordsLoading.collectAsState()
    val emojiViewMode by viewModel.emojiViewMode.collectAsState()
    val selectedSenderEmojis by viewModel.selectedSenderForEmojis.collectAsState()
    val topEmojiLimit by viewModel.topEmojiLimit.collectAsState()
    val senderTopEmojis by viewModel.senderTopEmojis.collectAsState()
    val isSenderEmojisLoading by viewModel.isSenderEmojisLoading.collectAsState()
    val stickerViewMode by viewModel.stickerViewMode.collectAsState()
    val selectedSenderStickers by viewModel.selectedSenderForStickers.collectAsState()
    val topStickerLimit by viewModel.topStickerLimit.collectAsState()
    val senderTopStickers by viewModel.senderTopStickers.collectAsState()
    val isStickerLoading by viewModel.isStickerLoading.collectAsState()
    val topStickers by viewModel.topStickers.collectAsState()

    var showAvatarZoom by remember { mutableStateOf(false) }
    var zoomAvatarPath by remember { mutableStateOf<String?>(null) }

    var previewEmoji by remember { mutableStateOf<String?>(null) }
    var previewEmojiCount by remember { mutableIntStateOf(0) }
    var previewSticker by remember { mutableStateOf<String?>(null) }
    var previewStickerCount by remember { mutableIntStateOf(0) }
    var chartDismissSignal by remember { mutableIntStateOf(0) }
    // Dialog informativi sezioni pie chart
    var showTopSendersInfoDialog by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var colorPickerSender by remember { mutableStateOf<SenderStat?>(null) }
    val senderColorArgbOverrides by viewModel.senderColorArgbOverrides.collectAsState()

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Final)
                            if (event.changes.any { it.changedToUp() }) {
                                chartDismissSignal++
                            }
                        }
                    }
                },
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
        ) {
            // ── Header ───────────────────────────────────────────────
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back_desc),
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }

                if (stats != null) {
                    val s = stats!!
                    if (s.avatarPath != null) {
                        AsyncImage(
                            model =
                                ImageRequest.Builder(LocalContext.current)
                                    .data(java.io.File(s.avatarPath))
                                    .crossfade(true)
                                    .build(),
                            contentDescription = stringResource(R.string.avatar_desc),
                            modifier =
                                Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        zoomAvatarPath = s.avatarPath
                                        showAvatarZoom = true
                                    },
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Box(
                            modifier =
                                Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                if (s.isGroup) Icons.Outlined.Group else Icons.Outlined.Person,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(
                        modifier =
                            Modifier.clickable(enabled = s.avatarPath != null) {
                                zoomAvatarPath = s.avatarPath
                                showAvatarZoom = true
                            },
                    ) {
                        ContactNameWithPhone(
                            name = s.contactName,
                            jid = s.jid,
                            nameStyle =
                                MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                ),
                        )
                        Text(
                            stringResource(R.string.messages_count_suffix, s.totalMessages),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    val shimmerBrush = rememberShimmerBrush()
                    // Se abbiamo già nome+avatar (caricati in anticipo), li mostriamo subito
                    val hi = headerInfo
                    if (hi != null) {
                        if (hi.avatarPath != null) {
                            AsyncImage(
                                model =
                                    ImageRequest.Builder(LocalContext.current)
                                        .data(java.io.File(hi.avatarPath))
                                        .crossfade(true)
                                        .build(),
                                contentDescription = stringResource(R.string.avatar_desc),
                                modifier =
                                    Modifier
                                        .size(40.dp)
                                        .clip(CircleShape),
                                contentScale = ContentScale.Crop,
                            )
                        } else {
                            Box(
                                modifier =
                                    Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    if (hi.isGroup) Icons.Outlined.Group else Icons.Outlined.Person,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                hi.name,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            ShimmerBox(
                                modifier = Modifier.width(80.dp).height(10.dp),
                                brush = shimmerBrush,
                            )
                        }
                    } else {
                        // Fallback shimmer completo prima che headerInfo sia disponibile
                        ShimmerBox(
                            modifier = Modifier.size(40.dp),
                            brush = shimmerBrush,
                            shape = CircleShape,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            ShimmerBox(modifier = Modifier.width(140.dp).height(14.dp), brush = shimmerBrush)
                            ShimmerBox(modifier = Modifier.width(80.dp).height(10.dp), brush = shimmerBrush)
                        }
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

            if (!isLoading) {
                // ── Sticky Filter Chips ───────────────────────────────────
                TimeRangeFilterRow(
                    selectedRange = selectedRange,
                    onRangeSelected = { viewModel.setTimeRange(it) },
                    customDateRange = customDateRange,
                    onDateRangeChanged = { viewModel.setCustomDateRange(it) },
                    onSubmitClicked = { viewModel.applyCustomDateRange() },
                    minDate = firstMessageTimestamp,
                )
            }

            if (isLoading) {
                ChatDetailSkeletonContent(modifier = Modifier.fillMaxSize())
            } else if (errorMsg != null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    SelectionContainer {
                        Text(
                            text = errorMsg ?: stringResource(R.string.error_unknown, ""),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            } else if (stats != null) {
                val s = stats!!
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // ── 3. Sent / Received ───────────────────────────────────
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            StatCard(
                                modifier = Modifier.weight(1f),
                                label = stringResource(R.string.chatdetail_sent),
                                value = "%,d".format(s.sentByMe),
                                icon = Icons.AutoMirrored.Filled.Send,
                            )
                            StatCard(
                                modifier = Modifier.weight(1f),
                                label = stringResource(R.string.chatdetail_received),
                                value = "%,d".format(s.receivedFromThem),
                                icon = Icons.AutoMirrored.Filled.CallReceived,
                            )
                        }
                    }

                    // ── 4. Density Stats ─────────────────────────────────────
                    item {
                        val totalMessages = s.sentByMe + s.receivedFromThem
                        val otherName = if (!s.isGroup) s.contactName else stringResource(R.string.fallback_them)
                        val meName = stringResource(R.string.fallback_me)

                        // Words
                        val totalWords = s.sentWords + s.receivedWords
                        val avgWords = if (totalMessages > 0) "%.1f".format(totalWords.toFloat() / totalMessages) else "—"
                        val sentWpm = if (s.sentByMe > 0) "%.1f".format(s.sentWords.toFloat() / s.sentByMe) else "0"
                        val recvWpm = if (s.receivedFromThem > 0) "%.1f".format(s.receivedWords.toFloat() / s.receivedFromThem) else "0"
                        val wordsSubtitle = stringResource(R.string.stat_split_format, meName, sentWpm, otherName, recvWpm)

                        // Emojis
                        val totalEmojis = s.sentEmojis + s.receivedEmojis
                        val avgEmojis = if (totalMessages > 0) "%.1f".format(totalEmojis.toFloat() / totalMessages) else "—"
                        val sentEpm = if (s.sentByMe > 0) "%.1f".format(s.sentEmojis.toFloat() / s.sentByMe) else "0"
                        val recvEpm = if (s.receivedFromThem > 0) "%.1f".format(s.receivedEmojis.toFloat() / s.receivedFromThem) else "0"
                        val emojisSubtitle = stringResource(R.string.stat_split_format, meName, sentEpm, otherName, recvEpm)

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.height(IntrinsicSize.Min),
                        ) {
                            StatCard(
                                modifier = Modifier.weight(1f),
                                label = stringResource(R.string.chatdetail_words_per_msg),
                                value = avgWords,
                                subtitle = wordsSubtitle,
                                icon = Icons.Outlined.TextFields,
                            )
                            if (isLoadingSlow) {
                                ShimmerBox(
                                    modifier =
                                        Modifier
                                            .weight(1f)
                                            .fillMaxHeight(),
                                    brush = rememberShimmerBrush(),
                                    shape = RoundedCornerShape(16.dp),
                                )
                            } else {
                                StatCard(
                                    modifier = Modifier.weight(1f),
                                    label = stringResource(R.string.chatdetail_emoji_per_msg),
                                    value = avgEmojis,
                                    subtitle = emojisSubtitle,
                                    icon = Icons.Outlined.EmojiEmotions,
                                )
                            }
                        }
                    }

                    // ── Native Ad (Free only) ─────────────────────────────────
                    if (showNativeAds) {
                        item {
                            NativeAdCard()
                        }
                    }

                    // ── 5. Chi scrive di più? (Distribuzione messaggi) ───────
                    item {
                        SectionCard(
                            title = stringResource(R.string.chatdetail_top_sender_title),
                            onInfoClick = { showTopSendersInfoDialog = true },
                        ) {
                            if (s.senderBreakdown.isNotEmpty()) {
                                MessagePieChart(
                                    chatId = chatId,
                                    senders = s.senderBreakdown,
                                    referenceSenders = s.senderBreakdown,
                                    totalMessages = s.totalMessages,
                                    senderColorArgbOverrides = senderColorArgbOverrides,
                                    onLegendColorClick = { colorPickerSender = it },
                                    dismissSignal = chartDismissSignal,
                                )
                            } else if (isLoadingSlow) {
                                LoadingSection(progress = progressSlow)
                            } else {
                                val otherName = if (!s.isGroup) s.contactName else stringResource(R.string.fallback_them)
                                val fallbackSenders =
                                    listOf(
                                        SenderStat("me", stringResource(R.string.fallback_me), s.sentByMe),
                                        SenderStat("other", otherName, s.receivedFromThem),
                                    )
                                MessagePieChart(
                                    chatId = chatId,
                                    senders = fallbackSenders,
                                    referenceSenders = fallbackSenders,
                                    totalMessages = s.totalMessages,
                                    senderColorArgbOverrides = senderColorArgbOverrides,
                                    onLegendColorClick = { colorPickerSender = it },
                                    dismissSignal = chartDismissSignal,
                                )
                            }
                        }
                    }

                    // ── 6. Activity Recap Card (Grafici interattivi a tab) ───
                    item {
                        ActivityRecapCard(
                            hourlyData = s.hourlyDistribution,
                            weeklyData = s.weeklyDistribution,
                            dailyData = s.dayOfMonthDistribution,
                            monthlyData = s.monthOfYearDistribution,
                            yearlyData = s.yearTrendDistribution,
                            referenceSenders = s.senderBreakdown,
                            scopeChatId = chatId,
                            senderColorArgbOverrides = senderColorArgbOverrides,
                            dismissSignal = chartDismissSignal,
                        )
                    }

                    // ── 7. Info Cards (Periodo / Risposta e Media / Lunghezza) ─
                    item {
                        Row(
                            modifier = Modifier.height(IntrinsicSize.Min),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            DateRangeCard(
                                modifier = Modifier.weight(1f),
                                label = stringResource(R.string.activity_period_label),
                                startTimestamp = s.firstMessageDate,
                                endTimestamp = s.lastMessageDate,
                                dateFormatter = dateFormatter,
                                icon = Icons.Outlined.CalendarMonth,
                                isSmallText = true,
                            )
                            if (isLoadingSlow) {
                                ShimmerBox(
                                    modifier =
                                        Modifier
                                            .weight(1f)
                                            .fillMaxHeight(),
                                    brush = rememberShimmerBrush(),
                                    shape = RoundedCornerShape(16.dp),
                                )
                            } else {
                                StatCard(
                                    modifier = Modifier.weight(1f),
                                    label = stringResource(R.string.avg_response_time),
                                    value = formatDuration(s.averageResponseTimeMs),
                                    icon = Icons.Outlined.AccessTime,
                                    isSmallText = true,
                                )
                            }
                        }
                    }

                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            StatCard(
                                modifier = Modifier.weight(1f),
                                label = stringResource(R.string.stat_total_media),
                                value = "%,d".format(s.mediaCount),
                                icon = Icons.Outlined.PhotoLibrary,
                                isSmallText = true,
                            )
                            StatCard(
                                modifier = Modifier.weight(1f),
                                label = stringResource(R.string.media_length_label),
                                value = "%.0f ${stringResource(R.string.chars_abbr)}".format(s.averageMessageLength),
                                icon = Icons.Outlined.TextFields,
                                isSmallText = true,
                            )
                        }
                    }

                    // ── 8. Chi inizia le conversazioni? ─────────────────────
                    item {
                        SectionCard(
                            title = stringResource(R.string.conv_starters_title),
                            onInfoClick = { showInfoDialog = true },
                        ) {
                            if (s.conversationStarterBreakdown.isNotEmpty()) {
                                val breakdownTotal = s.conversationStarterBreakdown.sumOf { it.count }.coerceAtLeast(1)
                                MessagePieChart(
                                    chatId = chatId,
                                    senders = s.conversationStarterBreakdown,
                                    referenceSenders = s.senderBreakdown,
                                    totalMessages = breakdownTotal,
                                    senderColorArgbOverrides = senderColorArgbOverrides,
                                    onLegendColorClick = { colorPickerSender = it },
                                    dismissSignal = chartDismissSignal,
                                    centerLabel = stringResource(R.string.conv_starters_center),
                                )
                            } else if (isLoadingSlow) {
                                LoadingSection(progress = progressSlow)
                            } else {
                                val starterTotal =
                                    (s.iStartedConversations + s.theyStartedConversations)
                                        .coerceAtLeast(1)
                                val otherName = if (!s.isGroup) s.contactName else stringResource(R.string.fallback_them)
                                val starterSyn =
                                    listOf(
                                        SenderStat("me", stringResource(R.string.fallback_me), s.iStartedConversations),
                                        SenderStat("other", otherName, s.theyStartedConversations),
                                    )
                                MessagePieChart(
                                    chatId = chatId,
                                    senders = starterSyn,
                                    referenceSenders = starterSyn,
                                    totalMessages = starterTotal,
                                    senderColorArgbOverrides = senderColorArgbOverrides,
                                    onLegendColorClick = { colorPickerSender = it },
                                    dismissSignal = chartDismissSignal,
                                    centerLabel = stringResource(R.string.conv_starters_center),
                                )
                            }
                        }
                    }

                    // ── 9. Milestone Prediction ──────────────────────────────
                    item {
                        MilestonePredictionCard(
                            state = s.milestonePredictionState,
                            periodLabel =
                                if (selectedRange != TimeRange.ALL_TIME) {
                                    stringResource(R.string.prediction_period_filtered_label)
                                } else {
                                    null
                                },
                        )
                    }

                    // ── 10. Analisi Testi (Parole / Emoji / Sticker) ─────────
                    // In Lite mode lo Sticker tab viene nascosto.
                    item {
                        val tabs =
                            if (isLiteMode) {
                                listOf(
                                    stringResource(R.string.tab_words),
                                    stringResource(R.string.tab_emojis),
                                )
                            } else {
                                listOf(
                                    stringResource(R.string.tab_words),
                                    stringResource(R.string.tab_emojis),
                                    stringResource(R.string.tab_stickers),
                                )
                            }
                        var selectedTab by remember { mutableIntStateOf(0) }
                        if (selectedTab >= tabs.size) selectedTab = 0

                        SectionCard(
                            title = stringResource(R.string.text_analysis_title),
                            trailingContent = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    val limits =
                                        when (selectedTab) {
                                            0 -> listOf(10, 20, 30, 50)
                                            1 -> listOf(10, 20, 30, 50)
                                            else -> listOf(10, 20, 30, 50)
                                        }
                                    val currentLimit =
                                        when (selectedTab) {
                                            0 -> topWordsLimit
                                            1 -> topEmojiLimit
                                            else -> topStickerLimit
                                        }
                                    val onLimitChange: (Int) -> Unit =
                                        when (selectedTab) {
                                            0 -> { l -> viewModel.setTopWordsLimit(l) }
                                            1 -> { l -> viewModel.setTopEmojiLimit(l) }
                                            else -> { l -> viewModel.setTopStickerLimit(l) }
                                        }
                                    limits.forEach { limit ->
                                        Box(
                                            modifier =
                                                Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(
                                                        if (currentLimit == limit) {
                                                            MaterialTheme.colorScheme.primary
                                                        } else {
                                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                                        },
                                                    )
                                                    .clickable { onLimitChange(limit) }
                                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                limit.toString(),
                                                style = MaterialTheme.typography.labelMedium,
                                                color =
                                                    if (currentLimit == limit) {
                                                        MaterialTheme.colorScheme.onPrimary
                                                    } else {
                                                        MaterialTheme.colorScheme.onSurfaceVariant
                                                    },
                                            )
                                        }
                                    }
                                }
                            },
                        ) {
                            val sharedIndicator: @Composable (List<androidx.compose.material3.TabPosition>) -> Unit = { tabPositions ->
                                if (selectedTab < tabPositions.size) {
                                    TabRowDefaults.SecondaryIndicator(
                                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                            if (isLiteMode) {
                                TabRow(
                                    selectedTabIndex = selectedTab,
                                    containerColor = Color.Transparent,
                                    contentColor = MaterialTheme.colorScheme.primary,
                                    divider = {},
                                    modifier = Modifier.padding(bottom = 12.dp),
                                    indicator = sharedIndicator,
                                ) {
                                    tabs.forEachIndexed { index, title ->
                                        Tab(
                                            selected = selectedTab == index,
                                            onClick = { selectedTab = index },
                                            text = { Text(title, fontSize = 12.sp) },
                                        )
                                    }
                                }
                            } else {
                                ScrollableTabRow(
                                    selectedTabIndex = selectedTab,
                                    containerColor = Color.Transparent,
                                    contentColor = MaterialTheme.colorScheme.primary,
                                    edgePadding = 0.dp,
                                    divider = {},
                                    modifier = Modifier.padding(bottom = 12.dp),
                                    indicator = sharedIndicator,
                                ) {
                                    tabs.forEachIndexed { index, title ->
                                        Tab(
                                            selected = selectedTab == index,
                                            onClick = { selectedTab = index },
                                            text = { Text(title, fontSize = 12.sp) },
                                        )
                                    }
                                }
                            }

                            val currentViewModeIsIndividual =
                                when (selectedTab) {
                                    0 -> wordViewMode == ChatDetailViewModel.WordViewMode.INDIVIDUAL
                                    1 -> emojiViewMode == ChatDetailViewModel.EmojiViewMode.INDIVIDUAL
                                    else -> stickerViewMode == ChatDetailViewModel.StickerViewMode.INDIVIDUAL
                                }
                            val onSwitchMode: (Boolean) -> Unit = { toIndividual ->
                                when (selectedTab) {
                                    0 ->
                                        viewModel.setWordViewMode(
                                            if (toIndividual) {
                                                ChatDetailViewModel.WordViewMode.INDIVIDUAL
                                            } else {
                                                ChatDetailViewModel.WordViewMode.AGGREGATED
                                            },
                                        )
                                    1 ->
                                        viewModel.setEmojiViewMode(
                                            if (toIndividual) {
                                                ChatDetailViewModel.EmojiViewMode.INDIVIDUAL
                                            } else {
                                                ChatDetailViewModel.EmojiViewMode.AGGREGATED
                                            },
                                        )
                                    else ->
                                        viewModel.setStickerViewMode(
                                            if (toIndividual) {
                                                ChatDetailViewModel.StickerViewMode.INDIVIDUAL
                                            } else {
                                                ChatDetailViewModel.StickerViewMode.AGGREGATED
                                            },
                                        )
                                }
                            }

                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 12.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                                        .padding(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                listOf(
                                    false to stringResource(R.string.view_mode_all),
                                    true to stringResource(R.string.view_mode_person),
                                ).forEach {
                                        (isIndividual, label) ->
                                    Box(
                                        modifier =
                                            Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(
                                                    if (currentViewModeIsIndividual == isIndividual) {
                                                        MaterialTheme.colorScheme.primary
                                                    } else {
                                                        Color.Transparent
                                                    },
                                                )
                                                .clickable { onSwitchMode(isIndividual) }
                                                .padding(vertical = 6.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            label,
                                            style = MaterialTheme.typography.labelLarge,
                                            color =
                                                if (currentViewModeIsIndividual == isIndividual) {
                                                    MaterialTheme.colorScheme.onPrimary
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                },
                                        )
                                    }
                                }
                            }

                            if (currentViewModeIsIndividual) {
                                stats?.let { s ->
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.padding(bottom = 16.dp),
                                    ) {
                                        // "me" viene sempre mostrato per primo nei chip di selezione persona.
                                        // Il dato grezzo (senderBreakdown) non viene alterato per non
                                        // influenzare altre sezioni come "Chi scrive di piu'".
                                        val sendersForChips =
                                            s.senderBreakdown
                                                .sortedWith(compareBy { if (it.jid == "me@s.whatsapp.net") 0 else 1 })
                                        items(sendersForChips) { sender ->
                                            val isSelected =
                                                when (selectedTab) {
                                                    0 -> selectedSenderWords?.jid == sender.jid
                                                    1 -> selectedSenderEmojis?.jid == sender.jid
                                                    else -> selectedSenderStickers?.jid == sender.jid
                                                }
                                            val onSelect: () -> Unit =
                                                when (selectedTab) {
                                                    0 -> {
                                                        { viewModel.setSelectedSenderForWords(sender) }
                                                    }
                                                    1 -> {
                                                        { viewModel.setSelectedSenderForEmojis(sender) }
                                                    }
                                                    else -> {
                                                        { viewModel.setSelectedSenderForStickers(sender) }
                                                    }
                                                }
                                            FilterChip(
                                                selected = isSelected,
                                                onClick = onSelect,
                                                label = {
                                                    ContactNameWithPhone(
                                                        name = sender.displayName,
                                                        jid = sender.jid,
                                                        nameStyle = MaterialTheme.typography.bodyMedium,
                                                        enableHorizontalScroll = false,
                                                    )
                                                },
                                                colors =
                                                    FilterChipDefaults.filterChipColors(
                                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    ),
                                            )
                                        }
                                    }
                                }
                            }

                            stats?.let { s ->
                                when (selectedTab) {
                                    0 -> {
                                        val currentWords =
                                            if (wordViewMode == ChatDetailViewModel.WordViewMode.AGGREGATED) {
                                                s.topWords.take(topWordsLimit.toInt())
                                            } else {
                                                senderTopWords
                                            }
                                        when {
                                            (wordViewMode == ChatDetailViewModel.WordViewMode.INDIVIDUAL && isSenderWordsLoading) ||
                                                (
                                                    wordViewMode == ChatDetailViewModel.WordViewMode.AGGREGATED &&
                                                        isLoadingSlow &&
                                                        currentWords.isEmpty()
                                                ) ->
                                                LoadingSection(
                                                    progress =
                                                        if (wordViewMode ==
                                                            ChatDetailViewModel.WordViewMode.AGGREGATED
                                                        ) {
                                                            progressSlow
                                                        } else {
                                                            null
                                                        },
                                                )
                                            currentWords.isEmpty() ->
                                                Text(
                                                    stringResource(R.string.no_words_found),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(vertical = 24.dp),
                                                )
                                            else ->
                                                InteractiveWordCloud(words = currentWords)
                                        }
                                    }
                                    1 -> {
                                        val currentEmojis =
                                            if (emojiViewMode == ChatDetailViewModel.EmojiViewMode.AGGREGATED) {
                                                s.topEmojis.take(topEmojiLimit.toInt())
                                            } else {
                                                senderTopEmojis
                                            }
                                        when {
                                            (emojiViewMode == ChatDetailViewModel.EmojiViewMode.INDIVIDUAL && isSenderEmojisLoading) ||
                                                (
                                                    emojiViewMode == ChatDetailViewModel.EmojiViewMode.AGGREGATED &&
                                                        isLoadingSlow &&
                                                        currentEmojis.isEmpty()
                                                ) ->
                                                LoadingSection(
                                                    progress =
                                                        if (emojiViewMode ==
                                                            ChatDetailViewModel.EmojiViewMode.AGGREGATED
                                                        ) {
                                                            progressSlow
                                                        } else {
                                                            null
                                                        },
                                                )
                                            currentEmojis.isEmpty() ->
                                                Text(
                                                    stringResource(R.string.no_emojis_found),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(vertical = 24.dp),
                                                )
                                            else ->
                                                InteractiveEmojiCloud(
                                                    emojis = currentEmojis,
                                                    onEmojiClick = { emoji, count ->
                                                        previewEmoji = emoji
                                                        previewEmojiCount = count
                                                    },
                                                )
                                        }
                                    }
                                    2 -> {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(bottom = 8.dp),
                                        ) {
                                            Icon(
                                                Icons.Outlined.Info,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                                modifier = Modifier.size(14.dp),
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text(
                                                stringResource(R.string.sticker_info_hint),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        val currentStickers =
                                            if (stickerViewMode == ChatDetailViewModel.StickerViewMode.AGGREGATED) {
                                                topStickers.take(topStickerLimit)
                                            } else {
                                                senderTopStickers
                                            }
                                        when {
                                            isStickerLoading ->
                                                LoadingSection()
                                            currentStickers.isEmpty() ->
                                                Text(
                                                    stringResource(R.string.no_stickers_found),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(vertical = 24.dp),
                                                )
                                            else ->
                                                InteractiveStickerCloud(
                                                    stickers = currentStickers,
                                                    onStickerClick = { fileName, count ->
                                                        previewSticker = fileName
                                                        previewStickerCount = count
                                                    },
                                                )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ── 11. Action Cards (Scorciatoie) ───────────────────────
                    item {
                        if (stats?.isGroup == true) {
                            if (isLiteMode) {
                                // Lite: mostra solo Radar + Media anche nei gruppi
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    ActionCard(
                                        modifier = Modifier.weight(1f),
                                        title = stringResource(R.string.radar_parole_title),
                                        subtitle = stringResource(R.string.radar_parole_sub),
                                        icon = Icons.Outlined.Search,
                                        onClick = { onNavigateToWordSearch(chatId) },
                                    )
                                    ActionCard(
                                        modifier = Modifier.weight(1f),
                                        title = stringResource(R.string.media_link_title),
                                        subtitle = stringResource(R.string.media_link_sub),
                                        icon = Icons.Outlined.Link,
                                        onClick = { onNavigateToMedia(chatId) },
                                    )
                                }
                            } else {
                                // Root: layout 2x2 invariato
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        ActionCard(
                                            modifier = Modifier.weight(1f),
                                            title = stringResource(R.string.radar_parole_title),
                                            subtitle = stringResource(R.string.radar_parole_sub),
                                            icon = Icons.Outlined.Search,
                                            onClick = { onNavigateToWordSearch(chatId) },
                                        )
                                        ActionCard(
                                            modifier = Modifier.weight(1f),
                                            title = stringResource(R.string.log_chiamate_title),
                                            subtitle = stringResource(R.string.log_chiamate_sub),
                                            icon = Icons.Outlined.Call,
                                            onClick = { onNavigateToCalls(chatId) },
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        ActionCard(
                                            modifier = Modifier.weight(1f),
                                            title = stringResource(R.string.media_link_title),
                                            subtitle = stringResource(R.string.media_link_sub),
                                            icon = Icons.Outlined.Link,
                                            onClick = { onNavigateToMedia(chatId) },
                                        )
                                        ActionCard(
                                            modifier = Modifier.weight(1f),
                                            title = stringResource(R.string.group_history_title),
                                            subtitle = stringResource(R.string.group_history_sub),
                                            icon = Icons.Outlined.History,
                                            onClick = { onNavigateToHistory(chatId) },
                                        )
                                    }
                                }
                            }
                        } else {
                            if (isLiteMode) {
                                // Lite: mostra solo Radar + Media per chat 1:1
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    ActionCard(
                                        modifier = Modifier.weight(1f),
                                        title = stringResource(R.string.radar_parole_title),
                                        subtitle = stringResource(R.string.radar_parole_sub),
                                        icon = Icons.Outlined.Search,
                                        onClick = { onNavigateToWordSearch(chatId) },
                                    )
                                    ActionCard(
                                        modifier = Modifier.weight(1f),
                                        title = stringResource(R.string.media_link_title),
                                        subtitle = stringResource(R.string.media_link_sub),
                                        icon = Icons.Outlined.Link,
                                        onClick = { onNavigateToMedia(chatId) },
                                    )
                                }
                            } else {
                                // Root: layout 1x3 invariato
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    ActionCard(
                                        modifier = Modifier.weight(1f),
                                        title = stringResource(R.string.radar_parole_title),
                                        subtitle = stringResource(R.string.radar_parole_sub),
                                        icon = Icons.Outlined.Search,
                                        onClick = { onNavigateToWordSearch(chatId) },
                                    )
                                    ActionCard(
                                        modifier = Modifier.weight(1f),
                                        title = stringResource(R.string.log_chiamate_title),
                                        subtitle = stringResource(R.string.log_chiamate_sub),
                                        icon = Icons.Outlined.Call,
                                        onClick = { onNavigateToCalls(chatId) },
                                    )
                                    ActionCard(
                                        modifier = Modifier.weight(1f),
                                        title = stringResource(R.string.media_link_title),
                                        subtitle = stringResource(R.string.media_link_sub),
                                        icon = Icons.Outlined.Link,
                                        onClick = { onNavigateToMedia(chatId) },
                                    )
                                }
                            }
                        }
                    }

                    // ── Bottom spacer ────────────────────────────────────────
                    item { Spacer(Modifier.height(32.dp)) }
                } // end LazyColumn
            } // end else if (stats != null)
        } // end Column

        // ── Info Dialog ────────────────────────────────────────
        if (showTopSendersInfoDialog) {
            key(appLang) {
                AlertDialog(
                    onDismissRequest = { showTopSendersInfoDialog = false },
                    title = { Text(stringResource(R.string.top_senders_info_title)) },
                    text = {
                        Text(stringResource(R.string.top_senders_info_desc))
                    },
                    confirmButton = {
                        TextButton(onClick = { showTopSendersInfoDialog = false }) {
                            Text(stringResource(R.string.ho_capito))
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (showInfoDialog) {
            key(appLang) {
                AlertDialog(
                    onDismissRequest = { showInfoDialog = false },
                    title = { Text(stringResource(R.string.info_dialog_title)) },
                    text = {
                        Text(stringResource(R.string.conv_starters_info))
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

        val pickerSender = colorPickerSender
        if (pickerSender != null) {
            SenderColorPickerDialog(
                contactName = pickerSender.displayName,
                onDismiss = { colorPickerSender = null },
                onColorChosen = { argb -> viewModel.setSenderColorOverride(pickerSender.jid, argb) },
                onReset = { viewModel.clearSenderColorOverride(pickerSender.jid) },
            )
        }

        // ── Avatar zoom overlay ────────────────────────────────
        if (showAvatarZoom && zoomAvatarPath != null) {
            com.whatstat.ui.component.ZoomableImageGallery(
                filePaths = listOf(zoomAvatarPath!!),
                initialIndex = 0,
                onDismiss = { showAvatarZoom = false },
            )
        }

        // ── Emoji Preview Modal ──────────────────────────────────
        if (previewEmoji != null) {
            EmojiPreviewModal(
                emoji = previewEmoji!!,
                count = previewEmojiCount,
                onDismiss = { previewEmoji = null },
            )
        }

        // ── Sticker Preview Modal ─────────────────────────────────
        if (previewSticker != null) {
            StickerPreviewModal(
                fileName = previewSticker!!,
                count = previewStickerCount,
                onDismiss = { previewSticker = null },
            )
        }
    } // end Box
}

@Composable
@Suppress("FunctionName")
private fun SectionCard(
    title: String,
    trailingContent: (@Composable () -> Unit)? = null,
    onInfoClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )

                if (trailingContent != null || onInfoClick != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        trailingContent?.invoke()
                        if (onInfoClick != null) {
                            AnimatedInfoButton(
                                onClick = onInfoClick,
                                contentDescription = stringResource(R.string.info_dialog_title),
                                tint = MaterialTheme.colorScheme.primary,
                                glowColor = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
@Suppress("FunctionName")
private fun ActionCard(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
private fun formatDuration(ms: Long): String {
    if (ms <= 0) return "—"
    val hours = TimeUnit.MILLISECONDS.toHours(ms)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
    return when {
        hours > 0 -> "${hours}${stringResource(R.string.unit_hours)} ${minutes}${stringResource(R.string.unit_minutes)}"
        minutes > 0 -> "$minutes ${stringResource(R.string.unit_min_full)}"
        else -> stringResource(R.string.less_than_one_min)
    }
}

// ── Pie Chart messaggi per mittente ──────────────────────────────────────────

@Composable
@Suppress("FunctionName")
private fun MessagePieChart(
    chatId: Long,
    senders: List<SenderStat>,
    referenceSenders: List<SenderStat>,
    totalMessages: Int,
    senderColorArgbOverrides: Map<String, Int>,
    onLegendColorClick: (SenderStat) -> Unit,
    dismissSignal: Int,
    centerLabel: String = stringResource(R.string.messages_label),
) {
    val total = totalMessages.coerceAtLeast(1)
    var expanded by remember { mutableStateOf(false) }
    val collapsedLegendCount = 7
    val showExpand = senders.size > collapsedLegendCount
    val colorChangeLabel = stringResource(R.string.sender_color_change_cd)
    var selectedSliceIndex by remember { mutableIntStateOf(-1) }
    var selectedTapOffset by remember { mutableStateOf<Offset?>(null) }
    var bubbleSizePx by remember { mutableStateOf(IntSize.Zero) }
    var ignoreDismissSignal by remember { mutableIntStateOf(-1) }
    var switchSliceJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()

    val selectedSender = senders.getOrNull(selectedSliceIndex)
    val selectedPct = selectedSender?.let { it.count.toFloat() / total * 100f } ?: 0f
    val selectedSliceOffsets =
        senders.mapIndexed { index, _ ->
            animateDpAsState(
                targetValue = if (index == selectedSliceIndex) 6.dp else 0.dp,
                animationSpec = spring(),
                label = "pie_slice_offset_$index",
            ).value
        }
    val density = LocalDensity.current
    val outerRadiusPx = with(density) { 140.dp.toPx() / 2f }
    val innerRadiusPx = with(density) { outerRadiusPx - 28.dp.toPx() }
    val bubbleVisible = selectedSender != null && selectedTapOffset != null

    LaunchedEffect(senders, totalMessages) {
        switchSliceJob?.cancel()
        selectedSliceIndex = -1
        selectedTapOffset = null
    }

    LaunchedEffect(dismissSignal) {
        if (dismissSignal == ignoreDismissSignal) {
            ignoreDismissSignal = -1
        } else if (selectedSliceIndex != -1) {
            switchSliceJob?.cancel()
            selectedSliceIndex = -1
        }
    }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // ── Donut Chart ──
            BoxWithConstraints(
                modifier =
                    Modifier
                        .size(140.dp)
                        .pointerInput(senders, total) {
                            detectTapGestures { tapOffset ->
                                ignoreDismissSignal = dismissSignal + 1
                                val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
                                val dx = tapOffset.x - center.x
                                val dy = tapOffset.y - center.y
                                val distance = hypot(dx, dy)

                                if (distance < innerRadiusPx || distance > outerRadiusPx) {
                                    switchSliceJob?.cancel()
                                    selectedSliceIndex = -1
                                    return@detectTapGestures
                                }

                                var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() + 90f
                                if (angle < 0f) angle += 360f

                                var runningStart = 0f
                                var tappedIndex = -1
                                for (index in senders.indices) {
                                    val sweep = (senders[index].count.toFloat() / total) * 360f
                                    val end = runningStart + sweep
                                    if (sweep > 0f && angle >= runningStart &&
                                        (angle < end || (index == senders.lastIndex && angle <= end))
                                    ) {
                                        tappedIndex = index
                                        break
                                    }
                                    runningStart = end
                                }

                                if (tappedIndex == -1) {
                                    switchSliceJob?.cancel()
                                    selectedSliceIndex = -1
                                    return@detectTapGestures
                                }

                                if (tappedIndex == selectedSliceIndex) {
                                    switchSliceJob?.cancel()
                                    selectedSliceIndex = -1
                                } else {
                                    val hadActiveSlice = selectedSliceIndex != -1
                                    switchSliceJob?.cancel()
                                    if (hadActiveSlice) {
                                        selectedSliceIndex = -1
                                        switchSliceJob =
                                            scope.launch {
                                                delay(110)
                                                selectedTapOffset = tapOffset
                                                selectedSliceIndex = tappedIndex
                                            }
                                    } else {
                                        selectedTapOffset = tapOffset
                                        selectedSliceIndex = tappedIndex
                                    }
                                }
                            }
                        },
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.foundation.Canvas(
                    modifier = Modifier.size(140.dp),
                ) {
                    val strokeWidth = 28.dp.toPx()
                    val arcSize = size.minDimension - strokeWidth
                    val topLeft =
                        androidx.compose.ui.geometry.Offset(
                            (size.width - arcSize) / 2f,
                            (size.height - arcSize) / 2f,
                        )
                    val arcRect = androidx.compose.ui.geometry.Size(arcSize, arcSize)
                    var startAngle = -90f

                    senders.forEachIndexed { index, sender ->
                        val sweep = (sender.count.toFloat() / total) * 360f
                        val key = senderColorOverrideKey(chatId, sender.jid)
                        val sliceColor =
                            getSenderColor(
                                sender.jid,
                                referenceSenders,
                                senderColorArgbOverrides[key],
                            )
                        val offsetPx = selectedSliceOffsets.getOrNull(index)?.toPx() ?: 0f
                        val midAngle = startAngle + (sweep / 2f)
                        val midAngleRad = Math.toRadians(midAngle.toDouble())
                        val arcOffset =
                            androidx.compose.ui.geometry.Offset(
                                x = cos(midAngleRad).toFloat() * offsetPx,
                                y = sin(midAngleRad).toFloat() * offsetPx,
                            )
                        drawArc(
                            color = sliceColor,
                            startAngle = startAngle,
                            sweepAngle = sweep,
                            useCenter = false,
                            topLeft = topLeft + arcOffset,
                            size = arcRect,
                            style =
                                androidx.compose.ui.graphics.drawscope.Stroke(
                                    width = strokeWidth,
                                    cap = androidx.compose.ui.graphics.StrokeCap.Butt,
                                ),
                        )
                        startAngle += sweep
                    }
                }
                // Totale al centro
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "%,d".format(totalMessages),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        centerLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                val tap = selectedTapOffset ?: Offset.Zero
                val chartWidthPx = with(density) { maxWidth.toPx().roundToInt() }
                val rawBubbleX = tap.x.roundToInt() - (bubbleSizePx.width / 2)
                val bubbleX = rawBubbleX.coerceIn(-24, chartWidthPx - bubbleSizePx.width + 24)
                val bubbleY = tap.y.roundToInt() - bubbleSizePx.height - with(density) { 8.dp.toPx().roundToInt() }

                androidx.compose.animation.AnimatedVisibility(
                    visible = bubbleVisible,
                    enter =
                        fadeIn(animationSpec = tween(120)) +
                            scaleIn(
                                initialScale = 0.92f,
                                animationSpec = tween(140),
                            ),
                    exit =
                        fadeOut(animationSpec = tween(90)) +
                            scaleOut(
                                targetScale = 0.96f,
                                animationSpec = tween(90),
                            ),
                    modifier =
                        Modifier
                            .align(Alignment.TopStart)
                            .offset { IntOffset(bubbleX, bubbleY) }
                            .zIndex(2f),
                ) {
                    PieSliceBubble(
                        onTap = { ignoreDismissSignal = dismissSignal + 1 },
                        senderName = selectedSender?.displayName.orEmpty(),
                        percentage = selectedPct,
                        count = selectedSender?.count ?: 0,
                        onMeasured = { bubbleSizePx = it },
                    )
                }
            }

            Spacer(Modifier.width(16.dp))

            // ── Legenda ──
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val displayCount = if (expanded) senders.size else minOf(senders.size, collapsedLegendCount)
                senders.take(displayCount).forEach { sender ->
                    val pct = (sender.count.toFloat() / total * 100)
                    val key = senderColorOverrideKey(chatId, sender.jid)
                    val legendColor =
                        getSenderColor(
                            sender.jid,
                            referenceSenders,
                            senderColorArgbOverrides[key],
                        )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .semantics { contentDescription = colorChangeLabel }
                                    .size(16.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable { onLegendColorClick(sender) }
                                    .background(legendColor),
                        )
                        Spacer(Modifier.width(6.dp))
                        ContactNameWithPhone(
                            name = sender.displayName,
                            jid = sender.jid,
                            nameStyle = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(6.dp))
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                "%,d".format(sender.count),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                "%.0f%%".format(pct),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        // ── Toggle "Mostra tutti" / "Mostra meno" ──
        if (showExpand) {
            Text(
                text = if (expanded) stringResource(R.string.show_less) else stringResource(R.string.show_more_with_count, senders.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { expanded = !expanded }
                        .padding(top = 8.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
@Suppress("FunctionName")
private fun PieSliceBubble(
    onTap: () -> Unit,
    senderName: String,
    percentage: Float,
    count: Int,
    onMeasured: (IntSize) -> Unit,
) {
    val bubbleColor = MaterialTheme.colorScheme.inverseSurface
    val onBubbleColor = MaterialTheme.colorScheme.inverseOnSurface
    val secondaryColor = onBubbleColor.copy(alpha = 0.7f)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            Modifier
                .onGloballyPositioned { onMeasured(it.size) }
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onTap() })
                },
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = bubbleColor,
            contentColor = onBubbleColor,
            shadowElevation = 6.dp,
            tonalElevation = 4.dp,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.Start,
            ) {
                Text(
                    text = senderName,
                    style = MaterialTheme.typography.labelSmall,
                    color = secondaryColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "%.1f%%".format(percentage),
                    style = MaterialTheme.typography.titleSmall,
                    color = onBubbleColor,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.messages_count_suffix, count),
                    style = MaterialTheme.typography.labelSmall,
                    color = secondaryColor,
                )
            }
        }

        androidx.compose.foundation.Canvas(modifier = Modifier.size(width = 12.dp, height = 8.dp)) {
            val path =
                Path().apply {
                    moveTo(0f, 0f)
                    lineTo(size.width, 0f)
                    lineTo(size.width / 2f, size.height)
                    close()
                }
            drawPath(path = path, color = bubbleColor)
        }
    }
}

@Composable
@Suppress("FunctionName")
private fun StickerItem(
    fileName: String,
    count: Int,
) {
    val context = LocalContext.current
    val stickerPaths =
        listOf(
            "/sdcard/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Stickers",
            "/sdcard/WhatsApp/Media/WhatsApp Stickers",
        )
    val stickerFile =
        remember(fileName) {
            stickerPaths.map { File(it, fileName) }.firstOrNull { it.exists() }
        }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(72.dp),
    ) {
        Box(
            modifier =
                Modifier.size(64.dp).clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (stickerFile != null) {
                AsyncImage(
                    model =
                        ImageRequest.Builder(context).data(stickerFile)
                            .crossfade(true).build(),
                    contentDescription = fileName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Icon(
                    Icons.Outlined.PhotoLibrary,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(32.dp),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "(%d)".format(count),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
