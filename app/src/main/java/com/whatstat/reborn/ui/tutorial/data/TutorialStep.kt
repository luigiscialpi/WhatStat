package com.whatstat.reborn.ui.tutorial.data

import androidx.compose.ui.geometry.Rect

/**
 * Data class representing a single tutorial step.
 *
 * @param id Unique identifier for the step (e.g., "dashboard_1")
 * @param titleResId String resource ID for the step title
 * @param descriptionResId String resource ID for the step description
 * @param targetKey Key used to identify the target composable via onGloballyPositioned
 * @param arrowDirection Position of the tooltip arrow relative to the target
 * @param highlightShape Shape of the spotlight cutout
 * @param targetBounds Optional pre-calculated bounds; will be updated via onGloballyPositioned
 */
data class TutorialStep(
    val id: String,
    val titleResId: Int,
    val descriptionResId: Int,
    val targetKey: String,
    val arrowDirection: ArrowDirection,
    val highlightShape: HighlightShape = HighlightShape.ROUNDED_RECT,
    var targetBounds: Rect? = null
)

enum class ArrowDirection {
    TOP,
    BOTTOM,
    LEFT,
    RIGHT
}

enum class HighlightShape {
    ROUNDED_RECT,
    CIRCLE,
    FULL_WIDTH
}
