package com.whatstat.reborn.ui.tutorial.tutorials

import androidx.compose.ui.geometry.Rect
import com.whatstat.reborn.R
import com.whatstat.reborn.ui.tutorial.data.ArrowDirection
import com.whatstat.reborn.ui.tutorial.data.HighlightShape
import com.whatstat.reborn.ui.tutorial.data.ScreenTutorialData
import com.whatstat.reborn.ui.tutorial.data.TutorialStep

/**
 * Tutorial steps for the Media Stats screen.
 * Include only elements present in the UI.
 */
object MediaTutorialSteps : ScreenTutorialData {
    override val steps: List<TutorialStep> = listOf(
        TutorialStep(
            id = "media_1",
            titleResId = R.string.tutorial_media_filters_title,
            descriptionResId = R.string.tutorial_media_filters_desc,
            targetKey = "media_time_filter",
            arrowDirection = ArrowDirection.TOP,
            highlightShape = HighlightShape.FULL_WIDTH
        ),
        TutorialStep(
            id = "media_2",
            titleResId = R.string.tutorial_media_tabs_title,
            descriptionResId = R.string.tutorial_media_tabs_desc,
            targetKey = "media_tabs",
            arrowDirection = ArrowDirection.BOTTOM,
            highlightShape = HighlightShape.FULL_WIDTH
        ),
        TutorialStep(
            id = "media_3",
            titleResId = R.string.tutorial_media_grid_title,
            descriptionResId = R.string.tutorial_media_grid_desc,
            targetKey = "media_grid",
            arrowDirection = ArrowDirection.LEFT,
            highlightShape = HighlightShape.FULL_WIDTH
        )
    )
}
