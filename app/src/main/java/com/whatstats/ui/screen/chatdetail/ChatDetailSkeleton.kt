package com.whatstats.ui.screen.chatdetail

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.whatstats.ui.component.ShimmerBox
import com.whatstats.ui.component.ShimmerTextPlaceholder
import com.whatstats.ui.component.StatCardLayout
import com.whatstats.ui.component.rememberShimmerBrush

// ── Skeleton: coppia di StatCard ──────────────────────────────────────────────

@Composable
@Suppress("FunctionName")
private fun SkeletonStatCardPair(
    brush: androidx.compose.ui.graphics.Brush,
    isSmallText: Boolean = false,
) {
    val valueStyle =
        if (isSmallText) {
            MaterialTheme.typography.titleMedium
        } else {
            MaterialTheme.typography.headlineSmall
        }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(2) {
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(StatCardLayout.contentPadding),
            ) {
                ShimmerBox(modifier = Modifier.size(StatCardLayout.iconSize), brush = brush, shape = RoundedCornerShape(6.dp))
                Spacer(Modifier.height(StatCardLayout.iconValueSpacing))
                ShimmerTextPlaceholder(
                    brush,
                    valueStyle,
                    widthFraction = 0.65f,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(StatCardLayout.valueLabelSpacing))
                ShimmerTextPlaceholder(brush, MaterialTheme.typography.bodySmall, widthFraction = 0.5f)
            }
        }
    }
}

// ── Skeleton: SectionCard con pie chart placeholder ───────────────────────────

@Composable
@Suppress("FunctionName")
private fun SkeletonPieChartCard(brush: androidx.compose.ui.graphics.Brush) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ShimmerBox(modifier = Modifier.fillMaxWidth(0.45f).height(16.dp), brush = brush)
        ShimmerBox(
            modifier =
                Modifier
                    .size(140.dp)
                    .align(androidx.compose.ui.Alignment.CenterHorizontally),
            brush = brush,
            shape = RoundedCornerShape(70.dp),
        )
    }
}

// ── Skeleton: ActivityRecapCard (tab + chart) ─────────────────────────────────

@Composable
@Suppress("FunctionName")
private fun SkeletonActivityCard(brush: androidx.compose.ui.graphics.Brush) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(bottom = 16.dp),
    ) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        ) {
            items(5) {
                ShimmerBox(
                    modifier = Modifier.width(72.dp).height(28.dp),
                    brush = brush,
                    shape = RoundedCornerShape(20.dp),
                )
            }
        }
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
            ShimmerBox(modifier = Modifier.fillMaxWidth(0.4f).height(16.dp), brush = brush)
            Spacer(Modifier.height(16.dp))
            ShimmerBox(
                modifier = Modifier.fillMaxWidth().height(68.dp),
                brush = brush,
                shape = RoundedCornerShape(12.dp),
            )
        }
    }
}

// ── Schermata skeleton completa ───────────────────────────────────────────────

@Composable
@Suppress("FunctionName")
fun ChatDetailSkeletonContent(modifier: Modifier = Modifier) {
    val brush = rememberShimmerBrush()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Sent / Received
        item { SkeletonStatCardPair(brush) }
        // Chi scrive di più (pie)
        item { SkeletonPieChartCard(brush) }
        // Info cards (date, risposta)
        item { SkeletonStatCardPair(brush) }
        // Info cards (media, lunghezza)
        item { SkeletonStatCardPair(brush, isSmallText = true) }
        // Chi inizia conversazioni (pie)
        item { SkeletonPieChartCard(brush) }
        // Activity recap
        item { SkeletonActivityCard(brush) }
        // Analisi testi placeholder
        item {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ShimmerBox(modifier = Modifier.fillMaxWidth(0.4f).height(16.dp), brush = brush)
                ShimmerBox(modifier = Modifier.fillMaxWidth().height(100.dp), brush = brush, shape = RoundedCornerShape(12.dp))
            }
        }
        item { Spacer(Modifier.height(32.dp)) }
    }
}
