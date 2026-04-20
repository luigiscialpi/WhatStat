package com.whatstat.reborn.ui.screen.chatdetail

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.whatstat.reborn.ui.component.ShimmerBox
import com.whatstat.reborn.ui.component.rememberShimmerBrush

// ── Skeleton: coppia di StatCard ──────────────────────────────────────────────

@Composable
private fun SkeletonStatCardPair(brush: androidx.compose.ui.graphics.Brush) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(2) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ShimmerBox(modifier = Modifier.size(24.dp), brush = brush, shape = RoundedCornerShape(6.dp))
                ShimmerBox(modifier = Modifier.fillMaxWidth(0.65f).height(24.dp), brush = brush)
                ShimmerBox(modifier = Modifier.fillMaxWidth(0.5f).height(11.dp), brush = brush)
            }
        }
    }
}

// ── Skeleton: SectionCard con pie chart placeholder ───────────────────────────

@Composable
private fun SkeletonPieChartCard(brush: androidx.compose.ui.graphics.Brush) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ShimmerBox(modifier = Modifier.fillMaxWidth(0.45f).height(16.dp), brush = brush)
        ShimmerBox(
            modifier = Modifier
                .size(140.dp)
                .align(androidx.compose.ui.Alignment.CenterHorizontally),
            brush = brush,
            shape = RoundedCornerShape(70.dp)
        )
    }
}

// ── Skeleton: ActivityRecapCard (tab + chart) ─────────────────────────────────

@Composable
private fun SkeletonActivityCard(brush: androidx.compose.ui.graphics.Brush) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(bottom = 16.dp)
    ) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
        ) {
            items(5) {
                ShimmerBox(
                    modifier = Modifier.width(72.dp).height(28.dp),
                    brush = brush,
                    shape = RoundedCornerShape(20.dp)
                )
            }
        }
        ShimmerBox(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .padding(horizontal = 16.dp),
            brush = brush,
            shape = RoundedCornerShape(12.dp)
        )
    }
}

// ── Schermata skeleton completa ───────────────────────────────────────────────

@Composable
fun ChatDetailSkeletonContent(modifier: Modifier = Modifier) {
    val brush = rememberShimmerBrush()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Sent / Received
        item { SkeletonStatCardPair(brush) }
        // Chi scrive di più (pie)
        item { SkeletonPieChartCard(brush) }
        // Info cards (date, risposta)
        item { SkeletonStatCardPair(brush) }
        // Info cards (media, lunghezza)
        item { SkeletonStatCardPair(brush) }
        // Chi inizia conversazioni (pie)
        item { SkeletonPieChartCard(brush) }
        // Activity recap
        item { SkeletonActivityCard(brush) }
        // Analisi testi placeholder
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ShimmerBox(modifier = Modifier.fillMaxWidth(0.4f).height(16.dp), brush = brush)
                ShimmerBox(modifier = Modifier.fillMaxWidth().height(100.dp), brush = brush, shape = RoundedCornerShape(12.dp))
            }
        }
        item { Spacer(Modifier.height(32.dp)) }
    }
}
