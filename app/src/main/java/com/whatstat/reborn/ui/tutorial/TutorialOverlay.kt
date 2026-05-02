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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.whatstat.reborn.ui.tutorial.data.ArrowDirection
import com.whatstat.reborn.ui.tutorial.data.HighlightShape
import com.whatstat.reborn.ui.tutorial.data.TutorialStep
import kotlin.math.roundToInt

@Composable
fun TutorialOverlay(
    step: TutorialStep,
    targetBounds: Rect,
    arrowDirection: ArrowDirection,
    isVisible: Boolean,
    isLastStep: Boolean,
    currentStepIndex: Int,
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
    // Stima iniziale per il primo frame — verrà sostituita dopo la prima misura
    val tooltipWidthEstimatePx = with(density) { 280.dp.toPx() }
    val tooltipHeightEstimatePx = with(density) { 220.dp.toPx() }
    val minMarginPx = with(density) { 8.dp.toPx() }

    // Dimensioni reali del tooltip misurate dopo il primo layout pass
    var tooltipSize by remember { mutableStateOf(IntSize.Zero) }

    val measuredWidth = if (tooltipSize.width > 0) tooltipSize.width.toFloat() else tooltipWidthEstimatePx
    val measuredHeight = if (tooltipSize.height > 0) tooltipSize.height.toFloat() else tooltipHeightEstimatePx

    val tooltipOffset = calculateTooltipOffset(
        targetBounds = targetBounds,
        arrowDirection = arrowDirection,
        screenWidth = screenWidthPx,
        screenHeight = screenHeightPx,
        tooltipWidth = measuredWidth,
        tooltipHeight = measuredHeight,
        arrowSize = arrowSizePx,
        spacing = spacingPx,
        minMargin = minMarginPx
    )

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
                currentStepIndex = currentStepIndex,
                onNext = onNext,
                onPrevious = onPrevious,
                onClose = {
                    if (isLastStep) onComplete() else onClose()
                },
                modifier = Modifier.onGloballyPositioned { coordinates ->
                    tooltipSize = coordinates.size
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

    // Semantica corretta (dal piano sezione 6.1):
    // TOP   = freccia punta in su  → tooltip SOTTO il target
    // BOTTOM = freccia punta in giù → tooltip SOPRA il target
    // LEFT  = freccia punta a sx   → tooltip A DESTRA del target
    // RIGHT = freccia punta a dx   → tooltip A SINISTRA del target
    when (arrowDirection) {
        ArrowDirection.TOP -> {
            y = targetBounds.bottom + arrowSize + spacing
            x = targetBounds.center.x - tooltipWidth / 2
        }
        ArrowDirection.BOTTOM -> {
            y = targetBounds.top - tooltipHeight - arrowSize - spacing
            x = targetBounds.center.x - tooltipWidth / 2
        }
        ArrowDirection.LEFT -> {
            x = targetBounds.right + arrowSize + spacing
            y = targetBounds.center.y - tooltipHeight / 2
        }
        ArrowDirection.RIGHT -> {
            x = targetBounds.left - tooltipWidth - arrowSize - spacing
            y = targetBounds.center.y - tooltipHeight / 2
        }
    }

    val maxX = maxOf(minMargin, screenWidth - tooltipWidth - minMargin)
    val maxY = maxOf(minMargin, screenHeight - tooltipHeight - minMargin)
    x = x.coerceIn(minMargin, maxX)
    y = y.coerceIn(minMargin, maxY)

    return androidx.compose.ui.geometry.Offset(x, y)
}
