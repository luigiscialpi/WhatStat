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
        private val groupEventKeywords: Map<GroupEventType, List<String>> =
            mapOf(
                GroupEventType.PARTICIPANT_JOIN to
                    listOf(
                        // it
                        "ha aggiunto",
                        // en
                        "added",
                        // en
                        "joined using",
                        // de
                        "hat hinzugefügt",
                        // es
                        "añadió",
                        // fr
                        "a ajouté",
                        // pt
                        "adicionou",
                        // tr
                        "ekledi",
                        // hi
                        "को जोड़ा",
                    ),
                GroupEventType.PARTICIPANT_LEAVE to
                    listOf(
                        // it
                        "ha rimosso",
                        // it
                        "ha abbandonato il gruppo",
                        // en
                        "removed",
                        // en
                        "left",
                        // de
                        "hat entfernt",
                        // de
                        "hat die Gruppe verlassen",
                        // es
                        "eliminó",
                        // es
                        "salió del grupo",
                        // fr
                        "a supprimé",
                        // fr
                        "a quitté le groupe",
                        // pt
                        "removeu",
                        // pt
                        "saiu do grupo",
                        // tr
                        "çıkardı",
                        // tr
                        "gruptan ayrıldı",
                        // hi
                        "को हटाया",
                        // hi
                        "ने ग्रुप छोड़ दिया",
                    ),
                GroupEventType.GROUP_CREATE to
                    listOf(
                        // it
                        "ha creato il gruppo",
                        // en
                        "created group",
                        // de
                        "hat die Gruppe erstellt",
                        // es
                        "creó el grupo",
                        // fr
                        "a créé le groupe",
                        // pt
                        "criou o grupo",
                        // tr
                        "grubu oluşturdu",
                        // hi
                        "ने ग्रुप बनाया",
                    ),
                GroupEventType.ICON_CHANGE to
                    listOf(
                        // it
                        "ha cambiato l'immagine del gruppo",
                        // en
                        "changed the group icon",
                        // de
                        "hat das Gruppenbild geändert",
                        // es
                        "cambió el ícono del grupo",
                        // fr
                        "a modifié l'icône du groupe",
                        // pt
                        "alterou o ícone do grupo",
                        // tr
                        "grup simgesini değiştirdi",
                        // hi
                        "ने ग्रुप की फ़ोटो बदली",
                    ),
                GroupEventType.NAME_CHANGE to
                    listOf(
                        // it
                        "ha modificato il nome del gruppo",
                        // it
                        "ha cambiato l'oggetto del gruppo",
                        // en
                        "changed the group name",
                        // en
                        "changed the subject",
                        // de
                        "hat den Betreff dieser Gruppe geändert",
                        // es
                        "cambió el asunto del grupo",
                        // fr
                        "a modifié le sujet du groupe",
                        // pt
                        "alterou o assunto do grupo",
                        // tr
                        "grup konusunu değiştirdi",
                        // hi
                        "ने ग्रुप का नाम बदला",
                    ),
            )

        // Built lazily from groupEventKeywords — no duplication
        private val groupActionRegex: Regex by lazy {
            val allKeywords =
                groupEventKeywords.values
                    .flatten()
                    .sortedByDescending { it.length }
                    .joinToString("|") { Regex.escape(it) }
            Regex("""(.+?)\s+($allKeywords)(.*)""", setOf(RegexOption.IGNORE_CASE))
        }

        // Media-body keywords per locale. "<Media" and "PTE-" are locale-neutral.
        private val mediaKeywords: List<String> =
            listOf(
                // it
                "omesso", "omessi", "allegato",
                // en
                "omitted", "attached",
                // de
                "weggelassen", "angehängt",
                // es
                "omitido", "omitida", "adjunto", "adjunta",
                // fr
                "omis", "joint", "jointe",
                // pt
                "oculta", "oculto", "anexo", "anexa",
                // de (alt)
                "ausgeschlossen",
                // tr
                "atlandı", "eklendi",
                // hi
                "शामिल नहीं",
                // universal
                "<Media", "PTE-",
            )

        private val mediaBodyRegex: Regex by lazy {
            val pattern =
                mediaKeywords
                    .sortedByDescending { it.length }
                    .joinToString("|") { Regex.escape(it) }
            Regex(pattern, RegexOption.IGNORE_CASE)
        }

        // Parenthesised "file attached" placeholders per locale
        private val mediaPlaceholders: List<String> =
            listOf(
                // it
                "(file allegato)",
                // en
                "(file attached)",
                // de
                "(Datei angehängt)",
                // es
                "(archivo adjunto)",
                // fr
                "(fichier joint)",
                // pt
                "(arquivo anexado)",
                // tr
                "(dosya eklendi)",
                // hi
                "(फ़ाइल अटैच)",
            )

        private val mediaTagRegex = Regex("""<[^>\n]{2,50}>""")

        private val mediaPlaceholderRegex: Regex by lazy {
            val pattern =
                mediaPlaceholders
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
