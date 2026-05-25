package com.whatstats.data.db

import android.database.sqlite.SQLiteDatabase
import com.whatstats.data.lite.LiteDatabase
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActiveDatabaseProvider
    @Inject
    constructor(
        private val whatsAppDatabase: WhatsAppDatabase,
        private val liteDatabase: LiteDatabase,
        @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    ) : DatabaseProvider {
        private val prefs = context.getSharedPreferences("whatstat_lite_prefs", android.content.Context.MODE_PRIVATE)

        var isLiteMode: Boolean
            get() = prefs.getBoolean("is_lite_mode", false)
            set(value) = prefs.edit().putBoolean("is_lite_mode", value).apply()

        override fun getMsgDb(): android.database.sqlite.SQLiteDatabase {
            return if (isLiteMode) liteDatabase.getOrCreate() else whatsAppDatabase.getMsgDb()
        }

        override fun getWaDb(): android.database.sqlite.SQLiteDatabase? {
            return if (isLiteMode) liteDatabase.getWaDb() else whatsAppDatabase.getWaDb()
        }

        override fun isOpen(): Boolean {
            return if (isLiteMode) true else whatsAppDatabase.isOpen()
        }
    }
