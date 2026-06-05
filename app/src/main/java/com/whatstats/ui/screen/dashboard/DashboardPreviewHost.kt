package com.whatstats.ui.screen.dashboard

import androidx.compose.runtime.Composable
import com.whatstats.ui.component.EmojiPreviewModal
import com.whatstats.ui.component.StickerPreviewModal

@Composable
@Suppress("FunctionName")
fun DashboardPreviewHost(
    previewEmoji: String?,
    previewEmojiCount: Int,
    onDismissEmoji: () -> Unit,
    previewSticker: String?,
    previewStickerCount: Int,
    onDismissSticker: () -> Unit,
    isLiteMode: Boolean,
    previewStickerLitePath: String?,
) {
    if (previewEmoji != null) {
        EmojiPreviewModal(
            emoji = previewEmoji,
            count = previewEmojiCount,
            onDismiss = onDismissEmoji,
        )
    }

    if (previewSticker != null) {
        StickerPreviewModal(
            fileName = previewSticker,
            count = previewStickerCount,
            onDismiss = onDismissSticker,
            isLiteMode = isLiteMode,
            litePath = previewStickerLitePath,
        )
    }
}
