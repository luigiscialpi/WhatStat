package com.whatstats.data.lite

import android.content.Context
import android.net.Uri
import com.whatstats.R
import com.whatstats.data.root.FileExtractor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.text.Normalizer
import java.time.ZoneId
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class LiteImportProgress(
    // 0f..1f
    val percent: Float,
    val label: String,
)

/**
 * Gestore dell'importazione ZIP in modalita' Lite.
 * Responsabilita': parsing, scrittura su SQLite e finalizzazione del mittente.
 * La cache JSON e' stata rimossa: tutti i dati persistono su [LiteDatabase].
 */
@Singleton
class LiteModeManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val fileExtractor: FileExtractor,
        private val parser: TextChatParser,
        private val mediaManager: LiteMediaManager,
        private val liteDbWriter: LiteDbWriter,
        private val liteDatabase: LiteDatabase,
    ) {
        private val _importProgress = MutableStateFlow(LiteImportProgress(0f, ""))
        val importProgress: StateFlow<LiteImportProgress> = _importProgress.asStateFlow()

        /** Timestamp dell'ultima importazione riuscita. Sorgente di verità per il refresh dei ViewModel Lite. */
        private val _importCompleted = MutableStateFlow(0L)
        val importCompleted: StateFlow<Long> = _importCompleted.asStateFlow()

        /** Restituisce il timestamp del primo messaggio (più antico) tra i messaggi importati. */
        fun getFirstMessageTimestamp(): Long? {
            return liteDatabase.getOrCreate().rawQuery(
                "SELECT MIN(timestamp) FROM message WHERE message_type NOT IN (7, 15, 90)",
                null,
            ).use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
            }
        }

        /** Notifica che un'importazione è andata a buon fine. Chiamato da [AnalyticsRepository.notifyLiteImportDone]. */
        fun notifyImportDone() {
            _importCompleted.value = System.currentTimeMillis()
        }

        /** ID dell'ultima chat importata: usato da [finalizeOwnerSelection] per limitare l'aggiornamento. */
        private var lastImportedChatId: Long = -1L

        /**
         * Processa lo ZIP importato: parsing del testo, analisi media e scrittura su SQLite.
         */
        suspend fun importAndProcess(
            zipUri: Uri,
            ownerName: String,
        ): Result<ImportResult> =
            withContext(Dispatchers.IO) {
                runCatching {
                    // 1. Estrazione ZIP
                    _importProgress.value = LiteImportProgress(0.05f, context.getString(R.string.lite_import_extract_zip))
                    val folder = fileExtractor.extractFullChatFolder(zipUri).getOrThrow()
                    val txtFile =
                        folder.listFiles()?.firstOrNull { it.extension == "txt" }
                            ?: throw Exception(context.getString(R.string.lite_import_error_no_txt))

                    // 2. Parsing Testo
                    _importProgress.value = LiteImportProgress(0.20f, context.getString(R.string.lite_import_read_messages))
                    val parseResult = parser.parse(txtFile, ownerName)
                    val identifiedSenders = parseResult.messages.map { it.senderName }.distinct()

                    // 3. Analisi Media
                    _importProgress.value = LiteImportProgress(0.40f, context.getString(R.string.lite_import_analyze_media))
                    val mediaStats = mediaManager.extractMediaFromFolder(folder, parseResult.messages)

                    val zipDisplayName = getFileName(zipUri)
                    val isGroup = identifiedSenders.size > 2
                    val fingerprint = computeContentFingerprint(parseResult.messages)

                    val chatDescriptor =
                        resolveChatDescriptor(
                            zipDisplayName = zipDisplayName,
                            txtDisplayName = txtFile.nameWithoutExtension,
                            ownerName = ownerName,
                            senders = identifiedSenders,
                            isGroup = isGroup,
                            fingerprint = fingerprint,
                        )

                    // 4. Scrittura su SQLite
                    _importProgress.value = LiteImportProgress(0.60f, context.getString(R.string.lite_import_save_db))
                    val importTimestamp = getFileTimestamp(zipUri)
                    val chatId =
                        liteDbWriter.writeChat(
                            chatName = chatDescriptor.displayName,
                            chatImportKey = chatDescriptor.importKey,
                            ownerName = ownerName,
                            isGroup = isGroup,
                            messages = parseResult.messages,
                            mediaItems = mediaStats.mediaItems,
                            importTimestamp = importTimestamp,
                        ) { progress ->
                            _importProgress.value =
                                LiteImportProgress(
                                    0.60f + (progress * 0.30f),
                                    context.getString(R.string.lite_import_write_db_progress, (progress * 100).toInt()),
                                )
                        }

                    lastImportedChatId = chatId

                    // 5. Slim in background: rimuove video/audio dal source.zip (già estratti su disco).
                    //    Fire-and-forget: il dato è già nel DB, l'utente può procedere subito.
                    CoroutineScope(Dispatchers.IO).launch {
                        fileExtractor.slimSourceZip(folder)
                    }

                    _importProgress.value = LiteImportProgress(0.92f, context.getString(R.string.lite_import_select_owner))
                    ImportResult(identifiedSenders)
                }
            }

        /**
         * Aggiorna il flag [from_me] per l'ultima chat importata dopo la scelta del mittente.
         */
        suspend fun finalizeOwnerSelection(ownerName: String): Result<Unit> =
            withContext(Dispatchers.IO) {
                runCatching {
                    val chatId =
                        lastImportedChatId.takeIf { it > 0L }
                            ?: throw Exception(context.getString(R.string.lite_import_error_no_chat_to_finalize))
                    _importProgress.value = LiteImportProgress(0.96f, context.getString(R.string.lite_import_finalize_owner))

                    val db = liteDatabase.getOrCreate()
                    val jidRowId =
                        db.query(
                            "jid",
                            arrayOf("_id"),
                            "raw_string = ?",
                            arrayOf(ownerName),
                            null,
                            null,
                            null,
                        ).use { cursor ->
                            if (cursor.moveToFirst()) cursor.getLong(0) else -1L
                        }

                    db.beginTransaction()
                    try {
                        db.execSQL(
                            "UPDATE message SET from_me = 0 WHERE chat_row_id = ?",
                            arrayOf(chatId),
                        )
                        if (jidRowId != -1L) {
                            db.execSQL(
                                "UPDATE message SET from_me = 1 WHERE chat_row_id = ? AND (key_id = ? OR sender_jid_row_id = ?)",
                                arrayOf(chatId, ownerName, jidRowId),
                            )
                        } else {
                            db.execSQL(
                                "UPDATE message SET from_me = 1 WHERE chat_row_id = ? AND key_id = ?",
                                arrayOf(chatId, ownerName),
                            )
                        }
                        db.setTransactionSuccessful()
                    } finally {
                        db.endTransaction()
                    }

                    _importProgress.value = LiteImportProgress(1f, context.getString(R.string.lite_import_completed))
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

        private fun getFileTimestamp(uri: Uri): Long? {
            if (uri.scheme == "content") {
                try {
                    liteDatabase.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val index = cursor.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                            if (index != -1) return cursor.getLong(index)
                        }
                    }
                } catch (e: Exception) {
                    // Fallback a MediaStore
                    try {
                        liteDatabase.contentResolver.query(
                            uri,
                            arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                            null,
                            null,
                            null,
                        )?.use {
                                _ ->
                            // Alcuni provider non espongono last_modified via query standard
                        }
                    } catch (e2: Exception) {
                    }
                }
            } else if (uri.scheme == "file") {
                return uri.path?.let { java.io.File(it).lastModified() }
            }
            return System.currentTimeMillis() // Fallback a "ora"
        }

        /**
         * Risolve il nome visualizzato e la chiave di import per una chat.
         *
         * Il [fingerprint] (hash dei primi N timestamp) viene incluso nell'importKey
         * per distinguere chat diverse che condividono lo stesso nome contatto
         * (es. stesso contatto con piu' numeri WhatsApp).
         * Lo stesso ZIP re-importato produce lo stesso fingerprint -> sovrascrittura corretta.
         * ZIP diversi dello stesso contatto -> fingerprint diverso -> chat separate.
         */
        private fun resolveChatDescriptor(
            zipDisplayName: String?,
            txtDisplayName: String?,
            ownerName: String,
            senders: List<String>,
            isGroup: Boolean,
            fingerprint: String,
        ): LiteChatDescriptor {
            val normalizedZipName = normalizeExportName(zipDisplayName)
            val normalizedTxtName = normalizeExportName(txtDisplayName)
            val preferredName =
                sequenceOf(normalizedZipName, normalizedTxtName)
                    .filterNotNull()
                    .firstOrNull { !isGenericChatName(it) }

            val fallbackName = buildFallbackChatName(ownerName, senders, isGroup)
            val displayName = preferredName ?: fallbackName
            val typePrefix = if (isGroup) "group" else "dm"
            val baseSlug =
                when {
                    preferredName != null -> slugifyForKey(preferredName)
                    !isGroup -> slugifyForKey(senders.distinct().sorted().joinToString("|"))
                    else -> slugifyForKey(senders.distinct().sorted().joinToString("|"))
                }
            // Il fingerprint rende l'importKey unico per contenuto,
            // permettendo chat omonime senza sovrascrittura accidentale.
            val importKey = "$typePrefix:$baseSlug:$fingerprint"

            return LiteChatDescriptor(displayName = displayName, importKey = importKey)
        }

        /**
         * Estrae il nome della chat dal nome del file ZIP/TXT esportato da WhatsApp.
         *
         * Strategia: ancora la ricerca alla parola "WhatsApp" presente in tutti gli export,
         * poi cerca la prima congiunzione linguistica (con, with, avec, ...) che segue.
         * Tutto cio' che viene dopo la congiunzione e' il nome della chat.
         *
         * Se "WhatsApp" non e' presente nel nome, restituisce il nome intero pulito
         * (caso di file rinominati manualmente o condivisioni dirette).
         *
         * Lingue supportate: IT, EN, ES, FR, DE, PT, RU, PL, NL/AF, NO/SE/DA.
         */
        private fun normalizeExportName(rawName: String?): String? {
            if (rawName.isNullOrBlank()) return null

            // Rimuove l'estensione (es. .zip o .txt) in modo case-insensitive
            val name =
                if (rawName.contains(".")) {
                    rawName.substringBeforeLast(".")
                } else {
                    rawName
                }

            // Regex ancorata a "WhatsApp": cerca la congiunzione DOPO la parola WhatsApp.
            // .*\bWhatsApp\b  -> matcha tutto fino a "WhatsApp" (greedy, ancora il contesto)
            // .*?             -> matcha il minimo necessario (lazy) fino alla congiunzione
            // \b(conj)\s+     -> congiunzione seguita da spazio
            // Il resto della stringa e' il nome della chat.
            //
            // Congiunzioni per lingua:
            //   IT/ES: con  |  EN: with  |  FR: avec  |  DE: mit  |  PT: com
            //   RU: \u0441 (cirillico "s")  |  NL/AF: met  |  NO/SE/DA: med
            //   PL: z u\u017cytkownikiem / z grup\u0105
            val prefixRegex =
                Regex(
                    """.*\bWhatsApp\b.*?\b(con|with|avec|mit|com|met|med|\u0441|z\s+u\u017cytkownikiem|z\s+grup\u0105)\s+""",
                    RegexOption.IGNORE_CASE,
                )
            val match = prefixRegex.find(name)

            if (match != null) {
                val cleanName = name.substring(match.range.last + 1).trim()
                if (cleanName.isNotBlank()) {
                    return cleanName
                }
            }

            // Se "WhatsApp" non e' nel nome, restituisce il nome intero pulito
            return name.trim().ifBlank { null }
        }

        private fun isGenericChatName(name: String): Boolean {
            val compact =
                name
                    .lowercase(Locale.ROOT)
                    .replace(Regex("[^a-z0-9]"), "")
            return compact.isBlank() || compact in setOf("chat", "whatsappchat", "chatwhatsapp")
        }

        private fun buildFallbackChatName(
            ownerName: String,
            senders: List<String>,
            isGroup: Boolean,
        ): String {
            val distinctSenders = senders.distinct()
            if (!isGroup) {
                return distinctSenders.firstOrNull { it != ownerName }
                    ?: distinctSenders.firstOrNull()
                    ?: context.getString(R.string.lite_fallback_chat_name)
            }

            val previewNames =
                distinctSenders
                    .filterNot { it == ownerName }
                    .sorted()
                    .take(3)
            val suffix = (distinctSenders.size - previewNames.size).coerceAtLeast(0)
            return when {
                previewNames.isEmpty() -> context.getString(R.string.lite_fallback_group_name)
                suffix > 0 -> "${previewNames.joinToString(", ")} +$suffix"
                else -> previewNames.joinToString(", ")
            }
        }

        private fun slugifyForKey(value: String): String {
            val normalized =
                Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
                    .replace(Regex("\\p{M}+"), "")
            return normalized
                .replace(Regex("[^a-z0-9]+"), "_")
                .trim('_')
                .ifBlank { "chat" }
        }

        /**
         * Genera un fingerprint compatto (6 caratteri hex) basato sui timestamp
         * dei primi [FINGERPRINT_SAMPLE_SIZE] messaggi della chat.
         *
         * Due import dello stesso ZIP producono lo stesso fingerprint (= re-import).
         * Due chat diverse dello stesso contatto (numeri diversi) producono
         * fingerprint diversi, evitando sovrascritture accidentali.
         */
        private fun computeContentFingerprint(messages: List<ParsedMessage>): String {
            val sample = messages.take(FINGERPRINT_SAMPLE_SIZE)
            val digest = MessageDigest.getInstance("MD5")
            for (msg in sample) {
                val epochMillis =
                    msg.timestamp
                        .atZone(ZoneId.systemDefault())
                        .toInstant()
                        .toEpochMilli()
                digest.update(epochMillis.toString().toByteArray())
            }
            // Primi 6 caratteri hex: 16^6 = ~16 milioni di combinazioni,
            // piu' che sufficienti per distinguere chat dello stesso contatto.
            return digest.digest()
                .joinToString("") { "%02x".format(it) }
                .take(6)
        }

        companion object {
            /** Numero di messaggi usati per il calcolo del fingerprint. */
            private const val FINGERPRINT_SAMPLE_SIZE = 10
        }
    }

data class ImportResult(
    val identifiedSenders: List<String>,
)

private data class LiteChatDescriptor(
    val displayName: String,
    val importKey: String,
)
