package com.whatstat.reborn.ui.screen.chatlist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.graphics.vector.ImageVector
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.whatstat.reborn.ui.component.ContactNameWithPhone
import com.whatstat.reborn.ui.component.SimpleScreenHeader
import com.whatstat.reborn.ui.tutorial.LocalTutorialManager
import com.whatstat.reborn.ui.tutorial.TutorialOverlay
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot

@Composable
fun ChatListScreen(
    viewModel: ChatListViewModel = hiltViewModel(),
    onChatSelected: (Long) -> Unit,
    onNavigateBack: () -> Unit
) {
    val chats by viewModel.filteredChats.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    // Tutorial integration
    val tutorialManager = LocalTutorialManager.current
    val tutorialState by tutorialManager.uiState.collectAsState()


    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // ── Header ───────────────────────────────────────────────
            SimpleScreenHeader(
                title = "Le tue chat",
                onBack = onNavigateBack
            )

            // ── Search Bar ───────────────────────────────────────────
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.onSearchQueryChange(it) },
                placeholder = {
                    Text(
                        "Cerca chat…",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Outlined.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .onGloballyPositioned {
                        if (tutorialState.isTutorialActive && tutorialState.currentScreen == "chatlist") {
                            tutorialManager.setTargetBounds("chatlist_search_field", it.boundsInRoot())
                        }
                    },
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                )
            )

            Spacer(Modifier.height(12.dp))

            // ── Chat counter ─────────────────────────────────────────
            Text(
                "${chats.size} chat",
                modifier = Modifier.padding(horizontal = 20.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(8.dp))

            // ── Chat List ────────────────────────────────────────────
            if (isLoading) {
                ChatListSkeletonContent(modifier = Modifier.fillMaxSize())
            } else {
                LazyColumn(
                    modifier = Modifier
                        .onGloballyPositioned {
                            if (tutorialState.isTutorialActive && tutorialState.currentScreen == "chatlist") {
                                tutorialManager.setTargetBounds("chatlist_list", it.boundsInRoot())
                            }
                        },
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(chats, key = { _, chat -> chat.chatId }) { index, chat ->
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn() + slideInVertically { it / 3 }
                        ) {
                            ChatListItem(
                                rank = index + 1,
                                name = chat.displayName,
                                jid = chat.jid,
                                messageCount = chat.totalMessages,
                                isGroup = chat.isGroup,
                                avatarPath = chat.avatarPath,
                                onClick = { onChatSelected(chat.chatId) },
                                modifier = if (index == 0) Modifier
                                    .onGloballyPositioned {
                                        if (tutorialState.isTutorialActive && tutorialState.currentScreen == "chatlist") {
                                            tutorialManager.setTargetBounds("chatlist_first_item", it.boundsInRoot())
                                        }
                                    } else Modifier,
                                badgeModifier = if (index == 0) Modifier
                                    .onGloballyPositioned {
                                        if (tutorialState.isTutorialActive && tutorialState.currentScreen == "chatlist") {
                                            tutorialManager.setTargetBounds("chatlist_message_count", it.boundsInRoot())
                                        }
                                    } else Modifier
                            )
                        }
                    }
                }
            }
        }

        // ── Tutorial Overlay ──────────────────────────────────────────
        if (tutorialState.isTutorialActive && tutorialState.currentScreen == "chatlist") {
            val currentStep = tutorialManager.currentStep
            if (currentStep != null) {
                val targetBounds = tutorialManager.getTargetBounds(currentStep.targetKey)
                if (targetBounds != null) {
                    TutorialOverlay(
                        step = currentStep,
                        targetBounds = targetBounds,
                        arrowDirection = currentStep.arrowDirection,
                        isVisible = true,
                        isLastStep = tutorialState.currentStepIndex >= tutorialState.steps.size - 1,
                        onNext = { tutorialManager.nextStep() },
                        onPrevious = { tutorialManager.previousStep() },
                        onClose = { tutorialManager.closeTutorial() },
                        onComplete = { tutorialManager.completeTutorial("chatlist") }
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatListItem(
    rank: Int,
    name: String,
    jid: String?,
    messageCount: Int,
    isGroup: Boolean,
    avatarPath: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badgeModifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Rank badge
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        if (rank <= 3) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "$rank",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (rank <= 3) FontWeight.Bold else FontWeight.Normal,
                    color = if (rank <= 3) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.width(14.dp))

            // Avatar image or icon fallback
            if (avatarPath != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(java.io.File(avatarPath))
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isGroup) Icons.Outlined.Group else Icons.Outlined.Person,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.width(10.dp))

            // Name + message count
            Column(modifier = Modifier.weight(1f)) {
                ContactNameWithPhone(
                    name = name,
                    jid = jid,
                    nameStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium
                    )
                )
                Text(
                    "%,d messaggi".format(messageCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Message count pill
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
                    .then(badgeModifier)
            ) {
                Text(
                    "%,d".format(messageCount),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
