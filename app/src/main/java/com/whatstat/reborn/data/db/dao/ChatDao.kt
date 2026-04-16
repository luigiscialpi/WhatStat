package com.whatstat.reborn.data.db.dao

import com.whatstat.reborn.data.db.WhatsAppDatabase
import com.whatstat.reborn.data.db.model.ChatBasicInfo
import com.whatstat.reborn.data.db.model.ChatListItem
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DAO per le query sulle chat.
 * Legge dalla tabella `chat` e `jid` del database msgstore.db.
 */
@Singleton
class ChatDao @Inject constructor(
    private val database: WhatsAppDatabase
) {

    /** Conta le chat totali. */
    fun getChatCount(): Int {
        val cursor = database.getMsgDb().rawQuery(
            "SELECT COUNT(*) FROM chat WHERE hidden = 0 OR hidden IS NULL", null
        )
        return cursor.use { if (it.moveToFirst()) it.getInt(0) else 0 }
    }

    /** Ritorna le info base di una chat. */
    fun getChatBasicInfo(chatId: Long): ChatBasicInfo {
        val query = """
            SELECT
                c._id AS chat_id,
                j.raw_string AS jid,
                c.subject AS name,
                CASE WHEN j.server = 'g.us' THEN 1 ELSE 0 END AS is_group,
                c.subject
            FROM chat c
            JOIN jid j ON c.jid_row_id = j._id
            WHERE c._id = ?
        """.trimIndent()

        val cursor = database.getMsgDb().rawQuery(query, arrayOf(chatId.toString()))
        return cursor.use {
            if (it.moveToFirst()) ChatBasicInfo(
                chatId  = it.getLong(it.getColumnIndexOrThrow("chat_id")),
                jid     = it.getString(it.getColumnIndexOrThrow("jid")) ?: "",
                name    = it.getString(it.getColumnIndexOrThrow("name")),
                isGroup = it.getInt(it.getColumnIndexOrThrow("is_group")) == 1,
                subject = it.getString(it.getColumnIndexOrThrow("subject"))
            ) else ChatBasicInfo(chatId, "", null, false, null)
        }
    }

    /** Ritorna il nome della chat più attiva (più messaggi) filtrata per data. */
    fun getMostActiveChat(startTime: Long? = null, endTime: Long? = null): String {
        val queryBuilder = StringBuilder("""
            SELECT j.raw_string AS jid
            FROM chat c
            JOIN jid j ON c.jid_row_id = j._id
            JOIN message m ON m.chat_row_id = c._id
            WHERE m.message_type = 0
        """.trimIndent())
        val args = mutableListOf<String>()
        
        if (startTime != null) {
            queryBuilder.append(" AND m.timestamp >= ?")
            args.add(startTime.toString())
        }
        if (endTime != null) {
            queryBuilder.append(" AND m.timestamp <= ?")
            args.add(endTime.toString())
        }
        
        queryBuilder.append(" GROUP BY c._id ORDER BY COUNT(*) DESC LIMIT 1")

        val cursor = database.getMsgDb().rawQuery(queryBuilder.toString(), if (args.isEmpty()) null else args.toTypedArray())
        return cursor.use { if (it.moveToFirst()) it.getString(0) ?: "Sconosciuto" else "Nessuna" }
    }

    /**
     * Ritorna la lista di tutte le chat con info per il ranking.
     * Include il nome visualizzato (da jid.user, chat.subject, o wa.db).
     */
    fun getChatList(): List<ChatListItem> {
        val query = """
            SELECT
                c._id AS chat_id,
                j.raw_string AS jid,
                COALESCE(c.subject, j.raw_string) AS display_name,
                CASE WHEN j.server = 'g.us' THEN 1 ELSE 0 END AS is_group,
                COUNT(m._id) AS total_messages,
                MAX(m.timestamp) AS last_message_ts
            FROM chat c
            JOIN jid j ON c.jid_row_id = j._id
            LEFT JOIN message m ON m.chat_row_id = c._id AND m.message_type = 0
            WHERE (c.hidden = 0 OR c.hidden IS NULL)
            GROUP BY c._id
            HAVING total_messages > 0
            ORDER BY total_messages DESC
        """.trimIndent()

        val cursor = database.getMsgDb().rawQuery(query, null)
        return cursor.use {
            buildList {
                while (it.moveToNext()) {
                    add(ChatListItem(
                        chatId              = it.getLong(it.getColumnIndexOrThrow("chat_id")),
                        jid                 = it.getString(it.getColumnIndexOrThrow("jid")) ?: "",
                        displayName         = it.getString(it.getColumnIndexOrThrow("display_name")) ?: "Sconosciuto",
                        isGroup             = it.getInt(it.getColumnIndexOrThrow("is_group")) == 1,
                        totalMessages       = it.getInt(it.getColumnIndexOrThrow("total_messages")),
                        lastMessageTimestamp = it.getLong(it.getColumnIndexOrThrow("last_message_ts"))
                    ))
                }
            }
        }
    }
}
