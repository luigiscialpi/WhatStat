package com.whatstat.reborn.ui.screen.setup

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.whatstat.reborn.data.root.FileExtractor
import android.Manifest
import android.os.Build
import android.widget.Toast

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    viewModel: SetupViewModel = hiltViewModel(),
    liteViewModel: LiteSetupViewModel = hiltViewModel(),
    incomingZipUri: String? = null,
    onUriConsumed: () -> Unit = {},
    onSetupComplete: () -> Unit,
    onNavigateToDashboard: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val liteState by liteViewModel.state.collectAsState()

    // Se c'è un URI in ingresso al momento della prima composizione, blocchiamo
    // la navigazione automatica (state.isComplete) finché il Lite import non è
    // davvero completato (liteState.isComplete). Senza questo flag, Compose può
    // eseguire il LaunchedEffect di navigazione prima che onZipSelected() abbia
    // avuto il tempo di impostare isLoading=true nella composizione successiva.
    val awaitLiteImport = remember { incomingZipUri != null }

    // Se arriva un URI dall'intent (cold start / condivisione da WhatsApp),
    // avviamo l'import direttamente dal tab Lite senza navigare a LiteSetupScreen.
    LaunchedEffect(incomingZipUri) {
        if (incomingZipUri != null) {
            liteViewModel.onZipSelected(android.net.Uri.parse(incomingZipUri))
            onUriConsumed()
        }
    }

    // Navigazione automatica verso Main:
    // - se stiamo aspettando il completamento di un import URI, lo gestiamo
    //   solo con liteState.isComplete (via il LaunchedEffect sotto)
    // - altrimenti: quando il setup generico è completo e non c'è un import in corso
    LaunchedEffect(state.isComplete, liteState.isLoading, liteState.showSenderSelection) {
        if (awaitLiteImport) return@LaunchedEffect
        if (state.isComplete && !liteState.isLoading && !liteState.showSenderSelection) {
            onSetupComplete()
        }
    }

    // Quando un import avviato da URI è completato, naviga verso Main.
    LaunchedEffect(liteState.isComplete) {
        if (awaitLiteImport && liteState.isComplete) {
            onSetupComplete()
        }
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(
                context,
                "Il permesso notifiche è necessario per seguire il progresso in background",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    fun checkAndStart(isManual: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (isManual) viewModel.startManualSetup() else viewModel.startSetup()
    }

    // 0 = Lite (No Root), 1 = Root
    var selectedTab by remember { mutableIntStateOf(0) }
    val zipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { liteViewModel.onZipSelected(it) }
    }

    BackHandler(enabled = liteState.isLoading || liteState.showSenderSelection) {}

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.background
                    )
                )
            )
    ) {
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── Logo / Icona ─────────────────────────────────────
            val infiniteTransition = rememberInfiniteTransition(label = "pulse")
            val pulseAlpha by infiniteTransition.animateFloat(
                initialValue = 0.6f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1500),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "pulseAlpha"
            )

            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = null,
                    modifier = Modifier
                        .size(48.dp)
                        .alpha(pulseAlpha),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(Modifier.height(28.dp))

            Text(
                "WhatStat Reborn",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(Modifier.height(8.dp))

            Text(
                "Analisi completa delle tue chat WhatsApp.\nTutti i dati rimangono sul dispositivo.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
            )

            Spacer(Modifier.height(32.dp))

            // ── Secondary Tabs ───────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column {
                    SecondaryTabRow(selectedTabIndex = selectedTab) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = {
                                if (!liteState.isLoading && !liteState.showSenderSelection) {
                                    selectedTab = 0
                                }
                            },
                            text = { Text("Lite") },
                            icon = {
                                Icon(
                                    imageVector = Icons.Outlined.PhoneAndroid,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = {
                                if (!liteState.isLoading && !liteState.showSenderSelection) {
                                    selectedTab = 1
                                }
                            },
                            text = { Text("Root") },
                            icon = {
                                Icon(
                                    imageVector = Icons.Outlined.Terminal,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        )
                    }

                    // ── Tab 0: Lite ──────────────────────────────
                    AnimatedVisibility(visible = selectedTab == 0) {
                        LiteTabContent(
                            hasLiteDb = state.hasLiteDb,
                            isLoading = liteState.isLoading,
                            progressPercent = liteState.progressPercent,
                            progressLabel = liteState.progressLabel,
                            error = liteState.error,
                            onNavigateToDashboard = {
                                viewModel.activateLiteMode()
                                onNavigateToDashboard()
                            },
                            onImportZip = { zipLauncher.launch("application/zip") }
                        )
                    }

                    // ── Tab 1: Root ──────────────────────────────
                    AnimatedVisibility(visible = selectedTab == 1) {
                        RootTabContent(
                            state = state,
                            onCheckAndStart = { checkAndStart(false) },
                            onCheckAndStartManual = { checkAndStart(true) },
                            onShowManualMode = { viewModel.showManualMode() },
                            onCancelSetup = { viewModel.cancelSetup() },
                            onResumeRoot = { viewModel.resumeRootSession() },
                            onClearRoot = { viewModel.clearRootSession() },
                            onManualFileSelected = { uri, type ->
                                viewModel.handleManualFileSelected(uri, type)
                            }
                        )
                    }
                }
            }
        }

        // ── Overlay cache check ──────────────────────────────────
        if (state.isCheckingCache) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.95f))
                    .clickable(enabled = false) {},
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(56.dp),
                        strokeWidth = 4.dp
                    )
                    Spacer(Modifier.height(24.dp))
                    Text(
                        "Verifica dei dati in corso...",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }

    if (liteState.showSenderSelection) {
        SenderSelectionDialog(
            senders = liteState.identifiedSenders,
            onSenderSelected = { liteViewModel.onSenderSelected(it) }
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

// ── Tab Lite ─────────────────────────────────────────────────────────────────

@Composable
private fun LiteTabContent(
    hasLiteDb: Boolean,
    isLoading: Boolean,
    progressPercent: Float,
    progressLabel: String,
    error: String?,
    onNavigateToDashboard: () -> Unit,
    onImportZip: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Hint contestuale
        Text(
            text = if (hasLiteDb)
                "Dati precedentemente importati trovati. Puoi accedere alla dashboard o importare un'altra chat ZIP."
            else
                "Nessun dato importato. Carica una chat WhatsApp esportata come file ZIP.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(4.dp))

        // Pulsante dashboard (abilitato solo se il db lite esiste)
        Button(
            onClick = onNavigateToDashboard,
            enabled = hasLiteDb,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Icon(
                imageVector = Icons.Filled.Dashboard,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text("Vai alla dashboard", style = MaterialTheme.typography.labelLarge)
        }

        if (isLoading) {
            val progressInt = (progressPercent * 100).toInt()
            Text(
                text = if (progressLabel.isNotEmpty()) progressLabel else "Elaborazione in corso...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LinearProgressIndicator(
                progress = { progressPercent },
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "$progressInt%",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        } else {
            OutlinedButton(
                onClick = onImportZip,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                )
            ) {
                Icon(
                    imageVector = Icons.Outlined.FolderZip,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "Importa Chat da file ZIP",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        if (!error.isNullOrBlank()) {
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

// ── Tab Root ──────────────────────────────────────────────────────────────────

@Composable
private fun RootTabContent(
    state: SetupState,
    onCheckAndStart: () -> Unit,
    onCheckAndStartManual: () -> Unit,
    onShowManualMode: () -> Unit,
    onCancelSetup: () -> Unit,
    onResumeRoot: () -> Unit,
    onClearRoot: () -> Unit,
    onManualFileSelected: (android.net.Uri, String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── Step indicators ──────────────────────────────────────
        SetupStep(
            label = "Accesso root",
            status = state.rootStatus,
            icon = Icons.Outlined.AdminPanelSettings
        )
        SetupStep(
            label = "Estrazione file",
            status = state.extractionStatus,
            icon = Icons.Outlined.FolderOpen
        )
        SetupStep(
            label = "Decifratura database",
            status = state.decryptionStatus,
            icon = Icons.Outlined.Key,
            progress = if (state.decryptionStatus == StepStatus.LOADING) state.decryptionProgress else null
        )
        SetupStep(
            label = "Lettura contatti",
            status = state.contactsStatus,
            icon = Icons.Outlined.Contacts
        )

        Spacer(Modifier.height(8.dp))

        // ── Dashboard & Clear Session ──────────────────────────
        // Hint contestuale per Root (simile a Lite)
        if (!state.isLoading) {
            Text(
                text = if (state.isRootReady)
                    "Sessione Root preservata trovata. Puoi accedere alla dashboard o avviare una nuova analisi."
                else
                    "Nessuna sessione Root attiva. Avvia l'analisi per estrarre i dati aggiornati.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onResumeRoot,
                    enabled = state.isRootReady,
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(Icons.Filled.Dashboard, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Vai alla dashboard", style = MaterialTheme.typography.labelLarge)
                }

                if (state.isRootReady) {
                    OutlinedButton(
                        onClick = onClearRoot,
                        modifier = Modifier.size(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(0.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Default.Delete, null, Modifier.size(20.dp))
                    }
                }
            }
        }

        // ── Error card & Manual Mode ─────────────────────────────
        AnimatedVisibility(
            visible = state.error != null,
            enter = fadeIn() + slideInVertically()
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    SelectionContainer {
                        Text(
                            state.error ?: "",
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                if (!state.isManualModeVisible) {
                    Button(
                        onClick = onShowManualMode,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Seleziona File Manualmente")
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = state.isManualModeVisible,
            enter = fadeIn() + slideInVertically()
        ) {
            var currentSelectionType by remember { mutableStateOf("") }
            val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
                if (uri != null && currentSelectionType.isNotEmpty()) {
                    onManualFileSelected(uri, currentSelectionType)
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Opzioni Manuali (No Root)", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(16.dp))

                    ManualFileButton(
                        label = "File Chiave (key)",
                        isLoaded = state.hasManualKey,
                        onClick = {
                            currentSelectionType = FileExtractor.KEY_DEST
                            launcher.launch("*/*")
                        }
                    )
                    ManualFileButton(
                        label = "Database (msgstore.db.crypt14)",
                        isLoaded = state.hasManualCrypt14,
                        onClick = {
                            currentSelectionType = FileExtractor.CRYPT14_DEST
                            launcher.launch("*/*")
                        }
                    )
                    ManualFileButton(
                        label = "Database già decifrato (msgstore.db)",
                        isLoaded = state.hasManualDbDecrypted,
                        onClick = {
                            currentSelectionType = FileExtractor.DB_DECRYPTED_DEST
                            launcher.launch("application/octet-stream")
                        }
                    )
                    ManualFileButton(
                        label = "Contatti (wa.db) - Opzionale",
                        isLoaded = state.hasManualWaDb,
                        onClick = {
                            currentSelectionType = FileExtractor.WA_DB_DEST
                            launcher.launch("*/*")
                        }
                    )
                }
            }
        }

        // ── Action button ────────────────────────────────────────

        if (state.isLoading) {
            OutlinedButton(
                onClick = onCancelSetup,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Annulla",
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text("Annulla", style = MaterialTheme.typography.labelLarge)
            }
        } else {
            Button(
                onClick = {
                    if (state.isManualModeVisible) onCheckAndStartManual()
                    else onCheckAndStart()
                },
                enabled = !state.isLoading && (
                    !state.isManualModeVisible ||
                    (state.hasManualKey && state.hasManualCrypt14) ||
                    (state.hasManualKey && state.hasManualDbDecrypted)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(
                    if (state.isManualModeVisible) "Decifra Database" else "Inizia analisi",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

// ── Componenti condivisi ──────────────────────────────────────────────────────

@Composable
private fun ManualFileButton(
    label: String,
    isLoaded: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(
            onClick = onClick,
            modifier = Modifier.weight(1f)
        ) {
            Text(if (isLoaded) "Sostituisci" else "Seleziona")
        }
        Spacer(Modifier.width(12.dp))
        Text(label, modifier = Modifier.weight(2f), style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.width(8.dp))
        Icon(
            imageVector = if (isLoaded) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (isLoaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
private fun SetupStep(
    label: String,
    status: StepStatus,
    icon: ImageVector,
    progress: Float? = null
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = when (status) {
                    StepStatus.SUCCESS -> MaterialTheme.colorScheme.primary
                    StepStatus.ERROR   -> MaterialTheme.colorScheme.error
                    else               -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Spacer(Modifier.width(14.dp))
            Text(
                label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = when (status) {
                    StepStatus.SUCCESS -> MaterialTheme.colorScheme.onSurface
                    StepStatus.ERROR   -> MaterialTheme.colorScheme.error
                    else               -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            when (status) {
                StepStatus.IDLE -> Icon(
                    Icons.Outlined.RadioButtonUnchecked,
                    null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.outline
                )
                StepStatus.LOADING -> CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                StepStatus.SUCCESS -> Icon(
                    Icons.Filled.CheckCircle,
                    null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                StepStatus.ERROR -> Icon(
                    Icons.Filled.Error,
                    null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }

        if (progress != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 36.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primaryContainer
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(48.dp))
        }
    }
}
