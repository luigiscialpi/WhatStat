package com.whatstats.ui.screen.chatdetail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
    brush: Brush,
    isSmallText: Boolean = false,
    hasSubtitle: Boolean = false,
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
                if (hasSubtitle) {
                    Spacer(Modifier.height(4.dp))
                    ShimmerTextPlaceholder(
                        brush,
                        MaterialTheme.typography.labelSmall,
                        widthFraction = 0.8f,
                    )
                }
            }
        }
    }
}

// ── Skeleton: SectionCard con pie chart placeholder ───────────────────────────

@Composable
@Suppress("FunctionName")
private fun SkeletonPieChartCard(
    brush: Brush,
    legendItemCount: Int,
    showExpand: Boolean,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShimmerTextPlaceholder(
                brush,
                MaterialTheme.typography.titleMedium,
                widthFraction = 0.45f,
                modifier = Modifier.weight(1f),
            )
            ShimmerBox(modifier = Modifier.size(48.dp), brush = brush, shape = RoundedCornerShape(50.dp))
        }
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShimmerBox(
                modifier = Modifier.size(140.dp),
                brush = brush,
                shape = RoundedCornerShape(70.dp),
            )
            Spacer(Modifier.width(16.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                repeat(legendItemCount) {
                    SkeletonPieLegendRow(brush)
                }
            }
        }
        if (showExpand) {
            ShimmerBox(
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp).width(104.dp).height(16.dp),
                brush = brush,
            )
        }
    }
}

@Composable
@Suppress("FunctionName")
private fun SkeletonPieLegendRow(brush: Brush) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShimmerBox(modifier = Modifier.size(16.dp), brush = brush, shape = RoundedCornerShape(4.dp))
        Spacer(Modifier.width(6.dp))
        ShimmerTextPlaceholder(
            brush,
            MaterialTheme.typography.bodySmall,
            widthFraction = 0.8f,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(6.dp))
        Column(
            modifier = Modifier.width(40.dp),
            horizontalAlignment = Alignment.End,
        ) {
            ShimmerTextPlaceholder(brush, MaterialTheme.typography.bodySmall, widthFraction = 0.8f)
            ShimmerTextPlaceholder(brush, MaterialTheme.typography.labelSmall, widthFraction = 0.55f)
        }
    }
}

@Composable
@Suppress("FunctionName")
private fun SkeletonDateAndResponsePair(brush: Brush) {
    Row(
        modifier = Modifier.height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(16.dp),
        ) {
            ShimmerBox(modifier = Modifier.size(24.dp), brush = brush, shape = RoundedCornerShape(6.dp))
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ShimmerTextPlaceholder(
                    brush,
                    MaterialTheme.typography.titleMedium,
                    widthFraction = 0.8f,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                ShimmerBox(modifier = Modifier.size(24.dp), brush = brush, shape = RoundedCornerShape(4.dp))
                ShimmerTextPlaceholder(
                    brush,
                    MaterialTheme.typography.titleMedium,
                    widthFraction = 0.8f,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(StatCardLayout.valueLabelSpacing))
            ShimmerTextPlaceholder(brush, MaterialTheme.typography.bodySmall, widthFraction = 0.7f)
        }
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
                MaterialTheme.typography.titleMedium,
                widthFraction = 0.65f,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(StatCardLayout.valueLabelSpacing))
            ShimmerTextPlaceholder(brush, MaterialTheme.typography.bodySmall, widthFraction = 0.5f)
        }
    }
}

@Composable
@Suppress("FunctionName")
private fun SkeletonMilestoneCard(brush: Brush) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                .padding(20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                ShimmerTextPlaceholder(
                    brush,
                    MaterialTheme.typography.titleMedium,
                    widthFraction = 0.75f,
                    fontWeight = FontWeight.Bold,
                )
                ShimmerTextPlaceholder(brush, MaterialTheme.typography.bodySmall, widthFraction = 0.5f)
            }
            ShimmerBox(modifier = Modifier.size(48.dp), brush = brush, shape = RoundedCornerShape(50.dp))
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(3) {
                ShimmerBox(
                    modifier = Modifier.width(72.dp).height(48.dp),
                    brush = brush,
                    shape = RoundedCornerShape(50.dp),
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            ShimmerTextPlaceholder(
                brush,
                MaterialTheme.typography.bodyMedium,
                widthFraction = 0.7f,
                modifier = Modifier.weight(1f),
            )
            ShimmerTextPlaceholder(
                brush,
                MaterialTheme.typography.bodySmall,
                widthFraction = 0.7f,
                modifier = Modifier.width(48.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        ShimmerBox(
            modifier = Modifier.fillMaxWidth().height(8.dp),
            brush = brush,
            shape = RoundedCornerShape(4.dp),
        )
        Spacer(Modifier.height(20.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                ShimmerTextPlaceholder(brush, MaterialTheme.typography.labelSmall, widthFraction = 0.45f)
                ShimmerTextPlaceholder(brush, MaterialTheme.typography.titleMedium, widthFraction = 0.55f)
            }
            ShimmerBox(modifier = Modifier.width(72.dp).height(30.dp), brush = brush, shape = RoundedCornerShape(12.dp))
        }
        Spacer(Modifier.height(16.dp))
        ShimmerTextPlaceholder(brush, MaterialTheme.typography.labelSmall, widthFraction = 0.65f)
    }
}

@Composable
@Suppress("FunctionName")
private fun SkeletonTextAnalysisCard(brush: Brush) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShimmerTextPlaceholder(
                brush,
                MaterialTheme.typography.titleMedium,
                widthFraction = 0.65f,
                modifier = Modifier.weight(1f),
            )
            ShimmerBox(modifier = Modifier.width(116.dp).height(20.dp), brush = brush, shape = RoundedCornerShape(6.dp))
        }
        Spacer(Modifier.height(16.dp))
        ShimmerBox(
            modifier = Modifier.fillMaxWidth().height(48.dp),
            brush = brush,
            shape = RoundedCornerShape(8.dp),
        )
        Spacer(Modifier.height(12.dp))
        ShimmerBox(
            modifier = Modifier.fillMaxWidth().height(40.dp),
            brush = brush,
            shape = RoundedCornerShape(8.dp),
        )
        Spacer(Modifier.height(12.dp))
        repeat(5) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ShimmerBox(modifier = Modifier.size(32.dp), brush = brush, shape = RoundedCornerShape(8.dp))
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    ShimmerTextPlaceholder(brush, MaterialTheme.typography.bodyMedium, widthFraction = 0.75f)
                    ShimmerTextPlaceholder(brush, MaterialTheme.typography.bodySmall, widthFraction = 0.5f)
                }
                ShimmerTextPlaceholder(
                    brush,
                    MaterialTheme.typography.bodyMedium,
                    widthFraction = 0.7f,
                    modifier = Modifier.width(64.dp),
                )
            }
        }
    }
}

// ── Skeleton: ActivityRecapCard (tab + chart) ─────────────────────────────────

@Composable
@Suppress("FunctionName")
private fun SkeletonActivityCard(brush: Brush) {
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

@Composable
@Suppress("FunctionName")
private fun SkeletonTimeRangeFilterRow(brush: Brush) {
    val chipWidths = listOf(72.dp, 96.dp, 84.dp, 72.dp, 52.dp)
    Column(modifier = Modifier.fillMaxWidth()) {
        ShimmerTextPlaceholder(
            brush,
            MaterialTheme.typography.labelSmall,
            widthFraction = 0.45f,
            modifier = Modifier.padding(start = 16.dp, top = 4.dp),
        )
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(chipWidths.size) { index ->
                Box(
                    modifier = Modifier.width(chipWidths[index]).height(48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    ShimmerBox(
                        modifier = Modifier.fillMaxWidth().height(32.dp),
                        brush = brush,
                        shape = RoundedCornerShape(50.dp),
                    )
                }
            }
        }
    }
}

// ── Schermata skeleton completa ───────────────────────────────────────────────

@Composable
@Suppress("FunctionName")
fun ChatDetailSkeletonContent(modifier: Modifier = Modifier) {
    val brush = rememberShimmerBrush()

    Column(modifier = modifier.fillMaxSize()) {
        SkeletonTimeRangeFilterRow(brush)
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Sent / Received
            item { SkeletonStatCardPair(brush) }
            // Densita: parole ed emoji per messaggio
            item { SkeletonStatCardPair(brush, hasSubtitle = true) }
            // Distribuzione dei messaggi
            item { SkeletonPieChartCard(brush, legendItemCount = 7, showExpand = true) }
            // Distribuzione attivita
            item { SkeletonActivityCard(brush) }
            // Periodo e tempo di risposta
            item { SkeletonDateAndResponsePair(brush) }
            // Media e lunghezza messaggi
            item { SkeletonStatCardPair(brush, isSmallText = true) }
            // Inizio conversazioni
            item { SkeletonPieChartCard(brush, legendItemCount = 2, showExpand = false) }
            // Previsione traguardo
            item { SkeletonMilestoneCard(brush) }
            // Analisi testi
            item { SkeletonTextAnalysisCard(brush) }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}
