package com.whatstats.ui.screen.media

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.whatstats.ui.component.ShimmerBox
import com.whatstats.ui.component.ShimmerTextPlaceholder
import com.whatstats.ui.component.rememberShimmerBrush

// ── Skeleton: riga domain chip ────────────────────────────────────────────────

@Composable
@Suppress("FunctionName")
private fun SkeletonDomainChipsRow(brush: Brush) {
    Column(modifier = Modifier.fillMaxWidth()) {
        ShimmerTextPlaceholder(
            brush,
            MaterialTheme.typography.titleMedium,
            widthFraction = 0.55f,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            items(6) {
                ShimmerBox(
                    modifier = Modifier.width(80.dp).height(30.dp),
                    brush = brush,
                    shape = RoundedCornerShape(50.dp),
                )
            }
        }
    }
}

// ── Skeleton: riga link/media per contatto ────────────────────────────────────

@Composable
@Suppress("FunctionName")
private fun SkeletonLinkCountTile(
    brush: Brush,
    iconTint: Color,
) {
    Column(
        modifier =
            Modifier
                .width(64.dp)
                .background(iconTint.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ShimmerBox(modifier = Modifier.size(24.dp), brush = brush, shape = RoundedCornerShape(5.dp))
        ShimmerTextPlaceholder(
            brush,
            MaterialTheme.typography.headlineSmall,
            widthFraction = 0.8f,
        )
        ShimmerTextPlaceholder(
            brush,
            MaterialTheme.typography.bodySmall,
            widthFraction = 0.65f,
        )
    }
}

@Composable
@Suppress("FunctionName")
private fun SkeletonLinkStatRow(brush: Brush) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShimmerBox(modifier = Modifier.size(36.dp), brush = brush, shape = CircleShape)
            Spacer(Modifier.width(10.dp))
            ShimmerTextPlaceholder(
                brush,
                MaterialTheme.typography.titleMedium,
                widthFraction = 0.8f,
            )
        }
        Spacer(Modifier.width(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SkeletonLinkCountTile(brush, MaterialTheme.colorScheme.secondary)
            SkeletonLinkCountTile(brush, MaterialTheme.colorScheme.primary)
        }
    }
}

// ── Schermata skeleton completa ───────────────────────────────────────────────

@Composable
@Suppress("FunctionName")
fun MediaStatsSkeletonContent(modifier: Modifier = Modifier) {
    val brush = rememberShimmerBrush()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { SkeletonDomainChipsRow(brush) }
        item {
            ShimmerTextPlaceholder(
                brush,
                MaterialTheme.typography.titleMedium,
                widthFraction = 0.6f,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
        items(7) { SkeletonLinkStatRow(brush) }
        item { Spacer(Modifier.height(48.dp)) }
    }
}
