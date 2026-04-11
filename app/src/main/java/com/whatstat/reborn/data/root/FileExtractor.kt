package com.whatstat.reborn.data.root

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Estrae i file protetti di WhatsApp nella directory privata dell'app.
 * Richiede accesso root per copiare file da /data/data/com.whatsapp/.
 */
@Singleton
class FileExtractor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val rootManager: RootManager
) {
    private val workDir: File = context.filesDir

    companion object {
        // Percorsi dei file WhatsApp sul dispositivo rootato
        const val KEY_PATH     = "/data/data/com.whatsapp/files/key"
        const val CRYPT14_PATH = "/sdcard/WhatsApp/Databases/msgstore.db.crypt14"
        const val CRYPT15_PATH = "/sdcard/WhatsApp/Databases/msgstore.db.crypt15"
        const val WA_DB_PATH   = "/data/data/com.whatsapp/databases/wa.db"

        // Percorsi alternativi (Android 11+ con scoped storage)
        const val CRYPT14_PATH_ALT = "/sdcard/Android/media/com.whatsapp/WhatsApp/Databases/msgstore.db.crypt14"
        const val CRYPT15_PATH_ALT = "/sdcard/Android/media/com.whatsapp/WhatsApp/Databases/msgstore.db.crypt15"

        // Nomi dei file nella directory privata
        const val KEY_DEST     = "whatsapp.key"
        const val CRYPT14_DEST = "msgstore.db.crypt14"
        const val WA_DB_DEST   = "wa.db"
    }

    /**
     * Estrae tutti i file necessari nella directory privata dell'app.
     * Cerca automaticamente nei percorsi standard e alternativi.
     */
    suspend fun extractAll(): Result<ExtractedFiles> = withContext(Dispatchers.IO) {
        runCatching {
            // 1. Copia la chiave AES
            val keyDest = File(workDir, KEY_DEST)
            copyWithRoot(KEY_PATH, keyDest)

            // 2. Cerca e copia il database cifrato (supporta entrambi i percorsi)
            val crypt14Dest = File(workDir, CRYPT14_DEST)
            val crypt14Source = findCryptFile()
            copyWithRoot(crypt14Source, crypt14Dest)

            // 3. Copia il database contatti
            val waDbDest = File(workDir, WA_DB_DEST)
            copyWithRoot(WA_DB_PATH, waDbDest)

            ExtractedFiles(
                keyFile     = keyDest,
                crypt14File = crypt14Dest,
                waDbFile    = waDbDest
            )
        }
    }

    /**
     * Cerca il file di backup crypt14 nei percorsi noti.
     * Supporta sia il percorso legacy che quello di Android 11+.
     *
     * @throws IOException se nessun file crypt14 viene trovato
     * @throws CryptVersionException se viene trovato solo un crypt15
     */
    private suspend fun findCryptFile(): String {
        // Prova percorso standard
        if (rootManager.fileExists(CRYPT14_PATH)) return CRYPT14_PATH
        // Prova percorso Android 11+
        if (rootManager.fileExists(CRYPT14_PATH_ALT)) return CRYPT14_PATH_ALT
        // Controlla se è crypt15 (backup E2E attivo)
        if (rootManager.fileExists(CRYPT15_PATH) || rootManager.fileExists(CRYPT15_PATH_ALT)) {
            throw CryptVersionException(
                "Rilevato backup crypt15 (E2E). Disattiva il backup crittografato " +
                "end-to-end nelle impostazioni di WhatsApp per usare WhatStat Reborn."
            )
        }
        throw IOException(
            "Database cifrato non trovato. Assicurati che il backup locale " +
            "sia attivo in WhatsApp (Impostazioni → Chat → Backup delle chat)."
        )
    }

    private suspend fun copyWithRoot(sourcePath: String, dest: File) {
        dest.parentFile?.mkdirs()
        // Usa cat per evitare problemi con i contesti SELinux
        val result = rootManager.execute("cat '$sourcePath' > '${dest.absolutePath}'")
        if (!result.success) {
            // Include l'output perché STDERR potrebbe essere rediretto su STDOUT
            val errorMsg = (result.error + result.output).joinToString("\n").trim()
            throw IOException(
                "Impossibile copiare $sourcePath: $errorMsg"
            )
        }
        // Rende il file leggibile dall'app senza root
        rootManager.execute("chmod 644 '${dest.absolutePath}'")
        // Imposta owner all'UID dell'app
        val appUid = android.os.Process.myUid()
        rootManager.execute("chown $appUid:$appUid '${dest.absolutePath}'")
    }

    /**
     * Verifica la versione del formato di cifratura leggendo l'header del file.
     */
    suspend fun detectCryptVersion(file: File): Int = withContext(Dispatchers.IO) {
        if (!file.exists() || file.length() < 15) return@withContext -1
        file.inputStream().use { stream ->
            val header = ByteArray(15)
            stream.read(header)
            return@withContext when {
                header.contains(14.toByte()) -> 14
                header.contains(15.toByte()) -> 15
                else -> -1
            }
        }
    }

    /**
     * Elimina tutti i file estratti dalla directory privata.
     */
    suspend fun cleanup() = withContext(Dispatchers.IO) {
        listOf(KEY_DEST, CRYPT14_DEST, WA_DB_DEST, "msgstore.db").forEach { name ->
            File(workDir, name).let { file ->
                if (file.exists()) file.delete()
            }
        }
    }

    /**
     * Copia un file selezionato manualmente (tramite content uri) nello storage privato.
     */
    suspend fun copyFromUri(uri: Uri, destFileName: String): File = withContext(Dispatchers.IO) {
        val destFile = File(workDir, destFileName)
        context.contentResolver.openInputStream(uri)?.use { input ->
            destFile.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: throw IOException("Impossibile leggere il file selezionato: $uri")
        destFile
    }
}

data class ExtractedFiles(
    val keyFile: File,
    val crypt14File: File,
    val waDbFile: File
)

class CryptVersionException(message: String) : Exception(message)
