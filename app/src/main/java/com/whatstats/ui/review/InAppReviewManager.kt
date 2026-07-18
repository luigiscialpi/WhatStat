package com.whatstats.ui.review

import android.app.Activity
import android.content.Context
import com.google.android.play.core.review.ReviewInfo
import com.google.android.play.core.review.ReviewManagerFactory
import com.whatstats.data.store.AppPreferencesStore
import com.whatstats.util.DebugLogManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InAppReviewManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val appPreferencesStore: AppPreferencesStore,
    ) {
        private val manager = ReviewManagerFactory.create(context)
        private var cachedReviewInfo: ReviewInfo? = null
        private var alreadyRequestedThisSession = false

        /**
         * Pre-carica il ReviewInfo in background.
         * Da chiamare in anticipo (es. quando le statistiche iniziano a caricarsi)
         * cosi' il dialog e' pronto al momento del trigger.
         */
        suspend fun warmUp() {
            if (cachedReviewInfo != null) return
            if (alreadyRequestedThisSession) return
            if (!shouldShowReview()) return

            try {
                cachedReviewInfo = manager.requestReviewFlow().await()
                DebugLogManager.log("InAppReviewManager | ReviewInfo pre-caricato")
            } catch (e: Exception) {
                DebugLogManager.log("InAppReviewManager | Errore pre-caricamento ReviewInfo: ${e.message}")
            }
        }

        /**
         * Lancia il flusso di recensione se elegibile e se il ReviewInfo e' stato pre-caricato.
         * Deve essere chiamato una sola volta per sessione (guarded da alreadyRequestedThisSession).
         */
        suspend fun launchIfEligible(activity: Activity): Boolean {
            if (alreadyRequestedThisSession) return false
            alreadyRequestedThisSession = true

            val reviewInfo = cachedReviewInfo ?: return false
            cachedReviewInfo = null

            if (!shouldShowReview()) return false

            val currentTime = System.currentTimeMillis()
            appPreferencesStore.recordReviewPromptShown(currentTime)

            return try {
                DebugLogManager.log("InAppReviewManager | Richiesta review flow avviata")
                manager.launchReviewFlow(activity, reviewInfo).await()
                DebugLogManager.log("InAppReviewManager | Review flow completato")
                appPreferencesStore.markReviewFlowCompleted()
                true
            } catch (e: Exception) {
                DebugLogManager.log("InAppReviewManager | Errore review flow: ${e.message}")
                false
            }
        }

        private suspend fun shouldShowReview(): Boolean {
            val appLaunchCount = appPreferencesStore.appLaunchCount.first()
            val hasSuccessfulAnalysis = appPreferencesStore.hasSuccessfulAnalysis.first()
            val hasCompletedReview = appPreferencesStore.hasCompletedReviewFlow.first()
            val reviewPromptShownCount = appPreferencesStore.reviewPromptShownCount.first()
            val lastReviewRequestTime = appPreferencesStore.lastReviewRequestTime.first()
            val currentTime = System.currentTimeMillis()

            val eligible =
                InAppReviewRules.shouldShowReview(
                    appLaunchCount = appLaunchCount,
                    hasSuccessfulAnalysis = hasSuccessfulAnalysis,
                    hasCompletedReview = hasCompletedReview,
                    reviewPromptShownCount = reviewPromptShownCount,
                    lastReviewRequestTime = lastReviewRequestTime,
                    currentTime = currentTime,
                )

            if (!eligible) {
                DebugLogManager.log(
                    "InAppReviewManager | Skip: Criteri non soddisfatti (" +
                        "avvii=$appLaunchCount, analizzato=$hasSuccessfulAnalysis, " +
                        "completato=$hasCompletedReview, mostrati=$reviewPromptShownCount)",
                )
            } else {
                DebugLogManager.log("InAppReviewManager | Elegibile per il prompt di recensione")
            }

            return eligible
        }
    }

object InAppReviewRules {
    fun shouldShowReview(
        appLaunchCount: Int,
        hasSuccessfulAnalysis: Boolean,
        hasCompletedReview: Boolean,
        reviewPromptShownCount: Int,
        lastReviewRequestTime: Long,
        currentTime: Long,
    ): Boolean {
        if (hasCompletedReview) return false
        if (reviewPromptShownCount >= 3) return false
        if (appLaunchCount < 3) return false
        if (!hasSuccessfulAnalysis) return false

        val minIntervalMs = 14 * 24 * 60 * 60 * 1000L // 14 giorni
        if (currentTime - lastReviewRequestTime < minIntervalMs) return false

        return true
    }
}
