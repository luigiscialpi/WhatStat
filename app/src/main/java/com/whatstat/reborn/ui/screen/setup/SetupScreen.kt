package com.whatstat.reborn.ui.screen.setup

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.whatstat.reborn.data.root.FileExtractor

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun SetupScreen(
    viewModel: SetupViewModel = hiltViewModel(),
    onSetupComplete: () -> Unit
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.isComplete) {
        if (state.isComplete) onSetupComplete()
    }

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

            Spacer(Modifier.height(48.dp))

            // ── Step indicators ──────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(Modifier.padding(20.dp)) {
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
                        icon = Icons.Outlined.Key
                    )
                    SetupStep(
                        label = "Lettura contatti",
                        status = state.contactsStatus,
                        icon = Icons.Outlined.Contacts
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // ── Error card & Manual Mode ───────────────────────────
            AnimatedVisibility(
                visible = state.error != null,
                enter = fadeIn() + slideInVertically()
            ) {
                Column {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            state.error ?: "",
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    if (!state.isManualModeVisible) {
                        Button(
                            onClick = { viewModel.showManualMode() },
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
                        viewModel.handleManualFileSelected(uri, currentSelectionType)
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

            Spacer(Modifier.height(24.dp))

            // ── Action button ────────────────────────────────────
            Button(
                onClick = { 
                    if (state.isManualModeVisible) {
                        viewModel.startManualSetup()
                    } else {
                        viewModel.startSetup() 
                    }
                },
                enabled = !state.isLoading && (!state.isManualModeVisible || (state.hasManualKey && state.hasManualCrypt14)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.5.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(12.dp))
                }
                Text(
                    if (state.isLoading) "Elaborazione…" 
                    else if (state.isManualModeVisible) "Decifra Database"
                    else "Inizia analisi",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

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
    icon: ImageVector
) {
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
}
