package com.whatstat.reborn.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import java.io.File

@Composable
@Suppress("FunctionName")
fun ZoomableImageViewer(
    filePath: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var rotation by remember { mutableFloatStateOf(0f) }
    var dragY by remember { mutableFloatStateOf(0f) }
    var backgroundAlpha by remember { mutableFloatStateOf(0.95f) }

    val animatedScale by animateFloatAsState(targetValue = scale, label = "scale")
    val animatedOffsetX by animateFloatAsState(targetValue = offsetX, label = "offsetX")
    val animatedOffsetY by animateFloatAsState(targetValue = offsetY, label = "offsetY")
    val animatedRotation by animateFloatAsState(
        targetValue = rotation,
        animationSpec = tween(durationMillis = 250),
        label = "rotation",
    )

    LaunchedEffect(rotation) {
        val normalizedRotation = ((rotation % 360f) + 360f) % 360f
        if (normalizedRotation != 0f && normalizedRotation != 90f && normalizedRotation != 180f && normalizedRotation != 270f) {
            delay(150)
            val snapAngle = ((normalizedRotation + 45f) / 90f).toInt() * 90f
            val finalSnap = if (snapAngle >= 360f) 0f else snapAngle
            rotation = finalSnap
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
            ),
    ) {
        Box(
            modifier =
                modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = backgroundAlpha))
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragEnd = {
                                if (dragY > 300f) {
                                    onDismiss()
                                } else {
                                    dragY = 0f
                                    backgroundAlpha = 0.95f
                                }
                            },
                            onVerticalDrag = { change, dragAmount ->
                                if (scale <= 1f) {
                                    dragY += dragAmount
                                    if (dragY > 0) {
                                        backgroundAlpha = (0.95f - (dragY / 600f)).coerceIn(0.5f, 0.95f)
                                    }
                                }
                                change.consume()
                            },
                        )
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                if (scale > 1.5f) {
                                    scale = 1f
                                    offsetX = 0f
                                    offsetY = 0f
                                    rotation = 0f
                                } else {
                                    scale = 2.5f
                                    offsetX = 0f
                                    offsetY = 0f
                                }
                            },
                        )
                    }
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val first = awaitFirstDown(pass = PointerEventPass.Initial)
                            val rotationAtStart = rotation
                            var totalRotation = 0f

                            do {
                                val event = awaitPointerEvent()
                                val zoomChange = event.calculateZoom()
                                val rotationChange = event.calculateRotation()
                                val panChange = event.calculatePan()

                                if (zoomChange != 1f) {
                                    val newScale = (scale * zoomChange).coerceIn(1f, 5f)
                                    scale = newScale
                                }

                                if (rotationChange != 0f) {
                                    totalRotation += rotationChange
                                    rotation = rotationAtStart + totalRotation
                                }

                                if (scale > 1f) {
                                    offsetX += panChange.x
                                    offsetY += panChange.y
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    },
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model =
                    ImageRequest.Builder(context)
                        .data(File(filePath))
                        .crossfade(true)
                        .build(),
                contentDescription = "Immagine",
                modifier =
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = animatedScale
                            scaleY = animatedScale
                            translationX = animatedOffsetX
                            translationY = animatedOffsetY
                            rotationZ = animatedRotation
                        },
                contentScale = ContentScale.Fit,
            )
        }

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(16.dp),
            contentAlignment = Alignment.TopEnd,
        ) {
            IconButton(
                onClick = onDismiss,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Chiudi",
                    tint = Color.White,
                )
            }
        }
    }
}
