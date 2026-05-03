package com.whatstat.reborn.data.lite

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LiteMediaManager @Inject constructor() {

    /**
     * Scansiona la cartella dello ZIP e associa ogni file al mittente e al timestamp
     * del messaggio corrispondente, cercando il nome del file nel contenuto dei messaggi.
     */
    suspend fun extractMediaFromFolder(
        folder: File,
        messages: List<ParsedMessage> = emptyList()
    ): LiteMediaStats = withContext(Dispatchers.IO) {
        val allFiles = mutableListOf<File>()
        
        fun walk(file: File) {
            if (file.isDirectory) {
                file.listFiles()?.forEach { walk(it) }
            } else if (!file.name.endsWith(".txt") && !file.name.startsWith(".")) {
                allFiles.add(file)
            }
        }
        walk(folder)

        // Crea una mappa fileName → (senderName, epochMilli)
        // partendo dai messaggi che contengono un allegato.
        val fileNameToMsgInfo = mutableMapOf<String, Pair<String?, Long>>()
        for (msg in messages) {
            if (!msg.hasMedia || msg.content.isBlank()) continue
            val ts = try {
                msg.timestamp.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            } catch (e: Exception) { 0L }
            val matches: Sequence<MatchResult> = LiteMetricsCalculator.MEDIA_FILENAME_REGEX.findAll(msg.content)
            for (match in matches) {
                val fullName = match.value
                val nameNoExt = fullName.substringBeforeLast(".")
                fileNameToMsgInfo[fullName] = msg.senderName to ts
                fileNameToMsgInfo[nameNoExt] = msg.senderName to ts
            }
        }

        val mediaItems = allFiles.mapNotNull { file ->
            val type = when (file.extension.lowercase()) {
                "jpg", "jpeg", "png", "gif", "webp" -> "IMAGE"
                "mp4", "3gp", "mov", "m4v" -> "VIDEO"
                "opus", "aac", "mp3", "m4a", "wav" -> "AUDIO"
                "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "zip" -> "DOCUMENT"
                else -> null
            }

            if (type != null) {
                // Cerca prima col nome completo, poi senza estensione
                val msgInfo = fileNameToMsgInfo[file.name]
                    ?: fileNameToMsgInfo[file.nameWithoutExtension]
                LiteMediaItem(
                    fileName = file.name,
                    relativePath = file.absolutePath,
                    type = type,
                    timestamp = if (msgInfo != null && msgInfo.second > 0L) msgInfo.second else file.lastModified(),
                    senderName = msgInfo?.first
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
