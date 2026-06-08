package com.whatstats.ui.screen.media

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.whatstats.R

internal data class LinkPreviewCacheEntry(
    val expiresAtMillis: Long,
    val metadata: LinkPreviewMetadata?,
)

internal data class LinkPreviewMetadata(
    val title: String,
    val imageUrl: String?,
    val siteName: String?,
)

private sealed interface LinkPreviewState {
    data object Loading : LinkPreviewState

    data class Loaded(
        val metadata: LinkPreviewMetadata,
    ) : LinkPreviewState

    data object Unavailable : LinkPreviewState
}

@Composable
internal fun LinkPreviewSection(
    url: String,
    cache: MutableMap<String, LinkPreviewCacheEntry>,
) {
    val previewState by produceState<LinkPreviewState>(
        initialValue = LinkPreviewState.Loading,
        key1 = url,
    ) {
        val metadata = loadLinkPreviewMetadata(url, cache)
        value =
            if (metadata != null) {
                LinkPreviewState.Loaded(metadata)
            } else {
                LinkPreviewState.Unavailable
            }
    }

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.65f))
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(10.dp),
                )
                .padding(8.dp),
    ) {
        when (val state = previewState) {
            LinkPreviewState.Loading -> {
                Text(
                    text = stringResource(R.string.link_preview_loading),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            is LinkPreviewState.Loaded -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val imageUrl = state.metadata.imageUrl
                    if (!imageUrl.isNullOrBlank()) {
                        AsyncImage(
                            model =
                                ImageRequest.Builder(LocalContext.current)
                                    .data(imageUrl)
                                    .crossfade(true)
                                    .build(),
                            contentDescription = null,
                            modifier =
                                Modifier
                                    .size(84.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop,
                            error = painterResource(id = android.R.drawable.ic_menu_report_image),
                        )
                        Spacer(Modifier.width(10.dp))
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = state.metadata.title,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val host = state.metadata.siteName ?: extractHost(url)
                        if (!host.isNullOrBlank()) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = host,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            LinkPreviewState.Unavailable -> {
                LinkPreviewFallback(url = url)
            }
        }
    }
}

@Composable
private fun LinkPreviewFallback(url: String) {
    val host = extractHost(url).orEmpty()

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (host.isNotBlank()) {
            AsyncImage(
                model =
                    ImageRequest.Builder(LocalContext.current)
                        .data("https://www.google.com/s2/favicons?sz=64&domain=$host")
                        .crossfade(true)
                        .build(),
                contentDescription = null,
                modifier =
                    Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(6.dp)),
                contentScale = ContentScale.Fit,
                error = painterResource(id = android.R.drawable.ic_menu_compass),
            )
            Spacer(Modifier.width(8.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (host.isNotBlank()) host else url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.link_preview_unavailable),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
