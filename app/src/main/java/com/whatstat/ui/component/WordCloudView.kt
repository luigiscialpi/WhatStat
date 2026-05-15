package com.whatstat.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whatstat.data.db.model.WordFrequency

/**
 * Visualizzazione "tag cloud" delle parole più usate.
 * Le parole più frequenti hanno un font più grande e un colore più vivace.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
@Suppress("FunctionName")
fun WordCloudView(
    words: List<WordFrequency>,
    modifier: Modifier = Modifier,
) {
    if (words.isEmpty()) return

    val maxFreq =
        remember(words) {
            words.maxOf { it.frequency }.toFloat().coerceAtLeast(1f)
        }

    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary

    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        words.forEachIndexed { index, word ->
            val ratio = word.frequency / maxFreq
            val fontSize = (12 + ratio * 14).sp
            val alpha = 0.4f + ratio * 0.6f

            val color =
                when (index % 3) {
                    0 -> primary
                    1 -> secondary
                    else -> tertiary
                }

            Box(
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(color.copy(alpha = alpha * 0.15f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    word.word,
                    fontSize = fontSize,
                    fontWeight = if (ratio > 0.5f) FontWeight.SemiBold else FontWeight.Normal,
                    color = color.copy(alpha = alpha),
                )
            }
        }
    }
}

/**
 * Versione interattiva della word cloud che permette di toccare le parole per vedere il conteggio.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
@Suppress("FunctionName")
fun InteractiveWordCloud(
    words: List<WordFrequency>,
    modifier: Modifier = Modifier,
    onWordClick: ((String, Int) -> Unit)? = null,
) {
    if (words.isEmpty()) return

    val maxFreq =
        remember(words) {
            words.maxOf { it.frequency }.toFloat().coerceAtLeast(1f)
        }

    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary

    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        words.forEachIndexed { index, word ->
            val ratio = word.frequency / maxFreq
            val fontSize = (12 + ratio * 14).sp
            val alpha = 0.4f + ratio * 0.6f

            val color =
                when (index % 3) {
                    0 -> primary
                    1 -> secondary
                    else -> tertiary
                }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier =
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(color.copy(alpha = alpha * 0.15f))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        word.word,
                        fontSize = fontSize,
                        fontWeight = if (ratio > 0.5f) FontWeight.SemiBold else FontWeight.Normal,
                        color = color.copy(alpha = alpha),
                    )
                }
                Text(
                    "(%,d)".format(word.frequency),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
