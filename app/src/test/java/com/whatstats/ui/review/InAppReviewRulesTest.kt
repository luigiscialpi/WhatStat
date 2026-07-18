package com.whatstats.ui.review

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InAppReviewRulesTest {
    @Test
    fun shouldShowReview_whenAllCriteriaMet_returnsTrue() {
        // > 14 giorni
        val eligible =
            InAppReviewRules.shouldShowReview(
                appLaunchCount = 3,
                hasSuccessfulAnalysis = true,
                hasCompletedReview = false,
                reviewPromptShownCount = 0,
                lastReviewRequestTime = 0L,
                currentTime = 14 * 24 * 60 * 60 * 1000L + 1,
            )
        assertTrue(eligible)
    }

    @Test
    fun shouldShowReview_whenAlreadyReviewed_returnsFalse() {
        val eligible =
            InAppReviewRules.shouldShowReview(
                appLaunchCount = 5,
                hasSuccessfulAnalysis = true,
                hasCompletedReview = true,
                reviewPromptShownCount = 1,
                lastReviewRequestTime = 0L,
                currentTime = 100000L,
            )
        assertFalse(eligible)
    }

    @Test
    fun shouldShowReview_whenInsufficientLaunches_returnsFalse() {
        val eligible =
            InAppReviewRules.shouldShowReview(
                appLaunchCount = 2,
                hasSuccessfulAnalysis = true,
                hasCompletedReview = false,
                reviewPromptShownCount = 0,
                lastReviewRequestTime = 0L,
                currentTime = 100000L,
            )
        assertFalse(eligible)
    }

    @Test
    fun shouldShowReview_whenNoSuccessfulAnalysis_returnsFalse() {
        val eligible =
            InAppReviewRules.shouldShowReview(
                appLaunchCount = 4,
                hasSuccessfulAnalysis = false,
                hasCompletedReview = false,
                reviewPromptShownCount = 0,
                lastReviewRequestTime = 0L,
                currentTime = 100000L,
            )
        assertFalse(eligible)
    }

    @Test
    fun shouldShowReview_whenTooManyPrompts_returnsFalse() {
        val eligible =
            InAppReviewRules.shouldShowReview(
                appLaunchCount = 4,
                hasSuccessfulAnalysis = true,
                hasCompletedReview = false,
                reviewPromptShownCount = 3,
                lastReviewRequestTime = 0L,
                currentTime = 14 * 24 * 60 * 60 * 1000L + 1,
            )
        assertFalse(eligible)
    }

    @Test
    fun shouldShowReview_whenTooFrequent_returnsFalse() {
        // solo 5 giorni dopo
        val eligible =
            InAppReviewRules.shouldShowReview(
                appLaunchCount = 4,
                hasSuccessfulAnalysis = true,
                hasCompletedReview = false,
                reviewPromptShownCount = 1,
                lastReviewRequestTime = 1000L,
                currentTime = 1000L + 5 * 24 * 60 * 60 * 1000L,
            )
        assertFalse(eligible)
    }
}
