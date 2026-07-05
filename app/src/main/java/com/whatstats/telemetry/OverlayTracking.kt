package com.whatstats.telemetry

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Helper Composable per tracciare overlay (dialog, modal, bottom sheet) che non
 * sono destinazioni di navigazione e quindi non generano screen_view_custom.
 *
 * Recupera [TelemetryRepository] via Hilt EntryPoint (stesso pattern di
 * DbThumbnailFetcher) per evitare di passare MainViewModel in ogni overlay.
 * L'evento overlay_view parte una sola volta per apertura, grazie a
 * LaunchedEffect(overlayName), senza rifirare sulle ricomposizioni.
 */
@Composable
fun TrackOverlayView(overlayName: String) {
    val context = LocalContext.current
    LaunchedEffect(overlayName) {
        val telemetry =
            EntryPointAccessors
                .fromApplication(
                    context.applicationContext,
                    OverlayTrackingEntryPoint::class.java,
                ).telemetryRepository()
        telemetry.trackOverlayView(overlayName)
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface OverlayTrackingEntryPoint {
    fun telemetryRepository(): TelemetryRepository
}
