@file:Suppress("FunctionName")

package com.whatstats.ui.component

import androidx.compose.animation.core.EaseOutQuad
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Reusable premium IconButton that displays an animated icon (defaulting to Icons.Outlined.Info)
 * with a continuous heartbeat scale breath and a glowing sonar ripple effect behind it.
 * This draws the user's attention quickly and organically without cluttering the UI.
 *
 * @param onClick Triggered when the button is clicked.
 * @param modifier Optional modifier to apply to the IconButton.
 * @param icon The ImageVector icon to draw (defaults to Icons.Outlined.Info).
 * @param contentDescription Description for screen readers and accessibility.
 * @param iconSize Size of the inner icon (default is 24.dp).
 * @param tint Color tint for the icon itself. Defaults to primary color to increase visual prominence.
 * @param glowColor Color of the expanding sonar wave behind the icon. Defaults to primary color.
 */
@Composable
fun AnimatedInfoButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Outlined.Info,
    contentDescription: String? = null,
    iconSize: Dp = 24.dp,
    tint: Color = MaterialTheme.colorScheme.primary,
    glowColor: Color = MaterialTheme.colorScheme.primary,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "sonar_button")

    // Gentle heartbeat breath effect for the icon scale
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "icon_scale",
    )

    // Sonar ring scale that expands outward
    val echoScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.8f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 2000, easing = EaseOutQuad),
                repeatMode = RepeatMode.Restart,
            ),
        label = "echo_scale",
    )

    // Sonar ring opacity that fades to zero
    val echoAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.0f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 2000, easing = EaseOutQuad),
                repeatMode = RepeatMode.Restart,
            ),
        label = "echo_alpha",
    )

    IconButton(
        onClick = onClick,
        modifier = modifier,
    ) {
        // Reserve drawing space for the halo wave
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(iconSize * 2),
        ) {
            // Draw the growing sonar echo behind the icon
            Box(
                modifier =
                    Modifier
                        .size(iconSize)
                        .drawBehind {
                            drawCircle(
                                color = glowColor,
                                radius = (size.minDimension / 2) * echoScale,
                                alpha = echoAlpha,
                            )
                        },
            )

            // Draw the scaling central icon
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier =
                    Modifier
                        .size(iconSize)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        },
            )
        }
    }
}
