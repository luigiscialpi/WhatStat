package com.whatstat.reborn.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.whatstat.reborn.R
import com.whatstat.reborn.data.db.model.SenderStat
import com.whatstat.reborn.ui.theme.getSenderColor
import com.whatstat.reborn.ui.theme.senderColorOverrideKey

@Suppress("FunctionName")
@Composable
fun ActivityDetailDialog(
    title: String,
    totalMessages: Int,
    breakdown: List<SenderStat>,
    referenceSenders: List<SenderStat> = emptyList(),
    scopeChatId: Long = 0L,
    senderColorArgbOverrides: Map<String, Int> = emptyMap(),
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth(0.9f)
                    .wrapContentHeight()
                    .padding(vertical = 24.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 12.dp,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
            ) {
                // Header
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Text(
                    text = stringResource(R.string.activity_breakdown_subtitle, totalMessages),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(20.dp))

                // List
                Box(modifier = Modifier.weight(1f, fill = false)) {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(breakdown) { sender ->
                            SenderBreakdownItem(
                                sender = sender,
                                totalMessages = totalMessages,
                                referenceSenders = referenceSenders,
                                scopeChatId = scopeChatId,
                                senderColorArgbOverrides = senderColorArgbOverrides,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Actions
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(stringResource(R.string.close))
                }
            }
        }
    }
}

@Suppress("FunctionName")
@Composable
private fun SenderBreakdownItem(
    sender: SenderStat,
    totalMessages: Int,
    referenceSenders: List<SenderStat>,
    scopeChatId: Long,
    senderColorArgbOverrides: Map<String, Int>,
) {
    val percentage = (sender.count.toFloat() / totalMessages.coerceAtLeast(1))
    val color =
        getSenderColor(
            sender.jid,
            referenceSenders,
            senderColorArgbOverrides[senderColorOverrideKey(scopeChatId, sender.jid)],
        )

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // Nome mittente
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(color),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = sender.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            // Conteggio e percentuale
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${sender.count}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "%.1f%%".format(percentage * 100),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Progress bar semplificata
        LinearProgressIndicator(
            progress = { percentage },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
            color = color,
            trackColor = color.copy(alpha = 0.1f),
        )
    }
}
