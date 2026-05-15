package com.whatstat.ui.screen.calls

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
import com.whatstat.ui.component.ShimmerBox
import com.whatstat.ui.component.rememberShimmerBrush

// ── Skeleton: summary card ────────────────────────────────────────────────────

@Composable
@Suppress("FunctionName")
private fun SkeletonCallSummaryCard(brush: androidx.compose.ui.graphics.Brush) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Titolo
        ShimmerBox(modifier = Modifier.fillMaxWidth(0.45f).height(18.dp), brush = brush)
        // Riga 3 stat
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(3) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ShimmerBox(modifier = Modifier.fillMaxWidth().height(22.dp), brush = brush)
                    ShimmerBox(modifier = Modifier.fillMaxWidth(0.7f).height(11.dp), brush = brush)
                }
            }
        }
        // Progress bar
        ShimmerBox(
            modifier = Modifier.fillMaxWidth().height(8.dp),
            brush = brush,
            shape = RoundedCornerShape(4.dp),
        )
    }
}

// ── Skeleton: riga dettaglio chiamata ─────────────────────────────────────────

@Composable
@Suppress("FunctionName")
private fun SkeletonCallDetailRow(brush: androidx.compose.ui.graphics.Brush) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShimmerBox(modifier = Modifier.size(40.dp), brush = brush, shape = CircleShape)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ShimmerBox(modifier = Modifier.fillMaxWidth(0.5f).height(14.dp), brush = brush)
            ShimmerBox(modifier = Modifier.fillMaxWidth(0.35f).height(11.dp), brush = brush)
        }
        // Durata
        ShimmerBox(modifier = Modifier.width(48.dp).height(14.dp), brush = brush)
    }
}

// ── Schermata skeleton completa ───────────────────────────────────────────────

@Composable
@Suppress("FunctionName")
fun CallsStatsSkeletonContent(modifier: Modifier = Modifier) {
    val brush = rememberShimmerBrush()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { SkeletonCallSummaryCard(brush) }
        item {
            ShimmerBox(
                modifier = Modifier.fillMaxWidth(0.3f).height(16.dp).padding(vertical = 4.dp),
                brush = brush,
            )
        }
        items(6) { SkeletonCallDetailRow(brush) }
        item { Spacer(Modifier.height(48.dp)) }
    }
}
