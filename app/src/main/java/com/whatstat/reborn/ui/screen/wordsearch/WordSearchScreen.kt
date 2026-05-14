package com.whatstat.reborn.ui.screen.wordsearch

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.whatstat.reborn.R
import com.whatstat.reborn.data.db.model.WordMessageResult
import com.whatstat.reborn.ui.component.ContactNameWithPhone
import com.whatstat.reborn.ui.component.LoadingSection
import com.whatstat.reborn.ui.component.SimpleScreenHeader
import com.whatstat.reborn.ui.component.TimeRangeFilterRow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("FunctionName")
fun WordSearchScreen(
    chatId: Long? = null,
    viewModel: WordSearchViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val results by viewModel.results.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val selectedRange by viewModel.selectedTimeRange.collectAsState()
    val customDateRange by viewModel.customDateRange.collectAsState()
    val firstMessageTimestamp by viewModel.firstMessageTimestamp.collectAsState()
    val chatContextName by viewModel.chatContextName.collectAsState()
    val chatContextAvatarPath by viewModel.chatContextAvatarPath.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()
    val messagePreview by viewModel.messagePreview.collectAsState()
    val isLoadingMessages by viewModel.isLoadingMessages.collectAsState()
    val isCaseSensitive by viewModel.isCaseSensitive.collectAsState()
    val isExactMatch by viewModel.isExactMatch.collectAsState()
    val onlyQuestions by viewModel.onlyQuestions.collectAsState()
    val isMultiWord by viewModel.isMultiWord.collectAsState()
    val multiWordMode by viewModel.multiWordMode.collectAsState()
    var showInfoDialog by remember { mutableStateOf(false) }

    LaunchedEffect(chatId) {
        viewModel.initialize(chatId)
    }

    LifecycleResumeEffect(Unit) {
        viewModel.syncFilterState()
        onPauseOrDispose { }
    }

    val isChatSpecific = chatId != null

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .statusBarsPadding(),
    ) {
        SimpleScreenHeader(
            title = stringResource(R.string.radar_parole_title),
            onBack = if (isChatSpecific) onNavigateBack else null,
            subtitle = chatContextName,
            avatarPath = chatContextAvatarPath,
        )

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(4.dp),
        ) {
            if (isLoading || isSearching) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }

        TimeRangeFilterRow(
            selectedRange = selectedRange,
            onRangeSelected = { viewModel.setTimeRange(it) },
            customDateRange = customDateRange,
            onDateRangeChanged = { viewModel.setCustomDateRange(it) },
            minDate = firstMessageTimestamp,
        )

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.onSearchQueryChange(it) },
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.word_search_placeholder)) },
                leadingIcon = {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = null,
                    )
                },
                shape = RoundedCornerShape(16.dp),
                singleLine = true,
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                            Icon(Icons.Outlined.Clear, contentDescription = stringResource(R.string.cancel))
                        }
                    }
                },
            )
            if (searchQuery.isNotEmpty()) {
                FilledTonalButton(
                    onClick = {
                        if (isSearching) {
                            viewModel.cancelSearch()
                        } else {
                            viewModel.startSearch()
                        }
                    },
                    modifier = Modifier.padding(start = 8.dp),
                    colors =
                        androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                            containerColor =
                                if (isSearching) {
                                    MaterialTheme.colorScheme.errorContainer
                                } else {
                                    MaterialTheme.colorScheme.primaryContainer
                                },
                            contentColor =
                                if (isSearching) {
                                    MaterialTheme.colorScheme.onErrorContainer
                                } else {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                },
                        ),
                ) {
                    Icon(
                        if (isSearching) Icons.Filled.Close else Icons.Filled.Search,
                        contentDescription = if (isSearching) stringResource(R.string.cancel) else stringResource(R.string.search_button),
                    )
                }
            }
        }

        // Riga opzioni di ricerca avanzata
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Chip Case Sensitive
            FilterChip(
                selected = isCaseSensitive,
                onClick = { viewModel.toggleCaseSensitive() },
                label = { Text("Aa") },
                leadingIcon = null,
            )
            // Chip Parola Esatta
            FilterChip(
                selected = isExactMatch,
                onClick = { viewModel.toggleExactMatch() },
                label = {
                    Text(
                        "ab",
                        style =
                            MaterialTheme.typography.labelLarge.copy(
                                textDecoration = TextDecoration.Underline,
                            ),
                    )
                },
                leadingIcon = null,
            )
            // Chip Solo Domande
            FilterChip(
                selected = onlyQuestions,
                onClick = { viewModel.toggleOnlyQuestions() },
                label = { Text("?") },
                leadingIcon = null,
            )
            // Chip Multi-parola (Toggle a 3 stati)
            FilterChip(
                selected = multiWordMode != MultiWordMode.NONE,
                onClick = { viewModel.cycleMultiWordMode() },
                label = {
                    val label =
                        when (multiWordMode) {
                            MultiWordMode.NONE -> stringResource(R.string.filter_multi)
                            MultiWordMode.AND -> "${stringResource(R.string.filter_multi)}: ${stringResource(R.string.and_label)}"
                            MultiWordMode.OR -> "${stringResource(R.string.filter_multi)}: ${stringResource(R.string.or_label)}"
                        }
                    Text(label)
                },
                leadingIcon = { Icon(Icons.Outlined.Link, contentDescription = null, modifier = Modifier.size(18.dp)) },
            )
            Spacer(Modifier.weight(1f))
            // Icona informativa spostata qui
            IconButton(onClick = { showInfoDialog = true }) {
                Icon(
                    Icons.Outlined.Info,
                    contentDescription = stringResource(R.string.info_dialog_title),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (suggestions.isNotEmpty()) {
            LazyRow(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(suggestions) { suggestion ->
                    SuggestionChip(
                        onClick = {
                            viewModel.onSearchQueryChange(suggestion)
                            viewModel.startSearch()
                        },
                        label = {
                            Text(
                                suggestion,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        },
                        border = null,
                        colors =
                            SuggestionChipDefaults.suggestionChipColors(
                                containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        if (isLoading) {
            LoadingSection()
        } else if (results.isNotEmpty()) {
            Text(
                stringResource(R.string.occurrences_by_sender),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(results) { result ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors =
                            CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            ),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                            horizontalArrangement = Arrangement.Start,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Immagine Profilo
                            Box(
                                modifier =
                                    Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (result.avatarPath != null) {
                                    AsyncImage(
                                        model =
                                            ImageRequest.Builder(LocalContext.current)
                                                .data(result.avatarPath)
                                                .crossfade(true)
                                                .build(),
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop,
                                    )
                                } else {
                                    Icon(
                                        Icons.Outlined.People,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
                            }

                            Spacer(Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                ContactNameWithPhone(
                                    name = result.displayName ?: result.jid.substringBefore("@"),
                                    jid = result.jid,
                                    nameStyle = MaterialTheme.typography.titleMedium,
                                )
                            }

                            Spacer(Modifier.width(8.dp))

                            Box(
                                modifier =
                                    Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f))
                                        .clickable {
                                            viewModel.loadMessagesForSender(
                                                senderJid = result.jid,
                                                senderName =
                                                    result.displayName
                                                        ?: result.jid.substringBefore("@"),
                                            )
                                        }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                            ) {
                                Text(
                                    "${result.count}",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
                // Bottom spacer
                item { Spacer(Modifier.height(48.dp)) }
            }
        } else if (searchQuery.length >= 2) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.no_results_word),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.word_search_hint_min),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (messagePreview != null) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.dismissMessagePreview() },
            sheetState = rememberModalBottomSheetState(),
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            dragHandle = {
                Box(
                    modifier =
                        Modifier
                            .padding(vertical = 12.dp)
                            .width(32.dp)
                            .height(4.dp)
                            .background(
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                RoundedCornerShape(2.dp),
                            ),
                )
            },
        ) {
            WordMessageSheet(
                senderName = messagePreview!!.first,
                senderAvatarPath = messagePreview!!.second,
                messages = messagePreview!!.third,
                searchWord = searchQuery,
                isLoading = isLoadingMessages,
                isChatSpecific = isChatSpecific,
                caseSensitive = isCaseSensitive,
            )
        }
    }

    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            title = { Text(stringResource(R.string.info_dialog_title)) },
            text = {
                Text(stringResource(R.string.radar_info_text))
            },
            confirmButton = {
                TextButton(onClick = { showInfoDialog = false }) {
                    Text(stringResource(R.string.ho_capito))
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
@Suppress("FunctionName")
private fun WordMessageSheet(
    senderName: String,
    senderAvatarPath: String?,
    messages: List<WordMessageResult>,
    searchWord: String,
    isLoading: Boolean,
    isChatSpecific: Boolean = false,
    caseSensitive: Boolean = false,
) {
    val dateFormatter = remember { SimpleDateFormat("dd/MM/yy", Locale.ITALIAN) }
    var sortAscending by remember { mutableStateOf(false) }
    var displayedCount by remember { mutableIntStateOf(50) }

    val sortedAll =
        remember(messages, sortAscending) {
            if (sortAscending) {
                messages.sortedBy { it.timestamp }
            } else {
                messages.sortedByDescending { it.timestamp }
            }
        }
    val visibleItems = sortedAll.take(displayedCount)
    val hasMore = displayedCount < sortedAll.size

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
    ) {
        // Header: avatar + titolo
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (senderAvatarPath != null) {
                AsyncImage(
                    model =
                        ImageRequest.Builder(LocalContext.current)
                            .data(java.io.File(senderAvatarPath))
                            .crossfade(true)
                            .build(),
                    contentDescription = null,
                    modifier =
                        Modifier
                            .size(48.dp)
                            .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
                Spacer(Modifier.width(12.dp))
            }
            Column {
                Text(
                    "${stringResource(R.string.results_for)} \"$searchWord\"",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    senderName,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        // Contatore + toggle ordinamento
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.messages_count_suffix, messages.size),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = { sortAscending = !sortAscending }) {
                Icon(Icons.Outlined.SwapVert, contentDescription = stringResource(R.string.radar_parole_title))
                Spacer(Modifier.width(4.dp))
                Text(if (sortAscending) stringResource(R.string.sort_oldest) else stringResource(R.string.sort_newest))
            }
        }
        Spacer(Modifier.height(12.dp))

        // Lista messaggi
        when {
            isLoading -> LoadingSection()
            messages.isEmpty() -> {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(R.string.no_results_word),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            else -> {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(visibleItems) { msg ->
                        MessagePreviewCard(
                            message = msg,
                            searchWord = searchWord,
                            dateFormatter = dateFormatter,
                            showChatName = !isChatSpecific,
                            caseSensitive = caseSensitive,
                        )
                    }
                    // Pulsante "Carica altri" (paginazione client-side)
                    if (hasMore) {
                        item {
                            OutlinedButton(
                                onClick = { displayedCount += 50 },
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                            ) {
                                Text(
                                    "${stringResource(
                                        R.string.load_more,
                                    )} (${sortedAll.size - displayedCount} ${stringResource(R.string.show_less)})",
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
@Suppress("FunctionName")
private fun MessagePreviewCard(
    message: WordMessageResult,
    searchWord: String,
    dateFormatter: SimpleDateFormat,
    showChatName: Boolean = true,
    caseSensitive: Boolean = false,
) {
    Card(
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Riga superiore: nome chat (se globale) + data
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (showChatName) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Outlined.ChatBubbleOutline,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            message.chatDisplayName,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                } else {
                    Spacer(Modifier.weight(1f))
                }
                Text(
                    dateFormatter.format(Date(message.timestamp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))

            // Testo messaggio con parola evidenziata
            HighlightedText(
                text = message.text,
                highlight = searchWord,
                style = MaterialTheme.typography.bodyMedium,
                highlightStyle =
                    SpanStyle(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    ),
                caseSensitive = caseSensitive,
            )
        }
    }
}

@Composable
@Suppress("FunctionName")
private fun HighlightedText(
    text: String,
    highlight: String,
    style: TextStyle,
    highlightStyle: SpanStyle,
    caseSensitive: Boolean = false,
) {
    val annotated =
        remember(text, highlight, caseSensitive) {
            buildAnnotatedString {
                val haystack = if (caseSensitive) text else text.lowercase()
                val needle = if (caseSensitive) highlight else highlight.lowercase()
                var idx = 0
                while (idx < text.length) {
                    val found = haystack.indexOf(needle, idx)
                    if (found == -1) {
                        append(text.substring(idx))
                        break
                    }
                    append(text.substring(idx, found))
                    withStyle(highlightStyle) {
                        append(text.substring(found, found + highlight.length))
                    }
                    idx = found + highlight.length
                }
            }
        }
    Text(
        text = annotated,
        style = style,
        maxLines = 4,
        overflow = TextOverflow.Ellipsis,
    )
}
