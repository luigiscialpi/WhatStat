package com.whatstat.reborn.ui.tutorial.tutorials

import androidx.compose.ui.geometry.Rect
import com.whatstat.reborn.R
import com.whatstat.reborn.ui.tutorial.data.ArrowDirection
import com.whatstat.reborn.ui.tutorial.data.HighlightShape
import com.whatstat.reborn.ui.tutorial.data.ScreenTutorialData
import com.whatstat.reborn.ui.tutorial.data.TutorialStep

/**
 * Tutorial steps for the Chat List screen.
 */
object ChatListTutorialSteps : ScreenTutorialData {
    override val steps: List<TutorialStep> = listOf(
        TutorialStep(
            id = "chatlist_1",
            titleResId = R.string.tutorial_chatlist_search_title,
            descriptionResId = R.string.tutorial_chatlist_search_desc,
            targetKey = "chatlist_search_field",
            arrowDirection = ArrowDirection.BOTTOM,
            highlightShape = HighlightShape.ROUNDED_RECT
        ),
        TutorialStep(
            id = "chatlist_2",
            titleResId = R.string.tutorial_chatlist_list_title,
            descriptionResId = R.string.tutorial_chatlist_list_desc,
            targetKey = "chatlist_list",
            arrowDirection = ArrowDirection.LEFT,
            highlightShape = HighlightShape.FULL_WIDTH
        ),
        TutorialStep(
            id = "chatlist_3",
            titleResId = R.string.tutorial_chatlist_item_title,
            descriptionResId = R.string.tutorial_chatlist_item_desc,
            targetKey = "chatlist_first_item",
            arrowDirection = ArrowDirection.LEFT,
            highlightShape = HighlightShape.ROUNDED_RECT
        ),
        TutorialStep(
            id = "chatlist_4",
            titleResId = R.string.tutorial_chatlist_badge_title,
            descriptionResId = R.string.tutorial_chatlist_badge_desc,
            targetKey = "chatlist_message_count",
            arrowDirection = ArrowDirection.RIGHT,
            highlightShape = HighlightShape.CIRCLE
        )
    )
}
