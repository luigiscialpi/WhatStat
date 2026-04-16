package com.whatstat.reborn.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whatstat.reborn.data.db.model.DayCount
import com.whatstat.reborn.data.db.model.HourlyCount

@Composable
fun InteractiveHourlyHeatmap(
    hourlyData: List<HourlyCount>,
    modifier: Modifier = Modifier,
    onHourClick: ((Int, Int) -> Unit)? = null
) {
    if (hourlyData.isEmpty()) return

    val maxCount = hourlyData.maxOf { it.count }.toFloat().coerceAtLeast(1f)
    val primary = MaterialTheme.colorScheme.primary

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        (0..23).forEach { hour ->
            val count = hourlyData.find { it.hour == hour }?.count ?: 0
            val ratio = count / maxCount

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clickable(enabled = onHourClick != null) {
                        onHourClick?.invoke(hour, count)
                    }
                    .padding(vertical = 4.dp)
            ) {
                if (count > 0) {
                    Box(
                        modifier = Modifier
                            .width(10.dp)
                            .height(44.dp),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Box(
                            modifier = Modifier
                                .width(10.dp)
                                .height(44.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(primary.copy(alpha = 0.08f))
                        )
                        Box(
                            modifier = Modifier
                                .width(10.dp)
                                .height((44 * ratio).dp.coerceAtLeast(2.dp))
                                .clip(RoundedCornerShape(4.dp))
                                .background(primary.copy(alpha = 0.15f + ratio * 0.85f))
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .width(10.dp)
                            .height(44.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(primary.copy(alpha = 0.05f))
                    )
                }
                if (hour % 3 == 0) {
                    Text(
                        "%02d".format(hour),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 8.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun InteractiveBarChart(
    data: List<StatPoint>,
    modifier: Modifier = Modifier,
    barWidth: Dp = 12.dp,
    barHeight: Dp = 60.dp,
    spacing: Dp = 8.dp,
    showLabels: Boolean = true,
    labelFrequency: Int = 1,
    onPointClick: ((StatPoint) -> Unit)? = null
) {
    if (data.isEmpty()) return

    val maxCount = data.maxOf { it.count }.toFloat().coerceAtLeast(1f)
    val primary = MaterialTheme.colorScheme.primary
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(spacing)
    ) {
        data.forEachIndexed { index, point ->
            val ratio = point.count / maxCount

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clickable(enabled = onPointClick != null) {
                        onPointClick?.invoke(point)
                    }
                    .padding(vertical = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(barWidth)
                        .height(barHeight),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    // Background bar
                    Box(
                        modifier = Modifier
                            .width(barWidth)
                            .height(barHeight)
                            .clip(RoundedCornerShape(4.dp))
                            .background(primary.copy(alpha = 0.08f))
                    )
                    // Active bar
                    if (point.count > 0) {
                        Box(
                            modifier = Modifier
                                .width(barWidth)
                                .height((barHeight.value * ratio).dp.coerceAtLeast(2.dp))
                                .clip(RoundedCornerShape(4.dp))
                                .background(primary.copy(alpha = 0.15f + ratio * 0.85f))
                        )
                    }
                }
                
                if (showLabels && (index % labelFrequency == 0)) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        point.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                } else if (showLabels) {
                    Spacer(Modifier.height(14.dp)) // Placeholder for alignment
                }
            }
        }
    }
}

data class StatPoint(
    val id: Any, // Original index or key
    val label: String,
    val count: Int
)
