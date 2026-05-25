package com.whatstats.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wrapper per i database SQLite di WhatsApp.
 *
 * Non usa Room perché stiamo leggendo un database esterno esistente
 * (non gestito dalla nostra app). Apriamo i file decifrati in READONLY.
 *
 * Due database:
 * - msgstore.db : messaggi, chat, JID
 * - wa.db       : contatti (nomi di rubrica)
 */
@Singleton
class WhatsAppDatabase
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : DatabaseProvider {
        private var msgDb: SQLiteDatabase? = null
        private var waDb: SQLiteDatabase? = null

        /**
         * Apre entrambi i database.
         * msgstore.db in READWRITE per creare indici di ottimizzazione.
         * wa.db in READONLY (non serve modificarlo).
         * I file devono già essere stati decifrati / estratti.
         */
        @Synchronized
        fun open(
            msgstoreFile: File,
            waDbFile: File,
        ) {
            close() // Chiudi eventuali connessioni precedenti

            msgDb =
                SQLiteDatabase.openDatabase(
                    msgstoreFile.absolutePath,
                    null,
                    SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.NO_LOCALIZED_COLLATORS,
                )
            // Abilita WAL per letture concorrenti (più thread possono leggere in parallelo)
            msgDb?.enableWriteAheadLogging()
            if (waDbFile.exists()) {
                waDb =
                    SQLiteDatabase.openDatabase(
                        waDbFile.absolutePath,
                        null,
                        SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS,
                    )
            }

            // Crea indici per ottimizzare le query
            createIndexes()
        }

        /**
         * Crea indici sulla copia locale di msgstore.db per velocizzare le query.
         * Usa IF NOT EXISTS per evitare errori a riaperture successive.
         */
        private fun createIndexes() {
            val db = msgDb ?: return
            try {
                // Indice composito principale: chat_row_id + message_type + timestamp
                // Copre: getMessageCountForChat, getHourlyDistribution, getDayOfWeekDistribution,
                //        getMonthlyTrend, getTopWords, getConversationStarters, ecc.
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_message_chat_type_ts ON message (chat_row_id, message_type, timestamp)")

                // Indice per from_me nelle query per mittente (pie chart, starters)
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_message_chat_type_from ON message (chat_row_id, message_type, from_me)")

                // Indice per sender_jid_row_id (JOINs nelle query per-sender)
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_message_sender_jid ON message (sender_jid_row_id)")

                // Indice per message_media (LEFT JOIN nelle query media/link)
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_message_media_row ON message_media (message_row_id)")

                // Indice per chat.jid_row_id (JOIN chat → jid)
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_chat_jid ON chat (jid_row_id)")

                // Indice per call_log (query chiamate)
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_call_log_jid ON call_log (jid_row_id)")

                // Indice per ricerca testo con LIKE (searchWordFrequency)
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS idx_message_chat_text ON message (chat_row_id, message_type) WHERE text_data IS NOT NULL",
                )

                android.util.Log.d("WhatStat", "Database indexes created successfully")
            } catch (e: Exception) {
                android.util.Log.w("WhatStat", "Failed to create some indexes: ${e.message}")
            }
        }

        /**
         * Chiude entrambi i database in modo sicuro.
         */
        @Synchronized
        fun close() {
            msgDb?.close()
            waDb?.close()
            msgDb = null
            waDb = null
        }

        /**
         * Ritorna il database messaggi (msgstore.db).
         * @throws IllegalStateException se il database non è stato aperto
         */
        override fun getMsgDb(): SQLiteDatabase =
            msgDb
                ?: throw IllegalStateException("Database msgstore.db non aperto. Chiama open() prima.")

        /**
         * Ritorna il database contatti (wa.db), o null se non disponibile.
         */
        override fun getWaDb(): SQLiteDatabase? = waDb

        /**
         * Verifica se il database principale (msgstore) è aperto e accessibile.
         * wa.db è opzionale (potrebbe non esistere su tutti i dispositivi).
         */
        override fun isOpen(): Boolean = msgDb?.isOpen == true
    }
