package com.whatstat.reborn.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.whatstat.reborn.data.db.model.EmojiStat

@OptIn(ExperimentalLayoutApi::class)
@Composable
@Suppress("FunctionName")
fun InteractiveEmojiCloud(
    emojis: List<EmojiStat>,
    modifier: Modifier = Modifier,
    onEmojiClick: ((String, Int) -> Unit)? = null,
) {
    if (emojis.isEmpty()) return

    val maxFreq = remember(emojis) { emojis.maxOf { it.count }.toFloat().coerceAtLeast(1f) }
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary

    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        emojis.forEachIndexed { index, emoji ->
            val ratio = emoji.count / maxFreq
            val fontSize = (16 + ratio * 16).sp
            val alpha = 0.4f + ratio * 0.6f
            val color = listOf(primary, secondary, tertiary)[index % 3]

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier =
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(color.copy(alpha = alpha * 0.15f))
                            .then(
                                if (onEmojiClick != null) {
                                    Modifier.clickable { onEmojiClick(emoji.emoji, emoji.count) }
                                } else {
                                    Modifier
                                },
                            )
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(
                        emoji.emoji,
                        fontSize = fontSize,
                        fontWeight = if (ratio > 0.5f) FontWeight.SemiBold else FontWeight.Normal,
                        color = color.copy(alpha = alpha),
                    )
                }
                Text(
                    "(%,d)".format(emoji.count),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
