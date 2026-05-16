package com.whatstat.data.root

import android.content.Context
import android.net.Uri
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.io.SuFile
import com.topjohnwu.superuser.io.SuFileInputStream
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import com.whatstat.util.SecurityUtils
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Estrae i file protetti di WhatsApp nella directory privata dell'app.
 * Richiede accesso root per copiare file da /data/data/com.whatsapp/.
 */
@Singleton
class FileExtractor
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val rootManager: RootManager,
    ) {
        private val workDir: File = context.filesDir

        companion object {
            // Percorsi dei file WhatsApp sul dispositivo rootato
            const val KEY_PATH = "/data/data/com.whatsapp/files/key"
            const val KEY_PATH_ALT = "/data/user/0/com.whatsapp/files/key"
            const val CRYPT14_PATH = "/sdcard/WhatsApp/Databases/msgstore.db.crypt14"
            const val CRYPT15_PATH = "/sdcard/WhatsApp/Databases/msgstore.db.crypt15"
            const val WA_DB_PATH = "/data/data/com.whatsapp/databases/wa.db"
            const val WA_DB_PATH_ALT = "/data/data/com.whatsapp/databases/contacts.db"

            // Percorsi alternativi (Android 11+ con scoped storage)
            const val CRYPT14_PATH_ALT = "/sdcard/Android/media/com.whatsapp/WhatsApp/Databases/msgstore.db.crypt14"
            const val CRYPT15_PATH_ALT = "/sdcard/Android/media/com.whatsapp/WhatsApp/Databases/msgstore.db.crypt15"

            // Nomi dei file nella directory privata
            const val KEY_DEST = "whatsapp.key"
            const val CRYPT14_DEST = "msgstore.db.crypt14"
            const val WA_DB_DEST = "wa.db"

            // Per import manuale (db già decifrato)
            const val DB_DECRYPTED_DEST = "msgstore.db"

            // Percorsi avatar WhatsApp
            const val AVATAR_DIR_PATH = "/data/data/com.whatsapp/files/Avatars"
            const val AVATAR_DIR_PATH_ALT = "/data/user/0/com.whatsapp/files/Avatars"
            const val AVATAR_DIR_DEST = "avatars"
        }

        /**
         * Estrae tutti i file necessari nella directory privata dell'app.
         * Usa su <userId> per accedere ai work profile.
         */
        suspend fun extractAll(): Result<ExtractedFiles> =
            withContext(Dispatchers.IO) {
                runCatching {
                    val keyInfo = findKeyFile()
                    val keyDest = File(workDir, KEY_DEST)
                    copyWithUserId(keyInfo.first, keyInfo.second, keyDest)

                    val crypt14Dest = File(workDir, CRYPT14_DEST)
                    copyWithSuFile(findCryptFile(), crypt14Dest)

                    val waDbInfo = findWaDbFile()
                    val waDbDest = File(workDir, WA_DB_DEST)
                    copyWithUserId(waDbInfo.first, waDbInfo.second, waDbDest)

                    // Estrai avatar directory
                    extractAvatarDir()

                    ExtractedFiles(
                        keyFile = keyDest,
                        crypt14File = crypt14Dest,
                        waDbFile = waDbDest,
                    )
                }
            }

        /**
         * Estrae la directory degli avatar nella directory privata dell'app.
         * Itera su tutti gli userId in /data/user (stessa strategia di findWaDbFile).
         */
        private suspend fun extractAvatarDir(): File? =
            withContext(Dispatchers.IO) {
                val avatarDestDir = File(workDir, AVATAR_DIR_DEST)
                if (avatarDestDir.exists() && (avatarDestDir.listFiles()?.isNotEmpty() == true)) {
                    return@withContext avatarDestDir
                }

                val dataUser = SuFile("/data/user")
                dataUser.listFiles()?.forEach { userDir ->
                    val sourceDir = SuFile(userDir, "com.whatsapp/files/Avatars")
                    android.util.Log.d("WhatStat", "Controllo avatar: ${sourceDir.absolutePath} exists=${sourceDir.exists()}")
                    if (sourceDir.exists() && sourceDir.isDirectory) {
                        avatarDestDir.mkdirs()
                        val files = sourceDir.listFiles()
                        var count = 0
                        files?.forEach { file ->
                            if (file.isFile && file.name.endsWith(".j")) {
                                // I file .j sono JPEG con estensione troncata, li rinominiamo in .jpg
                                val destName = file.name.removeSuffix(".j") + ".jpg"
                                val destFile = File(avatarDestDir, destName)
                                try {
                                    SuFileInputStream.open(file).use { input ->
                                        FileOutputStream(destFile).use { output ->
                                            input.copyTo(output)
                                        }
                                    }
                                    count++
                                } catch (e: Exception) {
                                    android.util.Log.w("WhatStat", "copyTo failed for ${file.name}: ${e.message}")
                                }
                            }
                        }
                        android.util.Log.d("WhatStat", "Avatars extracted: $count file(s) → ${avatarDestDir.absolutePath}")
                        // Copia anche me.jpg (foto profilo dell'utente corrente)
                        val meFile = SuFile(userDir, "com.whatsapp/files/me.jpg")
                        if (meFile.exists() && meFile.isFile) {
                            val meDest = File(avatarDestDir, "me.jpg")
                            try {
                                SuFileInputStream.open(meFile).use { input ->
                                    FileOutputStream(meDest).use { output ->
                                        input.copyTo(output)
                                    }
                                }
                                android.util.Log.d("WhatStat", "me.jpg extracted")
                            } catch (e: Exception) {
                                android.util.Log.w("WhatStat", "me.jpg copy failed: ${e.message}")
                            }
                        }
                        return@withContext avatarDestDir
                    }
                }
                android.util.Log.w("WhatStat", "Avatars directory non trovata in nessun profilo utente")
                null
            }

        /**
         * Ritorna la directory degli avatar.
         */
        fun getAvatarDir(): File = File(workDir, AVATAR_DIR_DEST)

        /**
         * Trova l'userId di WhatsApp e il percorso del file key.
         * @return Pair(userId, path)
         */
        private fun findKeyFile(): Pair<Int, String> {
            // Diagnostica
            android.util.Log.d("FileExtractor", "=== DIAGNOSTICA ===")
            val lsData = Shell.cmd("ls /data/data/").exec()
            android.util.Log.d("FileExtractor", "/data/data: ${lsData.out.take(5)}")

            val pmUsers = Shell.cmd("pm list users").exec()
            android.util.Log.d("FileExtractor", "pm list users: success=${pmUsers.isSuccess}, out=${pmUsers.out}, err=${pmUsers.err}")

            val userIds = mutableListOf<Int>()
            for (line in pmUsers.out) {
                val match = Regex("""\{(\d+):""").find(line)
                if (match != null) {
                    match.groupValues.getOrNull(1)?.toIntOrNull()?.let { if (it != 0) userIds.add(it) }
                }
            }

            android.util.Log.d("FileExtractor", "User IDs trovati: $userIds")

            // Prova direttamente user 999 (XSpace)
            android.util.Log.d("FileExtractor", "Provo user 999...")
            val test999 = Shell.cmd("su 999 -c \"ls /data/user/999/com.whatsapp/files/key\"").exec()
            android.util.Log.d("FileExtractor", "su 999 key: ${test999.out}, err: ${test999.err}")

            if (test999.isSuccess && test999.out.any { it.contains("key") }) {
                return Pair(999, "/data/user/999/com.whatsapp/files/key")
            }

            // Prova ogni userId
            for (userId in userIds) {
                val path = "/data/user/$userId/com.whatsapp/files/key"
                val check = Shell.cmd("su $userId -c \"ls '$path' 2>/dev/null\"").exec()
                android.util.Log.d("FileExtractor", "su $userId -c: ${check.out}")
                if (check.isSuccess && check.out.any { it.contains("key") }) {
                    return Pair(userId, path)
                }
            }

            // Prova user 0
            val keyPath = "/data/user/0/com.whatsapp/files/key"
            if (SuFile(keyPath).exists()) {
                return Pair(0, keyPath)
            }

            throw IOException("File key non trovato in nessun profilo utente. Prova il metodo manuale.")
        }

        /**
         * Trova l'userId di WhatsApp e il percorso del database contatti.
         */
        private fun findWaDbFile(): Pair<Int, String> {
            // Prova SuFile direttamente come ha funzionato per key
            val dataUser = SuFile("/data/user")
            dataUser.listFiles()?.forEach { userDir ->
                val dbDir = SuFile(userDir, "com.whatsapp/databases")
                android.util.Log.d("WhatStat", "Controllo: ${dbDir.absolutePath} exists=${dbDir.exists()}")
                if (dbDir.exists()) {
                    val files = dbDir.listFiles()
                    android.util.Log.d("WhatStat", "Files in databases: ${files?.map { it.name }}")

                    // Prova tutti i file .db che trova
                    files?.forEach { f ->
                        android.util.Log.d("WhatStat", "  → ${f.name} (${f.length()} bytes)")
                    }

                    val waDb = SuFile(dbDir, "wa.db")
                    if (waDb.exists()) return Pair(0, waDb.absolutePath)

                    val contacts = SuFile(dbDir, "contacts.db")
                    if (contacts.exists()) return Pair(0, contacts.absolutePath)
                }
            }

            throw IOException("wa.db non trovato. Controlla logcat tag WhatStat.")
        }

        /**
         * Copia file usando su <userId>.
         */
        private fun copyWithUserId(
            userId: Int,
            sourcePath: String,
            dest: File,
        ) {
            dest.parentFile?.mkdirs()
            try {
                if (userId == 0) {
                    copyWithSuFile(sourcePath, dest)
                    return
                }
                // Usa su <userId> per impersonare l'utente
                val result = Shell.cmd("su $userId -c \"cat '$sourcePath'\" > '${dest.absolutePath}'").exec()
                if (!result.isSuccess || dest.length() == 0L) {
                    throw IOException("Copia fallita per user $userId: ${result.err}")
                }
                dest.setReadable(true, false)
                dest.setWritable(true, false)
            } catch (e: Exception) {
                throw IOException("Impossibile copiare $sourcePath: ${e.message}")
            }
        }

        /**
         * Copia file usando SuFile (accesso standard).
         */
        private fun copyWithSuFile(
            sourcePath: String,
            dest: File,
        ) {
            dest.parentFile?.mkdirs()
            try {
                SuFileInputStream.open(SuFile(sourcePath)).use { input ->
                    FileOutputStream(dest).use { output ->
                        input.copyTo(output)
                    }
                }
                dest.setReadable(true, false)
                dest.setWritable(true, false)
            } catch (e: Exception) {
                throw IOException("Impossibile copiare $sourcePath: ${e.message}")
            }
        }

        /**
         * Cerca il file crypt14.
         */
        private fun findCryptFile(): String {
            val paths =
                listOf(
                    "/sdcard/WhatsApp/Databases/msgstore.db.crypt14",
                    "/sdcard/Android/media/com.whatsapp/WhatsApp/Databases/msgstore.db.crypt14",
                    "/storage/emulated/0/WhatsApp/Databases/msgstore.db.crypt14",
                )
            var mostRecentPath: String? = null
            var maxLastModified = -1L

            for (path in paths) {
                val f = SuFile(path)
                if (f.exists()) {
                    val lastMod = f.lastModified()
                    if (lastMod > maxLastModified) {
                        maxLastModified = lastMod
                        mostRecentPath = path
                    }
                }
            }

            if (mostRecentPath != null) {
                return mostRecentPath
            }

            throw IOException("Database cifrato non trovato.")
        }

        /**
         * Calcola la signature (lunghezza + timestamp) del file crypt14 sorgente sul dispositivo.
         * Usa SuFile per accedere ai percorsi che richiedono root.
         * @return signature hex string, oppure null se il file non è trovato/leggibile
         */
        fun calculateSourceCrypt14Hash(): String? {
            return try {
                val path = findCryptFile()
                val f = SuFile(path)
                "${f.length()}_${f.lastModified()}"
            } catch (e: Exception) {
                android.util.Log.w("WhatStat", "Failed to hash source crypt14: ${e.message}")
                null
            }
        }

        /**
         * Verifica la versione del formato di cifratura leggendo l'header del file.
         */
        suspend fun detectCryptVersion(file: File): Int =
            withContext(Dispatchers.IO) {
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
         * Copia il database live di WhatsApp direttamente dalla directory protetta.
         * Evita la decifratura del crypt14 e include il WAL (messaggi recenti).
         * Ordine di copia: WAL prima del DB principale, per coerenza transazionale.
         * @return Result<LiveExtractedFiles> con i percorsi nella filesDir dell'app
         */
        suspend fun extractLiveDb(): Result<LiveExtractedFiles> =
            withContext(Dispatchers.IO) {
                runCatching {
                    // Cerca la directory databases iterando su /data/user/* (stesso pattern di findWaDbFile)
                    val dataUser = SuFile("/data/user")
                    var sourceDatabasesDir: SuFile? = null

                    dataUser.listFiles()?.forEach { userDir ->
                        val dbDir = SuFile(userDir, "com.whatsapp/databases")
                        val msgstoreCandidate = SuFile(dbDir, "msgstore.db")
                        if (msgstoreCandidate.exists() && sourceDatabasesDir == null) {
                            sourceDatabasesDir = dbDir
                        }
                    }

                    val dbDir =
                        sourceDatabasesDir
                            ?: throw IOException("Directory databases di WhatsApp non trovata.")

                    // 1. Copia WAL e SHM PRIMA del file principale (ordine critico per coerenza)
                    val walSource = SuFile(dbDir, "msgstore.db-wal")
                    val shmSource = SuFile(dbDir, "msgstore.db-shm")
                    val walDest = File(workDir, "msgstore.db-wal")
                    val shmDest = File(workDir, "msgstore.db-shm")

                    if (walSource.exists()) {
                        try {
                            copyWithSuFile(walSource.absolutePath, walDest)
                        } catch (e: Exception) {
                            // WAL opzionale: se manca (checkpoint recente), il DB e' gia' completo
                            android.util.Log.w("WhatStat", "msgstore.db-wal non copiato: ${e.message}")
                        }
                    }
                    if (shmSource.exists()) {
                        try {
                            copyWithSuFile(shmSource.absolutePath, shmDest)
                        } catch (e: Exception) {
                            android.util.Log.w("WhatStat", "msgstore.db-shm non copiato: ${e.message}")
                        }
                    }

                    // 2. Copia il file principale msgstore.db
                    val msgstoreSource = SuFile(dbDir, "msgstore.db")
                    val msgstoreDest = File(workDir, "msgstore.db")
                    copyWithSuFile(msgstoreSource.absolutePath, msgstoreDest)

                    // 3. Copia wa.db con il relativo WAL e SHM (opzionali)
                    val waDbSource = SuFile(dbDir, "wa.db")
                    val waDbDest = File(workDir, WA_DB_DEST)
                    if (waDbSource.exists()) {
                        val waWalSource = SuFile(dbDir, "wa.db-wal")
                        val waShmSource = SuFile(dbDir, "wa.db-shm")
                        if (waWalSource.exists()) {
                            try {
                                copyWithSuFile(waWalSource.absolutePath, File(workDir, "wa.db-wal"))
                            } catch (e: Exception) {
                                android.util.Log.w("WhatStat", "wa.db-wal non copiato: ${e.message}")
                            }
                        }
                        if (waShmSource.exists()) {
                            try {
                                copyWithSuFile(waShmSource.absolutePath, File(workDir, "wa.db-shm"))
                            } catch (e: Exception) {
                                android.util.Log.w("WhatStat", "wa.db-shm non copiato: ${e.message}")
                            }
                        }
                        copyWithSuFile(waDbSource.absolutePath, waDbDest)
                    }

                    // 4. Estrai avatar (riuso del metodo esistente)
                    extractAvatarDir()

                    LiveExtractedFiles(
                        msgstoreDb = msgstoreDest,
                        waDbFile = waDbDest,
                    )
                }
            }

        /**
         * Calcola la signature del database sorgente di WhatsApp per rilevare aggiornamenti.
         * Include la dimensione del WAL per catturare i messaggi non ancora consolidati.
         * Tenta prima il DB live (root), poi ricade sul crypt14 come fallback.
         * @return stringa "size_lastmod_walsize", oppure null se nessuna sorgente e' accessibile
         */
        fun calculateSourceDbSignature(): String? {
            return try {
                val dataUser = SuFile("/data/user")
                dataUser.listFiles()?.forEach { userDir ->
                    val dbDir = SuFile(userDir, "com.whatsapp/databases")
                    val msgstoreDb = SuFile(dbDir, "msgstore.db")
                    if (msgstoreDb.exists()) {
                        val walFile = SuFile(dbDir, "msgstore.db-wal")
                        val walSize = if (walFile.exists()) walFile.length() else 0L
                        return "${msgstoreDb.length()}_${msgstoreDb.lastModified()}_$walSize"
                    }
                }
                // Fallback: usa la signature del crypt14 nella sdcard
                val cryptPath = findCryptFile()
                val f = SuFile(cryptPath)
                "${f.length()}_${f.lastModified()}"
            } catch (e: Exception) {
                android.util.Log.w("WhatStat", "calculateSourceDbSignature failed: ${e.message}")
                null
            }
        }

        /**
         * Elimina tutti i file estratti dalla directory privata.
         * Include WAL e SHM copiati dal flusso Live DB.
         */
        suspend fun cleanup() =
            withContext(Dispatchers.IO) {
                listOf(
                    KEY_DEST,
                    CRYPT14_DEST,
                    WA_DB_DEST,
                    "msgstore.db",
                    "msgstore.db-wal",
                    "msgstore.db-shm",
                    "wa.db-wal",
                    "wa.db-shm",
                ).forEach { name ->
                    SecurityUtils.secureDelete(File(workDir, name))
                }
            }

        suspend fun copyFromUri(
            uri: Uri,
            destFileName: String,
        ) = withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val destFile = File(workDir, destFileName)
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            } ?: throw IOException("Impossibile aprire l'URI: $uri")
        }

        /**
         * Estrae l'intero contenuto dello ZIP esportato da WhatsApp in una sottocartella della cache.
         * Mantiene la struttura dei file per permettere l'analisi dei media.
         */
        suspend fun extractFullChatFolder(zipUri: Uri): Result<File> =
            withContext(Dispatchers.IO) {
                runCatching {
                    val inputStream =
                        context.contentResolver.openInputStream(zipUri)
                            ?: throw IOException("Impossibile aprire lo stream URI: $zipUri")

                    // Crea una cartella unica nei file interni (non cache) per persistenza multi-chat
                    val mediaRoot = File(context.filesDir, "lite_media")
                    if (!mediaRoot.exists()) mediaRoot.mkdirs()

                    val destFolder = File(mediaRoot, "lite_chat_${System.currentTimeMillis()}")
                    destFolder.mkdirs()

                    ZipInputStream(inputStream).use { zis ->
                        var entry = zis.nextEntry
                        while (entry != null) {
                            if (!entry.isDirectory) {
                                val outFile = File(destFolder, entry.name)
                                // Crea eventuali sottocartelle se presenti nello zip
                                outFile.parentFile?.mkdirs()

                                FileOutputStream(outFile).use { fos ->
                                    zis.copyTo(fos)
                                }
                            }
                            zis.closeEntry()
                            entry = zis.nextEntry
                        }
                    }

                    android.util.Log.d("WhatStat", "ZIP estratto con successo in: ${destFolder.absolutePath}")
                    destFolder
                }
            }
    }

data class ExtractedFiles(
    val keyFile: File,
    val crypt14File: File,
    val waDbFile: File,
)

/**
 * File estratti tramite copia diretta del DB live (flusso Live-first).
 * Non richiedono decifratura: sono gia' in chiaro e includono il WAL.
 */
data class LiveExtractedFiles(
    val msgstoreDb: File,
    val waDbFile: File,
)

class CryptVersionException(message: String) : Exception(message)
