package com.whatstats.data.lite

import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

data class ParsedMessage(
    val timestamp: LocalDateTime,
    val senderName: String,
    val content: String,
    val isCurrentUser: Boolean,
    val isSystemMessage: Boolean = false,
    val linkCount: Int = 0,
    val emojiCount: Int = 0,
    val hasMedia: Boolean = false,
)

data class GroupEvent(
    val date: String,
    val description: String,
    val type: GroupEventType,
)

enum class GroupEventType {
    PARTICIPANT_JOIN,
    PARTICIPANT_LEAVE,
    NAME_CHANGE,
    ICON_CHANGE,
    GROUP_CREATE,
    UNKNOWN,
}

data class ParseResult(
    val messages: List<ParsedMessage>,
    val groupEvents: List<GroupEvent>,
)

@Singleton
class TextChatParser
    @Inject
    constructor() {
        // Regex per l'intestazione standard (Data, Ora - Mittente: Messaggio)
        // Supporta separatori comuni come / . - e formati 24h
        private val headerRegex = Regex("""^(\d{1,2}[/.,-]\d{1,2}[/.,-]\d{2,4})[,\s]+(\d{1,2}[:.]\d{2})\s+-\s+(.+?):(.*)$""")

        // Regex per eventi di sistema (senza i due punti dopo il mittente/azione)
        private val systemEventRegex = Regex("""^(\d{1,2}[/.,-]\d{1,2}[/.,-]\d{2,4})[,\s]+(\d{1,2}[:.]\d{2})\s+-\s+(.+)$""")

        // Rileva parole chiave negli eventi di gruppo (versione Italiana)
        private val groupActionRegex =
            Regex(
                """(.+)\s(ha aggiunto|ha rimosso|ha creato il gruppo|ha cambiato l'immagine del gruppo|" +
                    "ha abbandonato il gruppo|ha modificato il nome del gruppo|ha cambiato l'oggetto del gruppo)(.*)""",
            )

        private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yy, HH:mm")
        private val dateFormatterAlt = DateTimeFormatter.ofPattern("dd/MM/yyyy, HH:mm")

        fun parse(
            file: File,
            ownerName: String,
        ): ParseResult {
            val messages = mutableListOf<ParsedMessage>()
            val groupEvents = mutableListOf<GroupEvent>()
            var currentMsg: ParsedMessage? = null

            file.forEachLine { line ->
                val trimmedLine = line.trim()
                if (trimmedLine.isEmpty()) return@forEachLine

                val headerMatch = headerRegex.find(trimmedLine)
                if (headerMatch != null) {
                    // Nuovo messaggio trovato: salva il precedente
                    currentMsg?.let { messages.add(it) }

                    val (date, time, sender, body) = headerMatch.destructured
                    val timestamp = parseDateTime(date, time)

                    val hasMedia =
                        body.contains("omesso", ignoreCase = true) ||
                            body.contains("omessi", ignoreCase = true) ||
                            body.contains("allegato", ignoreCase = true) ||
                            body.contains("<Media", ignoreCase = true) ||
                            body.contains("PTE-", ignoreCase = true)

                    val links = extractLinkCount(body)
                    val emojis = extractEmojiCount(body)

                    val cleanSender = sender.replace("\u200E", "").trim()
                    val cleanBody =
                        body
                            .replace("\u200E", "") // Rimuove Left-to-Right Mark
                            .replace("(file allegato)", "")
                            .replace("<Media omesso>", "")
                            .trim()

                    // Manteniamo cleanBody anche per i media: il nome file (es. IMG-xxx.jpg) serve
                    // per associare il file fisico al mittente. La parola count già filtra hasMedia=true.
                    val finalContent = cleanBody

                    currentMsg =
                        ParsedMessage(
                            timestamp = timestamp,
                            senderName = cleanSender,
                            content = finalContent,
                            isCurrentUser = (cleanSender == ownerName),
                            isSystemMessage = hasMedia && finalContent.isEmpty(),
                            linkCount = links,
                            emojiCount = emojis,
                            hasMedia = hasMedia,
                        )
                } else {
                    // Potrebbe essere un evento di sistema o una riga di continuazione
                    val systemMatch = systemEventRegex.find(trimmedLine)
                    if (systemMatch != null) {
                        val (date, _, action) = systemMatch.destructured
                        val eventType = detectGroupEventType(action)
                        if (eventType != GroupEventType.UNKNOWN) {
                            currentMsg?.let { messages.add(it) }
                            currentMsg = null
                            groupEvents.add(GroupEvent(date, action.trim(), eventType))
                        } else {
                            // Se non è un evento noto, lo trattiamo come testo del messaggio precedente (raro)
                            appendToCurrent(currentMsg, trimmedLine)?.let { currentMsg = it }
                        }
                    } else {
                        // Riga di continuazione
                        appendToCurrent(currentMsg, trimmedLine)?.let { currentMsg = it }
                    }
                }
            }

            currentMsg?.let { messages.add(it) }
            return ParseResult(messages, groupEvents)
        }

        private fun appendToCurrent(
            current: ParsedMessage?,
            line: String,
        ): ParsedMessage? {
            val cleanLine = line.replace("\u200E", "").trim()
            return current?.copy(content = "${current.content}\n$cleanLine")
        }

        private fun parseDateTime(
            date: String,
            time: String,
        ): LocalDateTime {
            val cleanDate = date.replace(".", "/").replace("-", "/")
            val dateTimeStr = "$cleanDate, $time"
            return try {
                if (cleanDate.split("/").last().length == 4) {
                    LocalDateTime.parse(dateTimeStr, dateFormatterAlt)
                } else {
                    LocalDateTime.parse(dateTimeStr, dateFormatter)
                }
            } catch (e: Exception) {
                // Fallback: usa l'ora corrente se il parsing fallisce miseramente
                LocalDateTime.now()
            }
        }

        private fun detectGroupEventType(action: String): GroupEventType {
            val match = groupActionRegex.find(action) ?: return GroupEventType.UNKNOWN
            val phrase = match.groupValues[2].lowercase()

            return when {
                phrase.contains("aggiunto") -> GroupEventType.PARTICIPANT_JOIN
                phrase.contains("rimosso") || phrase.contains("abbandonato") -> GroupEventType.PARTICIPANT_LEAVE
                phrase.contains("creato") -> GroupEventType.GROUP_CREATE
                phrase.contains("immagine") -> GroupEventType.ICON_CHANGE
                phrase.contains("nome") || phrase.contains("oggetto") -> GroupEventType.NAME_CHANGE
                else -> GroupEventType.UNKNOWN
            }
        }

        private fun extractLinkCount(text: String): Int {
            val urlPattern = Regex("https?://[\\w\\d\\-.?=/&%]+")
            return urlPattern.findAll(text).count()
        }

        private fun extractEmojiCount(text: String): Int {
            var count = 0
            var i = 0
            while (i < text.length) {
                val codePoint = text.codePointAt(i)
                if (isEmoji(codePoint)) {
                    count++
                }
                i += Character.charCount(codePoint)
            }
            return count
        }

        private fun isEmoji(codePoint: Int): Boolean {
            return codePoint in 0x1F600..0x1F64F ||
                codePoint in 0x1F300..0x1F5FF ||
                codePoint in 0x1F680..0x1F6FF ||
                codePoint in 0x1F1E6..0x1F1FF ||
                codePoint in 0x2600..0x26FF ||
                codePoint in 0x2700..0x27BF ||
                codePoint in 0x1F900..0x1F9FF
        }
    }
