package com.whatstat.reborn.ui.tutorial.tutorials

import androidx.compose.ui.geometry.Rect
import com.whatstat.reborn.R
import com.whatstat.reborn.ui.tutorial.data.ArrowDirection
import com.whatstat.reborn.ui.tutorial.data.HighlightShape
import com.whatstat.reborn.ui.tutorial.data.ScreenTutorialData
import com.whatstat.reborn.ui.tutorial.data.TutorialStep

/**
 * Tutorial steps for the Setup screen.
 */
object SetupTutorialSteps : ScreenTutorialData {
    override val steps: List<TutorialStep> = listOf(
        TutorialStep(
            id = "setup_1",
            titleResId = R.string.tutorial_setup_button_title,
            descriptionResId = R.string.tutorial_setup_button_desc,
            targetKey = "setup_start_button",
            arrowDirection = ArrowDirection.BOTTOM,
            highlightShape = HighlightShape.ROUNDED_RECT
        ),
        TutorialStep(
            id = "setup_2",
            titleResId = R.string.tutorial_setup_status_title,
            descriptionResId = R.string.tutorial_setup_status_desc,
            targetKey = "setup_status_indicators",
            arrowDirection = ArrowDirection.LEFT,
            highlightShape = HighlightShape.ROUNDED_RECT
        ),
        TutorialStep(
            id = "setup_3",
            titleResId = R.string.tutorial_setup_privacy_title,
            descriptionResId = R.string.tutorial_setup_privacy_desc,
            targetKey = "setup_privacy_info",
            arrowDirection = ArrowDirection.TOP,
            highlightShape = HighlightShape.ROUNDED_RECT
        )
    )
}
