# Lite Mode (Non-Root) - Piano di Sviluppo

**Data:** 2026-05-03  
**Obiettivo:** Fornire un'esperienza completa agli utenti senza root, permettendo l'analisi statistiche e visive di WhatsApp tramite l'esportazione manuale della chat (formato `.zip` contenente `.txt` e media associati).

---

## 1. Contesto e Limitazioni Critiche
- **Target:** Utenti non-root (esperienza "Lite").
- **Formato Dati:** WhatsApp genera SEMPRE un archivio **`ChatExport_YYYY-MM-DD.zip`**. All'interno c'è il file di testo e i file multimediali associati (`.jpg`, `.opus`, `.pdf`, ecc.).
- **Dati non disponibili:** Crittografia E2E, database interno (`wa.db`).
- **Dati disponibili:** Testo, timestamp, mittenti, file multimediali, eventi di gruppo scritti nel testo.
- **Vincoli Utente:** Internazionalizzazione formato data, performance su chat grandi (Richiede Caching JSON e Delta Parsing).

---

## 2. Flusso Utente (UX/UI)

### 2.1. Schermata Principale
Se l'app rileva **Nessun Root**: nasconde sezioni non compatibili e mostra la CTA:
**[ 🔍 IMPORTA CHAT DA ZIP ]**

### 2.2. Importazione (SAF)
L'utente seleziona il file ZIP tramite `Intent.ACTION_GET_CONTENT`. Nessun permesso permanente richiesto.

### 2.3. Setup Utente
1. L'app estrae lo ZIP in `cacheDir/lite_chat_TIMESTAMP/`.
2. Analizza superficialmente il `.txt` per trovare i mittenti (es. `["Giovanni", "Papà"]`).
3. Mostra la schermata di scelta: *"Quale sei tu?"*. L'utente seleziona. Scelta salvata in locale.

### 2.4. Parsing, Media Extraction & Dashboard
- **Prima volta:** Viene eseguito il parsing del testo + scansione file multimediali + estrazione eventi di gruppo. Il risultato viene serializzato in JSON (`lite_chat_cache.json`).
- **Cache Hit:** Se l'utente riapre l'app e il file non è cambiato, carica la cache JSON (Istantaneo. Supporta anche il Delta Parsing: legge solo le righe nuove se il file è stato modificato).
- **UI:** Mostra la Dashboard completa, incluse le nuove schede **Media** ed **Eventi Gruppo**.

---

## 3. Architettura Tecnica (Cosa Modificare)

### 3.1. Cache e Delta Update
La cache non deve salvare solo i conteggi, ma anche lo stato del file per il delta.

```kotlin
data class LiteCacheData(
    val sourceFileName: String,
    val lastModified: Long,
    val totalLines: Int, // Per il Delta Parsing
    val ownerName: String,
    val globalStats: GlobalStats
)

class LiteCacheRepository(private val context: Context) {
    private val cacheFile = File(context.cacheDir, "lite_chat_cache.json")
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun saveCache(data: LiteCacheData) { ... }
    suspend fun loadCache(): LiteCacheData? { ... }
    
    // Logica Delta: Carica i vecchi messaggi JSON e accoda solo le righe nuove
    suspend fun getCachedMessages(): List<ParsedMessage> { ... } 
}
```

### 3.2. FileExtractor: Estrazione Completa
Deve estrarre tutto, non solo il testo, per l'analisi media successiva.

```kotlin
// In FileExtractor.kt
suspend fun extractFullChatFolder(zipUri: Uri, context: Context): Result<File> = withContext(Dispatchers.IO) {
    runCatching {
        val inputStream = context.contentResolver.openInputStream(zipUri) ?: throw IOException("File non valido")
        val destFolder = File(context.cacheDir, "lite_chat_${System.currentTimeMillis()}")
        destFolder.mkdirs()

        ZipFile(inputStream.use { ZipInputStream(it) }.channel).use { zip ->
            zip.entries().toList().forEach { entry ->
                if (!entry.isDirectory) {
                    val outFile = File(destFolder, entry.name)
                    outFile.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { input -> outFile.outputStream().use { it.write(input.readBytes()) } }
                }
            }
        }
        destFolder
    }
}
```

### 3.3. TextChatParser: Eventi, Media e Multiriga
Estende il parser per rilevare gli eventi di sistema scritti nel testo (Cronologia Gruppo) e preparare i dati per il Radar Parole.

```kotlin
class TextChatParser {
    private val headerRegex = Regex("""^(\d{1,2}[/.,-]\d{1,2}[/.,-]\d{2,4})[,\s]+(\d{1,2}:\d{2})\s+-\s+(.+?):(.*)$""")
    
    // Rileva eventi di sistema nel testo per la Timeline dei Gruppi
    private val groupEventRegex = Regex("""(.+) (ha aggiunto|ha rimosso|ha creato il gruppo|ha cambiato l'immagine del gruppo|ha abbandonato il gruppo|ha modificato il nome del gruppo).*""")

    fun parse(lines: List<String>, ownerName: String): ParseResult {
        val messages = mutableListOf<ParsedMessage>()
        val groupEvents = mutableListOf<GroupEvent>()
        var currentMsg: ParsedMessage? = null

        lines.forEach { raw ->
            val line = raw.trim()
            val match = headerRegex.find(line)
            if (match != null) {
                currentMsg?.let { messages.add(it) }
                val (date, time, sender, body) = match.destructured
                
                // Rileva Evento Gruppo
                groupEventRegex.find("$sender $body")?.let { ev ->
                    groupEvents.add(GroupEvent.fromString(ev.value, date))
                }

                currentMsg = ParsedMessage(
                    timestamp = parseDate(date, time),
                    senderName = sender.trim(),
                    content = body.trim(),
                    isCurrentUser = (sender.trim() == ownerName)
                )
            } else {
                if (line.isNotEmpty() && currentMsg != null) {
                    currentMsg = currentMsg!!.copy(content = "${currentMsg!!.content} $line")
                }
            }
        }
        currentMsg?.let { messages.add(it) }
        return ParseResult(messages, groupEvents)
    }
    
    data class ParseResult(val messages: List<ParsedMessage>, val groupEvents: List<GroupEvent>)
}

data class GroupEvent(
    val timestamp: String, // o LocalDateTime
    val description: String, // Es: "Mario ha aggiunto Luigi"
    val type: GroupEventType
)

enum class GroupEventType { PARTICIPANT_JOIN, PARTICIPANT_LEAVE, NAME_CHANGE, ICON_CHANGE }
```

### 3.4. Analisi Media (Galleria)
Scansiona la cartella estratta per popolare la `MediaGalleryScreen`.

```kotlin
data class MediaFileItem(
    val file: File,
    val type: MediaType,
    val thumbnailPath: String? = null
)

enum class MediaType { IMAGE, VIDEO, AUDIO, DOCUMENT }

suspend fun extractMediaFromFolder(folder: File): MediaStats = withContext(Dispatchers.IO) {
    val allFiles = folder.listFiles()?.filter { !it.name.endsWith(".txt") && !it.isDirectory } ?: emptyList()
    
    val mediaItems = allFiles.mapNotNull { file ->
        val type = when(file.extension.lowercase()) {
            "jpg", "jpeg", "png", "gif" -> MediaType.IMAGE
            "mp4", "3gp", "mov" -> MediaType.VIDEO
            "opus", "aac", "mp3" -> MediaType.AUDIO
            "pdf", "doc", "xls", "ppt" -> MediaType.DOCUMENT
            else -> null
        }
        type?.let { MediaFileItem(file, it) }
    }

    MediaStats(
        totalCount = mediaItems.size,
        images = mediaItems.count { it.type == MediaType.IMAGE },
        videos = mediaItems.count { it.type == MediaType.VIDEO },
        audioFiles = mediaItems.count { it.type == MediaType.AUDIO },
        documents = mediaItems.count { it.type == MediaType.DOCUMENT },
        mediaList = mediaItems
    )
}

data class MediaStats(
    val totalCount: Int,
    val images: Int,
    val videos: Int,
    val audioFiles: Int,
    val documents: Int,
    val mediaList: List<MediaFileItem>
)
```

### 3.5. Unificazione nel Repository
Il repository orchestrerà tutto per la UI.

```kotlin
// AnalyticsRepository.kt
suspend fun processLiteMode(
    chatFolder: File, 
    currentUser: String
): LiteModeResult = withContext(Dispatchers.IO) {
    val txtFile = chatFolder.listFiles()?.firstOrNull { it.extension == "txt" } ?: throw IOException("No txt")
    
    // 1. Parse Testo + Eventi Gruppo
    val parserResult = TextChatParser().parse(txtFile.readLines(), currentUser)
    
    // 2. Statistiche Testuali (Parole, Emoji, Risposte)
    val textStats = calculateLiteStats(parserResult.messages)
    
    // 3. Estrazione Media (per Galleria)
    val mediaStats = extractMediaFromFolder(chatFolder)

    LiteModeResult(
        globalStats = textStats.copy(
            totalMediaCount = mediaStats.totalCount,
            // ... unisci stats se necessario
        ),
        mediaList = mediaStats.mediaList,
        groupEvents = parserResult.groupEvents
    )
}

data class LiteModeResult(
    val globalStats: GlobalStats,
    val mediaList: List<MediaFileItem>,
    val groupEvents: List<GroupEvent>
)
```

---

## 4. Piano di Lavoro (Task - Aggiornato)

| Task | Descrizione | Priorità |
|------|-------------|----------|
| **1. Lite Cache & Delta** | `LiteCacheRepository` con validazione righe/file JSON. | Alta |
| **2. Decompressione Totale** | `extractFullChatFolder()`: salva tutto in una cartella gestibile. | Alta |
| **3. Media Extractor** | `extractMediaFromFolder()`: classifica JPG/MP4/PDF in oggetti `MediaFileItem`. | Alta |
| **4. Parser Testo + Eventi** | `TextChatParser`: aggiunge logica `GroupEventRegex` per timeline gruppi. | Alta |
| **5. UI: Group History** | Schermata `GroupHistoryScreen.kt` (`LazyColumn` di eventi testuali estratti). | Media |
| **6. UI: Media Gallery** | `LiteMediaScreen.kt`: Griglia foto/anteprime video dai file estratti (Coil). | Alta |
| **7. Radar Parole Lite** | Adatta `WordSearchScreen.kt` per usare la lista `words` della cache invece di Room. | Media |
| **8. Stats Unificate** | `processLiteMode()`: fonde testo, media ed eventi per la UI. | Alta |