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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    dismissSignal: Int = 0,
    onPointClick: ((String, String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Oraria", "Settimanale", "Giornaliera", "Mensile", "Annuale")

    val dayNames = listOf("Dom", "Lun", "Mar", "Mer", "Gio", "Ven", "Sab")
    val monthNames = listOf(
        "Gen", "Feb", "Mar", "Apr", "Mag", "Giu",
        "Lug", "Ago", "Set", "Ott", "Nov", "Dic"
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
                        0 -> "Distribuzione oraria"
                        1 -> "Attività settimanale"
                        2 -> "Giorni del mese (1-31)"
                        3 -> "Stagionalità (Gen-Dic)"
                        else -> "Trend negli anni"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(16.dp))

                when (selectedTab) {
                    0 -> {
                        InteractiveHourlyHeatmap(
                            hourlyData = hourlyData,
                            dismissSignal = dismissSignal,
                            onHourClick = { hour, count ->
                                handlePointClick("Ore $hour:00", "$count messaggi")
                            }
                        )
                    }
                    1 -> {
                        val points = (0..6).map { day ->
                            val count = weeklyData.find { it.dayOfWeek == day }?.count ?: 0
                            StatPoint(day, dayNames[day], count)
                        }
                        InteractiveBarChart(
                            data = points,
                            barWidth = 32.dp,
                            spacing = 12.dp,
                            dismissSignal = dismissSignal,
                            onPointClick = { point ->
                                val dayFull = listOf(
                                    "Domenica", "Lunedì", "Martedì",
                                    "Mercoledì", "Giovedì", "Venerdì", "Sabato"
                                )[point.id as Int]
                                handlePointClick(dayFull, "${point.count} messaggi")
                            }
                        )
                    }
                    2 -> {
                        val points = (1..31).map { day ->
                            val count = dailyData.find { it.day == day }?.count ?: 0
                            StatPoint(day, "$day", count)
                        }
                        InteractiveBarChart(
                            data = points,
                            barWidth = 14.dp,
                            spacing = 6.dp,
                            labelFrequency = 5,
                            dismissSignal = dismissSignal,
                            onPointClick = { point ->
                                handlePointClick("Giorno ${point.label}", "${point.count} messaggi")
                            }
                        )
                    }
                    3 -> {
                        val points = (1..12).map { month ->
                            val count = monthlyData.find { it.month == month }?.count ?: 0
                            StatPoint(month, monthNames[month - 1], count)
                        }
                        InteractiveBarChart(
                            data = points,
                            barWidth = 24.dp,
                            spacing = 8.dp,
                            dismissSignal = dismissSignal,
                            onPointClick = { point ->
                                val monthFull = listOf(
                                    "Gennaio", "Febbraio", "Marzo", "Aprile", "Maggio", "Giugno",
                                    "Luglio", "Agosto", "Settembre", "Ottobre", "Novembre", "Dicembre"
                                )[point.id as Int - 1]
                                handlePointClick(monthFull, "${point.count} messaggi")
                            }
                        )
                    }
                    4 -> {
                        val sortedYears = yearlyData.sortedBy { it.year }
                        val points = sortedYears.map {
                            StatPoint(it.year, "${it.year}", it.count)
                        }
                        InteractiveBarChart(
                            data = points,
                            barWidth = 48.dp,
                            spacing = 16.dp,
                            dismissSignal = dismissSignal,
                            onPointClick = { point ->
                                handlePointClick("Anno ${point.label}", "${point.count} messaggi")
                            }
                        )
                    }
                }
            }
        }
    }
}