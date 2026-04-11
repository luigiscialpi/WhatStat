package com.whatstat.reborn.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whatstat.reborn.data.db.model.HourlyCount

/**
 * Heatmap oraria: 24 barre verticali colorate in base all'intensità.
 * Le barre più alte / più colorate = ore con più messaggi.
 */
@Composable
fun HourlyHeatmap(
    hourlyData: List<HourlyCount>,
    modifier: Modifier = Modifier
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
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Barra con altezza proporzionale e opacità variabile
                Box(
                    modifier = Modifier
                        .width(10.dp)
                        .height(44.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    // Background molto leggero
                    Box(
                        modifier = Modifier
                            .width(10.dp)
                            .height(44.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(primary.copy(alpha = 0.08f))
                    )
                    // Barra colorata proporzionale
                    Box(
                        modifier = Modifier
                            .width(10.dp)
                            .height((44 * ratio).dp.coerceAtLeast(2.dp))
                            .clip(RoundedCornerShape(4.dp))
                            .background(primary.copy(alpha = 0.15f + ratio * 0.85f))
                    )
                }
                // Label solo ogni 3 ore
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
