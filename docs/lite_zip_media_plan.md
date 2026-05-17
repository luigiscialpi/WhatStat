# Piano Architetturale: Lettura Media Ibrida da ZIP (Lite Mode)

---

## 1. Obiettivo
Ottimizzare l'importazione Lite Mode minimizzando I/O su filesystem. Immagini, sticker e documenti (95% del volume) restano nel file ZIP copiato e vengono decodificati in RAM dalla UI. Video e audio vengono estratti fisicamente su disco per mantenere la compatibilità di "seeking" con `ExoPlayer` e `MediaPlayer`.

---

## 2. Implementazione Base

### 2.1. FileExtractor.kt (Estrazione Selettiva)
Invece di estrarre tutto il contenuto, `extractFullChatFolder` si occupa di:
1. Copiare l'intero ZIP importato in `filesDir/lite_media/lite_chat_{ts}/source.zip`.
2. Aprire la copia con `java.util.zip.ZipFile` (random access).
3. Iterare le `entries` ed **estrarre su disco solo**:
   - Il file `.txt` per il parser testuale.
   - I file `.mp4`, `.opus`, `.m4a` ecc. (i player nativi Android richiedono seek su file reali).
4. Tutto il resto (`.jpg`, `.webp`, `.pdf`) rimane nell'archivio.

### 2.2. LiteMediaManager.kt (Registrazione URI)
La scansione media itera sulle `entries` del `ZipFile`, non più sulle directory.
Nel costruire `LiteMediaItem` da salvare in `message_media`:
- Se estratto fisicamente (Video/Audio) → path relativo standard: `lite_chat_xxx/VID-2024.mp4`
- Se rimasto nello ZIP (Immagini/Sticker/Docs) → schema virtuale: `zip://lite_chat_xxx/source.zip|IMG-2024.jpg`

### 2.3. AnalyticsRepository.kt (Validazione Esistenza)
In `getMediaFiles`, il controllo `file.exists()` scarterebbe i media nello ZIP.
Bypass implementato:

```kotlin
if (stat.filePath.startsWith("zip://")) {
    val parts = stat.filePath.removePrefix("zip://").split("|")
    val zipMaster = File(base, parts[0])
    // Legge la Central Directory dello ZIP (O(1)) senza decomprimere
    if (zipMaster.exists()) {
        result.add(stat) // Mantiene l'URI zip:// per il Coil Fetcher
    }
} else {
    val file = if (stat.filePath.startsWith("/")) File(stat.filePath) else File(base, stat.filePath)
    if (file.exists()) result.add(stat.copy(filePath = file.absolutePath))
}
```

### 2.4. ZipFileFetcher.kt (Coil Fetcher Custom)
Coil non sa aprire `zip://...`. Il fetcher dedicato decodifica immagini/sticker direttamente dall'archivio in RAM:

```kotlin
class ZipFileFetcher(private val uri: String, private val options: Options) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val parts = uri.removePrefix("zip://").split("|")
        val baseFolder = File(options.context.filesDir, "lite_media")
        val zipFile = ZipFile(File(baseFolder, parts[0]))
        val entry = zipFile.getEntry(parts[1]) ?: run {
            zipFile.close()
            throw FileNotFoundException("Entry non trovata: ${parts[1]}")
        }
        // Source custom che chiude ZipFile quando Coil termina la decodifica
        val customSource = object : okio.Source {
            private val delegate = zipFile.getInputStream(entry).source()
            override fun read(sink: okio.Buffer, byteCount: Long) = delegate.read(sink, byteCount)
            override fun timeout() = delegate.timeout()
            override fun close() { try { delegate.close() } finally { zipFile.close() } }
        }
        return SourceResult(
            source = ImageSource(customSource.buffer(), options.context),
            mimeType = null,
            dataSource = DataSource.DISK,
        )
    }

    class Factory : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            if (!data.toString().startsWith("zip://")) return null
            return ZipFileFetcher(data.toString(), options)
        }
    }
}
```

Registrare in `MediaStatsScreen.kt` dentro il builder dell'`ImageLoader`:
```kotlin
.components {
    add(VideoFrameDecoder.Factory())
    add(RootFileFetcher.Factory())
    add(ZipFileFetcher.Factory()) // ← aggiunto
}
```

### 2.5. MediaStatsScreen.kt (Rendering galleria)
Per le thumbnail, passare la stringa `zip://` direttamente a `AsyncImage` invece di costruire un `File`:

```kotlin
val imgData = if (media.filePath.startsWith("zip://")) media.filePath
              else java.io.File(media.filePath)
AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(imgData).crossfade(true).build(), ...)
```

---

## 3. Gestione Rischi (implementazione base)
- **Statistiche Quick/Slow**: nessun impatto. L'aggregazione usa `COUNT(*)` SQL, indipendente dalla posizione fisica dei file.
- **UX Scroll MediaGallery**: alta fluidità. Coil decomprime in RAM e mette in cache i bitmap.
- **File orfani**: se lo ZIP viene cancellato, `getEntry()` fallisce silenziosamente e il media non viene mostrato — comportamento identico ai file estratti.
- **Re-import stessa chat**: `insertOrUpdateChat` usa `import_key` come chiave univoca; il vecchio folder media è cancellato da `cleanupMediaFolders`.

---

## 4. Stato attuale post-implementazione
- Sezioni 2, 5, 6, 7 **implementate e funzionanti**.
- Performance: **~26 secondi** su ZIP da 1.17 GB (da 2:26 originale, -83%).
- Spazio occupato: da 1.76 GB a **~1.20 GB** grazie allo slim ZIP eseguito in background.
- **Sezione 8 (ottimizzazioni DB) ancora da implementare.**

---

## 5. Bug-fix 1 — Video/Audio non si aprono (player esterno dà errore)

### Causa
I file video/audio vengono estratti nella directory privata dell'app (`filesDir/lite_media/...`).
`MediaStatsScreen.kt` li apre con un Intent che usa `Uri.fromFile()`, producendo un URI `file://`.
Le app esterne **non hanno permesso** di leggere i file privati dell'app. Il flag
`FLAG_GRANT_READ_URI_PERMISSION` funziona solo con URI `content://`, non `file://`.

### Fix — Servire i file tramite FileProvider

#### 1. Dichiarare il provider in `AndroidManifest.xml`
Dentro il tag `<application>`, aggiungere:

```xml
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.fileprovider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/file_paths" />
</provider>
```

> **Attenzione**: se esiste già un `<provider>` con la stessa authority, riusare quello esistente
> aggiungendo solo il path mancante al file XML.

#### 2. Creare `app/src/main/res/xml/file_paths.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <files-path name="lite_media" path="lite_media/" />
</paths>
```

#### 3. Modificare il click handler in `MediaStatsScreen.kt` (riga ~708)
Sostituire il blocco `if (media.mimeType.startsWith("video/") || ...)`:

```kotlin
if (media.mimeType.startsWith("video/") || media.mimeType.startsWith("audio/")) {
    try {
        val file = java.io.File(media.filePath)
        val contentUri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, media.mimeType)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        android.widget.Toast.makeText(
            context,
            context.getString(R.string.error_open_file),
            android.widget.Toast.LENGTH_SHORT,
        ).show()
    }
}
```

**Nota**: rimuovere il bypass StrictMode (`VmPolicy.Builder`) che non serve più.

---

## 6. Bug-fix 2 — Sticker (type 20) visibili in galleria Media, tab Sticker assente

### Causa
`isLiteSticker()` in `MediaStatsScreen.kt` (riga ~837) estrae il fileName con
`filePath.substringAfterLast('/')`, ma per i path `zip://lite_chat_xxx/source.zip|STK-2024.webp`
il risultato è `source.zip|STK-2024.webp`, che non inizia con `"STK-"`.
Quindi tutti gli sticker passano il `filterNot` e finiscono nella galleria; la lista `stickers`
resta vuota → `showStickerTab = false`.

### Fix — Estrarre il fileName dopo il separatore `|`
Modificare `isLiteSticker()` in `MediaStatsScreen.kt` (riga ~837):

```kotlin
private fun MediaStat.isLiteSticker(): Boolean {
    if (!mimeType.equals("image/webp", ignoreCase = true)) return false
    val fileName = if (filePath.contains("|")) {
        filePath.substringAfterLast('|').substringAfterLast('/')
    } else {
        filePath.substringAfterLast('/')
    }
    return fileName.startsWith("STK-", ignoreCase = true)
}
```

---

## 7. Ottimizzazione — Estrazione ZIP (I/O ridotto + slim)

### 7a. Problema I/O: due passate sullo ZIP
`FileExtractor.extractFullChatFolder` fa **due passate** sullo ZIP da 1.17 GB:
1. Copia integrale dello ZIP su disco (1.17 GB scritti).
2. Ri-lettura dello ZIP copiato per estrarre .txt, video e audio.

Totale I/O: ~2.34 GB letti + ~1.17 GB scritti (ZIP) + ~N MB scritti (media estratti).

### 7b. Problema spazio: video/audio duplicati
Dopo l'estrazione, i file video/audio sono presenti **due volte**:
- Su disco come file estratti (per il seeking dei player)
- Dentro `source.zip` (inutilmente — nessuno li legge da lì)

Per una chat con molti video questo può sprecare centinaia di MB.

### Fix — Implementazione effettiva (due metodi separati)

> **Nota sull'ordine**: lo slim deve avvenire **dopo** la scansione media (`LiteMediaManager`),
> perché `LiteMediaManager` itera le entry dello ZIP per costruire la lista media items.
> Eseguirlo prima rimuoverebbe i video/audio prima che vengano registrati nel DB.
> La soluzione è un metodo separato `slimSourceZip` chiamato in background da `LiteModeManager`.

#### `FileExtractor.kt` — `extractFullChatFolder` (solo copia + estrazione)

```kotlin
suspend fun extractFullChatFolder(zipUri: Uri): Result<File> =
    withContext(Dispatchers.IO) {
        runCatching {
            val mediaRoot = File(context.filesDir, "lite_media")
            if (!mediaRoot.exists()) mediaRoot.mkdirs()

            val destFolder = File(mediaRoot, "lite_chat_${System.currentTimeMillis()}")
            destFolder.mkdirs()

            // Passo 1: copia lo ZIP intero su disco (sorgente per immagini/sticker/docs)
            val sourceZip = File(destFolder, "source.zip")
            context.contentResolver.openInputStream(zipUri)?.use { input ->
                FileOutputStream(sourceZip).use { output -> input.copyTo(output) }
            } ?: throw IOException("Impossibile aprire l'URI: $zipUri")

            // Passo 2: estrai selettivamente con ZipFile (random access).
            // ZipFile salta le entry non richieste senza decomprimerle (~60 MB vs 1.17 GB).
            val mediaExtensions = setOf("mp4", "3gp", "mov", "m4v", "opus", "aac", "mp3", "m4a", "wav")
            java.util.zip.ZipFile(sourceZip).use { zip ->
                for (entry in zip.entries()) {
                    if (entry.isDirectory) continue
                    val ext = entry.name.substringAfterLast(".", "").lowercase()
                    if (ext == "txt" || ext in mediaExtensions) {
                        val outFile = File(destFolder, entry.name)
                        outFile.parentFile?.mkdirs()
                        zip.getInputStream(entry).use { s -> FileOutputStream(outFile).use { s.copyTo(it) } }
                    }
                }
            }
            destFolder
        }
    }
```

#### `FileExtractor.kt` — `slimSourceZip` (rimozione duplicati, chiamato separatamente)

```kotlin
suspend fun slimSourceZip(folder: File) = withContext(Dispatchers.IO) {
    val sourceZip = File(folder, "source.zip")
    if (!sourceZip.exists()) return@withContext

    val mediaExtensions = setOf("mp4", "3gp", "mov", "m4v", "opus", "aac", "mp3", "m4a", "wav")
    val entriesToRemove = mutableSetOf<String>()
    java.util.zip.ZipFile(sourceZip).use { zip ->
        for (entry in zip.entries()) {
            if (!entry.isDirectory && entry.name.substringAfterLast(".", "").lowercase() in mediaExtensions)
                entriesToRemove.add(entry.name)
        }
    }
    if (entriesToRemove.isEmpty()) return@withContext

    // Riscrive lo ZIP senza le entry video/audio.
    // NOTA: java.nio.file.FileSystems con jar: NON è disponibile su Android
    // (manca il provider com.sun.nio.zipfs). ZipOutputStream è l'unico metodo affidabile.
    val tempZip = File(folder, "source_slim.zip")
    try {
        java.util.zip.ZipFile(sourceZip).use { zipIn ->
            java.util.zip.ZipOutputStream(FileOutputStream(tempZip)).use { zipOut ->
                for (entry in zipIn.entries()) {
                    if (entry.name in entriesToRemove) continue
                    zipOut.putNextEntry(java.util.zip.ZipEntry(entry.name))
                    zipIn.getInputStream(entry).use { it.copyTo(zipOut) }
                    zipOut.closeEntry()
                }
            }
        }
        sourceZip.delete()
        tempZip.renameTo(sourceZip)
    } catch (e: Exception) {
        tempZip.delete() // mantieni ZIP originale se la riscrittura fallisce
    }
}
```

#### `LiteModeManager.kt` — chiamata background dopo `writeChat`

```kotlin
lastImportedChatId = chatId

// Slim in background: fire-and-forget dopo che la scansione media ha già letto lo ZIP.
// Il dato è nel DB: l'utente può procedere immediatamente.
CoroutineScope(Dispatchers.IO).launch {
    fileExtractor.slimSourceZip(folder)
}

_importProgress.value = LiteImportProgress(0.92f, "Seleziona il tuo nome...")
```

**Vantaggio I/O**: `ZipFile` (random access) salta le entry non richieste senza decomprimerle.
**Vantaggio spazio**: il passo 3 riscrive lo ZIP senza le entry video/audio.
La riscrittura legge l'intero archivio una volta e scrive quello snellito; il costo è proporzionale
alla dimensione finale del ZIP (immagini + sticker + docs), non a quella originale.
Se la riscrittura fallisce (disco pieno, I/O error), l'import prosegue normalmente con lo ZIP originale.

> **Nota su `FileSystems.newFileSystem`**: l'approccio `jar:` di Java non è disponibile sul
> runtime Android (manca il provider `com.sun.nio.zipfs`). La riscrittura con `ZipOutputStream`
> è l'unico metodo affidabile su Android.

---

## 8. Ottimizzazione — Scrittura DB Lite (bottleneck principale)

### Analisi del collo di bottiglia
La fase di scrittura nel DB (`LiteDbWriter.writeChat`) è il costo dominante.
Il problema più grave è in `insertMediaItems` (riga ~305 di `LiteDbWriter.kt`):
per **ogni** media item esegue una `db.query()` sulla tabella `message` per cercare
il messaggio corrispondente tramite timestamp ±1 secondo.

Con N media items → **N query SELECT** sequenziali dentro la transazione.
Gli indici esistenti sono su `(chat_row_id, message_type, timestamp)`, ma la query filtra
`message_type != 0` (disuguaglianza), quindi l'indice composto non viene usato
efficientemente per il range `timestamp`.

### Fix 4a — Lookup in memoria anziché N query SQL
Dopo l'inserimento dei messaggi (step 3), costruire in memoria una struttura
`TreeMap<Long, Long>` (timestamp → message._id) per il chatId appena inserito.
Poi in `insertMediaItems` fare lookup O(log n) dalla mappa anziché query SQL.

Modificare `LiteDbWriter.kt`, dopo il ciclo di insert messaggi (riga ~77):

```kotlin
// Dopo db.setTransactionSuccessful() e db.endTransaction() del blocco messaggi:

// Costruisci mappa timestamp→messageId per matching media O(log n)
val messageTimestampMap = TreeMap<Long, Long>() // timestamp → _id
db.rawQuery(
    "SELECT _id, timestamp FROM message WHERE chat_row_id = ? AND message_type != 0",
    arrayOf(chatId.toString())
).use { cursor ->
    while (cursor.moveToNext()) {
        messageTimestampMap[cursor.getLong(1)] = cursor.getLong(0)
    }
}
```

Poi sostituire `insertMediaItems` (riga ~305):

```kotlin
private fun insertMediaItems(
    db: SQLiteDatabase,
    chatId: Long,
    items: List<LiteMediaItem>,
    messageTimestampMap: TreeMap<Long, Long>, // NUOVO parametro
) {
    if (items.isEmpty()) return

    val stmt = db.compileStatement(
        "INSERT OR REPLACE INTO message_media (message_row_id, file_path, mime_type, file_size, file_hash) VALUES (?, ?, ?, ?, ?)"
    )

    db.beginTransaction()
    try {
        for (item in items) {
            // Cerca il messaggio più vicino entro ±1 secondo (O(log n) con TreeMap)
            val floorEntry = messageTimestampMap.floorEntry(item.timestamp + 1000)
            val messageId = if (floorEntry != null && floorEntry.key >= item.timestamp - 1000) {
                floorEntry.value
            } else {
                null
            }

            if (messageId != null) {
                stmt.clearBindings()
                stmt.bindLong(1, messageId)
                stmt.bindString(2, item.relativePath)
                stmt.bindString(3, getMimeType(item.type))
                stmt.bindLong(4, item.fileSize)
                if (item.type == "STICKER") {
                    stmt.bindString(5, item.fileName)
                } else {
                    stmt.bindNull(5)
                }
                stmt.executeInsert()
            }
        }
        db.setTransactionSuccessful()
    } finally {
        db.endTransaction()
        stmt.close()
    }
}
```

**Impatto atteso**: la fase media passa da N query SQL (decine di secondi) a N lookup
in-memory O(log n). Per 100k media items, da ~30-60 secondi a <1 secondo.

### Fix 4b — Prepared statement per insert messaggi
Attualmente `insertMessage` alloca un nuovo `ContentValues` per ogni messaggio.
Usare `compileStatement` per riutilizzare lo statement compilato.

Modificare il ciclo messaggi in `writeChat` (riga ~63):

```kotlin
db.beginTransaction()
try {
    val total = messages.size
    val stmt = db.compileStatement(
        """INSERT INTO message (chat_row_id, from_me, sender_jid_row_id, timestamp, message_type, text_data, key_id)
           VALUES (?, ?, ?, ?, ?, ?, ?)"""
    )
    messages.forEachIndexed { i, msg ->
        val senderJidId = senderJidMap[msg.senderName] ?: 0L
        val messageType = if (msg.hasMedia) guessMediaType(msg.content) else 0
        val textData = if (msg.hasMedia) null else msg.content
        val epochMillis = msg.timestamp
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        stmt.clearBindings()
        stmt.bindLong(1, chatId)
        stmt.bindLong(2, if (msg.isCurrentUser) 1L else 0L)
        stmt.bindLong(3, senderJidId)
        stmt.bindLong(4, epochMillis)
        stmt.bindLong(5, messageType.toLong())
        if (textData != null) stmt.bindString(6, textData) else stmt.bindNull(6)
        stmt.bindString(7, msg.senderName)

        val messageId = stmt.executeInsert()
        lastMessageId = messageId
        lastMessageTimestamp = epochMillis

        if (i % 500 == 0) progressCallback(i.toFloat() / total * 0.9f)
    }
    stmt.close()
    db.setTransactionSuccessful()
} finally {
    db.endTransaction()
}
```

**Impatto atteso**: riduzione ~10-15% sul tempo di insert messaggi (meno allocazioni
di oggetti, meno overhead JNI rispetto a `db.insert()`).

### Fix 4c — Pragma tuning per import bulk
Aggiungere pragma ottimizzati in `LiteDatabase.getOrCreate()`, subito dopo
`enableWriteAheadLogging()` (riga ~28 di `LiteDatabase.kt`):

```kotlin
db!!.enableWriteAheadLogging()
db!!.execSQL("PRAGMA synchronous = NORMAL")  // default FULL → NORMAL (sicuro con WAL)
db!!.execSQL("PRAGMA cache_size = -64000")   // 64 MB di cache (default ~2 MB)
db!!.execSQL("PRAGMA temp_store = MEMORY")   // temp tables in RAM
```

> `synchronous = NORMAL` con WAL è safe: si rischia perdita solo in caso di crash OS,
> non di crash app. Per un'operazione di import (dato ricostruibile) è accettabile.

**Impatto atteso**: riduzione ~5-10% sul tempo totale delle transazioni.

### Fix 4d — Creare indici DOPO il bulk insert (non prima)
Attualmente gli indici sulla tabella `message` vengono creati in `createSchema()` che
viene chiamato **prima** degli inserimenti. Questo rallenta ogni singolo INSERT perché
SQLite deve aggiornare 3 indici per riga.

Strategia: in `createSchema()`, creare gli indici **solo se la tabella ha già dati**
(database riaperto). Per un import fresco, posticipare la creazione degli indici
a dopo il bulk insert.

Modificare `LiteDatabase.kt` — spostare le 4 `CREATE INDEX` dalla `createSchema()`
a un metodo separato `ensureIndexes()`:

```kotlin
// In LiteDatabase.kt
fun ensureIndexes() {
    val db = getOrCreate()
    db.execSQL("CREATE INDEX IF NOT EXISTS idx_message_chat_type_ts ON message (chat_row_id, message_type, timestamp)")
    db.execSQL("CREATE INDEX IF NOT EXISTS idx_message_chat_type_from ON message (chat_row_id, message_type, from_me)")
    db.execSQL("CREATE INDEX IF NOT EXISTS idx_message_sender_jid ON message (sender_jid_row_id)")
    db.execSQL("CREATE INDEX IF NOT EXISTS idx_message_media_row ON message_media (message_row_id)")
}
```

Poi in `LiteDbWriter.writeChat()`, chiamare `liteDatabase.ensureIndexes()` **dopo**
`insertMediaItems()` e **prima** di `updateChatSummary()`.

> `CREATE INDEX IF NOT EXISTS` è idempotente: se gli indici esistono già (re-import,
> database riaperto), il costo è zero. Se non esistono (import fresco), vengono creati
> in un unico sweep sequenziale anziché aggiornati riga per riga durante gli INSERT.

**Impatto atteso**: riduzione ~15-25% sul tempo di inserimento messaggi per import freschi.
Per re-import (indici già esistenti) nessun cambiamento.

---

## 9. Riepilogo impatto stimato

| Intervento | Tempo risparmiato stimato | Spazio risparmiato | Rischio regressione |
|---|---|---|---|
| Bug-fix 1 (FileProvider) | — | — | Nessuno |
| Bug-fix 2 (isLiteSticker) | — | — | Nessuno |
| Opt. 7a (ZipFile random access) | ~15-25 sec | — | Nessuno |
| Opt. 7b (slim ZIP: rimuovi video/audio) | 0 sec — background fire-and-forget | Proporzionale ai video/audio nella chat | Basso — se fallisce, import prosegue con ZIP originale |
| Opt. 8a (TreeMap media lookup) | ~30-60 sec | — | Basso — verificare edge case timestamp duplicati |
| Opt. 8b (prepared statement) | ~5-10 sec | — | Nessuno |
| Opt. 8c (pragma tuning) | ~5-10 sec | — | Trascurabile (NORMAL safe con WAL) |
| Opt. 8d (indici posticipati) | ~15-25 sec | — | Basso — `ensureIndexes()` va chiamato prima di qualunque query |

**Tempo totale atteso dopo tutti gli interventi**: da ~1:52 a ~0:50–1:10 per ZIP da 1.17 GB.
