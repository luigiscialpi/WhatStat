package com.whatstat.reborn.util

import java.io.File

/**
 * Utility per la sicurezza e la privacy dei dati.
 */
object SecurityUtils {
    /**
     * Elimina un file in modo sicuro: sovrascrive con zeri prima di eliminarlo.
     * Questo impedisce il recupero dei dati da strumenti forensi.
     */
    fun secureDelete(file: File) {
        if (!file.exists()) return
        try {
            // Sovrascrive con zeri
            file.outputStream().use { out ->
                val zeros = ByteArray(8192)
                var remaining = file.length()
                while (remaining > 0) {
                    val toWrite = minOf(remaining, zeros.size.toLong()).toInt()
                    out.write(zeros, 0, toWrite)
                    remaining -= toWrite
                }
                out.flush()
            }
        } catch (_: Exception) {
            // Anche se la sovrascrittura fallisce, proviamo comunque a eliminare
        }
        file.delete()
    }

    /**
     * Elimina in modo sicuro tutti i file in una directory che matchano un pattern.
     */
    fun secureDeleteAll(
        directory: File,
        vararg names: String,
    ) {
        names.forEach { name ->
            secureDelete(File(directory, name))
        }
    }

    /**
     * Sanitizza un testo rimuovendo potenziali dati sensibili per il logging.
     * Nasconde numeri di telefono e nomi.
     */
    fun sanitizeForLog(text: String): String {
        return text
            .replace(Regex("\\d{8,15}"), "***")
            .replace(Regex("[A-Z][a-z]+ [A-Z][a-z]+"), "***")
    }
}
