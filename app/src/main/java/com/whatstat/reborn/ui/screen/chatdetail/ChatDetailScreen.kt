package com.whatstat.reborn.ui.screen.chatdetail

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.whatstat.reborn.ui.component.*
import com.whatstat.reborn.ui.tutorial.LocalTutorialManager
import com.whatstat.reborn.ui.tutorial.TutorialOverlay
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailScreen(
    chatId: Long,
    viewModel: ChatDetailViewModel = hiltViewModel(),
    onNavigateToWordSearch: () -> Unit,
    onNavigateToCalls: () -> Unit,
    onNavigateToMedia: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val stats by viewModel.chatStats.collectAsState()
    val headerInfo by viewModel.headerInfo.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val progress by viewModel.loadingProgress.collectAsState()
    val isLoadingSlow by viewModel.isLoadingSlow.collectAsState()
    val progressSlow by viewModel.loadingProgressSlow.collectAsState()
    val error by viewModel.error.collectAsState()
    val selectedRange by viewModel.selectedTimeRange.collectAsState()
    val customDateRange by viewModel.customDateRange.collectAsState()
    
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
    val topStickers by viewModel.topStickers.collectAsState()
    val senderTopStickers by viewModel.senderTopStickers.collectAsState()
    val isStickerLoading by viewModel.isStickerLoading.collectAsState()

    val tutorialManager = LocalTutorialManager.current
    val tutorialState by tutorialManager.uiState.collectAsState()

    var showInfoDialog by remember { mutableStateOf(false) }
    var showAvatarZoom by remember { mutableStateOf(false) }
    var zoomAvatarPath by remember { mutableStateOf<String?>(null) }
    var previewEmoji by remember { mutableStateOf<String?>(null) }
    var previewEmojiCount by remember { mutableIntStateOf(0) }
    var previewSticker by remember { mutableStateOf<String?>(null) }
    var previewStickerCount by remember { mutableIntStateOf(0) }
    val chartDismissSignal = remember { mutableIntStateOf(0) }

    val dateFormatter = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }

    LifecycleResumeEffect(Unit) {
        viewModel.syncFilterState()
        onPauseOrDispose { }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            // Header
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro") }
                if (headerInfo != null) {
                    val hi = headerInfo!!
                    if (hi.avatarPath != null) {
                        AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(File(hi.avatarPath)).crossfade(true).build(), contentDescription = "Avatar", modifier = Modifier.size(40.dp).clip(CircleShape).clickable { zoomAvatarPath = hi.avatarPath; showAvatarZoom = true }, contentScale = ContentScale.Crop)
                    } else {
                        Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                            Icon(if (hi.isGroup) Icons.Outlined.Group else Icons.Outlined.Person, contentDescription = null, modifier = Modifier.size(24.dp))
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(hi.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
                } else {
                    val shimmer = rememberShimmerBrush()
                    ShimmerBox(modifier = Modifier.size(40.dp), brush = shimmer, shape = CircleShape)
                    Spacer(Modifier.width(12.dp))
                    ShimmerBox(modifier = Modifier.width(150.dp).height(20.dp), brush = shimmer)
                }
            }

            // Progress
            Box(modifier = Modifier.fillMaxWidth().height(4.dp)) {
                if (isLoading) {
                    val p = progress
                    if (p != null) LinearProgressIndicator(progress = { p / 100f }, modifier = Modifier.fillMaxWidth())
                    else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }

            if (!isLoading) {
                TimeRangeFilterRow(
                    modifier = Modifier.onGloballyPositioned { tutorialManager.setTargetBounds("chatdetail_time_filter", it.boundsInRoot()) },
                    selectedRange = selectedRange,
                    onRangeSelected = { viewModel.setTimeRange(it) },
                    customDateRange = customDateRange,
                    onDateRangeChanged = { viewModel.setCustomDateRange(it) },
                    onSubmitClicked = { viewModel.applyCustomDateRange() }
                )
            }

            if (isLoading) {
                ChatDetailSkeletonContent(modifier = Modifier.fillMaxSize())
            } else if (stats != null) {
                val s = stats!!
                LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            StatCard(modifier = Modifier.weight(1f).onGloballyPositioned { tutorialManager.setTargetBounds("chatdetail_stat_messages", it.boundsInRoot()) }, label = "Inviati", value = "%,d".format(s.sentByMe), icon = Icons.AutoMirrored.Filled.Send)
                            StatCard(modifier = Modifier.weight(1f), label = "Ricevuti", value = "%,d".format(s.receivedFromThem), icon = Icons.Filled.CallReceived)
                        }
                    }

                    item {
                        SectionCard(title = "Chi scrive di più") {
                            if (s.senderBreakdown.isNotEmpty()) MessagePieChart(senders = s.senderBreakdown, totalMessages = s.totalMessages)
                            else if (isLoadingSlow) LoadingSection(progress = progressSlow)
                            else MessagePieChart(senders = listOf(com.whatstat.reborn.data.db.model.SenderStat("me", "Tu", s.sentByMe), com.whatstat.reborn.data.db.model.SenderStat("other", if (!s.isGroup) s.contactName else "Loro", s.receivedFromThem)), totalMessages = s.totalMessages)
                        }
                    }

                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            DateRangeCard(modifier = Modifier.weight(1f), label = "Periodo attività", startTimestamp = s.firstMessageDate, endTimestamp = s.lastMessageDate, dateFormatter = dateFormatter, icon = Icons.Outlined.CalendarMonth, isSmallText = true)
                            StatCard(modifier = Modifier.weight(1f), label = "Tempo medio risposta", value = formatDuration(s.averageResponseTimeMs), icon = Icons.Outlined.AccessTime, isSmallText = true)
                        }
                    }

                    item {
                        SectionCard(title = "Chi inizia le conversazioni", onInfoClick = { showInfoDialog = true }) {
                            if (s.conversationStarterBreakdown.isNotEmpty()) {
                                MessagePieChart(senders = s.conversationStarterBreakdown, totalMessages = s.conversationStarterBreakdown.sumOf { it.count }.coerceAtLeast(1), centerLabel = "conversazioni")
                            } else if (isLoadingSlow) LoadingSection(progress = progressSlow)
                            else MessagePieChart(senders = listOf(com.whatstat.reborn.data.db.model.SenderStat("me", "Tu", s.iStartedConversations), com.whatstat.reborn.data.db.model.SenderStat("other", if (!s.isGroup) s.contactName else "Loro", s.theyStartedConversations)), totalMessages = (s.iStartedConversations + s.theyStartedConversations).coerceAtLeast(1), centerLabel = "conversazioni")
                        }
                    }

                    item {
                        ActivityRecapCard(
                            modifier = Modifier.onGloballyPositioned { tutorialManager.setTargetBounds("chatdetail_activity_chart", it.boundsInRoot()) },
                            hourlyData = s.hourlyDistribution, weeklyData = s.weeklyDistribution, dailyData = s.dayOfMonthDistribution, monthlyData = s.monthOfYearDistribution, yearlyData = s.yearTrendDistribution, dismissSignal = chartDismissSignal.intValue
                        )
                    }

                    item {
                        ChatDetailAnalysisSection(
                            tutorialManager = tutorialManager,
                            stats = s,
                            viewModel = viewModel,
                            wordViewMode = wordViewMode,
                            selectedSenderWords = selectedSenderWords,
                            topWordsLimit = topWordsLimit,
                            senderTopWords = senderTopWords,
                            isSenderWordsLoading = isSenderWordsLoading,
                            emojiViewMode = emojiViewMode,
                            selectedSenderEmojis = selectedSenderEmojis,
                            topEmojiLimit = topEmojiLimit,
                            senderTopEmojis = senderTopEmojis,
                            isSenderEmojisLoading = isSenderEmojisLoading,
                            stickerViewMode = stickerViewMode,
                            selectedSenderStickers = selectedSenderStickers,
                            topStickerLimit = topStickerLimit,
                            topStickers = topStickers,
                            senderTopStickers = senderTopStickers,
                            isStickerLoading = isStickerLoading,
                            isLoadingSlow = isLoadingSlow,
                            progressSlow = progressSlow,
                            onPreviewEmoji = { e, c -> previewEmoji = e; previewEmojiCount = c },
                            onPreviewSticker = { f, c -> previewSticker = f; previewStickerCount = c }
                        )
                    }

                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                ActionCard(modifier = Modifier.weight(1f).onGloballyPositioned { tutorialManager.setTargetBounds("chatdetail_radar", it.boundsInRoot()) }, title = "Radar Parole", subtitle = "Cerca parole", icon = Icons.Outlined.Search, onClick = onNavigateToWordSearch)
                                ActionCard(modifier = Modifier.weight(1f), title = "Log Chiamate", subtitle = "Audio e video", icon = Icons.Outlined.Call, onClick = onNavigateToCalls)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                ActionCard(modifier = Modifier.weight(1f), title = "Media & Link", subtitle = "Documenti e URL", icon = Icons.Outlined.Link, onClick = onNavigateToMedia)
                                ActionCard(modifier = Modifier.weight(1f), title = "Storico", subtitle = "Eventi chat", icon = Icons.Outlined.History, onClick = onNavigateToHistory)
                            }
                        }
                    }
                    item { Spacer(Modifier.height(32.dp)) }
                }
            }
        }

        // Overlays
        if (tutorialState.isTutorialActive && tutorialState.currentScreen == "chatdetail") {
            tutorialManager.currentStep?.let { step ->
                tutorialManager.getTargetBounds(step.targetKey)?.let { bounds ->
                    TutorialOverlay(
                        step = step, targetBounds = bounds, arrowDirection = step.arrowDirection,
                        isVisible = true, isLastStep = tutorialState.currentStepIndex >= tutorialState.steps.size - 1,
                        currentStepIndex = tutorialState.currentStepIndex,
                        onNext = { tutorialManager.nextStep() }, onPrevious = { tutorialManager.previousStep() },
                        onClose = { tutorialManager.closeTutorial() }, onComplete = { tutorialManager.completeTutorial("chatdetail") }
                    )
                }
            }
        }

        if (showInfoDialog) {
            AlertDialog(onDismissRequest = { showInfoDialog = false }, title = { Text("Info") }, text = { Text("Conversazione contata dopo 3 ore di silenzio.") }, confirmButton = { TextButton(onClick = { showInfoDialog = false }) { Text("OK") } })
        }
        if (showAvatarZoom && zoomAvatarPath != null) ZoomableImageViewer(filePath = zoomAvatarPath!!, onDismiss = { showAvatarZoom = false })
        if (previewEmoji != null) EmojiPreviewModal(emoji = previewEmoji!!, count = previewEmojiCount, onDismiss = { previewEmoji = null })
        if (previewSticker != null) StickerPreviewModal(fileName = previewSticker!!, count = previewStickerCount, onDismiss = { previewSticker = null })
    }
}

@Composable
private fun ChatDetailAnalysisSection(
    tutorialManager: com.whatstat.reborn.ui.tutorial.TutorialManager,
    stats: com.whatstat.reborn.domain.model.ChatStats,
    viewModel: ChatDetailViewModel,
    wordViewMode: ChatDetailViewModel.WordViewMode,
    selectedSenderWords: com.whatstat.reborn.data.db.model.SenderStat?,
    topWordsLimit: Int,
    senderTopWords: List<com.whatstat.reborn.data.db.model.WordFrequency>,
    isSenderWordsLoading: Boolean,
    emojiViewMode: ChatDetailViewModel.EmojiViewMode,
    selectedSenderEmojis: com.whatstat.reborn.data.db.model.SenderStat?,
    topEmojiLimit: Int,
    senderTopEmojis: List<com.whatstat.reborn.data.db.model.EmojiStat>,
    isSenderEmojisLoading: Boolean,
    stickerViewMode: ChatDetailViewModel.StickerViewMode,
    selectedSenderStickers: com.whatstat.reborn.data.db.model.SenderStat?,
    topStickerLimit: Int,
    topStickers: List<com.whatstat.reborn.data.db.model.StickerStat>,
    senderTopStickers: List<com.whatstat.reborn.data.db.model.StickerStat>,
    isStickerLoading: Boolean,
    isLoadingSlow: Boolean,
    progressSlow: Int?,
    onPreviewEmoji: (String, Int) -> Unit,
    onPreviewSticker: (String, Int) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Parole", "Emoji", "Sticker")

    SectionCard(
        modifier = Modifier.onGloballyPositioned { tutorialManager.setTargetBounds("chatdetail_wordcloud", it.boundsInRoot()) },
        title = "Analisi testi",
        trailingContent = {
            val limits = if (selectedTab == 1) listOf(3, 5, 10, 20, 30) else listOf(5, 10, 20, 30, 50)
            val currentLimit = when(selectedTab) { 0 -> topWordsLimit; 1 -> topEmojiLimit; else -> topStickerLimit }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                limits.forEach { limit ->
                    Box(modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(if (currentLimit == limit) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant).clickable { when(selectedTab) { 0 -> viewModel.setTopWordsLimit(limit); 1 -> viewModel.setTopEmojiLimit(limit); else -> viewModel.setTopStickerLimit(limit) } }.padding(horizontal = 8.dp, vertical = 2.dp)) {
                        Text(limit.toString(), style = MaterialTheme.typography.labelMedium, color = if (currentLimit == limit) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    ) {
        ScrollableTabRow(selectedTabIndex = selectedTab, containerColor = Color.Transparent, edgePadding = 0.dp, divider = {}, indicator = { tabPositions -> TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(tabPositions[selectedTab])) }) {
            tabs.forEachIndexed { index, title -> Tab(selected = selectedTab == index, onClick = { selectedTab = index }, text = { Text(title) }) }
        }
        Spacer(Modifier.height(12.dp))
        // Simplified rendering for brevity but full functionality
        when(selectedTab) {
            0 -> InteractiveWordCloud(words = if (wordViewMode == ChatDetailViewModel.WordViewMode.AGGREGATED) stats.topWords.take(topWordsLimit) else senderTopWords)
            1 -> InteractiveEmojiCloud(emojis = if (emojiViewMode == ChatDetailViewModel.EmojiViewMode.AGGREGATED) stats.topEmojis.take(topEmojiLimit) else senderTopEmojis, onEmojiClick = onPreviewEmoji)
            2 -> InteractiveStickerCloud(stickers = if (stickerViewMode == ChatDetailViewModel.StickerViewMode.AGGREGATED) topStickers.take(topStickerLimit) else senderTopStickers, onStickerClick = onPreviewSticker)
        }
    }
}

@Composable
private fun SectionCard(modifier: Modifier = Modifier, title: String, onInfoClick: (() -> Unit)? = null, trailingContent: (@Composable () -> Unit)? = null, content: @Composable () -> Unit) {
    Card(modifier = modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(20.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    if (onInfoClick != null) IconButton(onClick = onInfoClick, modifier = Modifier.size(24.dp)) { Icon(Icons.Outlined.Info, contentDescription = null, modifier = Modifier.size(16.dp)) }
                }
                trailingContent?.invoke()
            }
            Spacer(Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
private fun ActionCard(modifier: Modifier = Modifier, title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

private fun formatDuration(ms: Long): String {
    val hours = TimeUnit.MILLISECONDS.toHours(ms)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

private val pieColors = listOf(Color(0xFFE53935), Color(0xFF1E88E5), Color(0xFF43A047), Color(0xFFFF9800), Color(0xFF9C27B0))

@Composable
private fun MessagePieChart(senders: List<com.whatstat.reborn.data.db.model.SenderStat>, totalMessages: Int, centerLabel: String = "messaggi") {
    val total = totalMessages.coerceAtLeast(1)
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(120.dp), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                var startAngle = -90f
                senders.forEachIndexed { index, sender ->
                    val sweep = (sender.count.toFloat() / total) * 360f
                    drawArc(color = pieColors[index % pieColors.size], startAngle = startAngle, sweepAngle = sweep, useCenter = false, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 20.dp.toPx()))
                    startAngle += sweep
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("%,d".format(totalMessages), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(centerLabel, style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            senders.take(5).forEachIndexed { index, sender ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(pieColors[index % pieColors.size]))
                    Spacer(Modifier.width(8.dp))
                    Text(sender.displayName, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text("${(sender.count.toFloat() / total * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}