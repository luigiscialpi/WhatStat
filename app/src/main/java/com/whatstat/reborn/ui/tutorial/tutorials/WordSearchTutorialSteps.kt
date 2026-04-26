package com.whatstat.reborn.ui.tutorial.tutorials

import androidx.compose.ui.geometry.Rect
import com.whatstat.reborn.R
import com.whatstat.reborn.ui.tutorial.data.ArrowDirection
import com.whatstat.reborn.ui.tutorial.data.HighlightShape
import com.whatstat.reborn.ui.tutorial.data.ScreenTutorialData
import com.whatstat.reborn.ui.tutorial.data.TutorialStep

/**
 * Tutorial steps for the Word Search (Radar) screen.
 */
object WordSearchTutorialSteps : ScreenTutorialData {
    override val steps: List<TutorialStep> = listOf(
        TutorialStep(
            id = "wordsearch_1",
            titleResId = R.string.tutorial_wordsearch_search_title,
            descriptionResId = R.string.tutorial_wordsearch_search_desc,
            targetKey = "wordsearch_search_field",
            arrowDirection = ArrowDirection.BOTTOM,
            highlightShape = HighlightShape.ROUNDED_RECT
        ),
        TutorialStep(
            id = "wordsearch_2",
            titleResId = R.string.tutorial_wordsearch_results_title,
            descriptionResId = R.string.tutorial_wordsearch_results_desc,
            targetKey = "wordsearch_results",
            arrowDirection = ArrowDirection.LEFT,
            highlightShape = HighlightShape.FULL_WIDTH
        )
    )
}
