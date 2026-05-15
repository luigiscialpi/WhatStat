package com.whatstat.ui.screen.dashboard

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.whatstat.ui.component.ShimmerBox
import com.whatstat.ui.component.rememberShimmerBrush

// ── Skeleton: singola StatCard ────────────────────────────────────────────────

@Composable
@Suppress("FunctionName")
private fun SkeletonStatCard(
    modifier: Modifier = Modifier,
    brush: Brush,
) {
    Column(
        modifier =
            modifier
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(16.dp),
    ) {
        // Icona placeholder
        ShimmerBox(modifier = Modifier.size(24.dp), brush = brush, shape = RoundedCornerShape(6.dp))
        Spacer(Modifier.height(10.dp))
        // Valore placeholder
        ShimmerBox(modifier = Modifier.fillMaxWidth(0.65f).height(26.dp), brush = brush)
        Spacer(Modifier.height(6.dp))
        // Label placeholder
        ShimmerBox(modifier = Modifier.fillMaxWidth(0.5f).height(12.dp), brush = brush)
    }
}

// ── Skeleton: griglia 2x2 StatCard ───────────────────────────────────────────

@Composable
@Suppress("FunctionName")
fun SkeletonStatCardsGrid(brush: Brush) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SkeletonStatCard(modifier = Modifier.weight(1f), brush = brush)
            SkeletonStatCard(modifier = Modifier.weight(1f), brush = brush)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SkeletonStatCard(modifier = Modifier.weight(1f), brush = brush)
            SkeletonStatCard(modifier = Modifier.weight(1f), brush = brush)
        }
    }
}

// ── Skeleton: ActivityRecapCard ───────────────────────────────────────────────

@Composable
@Suppress("FunctionName")
fun SkeletonActivityCard(brush: Brush) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(bottom = 16.dp),
    ) {
        // Tab row placeholder
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        ) {
            items(5) {
                ShimmerBox(
                    modifier =
                        Modifier
                            .width(72.dp)
                            .height(28.dp),
                    brush = brush,
                    shape = RoundedCornerShape(20.dp),
                )
            }
        }
        // Chart area placeholder
        ShimmerBox(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .padding(horizontal = 16.dp),
            brush = brush,
            shape = RoundedCornerShape(12.dp),
        )
    }
}

// ── Skeleton: SectionCard generica ───────────────────────────────────────────

@Composable
@Suppress("FunctionName")
fun SkeletonSectionCard(brush: Brush) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(20.dp),
    ) {
        // Titolo
        ShimmerBox(modifier = Modifier.fillMaxWidth(0.4f).height(18.dp), brush = brush)
        Spacer(Modifier.height(6.dp))
        // Sottotitolo
        ShimmerBox(modifier = Modifier.fillMaxWidth(0.65f).height(12.dp), brush = brush)
        Spacer(Modifier.height(16.dp))
        // Pulsante placeholder
        ShimmerBox(
            modifier = Modifier.fillMaxWidth().height(40.dp),
            brush = brush,
            shape = RoundedCornerShape(50.dp),
        )
    }
}

// ── Skeleton: singola ActionCard ─────────────────────────────────────────────

@Composable
@Suppress("FunctionName")
private fun SkeletonActionCard(
    modifier: Modifier = Modifier,
    brush: Brush,
) {
    Column(
        modifier =
            modifier
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
    ) {
        ShimmerBox(modifier = Modifier.size(28.dp), brush = brush, shape = RoundedCornerShape(8.dp))
        Spacer(Modifier.height(8.dp))
        ShimmerBox(modifier = Modifier.fillMaxWidth(0.7f).height(14.dp), brush = brush)
        Spacer(Modifier.height(4.dp))
        ShimmerBox(modifier = Modifier.fillMaxWidth(0.55f).height(11.dp), brush = brush)
    }
}

// ── Skeleton: griglia 2x2 ActionCard ─────────────────────────────────────────

@Composable
@Suppress("FunctionName")
fun SkeletonActionGrid(brush: Brush) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SkeletonActionCard(modifier = Modifier.weight(1f), brush = brush)
            SkeletonActionCard(modifier = Modifier.weight(1f), brush = brush)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SkeletonActionCard(modifier = Modifier.weight(1f), brush = brush)
            SkeletonActionCard(modifier = Modifier.weight(1f), brush = brush)
        }
    }
}

// ── Schermata skeleton completa ───────────────────────────────────────────────

@Composable
@Suppress("FunctionName")
fun DashboardSkeletonContent(modifier: Modifier = Modifier) {
    val brush = rememberShimmerBrush()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { SkeletonStatCardsGrid(brush) }
        item { SkeletonActivityCard(brush) }
        item { SkeletonSectionCard(brush) }
        item { SkeletonActionGrid(brush) }
        item { Spacer(Modifier.height(32.dp)) }
    }
}
