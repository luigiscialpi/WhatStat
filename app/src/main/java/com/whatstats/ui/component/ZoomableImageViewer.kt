package com.whatstats.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.whatstats.ui.component.ads.BottomBannerAd
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.abs

/**
 * Full-screen image gallery viewer with zoom, swipe-to-dismiss, filmstrip and auto-hiding UI.
 *
 * UX details:
 * - Single tap toggles top/bottom bars (auto-hide after 3 s of inactivity).
 * - Double tap toggles zoom 1x <-> 2.5x with haptic feedback.
 * - Pinch to zoom up to 5x; pan and two-finger rotation while zoomed.
 * - Horizontal pager swipe disabled while zoomed (prevents accidental page change).
 * - Vertical drag to dismiss: only the image content translates, not the UI bars.
 *   Haptic fires when the dismiss threshold (280 px) is crossed.
 * - Per-page zoom/pan/rotation state resets when leaving a page.
 * - Bottom filmstrip for quick navigation; auto-scrolls to keep current thumbnail visible.
 */
@Composable
@Suppress("FunctionName")
fun ZoomableImageGallery(
    filePaths: List<String>,
    initialIndex: Int,
    onDismiss: () -> Unit,
    onShare: ((String) -> Unit)? = null,
    showNativeAds: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    // Debounce: prevents duplicate share dialogs opened within 1.5 s.
    var lastShareClickTime by remember { mutableStateOf(0L) }

    val imageLoader =
        remember(context) {
            ImageLoader.Builder(context)
                .components {
                    add(RootFileFetcher.Factory())
                    add(ZipFileFetcher.Factory())
                }
                .build()
        }

    val pagerState =
        rememberPagerState(
            initialPage = initialIndex,
            pageCount = { filePaths.size },
        )

    // Whether the currently visible page has scale > 1.
    // Used to lock horizontal pager scrolling while zoomed.
    var currentPageIsZoomed by remember { mutableStateOf(false) }

    // Vertical offset applied to the pager content during a dismiss drag.
    // UI bars are NOT offset — only the image stack moves.
    var dragDismissOffset by remember { mutableFloatStateOf(0f) }
    val animatedDragOffset by animateFloatAsState(
        targetValue = dragDismissOffset,
        animationSpec = spring(),
        label = "dragOffset",
    )
    // Background fades to 30 % opacity when fully dragged.
    val backgroundAlpha = (1f - (abs(dragDismissOffset) / 700f)).coerceIn(0.3f, 1f)

    // UI bars visibility: shown for 3 s after last interaction, then auto-hidden.
    var uiVisible by remember { mutableStateOf(true) }
    var uiInteractionTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(uiInteractionTick) {
        if (uiVisible) {
            delay(3_000L)
            uiVisible = false
        }
    }

    // On page change: reset zoom flag, spring the offset back, refresh auto-hide timer.
    LaunchedEffect(pagerState.currentPage) {
        currentPageIsZoomed = false
        dragDismissOffset = 0f
        uiVisible = true
        uiInteractionTick++
    }

    // Filmstrip: scroll so the active thumbnail stays near the center of the strip.
    val filmstripState = rememberLazyListState()
    LaunchedEffect(pagerState.currentPage) {
        val target = (pagerState.currentPage - 2).coerceAtLeast(0)
        filmstripState.animateScrollToItem(target)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = false,
            ),
    ) {
        Box(
            modifier =
                modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = backgroundAlpha)),
            contentAlignment = Alignment.Center,
        ) {
            // ── Image pager ─────────────────────────────────────────────────────
            // Only this layer translates during the dismiss drag gesture.
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = !currentPageIsZoomed,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { translationY = animatedDragOffset },
            ) { page ->
                ZoomableImageItem(
                    filePath = filePaths[page],
                    imageLoader = imageLoader,
                    isCurrentPage = page == pagerState.currentPage,
                    onZoomChanged = { zoomed ->
                        if (page == pagerState.currentPage) {
                            currentPageIsZoomed = zoomed
                        }
                    },
                    onTap = {
                        // Toggle bar visibility; restart auto-hide countdown if showing.
                        uiVisible = !uiVisible
                        if (uiVisible) uiInteractionTick++
                    },
                    onDismissProgress = { deltaY ->
                        if (!currentPageIsZoomed) {
                            dragDismissOffset += deltaY
                        }
                    },
                    onDismissRelease = {
                        if (abs(dragDismissOffset) > 280f) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onDismiss()
                        } else {
                            // Below threshold: spring back to resting position.
                            dragDismissOffset = 0f
                        }
                    },
                    onDoubleTapHaptic = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                )
            }

            // ── Top bar: close + page counter + share ────────────────────────────
            AnimatedVisibility(
                visible = uiVisible,
                modifier = Modifier.align(Alignment.TopCenter),
                enter = fadeIn(tween(200)) + slideInVertically(tween(200)) { -it },
                exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { -it },
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.statusBars)
                            .background(Color.Black.copy(alpha = 0.45f))
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Chiudi",
                            tint = Color.White,
                        )
                    }
                    // Page counter shown only when there is more than one image.
                    if (filePaths.size > 1) {
                        Text(
                            text = "${pagerState.currentPage + 1} / ${filePaths.size}",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White,
                        )
                    }
                    // Share or empty spacer to keep counter centered.
                    if (onShare != null) {
                        IconButton(
                            onClick = {
                                val now = System.currentTimeMillis()
                                if (now - lastShareClickTime > 1500L) {
                                    lastShareClickTime = now
                                    onShare(filePaths[pagerState.currentPage])
                                }
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Share,
                                contentDescription = "Condividi",
                                tint = Color.White,
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.size(48.dp))
                    }
                }
            }

            // ── Bottom area ──────────────────────────────────────────────────────
            // The ad is ALWAYS visible (never hidden by auto-hide).
            // Only the filmstrip animates in/out with the rest of the UI chrome.
            Column(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        // Static scrim when there is always an ad underneath.
                        .then(
                            if (showNativeAds) {
                                Modifier.background(Color.Black.copy(alpha = 0.45f))
                            } else {
                                Modifier
                            },
                        )
                        .navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Filmstrip: slides in/out with the UI chrome.
                AnimatedVisibility(
                    visible = uiVisible && filePaths.size > 1,
                    enter = fadeIn(tween(200)) + slideInVertically(tween(200)) { it },
                    exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { it },
                ) {
                    // Semi-transparent scrim only when there is no persistent ad below.
                    LazyRow(
                        state = filmstripState,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .then(
                                    if (!showNativeAds) {
                                        Modifier.background(Color.Black.copy(alpha = 0.45f))
                                    } else {
                                        Modifier
                                    },
                                )
                                .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                    ) {
                        items(filePaths.size) { index ->
                            val isSelected = index == pagerState.currentPage
                            val imgData =
                                if (filePaths[index].startsWith("zip://")) {
                                    filePaths[index]
                                } else {
                                    File(filePaths[index])
                                }
                            AsyncImage(
                                model =
                                    ImageRequest.Builder(context)
                                        .data(imgData)
                                        // Small decode target keeps memory footprint low.
                                        .size(160)
                                        .crossfade(true)
                                        .build(),
                                imageLoader = imageLoader,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier =
                                    Modifier
                                        // Selected thumbnail is slightly larger and fully opaque.
                                        .size(if (isSelected) 60.dp else 52.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .then(
                                            if (isSelected) {
                                                Modifier.border(
                                                    width = 2.dp,
                                                    color = Color.White,
                                                    shape = RoundedCornerShape(6.dp),
                                                )
                                            } else {
                                                Modifier.graphicsLayer { alpha = 0.55f }
                                            },
                                        )
                                        .pointerInput(index) {
                                            detectTapGestures {
                                                scope.launch {
                                                    pagerState.animateScrollToPage(index)
                                                }
                                            }
                                        },
                            )
                        }
                    }
                }
                // Ad banner: always rendered, never hidden by auto-hide logic.
                if (showNativeAds) {
                    BottomBannerAd()
                }
            }
        }
    }
}

/**
 * Single page inside [ZoomableImageGallery].
 *
 * Handles independently:
 * - Pinch-to-zoom (1x..5x), two-finger rotation (snaps to 90 deg), pan while zoomed.
 * - Double-tap to toggle zoom 1x <-> 2.5x.
 * - Single-tap to forward the UI-toggle event to the gallery.
 * - Vertical drag forwarded to the gallery for the dismiss gesture (only when not zoomed).
 * - Self-reset of scale/offset/rotation when focus leaves this page.
 */
@Composable
@Suppress("FunctionName")
private fun ZoomableImageItem(
    filePath: String,
    imageLoader: ImageLoader,
    isCurrentPage: Boolean,
    onZoomChanged: (Boolean) -> Unit,
    onTap: () -> Unit,
    onDismissProgress: (Float) -> Unit,
    onDismissRelease: () -> Unit,
    onDoubleTapHaptic: () -> Unit,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var rotation by remember { mutableFloatStateOf(0f) }
    // Pixel dimensions of the composable, captured on first layout.
    // Used to compute tight pan bounds: the image cannot slide more than
    // (containerSize * (scale - 1) / 2) pixels from center in any direction.
    var containerWidth by remember { mutableFloatStateOf(0f) }
    var containerHeight by remember { mutableFloatStateOf(0f) }

    // Reset transform state when this page stops being the visible one.
    LaunchedEffect(isCurrentPage) {
        if (!isCurrentPage) {
            scale = 1f
            offsetX = 0f
            offsetY = 0f
            rotation = 0f
        }
    }

    val animatedScale by animateFloatAsState(targetValue = scale, label = "scale")
    val animatedOffsetX by animateFloatAsState(targetValue = offsetX, label = "offsetX")
    val animatedOffsetY by animateFloatAsState(targetValue = offsetY, label = "offsetY")
    val animatedRotation by animateFloatAsState(
        targetValue = rotation,
        animationSpec = tween(durationMillis = 250),
        label = "rotation",
    )

    // After a two-finger rotation gesture ends, snap to the nearest 90-degree angle.
    LaunchedEffect(rotation) {
        val normalized = ((rotation % 360f) + 360f) % 360f
        val isNotSnapped =
            normalized != 0f && normalized != 90f &&
                normalized != 180f && normalized != 270f
        if (isNotSnapped) {
            delay(150)
            val snapAngle = ((normalized + 45f) / 90f).toInt() * 90f
            rotation = if (snapAngle >= 360f) 0f else snapAngle
        }
    }

    LaunchedEffect(scale) {
        onZoomChanged(scale > 1f)
    }

    // Captured to gate the dismiss-drag pointerInput below.
    val isZoomed = scale > 1f

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .onSizeChanged { size ->
                    containerWidth = size.width.toFloat()
                    containerHeight = size.height.toFloat()
                }
                // Single and double tap detection.
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onTap() },
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
                            onDoubleTapHaptic()
                        },
                    )
                }
                // Vertical swipe-to-dismiss: disabled when the image is zoomed in,
                // because pan uses the same vertical axis.
                .pointerInput(isZoomed) {
                    if (!isZoomed) {
                        detectVerticalDragGestures(
                            onDragEnd = { onDismissRelease() },
                            onDragCancel = { onDismissRelease() },
                            onVerticalDrag = { change, dragAmount ->
                                onDismissProgress(dragAmount)
                                change.consume()
                            },
                        )
                    }
                }
                // Multi-touch: pinch zoom, two-finger rotation, pan while zoomed.
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(pass = PointerEventPass.Initial)
                        val rotationAtStart = rotation
                        var totalRotation = 0f

                        do {
                            val event = awaitPointerEvent()
                            val zoomChange = event.calculateZoom()
                            val rotationChange = event.calculateRotation()
                            val panChange = event.calculatePan()

                            if (zoomChange != 1f) {
                                scale = (scale * zoomChange).coerceIn(1f, 5f)
                            }

                            if (rotationChange != 0f) {
                                totalRotation += rotationChange
                                rotation = rotationAtStart + totalRotation
                            }

                            if (scale > 1f) {
                                // Pan bounds: at a given scale the image expands by
                                // (scale - 1) * containerSize in total, so each half-side
                                // can travel at most that amount from center.
                                val maxOffsetX = containerWidth * (scale - 1f) / 2f
                                val maxOffsetY = containerHeight * (scale - 1f) / 2f
                                offsetX = (offsetX + panChange.x).coerceIn(-maxOffsetX, maxOffsetX)
                                offsetY = (offsetY + panChange.y).coerceIn(-maxOffsetY, maxOffsetY)
                            }
                        } while (event.changes.any { it.pressed })

                        // Gesture ended: if the user pinched back to 1x, snap offset
                        // to center so no black background is visible.
                        if (scale <= 1f) {
                            scale = 1f
                            offsetX = 0f
                            offsetY = 0f
                        }
                    }
                },
        contentAlignment = Alignment.Center,
    ) {
        val context = LocalContext.current
        val imgData = if (filePath.startsWith("zip://")) filePath else File(filePath)
        AsyncImage(
            model =
                ImageRequest.Builder(context)
                    .data(imgData)
                    .crossfade(true)
                    .build(),
            imageLoader = imageLoader,
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
}
