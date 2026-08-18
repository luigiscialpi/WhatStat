package com.whatstats.ui.component

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.whatstats.BuildConfig
import com.whatstats.R
import com.whatstats.data.feedback.FeedbackItem
import com.whatstats.data.feedback.FeedbackStatus
import com.whatstats.data.feedback.FeedbackType
import com.whatstats.telemetry.TrackOverlayView
import com.whatstats.ui.feedback.FeedbackSendState
import com.whatstats.ui.feedback.FeedbackViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
@Suppress("FunctionName")
fun FeedbackDialog(
    initialTab: Int = 0,
    isLiteMode: Boolean = false,
    onDismiss: () -> Unit,
    feedbackViewModel: FeedbackViewModel = hiltViewModel(),
) {
    TrackOverlayView("feedback_dialog")
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(initialTab) }
    var selectedType by remember { mutableStateOf(FeedbackType.BUG) }
    var messageText by remember { mutableStateOf("") }

    val userFeedbacks by feedbackViewModel.userFeedbacks.collectAsState()
    val sendState by feedbackViewModel.sendState.collectAsState()

    LaunchedEffect(Unit) {
        feedbackViewModel.checkCooldown()
    }

    LaunchedEffect(sendState) {
        when (val state = sendState) {
            is FeedbackSendState.Sent -> {
                Toast.makeText(context, context.getString(R.string.feedback_sent_toast), Toast.LENGTH_SHORT).show()
                messageText = ""
                feedbackViewModel.resetSendState()
                selectedTab = 1
            }
            is FeedbackSendState.Error -> {
                Toast.makeText(context, context.getString(state.messageRes), Toast.LENGTH_SHORT).show()
            }
            else -> {}
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth(0.94f)
                    .wrapContentHeight()
                    .heightIn(max = 640.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp, bottom = 12.dp),
            ) {
                Text(
                    text = stringResource(R.string.feedback_dialog_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )

                Spacer(modifier = Modifier.height(12.dp))

                PrimaryTabRow(
                    selectedTabIndex = selectedTab,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text(stringResource(R.string.feedback_tab_new)) },
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.feedback_tab_history))
                                if (userFeedbacks.isNotEmpty()) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier =
                                            Modifier
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primaryContainer)
                                                .padding(horizontal = 6.dp, vertical = 2.dp),
                                    ) {
                                        Text(
                                            text = "${userFeedbacks.size}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }
                            }
                        },
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Box(
                    modifier =
                        Modifier
                            .weight(1f, fill = false)
                            .padding(horizontal = 20.dp),
                ) {
                    if (selectedTab == 0) {
                        NewFeedbackContent(
                            selectedType = selectedType,
                            onTypeSelected = { selectedType = it },
                            message = messageText,
                            onMessageChange = { if (it.length <= 2000) messageText = it },
                            sendState = sendState,
                            isLiteMode = isLiteMode,
                            onSend = {
                                feedbackViewModel.sendFeedback(selectedType, messageText)
                            },
                        )
                    } else {
                        FeedbackHistoryContent(
                            feedbacks = userFeedbacks,
                            onMarkRead = { feedbackViewModel.markReplyAsRead(it) },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = { openPlayStore(context) },
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Star,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.feedback_rate_play),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }

                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.close))
                    }
                }
            }
        }
    }
}

@Composable
@Suppress("FunctionName")
private fun NewFeedbackContent(
    selectedType: FeedbackType,
    onTypeSelected: (FeedbackType) -> Unit,
    message: String,
    onMessageChange: (String) -> Unit,
    sendState: FeedbackSendState,
    isLiteMode: Boolean,
    onSend: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            FilterChip(
                selected = selectedType == FeedbackType.BUG,
                onClick = { onTypeSelected(FeedbackType.BUG) },
                label = { Text(stringResource(R.string.feedback_type_bug)) },
            )
            FilterChip(
                selected = selectedType == FeedbackType.FEATURE,
                onClick = { onTypeSelected(FeedbackType.FEATURE) },
                label = { Text(stringResource(R.string.feedback_type_feature)) },
            )
            FilterChip(
                selected = selectedType == FeedbackType.OTHER,
                onClick = { onTypeSelected(FeedbackType.OTHER) },
                label = { Text(stringResource(R.string.feedback_type_other)) },
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = message,
            onValueChange = onMessageChange,
            placeholder = { Text(stringResource(R.string.feedback_message_hint)) },
            supportingText = {
                Text(
                    text = stringResource(R.string.feedback_char_count, message.length, 2000),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.End,
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            minLines = 3,
            maxLines = 5,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Diagnostics Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                ),
            shape = RoundedCornerShape(8.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.width(6.dp))
                val modeStr = if (isLiteMode) "Lite" else "Root"
                val deviceStr = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
                val diagText =
                    "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) • " +
                        "$modeStr • $deviceStr • Android ${Build.VERSION.SDK_INT}"
                Text(
                    text = diagText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        val isSending = sendState is FeedbackSendState.Sending
        val cooldownActive = sendState as? FeedbackSendState.CooldownActive

        Button(
            onClick = onSend,
            enabled = message.isNotBlank() && !isSending && cooldownActive == null,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        ) {
            if (isSending) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.feedback_sending))
            } else if (cooldownActive != null) {
                Text(stringResource(R.string.feedback_cooldown, cooldownActive.remainingMinutes))
            } else {
                Text(stringResource(R.string.feedback_send))
            }
        }
    }
}

@Composable
@Suppress("FunctionName")
private fun FeedbackHistoryContent(
    feedbacks: List<FeedbackItem>,
    onMarkRead: (String) -> Unit,
) {
    if (feedbacks.isEmpty()) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.feedback_no_history),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }

        LazyColumn(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(feedbacks, key = { it.id }) { item ->
                FeedbackHistoryCard(
                    item = item,
                    dateFormat = dateFormat,
                    onMarkRead = { onMarkRead(item.id) },
                )
            }
        }
    }
}

@Composable
@Suppress("FunctionName")
private fun FeedbackHistoryCard(
    item: FeedbackItem,
    dateFormat: SimpleDateFormat,
    onMarkRead: () -> Unit,
) {
    val isReplied = item.status == FeedbackStatus.REPLIED && !item.replyMessage.isNullOrBlank()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val typeRes =
                    when (item.type) {
                        FeedbackType.BUG -> R.string.feedback_type_bug
                        FeedbackType.FEATURE -> R.string.feedback_type_feature
                        FeedbackType.OTHER -> R.string.feedback_type_other
                    }
                Text(
                    text = stringResource(typeRes),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )

                Text(
                    text = if (item.createdAt > 0) dateFormat.format(Date(item.createdAt)) else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = item.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            if (isReplied) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors =
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        ),
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Outlined.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.feedback_reply_label),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                )
                            }
                            if (item.repliedAt != null && item.repliedAt > 0) {
                                Text(
                                    text = dateFormat.format(Date(item.repliedAt)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f),
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = item.replyMessage.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.feedback_status_pending),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun openPlayStore(context: Context) {
    val packageName = context.packageName
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    } catch (e: Exception) {
        val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName"))
        webIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(webIntent)
    }
}
