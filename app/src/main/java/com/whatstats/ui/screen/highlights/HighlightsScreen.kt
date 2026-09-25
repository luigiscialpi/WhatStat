@file:Suppress("FunctionName")

package com.whatstats.ui.screen.highlights

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.WavingHand
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.whatstats.R
import com.whatstats.domain.engagement.CopyVariantPicker
import com.whatstats.domain.model.Highlight
import com.whatstats.ui.component.LoadingSection
import com.whatstats.ui.component.SimpleScreenHeader
import com.whatstats.ui.component.formatReplyDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

private val WEEKDAY_NAMES =
    listOf(
        R.string.day_sunday,
        R.string.day_monday,
        R.string.day_tuesday,
        R.string.day_wednesday,
        R.string.day_thursday,
        R.string.day_friday,
        R.string.day_saturday,
    )

@Composable
fun HighlightsScreen(
    onNavigateBack: () -> Unit,
    viewModel: HighlightsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    Column(
        modifier = Modifier.fillMaxSize().statusBarsPadding().background(MaterialTheme.colorScheme.background),
    ) {
        SimpleScreenHeader(title = stringResource(R.string.highlights_title), onBack = onNavigateBack)
        when (val current = state) {
            HighlightsViewModel.UiState.Loading -> LoadingSection()
            is HighlightsViewModel.UiState.Error -> Message(current.message ?: "", MaterialTheme.colorScheme.error)
            is HighlightsViewModel.UiState.Success ->
                if (current.highlights.isEmpty()) {
                    Message(stringResource(R.string.highlights_empty), MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    val daySeed = remember { LocalDate.now().toEpochDay() }
                    val pagerState = rememberPagerState(pageCount = { current.highlights.size })
                    Column(Modifier.fillMaxSize()) {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 24.dp),
                            pageSpacing = 16.dp,
                        ) { page ->
                            HighlightPage(
                                highlight = current.highlights[page],
                                daySeed = daySeed,
                                onShared = viewModel::onHighlightShared,
                            )
                        }
                        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.Center) {
                            repeat(current.highlights.size) { index ->
                                Spacer(
                                    Modifier
                                        .padding(horizontal = 4.dp)
                                        .height(8.dp)
                                        .width(if (index == pagerState.currentPage) 20.dp else 8.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (index == pagerState.currentPage) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.outlineVariant
                                            },
                                        ),
                                )
                            }
                        }
                    }
                }
        }
    }
}

@Composable
private fun HighlightPage(
    highlight: Highlight,
    daySeed: Long,
    onShared: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val graphicsLayer = rememberGraphicsLayer()
    var lastShareClickTime by remember { mutableLongStateOf(0L) }
    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .weight(1f)
                .drawWithContent {
                    graphicsLayer.record { this@drawWithContent.drawContent() }
                    drawLayer(graphicsLayer)
                }
                .background(MaterialTheme.colorScheme.background),
        ) {
            HighlightCard(highlight, daySeed)
        }
        if (highlight.shareable) {
            OutlinedButton(
                onClick = {
                    val now = System.currentTimeMillis()
                    if (now - lastShareClickTime >= 1_500L) {
                        lastShareClickTime = now
                        scope.launch {
                            val bitmap = graphicsLayer.toImageBitmap().asAndroidBitmap()
                            val uri = writeHighlightPng(context, bitmap)
                            val intent =
                                Intent(Intent.ACTION_SEND).apply {
                                    type = "image/png"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    clipData = ClipData.newRawUri("", uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                            context.startActivity(Intent.createChooser(intent, null))
                            onShared(highlight.key)
                        }
                    }
                },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Icon(Icons.Outlined.Share, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.share))
            }
        }
    }
}

private suspend fun writeHighlightPng(
    context: Context,
    bitmap: Bitmap,
): Uri =
    withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "highlight_share")
        dir.deleteRecursively()
        dir.mkdirs()
        val file = File(dir, "highlight_${System.currentTimeMillis()}.png")
        val watermarkedBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val density = context.resources.displayMetrics.density
        val canvas = Canvas(watermarkedBitmap)

        // Opaque pill behind the label so it reads on both the light and dark
        // card backgrounds, instead of relying on translucent white over the theme color.
        val watermarkText = "WhatStat - Made by L.Scialpi"
        val textPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFFFFFFF.toInt()
                textAlign = Paint.Align.CENTER
                textSize = 12f * density
            }
        val fontMetrics = textPaint.fontMetrics
        val textWidth = textPaint.measureText(watermarkText)
        val textHeight = fontMetrics.descent - fontMetrics.ascent

        val horizontalPillPadding = 12f * density
        val verticalPillPadding = 6f * density
        val pillWidth = textWidth + horizontalPillPadding * 2f
        val pillHeight = textHeight + verticalPillPadding * 2f

        // HighlightCard applies Modifier.padding(vertical = 24.dp) before drawing itself,
        // so its bottom edge sits 24dp above this bitmap's bottom edge. Inset the pill a
        // bit further so it lands inside the card rather than in that outer margin.
        val cardBottomMargin = 24f * density
        val insideCardInset = 16f * density
        val pillBottom = watermarkedBitmap.height - cardBottomMargin - insideCardInset
        val pillTop = pillBottom - pillHeight
        val pillLeft = (watermarkedBitmap.width - pillWidth) / 2f
        val pillRight = pillLeft + pillWidth

        val pillPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xB3000000.toInt()
            }
        canvas.drawRoundRect(pillLeft, pillTop, pillRight, pillBottom, pillHeight / 2f, pillHeight / 2f, pillPaint)

        val textBaseline = pillBottom - verticalPillPadding - fontMetrics.descent
        canvas.drawText(watermarkText, watermarkedBitmap.width / 2f, textBaseline, textPaint)

        file.outputStream().use { watermarkedBitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

@Composable
private fun HighlightCard(
    highlight: Highlight,
    daySeed: Long,
) {
    val content = highlightContent(highlight)
    Card(
        Modifier.fillMaxSize().padding(vertical = 24.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(content.icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(16.dp))
            Text(content.title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(
                content.value,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            content.detail?.let {
                Spacer(Modifier.height(16.dp))
                Text(it, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
            }
            if (content.quips.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text(
                    content.quips[CopyVariantPicker.pick(highlight.key, daySeed, content.quips.size)],
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private class HighlightContent(
    val icon: ImageVector,
    val title: String,
    val value: String,
    val quips: List<String>,
    val detail: String? = null,
)

@Composable
private fun highlightContent(highlight: Highlight): HighlightContent =
    when (highlight) {
        is Highlight.MessagesSent ->
            HighlightContent(
                Icons.Outlined.ChatBubbleOutline,
                stringResource(R.string.highlight_messages_sent_title),
                "%,d".format(highlight.count),
                listOf(
                    stringResource(R.string.highlight_messages_sent_quip_1),
                    stringResource(R.string.highlight_messages_sent_quip_2),
                    stringResource(R.string.highlight_messages_sent_quip_3),
                ),
            )
        is Highlight.PeakHour ->
            HighlightContent(
                Icons.Outlined.Schedule,
                stringResource(R.string.highlight_peak_hour_title),
                "%02d:00".format(highlight.hour),
                listOf(
                    stringResource(R.string.highlight_peak_hour_quip_1),
                    stringResource(R.string.highlight_peak_hour_quip_2),
                    stringResource(R.string.highlight_peak_hour_quip_3),
                ),
            )
        is Highlight.TopWeekday ->
            HighlightContent(
                Icons.Outlined.CalendarMonth,
                stringResource(R.string.highlight_top_weekday_title),
                stringResource(WEEKDAY_NAMES[highlight.dayOfWeek]),
                listOf(
                    stringResource(R.string.highlight_top_weekday_quip_1),
                    stringResource(R.string.highlight_top_weekday_quip_2),
                    stringResource(R.string.highlight_top_weekday_quip_3),
                ),
            )
        is Highlight.NightShare ->
            HighlightContent(
                Icons.Outlined.Bedtime,
                stringResource(R.string.highlight_night_share_title),
                stringResource(R.string.engagement_percent_value, highlight.percent),
                listOf(
                    stringResource(R.string.highlight_night_share_quip_1),
                    stringResource(R.string.highlight_night_share_quip_2),
                    stringResource(R.string.highlight_night_share_quip_3),
                ),
            )
        is Highlight.PeakTimeBlock ->
            HighlightContent(
                Icons.Outlined.Schedule,
                stringResource(R.string.highlight_peak_time_block_title),
                stringResource(R.string.highlight_peak_time_block_value, highlight.startHour, highlight.endHour),
                listOf(
                    stringResource(R.string.highlight_peak_time_block_quip_1),
                    stringResource(R.string.highlight_peak_time_block_quip_2),
                    stringResource(R.string.highlight_peak_time_block_quip_3),
                ),
            )
        is Highlight.IntenseDay ->
            HighlightContent(
                Icons.Outlined.CalendarMonth,
                stringResource(R.string.highlight_intense_day_title),
                stringResource(
                    R.string.highlight_intense_day_value,
                    stringResource(WEEKDAY_NAMES[highlight.dayOfWeek]),
                    highlight.count,
                ),
                listOf(
                    stringResource(R.string.highlight_intense_day_quip_1),
                    stringResource(R.string.highlight_intense_day_quip_2),
                    stringResource(R.string.highlight_intense_day_quip_3),
                ),
            )
        is Highlight.WeekendShare ->
            HighlightContent(
                Icons.Outlined.DateRange,
                stringResource(R.string.highlight_weekend_share_title),
                stringResource(R.string.highlight_weekend_share_value, highlight.percent),
                listOf(
                    stringResource(R.string.highlight_weekend_share_quip_1),
                    stringResource(R.string.highlight_weekend_share_quip_2),
                    stringResource(R.string.highlight_weekend_share_quip_3),
                ),
            )
        is Highlight.ActivityPeriod ->
            HighlightContent(
                Icons.Outlined.WbSunny,
                stringResource(R.string.highlight_activity_period_title),
                stringResource(
                    R.string.highlight_activity_period_value,
                    stringResource(
                        if (highlight.isEvening) R.string.activity_period_evening else R.string.activity_period_day,
                    ),
                    highlight.percent,
                ),
                listOf(
                    stringResource(R.string.highlight_activity_period_quip_1),
                    stringResource(R.string.highlight_activity_period_quip_2),
                    stringResource(R.string.highlight_activity_period_quip_3),
                ),
            )
        is Highlight.FastestReplier ->
            HighlightContent(
                Icons.Outlined.Speed,
                stringResource(R.string.highlight_fastest_replier_title),
                highlight.name,
                emptyList(),
                stringResource(R.string.highlight_reply_average_detail, formatReplyDuration(highlight.averageMs)),
            )
        is Highlight.YouReplyFastest ->
            HighlightContent(
                Icons.Outlined.Bolt,
                stringResource(R.string.highlight_you_reply_fastest_title),
                highlight.name,
                emptyList(),
                stringResource(R.string.highlight_reply_average_detail, formatReplyDuration(highlight.averageMs)),
            )
        is Highlight.YouStartMost ->
            HighlightContent(
                Icons.Outlined.WavingHand,
                stringResource(R.string.highlight_you_start_most_title),
                highlight.name,
                emptyList(),
                stringResource(R.string.highlight_you_start_most_detail, highlight.percent),
            )
    }

@Composable
private fun Message(
    text: String,
    color: Color,
) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = color, textAlign = TextAlign.Center)
    }
}
