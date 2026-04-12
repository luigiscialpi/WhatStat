package com.whatstat.reborn.domain.model

/**
 * Sealed class per gli errori dell'app.
 * Ogni variante contiene un messaggio user-friendly in italiano.
 */
sealed class AppError(val userMessage: String) {

    object RootNotAvailable : AppError(
        "Root non disponibile. Assicurati che Magisk sia installato correttamente."
    )

    object RootNotGranted : AppError(
        "Permessi root negati. Apri Magisk e concedi i permessi a WhatStat Reborn."
    )

    object KeyFileNotFound : AppError(
        "File chiave non trovato. WhatsApp è installato sul dispositivo?"
    )

    object Crypt14NotFound : AppError(
        "Database cifrato non trovato. Assicurati che il backup locale " +
        "sia attivo in WhatsApp (Impostazioni → Chat → Backup delle chat)."
    )

    object Crypt15Detected : AppError(
        "Rilevato backup crypt15 (crittografia end-to-end). " +
        "Disattiva il backup crittografato E2E nelle impostazioni di WhatsApp " +
        "e rifai il backup."
    )

    data class DecryptionFailed(val reason: String) : AppError(
        "Decifratura fallita: $reason"
    )

    data class DecryptionError(val reason: String) : AppError(
        "Errore decifratura: $reason"
    )

    data class DatabaseError(val reason: String) : AppError(
        "Errore nel database: $reason"
    )

    data class ExtractionError(val reason: String) : AppError(
        "Errore durante l'estrazione dei file: $reason"
    )

    data class UnknownError(val throwable: Throwable) : AppError(
        "Errore imprevisto: ${throwable.localizedMessage ?: throwable.javaClass.simpleName}"
    )
}
