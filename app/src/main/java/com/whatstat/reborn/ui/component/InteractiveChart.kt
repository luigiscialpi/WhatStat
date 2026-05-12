package com.whatstat.reborn.ui.component

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.whatstat.reborn.data.db.model.SenderStat
import com.whatstat.reborn.data.db.model.DayCount
import com.whatstat.reborn.data.db.model.HourlyCount

@Composable
fun InteractiveHourlyHeatmap(
    hourlyData: List<HourlyCount>,
    modifier: Modifier = Modifier,
    referenceSenders: List<SenderStat> = emptyList(),
    dismissSignal: Int = 0,
    onHourClick: ((HourlyCount) -> Unit)? = null
) {
    if (hourlyData.isEmpty()) return

    val maxCount = hourlyData.maxOf { it.count }.toFloat().coerceAtLeast(1f)
    val primary = MaterialTheme.colorScheme.primary
    var selectedHour by remember { mutableIntStateOf(-1) }
    LaunchedEffect(dismissSignal) { if (dismissSignal > 0) selectedHour = -1 }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        (0..23).forEach { hour ->
            val data = hourlyData.find { it.hour == hour }
            val count = data?.count ?: 0
            val ratio = count / maxCount
            val isSelected = selectedHour == hour

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clickable {
                        selectedHour = if (isSelected) -1 else hour
                    }
                    .padding(vertical = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(10.dp)
                        .height(44.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    if (isSelected) {
                        Popup(
                            alignment = Alignment.BottomCenter,
                            offset = androidx.compose.ui.unit.IntOffset(0, -50),
                            properties = PopupProperties(focusable = false)
                        ) {
                            val data = hourlyData.find { it.hour == hour }
                            ChartPointBubble(
                                title = "%02d:00 - %02d:00".format(hour, (hour + 1) % 24),
                                total = count,
                                breakdown = data?.senderBreakdown ?: emptyList(),
                                referenceSenders = referenceSenders,
                                onClick = { if (data != null) onHourClick?.invoke(data) }
                            )
                        }
                    }

                    if (count > 0) {
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
                    } else {
                        Box(
                            modifier = Modifier
                                .width(10.dp)
                                .height(44.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(primary.copy(alpha = 0.05f))
                        )
                    }
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
    referenceSenders: List<SenderStat> = emptyList(),
    barWidth: Dp = 20.dp,
    barHeight: Dp = 60.dp,
    spacing: Dp = 8.dp,
    showLabels: Boolean = true,
    labelFrequency: Int = 1,
    onPointClick: ((StatPoint) -> Unit)? = null,
    dismissSignal: Int = 0
) {
    if (data.isEmpty()) return

    val maxCount = data.maxOf { it.count }.toFloat().coerceAtLeast(1f)
    val primary = MaterialTheme.colorScheme.primary
    val scrollState = rememberScrollState()
    var selectedIndex by remember { mutableIntStateOf(-1) }
    LaunchedEffect(dismissSignal) { if (dismissSignal > 0) selectedIndex = -1 }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(spacing)
    ) {
        data.forEachIndexed { index, point ->
            val ratio = point.count / maxCount
            val isSelected = selectedIndex == index

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clickable {
                        selectedIndex = if (isSelected) -1 else index
                    }
                    .padding(vertical = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(barWidth)
                        .height(barHeight),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    if (isSelected) {
                        Popup(
                            alignment = Alignment.BottomCenter,
                            offset = androidx.compose.ui.unit.IntOffset(0, -70),
                            properties = PopupProperties(focusable = false)
                        ) {
                            ChartPointBubble(
                                title = point.label,
                                total = point.count,
                                breakdown = point.senderBreakdown,
                                referenceSenders = referenceSenders,
                                onClick = { onPointClick?.invoke(point) }
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .width(barWidth)
                            .height(barHeight)
                            .clip(RoundedCornerShape(4.dp))
                            .background(primary.copy(alpha = 0.08f))
                    )
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
                    Spacer(Modifier.height(14.dp))
                }
            }
        }
    }
}

data class StatPoint(
    val id: Any,
    val label: String,
    val count: Int,
    val senderBreakdown: List<SenderStat> = emptyList()
)

@Composable
private fun ChartPointBubble(
    title: String,
    total: Int,
    breakdown: List<SenderStat>,
    referenceSenders: List<SenderStat> = emptyList(),
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val bubbleColor = MaterialTheme.colorScheme.inverseSurface
    val onBubbleColor = MaterialTheme.colorScheme.inverseOnSurface
    val secondaryColor = onBubbleColor.copy(alpha = 0.7f)

    Column(
        modifier = modifier.clickable { onClick?.invoke() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.widthIn(min = 80.dp, max = 180.dp),
            shape = RoundedCornerShape(12.dp),
            color = bubbleColor,
            contentColor = onBubbleColor,
            shadowElevation = 6.dp,
            tonalElevation = 4.dp
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = secondaryColor,
                    fontSize = 10.sp
                )
                Text(
                    text = "$total messaggi",
                    style = MaterialTheme.typography.labelMedium,
                    color = onBubbleColor,
                    fontSize = 13.sp
                )

                if (breakdown.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(onBubbleColor.copy(alpha = 0.15f))
                    )
                    Spacer(Modifier.height(6.dp))

                    breakdown.take(3).forEach { sender ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(com.whatstat.reborn.ui.theme.getSenderColor(sender.jid, referenceSenders))
                            )
                            Spacer(Modifier.width(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = sender.displayName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = secondaryColor,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "${sender.count}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = onBubbleColor,
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }
                    }

                    if (breakdown.size > 3) {
                        Text(
                            text = "+ Altri ${breakdown.size - 3}...",
                            style = MaterialTheme.typography.labelSmall,
                            color = secondaryColor,
                            fontSize = 9.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }

        Canvas(modifier = Modifier.size(width = 12.dp, height = 8.dp)) {
            val path = Path().apply {
                moveTo(0f, 0f)
                lineTo(size.width, 0f)
                lineTo(size.width / 2f, size.height)
                close()
            }
            drawPath(path = path, color = bubbleColor)
        }
    }
}
