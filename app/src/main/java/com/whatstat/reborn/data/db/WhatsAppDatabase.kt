package com.whatstat.reborn.data.db

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
class WhatsAppDatabase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var msgDb: SQLiteDatabase? = null
    private var waDb: SQLiteDatabase? = null

    /**
     * Apre entrambi i database in modalità read-only.
     * I file devono già essere stati decifrati / estratti.
     */
    @Synchronized
    fun open(msgstoreFile: File, waDbFile: File) {
        close() // Chiudi eventuali connessioni precedenti

        msgDb = SQLiteDatabase.openDatabase(
            msgstoreFile.absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
        )
        waDb = SQLiteDatabase.openDatabase(
            waDbFile.absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
        )
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
    fun getMsgDb(): SQLiteDatabase = msgDb
        ?: throw IllegalStateException("Database msgstore.db non aperto. Chiama open() prima.")

    /**
     * Ritorna il database contatti (wa.db).
     * @throws IllegalStateException se il database non è stato aperto
     */
    fun getWaDb(): SQLiteDatabase = waDb
        ?: throw IllegalStateException("Database wa.db non aperto. Chiama open() prima.")

    /**
     * Verifica se i database sono aperti e accessibili.
     */
    fun isOpen(): Boolean = msgDb?.isOpen == true && waDb?.isOpen == true
}
