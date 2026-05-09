package com.whatstat.reborn.ui.screen.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Group
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.res.stringResource
import com.whatstat.reborn.R
import coil.compose.AsyncImage
import com.whatstat.reborn.domain.model.GroupEventUiModel
import com.whatstat.reborn.ui.component.LoadingSection
import com.whatstat.reborn.ui.component.SimpleScreenHeader
import java.io.File

@Composable
fun GroupHistoryScreen(
    chatId: Long?,
    viewModel: GroupHistoryViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val chatName by viewModel.chatContextName.collectAsState()
    val chatAvatar by viewModel.chatContextAvatarPath.collectAsState()

    LaunchedEffect(chatId) {
        if (chatId != null) {
            viewModel.loadHistory(chatId)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .background(MaterialTheme.colorScheme.background)
    ) {
        SimpleScreenHeader(
            title = stringResource(R.string.group_history_title),
            subtitle = chatName ?: stringResource(R.string.loading),
            avatarPath = chatAvatar,
            onBack = onNavigateBack
        )

        // Legenda timeline
        TimelineLegendBar()

        when (val state = uiState) {
            is GroupHistoryViewModel.HistoryUiState.Loading -> {
                LoadingSection()
            }
            is GroupHistoryViewModel.HistoryUiState.Error -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = "Errore: ${state.message}", color = MaterialTheme.colorScheme.error)
                }
            }
            is GroupHistoryViewModel.HistoryUiState.Success -> {
                if (state.events.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.group_history_no_events),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                        reverseLayout = true
                    ) {
                        itemsIndexed(state.events) { index, uiModel ->
                            val isLeftAligned = uiModel.isLeftAligned
                            
                            // L'evento precedente cronologicamente (più vecchio) è all'indice index + 1
                            // Poiché usiamo reverseLayout=true, l'indice index+1 è quello che sta SOPRA visivamente
                            val olderIndex = index + 1
                            val olderUiModel = state.events.getOrNull(olderIndex)
                            
                            val showHeader = olderUiModel == null || 
                                             olderUiModel.userJid != uiModel.userJid || 
                                             olderUiModel.isLeftAligned != uiModel.isLeftAligned
                                             
                            ChatBubbleItem(
                                uiModel = uiModel,
                                isLeftAligned = isLeftAligned,
                                showHeader = showHeader
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TimelineLegendBar() {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 0.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Sinistra: Nomi/Descrizioni
            Box(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = stringResource(R.string.history_legend_names) + "/" + stringResource(R.string.history_legend_descriptions),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.history_legend_names),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "/",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = stringResource(R.string.history_legend_descriptions),
                            style = MaterialTheme.typography.titleMedium,
                            color = androidx.compose.ui.graphics.Color(0xFF0288D1) // Azzurro più scuro per la legenda
                        )
                    }
                }
            }
            // Destra: Entrate/Uscite
            Box(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.history_legend_io),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Outlined.Group,
                        contentDescription = stringResource(R.string.history_legend_io),
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ChatBubbleItem(
    uiModel: GroupEventUiModel,
    isLeftAligned: Boolean,
    showHeader: Boolean
) {
    val arrangement = if (isLeftAligned) Arrangement.Start else Arrangement.End
    
    // Padding più stretto tra eventi consecutivi
    val topPadding = if (showHeader) 8.dp else 2.dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = topPadding, bottom = 2.dp),
        horizontalArrangement = arrangement
    ) {
        if (isLeftAligned) {
            // Spazio per Avatar (Sinistra)
            AvatarSection(uiModel, showHeader)
        }

        // Forma della nuvoletta
        val bubbleShape = androidx.compose.foundation.shape.RoundedCornerShape(
            topStart = if (isLeftAligned && showHeader) 0.dp else 12.dp,
            topEnd = if (!isLeftAligned && showHeader) 0.dp else 12.dp,
            bottomStart = 12.dp,
            bottomEnd = 12.dp
        )

        val bubbleColor = when {
            uiModel.isDescriptionChange -> androidx.compose.ui.graphics.Color(0xFFE1F5FE) // Azzurrino tenue (Description style)
            isLeftAligned -> androidx.compose.ui.graphics.Color(0xFFE6F4EA) // Verde chiaro WhatsApp ricevuto
            else -> androidx.compose.ui.graphics.Color(0xFFEEEEEE) // Grigio chiaro leggermente più scuro (Participant Action)
        }

        val textColor = MaterialTheme.colorScheme.onSurfaceVariant

        Surface(
            shape = bubbleShape,
            color = bubbleColor,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                if (showHeader && isLeftAligned) {
                    Text(
                        text = uiModel.userName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }

                if (uiModel.primaryText.isNotEmpty()) {
                    Text(
                        text = uiModel.primaryText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = textColor
                    )
                }
                
                if (uiModel.secondaryText != null) {
                    if (uiModel.primaryText.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    Text(
                        text = uiModel.secondaryText,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = textColor
                    )
                }

                // Data e ora in basso a destra
                Text(
                    text = "${uiModel.formattedDate} - ${uiModel.formattedTime}",
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor.copy(alpha = 0.7f),
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(top = 4.dp)
                )
            }
        }

        if (!isLeftAligned) {
            // Spazio per Avatar (Destra)
            AvatarSection(uiModel, showHeader, isRight = true)
        }
    }
}

@Composable
fun AvatarSection(uiModel: GroupEventUiModel, showHeader: Boolean, isRight: Boolean = false) {
    Box(
        modifier = Modifier
            .width(40.dp)
            .padding(
                start = if (isRight) 8.dp else 0.dp,
                end = if (isRight) 0.dp else 8.dp
            )
    ) {
        if (showHeader) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (uiModel.avatarPath != null) {
                    AsyncImage(
                        model = File(uiModel.avatarPath),
                        contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        text = uiModel.userName.take(1).uppercase(),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
