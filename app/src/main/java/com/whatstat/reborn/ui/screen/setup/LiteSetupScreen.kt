package com.whatstat.reborn.ui.screen.setup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiteSetupScreen(
    viewModel: LiteSetupViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onImportComplete: () -> Unit
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.isComplete) {
        if (state.isComplete) onImportComplete()
    }

    val zipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.onZipSelected(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Importa Chat ZIP") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Indietro")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.surface
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CloudUpload,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                
                Spacer(Modifier.height(24.dp))
                
                Text(
                    "Modalità Lite (No Root)",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(Modifier.height(8.dp))
                
                Text(
                    "Seleziona il file .zip esportato da WhatsApp.\nL'app estrarrà i messaggi e i media localmente.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                
                Spacer(Modifier.height(48.dp))
                
                if (state.isLoading) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text("Elaborazione in corso...")
                } else {
                    Button(
                        onClick = { zipLauncher.launch("application/zip") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("SELEZIONA FILE ZIP")
                    }
                }
                
                state.error?.let {
                    Spacer(Modifier.height(16.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                }
            }
        }
    }

    if (state.showSenderSelection) {
        SenderSelectionDialog(
            senders = state.identifiedSenders,
            onSenderSelected = { viewModel.onSenderSelected(it) }
        )
    }
}

@Composable
fun SenderSelectionDialog(
    senders: List<String>,
    onSenderSelected: (String) -> Unit
) {
    Dialog(onDismissRequest = { /* Obbligatorio selezionare */ }) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 400.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(Modifier.padding(24.dp)) {
                Text(
                    "Chi sei tu?",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Seleziona il tuo nome dalla lista per distinguere i messaggi inviati da quelli ricevuti.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(senders) { sender ->
                        Text(
                            text = sender,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSenderSelected(sender) }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}
