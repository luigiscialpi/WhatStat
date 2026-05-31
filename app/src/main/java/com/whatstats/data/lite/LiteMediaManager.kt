package com.whatstats.data.lite

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.ZoneId
import java.util.zip.ZipFile
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LiteMediaManager
    @Inject
    constructor() {
        companion object {
            /** Pattern per i nomi file allegati WhatsApp (es. IMG-20231015-WA0001.jpg, STK-20250709-WA0005.webp, 00000041-STICKER-2022-12-01-14-16-17.webp). */
            private val MEDIA_FILENAME_REGEX =
                Regex(
                    "(?:(?:IMG|VID|AUD|PTT|DOC|STK)-\\d{8}-WA\\d{4,}|" +
                        "\\d{8}-[A-Z]+-\\d{4}-\\d{2}-\\d{2}-\\d{2}-\\d{2}-\\d{2})(?:\\.[a-z0-9]+)?",
                    RegexOption.IGNORE_CASE,
                )
        }

        /**
         * Scansiona la cartella dello ZIP e associa ogni file al mittente e al timestamp
         * del messaggio corrispondente, cercando il nome del file nel contenuto dei messaggi.
         */
        suspend fun extractMediaFromFolder(
            folder: File,
            messages: List<ParsedMessage> = emptyList(),
        ): LiteMediaStats =
            withContext(Dispatchers.IO) {
                // Crea una mappa fileName → (senderName, epochMilli)
                // partendo dai messaggi che contengono un allegato.
                val fileNameToMsgInfo = mutableMapOf<String, Pair<String?, Long>>()
                for (msg in messages) {
                    if (!msg.hasMedia || msg.content.isBlank()) continue
                    val ts =
                        try {
                            msg.timestamp.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        } catch (e: Exception) {
                            0L
                        }
                    val matches: Sequence<MatchResult> = MEDIA_FILENAME_REGEX.findAll(msg.content)
                    for (match in matches) {
                        val fullName = match.value
                        val nameNoExt = fullName.substringBeforeLast(".")
                        fileNameToMsgInfo[fullName] = msg.senderName to ts
                        fileNameToMsgInfo[nameNoExt] = msg.senderName to ts
                    }
                }

                val sourceZip = File(folder, "source.zip")
                if (!sourceZip.exists()) {
                    return@withContext LiteMediaStats(0, 0, 0, 0, 0, emptyList())
                }

                val mediaItems =
                    ZipFile(sourceZip).use { zip ->
                        zip.entries().asSequence()
                            .filter { !it.isDirectory && !it.name.endsWith(".txt") && !it.name.startsWith(".") }
                            .mapNotNull { entry ->
                                val entryName = entry.name.substringAfterLast("/")
                                val extension = entryName.substringAfterLast(".", "").lowercase()
                                if (entryName == "source.zip") return@mapNotNull null

                                val type =
                                    when (extension) {
                                        // I file STK-*.webp o che contengono "STICKER" sono sticker, gli altri .webp sono immagini normali
                                        "webp" ->
                                            if (entryName.startsWith("STK-", ignoreCase = true) ||
                                                entryName.contains("STICKER", ignoreCase = true)
                                            ) {
                                                "STICKER"
                                            } else {
                                                "IMAGE"
                                            }
                                        "jpg", "jpeg", "png" -> "IMAGE"
                                        "gif", "mp4", "3gp", "mov", "m4v" -> "VIDEO"
                                        "opus", "aac", "mp3", "m4a", "wav" -> "AUDIO"
                                        "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "zip" -> "DOCUMENT"
                                        else -> null
                                    }

                                if (type != null) {
                                    // Cerca prima col nome completo, poi senza estensione
                                    val msgInfo =
                                        fileNameToMsgInfo[entryName]
                                            ?: fileNameToMsgInfo[entryName.substringBeforeLast(".")]

                                    val isVideoOrAudio = type in setOf("VIDEO", "AUDIO")
                                    val relativePath =
                                        if (isVideoOrAudio) {
                                            "${folder.name}/${entry.name}"
                                        } else {
                                            "zip://${folder.name}/source.zip|${entry.name}"
                                        }

                                    LiteMediaItem(
                                        fileName = entryName,
                                        relativePath = relativePath,
                                        type = type,
                                        timestamp = if (msgInfo != null && msgInfo.second > 0L) msgInfo.second else entry.time,
                                        senderName = msgInfo?.first,
                                        fileSize = entry.size,
                                    )
                                } else {
                                    null
                                }
                            }.toList()
                    }

                LiteMediaStats(
                    totalCount = mediaItems.size,
                    images = mediaItems.count { it.type == "IMAGE" },
                    videos = mediaItems.count { it.type == "VIDEO" },
                    audioFiles = mediaItems.count { it.type == "AUDIO" },
                    documents = mediaItems.count { it.type == "DOCUMENT" },
                    mediaItems = mediaItems.sortedByDescending { it.timestamp },
                )
            }
    }

data class LiteMediaItem(
    val fileName: String,
    val relativePath: String,
    val type: String,
    val timestamp: Long,
    val senderName: String?,
    val fileSize: Long = 0L,
)

data class LiteMediaStats(
    val totalCount: Int,
    val images: Int,
    val videos: Int,
    val audioFiles: Int,
    val documents: Int,
    val mediaItems: List<LiteMediaItem>,
)
