package com.whatstat.reborn.ui.screen.media

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
import androidx.compose.foundation.lazy.LazyRow
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

// ── Skeleton: riga domain chip ────────────────────────────────────────────────

@Composable
private fun SkeletonDomainChipsRow(brush: androidx.compose.ui.graphics.Brush) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ShimmerBox(modifier = Modifier.fillMaxWidth(0.4f).height(16.dp), brush = brush)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 8.dp)
        ) {
            items(6) {
                ShimmerBox(
                    modifier = Modifier.width(80.dp).height(32.dp),
                    brush = brush,
                    shape = RoundedCornerShape(50.dp)
                )
            }
        }
    }
}

// ── Skeleton: riga link/media per contatto ────────────────────────────────────

@Composable
private fun SkeletonLinkStatRow(brush: androidx.compose.ui.graphics.Brush) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ShimmerBox(modifier = Modifier.size(44.dp), brush = brush, shape = CircleShape)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ShimmerBox(modifier = Modifier.fillMaxWidth(0.5f).height(14.dp), brush = brush)
            ShimmerBox(modifier = Modifier.fillMaxWidth(0.35f).height(11.dp), brush = brush)
        }
        ShimmerBox(modifier = Modifier.width(36.dp).height(14.dp), brush = brush)
    }
}

// ── Schermata skeleton completa ───────────────────────────────────────────────

@Composable
fun MediaStatsSkeletonContent(modifier: Modifier = Modifier) {
    val brush = rememberShimmerBrush()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item { SkeletonDomainChipsRow(brush) }
        item {
            ShimmerBox(
                modifier = Modifier.fillMaxWidth(0.35f).height(16.dp).padding(vertical = 4.dp),
                brush = brush
            )
        }
        items(7) { SkeletonLinkStatRow(brush) }
        item { Spacer(Modifier.height(48.dp)) }
    }
}
