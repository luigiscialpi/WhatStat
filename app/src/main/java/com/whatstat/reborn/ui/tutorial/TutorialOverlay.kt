package com.whatstat.reborn.ui.tutorial

import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.whatstat.reborn.ui.tutorial.data.ArrowDirection
import com.whatstat.reborn.ui.tutorial.data.HighlightShape
import com.whatstat.reborn.ui.tutorial.data.TutorialStep
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun TutorialOverlay(
    step: TutorialStep,
    targetBounds: Rect,
    arrowDirection: ArrowDirection,
    isVisible: Boolean,
    isLastStep: Boolean,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onClose: () -> Unit,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current

    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    val spotlightBorderWidthPx = with(density) { 2.dp.toPx() }
    val cornerRadiusPx = with(density) { 16.dp.toPx() }
    val arrowSizePx = with(density) { 24.dp.toPx() }
    val spacingPx = with(density) { 16.dp.toPx() }
    val tooltipWidthPx = with(density) { 280.dp.toPx() }
    val tooltipHeightPx = with(density) { 180.dp.toPx() }
    val minMarginPx = with(density) { 8.dp.toPx() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .alpha(if (isVisible) 1f else 0f)
            .pointerInput(Unit) {
                detectTapGestures { /* consume */ }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawBackdropWithSpotlight(
                targetBounds = targetBounds,
                backdropColor = Color.Black.copy(alpha = 0.75f),
                spotlightBorderColor = Color.White.copy(alpha = 0.3f),
                spotlightBorderWidth = spotlightBorderWidthPx,
                highlightShape = step.highlightShape,
                cornerRadius = cornerRadiusPx
            )
        }

        val tooltipOffset = calculateTooltipOffset(
            targetBounds = targetBounds,
            arrowDirection = arrowDirection,
            screenWidth = screenWidthPx,
            screenHeight = screenHeightPx,
            tooltipWidth = tooltipWidthPx,
            tooltipHeight = tooltipHeightPx,
            arrowSize = arrowSizePx,
            spacing = spacingPx,
            minMargin = minMarginPx
        )

        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(tween(350)),
            exit = fadeOut(tween(150)),
            modifier = Modifier.offset {
                IntOffset(
                    x = tooltipOffset.x.roundToInt(),
                    y = tooltipOffset.y.roundToInt()
                )
            }
        ) {
            TutorialTooltip(
                step = step,
                arrowDirection = arrowDirection,
                isLastStep = isLastStep,
                onNext = onNext,
                onPrevious = onPrevious,
                onClose = {
                    if (isLastStep) onComplete() else onClose()
                }
            )
        }
    }
}

private fun DrawScope.drawBackdropWithSpotlight(
    targetBounds: Rect,
    backdropColor: Color,
    spotlightBorderColor: Color,
    spotlightBorderWidth: Float,
    highlightShape: HighlightShape,
    cornerRadius: Float
) {
    val canvas = drawContext.canvas.nativeCanvas
    val saveCount = canvas.saveLayer(null, null)

    // Backdrop paint
    val backdropPaint = android.graphics.Paint().apply {
        color = backdropColor.toArgb()
        style = android.graphics.Paint.Style.FILL
        isAntiAlias = true
    }

    canvas.drawRect(0f, 0f, size.width, size.height, backdropPaint)

    // Clear paint for the hole
    val clearPaint = android.graphics.Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        isAntiAlias = true
    }

    // Cut out spotlight
    when (highlightShape) {
        HighlightShape.ROUNDED_RECT -> {
            canvas.drawRoundRect(
                android.graphics.RectF(targetBounds.left, targetBounds.top, targetBounds.right, targetBounds.bottom),
                cornerRadius,
                cornerRadius,
                clearPaint
            )
        }
        HighlightShape.CIRCLE -> {
            val radius = minOf(targetBounds.width, targetBounds.height) / 2
            canvas.drawCircle(targetBounds.center.x, targetBounds.center.y, radius, clearPaint)
        }
        HighlightShape.FULL_WIDTH -> {
            canvas.drawRect(targetBounds.left, targetBounds.top, targetBounds.right, targetBounds.bottom, clearPaint)
        }
    }

    canvas.restoreToCount(saveCount)

    // Draw border
    if (highlightShape != HighlightShape.FULL_WIDTH) {
        val borderPaint = android.graphics.Paint().apply {
            color = spotlightBorderColor.toArgb()
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = spotlightBorderWidth
            isAntiAlias = true
        }
        when (highlightShape) {
            HighlightShape.ROUNDED_RECT -> {
                canvas.drawRoundRect(
                    android.graphics.RectF(targetBounds.left, targetBounds.top, targetBounds.right, targetBounds.bottom),
                    cornerRadius,
                    cornerRadius,
                    borderPaint
                )
            }
            HighlightShape.CIRCLE -> {
                val radius = minOf(targetBounds.width, targetBounds.height) / 2
                canvas.drawCircle(targetBounds.center.x, targetBounds.center.y, radius, borderPaint)
            }
            else -> {}
        }
    }
}

private fun calculateTooltipOffset(
    targetBounds: Rect,
    arrowDirection: ArrowDirection,
    screenWidth: Float,
    screenHeight: Float,
    tooltipWidth: Float,
    tooltipHeight: Float,
    arrowSize: Float,
    spacing: Float,
    minMargin: Float
): androidx.compose.ui.geometry.Offset {
    var x = targetBounds.center.x - tooltipWidth / 2
    var y = targetBounds.center.y - tooltipHeight / 2

    when (arrowDirection) {
        ArrowDirection.TOP -> {
            y = targetBounds.top - tooltipHeight - arrowSize - spacing
            x = targetBounds.center.x - tooltipWidth / 2
        }
        ArrowDirection.BOTTOM -> {
            y = targetBounds.bottom + arrowSize + spacing
            x = targetBounds.center.x - tooltipWidth / 2
        }
        ArrowDirection.LEFT -> {
            x = targetBounds.left - tooltipWidth - arrowSize - spacing
            y = targetBounds.center.y - tooltipHeight / 2
        }
        ArrowDirection.RIGHT -> {
            x = targetBounds.right + arrowSize + spacing
            y = targetBounds.center.y - tooltipHeight / 2
        }
    }

    x = x.coerceIn(minMargin, screenWidth - tooltipWidth - minMargin)
    y = y.coerceIn(minMargin, screenHeight - tooltipHeight - minMargin)

    return androidx.compose.ui.geometry.Offset(x, y)
}
