package com.whatstat.reborn.ui.tutorial

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.compositionLocalOf
import androidx.lifecycle.ViewModel

/**
 * CompositionLocal that provides the TutorialManager to screens.
 */
val LocalTutorialManager = compositionLocalOf<TutorialManager> {
    error("TutorialManager not provided. Wrap your screen with TutorialManagerProvider.")
}

/**
 * Provides the TutorialManager to the composition.
 */
@Composable
fun ProvideTutorialManager(
    tutorialManager: TutorialManager,
    content: @Composable () -> Unit
) {
    androidx.compose.runtime.CompositionLocalProvider(
        LocalTutorialManager provides tutorialManager
    ) {
        content()
    }
}
