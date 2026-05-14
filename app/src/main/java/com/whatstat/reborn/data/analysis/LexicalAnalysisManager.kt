package com.whatstat.reborn.data.analysis

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gestore del progresso dell'analisi lessicale root.
 * Permette al [LexicalAnalysisService] di osservare il progresso
 * aggiornato dal [com.whatstat.reborn.ui.screen.dashboard.DashboardViewModel].
 */
@Singleton
class LexicalAnalysisManager
    @Inject
    constructor() {
        private val _progress = MutableStateFlow(-1)

        /** Progresso 0-100 durante l'analisi, -1 = idle. */
        val progress: StateFlow<Int> = _progress.asStateFlow()

        fun updateProgress(value: Int) {
            _progress.value = value.coerceIn(0, 100)
        }

        fun reset() {
            _progress.value = -1
        }
    }
