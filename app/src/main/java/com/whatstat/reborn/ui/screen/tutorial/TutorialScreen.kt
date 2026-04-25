package com.whatstat.reborn.ui.screen.tutorial

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch

@Composable
fun TutorialScreen(
    viewModel: TutorialViewModel = hiltViewModel(),
    onTutorialComplete: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { TutorialData.pages.size })
    val coroutineScope = rememberCoroutineScope()
    val state by viewModel.state.collectAsState()
    
    LaunchedEffect(state.isCompleted) {
        if (state.isCompleted) {
            onTutorialComplete()
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val tutorialPage = TutorialData.pages[page]
            TutorialPage(
                title = tutorialPage.title,
                description = tutorialPage.description,
                step = page + 1,
                totalSteps = TutorialData.pages.size
            )
        }
        
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        ) {
            IconButton(
                onClick = {
                    if (pagerState.currentPage == TutorialData.pages.size - 1) {
                        viewModel.setTutorialCompleted()
                    } else {
                        viewModel.skipTutorial()
                    }
                }
            ) {
                Icon(
                    imageVector = if (pagerState.currentPage == TutorialData.pages.size - 1) 
                        Icons.Default.Done else Icons.Default.Close,
                    contentDescription = if (pagerState.currentPage == TutorialData.pages.size - 1) 
                        "Finish" else "Skip",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(TutorialData.pages.size) { index ->
                    val color = if (index == pagerState.currentPage) 
                        MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.5f)
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .padding(2.dp)
                            .background(color, MaterialTheme.shapes.small)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (pagerState.currentPage > 0) {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage - 1)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("Indietro")
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }
                
                Button(
                    onClick = {
                        if (pagerState.currentPage == TutorialData.pages.size - 1) {
                            viewModel.setTutorialCompleted()
                        } else {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        }
                    }
                ) {
                    Text(if (pagerState.currentPage == TutorialData.pages.size - 1) "Fine" else "Avanti")
                    if (pagerState.currentPage == TutorialData.pages.size - 1) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Default.Done,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

object TutorialData {
    val pages = listOf(
        TutorialPageData(
            title = "Benvenuto in WhatStat Reborn",
            description = "Questa app ti permette di analizzare le tue chat WhatsApp direttamente sul dispositivo, senza inviare dati a server esterni. Funziona solo su dispositivi rootati con Magisk e richiede che il backup end-to-end di WhatsApp sia disattivato."
        ),
        TutorialPageData(
            title = "Dashboard - Panoramica Globale",
            description = "La schermata principale mostra statistiche generali come il numero totale di messaggi, chat, contatti e media. Include anche grafici di attività oraria, settimanale, mensile e annuale, oltre all'analisi delle parole e emoji più usate (che devi avviare manualmente)."
        ),
        TutorialPageData(
            title = "Lista Chat",
            description = "Visualizza tutte le tue conversazioni ordinate per numero di messaggi. Per ogni chat vedi l'avatar, il nome del contatto, il numero di telefono e il conteggio dei messaggi. Tocca una chat per vedere i dettagli."
        ),
        TutorialPageData(
            title = "Dettaglio Chat",
            description = "Per ogni chat puoi vedere statistiche dettagliate come messaggi inviati/ricevuti, chi scrive di più, chi inizia le conversazioni, grafici di attività, evoluzione mensile e analisi testuale. Sono disponibili anche il registro chiamate e le statistiche dei media e link condivisi."
        ),
        TutorialPageData(
            title = "Media Gallery",
            description = "Esplora tutti i foto e video condivisi nelle tue chat con una griglia visuale. Puoi ordinare per data e visualizzare i media a schermo intero con zoom e altre funzionalità."
        ),
        TutorialPageData(
            title = "Radar Parole",
            description = "Cerca occorrenze di parole o frasi specifiche in una chat e vedi un breakdown per mittente. Utile per trovare quando hai parlato di argomenti specifici."
        ),
        TutorialPageData(
            title = "Privacy e Sicurezza",
            description = "Tutti i dati rimangono sul tuo dispositivo. L'app non ha permessi di rete e non invia alcun dato a server esterni. I file estratti vengono salvati nella directory privata dell'app, inaccessibile ad altre applicazioni."
        )
    )
}

data class TutorialPageData(
    val title: String,
    val description: String
)