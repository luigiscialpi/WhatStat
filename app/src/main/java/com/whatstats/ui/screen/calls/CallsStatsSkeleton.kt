package com.whatstats.ui.screen.calls

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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

// ── Skeleton: summary card ────────────────────────────────────────────────────

@Composable
@Suppress("FunctionName")
private fun SkeletonCallSummaryCard(brush: Brush) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                .padding(16.dp),
    ) {
        // Titolo
        ShimmerTextPlaceholder(
            brush,
            MaterialTheme.typography.titleMedium,
            widthFraction = 0.45f,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(12.dp))
        // Riga quattro statistiche
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ShimmerBox(modifier = Modifier.size(24.dp), brush = brush, shape = RoundedCornerShape(6.dp))
                ShimmerTextPlaceholder(
                    brush,
                    MaterialTheme.typography.titleLarge,
                    widthFraction = 0.75f,
                    fontWeight = FontWeight.Bold,
                )
                ShimmerTextPlaceholder(
                    brush,
                    MaterialTheme.typography.bodySmall,
                    widthFraction = 0.8f,
                )
            }
            ShimmerBox(
                modifier = Modifier.width(2.dp).fillMaxHeight().padding(vertical = 4.dp),
                brush = brush,
            )
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                repeat(3) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        ShimmerBox(modifier = Modifier.size(24.dp), brush = brush, shape = RoundedCornerShape(6.dp))
                        ShimmerTextPlaceholder(
                            brush,
                            MaterialTheme.typography.titleLarge,
                            widthFraction = 0.75f,
                            fontWeight = FontWeight.Bold,
                        )
                        ShimmerTextPlaceholder(
                            brush,
                            MaterialTheme.typography.bodySmall,
                            widthFraction = 0.8f,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        ShimmerBox(modifier = Modifier.fillMaxWidth().height(1.dp), brush = brush)
        Spacer(Modifier.height(12.dp))
        ShimmerTextPlaceholder(
            brush,
            MaterialTheme.typography.titleSmall,
            widthFraction = 0.6f,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        SkeletonCallDurationRow(brush)
        Spacer(Modifier.height(4.dp))
        SkeletonCallDurationRow(brush)
    }
}

@Composable
@Suppress("FunctionName")
private fun SkeletonCallDurationRow(brush: Brush) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShimmerBox(modifier = Modifier.size(24.dp), brush = brush, shape = RoundedCornerShape(6.dp))
        Spacer(Modifier.width(4.dp))
        ShimmerBox(modifier = Modifier.width(44.dp).height(16.dp), brush = brush)
        ShimmerBox(
            modifier = Modifier.weight(1f).height(8.dp).padding(horizontal = 4.dp),
            brush = brush,
            shape = RoundedCornerShape(4.dp),
        )
        ShimmerBox(modifier = Modifier.width(52.dp).height(16.dp), brush = brush)
    }
}

// ── Skeleton: riga dettaglio chiamata ─────────────────────────────────────────

@Composable
@Suppress("FunctionName")
private fun SkeletonCallDetailRow(brush: Brush) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ShimmerBox(modifier = Modifier.size(36.dp), brush = brush, shape = CircleShape)
            Spacer(Modifier.width(10.dp))
            ShimmerTextPlaceholder(
                brush,
                MaterialTheme.typography.titleMedium,
                widthFraction = 0.65f,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ShimmerBox(modifier = Modifier.size(24.dp), brush = brush, shape = RoundedCornerShape(6.dp))
                ShimmerTextPlaceholder(
                    brush,
                    MaterialTheme.typography.titleMedium,
                    widthFraction = 0.7f,
                    fontWeight = FontWeight.Bold,
                )
                ShimmerTextPlaceholder(
                    brush,
                    MaterialTheme.typography.bodySmall,
                    widthFraction = 0.8f,
                )
            }
            ShimmerBox(
                modifier = Modifier.width(2.dp).fillMaxHeight().padding(vertical = 4.dp),
                brush = brush,
            )
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                repeat(3) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        ShimmerBox(modifier = Modifier.size(24.dp), brush = brush, shape = RoundedCornerShape(6.dp))
                        ShimmerTextPlaceholder(
                            brush,
                            MaterialTheme.typography.titleMedium,
                            widthFraction = 0.7f,
                            fontWeight = FontWeight.Bold,
                        )
                        ShimmerTextPlaceholder(
                            brush,
                            MaterialTheme.typography.bodySmall,
                            widthFraction = 0.8f,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        ShimmerBox(modifier = Modifier.fillMaxWidth().height(1.dp), brush = brush)
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(3) { index ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ShimmerBox(modifier = Modifier.size(24.dp), brush = brush, shape = RoundedCornerShape(6.dp))
                    Spacer(Modifier.width(4.dp))
                    ShimmerTextPlaceholder(
                        brush,
                        MaterialTheme.typography.bodySmall,
                        widthFraction = 0.65f,
                    )
                }
                if (index < 2) {
                    ShimmerBox(modifier = Modifier.width(1.dp).height(24.dp), brush = brush)
                }
            }
        }
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
            Column {
                Spacer(Modifier.height(4.dp))
                ShimmerTextPlaceholder(
                    brush,
                    MaterialTheme.typography.titleMedium,
                    widthFraction = 0.5f,
                )
            }
        }
        items(6) { SkeletonCallDetailRow(brush) }
        item { Spacer(Modifier.height(48.dp)) }
    }
}
