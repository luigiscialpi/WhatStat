# Radar Parole — Bottom Sheet Anteprima Messaggi

> Tap sul conteggio di un mittente nei risultati Radar per aprire una bottom sheet
> con la lista dei messaggi che contengono la parola cercata, evidenziata in grassetto,
> con indicazione del gruppo/chat di provenienza.

---

## Contesto attuale

La pagina Radar (`WordSearchScreen`) mostra, dopo una ricerca, una lista di card con:
- Avatar del mittente
- Nome + numero di telefono
- **Conteggio occorrenze** (testo `headlineSmall`, colore `primary`)

Il conteggio e' attualmente un `Text` statico. L'obiettivo e' renderlo tappabile e,
al tap, aprire una `ModalBottomSheet` con i messaggi effettivi.

### Riferimento UX esistente

La `MediaStatsScreen` usa gia' il pattern `ModalBottomSheet` (Material 3) con:
- Drag handle custom
- Header con avatar + nome contatto
- Contenuto scrollabile (`LazyColumn` / `LazyVerticalGrid`)

Replicheremo lo stesso pattern per coerenza visiva.

---

## Proposed Changes

### Step 1 — Data Layer: nuova query DAO + nuovo DTO

#### [NEW] Modello `WordMessageResult` in `QueryModels.kt`

```kotlin
/** Singolo messaggio trovato dalla ricerca Radar */
data class WordMessageResult(
    val text: String,
    val timestamp: Long,
    val chatRowId: Long,
    val chatDisplayName: String
)
```

Il campo `chatDisplayName` arriva dal DAO come `chat_subject` (gruppi) o `chat_jid`
(chat 1-a-1) e viene risolto in un nome leggibile nel Repository (step 2).

#### [MODIFY] `MessageDao.kt` — nuovo metodo `searchWordMessages`

Nuova query SQL che restituisce i **messaggi individuali** contenenti la parola,
filtrati per mittente (JID). Per ogni messaggio restituisce:

| Campo | Tipo | Descrizione |
|-------|------|-------------|
| `text_data` | String | Testo completo del messaggio |
| `timestamp` | Long | Timestamp Unix ms |
| `chat_row_id` | Long | ID della chat di appartenenza |
| `chat_jid` | String | JID della chat (per risolvere il nome) |
| `chat_subject` | String? | Subject del gruppo (null per chat 1-a-1) |

**Firma proposta:**

```kotlin
/**
 * Restituisce i messaggi che contengono la parola cercata,
 * filtrati per mittente (JID). Supporta filtro temporale e per chat.
 * Ordinamento: timestamp DESC (messaggi piu' recenti prima).
 * Nessun LIMIT SQL: la paginazione e' gestita in UI con "Carica altri".
 */
fun searchWordMessages(
    word: String,
    senderJid: String,
    chatId: Long? = null,
    startTime: Long? = null,
    endTime: Long? = null
): List<WordMessageResult>
```

**Logica SQL:**

```sql
SELECT m.text_data, m.timestamp, m.chat_row_id,
       COALESCE(cj.raw_string, '') AS chat_jid,
       c.subject AS chat_subject
FROM message m
LEFT JOIN chat c ON m.chat_row_id = c._id
LEFT JOIN jid cj ON c.jid_row_id = cj._id
LEFT JOIN jid sj ON m.sender_jid_row_id = sj._id AND m.sender_jid_row_id > 0
LEFT JOIN jid_map jm ON jm.lid_row_id = sj._id
LEFT JOIN jid rj ON rj._id = jm.jid_row_id
WHERE m.text_data IS NOT NULL
  AND m.message_type = 0
  AND LOWER(m.text_data) LIKE '%' || ? || '%'
  AND (
    (? = 'me@s.whatsapp.net' AND m.from_me = 1)
    OR (? != 'me@s.whatsapp.net' AND m.from_me = 0
        AND (rj.raw_string = ? OR sj.raw_string = ? OR cj.raw_string = ?))
  )
  -- filtri opzionali chatId, startTime, endTime
ORDER BY m.timestamp DESC
```

> **NOTA:** Il pattern `from_me` + `sender_jid_row_id` e' lo stesso gia' usato in
> `searchWordFrequency`. Per le chat 1-a-1, `sender_jid_row_id = 0`,
> quindi il fallback su `chat_jid` e' necessario.

Il parsing del cursor costruisce `WordMessageResult` usando `chat_subject` se non null,
altrimenti `chat_jid` (che verra' risolto nel repository).

---

### Step 2 — Repository Layer

#### [MODIFY] `AnalyticsRepository.kt`

Aggiungere metodo:

```kotlin
/**
 * Recupera i messaggi contenenti la parola cercata per un mittente specifico.
 * Risolve il nome della chat di appartenenza tramite wa.db (chat 1-a-1)
 * oppure usa il subject del gruppo (gia' presente nel risultato DAO).
 */
suspend fun getWordMessages(
    word: String,
    senderJid: String,
    chatId: Long? = null,
    startTime: Long? = null,
    endTime: Long? = null
): List<WordMessageResult> = withContext(Dispatchers.IO) {
    val raw = messageDao.searchWordMessages(word, senderJid, chatId, startTime, endTime)
    if (raw.isEmpty()) return@withContext emptyList()

    // Raccoglie i JID di chat 1-a-1 che hanno bisogno di risoluzione nome
    val jidsToResolve = raw
        .filter { !it.chatDisplayName.endsWith("@g.us") }
        .map { it.chatDisplayName }
        .distinct()
    val chatNames = if (jidsToResolve.isNotEmpty()) {
        contactDao.resolveContactNames(jidsToResolve)
    } else emptyMap()

    raw.map { msg ->
        val resolvedName = chatNames[msg.chatDisplayName]
            ?: msg.chatDisplayName.substringBefore("@")
        msg.copy(chatDisplayName = resolvedName)
    }
}
```

**Logica di risoluzione nomi chat:**
- Se il DAO ha gia' restituito `chat_subject` (gruppi) → usato direttamente come `chatDisplayName`
- Se il risultato contiene un JID `@s.whatsapp.net` → risolto tramite `contactDao.resolveContactNames()`
- Fallback: parte del JID prima di `@`

---

### Step 3 — ViewModel

#### [MODIFY] `WordSearchViewModel.kt`

**3a. Nuovi state flows:**

```kotlin
// Stato per la bottom sheet: (senderName, senderAvatarPath, lista messaggi)
private val _messagePreview = MutableStateFlow<Triple<String, String?, List<WordMessageResult>>?>(null)
val messagePreview: StateFlow<Triple<String, String?, List<WordMessageResult>>?> = _messagePreview.asStateFlow()

private val _isLoadingMessages = MutableStateFlow(false)
val isLoadingMessages: StateFlow<Boolean> = _isLoadingMessages.asStateFlow()
```

**3b. Metodo `loadMessagesForSender`:**

```kotlin
/**
 * Carica i messaggi contenenti la parola cercata per il mittente selezionato.
 * Apre la bottom sheet con i risultati.
 */
fun loadMessagesForSender(senderJid: String, senderName: String) {
    viewModelScope.launch {
        _isLoadingMessages.value = true
        try {
            val (startTime, endTime) = if (_selectedTimeRange.value == TimeRange.CUSTOM) {
                _customDateRange.value?.let {
                    analyticsRepository.getDateRangeBounds(it)
                } ?: (null to null)
            } else {
                analyticsRepository.getTimeRangeBounds(_selectedTimeRange.value, _customDateRange.value)
            }
            val messages = analyticsRepository.getWordMessages(
                word = _searchQuery.value,
                senderJid = senderJid,
                chatId = _chatId.value,
                startTime = startTime,
                endTime = endTime
            )
            _messagePreview.value = Triple(
                senderName,
                getAvatarPath(senderJid),
                messages
            )
        } catch (e: Exception) {
            _messagePreview.value = null
        } finally {
            _isLoadingMessages.value = false
        }
    }
}
```

**3c. Metodo `dismissMessagePreview`:**

```kotlin
fun dismissMessagePreview() {
    _messagePreview.value = null
}
```

---

### Step 4 — UI: Bottom Sheet + conteggio tappabile

#### [MODIFY] `WordSearchScreen.kt`

**4a. Rendere il conteggio tappabile**

Il `Text("${result.count}")` (riga ~282) viene avvolto in un contenitore tappabile:

```kotlin
Box(
    modifier = Modifier
        .clip(RoundedCornerShape(8.dp))
        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f))
        .clickable {
            viewModel.loadMessagesForSender(
                senderJid = result.jid,
                senderName = result.displayName
                    ?: result.jid.substringBefore("@")
            )
        }
        .padding(horizontal = 10.dp, vertical = 6.dp)
) {
    Text(
        "${result.count}",
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.primary
    )
}
```

**Affordance visiva:** sfondo `primaryContainer` con alpha 0.2 + angoli arrotondati 8dp
+ ripple effect nativo Material 3 → comunica chiaramente che il numero e' interattivo,
in modo coerente con lo stile dei contatori nella schermata Media & Link.

**4b. Aggiungere la `ModalBottomSheet`**

L'annotazione `@OptIn(ExperimentalMaterial3Api::class)` va aggiunta al composable.
Dopo il blocco `Column` principale e prima della chiusura dell'`if (showInfoDialog)`,
aggiungere il bottom sheet all'interno di un `Box` wrapper (stesso pattern di `MediaStatsScreen`):

```kotlin
val messagePreview by viewModel.messagePreview.collectAsState()
val isLoadingMessages by viewModel.isLoadingMessages.collectAsState()

if (messagePreview != null) {
    ModalBottomSheet(
        onDismissRequest = { viewModel.dismissMessagePreview() },
        sheetState = rememberModalBottomSheetState(),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(32.dp)
                    .height(4.dp)
                    .background(
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        RoundedCornerShape(2.dp)
                    )
            )
        }
    ) {
        WordMessageSheet(
            senderName = messagePreview!!.first,
            senderAvatarPath = messagePreview!!.second,
            messages = messagePreview!!.third,
            searchWord = searchQuery,
            isLoading = isLoadingMessages,
            isChatSpecific = chatId != null
        )
    }
}
```

**4c. Composable `WordMessageSheet`**

Include:
- **Toggle ordinamento** (piu' recenti / meno recenti) — stesso pattern `MediaGallerySheet`
- **Paginazione "Carica altri"** a blocchi di 50 — stesso pattern `MediaGallerySheet`
- **Parametro `isChatSpecific`** — se true, nasconde il nome chat nelle card

```kotlin
@Composable
private fun WordMessageSheet(
    senderName: String,
    senderAvatarPath: String?,
    messages: List<WordMessageResult>,
    searchWord: String,
    isLoading: Boolean,
    isChatSpecific: Boolean = false
) {
    val dateFormatter = remember { SimpleDateFormat("dd/MM/yy", Locale.ITALIAN) }
    var sortAscending by remember { mutableStateOf(false) }
    var displayedCount by remember { mutableIntStateOf(50) }

    val sortedAll = remember(messages, sortAscending) {
        if (sortAscending) messages.sortedBy { it.timestamp }
        else messages.sortedByDescending { it.timestamp }
    }
    val visibleItems = sortedAll.take(displayedCount)
    val hasMore = displayedCount < sortedAll.size

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp)
    ) {
        // Header: avatar + titolo
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (senderAvatarPath != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(java.io.File(senderAvatarPath))
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.width(12.dp))
            }
            Column {
                Text(
                    "Messaggi con \"$searchWord\"",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    senderName,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        // Contatore + toggle ordinamento
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "${messages.size} messaggi trovati",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = { sortAscending = !sortAscending }) {
                Icon(Icons.Outlined.SwapVert, contentDescription = "Ordina")
                Spacer(Modifier.width(4.dp))
                Text(if (sortAscending) "Meno recenti" else "Piu' recenti")
            }
        }
        Spacer(Modifier.height(12.dp))

        // Lista messaggi
        when {
            isLoading -> LoadingSection()
            messages.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Nessun messaggio trovato",
                         style = MaterialTheme.typography.bodyMedium,
                         color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            else -> {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(visibleItems) { msg ->
                        MessagePreviewCard(
                            message = msg,
                            searchWord = searchWord,
                            dateFormatter = dateFormatter,
                            showChatName = !isChatSpecific
                        )
                    }
                    // Pulsante "Carica altri" (paginazione client-side)
                    if (hasMore) {
                        item {
                            OutlinedButton(
                                onClick = { displayedCount += 50 },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                            ) {
                                Text("Carica altri (${sortedAll.size - displayedCount} rimasti)")
                            }
                        }
                    }
                }
            }
        }
    }
}
```

**4d. Composable `MessagePreviewCard`**

Layout di ogni card messaggio:

```
+----------------------------------------------------+
| [icona chat] Nome Gruppo/Chat              12/04/25 |
|                                                      |
| "...il testo con la **parola** in grassetto..."      |
+----------------------------------------------------+
```

```kotlin
@Composable
private fun MessagePreviewCard(
    message: WordMessageResult,
    searchWord: String,
    dateFormatter: SimpleDateFormat,
    showChatName: Boolean = true
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Riga superiore: nome chat (se globale) + data
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showChatName) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Outlined.ChatBubbleOutline,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            message.chatDisplayName,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    Spacer(Modifier.weight(1f))
                }
                Text(
                    dateFormatter.format(Date(message.timestamp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(8.dp))

            // Testo messaggio con parola evidenziata
            HighlightedText(
                text = message.text,
                highlight = searchWord,
                style = MaterialTheme.typography.bodyMedium,
                highlightStyle = SpanStyle(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}
```

**4e. Utility `HighlightedText`**

```kotlin
/**
 * Renderizza il testo con tutte le occorrenze di [highlight]
 * evidenziate con lo stile [highlightStyle].
 * La ricerca e' case-insensitive.
 */
@Composable
private fun HighlightedText(
    text: String,
    highlight: String,
    style: TextStyle,
    highlightStyle: SpanStyle
) {
    val annotated = remember(text, highlight) {
        buildAnnotatedString {
            val lower = text.lowercase()
            val searchLower = highlight.lowercase()
            var idx = 0
            while (idx < text.length) {
                val found = lower.indexOf(searchLower, idx)
                if (found == -1) {
                    append(text.substring(idx))
                    break
                }
                append(text.substring(idx, found))
                withStyle(highlightStyle) {
                    append(text.substring(found, found + highlight.length))
                }
                idx = found + highlight.length
            }
        }
    }
    Text(text = annotated, style = style, maxLines = 4, overflow = TextOverflow.Ellipsis)
}
```

---

## Design UX/UI — Riepilogo visivo

### Affordance del conteggio tappabile

| Aspetto | Scelta |
|---------|--------|
| Sfondo | `primaryContainer.copy(alpha = 0.2f)` con `RoundedCornerShape(8.dp)` |
| Ripple | Standard Material 3 (fornito da `clickable`) |
| Feedback | Il numero mantiene stile `headlineSmall` + colore `primary` |

### Bottom Sheet

| Aspetto | Scelta |
|---------|--------|
| Tipo | `ModalBottomSheet` Material 3 (coerente con `MediaStatsScreen`) |
| Altezza max | ~70% schermo (default M3, espandibile con drag) |
| Drag handle | Pillola custom come in `MediaStatsScreen` |
| Header | Avatar mittente + "Messaggi con «parola»" (label) + nome mittente (headline) |
| Contatore + sort | "N messaggi trovati" a sinistra, toggle "Piu' recenti / Meno recenti" a destra |
| Card messaggio | Sfondo `surfaceVariant(0.4)`, angoli 12dp |
| Nome chat | Label con icona chat, colore `primary` — **nascosto** se Radar aperto da chat specifica |
| Data | `dd/MM/yy` allineata a destra |
| Testo | `bodyMedium`, max 4 righe, overflow ellipsis |
| Parola evidenziata | `FontWeight.Bold` + colore `primary` |
| Paginazione | "Carica altri (N rimasti)" — blocchi da 50, stesso pattern `MediaGallerySheet` |
| Loading | `LoadingSection()` centrato (stesso componente usato ovunque) |
| Empty state | "Nessun messaggio trovato" centrato |

### Limiti prestazionali

- **Nessun LIMIT SQL** — tutti i messaggi vengono caricati, la paginazione e' client-side (blocchi da 50)
- **Testo troncato a 4 righe** per densita' visiva
- **LazyColumn** per rendering efficiente
- La query usa l'indice `idx_msg_text_chat` gia' esistente

---

## Decisioni Prese

1. **Paginazione + ordinamento:** Nessun LIMIT SQL. Tutti i messaggi vengono caricati
   in memoria. La UI mostra i primi 50 con pulsante "Carica altri (N rimasti)" in fondo.
   Toggle ordinamento (piu' recenti / meno recenti) nell'header, stesso pattern `MediaGallerySheet`.

2. **Nome chat nascosto in contesto chat-specifico:** Quando il Radar e' aperto da una
   chat specifica (`chatId != null`), il nome della chat viene nascosto dalle card
   messaggio per risparmiare spazio verticale. In modalita' globale rimane visibile.

---

## Verification Plan

### Build

```bash
./gradlew assembleDebug
```

### Test manuale

1. Apri Radar globale → cerca una parola comune → verifica card con conteggio
2. Tap sul conteggio di un mittente → bottom sheet si apre
3. Verifica: parola evidenziata in grassetto, nome chat visibile, data corretta
4. Scroll lista messaggi → verifica lazy loading fluido
5. Toggle ordinamento → verifica che i messaggi si riordinino correttamente
6. Scroll fino in fondo → tap "Carica altri" → verifica che appaiano nuovi messaggi
7. Dismiss bottom sheet (drag down o tap fuori)
8. Ripeti da una chat specifica (Radar contestuale) → verifica che il nome chat sia nascosto
9. Verifica con filtro temporale attivo

### Edge cases

- Parola presente in 0 messaggi per quel mittente (race condition se i dati cambiano)
- Messaggi molto lunghi (troncamento a 4 righe)
- Parola presente piu' volte nello stesso messaggio (tutte evidenziate)
- Caratteri speciali nella parola cercata

---

## Riepilogo file coinvolti

| File | Azione | Complessita' |
|------|--------|--------------|
| `data/db/model/QueryModels.kt` | Aggiungere `WordMessageResult` | Bassa |
| `data/db/dao/MessageDao.kt` | Aggiungere `searchWordMessages()` | Media |
| `data/repository/AnalyticsRepository.kt` | Aggiungere `getWordMessages()` | Media |
| `ui/screen/wordsearch/WordSearchViewModel.kt` | Aggiungere state + metodi | Media |
| `ui/screen/wordsearch/WordSearchScreen.kt` | Bottom sheet + card + highlight | Alta |

**Ordine di implementazione:** QueryModels → MessageDao → AnalyticsRepository → ViewModel → Screen

---

> Documento creato il 2026-05-02. Riferimento versione: v1.21.
