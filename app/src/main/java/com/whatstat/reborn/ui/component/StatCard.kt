package com.whatstat.reborn.ui.component

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Card con statistiche premium: icona, valore grande e label.
 * Usa sfondo semi-trasparente per un effetto glassmorphism.
 */
@Composable
@Suppress("FunctionName")
fun StatCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    subtitle: String? = null,
    icon: ImageVector,
    isSmallText: Boolean = false,
) {
    val motionSpec =
        remember(label, subtitle, isSmallText) {
            createStatCardMotionSpec("$label|${subtitle.orEmpty()}|$isSmallText")
        }
    val infinite = rememberInfiniteTransition(label = "stat_card_motion")
    val breathProgress =
        infinite.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec =
                infiniteRepeatable(
                    animation =
                        tween(
                            durationMillis = motionSpec.breathDurationMs,
                            easing = FastOutSlowInEasing,
                        ),
                    repeatMode = RepeatMode.Reverse,
                ),
            label = "stat_card_breath",
        ).value
    val pulseProgress =
        infinite.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec =
                infiniteRepeatable(
                    animation =
                        keyframes {
                            durationMillis = motionSpec.pulseCycleMs
                            0f at 0
                            0f at (motionSpec.pulseCycleMs - 420)
                            1f at (motionSpec.pulseCycleMs - 220)
                            0f at motionSpec.pulseCycleMs
                        },
                    repeatMode = RepeatMode.Restart,
                    initialStartOffset = StartOffset(motionSpec.pulsePhaseMs),
                ),
            label = "stat_card_pulse",
        ).value

    val breathCentered = (breathProgress - 0.5f) * 2f
    val cardScale = 1f + (breathCentered * motionSpec.breathScaleAmplitude)
    val iconOffsetY = (breathCentered * motionSpec.iconDriftAmplitudeDp).dp
    val valueScale = 1f + (pulseProgress * motionSpec.valuePulseAmplitude)

    Column(
        modifier =
            modifier
                .graphicsLayer {
                    scaleX = cardScale
                    scaleY = cardScale
                }
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(16.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.offset(y = iconOffsetY),
        )
        Spacer(Modifier.height(10.dp))
        AutoMarquee {
            Text(
                value,
                style =
                    if (isSmallText) {
                        MaterialTheme.typography.titleMedium
                    } else {
                        MaterialTheme.typography.headlineSmall
                    },
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                modifier =
                    Modifier.graphicsLayer {
                        scaleX = valueScale
                        scaleY = valueScale
                    },
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (subtitle != null) {
            Spacer(Modifier.height(4.dp))
            AutoMarquee {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                    maxLines = 1,
                )
            }
        }
    }
}

private data class StatCardMotionSpec(
    val breathDurationMs: Int,
    val breathScaleAmplitude: Float,
    val iconDriftAmplitudeDp: Float,
    val pulseCycleMs: Int,
    val pulsePhaseMs: Int,
    val valuePulseAmplitude: Float,
)

private fun createStatCardMotionSpec(key: String): StatCardMotionSpec {
    val seed = key.fold(0) { acc, c -> (acc * 31) + c.code }.let { if (it == Int.MIN_VALUE) 0 else kotlin.math.abs(it) }
    return StatCardMotionSpec(
        // 3.4s .. 4.8s
        breathDurationMs = 3400 + (seed % 1400),
        // ~0.28% .. 0.455%
        breathScaleAmplitude = 0.0028f + ((seed % 6) * 0.00035f),
        // 0.6dp .. 1.2dp
        iconDriftAmplitudeDp = 0.6f + ((seed % 6) * 0.12f),
        // 5.6s .. 8.2s
        pulseCycleMs = 5600 + (seed % 2600),
        // desincronizzazione iniziale
        pulsePhaseMs = seed % 2000,
        // 1.2% .. 2.4%
        valuePulseAmplitude = 0.012f + ((seed % 5) * 0.003f),
    )
}
