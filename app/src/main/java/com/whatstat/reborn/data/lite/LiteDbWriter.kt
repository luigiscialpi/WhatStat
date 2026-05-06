package com.whatstat.reborn.data.lite

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LiteDbWriter @Inject constructor(
    private val liteDatabase: LiteDatabase
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
        progressCallback: (Float) -> Unit = {}
    ): Long = withContext(Dispatchers.IO) {
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

        // 3. Inserisci messaggi in batch (transaction)
        var lastMessageId = 0L
        var lastMessageTimestamp = 0L
        db.beginTransaction()
        try {
            val total = messages.size
            messages.forEachIndexed { i, msg ->
                val senderJidId = senderJidMap[msg.senderName] ?: 0L
                val messageType = if (msg.hasMedia) guessMediaType(msg.content) else 0
                val textData = if (msg.hasMedia) null else msg.content

                val messageId = insertMessage(db, chatId, msg, senderJidId, messageType, textData)
                lastMessageId = messageId
                lastMessageTimestamp = msg.timestamp
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()

                if (i % 500 == 0) progressCallback(i.toFloat() / total * 0.9f)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }

        // 4. Inserisci media
        insertMediaItems(db, chatId, mediaItems)
        updateChatSummary(db, chatId, chatName, chatImportKey, isGroup, lastMessageId, lastMessageTimestamp)
        cleanupMediaFolders(upsertResult.obsoleteMediaFolders)

        progressCallback(1f)
        chatId
    }

    private fun getOrInsertJid(db: SQLiteDatabase, rawString: String, server: String = "s.whatsapp.net"): Long {
        val cursor = db.query("jid", arrayOf("_id"), "raw_string = ?", arrayOf(rawString), null, null, null)
        return cursor.use {
            if (it.moveToFirst()) {
                it.getLong(0)
            } else {
                val values = ContentValues().apply {
                    put("raw_string", rawString)
                    put("server", server)
                    put("user", rawString)
                }
                db.insert("jid", null, values)
            }
        }
    }

    private fun insertOrUpdateChat(
        db: SQLiteDatabase,
        jidRowId: Long,
        subject: String,
        importKey: String,
        isGroup: Boolean
    ): ChatUpsertResult {
        val existingId = db.query(
            "chat",
            arrayOf("_id"),
            "import_key = ?",
            arrayOf(importKey),
            null,
            null,
            null
        ).use { if (it.moveToFirst()) it.getLong(0) else null }
            ?: db.query(
                "chat",
                arrayOf("_id"),
                "import_key IS NULL AND jid_row_id = ?",
                arrayOf(jidRowId.toString()),
                null,
                null,
                null
            ).use { if (it.moveToFirst()) it.getLong(0) else null }

        if (existingId != null) {
            val obsoleteMediaFolders = getChatMediaFolders(db, existingId)
            val updateValues = ContentValues().apply {
                put("jid_row_id", jidRowId)
                put("subject", subject)
                put("group_type", if (isGroup) 1 else 0)
                put("import_key", importKey)
            }
            db.update("chat", updateValues, "_id = ?", arrayOf(existingId.toString()))
            // Pulizia per re-import
            db.delete("message_media", "message_row_id IN (SELECT _id FROM message WHERE chat_row_id = ?)", arrayOf(existingId.toString()))
            db.delete("message", "chat_row_id = ?", arrayOf(existingId.toString()))
            return ChatUpsertResult(existingId, obsoleteMediaFolders)
        }

        val values = ContentValues().apply {
            put("jid_row_id", jidRowId)
            put("subject", subject)
            put("group_type", if (isGroup) 1 else 0)
            put("import_key", importKey)
        }
        val chatId = db.insert("chat", null, values)
        return ChatUpsertResult(
            chatId = chatId,
            obsoleteMediaFolders = emptySet()
        )
    }

    private fun insertMessage(
        db: SQLiteDatabase, 
        chatId: Long, 
        msg: ParsedMessage, 
        senderJidId: Long, 
        messageType: Int, 
        textData: String?
    ): Long {
        val epochMillis = msg.timestamp
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        val values = ContentValues().apply {
            put("chat_row_id", chatId)
            put("from_me", if (msg.isCurrentUser) 1 else 0)
            put("sender_jid_row_id", senderJidId)
            put("timestamp", epochMillis)
            put("message_type", messageType)
            put("text_data", textData)
            put("key_id", msg.senderName)
        }
        return db.insert("message", null, values)
    }

    private fun guessMediaType(content: String): Int {
        return when {
            content.contains("IMG-") -> 1
            content.contains("AUD-") || content.contains("PTT-") -> 2
            content.contains("VID-") -> 3
            content.contains("DOC-") -> 9
            content.contains("STK-") -> 20
            else -> 0
        }
    }

    private fun insertMediaItems(db: SQLiteDatabase, chatId: Long, items: List<LiteMediaItem>) {
        db.beginTransaction()
        try {
            for (item in items) {
                // Tenta di trovare il messaggio corrispondente per timestamp approssimativo (stesso secondo)
                val cursor = db.query(
                    "message", 
                    arrayOf("_id"), 
                    "chat_row_id = ? AND timestamp >= ? AND timestamp <= ? AND message_type != 0",
                    arrayOf(chatId.toString(), (item.timestamp - 1000).toString(), (item.timestamp + 1000).toString()),
                    null, null, "timestamp ASC", "1"
                )
                
                val messageId = cursor.use { if (it.moveToFirst()) it.getLong(0) else null }
                
                if (messageId != null) {
                    val values = ContentValues().apply {
                        put("message_row_id", messageId)
                        put("file_path", item.relativePath)
                        put("mime_type", getMimeType(item.type))
                        put("file_size", item.fileSize)
                        // Per gli sticker usiamo il nome file come pseudo-hash:
                        // consente a getTopStickers (GROUP BY file_hash) di raggruppare
                        // correttamente anche in Lite mode dove non abbiamo hash reali.
                        if (item.type == "STICKER") put("file_hash", item.fileName)
                    }
                    db.insertWithOnConflict("message_media", null, values, SQLiteDatabase.CONFLICT_REPLACE)
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun getMimeType(type: String): String {
        return when (type) {
            "IMAGE" -> "image/jpeg"
            "STICKER" -> "image/webp"
            "VIDEO" -> "video/mp4"
            "AUDIO" -> "audio/ogg"
            "DOCUMENT" -> "application/pdf"
            else -> "application/octet-stream"
        }
    }

    suspend fun deleteChat(chatId: Long) = withContext(Dispatchers.IO) {
        val db = liteDatabase.getOrCreate()
        val obsoleteMediaFolders = getChatMediaFolders(db, chatId)
        db.beginTransaction()
        try {
            db.delete("message_media", "message_row_id IN (SELECT _id FROM message WHERE chat_row_id = ?)", arrayOf(chatId.toString()))
            db.delete("message", "chat_row_id = ?", arrayOf(chatId.toString()))
            db.delete("chat", "_id = ?", arrayOf(chatId.toString()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        cleanupMediaFolders(obsoleteMediaFolders)
    }

    private fun updateChatSummary(
        db: SQLiteDatabase,
        chatId: Long,
        subject: String,
        importKey: String,
        isGroup: Boolean,
        lastMessageId: Long,
        lastMessageTimestamp: Long
    ) {
        val values = ContentValues().apply {
            put("subject", subject)
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

    private fun getChatMediaFolders(db: SQLiteDatabase, chatId: Long): Set<String> {
        val cursor = db.rawQuery(
            """
            SELECT mm.file_path
            FROM message_media mm
            JOIN message m ON m._id = mm.message_row_id
            WHERE m.chat_row_id = ?
            """.trimIndent(),
            arrayOf(chatId.toString())
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
        val obsoleteMediaFolders: Set<String>
    )
}
