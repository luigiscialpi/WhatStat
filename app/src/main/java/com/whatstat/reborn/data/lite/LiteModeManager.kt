package com.whatstat.reborn.data.lite

import android.net.Uri
import com.whatstat.reborn.data.root.FileExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class LiteImportProgress(
    val percent: Float,   // 0f..1f
    val label: String
)

/**
 * Gestore dell'importazione ZIP in modalita' Lite.
 * Responsabilita': parsing, scrittura su SQLite e finalizzazione del mittente.
 * La cache JSON e' stata rimossa: tutti i dati persistono su [LiteDatabase].
 */
@Singleton
class LiteModeManager @Inject constructor(
    private val fileExtractor: FileExtractor,
    private val parser: TextChatParser,
    private val mediaManager: LiteMediaManager,
    private val liteDbWriter: LiteDbWriter,
    private val liteDatabase: LiteDatabase
) {

    private val _importProgress = MutableStateFlow(LiteImportProgress(0f, ""))
    val importProgress: StateFlow<LiteImportProgress> = _importProgress.asStateFlow()

    /**
     * Processa lo ZIP importato: parsing del testo, analisi media e scrittura su SQLite.
     * Non produce piu' una cache JSON intermedia.
     */
    suspend fun importAndProcess(zipUri: Uri, ownerName: String): Result<ImportResult> = withContext(Dispatchers.IO) {
        runCatching {
            // 1. Estrazione ZIP
            _importProgress.value = LiteImportProgress(0.05f, "Estrazione ZIP...")
            val folder = fileExtractor.extractFullChatFolder(zipUri).getOrThrow()
            val txtFile = folder.listFiles()?.firstOrNull { it.extension == "txt" }
                ?: throw Exception("Nessun file .txt trovato nello ZIP")

            // 2. Parsing Testo
            _importProgress.value = LiteImportProgress(0.20f, "Lettura messaggi...")
            val parseResult = parser.parse(txtFile, ownerName)
            val identifiedSenders = parseResult.messages.map { it.senderName }.distinct()

            // 3. Analisi Media
            _importProgress.value = LiteImportProgress(0.40f, "Analisi media...")
            val mediaStats = mediaManager.extractMediaFromFolder(folder, parseResult.messages)

            // 4. Scrittura su SQLite
            _importProgress.value = LiteImportProgress(0.60f, "Salvataggio database...")
            val chatName = txtFile.nameWithoutExtension
                .replace("Chat WhatsApp con ", "", ignoreCase = true)
                .trim()

            liteDbWriter.writeChat(
                chatName = chatName,
                ownerName = ownerName,
                isGroup = identifiedSenders.size > 2,
                messages = parseResult.messages,
                mediaItems = mediaStats.mediaItems
            ) { progress ->
                _importProgress.value = LiteImportProgress(0.60f + (progress * 0.38f), "Scrittura DB: ${(progress * 100).toInt()}%")
            }

            _importProgress.value = LiteImportProgress(1f, "Completato")

            ImportResult(identifiedSenders)
        }
    }

    /**
     * Aggiorna il flag [from_me] in SQLite dopo che l'utente ha selezionato il mittente.
     * Non dipende piu' dalla cache JSON: opera direttamente sul DB Lite.
     */
    suspend fun finalizeOwnerSelection(ownerName: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            _importProgress.value = LiteImportProgress(0.90f, "Finalizzazione mittente...")

            val db = liteDatabase.getOrCreate()

            // Recupera il jid_row_id del mittente scelto
            val jidRowIdCursor = db.query(
                "jid", arrayOf("_id"),
                "raw_string = ?", arrayOf(ownerName),
                null, null, null
            )
            val jidRowId = jidRowIdCursor.use {
                if (it.moveToFirst()) it.getLong(0) else -1L
            }

            if (jidRowId == -1L) {
                throw Exception("Mittente '$ownerName' non trovato nel database Lite")
            }

            db.beginTransaction()
            try {
                // Reset tutti i messaggi a non-owner
                db.execSQL("UPDATE message SET from_me = 0")
                // Imposta il mittente scelto come owner
                db.execSQL("UPDATE message SET from_me = 1 WHERE sender_jid_row_id = ?", arrayOf(jidRowId))
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }

            _importProgress.value = LiteImportProgress(1f, "Completato")
        }
    }

    /**
     * Cancella tutti i dati Lite: elimina il contenuto del DB SQLite Lite.
     */
    suspend fun clearCache() {
        liteDatabase.deleteAll()
    }
}

data class ImportResult(
    val identifiedSenders: List<String>
)
