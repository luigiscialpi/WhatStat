package com.whatstats.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Genera un [Brush] lineare animato per l'effetto shimmer.
 * Da usare come argomento condiviso tra tutti i componenti skeleton.
 */
@Composable
fun rememberShimmerBrush(): Brush {
    val baseColor = MaterialTheme.colorScheme.surfaceVariant
    val highlightColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
    val shimmerColors = listOf(baseColor, highlightColor, baseColor)

    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateX by transition.animateFloat(
        initialValue = -600f,
        targetValue = 1200f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 1200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "shimmer_x",
    )
    return Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(translateX, 0f),
        end = Offset(translateX + 600f, 0f),
    )
}

/**
 * Box rettangolare con sfondo shimmer e angoli arrotondati.
 */
@Composable
@Suppress("FunctionName")
fun ShimmerBox(
    modifier: Modifier,
    brush: Brush,
    shape: RoundedCornerShape = RoundedCornerShape(6.dp),
) {
    Box(
        modifier =
            modifier
                .clip(shape)
                .background(brush),
    )
}

@Composable
@Suppress("FunctionName")
fun ShimmerTextPlaceholder(
    brush: Brush,
    textStyle: TextStyle,
    widthFraction: Float,
    fontWeight: FontWeight? = null,
) {
    Text(
        text = "M",
        style = textStyle,
        color = Color.Transparent,
        fontWeight = fontWeight,
        maxLines = 1,
        softWrap = false,
        modifier =
            Modifier
                .fillMaxWidth()
                .clearAndSetSemantics {}
                .drawBehind {
                    drawRoundRect(
                        brush = brush,
                        size = Size(width = size.width * widthFraction.coerceIn(0f, 1f), height = size.height),
                        cornerRadius = CornerRadius(6.dp.toPx()),
                    )
                },
    )
}
