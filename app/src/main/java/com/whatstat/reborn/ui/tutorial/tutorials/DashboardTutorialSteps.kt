package com.whatstat.reborn.ui.tutorial.tutorials

import androidx.compose.ui.geometry.Rect
import com.whatstat.reborn.R
import com.whatstat.reborn.ui.tutorial.data.ArrowDirection
import com.whatstat.reborn.ui.tutorial.data.HighlightShape
import com.whatstat.reborn.ui.tutorial.data.ScreenTutorialData
import com.whatstat.reborn.ui.tutorial.data.TutorialStep

/**
 * Tutorial steps for the Dashboard screen.
 */
object DashboardTutorialSteps : ScreenTutorialData {
    override val steps: List<TutorialStep> = listOf(
        TutorialStep(
            id = "dashboard_1",
            titleResId = R.string.tutorial_dashboard_filters_title,
            descriptionResId = R.string.tutorial_dashboard_filters_desc,
            targetKey = "dashboard_time_filter",
            arrowDirection = ArrowDirection.TOP,
            highlightShape = HighlightShape.FULL_WIDTH
        ),
        TutorialStep(
            id = "dashboard_2",
            titleResId = R.string.tutorial_dashboard_statcard_title,
            descriptionResId = R.string.tutorial_dashboard_statcard_desc,
            targetKey = "dashboard_stat_messages",
            arrowDirection = ArrowDirection.LEFT,
            highlightShape = HighlightShape.ROUNDED_RECT
        ),
        TutorialStep(
            id = "dashboard_3",
            titleResId = R.string.tutorial_dashboard_chart_title,
            descriptionResId = R.string.tutorial_dashboard_chart_desc,
            targetKey = "dashboard_activity_chart",
            arrowDirection = ArrowDirection.LEFT,
            highlightShape = HighlightShape.ROUNDED_RECT
        ),
        TutorialStep(
            id = "dashboard_4",
            titleResId = R.string.tutorial_dashboard_tabs_title,
            descriptionResId = R.string.tutorial_dashboard_tabs_desc,
            targetKey = "dashboard_analysis_tabs",
            arrowDirection = ArrowDirection.BOTTOM,
            highlightShape = HighlightShape.ROUNDED_RECT
        ),
        TutorialStep(
            id = "dashboard_5",
            titleResId = R.string.tutorial_dashboard_analyze_title,
            descriptionResId = R.string.tutorial_dashboard_analyze_desc,
            targetKey = "dashboard_analyze_button",
            arrowDirection = ArrowDirection.TOP,
            highlightShape = HighlightShape.ROUNDED_RECT
        ),
        TutorialStep(
            id = "dashboard_6",
            titleResId = R.string.tutorial_dashboard_wordcloud_title,
            descriptionResId = R.string.tutorial_dashboard_wordcloud_desc,
            targetKey = "dashboard_wordcloud",
            arrowDirection = ArrowDirection.LEFT,
            highlightShape = HighlightShape.ROUNDED_RECT
        )
    )
}
