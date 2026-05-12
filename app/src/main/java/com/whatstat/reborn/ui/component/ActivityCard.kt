package com.whatstat.reborn.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import com.whatstat.reborn.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import com.whatstat.reborn.data.db.model.SenderStat
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whatstat.reborn.data.db.model.DayCount
import com.whatstat.reborn.data.db.model.DayOfMonthCount
import com.whatstat.reborn.data.db.model.HourlyCount
import com.whatstat.reborn.data.db.model.MonthOfYearCount
import com.whatstat.reborn.data.db.model.YearTrendCount

@Composable
fun ActivityRecapCard(
    hourlyData: List<HourlyCount>,
    weeklyData: List<DayCount>,
    dailyData: List<DayOfMonthCount>,
    monthlyData: List<MonthOfYearCount>,
    yearlyData: List<YearTrendCount>,
    referenceSenders: List<SenderStat> = emptyList(),
    dismissSignal: Int = 0,
    onPointClick: ((String, String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        stringResource(R.string.tab_hourly),
        stringResource(R.string.tab_weekly),
        stringResource(R.string.tab_daily),
        stringResource(R.string.tab_monthly),
        stringResource(R.string.tab_yearly)
    )

    var detailDialogData by remember { mutableStateOf<Triple<String, Int, List<SenderStat>>?>(null) }

    val dayNames = listOf(
        stringResource(R.string.day_sun),
        stringResource(R.string.day_mon),
        stringResource(R.string.day_tue),
        stringResource(R.string.day_wed),
        stringResource(R.string.day_thu),
        stringResource(R.string.day_fri),
        stringResource(R.string.day_sat)
    )
    val monthNames = listOf(
        stringResource(R.string.month_jan),
        stringResource(R.string.month_feb),
        stringResource(R.string.month_mar),
        stringResource(R.string.month_apr),
        stringResource(R.string.month_may),
        stringResource(R.string.month_jun),
        stringResource(R.string.month_jul),
        stringResource(R.string.month_aug),
        stringResource(R.string.month_sep),
        stringResource(R.string.month_oct),
        stringResource(R.string.month_nov),
        stringResource(R.string.month_dec)
    )

    val handlePointClick: (String, String) -> Unit = { title, value ->
        onPointClick?.invoke(title, value)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(bottom = 16.dp)) {
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.primary,
                edgePadding = 16.dp,
                divider = {},
                indicator = { tabPositions ->
                    if (selectedTab < tabPositions.size) {
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                title,
                                fontSize = 12.sp,
                                style = if (selectedTab == index)
                                    MaterialTheme.typography.labelLarge
                                else
                                    MaterialTheme.typography.labelMedium
                            )
                        }
                    )
                }
            }

            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                Text(
                    text = when (selectedTab) {
                        0 -> stringResource(R.string.activity_hourly_title)
                        1 -> stringResource(R.string.activity_weekly_title)
                        2 -> stringResource(R.string.activity_daily_title)
                        3 -> stringResource(R.string.activity_monthly_title)
                        else -> stringResource(R.string.activity_yearly_title)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(16.dp))

                when (selectedTab) {
                    0 -> {
                        val context = androidx.compose.ui.platform.LocalContext.current
                        InteractiveHourlyHeatmap(
                            hourlyData = hourlyData,
                            referenceSenders = referenceSenders,
                            dismissSignal = dismissSignal,
                            onHourClick = { data ->
                                val title = "%02d:00 - %02d:00".format(data.hour, (data.hour + 1) % 24)
                                val value = context.getString(R.string.messages_count_suffix, data.count)
                                handlePointClick(title, value)
                                detailDialogData = Triple(title, data.count, data.senderBreakdown)
                            }
                        )
                    }
                    1 -> {
                        val points = (0..6).map { day ->
                            val data = weeklyData.find { it.dayOfWeek == day }
                            StatPoint(day, dayNames[day], data?.count ?: 0, data?.senderBreakdown ?: emptyList())
                        }
                        val context = androidx.compose.ui.platform.LocalContext.current
                        val dayFullNames = listOf(
                            R.string.day_sunday, R.string.day_monday, R.string.day_tuesday,
                            R.string.day_wednesday, R.string.day_thursday, R.string.day_friday, R.string.day_saturday
                        )
                        InteractiveBarChart(
                            data = points,
                            referenceSenders = referenceSenders,
                            barWidth = 32.dp,
                            spacing = 12.dp,
                            dismissSignal = dismissSignal,
                            onPointClick = { point ->
                                val dayFull = context.getString(dayFullNames[point.id as Int])
                                val value = context.getString(R.string.messages_count_suffix, point.count)
                                handlePointClick(dayFull, value)
                                detailDialogData = Triple(dayFull, point.count, point.senderBreakdown)
                            }
                        )
                    }
                    2 -> {
                        val points = (1..31).map { day ->
                            val data = dailyData.find { it.day == day }
                            StatPoint(day, "$day", data?.count ?: 0, data?.senderBreakdown ?: emptyList())
                        }
                        val context = androidx.compose.ui.platform.LocalContext.current
                        InteractiveBarChart(
                            data = points,
                            referenceSenders = referenceSenders,
                            barWidth = 14.dp,
                            spacing = 6.dp,
                            labelFrequency = 5,
                            dismissSignal = dismissSignal,
                            onPointClick = { point ->
                                val title = context.getString(R.string.click_day_prefix, point.label)
                                val value = context.getString(R.string.messages_count_suffix, point.count)
                                handlePointClick(title, value)
                                detailDialogData = Triple(title, point.count, point.senderBreakdown)
                            }
                        )
                    }
                    3 -> {
                        val points = (1..12).map { month ->
                            val data = monthlyData.find { it.month == month }
                            StatPoint(month, monthNames[month - 1], data?.count ?: 0, data?.senderBreakdown ?: emptyList())
                        }
                        val context = androidx.compose.ui.platform.LocalContext.current
                        val monthFullNames = listOf(
                            R.string.month_january, R.string.month_february, R.string.month_march,
                            R.string.month_april, R.string.month_may_full, R.string.month_june,
                            R.string.month_july, R.string.month_august, R.string.month_september,
                            R.string.month_october, R.string.month_november, R.string.month_december
                        )
                        InteractiveBarChart(
                            data = points,
                            referenceSenders = referenceSenders,
                            barWidth = 24.dp,
                            spacing = 8.dp,
                            dismissSignal = dismissSignal,
                            onPointClick = { point ->
                                val monthFull = context.getString(monthFullNames[point.id as Int - 1])
                                val value = context.getString(R.string.messages_count_suffix, point.count)
                                handlePointClick(monthFull, value)
                                detailDialogData = Triple(monthFull, point.count, point.senderBreakdown)
                            }
                        )
                    }
                    4 -> {
                        val sortedYears = yearlyData.sortedBy { it.year }
                        val points = sortedYears.map {
                            StatPoint(it.year, "${it.year}", it.count, it.senderBreakdown)
                        }
                        val context = androidx.compose.ui.platform.LocalContext.current
                        InteractiveBarChart(
                            data = points,
                            referenceSenders = referenceSenders,
                            barWidth = 48.dp,
                            spacing = 16.dp,
                            dismissSignal = dismissSignal,
                            onPointClick = { point ->
                                val title = context.getString(R.string.click_year_prefix, point.label)
                                val value = context.getString(R.string.messages_count_suffix, point.count)
                                handlePointClick(title, value)
                                detailDialogData = Triple(title, point.count, point.senderBreakdown)
                            }
                        )
                    }
                }
            }
        }
    }

    detailDialogData?.let { (title, total, breakdown) ->
        ActivityDetailDialog(
            title = title,
            totalMessages = total,
            breakdown = breakdown,
            referenceSenders = referenceSenders,
            onDismiss = { detailDialogData = null }
        )
    }
}
