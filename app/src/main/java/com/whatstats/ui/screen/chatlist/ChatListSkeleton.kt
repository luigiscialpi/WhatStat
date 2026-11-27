package com.whatstats.ui.screen.chatlist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.whatstats.ui.component.ShimmerBox
import com.whatstats.ui.component.ShimmerTextPlaceholder
import com.whatstats.ui.component.rememberShimmerBrush

// ── Skeleton: singola riga chat ───────────────────────────────────────────────

@Composable
@Suppress("FunctionName")
private fun SkeletonChatRow(
    brush: Brush,
    isLiteMode: Boolean,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                .padding(start = 16.dp, top = 16.dp, bottom = 16.dp, end = if (isLiteMode) 4.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!isLiteMode) {
            // Badge rank
            ShimmerBox(
                modifier = Modifier.size(36.dp),
                brush = brush,
                shape = CircleShape,
            )
            Spacer(Modifier.width(14.dp))
        }
        // Avatar
        ShimmerBox(
            modifier = Modifier.size(40.dp),
            brush = brush,
            shape = CircleShape,
        )
        Spacer(Modifier.width(10.dp))
        // Testo
        Column(modifier = Modifier.weight(1f)) {
            ShimmerTextPlaceholder(
                brush,
                MaterialTheme.typography.bodyMedium,
                widthFraction = 0.75f,
                fontWeight = FontWeight.Medium,
            )
            if (isLiteMode) {
                ShimmerTextPlaceholder(
                    brush,
                    MaterialTheme.typography.labelSmall,
                    widthFraction = 0.7f,
                )
            }
            ShimmerTextPlaceholder(
                brush,
                MaterialTheme.typography.bodySmall,
                widthFraction = 0.6f,
            )
        }
        if (isLiteMode) {
            repeat(2) {
                Box(
                    modifier = Modifier.size(48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    ShimmerBox(modifier = Modifier.size(24.dp), brush = brush)
                }
            }
        } else {
            Spacer(Modifier.width(8.dp))
            ShimmerBox(
                modifier = Modifier.width(72.dp).height(24.dp),
                brush = brush,
                shape = RoundedCornerShape(8.dp),
            )
        }
    }
}

// ── Schermata skeleton completa ───────────────────────────────────────────────

@Composable
@Suppress("FunctionName")
fun ChatListSkeletonContent(
    modifier: Modifier = Modifier,
    isLiteMode: Boolean = false,
) {
    val brush = rememberShimmerBrush()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(10) {
            SkeletonChatRow(brush, isLiteMode)
        }
        item { Spacer(Modifier.height(32.dp)) }
    }
}
