package com.whatstat.reborn.util

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DebugLogManager {
    private const val ENTRY_SEPARATOR = "\n<<<LOG_ENTRY>>>\n"

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs = _logs.asStateFlow()

    private val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    private var logFile: File? = null

    @Synchronized
    fun init(context: Context) {
        if (logFile != null) return
        logFile = File(context.filesDir, "whatstat_debug_logs.txt")
        val persistedLogs = runCatching {
            logFile
                ?.takeIf { it.exists() }
                ?.readText()
                ?.split(ENTRY_SEPARATOR)
                ?.filter { it.isNotBlank() }
                ?: emptyList()
        }.getOrDefault(emptyList())

        if (persistedLogs.isNotEmpty()) {
            _logs.value = persistedLogs
        }
    }

    @Synchronized
    fun log(message: String, throwable: Throwable? = null) {
        val timestamp = sdf.format(Date())
        val logLine = "[$timestamp] $message" + (throwable?.let { "\n${it.stackTraceToString()}" } ?: "")

        val current = _logs.value.toMutableList()
        current.add(0, logLine) // Più recenti in alto
        if (current.size > 200) current.removeAt(current.size - 1)
        _logs.value = current

        runCatching {
            logFile?.writeText(current.joinToString(ENTRY_SEPARATOR))
        }

        android.util.Log.d("WhatStatDebug", logLine)
    }

    @Synchronized
    fun clear() {
        _logs.value = emptyList()
        runCatching { logFile?.writeText("") }
    }
}
