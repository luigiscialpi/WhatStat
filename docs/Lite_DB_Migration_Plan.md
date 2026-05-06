# Piano di migrazione Lite Mode: da JSON cache a SQLite

> Obiettivo: sostituire il sistema `LiteCacheRepository` (JSON in-memory) con un database SQLite locale
> che rispecchi la struttura di `msgstore.db`, consentendo il riuso dei DAO root e il supporto multi-chat.

---

## Indice

1. [Motivazione e benefici](#1-motivazione-e-benefici)
2. [Architettura target](#2-architettura-target)
3. [Schema del database Lite](#3-schema-del-database-lite)
4. [Fase 1 — Creare il database e il writer](#fase-1--creare-il-database-e-il-writer)
5. [Fase 2 — Adattare i DAO per supportare due sorgenti](#fase-2--adattare-i-dao-per-supportare-due-sorgenti)
6. [Fase 3 — Eliminare i branch Lite da AnalyticsRepository](#fase-3--eliminare-i-branch-lite-da-analyticsrepository)
7. [Fase 4 — UI multi-chat e convergenza navigazione](#fase-4--ui-multi-chat-e-convergenza-navigazione)
8. [Fase 5 — Import incrementale e gestione chat multiple](#fase-5--import-incrementale-e-gestione-chat-multiple)
9. [Fase 6 — Cleanup del vecchio sistema](#fase-6--cleanup-del-vecchio-sistema)
10. [Rischi e note importanti](#rischi-e-note-importanti)

---

## 1. Motivazione e benefici

| Problema attuale | Con il DB Lite |
|---|---|
| 16 branch `if (isLiteMode)` in `AnalyticsRepository` (1652 righe) | Eliminati: un solo percorso dati |
| Scansione lineare in-memory per ogni query | Query SQL indicizzate |
| Una sola chat per import (sovrascrive il JSON) | Multi-chat: ogni ZIP aggiunge una chat al DB |
| Modelli duplicati (`ParsedMessage` vs `ChatMessageCount`) | Un unico set di modelli (`QueryModels.kt`) |
| UI speciale per Lite (`chatId = -1L`, no ChatListScreen) | Stessa UI di root (Dashboard → ChatList → ChatDetail) |
| Cache non sopravvive al clear del processo | DB persistente in `filesDir` |

---

## 2. Architettura target

```
                    ┌─────────────────────┐
                    │   TextChatParser     │ (invariato: produce ParseResult)
                    └──────────┬──────────┘
                               │
                    ┌──────────▼──────────┐
                    │  LiteDbWriter        │ (NUOVO: scrive nel DB Lite)
                    └──────────┬──────────┘
                               │
                    ┌──────────▼──────────┐
                    │   lite_msgstore.db   │ (NUOVO: schema compatibile root)
                    └──────────┬──────────┘
                               │
              ┌────────────────┼────────────────┐
              │                │                │
     ┌────────▼───────┐ ┌─────▼─────┐ ┌────────▼───────┐
     │   ChatDao       │ │MessageDao │ │  ContactDao    │
     │ (invariato)     │ │(invariato)│ │ (stub/minimal) │
     └────────┬───────┘ └─────┬─────┘ └────────┬───────┘
              │                │                │
     ┌────────▼────────────────▼────────────────▼───────┐
     │              AnalyticsRepository                  │
     │     (un solo codepath — no branch isLiteMode)     │
     └──────────────────────┬───────────────────────────┘
                            │
                    ┌───────▼───────┐
                    │      UI       │ (identica per root e lite)
                    └───────────────┘
```

Il punto chiave è che `WhatsAppDatabase.open(msgstoreFile, waDbFile)` oggi apre i DB root. Per la Lite Mode, la stessa chiamata aprirà `lite_msgstore.db` e un `lite_wa.db` (stub vuoto con la tabella `wa_contacts`). I DAO non cambiano: leggono da `database.getMsgDb()` che punta al DB giusto.

---

## 3. Schema del database Lite

Il DB Lite deve contenere le tabelle minime che i DAO root interrogano. Le tabelle non rilevanti (call_log, bot_*, ai_*, ecc.) **non** vengono create — i DAO che le usano restituiranno semplicemente risultati vuoti.

### 3.1 — Tabella `jid`

Mappa i mittenti a un ID numerico, come in `msgstore.db`.

```sql
CREATE TABLE jid (
    _id         INTEGER PRIMARY KEY AUTOINCREMENT,
    raw_string  TEXT NOT NULL UNIQUE,  -- es. "Mario Rossi" (Lite) o "39123@s.whatsapp.net" (root)
    server      TEXT NOT NULL DEFAULT 's.whatsapp.net',  -- Lite: sempre 's.whatsapp.net' (chat 1:1) o 'g.us' (gruppo)
    user        TEXT  -- il nome utente
);
```

**Nota**: in Lite Mode, `raw_string` contiene il **nome del mittente** (es. `"Mario Rossi"`), non il JID reale di WhatsApp. Questo è l'unico dato disponibile dall'export `.txt`. Per l'utente corrente, `raw_string` sarà il `ownerName` scelto durante l'import.

### 3.2 — Tabella `chat`

Una riga per ogni chat importata (= per ogni ZIP).

```sql
CREATE TABLE chat (
    _id                 INTEGER PRIMARY KEY AUTOINCREMENT,
    jid_row_id          INTEGER UNIQUE,   -- FK → jid._id del contatto/gruppo
    hidden              INTEGER DEFAULT 0,
    subject             TEXT,              -- nome della chat (es. "Giovanni" o "Gruppo Amici")
    sort_timestamp      INTEGER,           -- timestamp ultimo messaggio
    display_message_row_id INTEGER,
    last_message_row_id    INTEGER,
    unseen_message_count   INTEGER DEFAULT 0,
    group_type          INTEGER DEFAULT 0  -- 0 = chat singola, 1 = gruppo
);
```

### 3.3 — Tabella `message`

La tabella principale. Deve avere **esattamente le colonne** che i DAO root leggono nelle query SQL. Le colonne non popolabili dall'export `.txt` avranno valori di default.

```sql
CREATE TABLE message (
    _id                 INTEGER PRIMARY KEY AUTOINCREMENT,
    chat_row_id         INTEGER NOT NULL,          -- FK → chat._id
    from_me             INTEGER NOT NULL DEFAULT 0, -- 1 = isCurrentUser
    sender_jid_row_id   INTEGER DEFAULT 0,          -- FK → jid._id del mittente
    timestamp           INTEGER NOT NULL,            -- epoch millis
    message_type        INTEGER NOT NULL DEFAULT 0,  -- 0 = testo, 1 = immagine, 2 = audio, 3 = video, 9 = documento
    text_data           TEXT,                        -- contenuto messaggio
    -- Campi necessari per compatibilità DAO ma non popolati:
    key_id              TEXT,
    received_timestamp  INTEGER DEFAULT 0,
    receipt_server_timestamp INTEGER DEFAULT 0
);

-- Indici (stessi del root, per performance)
CREATE INDEX idx_message_chat_type_ts ON message (chat_row_id, message_type, timestamp);
CREATE INDEX idx_message_chat_type_from ON message (chat_row_id, message_type, from_me);
CREATE INDEX idx_message_sender_jid ON message (sender_jid_row_id);
CREATE INDEX idx_message_chat_text ON message (chat_row_id, message_type) WHERE text_data IS NOT NULL;
```

### 3.4 — Tabella `message_media`

Per i file media associati ai messaggi.

```sql
CREATE TABLE message_media (
    message_row_id  INTEGER PRIMARY KEY,   -- FK → message._id
    file_path       TEXT,                   -- percorso relativo del file (es. "IMG-20240101-WA0001.jpg")
    mime_type       TEXT,                   -- "image/jpeg", "video/mp4", "audio/ogg", "application/pdf"
    file_length     INTEGER DEFAULT 0,
    file_hash       TEXT
);

CREATE INDEX idx_message_media_row ON message_media (message_row_id);
```

### 3.5 — Tabella `jid_map` (stub)

Diversi DAO root fanno JOIN con `jid_map` (LID → JID reale). In Lite Mode non esiste questa mappatura, ma la tabella deve esistere perché le query SQL la referenziano con `LEFT JOIN`.

```sql
CREATE TABLE jid_map (
    _id         INTEGER PRIMARY KEY AUTOINCREMENT,
    lid_row_id  INTEGER,
    jid_row_id  INTEGER
);
```

### 3.6 — Tabella `wa_contacts` (nel lite_wa.db)

Stub vuoto per `ContactDao`. Le query restituiranno nomi vuoti; la UI mostra il nome dall'export.

```sql
CREATE TABLE wa_contacts (
    _id         INTEGER PRIMARY KEY AUTOINCREMENT,
    jid         TEXT UNIQUE,
    display_name TEXT
);
```

### 3.7 — Tabelle NON necessarie

Le seguenti tabelle root **non** vengono create nel DB Lite. Se un DAO prova a fare query su queste, restituirà risultati vuoti o errore catturato:

- `call_log` / `call_log_participant_v2` — chiamate non presenti nel `.txt`
- `addon_message_media` — non usato
- `message_system` / `system_message_type` — eventi di sistema gestiti diversamente
- Tutte le tabelle `ai_*`, `bot_*`, `agent_*`

**Nota importante**: il DAO root usa `addon_message_media` in una UNION con `message_media` in `getMediaFiles()` e `getLinkStats()`. Per evitare crash su tabella mancante, **creare `addon_message_media` come tabella vuota** con lo stesso schema di `message_media`.

```sql
CREATE TABLE addon_message_media (
    _id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    message_row_id       INTEGER NOT NULL,
    addon_message_index  INTEGER NOT NULL,
    chat_row_id          INTEGER,
    file_path            TEXT,
    mime_type            TEXT,
    file_length          INTEGER
);
```

---

## Fase 1 — Creare il database e il writer

> **Obiettivo**: creare `LiteDatabase` (gestione SQLite) e `LiteDbWriter` (converte ParseResult → righe SQL).

### File da creare

#### 1.1 — `data/lite/LiteDatabase.kt`

```kotlin
@Singleton
class LiteDatabase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var db: SQLiteDatabase? = null

    fun getOrCreate(): SQLiteDatabase {
        if (db?.isOpen == true) return db!!
        val file = File(context.filesDir, "lite_msgstore.db")
        db = SQLiteDatabase.openOrCreateDatabase(file, null)
        db!!.enableWriteAheadLogging()
        createSchema(db!!)
        return db!!
    }

    fun getWaDb(): SQLiteDatabase {
        // Stub: wa.db per ContactDao
        val file = File(context.filesDir, "lite_wa.db")
        val waDb = SQLiteDatabase.openOrCreateDatabase(file, null)
        waDb.execSQL("CREATE TABLE IF NOT EXISTS wa_contacts (_id INTEGER PRIMARY KEY, jid TEXT UNIQUE, display_name TEXT)")
        return waDb
    }

    private fun createSchema(db: SQLiteDatabase) {
        db.execSQL(/* CREATE TABLE jid ... come sopra */)
        db.execSQL(/* CREATE TABLE chat ... come sopra */)
        db.execSQL(/* CREATE TABLE message ... come sopra */)
        db.execSQL(/* CREATE TABLE message_media ... come sopra */)
        db.execSQL(/* CREATE TABLE jid_map (stub) */)
        db.execSQL(/* CREATE TABLE addon_message_media (stub vuoto) */)
        // Crea indici
    }

    fun close() { db?.close(); db = null }

    fun deleteAll() {
        close()
        File(context.filesDir, "lite_msgstore.db").delete()
        File(context.filesDir, "lite_wa.db").delete()
    }
}
```

**Note**:
- Usare `filesDir` (non `cacheDir`) perché il DB deve persistere.
- Lo schema viene creato con `CREATE TABLE IF NOT EXISTS` → idempotente.
- `enableWriteAheadLogging()` per letture concorrenti dai ViewModel.

#### 1.2 — `data/lite/LiteDbWriter.kt`

```kotlin
@Singleton
class LiteDbWriter @Inject constructor(
    private val liteDatabase: LiteDatabase
) {
    /**
     * Scrive una chat importata nel DB Lite.
     * Gestisce: jid, chat, message, message_media.
     * Ritorna il chatId inserito.
     */
    suspend fun writeChat(
        chatName: String,
        ownerName: String,
        isGroup: Boolean,
        messages: List<ParsedMessage>,
        mediaItems: List<LiteMediaItem>,
        progressCallback: (Float) -> Unit = {}
    ): Long = withContext(Dispatchers.IO) {
        val db = liteDatabase.getOrCreate()

        // 1. Inserisci/recupera jid per ogni mittente
        val senderJidMap = mutableMapOf<String, Long>() // senderName → jid._id
        val allSenders = messages.map { it.senderName }.distinct()
        for (sender in allSenders) {
            senderJidMap[sender] = getOrInsertJid(db, sender)
        }

        // 2. Crea entry chat
        val chatJidId = getOrInsertJid(db, chatName, if (isGroup) "g.us" else "s.whatsapp.net")
        val chatId = insertChat(db, chatJidId, chatName, isGroup)

        // 3. Inserisci messaggi in batch (transaction)
        db.beginTransaction()
        try {
            val total = messages.size
            messages.forEachIndexed { i, msg ->
                val senderJidId = senderJidMap[msg.senderName] ?: 0L
                val messageType = if (msg.hasMedia) guessMediaType(msg.content) else 0
                val textData = if (msg.hasMedia) null else msg.content

                insertMessage(db, chatId, msg, senderJidId, messageType, textData)

                if (i % 500 == 0) progressCallback(i.toFloat() / total)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }

        // 4. Inserisci media
        insertMediaItems(db, chatId, mediaItems, messages)

        progressCallback(1f)
        chatId
    }

    private fun getOrInsertJid(db: SQLiteDatabase, rawString: String, server: String = "s.whatsapp.net"): Long {
        // SELECT _id FROM jid WHERE raw_string = ?
        // Se non esiste: INSERT INTO jid (raw_string, server, user) VALUES (?, ?, ?)
        // Ritorna _id
    }

    private fun insertChat(db: SQLiteDatabase, jidRowId: Long, subject: String, isGroup: Boolean): Long {
        // Controlla se la chat esiste già (stesso jid_row_id)
        // Se sì: cancella i vecchi messaggi e media (reimport)
        // Se no: INSERT INTO chat
        // Ritorna chat._id
    }

    private fun insertMessage(db: SQLiteDatabase, chatId: Long, msg: ParsedMessage, senderJidId: Long, messageType: Int, textData: String?) {
        // INSERT INTO message (chat_row_id, from_me, sender_jid_row_id, timestamp, message_type, text_data)
        // VALUES (?, ?, ?, ?, ?, ?)
        //
        // Nota: timestamp deve essere epoch millis. ParsedMessage.timestamp è LocalDateTime.
        // Conversione: msg.timestamp.toInstant(ZoneOffset.systemDefault().rules.getOffset(msg.timestamp)).toEpochMilli()
    }

    private fun guessMediaType(content: String): Int {
        // Basato sul prefisso filename nel content:
        // IMG- → 1 (immagine)
        // AUD-/PTT- → 2 (audio)
        // VID- → 3 (video)
        // DOC- → 9 (documento)
        // default → 0
    }

    private fun insertMediaItems(db: SQLiteDatabase, chatId: Long, items: List<LiteMediaItem>, messages: List<ParsedMessage>) {
        // Per ogni LiteMediaItem, trova il message._id corrispondente (per timestamp + senderName)
        // INSERT INTO message_media (message_row_id, file_path, mime_type, file_length)
    }

    /**
     * Cancella una chat e tutti i suoi messaggi/media dal DB Lite.
     */
    suspend fun deleteChat(chatId: Long) = withContext(Dispatchers.IO) {
        val db = liteDatabase.getOrCreate()
        db.beginTransaction()
        try {
            db.execSQL("DELETE FROM message_media WHERE message_row_id IN (SELECT _id FROM message WHERE chat_row_id = ?)", arrayOf(chatId.toString()))
            db.execSQL("DELETE FROM message WHERE chat_row_id = ?", arrayOf(chatId.toString()))
            db.execSQL("DELETE FROM chat WHERE _id = ?", arrayOf(chatId.toString()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
}
```

### Test di validazione Fase 1

- [ ] Scrivi un test che crea il DB, inserisce 100 messaggi fittizi, e poi esegue `MessageDao.getTotalMessageCount()` → deve restituire 100.
- [ ] Scrivi un test che inserisce 2 chat e verifica che `ChatDao.getChatList()` le restituisca entrambe.

---

## Fase 2 — Adattare i DAO per supportare due sorgenti

> **Obiettivo**: fare in modo che `ChatDao`, `MessageDao`, `ContactDao` possano leggere da entrambi i DB senza duplicare codice.

### Approccio: `WhatsAppDatabase` come interfaccia comune

Attualmente i DAO iniettano `WhatsAppDatabase` e chiamano `getMsgDb()`. La strategia è:

1. **Estrarre un'interfaccia** `DatabaseProvider`:
   ```kotlin
   interface DatabaseProvider {
       fun getMsgDb(): SQLiteDatabase
       fun getWaDb(): SQLiteDatabase
       fun isOpen(): Boolean
   }
   ```

2. **`WhatsAppDatabase` implementa `DatabaseProvider`** (nessuna modifica di comportamento root).

3. **`LiteDatabaseProvider` implementa `DatabaseProvider`**:
   ```kotlin
   @Singleton
   class LiteDatabaseProvider @Inject constructor(
       private val liteDatabase: LiteDatabase
   ) : DatabaseProvider {
       override fun getMsgDb() = liteDatabase.getOrCreate()
       override fun getWaDb() = liteDatabase.getWaDb()
       override fun isOpen() = true // sempre aperto
   }
   ```

4. **I DAO iniettano `DatabaseProvider` invece di `WhatsAppDatabase`**:
   ```kotlin
   @Singleton
   class MessageDao @Inject constructor(
       private val database: DatabaseProvider  // era: WhatsAppDatabase
   )
   ```

5. **Hilt fornisce l'implementazione giusta** basandosi su un flag:
   ```kotlin
   @Module
   @InstallIn(SingletonComponent::class)
   abstract class DatabaseModule {
       // Bind di default a WhatsAppDatabase (root)
       // Quando isLiteMode = true, override via custom component o Qualifier
   }
   ```

   **Alternativa più semplice (consigliata per la prima iterazione)**: non usare Hilt per il dispatch. Invece, avere `AnalyticsRepository` che tiene entrambi i provider e chiama `setActiveProvider(lite/root)` durante il setup. I DAO continuano a iniettare un singolo `DatabaseProvider` wrapper che delega al provider attivo.

### File da modificare

| File | Modifica |
|------|----------|
| `data/db/WhatsAppDatabase.kt` | Implementa `DatabaseProvider` (aggiungere `interface DatabaseProvider`) |
| `data/db/dao/ChatDao.kt` | Cambia tipo iniettato: `WhatsAppDatabase` → `DatabaseProvider` |
| `data/db/dao/MessageDao.kt` | Idem |
| `data/db/dao/ContactDao.kt` | Idem |
| `data/lite/LiteDatabaseProvider.kt` | Nuovo file, implementa `DatabaseProvider` |
| Modulo Hilt | Binding `DatabaseProvider` → wrapper con switch root/lite |

### Attenzione: query con tabelle mancanti

Alcune query root fanno JOIN con tabelle che non esistono in Lite. Per ogni query che crasha:

1. Verificare se la tabella è nello schema Lite (sezione 3).
2. Se no, creare uno **stub vuoto** (tabella con schema minimo, 0 righe).
3. Le query con `LEFT JOIN` su stub vuoti restituiscono semplicemente NULL → funzionano.
4. Le query con `JOIN` (inner) su stub vuoti restituiscono 0 righe → potrebbe essere un problema. **Convertire in LEFT JOIN** dove necessario.

**Tabelle stub necessarie** (da creare in `LiteDatabase.createSchema()`):
- `jid_map` — LEFT JOIN in molte query `MessageDao`
- `addon_message_media` — UNION in `getMediaFiles()`, `getLinkStats()`

### Test di validazione Fase 2

- [ ] Tutte le query root che funzionano su `msgstore.db` devono anche funzionare su `lite_msgstore.db` senza crash.
- [ ] Test: crea DB Lite con dati, chiama `ChatDao.getChatList()`, `MessageDao.getHourlyDistribution()`, `MessageDao.getTopWords()`.

---

## Fase 3 — Eliminare i branch Lite da AnalyticsRepository

> **Obiettivo**: rimuovere tutti i 16 `if (isLiteMode.value)` branch da `AnalyticsRepository`.

### Strategia

Una volta che i DAO leggono dal DB giusto (Fase 2), ogni metodo di `AnalyticsRepository` funziona ugualmente per root e lite. I branch da eliminare sono:

```
Riga 383:  getQuickGlobalStats → branch Lite
Riga 494:  getQuickGlobalStats → secondo branch
Riga 830:  getTopEmojis → branch Lite
Riga 871:  getEmojiStatsBySender → branch Lite
Riga 945:  getMediaFiles → branch Lite
Riga 1049: getLinkStats → branch Lite
Riga 1086: getLinkStatsBySender → branch Lite
Riga 1112: getWordSuggestions → branch Lite
Riga 1173: searchWordFrequency → branch Lite
Riga 1211: searchWordMessages → branch Lite
Riga 1234: getCallStats → branch Lite (ritorna lista vuota — corretto, non ci sono chiamate)
Riga 1255: getMediaStats → branch Lite
Riga 1596: getChatContextInfo → branch Lite
```

**Procedura per ciascun branch**:
1. Leggi la versione Lite del branch.
2. Verifica che la query DAO root produca un risultato equivalente se alimentata dal DB Lite.
3. Se sì: cancella il branch.
4. Se no (es. `getCallStats`): lascia un return di lista vuota solo se la tabella non esiste, oppure crea la tabella stub.

### Metodi da gestire con attenzione

| Metodo | Problema | Soluzione |
|--------|----------|-----------|
| `getCallStats` | `call_log` non esiste in Lite | Creare tabella stub vuota O lasciare un early return se la tab Calls non è mostrata |
| `getChatContextInfo` | Root usa `ContactDao.resolveContactName(jid)` | In Lite, `jid` è già il nome → `resolveContactName` restituirà null, usare `chat.subject` come fallback (già lo fa) |
| `getMediaFiles` | Root usa percorso `Media/WhatsApp Images/...` | Il writer deve scrivere il percorso relativo dello ZIP in `message_media.file_path` |
| Owner = "Tu" | Root usa `from_me = 1` | Il writer imposta `from_me = 1` per `isCurrentUser` → funziona |

### File da rimuovere dopo la migrazione

- `data/lite/LiteCacheRepository.kt` — non serve più (il DB sostituisce il JSON)
- `data/lite/LiteMetricsCalculator.kt` — le metriche le calcolano i DAO SQL
- I campi `liteModeManager.getCachedMessages()`, `getCachedMedia()`, `getCachedStats()` vengono eliminati; i dati si leggono dai DAO

### Metodi da mantenere in `LiteModeManager`

- `importAndProcess()` → diventa un wrapper che chiama `TextChatParser.parse()` + `LiteDbWriter.writeChat()` + `LiteMediaManager` (per copiare file media in una cartella stabile)
- `importProgress` → invariato
- `clearCache()` → diventa `liteDatabase.deleteAll()`

---

## Fase 4 — UI multi-chat e convergenza navigazione

> **Obiettivo**: la Lite Mode usa la stessa UI della root mode (Dashboard, ChatList, ChatDetail) con 4 tab.

### 4.1 — BottomNavigationBar condizionale

Attualmente la BottomNav ha 5 tab: **Panoramica, Chat, Media, Radar, Chiamate**.

In Lite Mode, mostrare **4 tab**: **Panoramica, Chat, Media, Radar** (nascondere Chiamate).

**File**: `ui/screen/home/AppScaffold.kt`

```kotlin
val tabs = if (isLiteMode) {
    listOf(Tab.Overview, Tab.Chats, Tab.Media, Tab.Radar)
} else {
    listOf(Tab.Overview, Tab.Chats, Tab.Media, Tab.Radar, Tab.Calls)
}
```

### 4.2 — DashboardScreen

Rimuovere il branch `if (isLiteMode) { ChatDetailScreen(chatId = -1L) }`.

La Dashboard mostra le stesse card statistiche globali. In Lite Mode con una sola chat importata, le statistiche globali coincidono con quelle della singola chat — OK, è coerente.

### 4.3 — ChatListScreen

Funziona già con `ChatDao.getChatList()`. Con il DB Lite che ha le stesse tabelle, la query restituisce la lista di chat importate.

**Aggiunta**: un FAB o pulsante "Importa chat" che apre il picker ZIP / naviga a `LiteSetupScreen` in modalità "aggiungi chat" (senza resettare il DB).

### 4.4 — ChatDetailScreen

Rimuovere il branch `chatId == -1L`. Ogni chat Lite ha un `chatId` reale nel DB.

### 4.5 — Navigazione: dopo import

**Attualmente**: import → `NavGraphRoute.Main` → Dashboard mostra ChatDetail direttamente.

**Target**: import → `NavGraphRoute.Main` → Dashboard (panoramica globale). L'utente naviga a Chat → sceglie la chat → ChatDetail.

**Variante UX (consigliata se una sola chat)**: se dopo l'import c'è solo 1 chat nel DB, auto-navigare a ChatDetail. Se ci sono 2+, mostrare Dashboard.

---

## Fase 5 — Import incrementale e gestione chat multiple

> **Obiettivo**: l'utente può importare più ZIP e gestire le chat importate.

### 5.1 — Reimport stessa chat

Se l'utente condivide nuovamente lo stesso ZIP (stessa chat), il `LiteDbWriter.insertChat()` deve:
1. Trovare la chat esistente per `jid_row_id` (il `chatName` è lo stesso).
2. Cancellare i vecchi messaggi e media di quella chat.
3. Reinserire tutto → la chat viene aggiornata senza duplicati.

### 5.2 — Import altra chat

Se l'utente condivide un ZIP diverso (altra chat), il writer crea una nuova riga in `chat` e nuovi messaggi. Il DB cresce.

### 5.3 — Cancellazione chat

In `ChatListScreen`, aggiungere un'opzione (long press o swipe) per cancellare una chat importata. Chiama `LiteDbWriter.deleteChat(chatId)`.

### 5.4 — Intent da WhatsApp (già funzionante)

Il flusso intent (`ACTION_SEND` con ZIP) è già implementato. L'unica modifica: dopo l'import, se l'app era già in Main graph con dati, non resettare tutto — semplicemente aggiungere la chat al DB e notificare i ViewModel tramite `liteCacheVersion` bump (o un meccanismo equivalente).

### 5.5 — Flusso UX per import aggiuntivo

```
App aperta su Dashboard
        ↓
Utente condivide ZIP da WhatsApp
        ↓  (onNewIntent)
Naviga a LiteSetupScreen (con incomingZipUri)
        ↓
Parser + Writer aggiungono la chat al DB
        ↓
Naviga a Main → Dashboard aggiornata con stats aggregate
```

---

## Fase 6 — Cleanup del vecchio sistema

> **Obiettivo**: rimuovere tutto il codice Lite JSON-based obsoleto.

### File da eliminare

| File | Motivo |
|------|--------|
| `data/lite/LiteCacheRepository.kt` | Sostituito da `LiteDatabase` |
| `data/lite/LiteMetricsCalculator.kt` | Metriche calcolate via SQL |
| I modelli `LiteCacheData`, `LiteMediaStats`, `LiteMediaItem` | Sostituiti dai modelli DB |

### Codice da rimuovere

- `AnalyticsRepository`: tutti i branch `if (isLiteMode.value)` e i metodi helper `filteredLiteMessages()`
- `LiteModeManager`: i metodi `getCachedStats()`, `getCachedMessages()`, `getCachedMedia()`, `getOwnerName()` — tutti sostituiti da query DAO
- `DashboardScreen`: branch `if (isLiteMode) { ChatDetailScreen(chatId = -1L) }`
- `ChatDetailViewModel`: logica speciale `chatId == -1L`

### Modelli da mantenere

- `ParsedMessage` — usato come DTO intermedio dal parser, ma non viene più serializzato in JSON. Valutare se convertirlo in un modello non-serializable.
- `TextChatParser` / `ParseResult` — invariati, producono l'input per `LiteDbWriter`.
- `LiteMediaManager` — serve ancora per scansionare i file nello ZIP ed estrarre tipo/percorso. Il suo output viene passato a `LiteDbWriter.insertMediaItems()`.

---

## Rischi e note importanti

### Conversione timestamp

`ParsedMessage.timestamp` è `LocalDateTime`. I DAO root lavorano con **epoch millis (Long)**. Il `LiteDbWriter` deve convertire:

```kotlin
val epochMillis = msg.timestamp
    .atZone(ZoneId.systemDefault())
    .toInstant()
    .toEpochMilli()
```

**Attenzione**: il fuso orario dell'export WhatsApp è quello del telefono al momento dell'export. Se l'utente ha cambiato fuso orario tra l'export e l'analisi, le date saranno sfasate. Questo è un limite intrinseco del `.txt`.

### `from_me` e il JID dell'owner

In root mode, `from_me = 1` identifica i messaggi inviati. Il `LiteDbWriter` deve impostare `from_me = 1` quando `msg.isCurrentUser == true`. Questo è già calcolato dal `TextChatParser` in base a `senderName == ownerName`.

Per le query che usano `"me@s.whatsapp.net"` come JID del sender:
- Il DAO root non inserisce mai `me@s.whatsapp.net` nella tabella `jid` — lo usa come stringa hard-coded.
- In Lite, lo stesso: quando `from_me = 1`, il `sender_jid_row_id` è 0 (non presente in `jid`).
- Le query `MessageDao` che costruiscono `CASE WHEN m.from_me = 1 THEN 'me@s.whatsapp.net'` funzioneranno identicamente.

### Dimensione del DB

Un export WhatsApp di 50k messaggi produce un DB Lite di circa 10–20 MB. Con 10 chat importate → 100–200 MB. Gestibile su qualsiasi dispositivo Android moderno.

### Migrazione dati esistenti

Se un utente ha già una cache JSON dal vecchio sistema:
1. Al primo avvio dopo l'aggiornamento, leggere la vecchia cache JSON.
2. Convertirla in righe del DB Lite via `LiteDbWriter`.
3. Cancellare il JSON.
4. Mostrare un messaggio "Migrazione completata".

### Ordine consigliato di implementazione

```
Fase 1 (DB + Writer)     → può essere testata isolatamente
Fase 2 (DAO adapter)     → richiede Fase 1
Fase 3 (cleanup branch)  → richiede Fase 2 + test approfonditi
Fase 4 (UI convergenza)  → può procedere in parallelo con Fase 3
Fase 5 (multi-chat)      → richiede Fase 4
Fase 6 (cleanup finale)  → solo dopo che tutto funziona
```

Tempo stimabile: Fase 1–2 sono le più critiche e complesse. Le Fasi 3–6 sono prevalentemente rimozione di codice e adattamenti UI.
