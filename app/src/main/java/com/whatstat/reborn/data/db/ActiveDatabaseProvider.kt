package com.whatstat.reborn.data.db

import android.database.sqlite.SQLiteDatabase
import com.whatstat.reborn.data.lite.LiteDatabase
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActiveDatabaseProvider @Inject constructor(
    private val whatsAppDatabase: WhatsAppDatabase,
    private val liteDatabase: LiteDatabase
) : DatabaseProvider {
    
    @Volatile
    var isLiteMode: Boolean = false

    override fun getMsgDb(): SQLiteDatabase {
        return if (isLiteMode) liteDatabase.getOrCreate() else whatsAppDatabase.getMsgDb()
    }

    override fun getWaDb(): SQLiteDatabase {
        return if (isLiteMode) liteDatabase.getWaDb() else whatsAppDatabase.getWaDb()
    }

    override fun isOpen(): Boolean {
        // Se in LiteMode diamo per scontato che sia aperto o lo apriamo on-demand.
        return if (isLiteMode) true else whatsAppDatabase.isOpen()
    }
}
