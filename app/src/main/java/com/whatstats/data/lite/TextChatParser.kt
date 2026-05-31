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
        private val iosHeaderRegex = Regex("""^\[(\d{1,2}[/.,-]\d{1,2}[/.,-]\d{2,4})[,\s]+(\d{1,2}[:.]\d{2})[:.\d]*\]\s+(.+?):(.*)$""")

        private val systemEventRegex = Regex("""^(\d{1,2}[/.,-]\d{1,2}[/.,-]\d{2,4})[,\s]+(\d{1,2}[:.]\d{2})\s+-\s+(.+)$""")
        private val iosSystemEventRegex = Regex("""^\[(\d{1,2}[/.,-]\d{1,2}[/.,-]\d{2,4})[,\s]+(\d{1,2}[:.]\d{2})[:.\d]*\]\s+(.+)$""")

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

        // iOS system events appear as "[date] SENDER: \u200Esystem_text" instead of a standalone line.
        // This map covers the second-person and passive forms that iOS uses instead of the third-person
        // phrases already handled by groupEventKeywords (Android format).
        private val iosSystemEventKeywords: Map<GroupEventType, List<String>> =
            mapOf(
                GroupEventType.NAME_CHANGE to
                    listOf(
                        // it — second person (SINNER AUSTRIACO DI ...: ‎Hai cambiato...)
                        "Hai cambiato il nome del gruppo",
                        "Hai cambiato la descrizione del gruppo",
                        // it — third person (Tu: Tu ha cambiato...) and others-perspective (Luigi: ‎Luigi ha cambiato...)
                        "ha cambiato il nome del gruppo",
                        "ha cambiato la descrizione del gruppo",
                        // en
                        "You changed the subject to",
                        "You changed the group name to",
                        "You changed the group's description",
                        "changed the group name to",
                        "changed the group's description",
                        "changed the group description",
                        // de
                        "Du hast den Betreff dieser Gruppe geändert",
                        "Du hast den Gruppennamen geändert",
                        "Du hast die Gruppenbeschreibung geändert",
                        "hat den Betreff dieser Gruppe geändert",
                        "hat den Gruppennamen geändert",
                        // es
                        "Cambiaste el nombre del grupo",
                        "Cambiaste el asunto del grupo",
                        "Cambiaste la descripción del grupo",
                        "cambió el nombre del grupo",
                        "cambió la descripción del grupo",
                        // fr
                        "Vous avez modifié le sujet du groupe",
                        "Vous avez modifié la description du groupe",
                        "a modifié la description du groupe",
                        // pt
                        "Você alterou o assunto do grupo",
                        "Você alterou a descrição do grupo",
                        "alterou a descrição do grupo",
                        // tr
                        "Grup konusunu değiştirdin",
                        "Grup açıklamasını değiştirdin",
                        "grup açıklamasını değiştirildi",
                        // hi
                        "आपने ग्रुप का नाम बदला",
                        "ग्रुप विवरण बदला",
                    ),
                GroupEventType.ICON_CHANGE to
                    listOf(
                        // it — second person
                        "Hai cambiato l'immagine del gruppo",
                        // it — third person (Tu: / others-perspective)
                        "ha cambiato l'immagine del gruppo",
                        // en
                        "You changed this group's icon",
                        "changed the group's icon",
                        // de
                        "Du hast das Gruppenbild geändert",
                        "hat das Gruppenbild geändert",
                        // es
                        "Cambiaste el ícono del grupo",
                        "cambió el ícono del grupo",
                        // fr
                        "Vous avez modifié l'icône du groupe",
                        "a modifié l'icône du groupe",
                        // pt
                        "Você alterou o ícone do grupo",
                        "alterou o ícone do grupo",
                        // tr
                        "Grup simgesini değiştirdin",
                        "grup simgesini değiştirdi",
                        // hi
                        "आपने ग्रुप की फ़ोटो बदली",
                        "ग्रुप फ़ोटो बदली",
                    ),
                GroupEventType.PARTICIPANT_LEAVE to
                    listOf(
                        // it
                        "Hai abbandonato",
                        "ti ha rimosso",
                        // en
                        "You left",
                        "removed you",
                        // de
                        "Du hast diese Gruppe verlassen",
                        "hat dich entfernt",
                        // es
                        "Saliste del grupo",
                        "te eliminó",
                        // fr
                        "Vous avez quitté le groupe",
                        "vous a supprimé",
                        // pt
                        "Você saiu do grupo",
                        "removeu você",
                        // tr
                        "Gruptan ayrıldın",
                        "seni çıkardı",
                        // hi
                        "आप ग्रुप से निकल गए",
                        "ने आपको हटाया",
                    ),
                GroupEventType.PARTICIPANT_JOIN to
                    listOf(
                        // it
                        "ti ha aggiunto",
                        "Hai aggiunto",
                        "fai parte del gruppo",
                        // en
                        "added you",
                        "You added",
                        "joined using",
                        "joined",
                        // de
                        "hat dich hinzugefügt",
                        "Du hast",
                        // es
                        "te añadió",
                        "Agregaste",
                        // fr
                        "vous a ajouté",
                        "Vous avez ajouté",
                        // pt
                        "adicionou você",
                        "Você adicionou",
                        // tr
                        "seni ekledi",
                        "Ekledin",
                        // hi
                        "ने आपको जोड़ा",
                        "आपने जोड़ा",
                    ),
                GroupEventType.GROUP_CREATE to
                    listOf(
                        // it
                        "Hai creato il gruppo",
                        // en
                        "You created group",
                        // de
                        "Du hast die Gruppe erstellt",
                        // es
                        "Creaste el grupo",
                        // fr
                        "Vous avez créé le groupe",
                        // pt
                        "Você criou o grupo",
                        // tr
                        "Grubu sen oluşturdun",
                        // hi
                        "आपने ग्रुप बनाया",
                    ),
            )

        private val iosSystemEventRegexMap: Map<GroupEventType, Regex> by lazy {
            iosSystemEventKeywords.mapValues { (_, keywords) ->
                val pattern =
                    keywords
                        .sortedByDescending { it.length }
                        .joinToString("|") { Regex.escape(it) }
                Regex(pattern, RegexOption.IGNORE_CASE)
            }
        }

        // Media-body keywords per locale. "<Media" and "PTE-" are locale-neutral.
        private val mediaKeywords: List<String> =
            listOf(
                // it
                "omesso", "omessi", "omessa", "allegato",
                // en
                "omitted", "attached",
                // de
                "weggelassen", "ausgelassen", "angehängt",
                // es
                "omitido", "omitida", "adjunto", "adjunta",
                // fr
                "omis", "omise", "joint", "jointe",
                // pt
                "oculta", "oculto", "ocultada", "ocultado", "anexo", "anexa",
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
                "(file allegato)", "immagine omessa", "video omesso", "audio omesso",
                "GIF omessa", "sticker omesso", "documento omesso",
                // en
                "(file attached)", "image omitted", "video omitted", "audio omitted",
                "GIF omitted", "sticker omitted", "document omitted",
                // de
                "(Datei angehängt)", "Bild weggelassen", "Video weggelassen", "Audio weggelassen",
                "GIF weggelassen", "Sticker weggelassen", "Dokument weggelassen", "Bild ausgelassen",
                // es
                "(archivo adjunto)", "imagen omitida", "video omitido", "audio omitido",
                "GIF omitida", "sticker omitido", "documento omitido",
                // fr
                "(fichier joint)", "image omise", "vidéo omise", "audio omis", "GIF omise",
                "sticker omis", "document omis",
                // pt
                "(arquivo anexado)", "imagem ocultada", "vídeo ocultado", "áudio ocultado",
                "GIF ocultado", "figurinha ocultada", "documento ocultado",
                // tr
                "(dosya eklendi)", "görüntü atlandı", "video atlandı", "ses atlandı",
                "GIF atlandı", "çıkartma atlandı", "belge atlandı",
                // hi
                "(फ़ाइल अटैच)", "छवि शामिल नहीं", "वीडियो शामिल नहीं", "ऑडियो शामिल नहीं",
                "GIF शामिल नहीं", "स्टिकर शामिल नहीं", "दस्तावेज़ शामिल नहीं",
            )

        private val iosSystemMessageKeywords: List<String> =
            listOf(
                // it
                "codice di sicurezza", "codice sicurezza", "chiamata persa", "videochiamata persa", "chiamata vocale",
                "crittografati end-to-end",
                // en
                "security code", "missed voice call", "missed video call", "missed call",
                "end-to-end encrypted",
                // de
                "Sicherheitsnummer", "Anruf verpasst", "Videoanruf verpasst", "Sprachanruf verpasst",
                "Ende-zu-Ende-verschlüsselt",
                // es
                "código de seguridad", "código seguridad", "llamada perdida", "videollamada perdida", "llamada de voz perdida",
                "cifrados de extremo a extremo",
                // fr
                "code de sécurité", "appel manqué", "appel vidéo manqué", "appel vocal manqué",
                "chiffrés de bout en bout",
                // pt
                "código de segurança", "chamada perdida", "chamada de vídeo perdida", "chamada de voz perdida",
                "criptografia de ponta a ponta",
                // tr
                "güvenlik kodu", "cevapsız arama", "cevapsız görüntülü arama", "cevapsız sesli arama",
                "uçtan uca şifrelidir",
                // hi
                "सुरक्षा कोड", "मिस कॉल", "मिस्ड कॉल",
                "एंड-टू-एंड एन्क्रिप्टेड",
            )

        private val iosSystemMessageRegex: Regex by lazy {
            val pattern =
                iosSystemMessageKeywords
                    .sortedByDescending { it.length }
                    .joinToString("|") { Regex.escape(it) }
            Regex(pattern, RegexOption.IGNORE_CASE)
        }

        private val mediaTagRegex = Regex("""<[^>\n]{2,100}>""")

        private val mediaPlaceholderRegex: Regex by lazy {
            val pattern =
                mediaPlaceholders
                    .sortedByDescending { it.length }
                    .joinToString("|") { Regex.escape(it) }
            Regex(pattern, RegexOption.IGNORE_CASE)
        }

        private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yy, HH:mm")
        private val dateFormatterAlt = DateTimeFormatter.ofPattern("dd/MM/yyyy, HH:mm")

        /**
         * Parses the chat export file into messages and group events.
         *
         * @param isIosFormat Set to true when the zip was exported from WhatsApp iOS.
         *   Enables iOS-specific parsing (square-bracket timestamps, LRM stripping, tagged
         *   attachment extraction). When false the original Android logic runs unmodified,
         *   guaranteeing zero regressions on existing imports.
         */
        fun parse(
            file: File,
            ownerName: String,
            isIosFormat: Boolean = false,
        ): ParseResult {
            val messages = mutableListOf<ParsedMessage>()
            val groupEvents = mutableListOf<GroupEvent>()
            var currentMsg: ParsedMessage? = null

            file.forEachLine { line ->
                // iOS prepends invisible LRM/RLM markers: strip them before any regex attempt.
                val trimmedLine =
                    if (isIosFormat) {
                        line.replace("\u200E", "").replace("\u200F", "").trim()
                    } else {
                        line.trim()
                    }
                if (trimmedLine.isEmpty()) return@forEachLine

                // Choose regex set based on export platform.
                val headerMatch =
                    if (isIosFormat) {
                        iosHeaderRegex.find(trimmedLine)
                    } else {
                        headerRegex.find(trimmedLine)
                    }

                if (headerMatch != null) {
                    currentMsg?.let { messages.add(it) }

                    val (date, time, sender, body) = headerMatch.destructured

                    // iOS system events arrive as "[date] SENDER: \u200Eevent_text".
                    // Detect and store them as GroupEvents before treating the line as a message.
                    if (isIosFormat) {
                        val cleanBodyForEvent = body.replace("\u200E", "").trim()
                        val iosEventType = detectIosSystemEvent(cleanBodyForEvent)
                        if (iosEventType != GroupEventType.UNKNOWN) {
                            currentMsg = null
                            groupEvents.add(GroupEvent(date, cleanBodyForEvent, iosEventType))
                            return@forEachLine
                        }
                    }

                    val timestamp = parseDateTime(date, time)
                    val hasMedia = mediaBodyRegex.containsMatchIn(body)
                    val links = extractLinkCount(body)
                    val emojis = extractEmojiCount(body)

                    val cleanSender = sender.replace("\u200E", "").trim()
                    val cleanBody = stripMediaPlaceholders(body, isIosFormat)

                    val isSystemEventText =
                        if (isIosFormat) {
                            iosSystemMessageRegex.containsMatchIn(cleanBody)
                        } else {
                            false
                        }

                    currentMsg =
                        ParsedMessage(
                            timestamp = timestamp,
                            senderName = cleanSender,
                            content = cleanBody,
                            isCurrentUser = (cleanSender == ownerName),
                            isSystemMessage = (hasMedia && cleanBody.isEmpty()) || isSystemEventText,
                            linkCount = links,
                            emojiCount = emojis,
                            hasMedia = hasMedia,
                        )
                } else {
                    val systemMatch =
                        if (isIosFormat) {
                            iosSystemEventRegex.find(trimmedLine)
                        } else {
                            systemEventRegex.find(trimmedLine)
                        }
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

        /**
         * Removes media placeholder text from a message body.
         *
         * Android path: leaves the body untouched except for stripping known placeholder
         * strings wrapped in angle-bracket tags (e.g. <Media omitted>).
         *
         * iOS path: first unwraps tagged attachments preserving the filename
         * (e.g. <allegato: 00000041-STICKER-...webp> -> 00000041-STICKER-...webp),
         * then removes bare locale-specific placeholders (e.g. "immagine omessa").
         */
        private fun stripMediaPlaceholders(
            text: String,
            isIosFormat: Boolean = false,
        ): String {
            if (!isIosFormat) {
                // Android: original logic, no changes.
                return text
                    .replace(mediaTagRegex) { match ->
                        if (mediaBodyRegex.containsMatchIn(match.value)) "" else match.value
                    }
                    .replace(mediaPlaceholderRegex, "")
                    .trim()
            }
            // iOS: strip LRM, unwrap <label: filename> preserving the filename, then
            // strip bare locale placeholders (e.g. "immagine omessa").
            return text
                .replace("\u200E", "")
                .replace(Regex("""<[^>:\n]+:\s*([^>\n]+)>""")) { match ->
                    match.groupValues[1]
                }
                .replace(mediaTagRegex) { match ->
                    if (mediaBodyRegex.containsMatchIn(match.value)) "" else match.value
                }
                .replace(mediaPlaceholderRegex, "")
                .trim()
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

        /**
         * Checks whether a message body (already stripped of LRM) from an iOS export
         * corresponds to a known system event.
         *
         * In iOS, system events are written as "[date] SENDER: \u200Eevent_text", so the event
         * text ends up in the body captured by iosHeaderRegex. This function matches that body
         * against iOS-specific second-person / passive phrases.
         */
        private fun detectIosSystemEvent(body: String): GroupEventType =
            iosSystemEventRegexMap.entries
                .firstOrNull { (_, regex) -> regex.containsMatchIn(body) }
                ?.key
                ?: GroupEventType.UNKNOWN

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
