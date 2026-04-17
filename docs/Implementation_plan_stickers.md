# Piano di Implementazione: Sticker più usati

## Obiettivo

Aggiungere nella schermata di dettaglio chat una sezione **"Sticker più usati"**, analoga a *"Parole più usate"* ed *"Emoji più usate"*, con visualizzazione dell'immagine dello sticker e numero di utilizzi.

---

## Analisi della struttura dati (verificata su DB reale)

### Tabelle DB rilevanti (msgstore.db)

**message** — tabella principale messaggi

| Colonna | Uso |
|---------|-----|
| `_id` | PK, join con message_media |
| `chat_row_id` | filtro per chat |
| `from_me` | filtro inviati/ricevuti |
| `sender_jid_row_id` | filtro per mittente (gruppi) |
| `timestamp` | filtro temporale |
| `message_type` | **20 = sticker** |

**message_media** — contiene il file_path dello sticker

| Colonna | Uso |
|---------|-----|
| `message_row_id` | FK → message._id |
| `file_path` | path relativo (es. `Media/WhatsApp Stickers/STK-20240406-WA0029.webp`) |
| `is_animated_sticker` | ⚠️ **Solo 136 su 3884 sticker** — NON usare come filtro |
| `sticker_flags` | ⚠️ **Tutti NULL** — non utilizzabile |

### Come identificare i messaggi sticker

**Filtro corretto:** `message.message_type = 20`

| Filtro | Sticker trovati | Percentuale |
|--------|-----------------|-------------|
| `message_type = 20` | **3884** | 100% ✅ |
| `is_animated_sticker = 1` | 136 | 3.5% ❌ |

### Colonne inesistenti

- `media_wa_type` → **NON ESISTE** nella tabella `message_media`
- `message_thumbnail` → esiste come tabella ma è **VUOTA** per sticker (0 righe con message_type=20)

### Formato file_path nel DB

```
Media/WhatsApp Stickers/STK-20181125-WA0012.webp
Media/WhatsApp Stickers/STK-20190101-WA0002.webp
Media/WhatsApp Stickers/STK-20240406-WA0029.webp
```

Pattern: `Media/WhatsApp Stickers/STK-YYYYMMDD-WANNNN.webp`

### File sticker sul device

| Percorso | Contenuto | Nomi file |
|----------|-----------|-----------|
| `/data/data/com.whatsapp/files/Stickers/` | File con nomi hash | `2938ruyd89734y8r7whd.webp` ❌ non mappabili |
| `/sdcard/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Stickers/` | File con nomi originali | `STK-20181125-WA0012.webp` ✅ corrispondono al DB |
| `/sdcard/WhatsApp/Media/WhatsApp Stickers/` | Vecchio path (Android ≤ 10) | Stesso formato |

**Strategia:** leggere da sdcard (nomi corrispondono al DB), **NON** dalla cartella interna (nomi hash).

### Permessi di accesso

L'app ha già `MANAGE_EXTERNAL_STORAGE` nel manifest → accesso completo alla sdcard su tutte le versioni Android. **Nessun permesso aggiuntivo necessario.**

**NON serve root** per accedere ai file sticker.

---

## Cosa mostrare in UI

| Dato | Fonte |
|------|-------|
| Immagine sticker | sdcard → `AsyncImage` (Coil) da `File` locale |
| Numero utilizzi | `COUNT(*)` su `file_path` (GROUP BY) |
| Per mittente | filtro su `from_me` / `sender_jid_row_id` |
| Fallback | Icona placeholder se file non trovato su sdcard |

**Rimossi** rispetto al piano originale (tutti NULL nel DB):
- ~~Badge "GIF" / "Lottie"~~ → `sticker_flags` è NULL
- ~~Nome pack~~ → `message_sticker_pack` è vuoto

---

## Query SQL corretta

```sql
SELECT
  mm.file_path,
  COUNT(*) AS cnt
FROM message m
JOIN message_media mm ON m._id = mm.message_row_id
WHERE m.chat_row_id = ?
  AND m.message_type = 20
GROUP BY mm.file_path
ORDER BY cnt DESC
LIMIT ?;
```

### Con filtro temporale e mittente

```sql
SELECT
  mm.file_path,
  COUNT(*) AS cnt
FROM message m
JOIN message_media mm ON m._id = mm.message_row_id
WHERE m.chat_row_id = ?
  AND m.message_type = 20
  [AND m.timestamp >= ?]
  [AND m.timestamp <= ?]
  [AND m.sender_jid_row_id = (SELECT _id FROM jid WHERE raw_string = ?)]
GROUP BY mm.file_path
ORDER BY cnt DESC
LIMIT ?;
```

---

## Architettura delle modifiche

### Step 1 — Data Model

**[MODIFY] QueryModels.kt**

```kotlin
/** Statistiche per singolo sticker */
data class StickerStat(
    val filePath: String,    // "Media/WhatsApp Stickers/STK-20240406-WA0029.webp"
    val fileName: String,    // "STK-20240406-WA0029.webp"
    val count: Int
)
```

### Step 2 — Risoluzione path file sticker

**NON serve FileExtractor** — accesso diretto da sdcard.

```kotlin
// Utility per risolvere il path dello sticker sul filesystem
object StickerPathResolver {
    private val SDCARD_PATHS = listOf(
        // Android 11+ (Scoped Storage)
        "/sdcard/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Stickers",
        // Android ≤ 10 (legacy)
        "/sdcard/WhatsApp/Media/WhatsApp Stickers"
    )

    fun resolve(fileName: String): File? {
        for (basePath in SDCARD_PATHS) {
            val file = File(basePath, fileName)
            if (file.exists()) return file
        }
        return null
    }
}
```

### Step 3 — DAO

**[MODIFY] MessageDao.kt**

```kotlin
/**
 * Ritorna gli sticker più usati in una chat.
 * Filtro: message_type = 20.
 * Supporta filtro per mittente e range temporale.
 */
fun getTopStickers(
    chatId: Long,
    jid: String? = null,
    startTime: Long? = null,
    endTime: Long? = null,
    limit: Int = 10
): List<StickerStat>
```

### Step 4 — Repository

**[MODIFY] AnalyticsRepository.kt**

```kotlin
/** Top sticker aggregati per chat ("Tutti") */
suspend fun getTopStickers(
    chatId: Long,
    startTime: Long?,
    endTime: Long?,
    limit: Int = 10
): List<StickerStat>

/** Top sticker per mittente ("Per Persona") */
suspend fun getTopStickersBySender(
    chatId: Long,
    jid: String?,
    startTime: Long?,
    endTime: Long?,
    limit: Int = 10
): List<StickerStat>
```

### Step 5 — ViewModel

**[MODIFY] ChatDetailViewModel.kt**

```kotlin
enum class StickerViewMode { AGGREGATED, INDIVIDUAL }

private val _stickerViewMode = MutableStateFlow(StickerViewMode.AGGREGATED)
val stickerViewMode: StateFlow<StickerViewMode> = _stickerViewMode.asStateFlow()

private val _selectedSenderForStickers = MutableStateFlow<SenderStat?>(null)
val selectedSenderForStickers: StateFlow<SenderStat?> = ...

private val _topStickerLimit = MutableStateFlow(10)
val topStickerLimit: StateFlow<Int> = ...

private val _topStickers = MutableStateFlow<List<StickerStat>>(emptyList())
val topStickers: StateFlow<List<StickerStat>> = ...

private val _senderTopStickers = MutableStateFlow<List<StickerStat>>(emptyList())
val senderTopStickers: StateFlow<List<StickerStat>> = ...

private val _isStickerLoading = MutableStateFlow(false)
val isStickerLoading: StateFlow<Boolean> = ...
```

### Step 6 — Cache

**[MODIFY] CacheManager.kt**

```kotlin
@Serializable
private data class CachedStickerStat(
    val filePath: String,
    val fileName: String,
    val count: Int
)
```

Chiave: `sticker_chat_<chatId>_v1`

### Step 7 — UI

**[MODIFY] ChatDetailScreen.kt**

Nuova sezione `SectionCard("Sticker più usati")` posizionata dopo la sezione emoji.

**Struttura:**
- **Header**: Chips limite (5 / 10 / 20) + Switcher (Tutti / Per Persona)
- **Griglia**: `LazyVerticalGrid` o `LazyRow` con sticker
  - `AsyncImage` (Coil) da `File` locale (sdcard)
  - Testo: `"(N volte)"`
  - Fallback: icona placeholder se file non trovato

---

## Ordine di implementazione

1. `QueryModels.kt` — aggiunta `StickerStat`
2. `MessageDao.kt` — query `getTopStickers()`
3. `AnalyticsRepository.kt` — metodi sticker
4. `ChatDetailViewModel.kt` — stato e caricamento
5. `CacheManager.kt` — cache sticker
6. `ChatDetailScreen.kt` — sezione UI + `StickerPathResolver`

---

## Test

### Query di test (tutte le chat)

```sql
SELECT
  mm.file_path,
  COUNT(*) AS cnt
FROM message m
JOIN message_media mm ON m._id = mm.message_row_id
WHERE m.message_type = 20
GROUP BY mm.file_path
ORDER BY cnt DESC
LIMIT 10;
```

### Dati verificati

- Totale sticker nel DB: **3884**
- `message_type = 20` cattura il **100%** degli sticker
- File presenti su sdcard: `/sdcard/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Stickers/`
- Nomi file su sdcard **corrispondono** al `file_path` nel DB
- `MANAGE_EXTERNAL_STORAGE` già presente nel manifest
