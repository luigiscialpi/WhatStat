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
            title = "Storia del Gruppo",
            subtitle = chatName ?: "Caricamento...",
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
                            text = "Nessun evento storico trovato per questo gruppo.",
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
                            val currentEvent = uiModel.event
                            val isNameChange = currentEvent is com.whatstat.reborn.domain.model.GroupEvent.NameChange
                            // L'evento precedente cronologicamente è all'indice index + 1
                            val olderIndex = index + 1
                            val olderUiModel = state.events.getOrNull(olderIndex)
                            val showHeader = olderUiModel == null ||
                                olderUiModel.userName != uiModel.userName ||
                                olderUiModel.event.javaClass != currentEvent.javaClass
                            ChatBubbleItem(
                                uiModel = uiModel,
                                isNameChange = isNameChange,
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
            // Sinistra: Nuovi nomi
            Box(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = "Nuovi nomi",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Nuovi nomi",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            // Destra: Entrate/Uscite
            Box(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Entrate/Uscite",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Outlined.Group,
                        contentDescription = "Entrate/Uscite",
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
    isNameChange: Boolean,
    showHeader: Boolean
) {
    val arrangement = if (isNameChange) Arrangement.Start else Arrangement.End

    val topPadding = if (showHeader) 8.dp else 1.dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = topPadding, bottom = 2.dp),
        horizontalArrangement = arrangement
    ) {
        if (isNameChange) {
            if (showHeader) {
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .padding(end = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
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
            } else {
                Spacer(modifier = Modifier.width(40.dp))
            }
        }

        // Forma della nuvoletta
        val bubbleShape = androidx.compose.foundation.shape.RoundedCornerShape(
            topStart = if (isNameChange && showHeader) 0.dp else 12.dp,
            topEnd = if (!isNameChange && showHeader) 0.dp else 12.dp,
            bottomStart = 12.dp,
            bottomEnd = 12.dp
        )

        val bubbleColor = if (isNameChange) {
            androidx.compose.ui.graphics.Color(0xFFE6F4EA)
        } else {
            MaterialTheme.colorScheme.primaryContainer
        }

        val textColor = if (isNameChange) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.onPrimaryContainer
        }

        Surface(
            shape = bubbleShape,
            color = bubbleColor,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Column(
                modifier = Modifier.padding(
                    start = 10.dp,
                    end = 10.dp,
                    top = if (showHeader) 10.dp else 4.dp,
                    bottom = 6.dp
                )
            ) {
                if (showHeader && isNameChange) {
                    Text(
                        text = uiModel.userName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    if (uiModel.secondaryText != null) {
                        Text(
                            text = uiModel.secondaryText,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = textColor
                        )
                    }
                } else {
                    if (isNameChange) {
                        // Senza header: solo il nome nuovo, niente frase introduttiva
                        if (uiModel.secondaryText != null) {
                            Text(
                                text = uiModel.secondaryText,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = textColor
                            )
                        }
                    } else {
                        Text(
                            text = uiModel.primaryText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = textColor
                        )
                        if (uiModel.secondaryText != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = uiModel.secondaryText,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = textColor
                            )
                        }
                    }
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
    }
}
