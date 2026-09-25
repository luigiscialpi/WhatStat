@file:Suppress("FunctionName")

package com.whatstats.ui.screen.highlights

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.whatstats.R
import com.whatstats.domain.engagement.CopyVariantPicker
import com.whatstats.domain.model.Highlight
import com.whatstats.ui.component.LoadingSection
import com.whatstats.ui.component.SimpleScreenHeader
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
                        ) { page -> HighlightCard(current.highlights[page], daySeed) }
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
private fun HighlightCard(
    highlight: Highlight,
    daySeed: Long,
) {
    val content = highlightContent(highlight)
    val quips = content.quips
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
            Spacer(Modifier.height(16.dp))
            Text(
                quips[CopyVariantPicker.pick(highlight.key, daySeed, quips.size)],
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private class HighlightContent(
    val icon: ImageVector,
    val title: String,
    val value: String,
    val quips: List<String>,
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
