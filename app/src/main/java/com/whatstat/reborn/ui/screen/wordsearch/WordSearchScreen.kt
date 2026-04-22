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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.outlined.People
import androidx.hilt.navigation.compose.hiltViewModel
import com.whatstat.reborn.data.db.model.TimeRange
import com.whatstat.reborn.ui.component.DateRangePicker
import com.whatstat.reborn.ui.component.LoadingSection
import com.whatstat.reborn.ui.component.ContactNameWithPhone
import com.whatstat.reborn.ui.component.SimpleScreenHeader
import com.whatstat.reborn.ui.component.TimeRangeFilterRow

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

    LaunchedEffect(chatId) {
        chatId?.let { viewModel.setChatId(it) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        SimpleScreenHeader(title = "Radar Parole", onBack = onNavigateBack)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
        ) {
            if (isLoading || isSearching) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }

        TimeRangeFilterRow(
            selectedRange = selectedRange,
            onRangeSelected = { viewModel.setTimeRange(it) },
            customDateRange = customDateRange,
            onDateRangeChanged = { viewModel.setCustomDateRange(it) }
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.onSearchQueryChange(it) },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Cerca una parola...") },
                leadingIcon = { 
                    Icon(
                        Icons.Filled.Search, 
                        contentDescription = null 
                    ) 
                },
                shape = RoundedCornerShape(16.dp),
                singleLine = true
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
                    colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                        containerColor = if (isSearching) 
                            MaterialTheme.colorScheme.errorContainer 
                        else 
                            MaterialTheme.colorScheme.primaryContainer,
                        contentColor = if (isSearching) 
                            MaterialTheme.colorScheme.onErrorContainer 
                        else 
                            MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Icon(
                        if (isSearching) Icons.Filled.Close else Icons.Filled.Search,
                        contentDescription = if (isSearching) "Annulla" else "Cerca"
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        if (isLoading) {
            LoadingSection()
        } else if (results.isNotEmpty()) {
            Text(
                "Top mittenti",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(results) { result ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.Start,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Immagine Profilo
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                if (result.avatarPath != null) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(result.avatarPath)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(
                                        Icons.Outlined.People,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                            
                            Spacer(Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                ContactNameWithPhone(
                                    name = result.displayName ?: result.jid.substringBefore("@"),
                                    jid = result.jid,
                                    nameStyle = MaterialTheme.typography.titleMedium
                                )
                            }
                            
                            Spacer(Modifier.width(8.dp))
                            
                            Text(
                                "${result.count}",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                // Bottom spacer
                item { Spacer(Modifier.height(48.dp)) }
            }
        } else if (searchQuery.length >= 2) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Nessun risultato per \"$searchQuery\"",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Inserisci almeno 2 caratteri per cercare",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}