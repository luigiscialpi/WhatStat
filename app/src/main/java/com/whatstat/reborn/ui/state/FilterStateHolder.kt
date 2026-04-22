package com.whatstat.reborn.ui.state

import com.whatstat.reborn.data.db.model.DateRange
import com.whatstat.reborn.data.db.model.TimeRange
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Singleton che mantiene il filtro temporale attivo tra le schermate.
 *
 * Due contesti indipendenti:
 *  - **Global**: condiviso tra Dashboard, CallsStats (globale), MediaStats (globale), WordSearch (globale).
 *  - **Chat**: condiviso tra ChatDetail, CallsStats (per-chat), MediaStats (per-chat), WordSearch (per-chat).
 *
 * La separazione evita che il filtro di una chat "inquini" la Dashboard globale
 * quando l'utente naviga avanti e indietro.
 */
@Singleton
class FilterStateHolder @Inject constructor() {

    // ── Contesto globale (Dashboard e schermate senza chatId) ─────────────────
    var globalTimeRange: TimeRange = TimeRange.ALL_TIME
        private set
    var globalCustomRange: DateRange? = null
        private set

    fun setGlobal(range: TimeRange, custom: DateRange? = null) {
        globalTimeRange = range
        globalCustomRange = if (range == TimeRange.CUSTOM) custom else null
    }

    // ── Contesto chat (ChatDetail e schermate con chatId) ─────────────────────
    var chatTimeRange: TimeRange = TimeRange.ALL_TIME
        private set
    var chatCustomRange: DateRange? = null
        private set

    fun setChat(range: TimeRange, custom: DateRange? = null) {
        chatTimeRange = range
        chatCustomRange = if (range == TimeRange.CUSTOM) custom else null
    }
}
