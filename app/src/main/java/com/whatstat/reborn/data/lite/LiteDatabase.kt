package com.whatstat.reborn.data.lite

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import com.whatstat.reborn.util.DebugLogManager
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LiteDatabase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var db: SQLiteDatabase? = null
    private var waDb: SQLiteDatabase? = null

    val filesDir: File get() = context.filesDir
    val cacheDir: File get() = context.cacheDir
    val contentResolver get() = context.contentResolver

    fun getOrCreate(): SQLiteDatabase {
        if (db?.isOpen == true) return db!!
        val file = File(context.filesDir, "lite_msgstore.db")
        db = SQLiteDatabase.openOrCreateDatabase(file, null)
        db!!.enableWriteAheadLogging()
        createSchema(db!!)
        return db!!
    }

    fun getWaDb(): SQLiteDatabase {
        if (waDb?.isOpen == true) return waDb!!
        val file = File(context.filesDir, "lite_wa.db")
        waDb = SQLiteDatabase.openOrCreateDatabase(file, null)
        waDb!!.execSQL("CREATE TABLE IF NOT EXISTS wa_contacts (_id INTEGER PRIMARY KEY, jid TEXT UNIQUE, display_name TEXT)")
        return waDb!!
    }

    private fun createSchema(db: SQLiteDatabase) {
        // Tabella jid
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS jid (
                _id         INTEGER PRIMARY KEY AUTOINCREMENT,
                raw_string  TEXT NOT NULL UNIQUE,
                server      TEXT NOT NULL DEFAULT 's.whatsapp.net',
                user        TEXT
            )
        """.trimIndent())

        // Tabella chat
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS chat (
                _id                 INTEGER PRIMARY KEY AUTOINCREMENT,
                jid_row_id          INTEGER UNIQUE,
                import_key          TEXT,
                hidden              INTEGER DEFAULT 0,
                subject             TEXT,
                sort_timestamp      INTEGER,
                display_message_row_id INTEGER,
                last_message_row_id    INTEGER,
                unseen_message_count   INTEGER DEFAULT 0,
                group_type          INTEGER DEFAULT 0
            )
        """.trimIndent())

        ensureColumnExists(db, "chat", "import_key", "TEXT")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_chat_import_key ON chat (import_key)")

        // Tabella message
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS message (
                _id                 INTEGER PRIMARY KEY AUTOINCREMENT,
                chat_row_id         INTEGER NOT NULL,
                from_me             INTEGER NOT NULL DEFAULT 0,
                sender_jid_row_id   INTEGER DEFAULT 0,
                timestamp           INTEGER NOT NULL,
                message_type        INTEGER NOT NULL DEFAULT 0,
                text_data           TEXT,
                key_id              TEXT,
                received_timestamp  INTEGER DEFAULT 0,
                receipt_server_timestamp INTEGER DEFAULT 0
            )
        """.trimIndent())

        // Tabella message_media
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS message_media (
                message_row_id  INTEGER PRIMARY KEY,
                file_path       TEXT,
                mime_type       TEXT,
                file_size       INTEGER DEFAULT 0,
                file_hash       TEXT
            )
        """.trimIndent())

        // Stub: jid_map
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS jid_map (
                _id         INTEGER PRIMARY KEY AUTOINCREMENT,
                lid_row_id  INTEGER,
                jid_row_id  INTEGER
            )
        """.trimIndent())

        // Stub: addon_message_media
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS addon_message_media (
                _id                  INTEGER PRIMARY KEY AUTOINCREMENT,
                message_row_id       INTEGER NOT NULL,
                addon_message_index  INTEGER NOT NULL,
                chat_row_id          INTEGER,
                file_path            TEXT,
                mime_type            TEXT,
                file_size            INTEGER DEFAULT 0
            )
        """.trimIndent())

        // Indici per performance e compatibilità query root
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_message_chat_type_ts ON message (chat_row_id, message_type, timestamp)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_message_chat_type_from ON message (chat_row_id, message_type, from_me)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_message_sender_jid ON message (sender_jid_row_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_message_media_row ON message_media (message_row_id)")

        // Stub tables per evitare crash DAO root
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS call_log (
                _id INTEGER PRIMARY KEY, 
                jid_row_id INTEGER, 
                from_me INTEGER, 
                timestamp INTEGER, 
                video_call INTEGER, 
                duration INTEGER, 
                call_result INTEGER, 
                group_jid_row_id INTEGER DEFAULT 0
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS call_log_participant_v2 (
                _id INTEGER PRIMARY KEY, 
                call_log_row_id INTEGER, 
                jid_row_id INTEGER, 
                call_result INTEGER
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS message_system (
                message_row_id INTEGER PRIMARY KEY, 
                action_type INTEGER, 
                is_me_joined INTEGER DEFAULT 0
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS message_system_chat_participant (
                message_row_id INTEGER, 
                user_jid_row_id INTEGER
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS message_system_value_change (
                message_row_id INTEGER PRIMARY KEY, 
                old_data TEXT, 
                new_data TEXT
            )
        """.trimIndent())
    }

    private fun ensureColumnExists(
        db: SQLiteDatabase,
        tableName: String,
        columnName: String,
        definition: String
    ) {
        val columnExists = db.rawQuery("PRAGMA table_info($tableName)", null).use { cursor ->
            var found = false
            while (cursor.moveToNext()) {
                if (cursor.getString(cursor.getColumnIndexOrThrow("name")) == columnName) {
                    found = true
                    break
                }
            }
            found
        }

        if (!columnExists) {
            db.execSQL("ALTER TABLE $tableName ADD COLUMN $columnName $definition")
        }
    }

    fun hasChatData(): Boolean {
        val db = getOrCreate()
        val cursor = db.rawQuery("SELECT COUNT(*) FROM chat LIMIT 1", null)
        return cursor.use {
            if (it.moveToFirst()) it.getInt(0) > 0 else false
        }
    }

    fun close() {
        db?.close()
        db = null
        waDb?.close()
        waDb = null
    }

    fun deleteAll() {
        DebugLogManager.log("LiteDatabase.deleteAll invoked | dbExists=${File(context.filesDir, "lite_msgstore.db").exists()} waExists=${File(context.filesDir, "lite_wa.db").exists()}")
        close()
        File(context.filesDir, "lite_msgstore.db").delete()
        File(context.filesDir, "lite_wa.db").delete()
    }
}
