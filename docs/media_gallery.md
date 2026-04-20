# Media Gallery BottomSheet - "Media & Link"

## Obiettivo
Implementare una bottomsheet per i media (foto, video) analoga a quella dei link, con:
- Colonna "Media" nella card diventa cliccabile (come già funziona per i link)
- Galleria di miniature dei media (foto e video) ordinata per data
- Filtro automatico per i media presenti nel filesystem del telefono
- Lazy loading a blocchi di 100 con pulsante "Carica altri"
- Controllo ordinamento data (crescente/decrescente)

## Analisi del codice esistente

### Struttura attuale
- **MediaStatsScreen**: Pagina con link/media per contatto (`MediaStatsScreen.kt`)
- **DomainBreakdownSheet**: BottomSheet per i link con accordion — pattern da seguire
- **LinkStatCardContent**: Colonna link già cliccabile; da replicare per la colonna media
- **MessageDao.getTopStickers**: Query su `message_media` con `file_path` — base da cui derivare `getMediaFiles`

### Tabella message_media
- `file_path`: percorso **relativo** al root WhatsApp (es. `Media/WhatsApp Images/IMG-xxx.jpg`)
- `mime_type`: tipo MIME (`image/*`, `video/*`, ecc.)
- `file_hash`: hash del file (usato per deduplicare gli sticker, **non** utile qui)
- `message_row_id`: FK verso `message._id`

### Risoluzione del percorso assoluto
`file_path` è relativo: `File(it.filePath).exists()` non funziona.
Va costruito il path assoluto provando entrambe le directory base di WhatsApp (già note nel codebase):

```kotlin
private val WHATSAPP_BASES = listOf(
    "/sdcard/Android/media/com.whatsapp/WhatsApp",
    "/sdcard/WhatsApp"
)

fun resolveMediaPath(relativePath: String): String? =
    WHATSAPP_BASES.map { File(it, relativePath) }.firstOrNull { it.exists() }?.absolutePath
```

La risoluzione avviene nel **Repository**, non nel DAO.

## Implementazione

### 1. Nuovo data class `MediaStat` — `QueryModels.kt`

`count` non è significativo per foto/video (ogni file è quasi sempre unico, a differenza degli sticker).
Il modello è semplice, orientato al singolo file:

```kotlin
data class MediaStat(
    val filePath: String,    // percorso relativo dal DB
    val mimeType: String,    // "image/jpeg", "video/mp4", ecc.
    val timestamp: Long      // timestamp del messaggio in ms
)
```

### 2. MessageDao — Nuova funzione `getMediaFiles`

Nessun `GROUP BY` (ogni file è individuale), filtro MIME esplicito per escludere audio/documenti/sticker.
La query restituisce solo `image/*` e `video/*`.

```kotlin
fun getMediaFiles(
    chatId: Long? = null,
    jid: String? = null,
    startTime: Long? = null,
    endTime: Long? = null
): List<MediaStat> {
    // WHERE chat_row_id = ? (se chatId != null)
    //   AND (mm.mime_type LIKE 'image/%' OR mm.mime_type LIKE 'video/%')
    //   AND m.timestamp >= ? / <= ?
    //   AND jid filter (come in getTopStickers)
    // ORDER BY m.timestamp DESC
    // Nessun LIMIT — il paging avviene in memoria nel Repository
}
```

**Nota**: nessun `LIMIT` nella query SQL — il Dataset scaricato è già filtrato per chat/jid
e tipicamente non supera qualche migliaia di righe. Il paging avviene lato Repository.

### 3. AnalyticsRepository — Nuova funzione `getMediaFiles`

Risolve i path assoluti e filtra quelli esistenti. Restituisce l'intera lista ordinata;
il paging è gestito dalla UI tramite `displayedCount`.

```kotlin
suspend fun getMediaFiles(
    chatId: Long?,
    jid: String?,
    startTime: Long?,
    endTime: Long?
): List<MediaStat> = withContext(Dispatchers.IO) {
    val raw = messageDao.getMediaFiles(chatId, jid, startTime, endTime)
    raw.mapNotNull { stat ->
        resolveMediaPath(stat.filePath)?.let { absPath ->
            stat.copy(filePath = absPath)
        }
    }
    // Nessun .take() qui: la lista completa serve per il lazy paging UI
}
```

### 4. MediaStatsViewModel — Nuovo stato

Specchia il pattern di `_contactDomainStats` già esistente:

```kotlin
// Galleria media: Triple(displayName, avatarPath, listaMedia)
private val _mediaGalleryState =
    MutableStateFlow<Triple<String, String?, List<MediaStat>>?>(null)
val mediaGalleryState: StateFlow<...> = _mediaGalleryState.asStateFlow()

private val _isMediaGalleryLoading = MutableStateFlow(false)
val isMediaGalleryLoading: StateFlow<Boolean> = _isMediaGalleryLoading.asStateFlow()

fun loadMediaGalleryForContact(jid: String, displayName: String?) { ... }
fun dismissMediaGallery() { _mediaGalleryState.value = null }
```

### 5. MediaStatsScreen — Modifiche UI

#### 5a. `LinkStatCardContent` — abilitare colonna media cliccabile

Aggiungere `onMediaClick: (() -> Unit)?` con la stessa logica condizionale già usata per `onLinksClick`.
Il bottone media va abilitato **solo se `mediaCount > 0`**; icona e colori esistenti rimangono.

#### 5b. Nuovo composable `MediaGallerySheet`

Struttura analoga a `DomainBreakdownSheet`. La sheet riceve la lista completa e gestisce
internamente `displayedCount` e `sortAscending`.

## Lazy Loading — "Carica altri"

Il paging avviene interamente in UI, senza nuove query al DB.

```kotlin
// Stato interno del composable
var displayedCount by remember { mutableIntStateOf(PAGE_SIZE) }   // PAGE_SIZE = 100
val visibleItems = remember(mediaFiles, displayedCount) {
    mediaFiles.take(displayedCount)
}
val hasMore = displayedCount < mediaFiles.size
```

In fondo alla `LazyVerticalGrid`:

```kotlin
if (hasMore) {
    item(span = { GridItemSpan(3) }) {
        OutlinedButton(
            onClick = { displayedCount += PAGE_SIZE },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text("Carica altri (${mediaFiles.size - displayedCount} rimasti)")
        }
    }
}
```

**UX**: il conteggio nel pulsante (`N rimasti`) evita click inutili e dà contesto.
`displayedCount` si resetta a `PAGE_SIZE` ogni volta che la sheet viene riaperta.

## Ordinamento — Toggle crescente/decrescente

La lista arriva già ordinata `DESC` dal DAO. Il toggle inverte in memoria senza query.

```kotlin
var sortAscending by remember { mutableStateOf(false) }  // default: più recenti prima
val sortedItems = remember(visibleItems, sortAscending) {
    if (sortAscending) visibleItems.sortedBy { it.timestamp }
    else visibleItems.sortedByDescending { it.timestamp }
}
```

**Nota**: `remember(visibleItems, sortAscending)` ricalcola solo quando cambia uno dei due.
La finestra visibile rimane `displayedCount` elementi, quindi il sort è sempre O(100-200) al massimo.

### Posizione del toggle nella UI

Nell'header della `MediaGallerySheet`, a destra del titolo:

```
┌─────────────────────────────────────────┐
│ 👤 Mario Rossi                          │
│ Analisi Media                           │
│                                         │
│ [📷 42 foto · 🎬 8 video]  [↑↓ Data ▼] │
└─────────────────────────────────────────┘
```

Implementazione con `TextButton` + icona rotante (animazione `animateFloatAsState` sull'angolo):

```kotlin
TextButton(onClick = { sortAscending = !sortAscending }) {
    Icon(
        Icons.Outlined.SwapVert,
        contentDescription = "Ordine data",
        modifier = Modifier.rotate(if (sortAscending) 180f else 0f)
    )
    Spacer(Modifier.width(4.dp))
    Text(if (sortAscending) "Meno recenti" else "Più recenti")
}
```

L'icona `SwapVert` ruota di 180° con animazione fluida al cambio stato.

## Gestione thumbnail video

`coil-compose:2.7.0` non estrae frame da video nativamente. Opzioni:

1. **Aggiungere `coil-video:2.7.0`** (consigliato) → thumbnail automatici con zero codice aggiuntivo
2. **Fallback icona**: mostrare `Icons.Outlined.PlayCircle` per i `video/*` senza caricare thumbnail

La scelta va fatta nel `build.gradle.kts`. Con l'opzione 1 basta aggiungere la dipendenza;
Coil rileva automaticamente il tipo MIME e usa il decoder video.

## Struttura visiva di ogni thumbnail

```
┌────────┐
│        │  ← AsyncImage 1:1, ContentScale.Crop
│  img   │     Aspect ratio forzato con Modifier.aspectRatio(1f)
│        │
│ [▶ v] │  ← overlay badge in basso-destra: solo per video
├────────┤
│dd/mm/aa│  ← data sotto, typography.labelSmall, onSurfaceVariant
└────────┘
```

Il badge video è un `Box` sovrapposto con sfondo nero semi-trasparente e icona `PlayArrow` 12dp.

## Note UX riepilogative

- **Apertura sheet**: il contatore media nella card diventa cliccabile solo se `mediaCount > 0`;
  se è 0 rimane non-cliccabile (come già funziona per i link)
- **Galleria**: `LazyVerticalGrid(columns = Fixed(3))` con `contentPadding = PaddingValues(8.dp)`
  e `horizontalArrangement/verticalArrangement = Arrangement.spacedBy(4.dp)`
- **Lazy loading**: pulsante `OutlinedButton` a piena larghezza in fondo alla griglia,
  span 3 colonne, con conteggio rimasti — reset a 100 alla riapertura
- **Ordinamento**: toggle nell'header, icona `SwapVert` con rotazione animata,
  label che descrive l'ordinamento corrente ("Più recenti" / "Meno recenti")
- **Badge video**: overlay in basso-destra sulla thumbnail, semi-trasparente
- **Fallback immagine**: `Box` con `Icons.Outlined.BrokenImage` se Coil fallisce il load
- **Loading state**: `CircularProgressIndicator` centrato mentre la lista viene costruita nel ViewModel
- **Conteggio nell'header**: riga `"X foto · Y video"` ricavata filtrando `mimeType.startsWith("image/")`
  vs `"video/"` sulla lista completa (non solo su quella visibile)

## File da modificare

| File | Modifica |
|------|---------|
| `QueryModels.kt` | Aggiungere `data class MediaStat(filePath, mimeType, timestamp)` |
| `MessageDao.kt` | Nuova `getMediaFiles()` — filtra `image/` e `video/`, ordina per `timestamp DESC`, no GROUP BY, no LIMIT |
| `AnalyticsRepository.kt` | Nuova `getMediaFiles()` — risolve path con le due base dir, filtra esistenti, restituisce lista completa |
| `MediaStatsViewModel.kt` | `_mediaGalleryState`, `_isMediaGalleryLoading`, `loadMediaGalleryForContact()`, `dismissMediaGallery()` |
| `MediaStatsScreen.kt` | `onMediaClick` in `LinkStatCardContent` + nuovo composable `MediaGallerySheet` con grid, lazy loading e sort toggle |
| `build.gradle.kts` | Opzionale: `io.coil-kt:coil-video:2.7.0` per thumbnail video automatici |
