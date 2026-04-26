package com.whatstat.reborn.ui.tutorial.tutorials

import androidx.compose.ui.geometry.Rect
import com.whatstat.reborn.R
import com.whatstat.reborn.ui.tutorial.data.ArrowDirection
import com.whatstat.reborn.ui.tutorial.data.HighlightShape
import com.whatstat.reborn.ui.tutorial.data.ScreenTutorialData
import com.whatstat.reborn.ui.tutorial.data.TutorialStep

/**
 * Tutorial steps for the Chat Detail screen.
 */
object ChatDetailTutorialSteps : ScreenTutorialData {
    override val steps: List<TutorialStep> = listOf(
        TutorialStep(
            id = "chatdetail_1",
            titleResId = R.string.tutorial_chatdetail_filters_title,
            descriptionResId = R.string.tutorial_chatdetail_filters_desc,
            targetKey = "chatdetail_time_filter",
            arrowDirection = ArrowDirection.TOP,
            highlightShape = HighlightShape.FULL_WIDTH
        ),
        TutorialStep(
            id = "chatdetail_2",
            titleResId = R.string.tutorial_chatdetail_tabs_title,
            descriptionResId = R.string.tutorial_chatdetail_tabs_desc,
            targetKey = "chatdetail_tabs",
            arrowDirection = ArrowDirection.BOTTOM,
            highlightShape = HighlightShape.FULL_WIDTH
        ),
        TutorialStep(
            id = "chatdetail_3",
            titleResId = R.string.tutorial_chatdetail_stats_title,
            descriptionResId = R.string.tutorial_chatdetail_stats_desc,
            targetKey = "chatdetail_stat_card",
            arrowDirection = ArrowDirection.LEFT,
            highlightShape = HighlightShape.ROUNDED_RECT
        ),
        TutorialStep(
            id = "chatdetail_4",
            titleResId = R.string.tutorial_chatdetail_activity_title,
            descriptionResId = R.string.tutorial_chatdetail_activity_desc,
            targetKey = "chatdetail_activity_chart",
            arrowDirection = ArrowDirection.LEFT,
            highlightShape = HighlightShape.ROUNDED_RECT
        ),
        TutorialStep(
            id = "chatdetail_5",
            titleResId = R.string.tutorial_chatdetail_wordcloud_title,
            descriptionResId = R.string.tutorial_chatdetail_wordcloud_desc,
            targetKey = "chatdetail_wordcloud",
            arrowDirection = ArrowDirection.BOTTOM,
            highlightShape = HighlightShape.ROUNDED_RECT
        )
    )
}
