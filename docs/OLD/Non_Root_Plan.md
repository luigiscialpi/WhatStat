# Piano Dettagliato: WhatStat per Utenti Non-Root

## Obiettivo

Rendere l'app utilizzabile dal **99% degli utenti Android**, non solo da quelli con root. L'app deve guidare l'utente attraverso le opzioni disponibili e adattare automaticamente le funzionalità in base alla modalità di accesso scelta.

---

## 1. Le Tre Modalità di Accesso

L'app supporterà tre modalità distinte, ciascuna con un diverso livello di completezza.

### Matrice Funzionalità per Modalità

| Funzionalità | 🔓 Root | 🔑 Manuale (DB) | 📄 Export Testo |
|---|:---:|:---:|:---:|
| **Conteggio messaggi** | ✅ | ✅ | ✅ |
| **Inviati / Ricevuti** | ✅ | ✅ | ✅ |
| **Distribuzione oraria** | ✅ | ✅ | ⚠️ solo HH:MM |
| **Distribuzione settimanale** | ✅ | ✅ | ✅ |
| **Distribuzione mensile** | ✅ | ✅ | ✅ |
| **Top words / Word cloud** | ✅ | ✅ | ✅ |
| **Radar parole (ricerca)** | ✅ | ✅ | ✅ |
| **Messaggi più lunghi** | ✅ | ✅ | ✅ |
| **Chi inizia le conversazioni** | ✅ | ✅ | ✅ |
| **Tempo medio di risposta** | ✅ | ✅ | ⚠️ meno preciso (minuti, non secondi) |
| **Breakdown per mittente (gruppo)** | ✅ | ✅ | ✅ |
| **Nomi contatti** | ✅ (wa.db) | ⚠️ opzionale | ✅ (dal testo) |
| **Media count (foto/video/audio)** | ✅ | ✅ | ⚠️ solo `<Media omessi>` |
| **Dettaglio media per tipo** | ✅ | ✅ | ❌ |
| **Log chiamate (durata, tipo)** | ✅ | ✅ | ❌ |
| **Link condivisi** | ✅ | ✅ | ✅ (parsing URL) |
| **Filtri temporali (date range)** | ✅ | ✅ | ✅ |
| **Multi-chat (lista chat)** | ✅ | ✅ | ❌ (1 export = 1 chat) |

**Legenda**: ✅ completo | ⚠️ parziale | ❌ non disponibile

---

## 2. Nuova Schermata di Onboarding: "Scegli come accedere"

### 2.1 Design della Schermata

Prima della schermata di Setup attuale, introdurre una schermata di **scelta modalità** con 3 card Material 3, ciascuna con icona, titolo, sottotitolo e badge di completezza.

```
┌────────────────────────────────────────┐
│               WhatStat                 │
│    Come vuoi analizzare le chat?       │
│                                        │
│  ┌──────────────────────────────────┐  │
│  │ 🔓  Accesso Root (consigliato)   │  │
│  │  Automatico, tutte le funzioni   │  │
│  │  ★★★★★ Completezza               │  │
│  └──────────────────────────────────┘  │
│                                        │
│  ┌──────────────────────────────────┐  │
│  │ 🔑  Database da PC               │  │
│  │  Richiede procedura dal PC       │  │
│  │  ★★★★☆ Completezza               │  │
│  └──────────────────────────────────┘  │
│                                        │
│  ┌──────────────────────────────────┐  │
│  │ 📄  Export chat WhatsApp         │  │
│  │  Nessun requisito speciale       │  │
│  │  ★★★☆☆ Completezza               │  │
│  └──────────────────────────────────┘  │
│                                        │
│       ℹ️  Confronta le opzioni         │
└────────────────────────────────────────┘
```

### 2.2 File Coinvolti

| Azione | File | Descrizione |
|--------|------|-------------|
| **CREA** | `ui/screen/onboarding/OnboardingScreen.kt` | La nuova schermata di scelta |
| **CREA** | `ui/screen/onboarding/OnboardingViewModel.kt` | Gestione stato (modalità selezionata, navigazione) |
| **MODIFICA** | `ui/navigation/AppNavigation.kt` | Nuova route `onboarding` come `startDestination`, SetupScreen riceve la modalità scelta |
| **MODIFICA** | `ui/screen/setup/SetupScreen.kt` | Riceve `mode: SetupMode` come parametro di navigazione |

### 2.3 Enum `SetupMode`

```kotlin
enum class SetupMode {
    ROOT,           // Flusso attuale: root → estrai → decifra → apri
    MANUAL_DB,      // Flusso manuale attuale: key + crypt14/db → decifra → apri
    TEXT_EXPORT     // Nuovo: importa .txt → parse → analisi
}
```

---

## 3. Guida "Database da PC" — Istruzioni per l'Utente

### 3.1 Contenuto della Guida

Quando l'utente seleziona "Database da PC", mostrare una guida step-by-step **dentro l'app** (non un link esterno) con istruzioni chiare.

#### Schermata Guida (scrollabile, con illustrazioni)

```
┌────────────────────────────────────────┐
│  🔑  Procedura dal PC                 │
│                                        │
│  Questa procedura ti permette di       │
│  estrarre il database WhatsApp senza   │
│  root. Serve un PC con ADB.            │
│                                        │
│  ─── OPZIONE A: wa-crypt-tools ───     │
│                                        │
│  1️⃣ Installa Python 3 sul PC          │
│     python.org/downloads               │
│                                        │
│  2️⃣ Installa wa-crypt-tools           │
│     pip install wa-crypt-tools         │
│                                        │
│  3️⃣ Collega il telefono via USB       │
│     Attiva "Debug USB" nelle           │
│     Opzioni Sviluppatore               │
│                                        │
│  4️⃣ Estrai la chiave                  │
│     python -m wa_crypt_tools           │
│       extract-key                      │
│                                        │
│  5️⃣ Copia il backup crypt14           │
│     Il file si trova in:               │
│     /sdcard/WhatsApp/Databases/        │
│       msgstore.db.crypt14              │
│                                        │
│     adb pull /sdcard/WhatsApp/         │
│       Databases/msgstore.db.crypt14    │
│                                        │
│  6️⃣ Decifra il database               │
│     python -m wa_crypt_tools           │
│       decrypt14 key                    │
│       msgstore.db.crypt14              │
│       msgstore.db                      │
│                                        │
│  7️⃣ Trasferisci msgstore.db           │
│     sul telefono e selezionalo qui     │
│                                        │
│  ─── OPZIONE B: Solo chiave ──────    │
│                                        │
│  Se hai solo la chiave, seleziona      │
│  sia il file key che il crypt14:       │
│  l'app lo decifrerà per te.            │
│                                        │
│  ──── OPZIONE C: WhatsApp Viewer ──── │
│                                        │
│  1️⃣ Scarica "WhatsApp Viewer" dal PC  │
│     github.com/nicmarin/               │
│       WhatsApp-Viewer                  │
│                                        │
│  2️⃣ Segui la procedura integrata      │
│     per estrarre la chiave             │
│                                        │
│  ─────────────────────────────────     │
│                                        │
│  [ Ho il database decifrato ]          │
│  [ Ho chiave + file crypt14 ]          │
│                                        │
└────────────────────────────────────────┘
```

### 3.2 File Coinvolti

| Azione | File | Descrizione |
|--------|------|-------------|
| **CREA** | `ui/screen/guide/PcGuideScreen.kt` | Schermata guida scrollabile con step illustrati |
| **MODIFICA** | `ui/navigation/AppNavigation.kt` | Nuova route `guide_pc` con navigazione verso setup manuale |

### 3.3 Note di Sicurezza

- **NON** memorizzare la chiave crypt14 in chiaro dopo l'uso. Cancellarla dopo la decifratura.
- Mostrare un avviso: "La chiave e il database rimangono esclusivamente sul tuo dispositivo e non vengono mai trasmessi."
- Il file `key` va cancellato da `filesDir` dopo la decifratura riuscita (già fatto: dopo `decryptor.decrypt()`, il file non serve più).

---

## 4. Guida "Export Chat WhatsApp"

### 4.1 Istruzioni In-App

```
┌────────────────────────────────────────┐
│  📄  Export da WhatsApp                │
│                                        │
│  1️⃣ Apri WhatsApp                     │
│  2️⃣ Vai nella chat che vuoi           │
│     analizzare                         │
│  3️⃣ Tocca ⋮ (menu) → Altro →         │
│     Esporta chat                       │
│  4️⃣ Scegli "Senza media"             │
│  5️⃣ Condividi/salva il file .txt      │
│  6️⃣ Selezionalo qui sotto             │
│                                        │
│  ⚠️ Limitazioni:                       │
│  • Solo 1 chat per volta              │
│  • Niente statistiche chiamate        │
│  • Niente dettaglio media per tipo    │
│  • Precisione temporale ai minuti     │
│                                        │
│  [ Seleziona file .txt ]               │
│                                        │
└────────────────────────────────────────┘
```

### 4.2 File Coinvolti

| Azione | File | Descrizione |
|--------|------|-------------|
| **CREA** | `ui/screen/guide/ExportGuideScreen.kt` | Schermata guida per export WhatsApp |
| **MODIFICA** | `ui/navigation/AppNavigation.kt` | Nuova route `guide_export` |

---

## 5. Parser del File di Testo Esportato

### 5.1 Formato Analizzato

```
DD/MM/YY, HH:MM - <sistema>
DD/MM/YY, HH:MM - <mittente>: <messaggio>
DD/MM/YY, HH:MM - <mittente>: <messaggio che continua
sulla riga successiva>
```

**Messaggi di sistema** (senza `:`  dopo il mittente):
- `I messaggi e le chiamate sono crittografati end-to-end...`
- `<Nome> ha creato il gruppo "<Nome>"`
- `<Nome> ha aggiunto <Nome>`
- `<Nome> ha cambiato il numero di telefono...`
- `Hai cambiato l'immagine del gruppo`

**Messaggi speciali**:
- `<Media omessi>` → conteggio media generico
- `Questo messaggio è stato eliminato` → messaggio cancellato
- `<Allegato: XXXX.pdf>` → file allegato (raro nel formato senza media)
- Link (URL) → estraibili con regex

### 5.2 Regex Multi-Locale

Il formato del timestamp varia per lingua:
- **IT**: `06/10/25, 12:50 - Luigi: ciao`
- **EN (US)**: `10/6/25, 12:50 PM - Luigi: ciao`  
- **EN (UK)**: `06/10/2025, 12:50 - Luigi: ciao`
- **DE**: `06.10.25, 12:50 - Luigi: ciao`
- **PT-BR**: `06/10/2025 12:50 - Luigi: ciao`

```kotlin
// Regex robusto che copre i formati più comuni
private val MESSAGE_REGEX = Regex(
    """^(\d{1,2}[/.\-]\d{1,2}[/.\-]\d{2,4})[,\s]+(\d{1,2}:\d{2}(?::\d{2})?(?:\s?[APap][Mm])?)\s*[-–]\s*(.+?):\s(.+)$"""
)

private val SYSTEM_REGEX = Regex(
    """^(\d{1,2}[/.\-]\d{1,2}[/.\-]\d{2,4})[,\s]+(\d{1,2}:\d{2}(?::\d{2})?(?:\s?[APap][Mm])?)\s*[-–]\s*(.+)$"""
)
```

### 5.3 Modello Dati Parsed

```kotlin
data class ParsedMessage(
    val timestamp: Long,          // epoch ms (precisione al minuto)
    val senderName: String,       // nome esatto come appare nel testo
    val text: String,             // corpo del messaggio (può essere multilinea)
    val isSystemMessage: Boolean, // true se messaggio di sistema
    val isMedia: Boolean,         // true se "<Media omessi>"
    val isDeleted: Boolean        // true se "Questo messaggio è stato eliminato"
)

data class ParsedChat(
    val fileName: String,                  // nome del file .txt importato
    val participants: Set<String>,         // nomi unici dei partecipanti
    val messages: List<ParsedMessage>,     // messaggi ordinati per timestamp
    val isGroup: Boolean,                  // true se >2 partecipanti
    val firstMessageDate: Long,
    val lastMessageDate: Long
)
```

### 5.4 Architettura del Parser

```
┌─────────────────┐
│  File .txt      │
│  (Uri da picker)│
└────────┬────────┘
         │ ContentResolver.openInputStream()
         ▼
┌─────────────────┐
│  ChatTxtParser  │  ← nuovo file
│  (riga per riga)│
└────────┬────────┘
         │ ParsedChat
         ▼
┌─────────────────────────┐
│  TextExportRepository   │  ← nuovo file
│  (calcola le statistiche│
│   in-memory su           │
│   List<ParsedMessage>)   │
└────────┬────────────────┘
         │ ChatStats / GlobalStats
         ▼
┌────────────────────────┐
│  AnalyticsRepository   │  ← modificato: delega a TextExportRepository
│  (interfaccia unica)   │    se mode == TEXT_EXPORT
└────────────────────────┘
```

### 5.5 File Coinvolti

| Azione | File | Descrizione |
|--------|------|-------------|
| **CREA** | `data/parser/ChatTxtParser.kt` | Parser riga-per-riga del formato WhatsApp export |
| **CREA** | `data/parser/ParsedModels.kt` | `ParsedMessage`, `ParsedChat` data classes |
| **CREA** | `data/repository/TextExportRepository.kt` | Calcola statistiche in-memory da `ParsedChat` |
| **MODIFICA** | `data/repository/AnalyticsRepository.kt` | Aggiunta delega a `TextExportRepository` |
| **MODIFICA** | `domain/model/Stats.kt` | Nessuna modifica (riusa `ChatStats` e `GlobalStats` esistenti) |

---

## 6. Dettaglio Implementazione Parser: `ChatTxtParser.kt`

### 6.1 Pseudocodice

```kotlin
@Singleton
class ChatTxtParser @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    /**
     * Parsa un file .txt esportato da WhatsApp.
     * Streaming riga per riga — non carica tutto in memoria.
     */
    suspend fun parse(uri: Uri): Result<ParsedChat> = withContext(Dispatchers.IO) {
        runCatching {
            val messages = mutableListOf<ParsedMessage>()
            var currentMessage: StringBuilder? = null
            var currentSender: String? = null
            var currentTimestamp: Long = 0
            var lineCount = 0
            
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.useLines { lines ->
                for (line in lines) {
                    lineCount++
                    val match = MESSAGE_REGEX.matchEntire(line)
                    if (match != null) {
                        // Salva il messaggio precedente (se multilinea)
                        flushMessage(currentTimestamp, currentSender, currentMessage, messages)
                        
                        // Nuova riga di messaggio
                        val (dateStr, timeStr, sender, text) = match.destructured
                        currentTimestamp = parseDateTime(dateStr, timeStr)
                        currentSender = sender
                        currentMessage = StringBuilder(text)
                    } else {
                        val sysMatch = SYSTEM_REGEX.matchEntire(line)
                        if (sysMatch != null) {
                            flushMessage(currentTimestamp, currentSender, currentMessage, messages)
                            // Messaggio di sistema — ignora
                            currentMessage = null
                            currentSender = null
                        } else {
                            // Continuazione multilinea
                            currentMessage?.append("\n")?.append(line)
                        }
                    }
                }
                // Flush ultimo messaggio
                flushMessage(currentTimestamp, currentSender, currentMessage, messages)
            }
            
            val participants = messages.filter { !it.isSystemMessage }.map { it.senderName }.toSet()
            
            ParsedChat(
                fileName = getFileName(uri),
                participants = participants,
                messages = messages,
                isGroup = participants.size > 2,
                firstMessageDate = messages.firstOrNull()?.timestamp ?: 0,
                lastMessageDate = messages.lastOrNull()?.timestamp ?: 0
            )
        }
    }
    
    /**
     * Auto-detect del formato data (DD/MM vs MM/DD).
     * Strategia: scorre le prime 50 righe e controlla
     * se il primo campo supera 12 → è il giorno (formato europeo).
     */
    private fun detectDateFormat(lines: List<String>): DateFormat { ... }
}
```

### 6.2 Gestione Formati Data

Il problema più insidioso è distinguere `DD/MM/YY` da `MM/DD/YY`.

**Strategia di auto-detection**:
1. Leggi le prime 100 righe con timestamp
2. Estrai tutti i valori del primo campo numerico
3. Se almeno uno supera 12 → formato `DD/MM` (europeo)
4. Se almeno uno del secondo campo supera 12 → formato `MM/DD` (americano)
5. Se ambiguo → chiedi all'utente con un dialog Material 3

```kotlin
enum class DateOrder {
    DAY_MONTH,   // DD/MM/YY — Europa, Italia, UK
    MONTH_DAY    // MM/DD/YY — USA
}
```

### 6.3 Gestione AM/PM

```kotlin
private fun parseTime(timeStr: String): Pair<Int, Int> {
    val isPM = timeStr.contains("PM", ignoreCase = true)
    val isAM = timeStr.contains("AM", ignoreCase = true)
    val cleaned = timeStr.replace(Regex("[APap][Mm]"), "").trim()
    val parts = cleaned.split(":")
    var hour = parts[0].toInt()
    val minute = parts[1].toInt()
    
    if (isPM && hour != 12) hour += 12
    if (isAM && hour == 12) hour = 0
    
    return hour to minute
}
```

---

## 7. `TextExportRepository.kt` — Statistiche In-Memory

### 7.1 Responsabilità

Questo repository replica la logica di `AnalyticsRepository` ma opera su `List<ParsedMessage>` in memoria anziché su query SQL. Poiché i file di export sono tipicamente < 100K righe (WhatsApp limita a ~40K messaggi per export senza media), il calcolo in-memory è performante.

### 7.2 Funzioni da Implementare

```kotlin
@Singleton
class TextExportRepository @Inject constructor() {

    private var parsedChat: ParsedChat? = null
    
    fun loadChat(chat: ParsedChat) { parsedChat = chat }

    // --- Quick Stats (< 1s) ---
    
    fun getTotalMessages(): Int
    fun getSentByMe(myName: String): Int
    fun getReceivedFromThem(myName: String): Int
    fun getHourlyDistribution(): List<HourlyCount>
    fun getWeeklyDistribution(): List<DayCount>
    fun getMonthlyTrend(): List<MonthlyCount>
    fun getMediaCount(): Int  // conta "<Media omessi>"
    fun getFirstMessageDate(): Long
    fun getLastMessageDate(): Long
    
    // --- Slow Stats ---
    
    fun getTopWords(limit: Int = 0): List<WordFrequency>
    fun getSenderBreakdown(): List<SenderStat>
    fun getConversationStarters(gapMinutes: Int = 360): List<SenderStat>
    fun getAverageResponseTimeMs(myName: String): Long
    fun getLongestMessage(): String
    fun searchWordFrequency(word: String): List<WordSearchResult>
    fun extractLinks(): List<String>
}
```

### 7.3 Identificazione di "me"

**Problema**: nel file .txt non c'è un flag `from_me`. Serve identificare quale mittente è l'utente corrente.

**Soluzioni (in ordine di preferenza)**:
1. **Chiedi all'utente**: dopo il parsing, mostra un dialog "Seleziona il tuo nome" con la lista dei partecipanti
2. **Euristica**: in chat 1:1, il nome meno frequente è spesso il proprietario (ma non è affidabile)

**Implementazione**: dialog Material 3 con `RadioButton` per ogni partecipante, mostrato subito dopo il parsing.

```
┌────────────────────────────────────┐
│  Chi sei tu in questa chat?        │
│                                    │
│  ○  Luigi                          │
│  ○  Matilde Barbaro                │
│                                    │
│            [ Conferma ]            │
└────────────────────────────────────┘
```

---

## 8. Modifiche al Layer di Navigazione e Stato

### 8.1 Nuova Variabile Globale: `DataSource`

```kotlin
sealed class DataSource {
    object SqliteDatabase : DataSource()
    data class TextExport(val myName: String) : DataSource()
}
```

Salvata nel `WhatsAppRepository` o in un nuovo `SessionManager`, accessibile da tutti i ViewModel.

### 8.2 Modifiche alla Navigazione

```
startDestination = "onboarding"  // era "setup"

onboarding → (ROOT)        → setup (flusso attuale)
onboarding → (MANUAL_DB)   → guide_pc → setup (con file picker)
onboarding → (TEXT_EXPORT)  → guide_export → text_import → dashboard
```

**Nuove routes**:
- `onboarding` — schermata scelta modalità
- `guide_pc` — guida procedura da PC
- `guide_export` — guida export WhatsApp
- `text_import` — parsing + selezione "chi sono io"

### 8.3 Impatto sui ViewModel Esistenti

| ViewModel | Cambiamento |
|-----------|-------------|
| `DashboardViewModel` | Se `DataSource == TextExport`: mostra stats di una sola chat, nasconde "Lista Chat" |
| `ChatListViewModel` | Non accessibile in modalità TextExport |
| `ChatDetailViewModel` | Delega a `TextExportRepository` se `DataSource == TextExport` |
| `WordSearchViewModel` | Delega a `TextExportRepository.searchWordFrequency()` |
| `CallsStatsViewModel` | Nasconde la sezione o mostra "Non disponibile con export testuale" |
| `MediaStatsViewModel` | Mostra solo conteggio generico (no breakdown per tipo) |

### 8.4 Modifiche a `AnalyticsRepository`

```kotlin
@Singleton
class AnalyticsRepository @Inject constructor(
    private val messageDao: MessageDao,
    private val chatDao: ChatDao,
    private val contactDao: ContactDao,
    private val textExportRepo: TextExportRepository  // nuovo
) {
    var dataSource: DataSource = DataSource.SqliteDatabase
    
    suspend fun getQuickChatStats(chatId: Long, ...): ChatStats {
        return when (dataSource) {
            is DataSource.SqliteDatabase -> getQuickChatStatsFromDb(chatId, ...)
            is DataSource.TextExport -> getQuickChatStatsFromText(...)
        }
    }
    
    // ... pattern analogo per tutte le funzioni
}
```

---

## 9. Adattamenti UI per Modalità Text Export

### 9.1 Sezioni da Nascondere/Modificare

| Schermata | Elemento | Comportamento in Text Export |
|-----------|----------|------------------------------|
| **Dashboard** | Card "Analisi Chat" | → Mostra direttamente le stats (no lista) |
| **Dashboard** | Card "Log Chiamate" | → Nascosta o badge "Non disponibile" |
| **Dashboard** | Card "Media & Link" | → Solo conteggio generico |
| **Dashboard** | Stats globali | → Stats della singola chat importata |
| **ChatDetail** | Pie chart media tipo | → Nascosto (no breakdown audio/video/foto) |
| **ChatDetail** | Tempo risposta | → Mostra con disclaimer "precisione ±1 min" |
| **Navigation** | Bottom bar / Menu | → Nascondi "Chiamate" se non disponibile |

### 9.2 Banner Informativo

In ogni schermata, se `DataSource == TextExport`, mostrare un banner compatto:

```
┌──────────────────────────────────────┐
│ 📄 Modalità export — alcune         │
│ statistiche potrebbero essere        │
│ limitate. Usa la modalità DB per     │
│ dati completi.                       │
└──────────────────────────────────────┘
```

Implementato come composable riutilizzabile `TextExportBanner()`.

---

## 10. Gestione Multi-Import (Export Multipli)

### 10.1 Problema

Ogni export WhatsApp contiene una sola chat. L'utente potrebbe voler analizzare più chat.

### 10.2 Soluzione

Permettere di importare più file `.txt` e aggregarli:

1. Nella schermata `text_import`, pulsante **"+ Aggiungi altra chat"**
2. Ogni file viene parsato e aggiunto alla lista
3. Il `TextExportRepository` mantiene una `Map<String, ParsedChat>` (chiave = nome file)
4. La Dashboard mostra tutte le chat importate come se fossero nella lista chat
5. Le stats globali vengono aggregate da tutti i file importati

```kotlin
data class TextExportSession(
    val chats: MutableMap<String, ParsedChat> = mutableMapOf(),
    val myName: String = ""  // consistente tra tutti i file
)
```

### 10.3 Persistenza Sessione

I file `.txt` parsati possono essere serializzati con `kotlinx.serialization` in un file JSON nella `filesDir` dell'app, per non dover ri-parsare ad ogni apertura.

---

## 11. Piano di Implementazione — Fasi

### Fase 1: Fondamenta (Priorità ALTA)

| # | Task | File | Stima complessità |
|---|------|------|-------------------|
| 1.1 | Creare `ChatTxtParser.kt` con regex multi-locale | `data/parser/ChatTxtParser.kt` | Media |
| 1.2 | Creare `ParsedModels.kt` | `data/parser/ParsedModels.kt` | Bassa |
| 1.3 | Creare `TextExportRepository.kt` con tutte le stats in-memory | `data/repository/TextExportRepository.kt` | Alta |
| 1.4 | Unit test del parser con file di esempio IT, EN-US, EN-UK | `test/` | Media |

### Fase 2: Onboarding e Guide (Priorità ALTA)

| # | Task | File | Stima complessità |
|---|------|------|-------------------|
| 2.1 | Creare `OnboardingScreen.kt` con 3 card | `ui/screen/onboarding/` | Media |
| 2.2 | Creare `PcGuideScreen.kt` con istruzioni step-by-step | `ui/screen/guide/` | Bassa |
| 2.3 | Creare `ExportGuideScreen.kt` con istruzioni WhatsApp | `ui/screen/guide/` | Bassa |
| 2.4 | Creare `TextImportScreen.kt` (picker + parsing + "chi sei tu") | `ui/screen/textimport/` | Media |
| 2.5 | Aggiornare `AppNavigation.kt` con le nuove route | `ui/navigation/` | Media |

### Fase 3: Integrazione (Priorità MEDIA)

| # | Task | File | Stima complessità |
|---|------|------|-------------------|
| 3.1 | Aggiungere `DataSource` sealed class | `domain/model/` | Bassa |
| 3.2 | Modificare `AnalyticsRepository` per delegare a `TextExportRepository` | `data/repository/` | Alta |
| 3.3 | Aggiornare tutti i ViewModel per gestire `DataSource` | `ui/screen/*/` | Media |
| 3.4 | Nascondere/modificare sezioni UI non disponibili in TextExport | `ui/screen/*/` | Media |
| 3.5 | Creare `TextExportBanner()` composable | `ui/component/` | Bassa |

### Fase 4: Polish e Multi-Import (Priorità BASSA)

| # | Task | File | Stima complessità |
|---|------|------|-------------------|
| 4.1 | Multi-import (aggiungi più .txt) | `ui/screen/textimport/` | Media |
| 4.2 | Persistenza sessione (JSON serialization) | `data/repository/` | Media |
| 4.3 | Auto-detect formato data con fallback dialog | `data/parser/` | Bassa |
| 4.4 | Supporto formato 24h e 12h (AM/PM) | `data/parser/` | Bassa |
| 4.5 | Aggiornare documentazione | `docs/` | Bassa |

---

## 12. Rischi e Mitigazioni

| Rischio | Probabilità | Impatto | Mitigazione |
|---------|-------------|---------|-------------|
| Formato `.txt` cambia tra versioni WhatsApp | Media | Alto | Regex flessibile + test con file reali di diverse versioni |
| Formato data ambiguo (DD/MM vs MM/DD) | Alta | Medio | Auto-detection + fallback dialog utente |
| Messaggi multilinea spezzano il parsing | Media | Medio | Riga non inizia con timestamp → appendere al messaggio precedente |
| Encoding Unicode (emoji, RTL, ecc.) | Bassa | Medio | `BufferedReader` con UTF-8 esplicito |
| Export WhatsApp ha limite ~40K messaggi | Media | Medio | Avviso all'utente se il file sembra troncato |
| Utente non sa usare ADB per chiave | Alta | Alto | Guida chiara con screenshot + opzione export testo come fallback |
| Performance con file molto grandi | Bassa | Basso | Streaming riga per riga, mai caricare tutto in memoria |

---

## 13. Riepilogo Architettura Finale

```
                    ┌─────────────┐
                    │ Onboarding  │
                    │   Screen    │
                    └──────┬──────┘
               ┌───────────┼───────────┐
               ▼           ▼           ▼
         ┌──────────┐ ┌──────────┐ ┌──────────┐
         │  Root    │ │ PC Guide │ │  Export  │
         │  Setup   │ │ → Manual │ │  Guide   │
         │          │ │   Setup  │ │ → Import │
         └────┬─────┘ └────┬─────┘ └────┬─────┘
              │            │            │
              ▼            ▼            ▼
         ┌─────────────────────────────────┐
         │       AnalyticsRepository       │
         │   ┌──────────┐ ┌────────────┐   │
         │   │ SQLite   │ │ TextExport │   │
         │   │ DAOs     │ │ Repository │   │
         │   └──────────┘ └────────────┘   │
         └───────────────┬─────────────────┘
                         │ ChatStats / GlobalStats
                         ▼
         ┌─────────────────────────────────┐
         │          UI Screens             │
         │  (adattate per DataSource)      │
         └─────────────────────────────────┘
```
