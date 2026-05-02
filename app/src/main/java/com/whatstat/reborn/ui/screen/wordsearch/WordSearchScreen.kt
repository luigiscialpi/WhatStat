package com.whatstat.reborn.ui.screen.wordsearch

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.People
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.whatstat.reborn.ui.component.ContactNameWithPhone
import com.whatstat.reborn.ui.component.LoadingSection
import com.whatstat.reborn.ui.component.SimpleScreenHeader
import com.whatstat.reborn.ui.component.TimeRangeFilterRow
import com.whatstat.reborn.ui.tutorial.LocalTutorialManager
import com.whatstat.reborn.ui.tutorial.TutorialOverlay
import java.io.File

@Composable
fun WordSearchScreen(
    chatId: Long? = null,
    viewModel: WordSearchViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val results by viewModel.results.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val selectedRange by viewModel.selectedTimeRange.collectAsState()
    val customDateRange by viewModel.customDateRange.collectAsState()
    val chatContextName by viewModel.chatContextName.collectAsState()
    val chatContextAvatarPath by viewModel.chatContextAvatarPath.collectAsState()

    val tutorialManager = LocalTutorialManager.current
    val tutorialState by tutorialManager.uiState.collectAsState()

    LaunchedEffect(chatId) {
        viewModel.initialize(chatId)
    }

    LifecycleResumeEffect(Unit) {
        viewModel.syncFilterState()
        onPauseOrDispose { }
    }

    val isChatSpecific = chatId != null

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            SimpleScreenHeader(
                title = "Radar Parole",
                onBack = if (isChatSpecific) onNavigateBack else null,
                subtitle = chatContextName,
                avatarPath = chatContextAvatarPath
            )

            Box(modifier = Modifier.fillMaxWidth().height(4.dp)) {
                if (isLoading || isSearching) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }

            TimeRangeFilterRow(
                selectedRange = selectedRange,
                onRangeSelected = { viewModel.setTimeRange(it) },
                customDateRange = customDateRange,
                onDateRangeChanged = { viewModel.setCustomDateRange(it) },
                onSubmitClicked = { viewModel.startSearch() }
            )

            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.onSearchQueryChange(it) },
                    modifier = Modifier.weight(1f).onGloballyPositioned {
                        tutorialManager.setTargetBounds("wordsearch_search_field", it.boundsInRoot())
                    },
                    placeholder = { Text("Cerca una parola...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true
                )
                if (searchQuery.isNotEmpty()) {
                    FilledTonalButton(
                        onClick = { if (isSearching) viewModel.cancelSearch() else viewModel.startSearch() },
                        modifier = Modifier.padding(start = 8.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (isSearching) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                            contentColor = if (isSearching) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Icon(if (isSearching) Icons.Filled.Close else Icons.Filled.Search, contentDescription = if (isSearching) "Annulla" else "Cerca")
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            if (isLoading) {
                LoadingSection()
            } else if (results.isNotEmpty()) {
                Text("Top mittenti", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp))
                LazyColumn(
                    modifier = Modifier.weight(1f).onGloballyPositioned {
                        tutorialManager.setTargetBounds("wordsearch_results", it.boundsInRoot())
                    },
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(results) { result ->
                        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)), shape = RoundedCornerShape(12.dp)) {
                            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                                    if (result.avatarPath != null) {
                                        AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(result.avatarPath).crossfade(true).build(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                    } else {
                                        Icon(Icons.Outlined.People, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(24.dp))
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    ContactNameWithPhone(name = result.displayName ?: result.jid.substringBefore("@"), jid = result.jid, nameStyle = MaterialTheme.typography.titleMedium)
                                }
                                Spacer(Modifier.width(8.dp))
                                Text("${result.count}", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    item { Spacer(Modifier.height(48.dp)) }
                }
            } else if (searchQuery.length >= 2) {
                Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    Text("Nessun risultato per \"$searchQuery\"", style = MaterialTheme.typography.bodyLarge)
                }
            }
        }

        // Overlay Tutorial
        if (tutorialState.isTutorialActive && tutorialState.currentScreen == "wordsearch") {
            tutorialManager.currentStep?.let { step ->
                tutorialManager.getTargetBounds(step.targetKey)?.let { bounds ->
                    TutorialOverlay(
                        step = step, targetBounds = bounds, arrowDirection = step.arrowDirection,
                        isVisible = true, isLastStep = tutorialState.currentStepIndex >= tutorialState.steps.size - 1,
                        currentStepIndex = tutorialState.currentStepIndex,
                        onNext = { tutorialManager.nextStep() }, onPrevious = { tutorialManager.previousStep() },
                        onClose = { tutorialManager.closeTutorial() }, onComplete = { tutorialManager.completeTutorial("wordsearch") }
                    )
                }
            }
        }
    }
}