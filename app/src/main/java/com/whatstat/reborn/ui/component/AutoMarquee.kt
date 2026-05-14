package com.whatstat.reborn.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

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
@Suppress("FunctionName")
fun AutoMarquee(
    modifier: Modifier = Modifier,
    delayMillis: Long = 1500L,
    speedMultiplier: Int = 20,
    content: @Composable () -> Unit,
) {
    val scrollState = rememberScrollState()

    LaunchedEffect(delayMillis, speedMultiplier) {
        var attempts = 0
        while (scrollState.maxValue <= 0 && attempts < 20) {
            delay(100)
            attempts++
        }

        if (scrollState.maxValue <= 0) return@LaunchedEffect

        while (true) {
            if (!scrollState.isScrollInProgress) {
                delay(delayMillis)
                try {
                    scrollState.animateScrollTo(
                        scrollState.maxValue,
                        animationSpec =
                            tween(
                                durationMillis = scrollState.maxValue * speedMultiplier,
                                easing = LinearEasing,
                            ),
                    )
                    delay(delayMillis)
                    scrollState.animateScrollTo(0, animationSpec = tween(1000))
                } catch (e: CancellationException) {
                    // Se il coroutine stesso è stato cancellato (es. composable rimosso),
                    // rilancia per permettere la cancellazione corretta.
                    if (!isActive) throw e
                    // Altrimenti l'animazione è stata interrotta da un gesto dell'utente
                    // o dallo scroll del parent: aspetta che finisca e ricomincia.
                    while (scrollState.isScrollInProgress) delay(200)
                }
            } else {
                delay(200)
            }
        }
    }

    Box(
        modifier = modifier.horizontalScroll(scrollState),
    ) {
        content()
    }
}
