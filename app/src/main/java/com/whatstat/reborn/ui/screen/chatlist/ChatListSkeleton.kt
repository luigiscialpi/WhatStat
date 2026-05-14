package com.whatstat.reborn.ui.screen.chatlist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.unit.dp
import com.whatstat.reborn.ui.component.ShimmerBox
import com.whatstat.reborn.ui.component.rememberShimmerBrush

// ── Skeleton: singola riga chat ───────────────────────────────────────────────

@Composable
@Suppress("FunctionName")
private fun SkeletonChatRow(brush: androidx.compose.ui.graphics.Brush) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Badge rank
        ShimmerBox(
            modifier = Modifier.size(36.dp),
            brush = brush,
            shape = CircleShape,
        )
        Spacer(Modifier.width(12.dp))
        // Avatar
        ShimmerBox(
            modifier = Modifier.size(44.dp),
            brush = brush,
            shape = CircleShape,
        )
        Spacer(Modifier.width(12.dp))
        // Testo
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ShimmerBox(modifier = Modifier.fillMaxWidth(0.55f).height(15.dp), brush = brush)
            ShimmerBox(modifier = Modifier.fillMaxWidth(0.35f).height(12.dp), brush = brush)
        }
    }
}

// ── Schermata skeleton completa ───────────────────────────────────────────────

@Composable
@Suppress("FunctionName")
fun ChatListSkeletonContent(modifier: Modifier = Modifier) {
    val brush = rememberShimmerBrush()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(10) {
            SkeletonChatRow(brush)
        }
        item { Spacer(Modifier.height(32.dp)) }
    }
}
