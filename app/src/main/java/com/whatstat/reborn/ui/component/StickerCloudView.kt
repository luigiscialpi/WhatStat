package com.whatstat.reborn.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.whatstat.reborn.data.db.model.StickerStat
import java.io.File

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InteractiveStickerCloud(
    stickers: List<StickerStat>,
    modifier: Modifier = Modifier,
    onStickerClick: ((String, Int) -> Unit)? = null
) {
    if (stickers.isEmpty()) return

    val maxFreq = remember(stickers) { stickers.maxOf { it.count }.toFloat().coerceAtLeast(1f) }

    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        stickers.forEach { sticker ->
            val ratio = sticker.count / maxFreq
            // Size from 40dp (least used) to 80dp (most used)
            val size = (40 + ratio * 40).dp

            StickerCloudItem(
                fileName = sticker.fileName,
                count = sticker.count,
                size = size,
                onClick = if (onStickerClick != null) {
                    { onStickerClick(sticker.fileName, sticker.count) }
                } else null
            )
        }
    }
}

@Composable
private fun StickerCloudItem(
    fileName: String,
    count: Int,
    size: androidx.compose.ui.unit.Dp,
    onClick: (() -> Unit)?
) {
    val context = LocalContext.current
    val stickerPaths = listOf(
        "/sdcard/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Stickers",
        "/sdcard/WhatsApp/Media/WhatsApp Stickers"
    )
    val stickerFile = remember(fileName) {
        stickerPaths.map { File(it, fileName) }.firstOrNull { it.exists() }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
            contentAlignment = Alignment.Center
        ) {
            if (stickerFile != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(stickerFile)
                        .crossfade(true).build(),
                    contentDescription = fileName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            } else {
                Icon(
                    Icons.Outlined.PhotoLibrary,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(size * 0.5f)
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            "(%d)".format(count),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
