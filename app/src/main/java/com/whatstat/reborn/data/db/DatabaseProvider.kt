package com.whatstat.reborn.data.db

import android.database.sqlite.SQLiteDatabase

interface DatabaseProvider {
    fun getMsgDb(): SQLiteDatabase
    fun getWaDb(): SQLiteDatabase?
    fun isOpen(): Boolean
}
