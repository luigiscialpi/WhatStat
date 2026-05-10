package com.whatstat.reborn.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay

/**
 * Wrapper che aggiunge lo scorrimento orizzontale automatico (Marquee) al contenuto.
 * Se il contenuto eccede lo spazio disponibile, inizierà a scorrere dopo un delay.
 * 
 * @param modifier Modifier da applicare al Box contenitore
 * @param delayMillis Ritardo prima di iniziare lo scorrimento e tra un ciclo e l'altro
 * @param speedMultiplier Moltiplicatore per la durata dell'animazione (più alto = più lento)
 * @param content Il contenuto che deve scorrere
 */
@Composable
fun AutoMarquee(
    modifier: Modifier = Modifier,
    delayMillis: Long = 1500L,
    speedMultiplier: Int = 20,
    content: @Composable () -> Unit
) {
    val scrollState = rememberScrollState()

    LaunchedEffect(scrollState.maxValue) {
        if (scrollState.maxValue > 0) {
            while (true) {
                // Se l'utente non sta interagendo manualmente
                if (!scrollState.isScrollInProgress) {
                    delay(delayMillis)
                    
                    if (!scrollState.isScrollInProgress) {
                        // Scorrimento verso la fine
                        scrollState.animateScrollTo(
                            scrollState.maxValue,
                            animationSpec = tween(
                                durationMillis = (scrollState.maxValue * speedMultiplier),
                                easing = LinearEasing
                            )
                        )
                        
                        delay(delayMillis)
                        
                        // Ritorno fluido all'inizio
                        scrollState.animateScrollTo(0, animationSpec = tween(1000))
                    }
                }
                delay(1000) // Ciclo di controllo
            }
        }
    }

    Box(
        modifier = modifier.horizontalScroll(scrollState)
    ) {
        content()
    }
}
