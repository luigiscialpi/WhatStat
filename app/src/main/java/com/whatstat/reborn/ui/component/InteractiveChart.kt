package com.whatstat.reborn.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whatstat.reborn.data.db.model.HourlyCount
import com.whatstat.reborn.data.db.model.DayCount

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
fun InteractiveDailyBarChart(
    data: List<DayCount>,
    modifier: Modifier = Modifier,
    onDayClick: ((Int, Int) -> Unit)? = null,
    dayLabels: List<String> = listOf("Dom", "Lun", "Mar", "Mer", "Gio", "Ven", "Sab")
) {
    if (data.isEmpty()) return

    val maxCount = data.maxOf { it.count }.toFloat().coerceAtLeast(1f)
    val primary = MaterialTheme.colorScheme.primary

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        (0..6).forEach { day ->
            val count = data.find { it.dayOfWeek == day }?.count ?: 0
            val ratio = count / maxCount

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clickable(enabled = onDayClick != null) {
                        onDayClick?.invoke(day, count)
                    }
                    .padding(vertical = 4.dp)
            ) {
                if (count > 0) {
                    Box(
                        modifier = Modifier
                            .width(32.dp)
                            .height(60.dp),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Box(
                            modifier = Modifier
                                .width(32.dp)
                                .height(60.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(primary.copy(alpha = 0.08f))
                        )
                        Box(
                            modifier = Modifier
                                .width(32.dp)
                                .height((60 * ratio).dp.coerceAtLeast(2.dp))
                                .clip(RoundedCornerShape(4.dp))
                                .background(primary.copy(alpha = 0.15f + ratio * 0.85f))
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .width(32.dp)
                            .height(60.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(primary.copy(alpha = 0.05f))
                    )
                }
                Text(
                    dayLabels.getOrElse(day) { "$day" },
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
