package com.whatstats.data.lite

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LiteDbWriter
    @Inject
    constructor(
        private val liteDatabase: LiteDatabase,
    ) {
        /**
         * Scrive una chat importata nel DB Lite.
         * Gestisce: jid, chat, message, message_media.
         * Ritorna il chatId inserito.
         */
        suspend fun writeChat(
            chatName: String,
            chatImportKey: String,
            ownerName: String,
            isGroup: Boolean,
            messages: List<ParsedMessage>,
            mediaItems: List<LiteMediaItem>,
            importTimestamp: Long? = null,
            progressCallback: (Float) -> Unit = {},
        ): Long =
            withContext(Dispatchers.IO) {
                val db = liteDatabase.getOrCreate()

                // 1. Inserisci/recupera jid per ogni mittente
                val senderJidMap = mutableMapOf<String, Long>() // senderName -> jid._id
                val allSenders = messages.map { it.senderName }.distinct()
                for (sender in allSenders) {
                    senderJidMap[sender] = getOrInsertJid(db, sender)
                }

                // 2. Crea entry chat (o recupera esistente per re-import)
                val chatJidId = getOrInsertJid(db, chatName, if (isGroup) "g.us" else "s.whatsapp.net")
                val upsertResult = insertOrUpdateChat(db, chatJidId, chatName, chatImportKey, isGroup)
                val chatId = upsertResult.chatId

                // 2b. Registra il timestamp di import in lite_chat_metadata (se fornito)
                if (importTimestamp != null) {
                    upsertChatMetadata(db, chatId, importTimestamp)
                }

                // 3. Inserisci messaggi in batch (transaction) con Prepared Statement
                var lastMessageId = 0L
                var lastMessageTimestamp = 0L
                db.beginTransaction()
                try {
                    val total = messages.size
                    val stmt =
                        db.compileStatement(
                            """INSERT INTO message (chat_row_id, from_me, sender_jid_row_id, timestamp, message_type, text_data, key_id)
                           VALUES (?, ?, ?, ?, ?, ?, ?)""",
                        )
                    messages.forEachIndexed { i, msg ->
                        val senderJidId = senderJidMap[msg.senderName] ?: 0L
                        val messageType = if (msg.hasMedia) guessMediaType(msg.content) else 0
                        // I messaggi di sistema (es. "codice sicurezza cambiato", "videochiamata persa")
                        // vengono scritti con text_data = null per escluderli automaticamente dalle
                        // query di text analysis e word search (che richiedono text_data IS NOT NULL).
                        val textData =
                            when {
                                msg.hasMedia || msg.isSystemMessage -> null
                                else -> msg.content
                            }
                        val epochMillis =
                            msg.timestamp
                                .atZone(ZoneId.systemDefault())
                                .toInstant()
                                .toEpochMilli()

                        stmt.clearBindings()
                        stmt.bindLong(1, chatId)
                        stmt.bindLong(2, if (msg.isCurrentUser) 1L else 0L)
                        stmt.bindLong(3, senderJidId)
                        stmt.bindLong(4, epochMillis)
                        stmt.bindLong(5, messageType.toLong())
                        if (textData != null) stmt.bindString(6, textData) else stmt.bindNull(6)
                        stmt.bindString(7, msg.senderName)

                        val messageId = stmt.executeInsert()
                        lastMessageId = messageId
                        lastMessageTimestamp = epochMillis

                        if (i % 500 == 0) progressCallback(i.toFloat() / total * 0.9f)
                    }
                    stmt.close()
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }

                // Costruisci mappa timestamp→[messageId, ...] per matching media O(log n).
                // Un ArrayDeque per bucket permette di gestire N messaggi con lo stesso
                // timestamp al secondo (es. sticker iOS inviati in rapida sequenza),
                // evitando che le entry duplicate si sovrascrivano.
                val messageTimestampMap = java.util.TreeMap<Long, ArrayDeque<Long>>() // timestamp → [_id, ...]
                db.rawQuery(
                    "SELECT _id, timestamp FROM message WHERE chat_row_id = ? AND (message_type != 0 OR text_data IS NULL)",
                    arrayOf(chatId.toString()),
                ).use { cursor ->
                    while (cursor.moveToNext()) {
                        val ts = cursor.getLong(1)
                        val id = cursor.getLong(0)
                        messageTimestampMap.getOrPut(ts) { ArrayDeque() }.addLast(id)
                    }
                }

                // 4. Inserisci media
                insertMediaItems(db, chatId, mediaItems, messageTimestampMap)

                // 4c. Crea indici posticipati (se non presenti)
                liteDatabase.ensureIndexes()

                updateChatSummary(db, chatId, chatName, chatImportKey, isGroup, lastMessageId, lastMessageTimestamp)
                cleanupMediaFolders(upsertResult.obsoleteMediaFolders)

                // 4b. Per chat 1-1: registra in wa_contacts la mappatura sender→chatName.
                // In Lite mode il chatName viene estratto dal nome file ZIP (es. "Matilde lavoro"),
                // mentre il senderName nei messaggi è il nome WhatsApp della persona (es. "Matilde Barbaro").
                // Senza questa mappatura, resolveContactNames non riesce a risolvere il senderJID
                // al nome personalizzato, e le slow stats mostrano il nome raw del file .txt.
                // CONFLICT_IGNORE: preserva eventuali rename manuali già presenti in wa_contacts.
                if (!isGroup) {
                    val waDb = liteDatabase.getWaDb()
                    messages.filter { !it.isCurrentUser }
                        .map { it.senderName }
                        .distinct()
                        .forEach { senderName ->
                            val waValues =
                                ContentValues().apply {
                                    put("jid", senderName)
                                    put("display_name", chatName)
                                }
                            waDb.insertWithOnConflict("wa_contacts", null, waValues, SQLiteDatabase.CONFLICT_IGNORE)
                        }
                }

                progressCallback(1f)
                chatId
            }

        private fun getOrInsertJid(
            db: SQLiteDatabase,
            rawString: String,
            server: String = "s.whatsapp.net",
        ): Long {
            val cursor = db.query("jid", arrayOf("_id"), "raw_string = ?", arrayOf(rawString), null, null, null)
            return cursor.use {
                if (it.moveToFirst()) {
                    it.getLong(0)
                } else {
                    val values =
                        ContentValues().apply {
                            put("raw_string", rawString)
                            put("server", server)
                            put("user", rawString)
                        }
                    db.insert("jid", null, values)
                }
            }
        }

        /**
         * Inserisce o aggiorna una chat nel DB Lite.
         *
         * Logica di upsert:
         * - Se esiste una chat con lo stesso [importKey] -> re-import: sovrascrive messaggi.
         * - Se esiste una chat legacy senza importKey con lo stesso jidRowId -> migrazione.
         * - Altrimenti -> nuova chat. Se il [subject] e' gia' usato da un'altra chat,
         *   appende un suffisso numerico progressivo "(2)", "(3)" ecc. per disambiguare.
         */
        private fun insertOrUpdateChat(
            db: SQLiteDatabase,
            jidRowId: Long,
            subject: String,
            importKey: String,
            isGroup: Boolean,
        ): ChatUpsertResult {
            val existingId =
                db.query(
                    "chat",
                    arrayOf("_id"),
                    "import_key = ?",
                    arrayOf(importKey),
                    null,
                    null,
                    null,
                ).use { if (it.moveToFirst()) it.getLong(0) else null }
                    ?: db.query(
                        "chat",
                        arrayOf("_id"),
                        "import_key IS NULL AND jid_row_id = ?",
                        arrayOf(jidRowId.toString()),
                        null,
                        null,
                        null,
                    ).use { if (it.moveToFirst()) it.getLong(0) else null }

            if (existingId != null) {
                val obsoleteMediaFolders = getChatMediaFolders(db, existingId)
                val updateValues =
                    ContentValues().apply {
                        put("jid_row_id", jidRowId)
                        put("subject", subject)
                        put("group_type", if (isGroup) 1 else 0)
                        put("import_key", importKey)
                    }
                db.update("chat", updateValues, "_id = ?", arrayOf(existingId.toString()))
                // Pulizia per re-import
                db.delete(
                    "message_media",
                    "message_row_id IN (SELECT _id FROM message WHERE chat_row_id = ?)",
                    arrayOf(existingId.toString()),
                )
                db.delete("message", "chat_row_id = ?", arrayOf(existingId.toString()))
                return ChatUpsertResult(existingId, obsoleteMediaFolders)
            }

            // Nuova chat: disambigua il displayName se necessario.
            // Conta quante chat hanno gia' lo stesso subject (o subject + suffisso numerico).
            val disambiguatedSubject = disambiguateDisplayName(db, subject)

            val values =
                ContentValues().apply {
                    put("jid_row_id", jidRowId)
                    put("subject", disambiguatedSubject)
                    put("group_type", if (isGroup) 1 else 0)
                    put("import_key", importKey)
                }
            val chatId = db.insert("chat", null, values)
            return ChatUpsertResult(
                chatId = chatId,
                obsoleteMediaFolders = emptySet(),
            )
        }

        /**
         * Se esistono gia' chat con lo stesso nome base, appende un suffisso numerico
         * progressivo: "Nome", "Nome (2)", "Nome (3)", ecc.
         *
         * Cerca nel DB tutte le chat il cui subject corrisponde al nome base
         * (con o senza suffisso numerico) e calcola il prossimo indice disponibile.
         */
        private fun disambiguateDisplayName(
            db: SQLiteDatabase,
            baseName: String,
        ): String {
            // Conta le chat il cui subject e' esattamente baseName oppure baseName + " (N)"
            val cursor =
                db.rawQuery(
                    """
                    SELECT subject FROM chat
                    WHERE subject = ? OR subject LIKE ?
                    """.trimIndent(),
                    arrayOf(baseName, "$baseName (%)"),
                )

            val existingNames =
                cursor.use {
                    buildSet {
                        while (it.moveToNext()) {
                            add(it.getString(0))
                        }
                    }
                }

            if (existingNames.isEmpty()) return baseName

            // Trova il prossimo indice numerico disponibile
            var nextIndex = 2
            while (existingNames.contains("$baseName ($nextIndex)")) {
                nextIndex++
            }
            return "$baseName ($nextIndex)"
        }

        private fun upsertChatMetadata(
            db: SQLiteDatabase,
            chatId: Long,
            importTimestamp: Long,
        ) {
            val values =
                ContentValues().apply {
                    put("chat_id", chatId)
                    put("import_timestamp", importTimestamp)
                }
            db.insertWithOnConflict("lite_chat_metadata", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        }

        private fun guessMediaType(content: String): Int {
            return when {
                content.contains("IMG-") ||
                    content.contains(".jpg", ignoreCase = true) ||
                    content.contains(".jpeg", ignoreCase = true) ||
                    content.contains(".png", ignoreCase = true) -> 1

                content.contains("AUD-") ||
                    content.contains("PTT-") ||
                    content.contains(".opus", ignoreCase = true) ||
                    content.contains(".aac", ignoreCase = true) ||
                    content.contains(".wav", ignoreCase = true) -> 2

                content.contains("VID-") ||
                    content.contains(".mp4", ignoreCase = true) ||
                    content.contains(".gif", ignoreCase = true) ||
                    content.contains(".mov", ignoreCase = true) -> 3

                content.contains("DOC-") ||
                    content.contains(".pdf", ignoreCase = true) -> 9

                content.contains("STK-") ||
                    content.contains("STICKER", ignoreCase = true) ||
                    content.contains(".webp", ignoreCase = true) -> 20

                else -> 0
            }
        }

        private fun insertMediaItems(
            db: SQLiteDatabase,
            chatId: Long,
            items: List<LiteMediaItem>,
            messageTimestampMap: java.util.TreeMap<Long, ArrayDeque<Long>>,
        ) {
            if (items.isEmpty()) return

            val stmt =
                db.compileStatement(
                    """
                    INSERT OR REPLACE INTO message_media (message_row_id, file_path, mime_type, file_size, file_hash)
                    VALUES (?, ?, ?, ?, ?)
                    """.trimIndent(),
                )

            db.beginTransaction()
            try {
                for (item in items) {
                    // Cerca il bucket con timestamp piu' vicino entro ±1 secondo (O(log n)).
                    // Si usa floorEntry(ts+1000) per trovare l'entry con chiave <= ts+1000,
                    // poi si verifica che sia >= ts-1000. Se il bucket ha piu' ID (messaggi
                    // con lo stesso timestamp), si consuma il primo disponibile con removeFirst()
                    // per associare ogni media a un messaggio distinto.
                    val floorEntry = messageTimestampMap.floorEntry(item.timestamp + 1000)
                    val messageId =
                        if (floorEntry != null && floorEntry.key >= item.timestamp - 1000) {
                            // Pop del primo ID disponibile nel bucket; se il bucket si svuota
                            // lo rimuoviamo dalla mappa per non lasciare entry vuote.
                            val id = floorEntry.value.removeFirstOrNull()
                            if (floorEntry.value.isEmpty()) {
                                messageTimestampMap.remove(floorEntry.key)
                            }
                            id
                        } else {
                            null
                        }

                    if (messageId != null) {
                        stmt.clearBindings()
                        stmt.bindLong(1, messageId)
                        stmt.bindString(2, item.relativePath)
                        stmt.bindString(3, getMimeType(item.type))
                        stmt.bindLong(4, item.fileSize)
                        if (item.type == "STICKER") {
                            stmt.bindString(5, item.fileName)
                        } else {
                            stmt.bindNull(5)
                        }
                        stmt.executeInsert()
                    }
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
                stmt.close()
            }
        }

        private fun getMimeType(type: String): String {
            return when (type) {
                "IMAGE" -> "image/jpeg"
                "STICKER" -> "image/webp"
                "GIF" -> "image/gif"
                "VIDEO" -> "video/mp4"
                "AUDIO" -> "audio/ogg"
                "DOCUMENT" -> "application/pdf"
                else -> "application/octet-stream"
            }
        }

        suspend fun deleteChat(chatId: Long) =
            withContext(Dispatchers.IO) {
                val db = liteDatabase.getOrCreate()
                val obsoleteMediaFolders = getChatMediaFolders(db, chatId)
                db.beginTransaction()
                try {
                    db.delete(
                        "message_media",
                        "message_row_id IN (SELECT _id FROM message WHERE chat_row_id = ?)",
                        arrayOf(chatId.toString()),
                    )
                    db.delete("message", "chat_row_id = ?", arrayOf(chatId.toString()))
                    db.delete("chat", "_id = ?", arrayOf(chatId.toString()))
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
                cleanupMediaFolders(obsoleteMediaFolders)
            }

        /**
         * Rinomina una chat aggiornando solo il campo [subject] (nome visualizzato).
         *
         * L'[importKey] non viene modificato: il meccanismo di re-import
         * continua a funzionare correttamente anche dopo il rename.
         */
        suspend fun renameChat(
            chatId: Long,
            newName: String,
        ) = withContext(Dispatchers.IO) {
            val db = liteDatabase.getOrCreate()
            val waDb = liteDatabase.getWaDb()
            val trimmedName = newName.trim()

            // 1. Recupera il JID e il tipo (gruppo/1-1) associati alla chat
            data class ChatMeta(val jid: String, val isGroup: Boolean)
            val chatMeta =
                db.rawQuery(
                    """
                    SELECT j.raw_string, c.group_type
                    FROM jid j
                    JOIN chat c ON c.jid_row_id = j._id
                    WHERE c._id = ?
                    """.trimIndent(),
                    arrayOf(chatId.toString()),
                ).use { if (it.moveToFirst()) ChatMeta(it.getString(0), it.getInt(1) == 1) else null }
            val jid = chatMeta?.jid

            // 2. Aggiorna il subject nel database principale (per la lista chat)
            val values =
                ContentValues().apply {
                    put("subject", trimmedName)
                }
            db.update("chat", values, "_id = ?", arrayOf(chatId.toString()))

            // 3. Aggiorna display_name in wa_contacts (per la risoluzione nomi globale/dashboard)
            // Usiamo insertWithOnConflict perche' in Lite mode wa_contacts potrebbe essere vuota.
            if (jid != null) {
                val waValues =
                    ContentValues().apply {
                        put("jid", jid)
                        put("display_name", trimmedName)
                    }
                waDb.insertWithOnConflict("wa_contacts", null, waValues, SQLiteDatabase.CONFLICT_REPLACE)

                // 4. Aggiorna anche il campo 'user' nella tabella jid per evitare che il fallback
                // di ContactDao ripristini il nome originale dell'importazione.
                val jidValues =
                    ContentValues().apply {
                        put("user", trimmedName)
                    }
                db.update("jid", jidValues, "raw_string = ?", arrayOf(jid))

                // 5. Solo per chat 1-1: aggiorna wa_contacts e jid.user per i sender.
                // In Lite mode il chatName (dal nome file ZIP) e il senderName (dal .txt) sono diversi;
                // questa mappatura garantisce che resolveContactName(senderJID) ritorni il nome
                // personalizzato della chat nelle slow stats (senderBreakdown, ecc.).
                // NON eseguire per gruppi: i membri hanno identità indipendenti dalla chat.
                if (chatMeta?.isGroup == false) {
                    db.rawQuery(
                        """
                        SELECT DISTINCT j.raw_string
                        FROM message m
                        JOIN jid j ON m.sender_jid_row_id = j._id
                        WHERE m.chat_row_id = ? AND m.from_me = 0
                        """.trimIndent(),
                        arrayOf(chatId.toString()),
                    ).use { cursor ->
                        while (cursor.moveToNext()) {
                            val senderRaw = cursor.getString(0) ?: continue
                            db.update("jid", ContentValues().apply { put("user", trimmedName) }, "raw_string = ?", arrayOf(senderRaw))
                            waDb.insertWithOnConflict(
                                "wa_contacts",
                                null,
                                ContentValues().apply {
                                    put("jid", senderRaw)
                                    put("display_name", trimmedName)
                                },
                                SQLiteDatabase.CONFLICT_REPLACE,
                            )
                        }
                    }
                }
            }
        }

        /**
         * Aggiorna i campi di riepilogo della chat (ultimo messaggio, timestamp di ordinamento).
         *
         * NOTA: Non sovrascrive il campo [subject] perche' potrebbe contenere
         * un suffisso di disambiguazione (es. "Nome (2)") impostato da [insertOrUpdateChat].
         */
        private fun updateChatSummary(
            db: SQLiteDatabase,
            chatId: Long,
            subject: String,
            importKey: String,
            isGroup: Boolean,
            lastMessageId: Long,
            lastMessageTimestamp: Long,
        ) {
            val values =
                ContentValues().apply {
                    put("import_key", importKey)
                    put("group_type", if (isGroup) 1 else 0)
                    if (lastMessageId > 0L) {
                        put("display_message_row_id", lastMessageId)
                        put("last_message_row_id", lastMessageId)
                        put("sort_timestamp", lastMessageTimestamp)
                    }
                }
            db.update("chat", values, "_id = ?", arrayOf(chatId.toString()))
        }

        private fun getChatMediaFolders(
            db: SQLiteDatabase,
            chatId: Long,
        ): Set<String> {
            val cursor =
                db.rawQuery(
                    """
                    SELECT mm.file_path
                    FROM message_media mm
                    JOIN message m ON m._id = mm.message_row_id
                    WHERE m.chat_row_id = ?
                    """.trimIndent(),
                    arrayOf(chatId.toString()),
                )

            return cursor.use {
                buildSet {
                    while (it.moveToNext()) {
                        val filePath = it.getString(0).orEmpty()
                        val folderName = filePath.substringBefore("/", missingDelimiterValue = "")
                        if (folderName.startsWith("lite_chat_")) {
                            add(folderName)
                        }
                    }
                }
            }
        }

        private fun cleanupMediaFolders(folderNames: Set<String>) {
            if (folderNames.isEmpty()) return
            val mediaRoot = File(liteDatabase.filesDir, "lite_media")
            folderNames.forEach { folderName ->
                File(mediaRoot, folderName).deleteRecursively()
            }
        }

        private data class ChatUpsertResult(
            val chatId: Long,
            val obsoleteMediaFolders: Set<String>,
        )
    }
