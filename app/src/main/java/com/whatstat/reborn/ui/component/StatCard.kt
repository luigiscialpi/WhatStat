package com.whatstat.reborn.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.delay
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Card con statistiche premium: icona, valore grande e label.
 * Usa sfondo semi-trasparente per un effetto glassmorphism.
 */
@Composable
fun StatCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    subtitle: String? = null,
    icon: ImageVector,
    isSmallText: Boolean = false
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(16.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(10.dp))
        Text(
            value,
            style = if (isSmallText) {
                MaterialTheme.typography.titleMedium
            } else {
                MaterialTheme.typography.headlineSmall
            },
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (subtitle != null) {
            val scrollState = rememberScrollState()
            
            // Logica per auto-scroll che riprende dopo l'interazione
            LaunchedEffect(scrollState.maxValue) {
                if (scrollState.maxValue > 0) {
                    while (true) {
                        // Se l'utente non sta interagendo
                        if (!scrollState.isScrollInProgress) {
                            // Se siamo fermi all'inizio o alla fine, aspetta un momento
                            delay(1500)
                            
                            if (!scrollState.isScrollInProgress) {
                                // Scorri verso la fine
                                scrollState.animateScrollTo(
                                    scrollState.maxValue,
                                    animationSpec = tween(
                                        durationMillis = (scrollState.maxValue * 20),
                                        easing = LinearEasing
                                    )
                                )
                                delay(1500)
                                // Torna all'inizio in modo fluido
                                scrollState.animateScrollTo(0, animationSpec = tween(1000))
                            }
                        }
                        delay(1000) // Ciclo di controllo
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                maxLines = 1,
                modifier = Modifier.horizontalScroll(scrollState)
            )
        }
    }
}
