package com.whatstats.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.whatstats.data.lite.LiteDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActiveDatabaseProvider
    @Inject
    constructor(
        private val whatsAppDatabase: WhatsAppDatabase,
        private val liteDatabase: LiteDatabase,
        @ApplicationContext private val context: Context,
    ) : DatabaseProvider {
        private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        var isLiteMode: Boolean
            get() = prefs.getBoolean(KEY_IS_LITE_MODE, false)
            set(value) = prefs.edit().putBoolean(KEY_IS_LITE_MODE, value).apply()

        override fun getMsgDb(): SQLiteDatabase {
            return if (isLiteMode) liteDatabase.getOrCreate() else whatsAppDatabase.getMsgDb()
        }

        override fun getWaDb(): SQLiteDatabase? {
            return if (isLiteMode) liteDatabase.getWaDb() else whatsAppDatabase.getWaDb()
        }

        override fun isOpen(): Boolean {
            return if (isLiteMode) true else whatsAppDatabase.isOpen()
        }

        companion object {
            const val PREFS_NAME = "whatstat_lite_prefs"
            const val KEY_IS_LITE_MODE = "is_lite_mode"
            const val ROOT_DB_NAME = "msgstore.db"
        }
    }
