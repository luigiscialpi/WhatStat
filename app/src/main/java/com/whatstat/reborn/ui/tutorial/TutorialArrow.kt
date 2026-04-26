package com.whatstat.reborn.ui.tutorial

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.whatstat.reborn.ui.tutorial.data.ArrowDirection

/**
 * Composable that draws a directional arrow (triangle).
 *
 * @param direction The direction the arrow should point
 * @param color Color of the arrow
 * @param size Size of the arrow (both width and height)
 * @param modifier Modifier for the Canvas
 */
@Composable
fun TutorialArrow(
    direction: ArrowDirection,
    color: Color = Color.White,
    size: Dp = 24.dp,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(size)) {
        drawArrow(direction, color)
    }
}

private fun DrawScope.drawArrow(direction: ArrowDirection, color: Color) {
    val path = Path()
    val width = size.width
    val height = size.height

    when (direction) {
        ArrowDirection.TOP -> {
            path.moveTo(width / 2, 0f)
            path.lineTo(0f, height)
            path.lineTo(width, height)
        }
        ArrowDirection.BOTTOM -> {
            path.moveTo(0f, 0f)
            path.lineTo(width, 0f)
            path.lineTo(width / 2, height)
        }
        ArrowDirection.LEFT -> {
            path.moveTo(width, 0f)
            path.lineTo(0f, height / 2)
            path.lineTo(width, height)
        }
        ArrowDirection.RIGHT -> {
            path.moveTo(0f, 0f)
            path.lineTo(width, height / 2)
            path.lineTo(0f, height)
        }
    }
    path.close()

    drawPath(path = path, color = color)
}
