package com.whatstat.reborn.ui.tutorial.tutorials

import androidx.compose.ui.geometry.Rect
import com.whatstat.reborn.R
import com.whatstat.reborn.ui.tutorial.data.ArrowDirection
import com.whatstat.reborn.ui.tutorial.data.HighlightShape
import com.whatstat.reborn.ui.tutorial.data.ScreenTutorialData
import com.whatstat.reborn.ui.tutorial.data.TutorialStep

/**
 * Tutorial steps for the Calls Stats screen.
 */
object CallsTutorialSteps : ScreenTutorialData {
    override val steps: List<TutorialStep> = listOf(
        TutorialStep(
            id = "calls_1",
            titleResId = R.string.tutorial_calls_filters_title,
            descriptionResId = R.string.tutorial_calls_filters_desc,
            targetKey = "calls_time_filter",
            arrowDirection = ArrowDirection.TOP,
            highlightShape = HighlightShape.FULL_WIDTH
        ),
        TutorialStep(
            id = "calls_2",
            titleResId = R.string.tutorial_calls_summary_title,
            descriptionResId = R.string.tutorial_calls_summary_desc,
            targetKey = "calls_summary_card",
            arrowDirection = ArrowDirection.LEFT,
            highlightShape = HighlightShape.ROUNDED_RECT
        ),
        TutorialStep(
            id = "calls_3",
            titleResId = R.string.tutorial_calls_list_title,
            descriptionResId = R.string.tutorial_calls_list_desc,
            targetKey = "calls_list",
            arrowDirection = ArrowDirection.LEFT,
            highlightShape = HighlightShape.FULL_WIDTH
        )
    )
}
