package com.whatstat.reborn.data.lite

import android.net.Uri
import com.whatstat.reborn.data.root.FileExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.text.Normalizer
import java.util.Locale
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

    /** ID dell'ultima chat importata: usato da [finalizeOwnerSelection] per limitare l'aggiornamento. */
    private var lastImportedChatId: Long = -1L

    /**
     * Processa lo ZIP importato: parsing del testo, analisi media e scrittura su SQLite.
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

            val zipDisplayName = getFileName(zipUri)
            val isGroup = identifiedSenders.size > 2

            val chatDescriptor = resolveChatDescriptor(
                zipDisplayName = zipDisplayName,
                txtDisplayName = txtFile.nameWithoutExtension,
                ownerName = ownerName,
                senders = identifiedSenders,
                isGroup = isGroup
            )

            // 4. Scrittura su SQLite
            _importProgress.value = LiteImportProgress(0.60f, "Salvataggio database...")
            val chatId = liteDbWriter.writeChat(
                chatName = chatDescriptor.displayName,
                chatImportKey = chatDescriptor.importKey,
                ownerName = ownerName,
                isGroup = isGroup,
                messages = parseResult.messages,
                mediaItems = mediaStats.mediaItems
            ) { progress ->
                _importProgress.value = LiteImportProgress(0.60f + (progress * 0.30f), "Scrittura DB: ${(progress * 100).toInt()}%")
            }

            lastImportedChatId = chatId
            _importProgress.value = LiteImportProgress(0.92f, "Seleziona il tuo nome...")
            ImportResult(identifiedSenders)
        }
    }

    /**
     * Aggiorna il flag [from_me] per l'ultima chat importata dopo la scelta del mittente.
     */
    suspend fun finalizeOwnerSelection(ownerName: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val chatId = lastImportedChatId.takeIf { it > 0L }
                ?: throw Exception("Nessuna chat importata da finalizzare")
            _importProgress.value = LiteImportProgress(0.96f, "Finalizzazione mittente...")

            val db = liteDatabase.getOrCreate()
            val jidRowId = db.query(
                "jid",
                arrayOf("_id"),
                "raw_string = ?",
                arrayOf(ownerName),
                null,
                null,
                null
            ).use { cursor ->
                if (cursor.moveToFirst()) cursor.getLong(0) else -1L
            }

            db.beginTransaction()
            try {
                db.execSQL(
                    "UPDATE message SET from_me = 0 WHERE chat_row_id = ?",
                    arrayOf(chatId)
                )
                if (jidRowId != -1L) {
                    db.execSQL(
                        "UPDATE message SET from_me = 1 WHERE chat_row_id = ? AND (key_id = ? OR sender_jid_row_id = ?)",
                        arrayOf(chatId, ownerName, jidRowId)
                    )
                } else {
                    db.execSQL(
                        "UPDATE message SET from_me = 1 WHERE chat_row_id = ? AND key_id = ?",
                        arrayOf(chatId, ownerName)
                    )
                }
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

    private fun getFileName(uri: Uri): String? {
        if (uri.scheme == "content") {
            liteDatabase.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (index != -1) return cursor.getString(index)
                }
            }
        }
        return uri.path?.let { java.io.File(it).name }
    }

    private fun resolveChatDescriptor(
        zipDisplayName: String?,
        txtDisplayName: String?,
        ownerName: String,
        senders: List<String>,
        isGroup: Boolean
    ): LiteChatDescriptor {
        val normalizedZipName = normalizeExportName(zipDisplayName)
        val normalizedTxtName = normalizeExportName(txtDisplayName)
        val preferredName = sequenceOf(normalizedZipName, normalizedTxtName)
            .filterNotNull()
            .firstOrNull { !isGenericChatName(it) }

        val fallbackName = buildFallbackChatName(ownerName, senders, isGroup)
        val displayName = preferredName ?: fallbackName
        val importKey = when {
            preferredName != null -> "${if (isGroup) "group" else "dm"}:${slugifyForKey(preferredName)}"
            !isGroup -> "dm:${slugifyForKey(senders.distinct().sorted().joinToString("|"))}"
            else -> "group:${slugifyForKey(senders.distinct().sorted().joinToString("|"))}"
        }

        return LiteChatDescriptor(displayName = displayName, importKey = importKey)
    }

    private fun normalizeExportName(rawName: String?): String? {
        if (rawName.isNullOrBlank()) return null
        return rawName
            .replace("Chat WhatsApp con ", "", ignoreCase = true)
            .replace("Chat WhatsApp with ", "", ignoreCase = true)
            .replace("WhatsApp Chat - ", "", ignoreCase = true)
            .replace("WhatsApp Chat with ", "", ignoreCase = true)
            .removeSuffix(".zip")
            .removeSuffix(".txt")
            .trim()
            .ifBlank { null }
    }

    private fun isGenericChatName(name: String): Boolean {
        val compact = name
            .lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]"), "")
        return compact.isBlank() || compact in setOf("chat", "whatsappchat", "chatwhatsapp")
    }

    private fun buildFallbackChatName(ownerName: String, senders: List<String>, isGroup: Boolean): String {
        val distinctSenders = senders.distinct()
        if (!isGroup) {
            return distinctSenders.firstOrNull { it != ownerName }
                ?: distinctSenders.firstOrNull()
                ?: "Chat importata"
        }

        val previewNames = distinctSenders
            .filterNot { it == ownerName }
            .sorted()
            .take(3)
        val suffix = (distinctSenders.size - previewNames.size).coerceAtLeast(0)
        return when {
            previewNames.isEmpty() -> "Gruppo importato"
            suffix > 0 -> "${previewNames.joinToString(", ")} +$suffix"
            else -> previewNames.joinToString(", ")
        }
    }

    private fun slugifyForKey(value: String): String {
        val normalized = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
        return normalized
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .ifBlank { "chat" }
    }

}

data class ImportResult(
    val identifiedSenders: List<String>
)

private data class LiteChatDescriptor(
    val displayName: String,
    val importKey: String
)
