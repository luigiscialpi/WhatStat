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
        private val headerRegex = Regex("""^(\d{1,2}[/.,-]\d{1,2}[/.,-]\d{2,4})[,\s]+(\d{1,2}[:.]\d{2})\s+-\s+(.+?):(.*)$""")

        private val systemEventRegex = Regex("""^(\d{1,2}[/.,-]\d{1,2}[/.,-]\d{2,4})[,\s]+(\d{1,2}[:.]\d{2})\s+-\s+(.+)$""")

        // Single source of truth for group-event keywords.
        // To add a language: append its keywords to the relevant list with a locale comment.
        private val groupEventKeywords: Map<GroupEventType, List<String>> = mapOf(
            GroupEventType.PARTICIPANT_JOIN to listOf(
                "ha aggiunto",              // it
                "added",                    // en
                "joined using",             // en
                "hat hinzugefügt",          // de
                "añadió",                   // es
                "a ajouté",                 // fr
                "adicionou",                // pt
                "ekledi",                   // tr
                "को जोड़ा",                   // hi
            ),
            GroupEventType.PARTICIPANT_LEAVE to listOf(
                "ha rimosso",               // it
                "ha abbandonato il gruppo", // it
                "removed",                  // en
                "left",                     // en
                "hat entfernt",             // de
                "hat die Gruppe verlassen", // de
                "eliminó",                  // es
                "salió del grupo",          // es
                "a supprimé",               // fr
                "a quitté le groupe",       // fr
                "removeu",                  // pt
                "saiu do grupo",            // pt
                "çıkardı",                  // tr
                "gruptan ayrıldı",          // tr
                "को हटाया",                  // hi
                "ने ग्रुप छोड़ दिया",         // hi
            ),
            GroupEventType.GROUP_CREATE to listOf(
                "ha creato il gruppo",      // it
                "created group",            // en
                "hat die Gruppe erstellt",  // de
                "creó el grupo",            // es
                "a créé le groupe",         // fr
                "criou o grupo",            // pt
                "grubu oluşturdu",          // tr
                "ने ग्रुप बनाया",             // hi
            ),
            GroupEventType.ICON_CHANGE to listOf(
                "ha cambiato l'immagine del gruppo",    // it
                "changed the group icon",               // en
                "hat das Gruppenbild geändert",         // de
                "cambió el ícono del grupo",            // es
                "a modifié l'icône du groupe",          // fr
                "alterou o ícone do grupo",             // pt
                "grup simgesini değiştirdi",            // tr
                "ने ग्रुप की फ़ोटो बदली",                // hi
            ),
            GroupEventType.NAME_CHANGE to listOf(
                "ha modificato il nome del gruppo",         // it
                "ha cambiato l'oggetto del gruppo",         // it
                "changed the group name",                   // en
                "changed the subject",                      // en
                "hat den Betreff dieser Gruppe geändert",  // de
                "cambió el asunto del grupo",               // es
                "a modifié le sujet du groupe",             // fr
                "alterou o assunto do grupo",               // pt
                "grup konusunu değiştirdi",                 // tr
                "ने ग्रुप का नाम बदला",                      // hi
            ),
        )

        // Built lazily from groupEventKeywords — no duplication
        private val groupActionRegex: Regex by lazy {
            val allKeywords = groupEventKeywords.values
                .flatten()
                .sortedByDescending { it.length }
                .joinToString("|") { Regex.escape(it) }
            Regex("""(.+?)\s+($allKeywords)(.*)""", setOf(RegexOption.IGNORE_CASE))
        }

        // Media-body keywords per locale. "<Media" and "PTE-" are locale-neutral.
        private val mediaKeywords: List<String> = listOf(
            "omesso", "omessi", "allegato",              // it
            "omitted", "attached",                       // en
            "weggelassen", "angehängt",                  // de
            "omitido", "omitida", "adjunto", "adjunta",  // es
            "omis", "joint", "jointe",                   // fr
            "oculta", "oculto", "anexo", "anexa",        // pt
            "ausgeschlossen",                            // de (alt)
            "atlandı", "eklendi",                        // tr
            "शामिल नहीं",                                  // hi
            "<Media", "PTE-",                            // universal
        )

        private val mediaBodyRegex: Regex by lazy {
            val pattern = mediaKeywords
                .sortedByDescending { it.length }
                .joinToString("|") { Regex.escape(it) }
            Regex(pattern, RegexOption.IGNORE_CASE)
        }

        // Parenthesised "file attached" placeholders per locale
        private val mediaPlaceholders: List<String> = listOf(
            "(file allegato)",    // it
            "(file attached)",    // en
            "(Datei angehängt)",  // de
            "(archivo adjunto)",  // es
            "(fichier joint)",    // fr
            "(arquivo anexado)",  // pt
            "(dosya eklendi)",    // tr
            "(फ़ाइल अटैच)",         // hi
        )

        private val mediaTagRegex = Regex("""<[^>\n]{2,50}>""")

        private val mediaPlaceholderRegex: Regex by lazy {
            val pattern = mediaPlaceholders
                .sortedByDescending { it.length }
                .joinToString("|") { Regex.escape(it) }
            Regex(pattern, RegexOption.IGNORE_CASE)
        }

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
                    currentMsg?.let { messages.add(it) }

                    val (date, time, sender, body) = headerMatch.destructured
                    val timestamp = parseDateTime(date, time)
                    val hasMedia = mediaBodyRegex.containsMatchIn(body)
                    val links = extractLinkCount(body)
                    val emojis = extractEmojiCount(body)

                    val cleanSender = sender.replace("\u200E", "").trim()
                    val cleanBody = stripMediaPlaceholders(body)

                    currentMsg =
                        ParsedMessage(
                            timestamp = timestamp,
                            senderName = cleanSender,
                            content = cleanBody,
                            isCurrentUser = (cleanSender == ownerName),
                            isSystemMessage = hasMedia && cleanBody.isEmpty(),
                            linkCount = links,
                            emojiCount = emojis,
                            hasMedia = hasMedia,
                        )
                } else {
                    val systemMatch = systemEventRegex.find(trimmedLine)
                    if (systemMatch != null) {
                        val (date, _, action) = systemMatch.destructured
                        val eventType = detectGroupEventType(action)
                        if (eventType != GroupEventType.UNKNOWN) {
                            currentMsg?.let { messages.add(it) }
                            currentMsg = null
                            groupEvents.add(GroupEvent(date, action.trim(), eventType))
                        } else {
                            appendToCurrent(currentMsg, trimmedLine)?.let { currentMsg = it }
                        }
                    } else {
                        appendToCurrent(currentMsg, trimmedLine)?.let { currentMsg = it }
                    }
                }
            }

            currentMsg?.let { messages.add(it) }
            return ParseResult(messages, groupEvents)
        }

        private fun stripMediaPlaceholders(text: String): String =
            text
                .replace("\u200E", "")
                .replace(mediaTagRegex) { match ->
                    if (mediaBodyRegex.containsMatchIn(match.value)) "" else match.value
                }
                .replace(mediaPlaceholderRegex, "")
                .trim()

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
                LocalDateTime.now()
            }
        }

        private fun detectGroupEventType(action: String): GroupEventType {
            val match = groupActionRegex.find(action) ?: return GroupEventType.UNKNOWN
            val matchedKeyword = match.groupValues[2].lowercase()
            return groupEventKeywords.entries
                .firstOrNull { (_, keywords) -> keywords.any { it.lowercase() == matchedKeyword } }
                ?.key
                ?: GroupEventType.UNKNOWN
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
