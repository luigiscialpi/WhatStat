package com.whatstat.reborn.ui.screen.wordsearch

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.whatstat.reborn.ui.component.ContactNameWithPhone
import com.whatstat.reborn.ui.component.SimpleScreenHeader
import com.whatstat.reborn.ui.component.TimeRangeFilterRow
import com.whatstat.reborn.ui.navigation.NavGraphRoute
import com.whatstat.reborn.ui.navigation.BottomNavigationBar
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

    LaunchedEffect(chatId) {
        viewModel.initialize(chatId)
    }

    LifecycleResumeEffect(Unit) {
        viewModel.syncFilterState()
        onPauseOrDispose { }
    }

    Scaffold(
        topBar = {
            SimpleScreenHeader(
                title = chatContextName?.let { "Cerca in: $it" } ?: "Cerca messaggi",
                onBack = onNavigateBack
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Search Input
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.onSearchQueryChange(it) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                placeholder = { Text("Inserisci parola (min. 2 caratteri)") },
                trailingIcon = {
                    if (isSearching) {
                        IconButton(onClick = { viewModel.cancelSearch() }) {
                            Icon(Icons.Outlined.Close, contentDescription = "Annulla")
                        }
                    } else {
                        IconButton(onClick = { viewModel.startSearch() }, enabled = searchQuery.length >= 2) {
                            Icon(Icons.Outlined.Search, contentDescription = "Cerca")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp)
            )

            TimeRangeFilterRow(
                selectedRange = selectedRange,
                onRangeSelected = { viewModel.setTimeRange(it) },
                customDateRange = customDateRange,
                onDateRangeChanged = { viewModel.setCustomDateRange(it) },
                onSubmitClicked = { viewModel.applyCustomDateRange() },
                isSearching = isLoading
            )

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
                    items(results) { result ->
                        WordSearchItem(result)
                    }
                }
            }
        }
    }
}

@Composable
private fun WordSearchItem(result: com.whatstat.reborn.data.db.model.WordSearchResult) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (result.avatarPath != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current).data(File(result.avatarPath)).crossfade(true).build(),
                    contentDescription = null,
                    modifier = Modifier.size(40.dp).clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Person, contentDescription = null)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column {
                ContactNameWithPhone(
                    name = result.displayName ?: result.jid.substringBefore("@"),
                    jid = result.jid,
                    nameStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "${result.count} occorrenze",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}