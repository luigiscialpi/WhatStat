@file:Suppress("FunctionName")

package com.whatstats.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.whatstats.R
import kotlinx.coroutines.launch

/**
 * ScrollToEdgeFab - Floating Action Button riusabile per lo scroll rapido.
 * Supporta LazyListState (per LazyColumn) e LazyGridState (per LazyVerticalGrid).
 * Compare con un'animazione scale+fade quando viene superata la soglia specificata.
 */
@Composable
fun ScrollToEdgeFab(
    listState: LazyListState,
    modifier: Modifier = Modifier,
    threshold: Int = 3,
    reverseScroll: Boolean = false,
) {
    val coroutineScope = rememberCoroutineScope()
    val showButton by remember {
        derivedStateOf {
            if (reverseScroll) {
                // Nel caso di reverseLayout, mostriamo il pulsante se l'indice corrente
                // e' maggiore della soglia a partire dal fondo (indicando che l'utente ha scroolato in alto).
                listState.firstVisibleItemIndex > threshold
            } else {
                listState.firstVisibleItemIndex > threshold
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomEnd,
    ) {
        AnimatedVisibility(
            visible = showButton,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = modifier,
        ) {
            SmallFloatingActionButton(
                onClick = {
                    coroutineScope.launch {
                        if (reverseScroll) {
                            // Scorri all'inizio della lista (che per reverseLayout = true e' l'item piu' recente in fondo)
                            listState.animateScrollToItem(0)
                        } else {
                            listState.animateScrollToItem(0)
                        }
                    }
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Icon(
                    imageVector = if (reverseScroll) Icons.Outlined.KeyboardArrowDown else Icons.Outlined.KeyboardArrowUp,
                    contentDescription = if (reverseScroll) "Scorri in basso" else "Scorri in alto",
                )
            }
        }
    }
}

/**
 * Overload di ScrollToEdgeFab per LazyGridState (usato in LazyVerticalGrid).
 */
@Composable
fun ScrollToEdgeFab(
    gridState: LazyGridState,
    modifier: Modifier = Modifier,
    threshold: Int = 3,
) {
    val coroutineScope = rememberCoroutineScope()
    val showButton by remember {
        derivedStateOf {
            gridState.firstVisibleItemIndex > threshold
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomEnd,
    ) {
        AnimatedVisibility(
            visible = showButton,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = modifier,
        ) {
            SmallFloatingActionButton(
                onClick = {
                    coroutineScope.launch {
                        gridState.animateScrollToItem(0)
                    }
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Icon(
                    imageVector = Icons.Outlined.KeyboardArrowUp,
                    contentDescription = stringResource(R.string.scroll_to_top),
                )
            }
        }
    }
}
