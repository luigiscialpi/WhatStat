package com.whatstat.util

/**
 * Impalcatura per la futura gestione delle funzionalita' Premium/IAP.
 * L'uso di `inline` fa si' che il bytecode del controllo venga sparso in tutti i chiamanti,
 * rendendo inefficace la modifica di un singolo punto da parte di tool come Lucky Patcher.
 */
object FeatureGate {

    // Utilizziamo costanti arbitrarie invece di Boolean (true/false) per
    // nascondere lo stato ai classici search pattern dei patcher.
    @PublishedApi
    internal const val STATE_UNLOCKED = 0x1A2B3C
    @PublishedApi
    internal const val STATE_LOCKED = 0x3C2B1A

    /**
     * Metodo interno per verificare lo stato di una licenza.
     * Attualmente l'app e' gratuita, quindi restituisce sempre UNLOCKED,
     * a meno che non venga rilevato tampering all'APK.
     */
    @PublishedApi
    internal fun getInternalFeatureState(featureId: String): Int {
        // Se IntegrityChecker rileva una firma APK errata (tampering),
        // simuliamo il blocco della feature silenziosamente (Poisoning).
        if (IntegrityChecker.isTampered) {
            return STATE_LOCKED
        }
        
        // TODO: In futuro, inserire qui il check IAP (es. billingManager.isPurchased(featureId))
        return STATE_UNLOCKED
    }

    /**
     * Esegue il [block] se la feature e' sbloccata, altrimenti esegue [fallback].
     * L'inline forza il compilatore a copiare l'intera logica ovunque venga chiamata,
     * costringendo l'attaccante a patchare n posizioni distinte.
     */
    inline fun <T> requireFeature(featureId: String, fallback: () -> T, block: () -> T): T {
        val state = getInternalFeatureState(featureId)
        return if (state == STATE_UNLOCKED) {
            block()
        } else {
            fallback()
        }
    }

    /**
     * Versione semplificata per azioni UI (es. click bottoni) che non fanno nulla se la feature e' bloccata.
     */
    inline fun withFeature(featureId: String, block: () -> Unit) {
        if (getInternalFeatureState(featureId) == STATE_UNLOCKED) {
            block()
        } else {
            // TODO: In futuro, emettere un evento per aprire il paywall
            DebugLogManager.log("Accesso negato alla feature: $featureId")
        }
    }
}
