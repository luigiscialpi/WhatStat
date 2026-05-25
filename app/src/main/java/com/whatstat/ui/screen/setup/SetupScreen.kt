package com.whatstat.ui.screen.setup

import android.Manifest
import android.content.Context
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import com.whatstat.R
import com.whatstat.data.root.FileExtractor
import com.whatstat.ui.component.AnimatedInfoButton
import com.whatstat.ui.component.DoubleBackToExitHandler
import com.whatstat.ui.component.LocalDrawerState
import com.whatstat.ui.component.ads.BottomBannerAd
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun setupScreen(
    viewModel: SetupViewModel = hiltViewModel(),
    liteViewModel: LiteSetupViewModel = hiltViewModel(),
    autoNavigate: Boolean = true,
    incomingZipUri: String? = null,
    onUriConsumed: () -> Unit = {},
    onSetupComplete: () -> Unit,
    onNavigateToDashboard: () -> Unit,
    showBannerAd: Boolean = false,
) {
    val state by viewModel.state.collectAsState()
    val liteState by liteViewModel.state.collectAsState()

    var awaitLiteImport by remember { mutableStateOf(false) }
    // Flag one-shot: una volta navigato, non navigare più.
    var hasNavigated by remember { mutableStateOf(false) }
    // Memorizza l'URI che abbiamo GIÀ processato per non ri-processarlo al ritorno.
    var processedUri by remember { mutableStateOf<String?>(null) }
    // Controlla se auto-navigare quando state.isComplete (disabilitato al ritorno da Dashboard)
    var shouldAutoNavigate by remember { mutableStateOf(autoNavigate) }

    // Se arriva un URI dall'intent (cold start / condivisione da WhatsApp),
    // avviamo l'import direttamente dal tab Lite senza navigare a LiteSetupScreen.
    LaunchedEffect(incomingZipUri) {
        if (incomingZipUri != null && incomingZipUri != processedUri) {
            processedUri = incomingZipUri
            awaitLiteImport = true
            hasNavigated = false
            shouldAutoNavigate = true
            liteViewModel.onZipSelected(Uri.parse(incomingZipUri))
            onUriConsumed()
        }
    }

    // Navigazione automatica verso Main:
    LaunchedEffect(state.isComplete, liteState.isLoading, liteState.showSenderSelection, awaitLiteImport, state.hasNewDbAvailable) {
        if (hasNavigated) return@LaunchedEffect
        if (awaitLiteImport) return@LaunchedEffect
        if (!shouldAutoNavigate) {
            return@LaunchedEffect
        }
        if (state.isComplete && !liteState.isLoading && !liteState.showSenderSelection && !state.hasNewDbAvailable) {
            hasNavigated = true
            onSetupComplete()
        }
    }

    // Quando un import avviato da URI è completato, naviga verso Main.
    LaunchedEffect(liteState.isComplete, awaitLiteImport) {
        if (hasNavigated) return@LaunchedEffect
        if (awaitLiteImport && liteState.isComplete) {
            hasNavigated = true
            awaitLiteImport = false
            liteViewModel.consumeImportComplete()
            onSetupComplete()
        }
    }

    val context = LocalContext.current
    val permissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
        ) { isGranted ->
            if (!isGranted) {
                Toast.makeText(
                    context,
                    "Il permesso notifiche è necessario per seguire il progresso in background",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }

    fun checkAndStart(isManual: Boolean) {
        shouldAutoNavigate = true
        hasNavigated = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (isManual) viewModel.startManualSetup() else viewModel.startSetup()
    }

    // 0 = Lite (No Root), 1 = Root
    // Tab predefinita: se c'è un URI in ingresso → Lite; altrimenti segui il modo corrente
    val initialTab =
        remember {
            when {
                incomingZipUri != null -> 0 // Share da WhatsApp → tab Lite
                state.isRootReady -> 1 // DB Root preservato → tab Root
                else -> 0 // Default → tab Lite
            }
        }
    var selectedTab by remember { mutableIntStateOf(initialTab) }
    // Aggiorna la tab predefinita quando il check asincrono completa (cold start)
    var hasAutoSwitched by remember { mutableStateOf(false) }
    LaunchedEffect(state.isRootReady) {
        if (incomingZipUri == null && state.isRootReady && !hasAutoSwitched && selectedTab == 0) {
            selectedTab = 1
            hasAutoSwitched = true
        }
    }
    val zipLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent(),
        ) { uri ->
            uri?.let {
                awaitLiteImport = true
                hasNavigated = false
                shouldAutoNavigate = true
                liteViewModel.onZipSelected(it)
            }
        }

    DoubleBackToExitHandler()
    BackHandler(enabled = liteState.isLoading || liteState.showSenderSelection) {}

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors =
                            listOf(
                                MaterialTheme.colorScheme.background,
                                MaterialTheme.colorScheme.surface,
                                MaterialTheme.colorScheme.background,
                            ),
                    ),
                ),
    ) {
        val drawerState = LocalDrawerState.current
        val scope = rememberCoroutineScope()
        val snackbarHostState = remember { SnackbarHostState() }

        IconButton(
            onClick = { scope.launch { drawerState.open() } },
            modifier =
                Modifier
                    .padding(8.dp)
                    .statusBarsPadding()
                    .zIndex(1f),
        ) {
            Icon(
                imageVector = Icons.Default.Menu,
                contentDescription = "Menu",
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }

        val scrollState = rememberScrollState()
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // ── Logo / Icona ─────────────────────────────────────
            val infiniteTransition = rememberInfiniteTransition(label = "pulse")
            val pulseAlpha by infiniteTransition.animateFloat(
                initialValue = 0.6f,
                targetValue = 1f,
                animationSpec =
                    infiniteRepeatable(
                        animation = tween(1500),
                        repeatMode = RepeatMode.Reverse,
                    ),
                label = "pulseAlpha",
            )

            var easterEggTaps by remember { mutableIntStateOf(0) }
            var easterEggLastTapMs by remember { mutableStateOf(0L) }
            var easterEggLastWasIcon by remember { mutableStateOf<Boolean?>(null) }
            Box(
                modifier =
                    Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            val now = System.currentTimeMillis()
                            val continues =
                                easterEggLastWasIcon != true &&
                                    (easterEggLastWasIcon == null || now - easterEggLastTapMs < 500L)
                            easterEggTaps = if (continues) easterEggTaps + 1 else 1
                            easterEggLastWasIcon = true
                            easterEggLastTapMs = now
                            if (easterEggTaps >= 10) {
                                easterEggTaps = 0
                                easterEggLastWasIcon = null
                                viewModel.unlockProEasterEgg()
                                val msg = context.getString(R.string.easter_egg_pro_unlocked)
                                scope.launch { snackbarHostState.showSnackbar(msg) }
                            }
                        },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = null,
                    modifier =
                        Modifier
                            .size(48.dp)
                            .alpha(pulseAlpha),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(Modifier.height(28.dp))

            Text(
                stringResource(R.string.setup_title),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier =
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        val now = System.currentTimeMillis()
                        val continues =
                            easterEggLastWasIcon != false &&
                                (easterEggLastWasIcon == null || now - easterEggLastTapMs < 500L)
                        easterEggTaps = if (continues) easterEggTaps + 1 else 1
                        easterEggLastWasIcon = false
                        easterEggLastTapMs = now
                        if (easterEggTaps >= 10) {
                            easterEggTaps = 0
                            easterEggLastWasIcon = null
                            viewModel.unlockProEasterEgg()
                            val msg = context.getString(R.string.easter_egg_pro_unlocked)
                            scope.launch { snackbarHostState.showSnackbar(msg) }
                        }
                    },
            )

            Spacer(Modifier.height(8.dp))

            Text(
                stringResource(R.string.setup_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight,
            )

            Spacer(Modifier.height(32.dp))

            // ── Secondary Tabs ───────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    ),
                shape = RoundedCornerShape(16.dp),
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
                            text = { Text(stringResource(R.string.tab_lite)) },
                            icon = {
                                Icon(
                                    imageVector = Icons.Outlined.PhoneAndroid,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = {
                                if (!liteState.isLoading && !liteState.showSenderSelection) {
                                    selectedTab = 1
                                }
                            },
                            text = { Text(stringResource(R.string.tab_root)) },
                            icon = {
                                Icon(
                                    imageVector = Icons.Outlined.Terminal,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                        )
                    }

                    // ── Tab 0: Lite ──────────────────────────────
                    AnimatedVisibility(visible = selectedTab == 0) {
                        val liteContext = LocalContext.current
                        liteTabContent(
                            hasLiteDb = state.hasLiteDb,
                            isLoading = liteState.isLoading,
                            progressPercent = liteState.progressPercent,
                            progressLabel = liteState.progressLabel,
                            error = liteState.error,
                            onNavigateToDashboard = {
                                viewModel.activateLiteMode()
                                onNavigateToDashboard()
                            },
                            onOpenWhatsApp = {
                                if (!openInstalledWhatsApp(liteContext)) {
                                    Toast.makeText(
                                        liteContext,
                                        liteContext.getString(R.string.lite_whatsapp_not_installed),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            },
                            onImportZip = { zipLauncher.launch("application/zip") },
                        )
                    }

                    // ── Tab 1: Root ──────────────────────────────
                    AnimatedVisibility(visible = selectedTab == 1) {
                        rootTabContent(
                            state = state,
                            onCheckAndStart = { checkAndStart(false) },
                            onCheckAndStartManual = { checkAndStart(true) },
                            onShowManualMode = { viewModel.showManualMode() },
                            onCancelSetup = { viewModel.cancelSetup() },
                            onResumeRoot = {
                                viewModel.resumeRootSession()
                                onNavigateToDashboard()
                            },
                            onClearRoot = { viewModel.clearRootSession() },
                            onManualFileSelected = { uri, type ->
                                viewModel.handleManualFileSelected(uri, type)
                            },
                        )
                    }
                }
            }
        }

        // ── Overlay import da condivisione WhatsApp ─────────────
        if (awaitLiteImport && (liteState.isLoading || liteState.showSenderSelection)) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.97f))
                        .clickable(enabled = false) {},
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.FolderZip,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(20.dp))
                    Text(
                        stringResource(R.string.importing_overlay),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(8.dp))
                    if (liteState.progressLabel.isNotEmpty()) {
                        Text(
                            liteState.progressLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                    LinearProgressIndicator(
                        progress = { liteState.progressPercent },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${(liteState.progressPercent * 100).toInt()}%",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        // ── Overlay cache check ──────────────────────────────────
        if (state.isCheckingCache && !awaitLiteImport) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.95f))
                        .clickable(enabled = false) {},
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(56.dp),
                        strokeWidth = 4.dp,
                    )
                    Spacer(Modifier.height(24.dp))
                    Text(
                        stringResource(R.string.checking_data_overlay),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = if (showBannerAd) 56.dp else 0.dp),
            snackbar = { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                )
            },
        )
        if (showBannerAd) {
            BottomBannerAd(modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
        }
    }

    if (liteState.showSenderSelection) {
        senderSelectionDialog(
            senders = liteState.identifiedSenders,
            onSenderSelected = { liteViewModel.onSenderSelected(it) },
        )
    }
}

@Composable
fun senderSelectionDialog(
    senders: List<String>,
    onSenderSelected: (String) -> Unit,
) {
    Dialog(onDismissRequest = { /* Obbligatorio selezionare */ }) {
        Card(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(Modifier.padding(24.dp)) {
                Text(
                    stringResource(R.string.who_are_you_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.who_are_you_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))

                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(senders) { sender ->
                        Text(
                            text = sender,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onSenderSelected(sender) }
                                    .padding(vertical = 12.dp, horizontal = 8.dp),
                            style = MaterialTheme.typography.bodyLarge,
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
private fun liteTabContent(
    hasLiteDb: Boolean,
    isLoading: Boolean,
    progressPercent: Float,
    progressLabel: String,
    error: String?,
    onNavigateToDashboard: () -> Unit,
    onOpenWhatsApp: () -> Unit,
    onImportZip: () -> Unit,
) {
    // Stato locale per mostrare/nascondere il dialogo guida.
    var showGuide by remember { mutableStateOf(false) }

    if (showGuide) {
        liteGuideDialog(onDismiss = { showGuide = false })
    }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ── Riga hint + icona informativa ────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text =
                    if (hasLiteDb) {
                        stringResource(R.string.lite_hint_import_found)
                    } else {
                        stringResource(R.string.lite_hint_no_import)
                    },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            AnimatedInfoButton(
                onClick = { showGuide = true },
                modifier = Modifier.size(32.dp),
                contentDescription = stringResource(R.string.lite_guide_cd),
                iconSize = 20.dp,
                tint = MaterialTheme.colorScheme.primary,
                glowColor = MaterialTheme.colorScheme.primary,
            )
        }

        Spacer(Modifier.height(4.dp))

        // ── Pulsante 1: Dashboard (abilitato solo se db lite esiste) ──
        Button(
            onClick = onNavigateToDashboard,
            enabled = hasLiteDb,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
        ) {
            Icon(
                imageVector = Icons.Filled.Dashboard,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.go_to_dashboard), style = MaterialTheme.typography.labelLarge)
        }

        if (isLoading) {
            // ── Indicatore di progresso durante import ───────────
            val progressInt = (progressPercent * 100).toInt()
            Text(
                text = if (progressLabel.isNotEmpty()) progressLabel else stringResource(R.string.processing),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LinearProgressIndicator(
                progress = { progressPercent },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "$progressInt%",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            // ── Pulsante 2: Condividi da WhatsApp ────────────────
            OutlinedButton(
                onClick = onOpenWhatsApp,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                border =
                    BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f),
                    ),
                colors =
                    ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.secondary,
                    ),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Share,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(R.string.lite_open_whatsapp_button),
                    style = MaterialTheme.typography.labelLarge,
                )
            }

            // ── Pulsante 3: Importa ZIP da file ──────────────────
            OutlinedButton(
                onClick = onImportZip,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                border =
                    BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    ),
            ) {
                Icon(
                    imageVector = Icons.Outlined.FolderZip,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(R.string.import_zip_button),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        if (!error.isNullOrBlank()) {
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/**
 * Dialog modale che guida l'utente nell'esportazione di una chat da WhatsApp.
 * Mostra 4 step sequenziali con testo esplicativo.
 */
@Composable
private fun liteGuideDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Outlined.Share,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = {
            Text(
                stringResource(R.string.lite_guide_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                liteGuideStep(text = boldTagsToAnnotated(stringResource(R.string.lite_guide_step1)))
                liteGuideStep(text = boldTagsToAnnotated(stringResource(R.string.lite_guide_step2)))
                liteGuideStep(text = boldTagsToAnnotated(stringResource(R.string.lite_guide_step3)))
                liteGuideStep(text = boldTagsToAnnotated(stringResource(R.string.lite_guide_step4)))
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.ho_capito))
            }
        },
    )
}

/**
 * Parses a plain string that may contain <b>...</b> tags and returns an
 * AnnotatedString where the tagged spans have FontWeight.Bold applied.
 * Only <b> / </b> are supported; all other HTML is left as-is.
 */
private fun boldTagsToAnnotated(raw: String): AnnotatedString =
    buildAnnotatedString {
        val openTag = "<b>"
        val closeTag = "</b>"
        var cursor = 0
        while (cursor < raw.length) {
            val openIdx = raw.indexOf(openTag, cursor, ignoreCase = true)
            if (openIdx == -1) {
                append(raw.substring(cursor))
                break
            }
            append(raw.substring(cursor, openIdx))
            val closeIdx = raw.indexOf(closeTag, openIdx + openTag.length, ignoreCase = true)
            if (closeIdx == -1) {
                // malformed: no closing tag; emit the rest verbatim
                append(raw.substring(openIdx))
                break
            }
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                append(raw.substring(openIdx + openTag.length, closeIdx))
            }
            cursor = closeIdx + closeTag.length
        }
    }

private val WHATSAPP_PACKAGE_NAMES =
    listOf(
        "com.whatsapp",
        "com.whatsapp.w4b",
    )

private fun openInstalledWhatsApp(context: Context): Boolean {
    val packageManager = context.packageManager
    val launchIntent =
        WHATSAPP_PACKAGE_NAMES.firstNotNullOfOrNull { packageName ->
            packageManager.getLaunchIntentForPackage(packageName)
        }
    if (launchIntent != null) {
        context.startActivity(launchIntent)
        return true
    }
    return false
}

/**
 * Riga singola di uno step nella guida Lite.
 * Usa un bullet visivo colorato per distinguere i passi.
 * Accetta AnnotatedString per supportare il grassetto nei tag <b>.
 */
@Composable
private fun liteGuideStep(text: AnnotatedString) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .padding(top = 5.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ── Tab Root ──────────────────────────────────────────────────────────────────

@Composable
private fun rootTabContent(
    state: SetupState,
    onCheckAndStart: () -> Unit,
    onCheckAndStartManual: () -> Unit,
    onShowManualMode: () -> Unit,
    onCancelSetup: () -> Unit,
    onResumeRoot: () -> Unit,
    onClearRoot: () -> Unit,
    onManualFileSelected: (Uri, String) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ── Step indicators ──────────────────────────────────────
        // Show steps only when database is not ready or setup is active
        if (!state.isRootReady || state.isLoading) {
            SetupStep(
                label = stringResource(R.string.root_step_access),
                status = state.rootStatus,
                icon = Icons.Outlined.AdminPanelSettings,
            )
            SetupStep(
                label = stringResource(R.string.root_step_extraction),
                status = state.extractionStatus,
                icon = Icons.Outlined.FolderOpen,
            )
            SetupStep(
                label = stringResource(R.string.root_step_decryption),
                status = state.decryptionStatus,
                icon = Icons.Outlined.Key,
                progress = if (state.decryptionStatus == StepStatus.LOADING) state.decryptionProgress else null,
            )
            SetupStep(
                label = stringResource(R.string.root_step_contacts),
                status = state.contactsStatus,
                icon = Icons.Outlined.Contacts,
            )
        }

        Spacer(Modifier.height(8.dp))

        // ── Dashboard & Clear Session ──────────────────────────
        // Hint contestuale per Root (simile a Lite)
        if (!state.isLoading) {
            if (state.hasNewDbAvailable) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            stringResource(R.string.new_db_available_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.new_db_available_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = onCheckAndStart,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.new_db_update_button))
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            } else {
                Text(
                    text =
                        if (state.isRootReady) {
                            stringResource(R.string.root_hint_session_found)
                        } else {
                            stringResource(R.string.root_hint_no_session)
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onResumeRoot,
                    enabled = state.isRootReady,
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                ) {
                    Icon(Icons.Filled.Dashboard, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.go_to_dashboard), style = MaterialTheme.typography.labelLarge)
                }

                if (state.isRootReady) {
                    OutlinedButton(
                        onClick = onClearRoot,
                        modifier = Modifier.size(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(0.dp),
                        colors =
                            ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error,
                            ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                    ) {
                        Icon(Icons.Default.Delete, null, Modifier.size(20.dp))
                    }
                }
            }
        }

        // ── Error card & Manual Mode ─────────────────────────────
        AnimatedVisibility(
            visible = state.error != null,
            enter = fadeIn() + slideInVertically(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors =
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                        ),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    SelectionContainer {
                        Text(
                            state.error ?: "",
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                if (!state.isManualModeVisible) {
                    Button(
                        onClick = onShowManualMode,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.manual_mode_button))
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = state.isManualModeVisible,
            enter = fadeIn() + slideInVertically(),
        ) {
            var currentSelectionType by remember { mutableStateOf("") }
            val launcher =
                rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
                    if (uri != null && currentSelectionType.isNotEmpty()) {
                        onManualFileSelected(uri, currentSelectionType)
                    }
                }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.manual_mode_title), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(16.dp))

                    manualFileButton(
                        label = stringResource(R.string.manual_file_key),
                        isLoaded = state.hasManualKey,
                        onClick = {
                            currentSelectionType = FileExtractor.KEY_DEST
                            launcher.launch("*/*")
                        },
                    )
                    manualFileButton(
                        label = stringResource(R.string.manual_file_crypt14),
                        isLoaded = state.hasManualCrypt14,
                        onClick = {
                            currentSelectionType = FileExtractor.CRYPT14_DEST
                            launcher.launch("*/*")
                        },
                    )
                    manualFileButton(
                        label = stringResource(R.string.manual_file_decrypted),
                        isLoaded = state.hasManualDbDecrypted,
                        onClick = {
                            currentSelectionType = FileExtractor.DB_DECRYPTED_DEST
                            launcher.launch("application/octet-stream")
                        },
                    )
                    manualFileButton(
                        label = stringResource(R.string.manual_file_wadb),
                        isLoaded = state.hasManualWaDb,
                        onClick = {
                            currentSelectionType = FileExtractor.WA_DB_DEST
                            launcher.launch("*/*")
                        },
                    )
                }
            }
        }

        // ── Action button ────────────────────────────────────────

        if (state.isLoading) {
            OutlinedButton(
                onClick = onCancelSetup,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors =
                    ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.cancel),
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.cancel), style = MaterialTheme.typography.labelLarge)
            }
        } else {
            Button(
                onClick = {
                    if (state.isManualModeVisible) {
                        onCheckAndStartManual()
                    } else {
                        onCheckAndStart()
                    }
                },
                enabled =
                    !state.isLoading && (
                        !state.isManualModeVisible ||
                            (state.hasManualKey && state.hasManualCrypt14) ||
                            (state.hasManualKey && state.hasManualDbDecrypted)
                    ),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
            ) {
                Text(
                    if (state.isManualModeVisible) "Decifra Database" else "Inizia analisi",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

// ── Componenti condivisi ──────────────────────────────────────────────────────

@Composable
private fun manualFileButton(
    label: String,
    isLoaded: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            onClick = onClick,
            modifier = Modifier.weight(1f),
        ) {
            Text(if (isLoaded) stringResource(R.string.button_replace) else stringResource(R.string.button_select))
        }
        Spacer(Modifier.width(12.dp))
        Text(label, modifier = Modifier.weight(2f), style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.width(8.dp))
        Icon(
            imageVector = if (isLoaded) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (isLoaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        )
    }
}

@Composable
@Suppress("FunctionName")
private fun SetupStep(
    label: String,
    status: StepStatus,
    icon: ImageVector,
    progress: Float? = null,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint =
                    when (status) {
                        StepStatus.SUCCESS -> MaterialTheme.colorScheme.primary
                        StepStatus.ERROR -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
            Spacer(Modifier.width(14.dp))
            Text(
                label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color =
                    when (status) {
                        StepStatus.SUCCESS -> MaterialTheme.colorScheme.onSurface
                        StepStatus.ERROR -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
            when (status) {
                StepStatus.IDLE ->
                    Icon(
                        Icons.Outlined.RadioButtonUnchecked,
                        null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.outline,
                    )
                StepStatus.LOADING ->
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                StepStatus.SUCCESS ->
                    Icon(
                        Icons.Filled.CheckCircle,
                        null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                StepStatus.ERROR ->
                    Icon(
                        Icons.Filled.Error,
                        null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
            }
        }

        if (progress != null) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 36.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier =
                        Modifier
                            .weight(1f)
                            .height(4.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primaryContainer,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(48.dp))
        }
    }
}
