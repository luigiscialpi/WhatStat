package com.whatstat.reborn.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DebugLogManager {
    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs = _logs.asStateFlow()

    private val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    fun log(message: String, throwable: Throwable? = null) {
        val timestamp = sdf.format(Date())
        val logLine = "[$timestamp] $message" + (throwable?.let { "\n${it.stackTraceToString()}" } ?: "")
        
        val current = _logs.value.toMutableList()
        current.add(0, logLine) // Più recenti in alto
        if (current.size > 200) current.removeAt(current.size - 1)
        _logs.value = current
        
        android.util.Log.d("WhatStatDebug", logLine)
    }

    fun clear() {
        _logs.value = emptyList()
    }
}
