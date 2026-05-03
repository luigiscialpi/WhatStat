package com.whatstat.reborn.data.lite

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LiteMediaManager @Inject constructor() {

    suspend fun extractMediaFromFolder(folder: File): LiteMediaStats = withContext(Dispatchers.IO) {
        val allFiles = mutableListOf<File>()
        
        fun walk(file: File) {
            if (file.isDirectory) {
                file.listFiles()?.forEach { walk(it) }
            } else if (!file.name.endsWith(".txt") && !file.name.startsWith(".")) {
                allFiles.add(file)
            }
        }
        walk(folder)
        
        val mediaItems = allFiles.mapNotNull { file ->
            val type = when (file.extension.lowercase()) {
                "jpg", "jpeg", "png", "gif", "webp" -> "IMAGE"
                "mp4", "3gp", "mov", "m4v" -> "VIDEO"
                "opus", "aac", "mp3", "m4a", "wav" -> "AUDIO"
                "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "zip" -> "DOCUMENT"
                else -> null
            }
            
            if (type != null) {
                LiteMediaItem(
                    fileName = file.name,
                    relativePath = file.absolutePath, // Usiamo path assoluto per Coil/Player
                    type = type,
                    timestamp = file.lastModified() // Fallback timestamp
                )
            } else null
        }

        LiteMediaStats(
            totalCount = mediaItems.size,
            images = mediaItems.count { it.type == "IMAGE" },
            videos = mediaItems.count { it.type == "VIDEO" },
            audioFiles = mediaItems.count { it.type == "AUDIO" },
            documents = mediaItems.count { it.type == "DOCUMENT" },
            mediaItems = mediaItems.sortedByDescending { it.timestamp }
        )
    }
}
