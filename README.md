# WhatStat — Documentazione funzionale

> App Android nativa che legge, decifra e analizza il database di WhatsApp direttamente sul dispositivo. I contenuti delle chat non vengono mai inviati a server esterni. La versione Free utilizza Google AdMob per gli annunci; la versione Pro rimuove completamente la pubblicità.

---

## Indice

1. [Requisiti e compatibilità](#1-requisiti-e-compatibilità)
2. [Setup e primo avvio](#2-setup-e-primo-avvio)
3. [Sistema di cache e prestazioni](#3-sistema-di-cache-e-prestazioni)
4. [Filtri temporali](#4-filtri-temporali)
5. [Dashboard — Panoramica globale](#5-dashboard--panoramica-globale)
6. [Le tue chat — Lista e ranking](#6-le-tue-chat--lista-e-ranking)
 7. [Dettaglio chat](#7-dettaglio-chat)
 8. [Media Gallery](#8-media-gallery)
 9. [Radar Parole](#9-radar-parole)
10. [Privacy, Sicurezza e Build Automation](#10-privacy-sicurezza-e-build-automation)
11. [Gestione degli errori](#11-gestione-degli-errori)
12. [Struttura dei dati sorgente](#12-struttura-dei-dati-sorgente)
13. [Comportamenti noti e quirk del database](#13-comportamenti-noti-e-quirk-del-database)
14. [Struttura della navigazione](#14-struttura-della-navigazione)
15. [Tutorial di onboarding](#15-tutorial-di-onboarding)
16. [Roadmap](#16-roadmap)
17. [Internazionalizzazione (i18n)](#17-internazionalizzazione-i18n)
18. [Modalità Lite (non-root)](#18-modalità-lite-non-root)
19. [Linee Guida per Assistenti AI](#19-linee-guida-per-assistenti-ai)
20. [Monetizzazione: Ads e Pro](#20-monetizzazione-ads-e-pro)

---

## 1. Requisiti e compatibilità

Il funzionamento dell'app richiede:

- Dispositivo Android rootato, tipicamente con Magisk. I test su emulatore con root sono sconsigliati per dati reali.
- Android 8.0 (API 26) o superiore. La versione target è Android 16 (API 36).
- WhatsApp installato. Il flusso primario (Live DB) legge direttamente il database da `/data/user/*/com.whatsapp/databases/` e non dipende dal formato di backup. Il flusso fallback richiede che il backup locale sia attivo e in formato `crypt14` (backup E2E disattivato). Il formato `crypt15` (backup E2E attivo) non è ancora supportato nel fallback, ma è in roadmap con inserimento manuale della chiave a 64 caratteri esadecimali.

---

## 2. Setup e primo avvio

### Script rapido installazione Android (`bootstrap_project.sh`)

Per installare rapidamente l'app su device fisico o emulatore:

```bash
./bootstrap_project.sh
```

Comportamento:

- Variante di build di default: **Debug** (`:app:installDebug`).
- Override manuale: `./bootstrap_project.sh --release` per installare la Release.
- Modalita watch (senza Android Studio): `./bootstrap_project.sh --watch`.
- In watch mode, a ogni modifica in `app/src` o `app/build.gradle.kts` lo script reinstalla e riapre automaticamente l'app.
- Requisito watch mode: `fswatch` installato (macOS: `brew install fswatch`).
- Selezione target automatica in questo ordine:
    1. device fisico collegato via ADB
    2. emulatore gia avviato
    3. avvio di un nuovo AVD (con scelta interattiva)
- Dopo l'installazione, lo script tenta l'avvio automatico dell'app sul target.

### Schermata di setup (`SetupScreen` / `SetupViewModel`)

Al primo avvio l'app esegue in sequenza quattro operazioni, ognuna con un proprio indicatore di stato visibile nella schermata di setup:

**1. Verifica accesso root** — L'app controlla che la shell root sia disponibile e accessibile tramite Magisk. Se il root non è disponibile o i permessi sono stati negati, il processo si interrompe con un messaggio esplicativo.

**2. Estrazione file (Live-first)** — Con i permessi root, l'app tenta di copiare il database live direttamente dalla directory protetta di WhatsApp. L'ordine di copia è preciso per garantire coerenza transazionale:
- `msgstore.db-wal` (Write-Ahead Log, messaggi recenti)
- `msgstore.db-shm` (Shared Memory Map, indice del WAL)
- `msgstore.db` (database principale)
- `wa.db-wal`, `wa.db-shm`, `wa.db` (contatti, opzionali)

Tutti i file sono copiati da `/data/user/*/com.whatsapp/databases/` — percorso che l'app individua iterando tra i profili utente (`/data/user/*`) per supportare configurazioni con profili multipli (es. XSpace). Gli avatar vengono estratti separatamente da `/data/user/*/com.whatsapp/files/Avatars/`.

**Fallback crypt14** — Se la copia live fallisce (es. policy SELinux restrittiva), l'app ricade automaticamente sul vecchio flusso: copia di `key` + `msgstore.db.crypt14` dalla sdcard, seguito dalla fase di decifratura.

**3. Decifratura database (solo fallback)** — Eseguita solo se il flusso Live non è disponibile. Il file crypt14 viene decifrato in locale: lettura chiave AES-256 dal file `key` (byte 99–131), estrazione IV dall'header protobuf, decifratura AES-GCM, decompressione zlib. Il risultato è un file SQLite valido (`msgstore.db`). L'operazione fallisce se la chiave non corrisponde al database (errore GCM authentication tag). Nel flusso Live questo step viene saltato e marcato SUCCESS direttamente.

**4. Apertura database contatti** — Il file `wa.db` viene aperto in sola lettura per la risoluzione dei nomi dei contatti. I nomi sono ricavati dalla tabella `wa_contacts` del file `wa.db`, non da `jid.cached_name` in `msgstore.db` (spesso vuoto nelle versioni moderne di WhatsApp).

### Gestione sessione Root

Il database (`msgstore.db`) viene preservato tra le sessioni in `filesDir`. Al successivo avvio, se il file è già presente, il `SetupViewModel` rileva che la sessione Root è ancora valida (`isRootReady = true`) e mostra due opzioni nella schermata di setup:

- **Riprendi sessione** — naviga direttamente alla Dashboard senza rieseguire estrazione, garantendo avvio immediato.
- **Cancella sessione** — elimina `msgstore.db` e tutti i file derivati (inclusi WAL, SHM, avatars), riportando l'app allo stato iniziale e richiedendo un nuovo ciclo completo di setup.

Il pulsante "Avvia analisi" diventa "Analizza di nuovo" in caso di sessione già pronta, per distinguere visivamente i due casi. Implementato in `SetupViewModel.resumeRootSession()` e `clearRootSession()`.

### Avvii successivi e rilevamento aggiornamenti

Ad ogni avvio l'app calcola una **signature** del database sorgente di WhatsApp e la confronta con quella dell'ultima sessione. La signature è composta unicamente dalla **dimensione** del file `msgstore.db` live (percorso root). Ignorare volutamente la dimensione del file WAL e il `lastModified` evita la comparsa di notifiche continue ad ogni singolo messaggio ricevuto.

Il confronto avviene con una **soglia minima di 128 KB** (`SIGNATURE_CHANGE_THRESHOLD` in `CacheManager`): la notifica "Nuovo backup rilevato" appare solo se la dimensione del DB è variata di almeno 128 KB rispetto all'ultima estrazione. Questo filtra i falsi positivi causati dagli auto-checkpoint frequenti di SQLite (es. WhatsApp che va in background dopo pochi messaggi), che tipicamente producono crescite di pochi KB. I veri cicli di backup (notturni o dopo uso intenso) producono variazioni ben superiori alla soglia.

Tuttavia, in fase di estrazione, l'app continua a copiare sia il DB che il WAL per garantire sempre l'accesso ai dati più recenti. Se la signature supera la soglia, viene mostrata la card che lascia all'utente la scelta se aggiornare o continuare con i dati esistenti.

### Flusso di navigazione al primo avvio

Il `NavHost` parte sempre da `NavGraphRoute.Setup` (`setup_graph`). `SetupScreen` controlla se esiste già un DB valido:

- **Modalità Root pronta** → `state.isRootReady = true` → auto-naviga a `root_mode_graph`
- **Modalità Lite pronta** → `state.hasLiteDb = true` → auto-naviga a `lite_mode_graph`
- **Primo avvio assoluto** → nessun DB trovato → l'utente sceglie tra tab Root e tab Lite

L'auto-navigazione avviene solo se il parametro `autoNavigate = true` nella route di Setup (vedi sotto).

### Condivisione ZIP da WhatsApp

Quando l'utente condivide un file ZIP direttamente da WhatsApp, l'app riceve un `Intent` con `ACTION_SEND`. Il flusso attraversa tre componenti:

**Ricezione (`MainActivity`)** — `handleIncomingIntent()` viene chiamato sia in `onCreate()` sia in `onNewIntent()`: valida che il tipo sia ZIP, applica un guard anti-duplicati (stesso URI entro 8 s), chiama `mainViewModel.setIncomingZipUri(uri)` e azzera `intent.action` per evitare re-delivery. Il `MainViewModel` espone l'URI via `incomingZipUri: StateFlow<String?>`.

**Intercettazione in `AppNavigation`** — Un `LaunchedEffect(Unit)` osserva il flow dell'URI. Se arriva un URI mentre la destinazione corrente non è Setup, naviga direttamente a Setup con `autoNavigate = true`:

```kotlin
mainViewModel.incomingZipUri.collect { uri ->
    val currentRoute = navController.currentDestination?.route ?: ""
    if (uri != null && !currentRoute.startsWith("setup")) {
        navController.navigate(Screen.Setup.createRoute(autoNavigate = true)) {
            popUpTo("app_graph") { inclusive = false }
            launchSingleTop = true
        }
    }
}
```

> Si usa `navigate()` anziché `popBackStack(NavGraphRoute.Setup)` perché `setup_graph` potrebbe non essere nel backstack quando l'app era in background sulla Dashboard.

**Elaborazione in `SetupScreen`** — `SetupScreen` riceve `incomingZipUri` come parametro reattivo (via `collectAsState()`). Quando l'URI cambia: imposta `awaitLiteImport = true` e `shouldAutoNavigate = true`, chiama `liteViewModel.onZipSelected(uri)`, poi consuma l'URI (`mainViewModel.setIncomingZipUri(null)`). Al completamento (`liteState.isComplete = true`), naviga a `lite_mode_graph` con `popUpTo(setup_graph) { inclusive = false }` — Setup rimane nello stack per permettere il ritorno.

### Il parametro `autoNavigate`

Questo parametro della route di Setup distingue il **primo avvio** dal **ritorno manuale da Dashboard**.

**`autoNavigate = true`** (default): SetupScreen naviga automaticamente alla Dashboard non appena rileva `state.isComplete = true`. Usato al primo avvio dell'app, alla condivisione da WhatsApp, all'avvio manuale di un'analisi Root (`checkAndStart`) e all'import da file picker.

**`autoNavigate = false`**: Quando l'utente preme back dalla Dashboard, entrambe le modalità navigano a Setup con questo flag. SetupScreen blocca l'auto-nav (`if (!shouldAutoNavigate) return@LaunchedEffect`) e mostra la schermata con i dati già caricati, permettendo di scegliere se tornare alla Dashboard, importare un nuovo ZIP o avviare una nuova analisi Root.

`shouldAutoNavigate` torna `true` automaticamente in tre casi: arriva un nuovo URI da share WhatsApp, l'utente preme "Avvia analisi" Root (`checkAndStart()`), oppure seleziona un file ZIP dal picker (`zipLauncher` callback).

---

## 3. Sistema di cache e prestazioni

La cache è il meccanismo centrale per garantire aperture veloci. Usa `kotlinx.serialization` per serializzare tutti i modelli di dati, incluse distribuzioni, liste di parole, emoji e breakdown per mittente.

### Tipi di cache

**Cache Quick Stats globali** — Contiene `GlobalStats` con tutte le distribuzioni (oraria, settimanale, mensile, annuale). Ogni punto della distribuzione (es. una specifica ora o un giorno) include un `senderBreakdown`, ovvero la lista dei mittenti che hanno contribuito a quel volume di messaggi. Viene popolata dopo la prima analisi completa e mostrata istantaneamente alle aperture successive.

**Cache Slow Stats globali** — Contiene le parole più usate e le emoji più usate a livello globale. Queste analisi sono più lente perché richiedono di scorrere i testi di tutti i messaggi. Vengono cachate dopo la prima analisi manuale (opt-in) e mostrate automaticamente alle riaperture.

**Pool in-memory per modalità mittente singolo** — Il `ChatDetailViewModel` mantiene tre dizionari in memoria (`senderWordsPool`, `senderEmojisPool`, `senderStickersPool`) che cachano i risultati delle analisi testuali per singolo mittente. Le chiavi dei pool combinano il JID del mittente e i bounds temporali (`senderJid` + `startTime` + `endTime`). Quando l'utente cambia solo il limite di visualizzazione (es. da 10 a 20 parole), il risultato viene estratto dal pool senza rieseguire la query SQL. I pool vengono svuotati quando cambia il mittente selezionato, per garantire dati freschi.

**Indice lessicale incrementale** — Le frequenze di parole ed emoji globali e per-chat vengono persistite su disco come file gzippati (`word_index_global.gz`, `word_index_chat_{id}.gz` in `filesDir`/`cacheDir`). Ogni file memorizza la mappa completa `parola → conteggio`, il timestamp dell'ultimo messaggio processato e il totale dei messaggi come sanity check. Quando il DB cambia (SHA-256 diverso), l'app esegue un **delta**: legge solo i messaggi con `timestamp > lastProcessedTimestamp`, unisce le frequenze e aggiorna il file. Il rebuild completo avviene solo al primo avvio o se il totale messaggi nel nuovo DB è inferiore a quello registrato nell'indice (cambio account/device).

I modelli che strutturano l'indice sono `WordFrequencyIndexData` e `MonthlyWordIndexData`. Gli helper principali in `AnalyticsRepository` sono `buildOrUpdateGlobalWordIndex`, `buildOrUpdateChatWordIndex` e `computeFromMonthlyIndex`. I metodi DAO coinvolti in `MessageDao` sono `getTextsWithTimestamps`, `buildWordFrequencyMap`, `buildEmojiFrequencyMap`, `getGlobalTextMessageCount` e `getChatTextMessageCount`. La stop-word list è definita in `MessageDao.STOP_WORDS` (companion object).

**Indice mensile** — Accanto all'indice globale viene mantenuto `word_index_monthly.gz` (in `filesDir`), che bucket-izza le frequenze per mese ("YYYY-MM"). Per query con filtri temporali che coprono ≥ 28 giorni e il cui `endTime` ricade entro 7 giorni dall'ultimo aggiornamento dell'indice, le parole/emoji vengono calcolate sommando i bucket mensili pertinenti in memoria — zero accessi a SQLite. Per range più brevi (es. Ultimi 7/30 giorni o range custom ravvicinati) continua ad essere usata la query SQL diretta.

**Cache Quick Stats per chat** — Contiene distribuzioni orarie, settimanali, mensili e annuali per ogni singola chat, inclusi i breakdown dei mittenti per ogni punto dati. Indicizzata per `chatId`. Garantisce l'apertura istantanea del dettaglio di una chat.

**Cache Slow Stats per chat** — Contiene le analisi testuali per chat: parole più usate, emoji più usate, sticker più usati, breakdown per mittente, breakdown degli starter, tempo medio di risposta. Cachata per `chatId` dopo la prima analisi opt-in.

### Comportamento della cache per i filtri temporali e previsioni

La cache viene salvata **solo per il filtro `ALL_TIME`**. Quando l'utente applica un filtro temporale diverso (Ultimi 7 giorni, Ultimi 6 mesi, custom, ecc.), le query vengono rieseguite direttamente sul database. Questo è accettabile perché le query filtrate sono veloci grazie agli indici SQL.

**Nota sulle Previsioni**: I dati della card "Previsione Traguardi" **non vengono mai cachati**. Poiché dipendono strettamente dal ritmo dei messaggi calcolato sul periodo selezionato, vengono ricalcolati in tempo reale ad ogni cambio di filtro per garantire la massima accuratezza.

### Ottimizzazioni database

Sono presenti 7 indici SQL compositi sulle colonne più usate nelle query: `chat_row_id`, `message_type`, `timestamp`. Il database è aperto in WAL mode (Write-Ahead Logging) per supportare letture concorrenti. Le query pesanti sono parallelizzate con `async`/`await` (fino a 14 query in parallelo per il calcolo delle statistiche globali).

### Architettura DataStore e chiavi di cache

La cache usa **DataStore Preferences** (Jetpack) con due namespace separati, uno per Root Mode e uno per Lite Mode:

| Namespace | DataStore name |
|-----------|---------------|
| Root | `stats_cache_root` |
| Lite | `stats_cache_lite` |

Il `CacheManager` seleziona il DataStore corretto in base a `ActiveDatabaseProvider.isLiteMode`:

```kotlin
private val dataStore: DataStore<Preferences>
    get() = if (activeDb.isLiteMode) context.liteCacheDataStore else context.rootCacheDataStore
```

Le chiavi di cache disponibili:

| Chiave | Contenuto |
|--------|-----------|
| `global_stats_v4` | `GlobalStats` (quick stats: conteggi, distribuzioni orarie/settimanali/mensili) |
| `global_slow_v4` | Slow stats (top parole, emoji, sticker) |
| `chat_quick_{id}` | Stats rapide per singola chat |
| `chat_slow_{id}` | Stats lente per singola chat |

**DataStore colori mittente** — Le personalizzazioni di colore per i mittenti sono persistite in un DataStore dedicato tramite `SenderColorOverrideStore`. Le chiavi sono composte da `chatId|jid` e il valore è il colore ARGB scelto dall'utente. Questo DataStore è separato da `stats_cache_root` e `stats_cache_lite` e non viene invalidato al cambio del database.

### Invalidazione della cache

La cache viene invalidata esplicitamente in quattro occasioni:

1. **Nuovo import Lite** (`AnalyticsRepository.notifyLiteImportDone()`):
   ```kotlin
   setLiteMode(true)           // ← prima, per puntare al DataStore Lite
   cacheManager.clearCache()   // ← poi, cancella il DataStore Lite
   liteModeManager.notifyImportDone()
   ```
   > **Importante:** `setLiteMode(true)` va chiamato *prima* di `clearCache()`, altrimenti viene cancellato il DataStore Root invece del Lite.

2. **Eliminazione chat Lite** (`AnalyticsRepository.notifyLiteChatDeleted()`):
   ```kotlin
   cacheManager.clearCache(resetWordIndexes = true)
   liteModeManager.notifyImportDone()  // aggiorna liteCacheVersion → Dashboard si aggiorna
   ```
   Chiamato da `ChatListViewModel.deleteChat()` quando restano ancora altre chat. Se viene eliminata l'ultima chat, viene invece chiamato `fullResetLiteMode()` per un reset completo.

3. **Eliminazione sessione Root** (`WhatsAppRepository.cleanup()`): cancella la cache Root quando l'utente preme l'icona del cestino nella tab Root.

4. **DB Root modificato** (signature oltre soglia): `FileExtractor.calculateSourceDbSignature()` calcola la firma del database live (`/data/user/*/com.whatsapp/databases/msgstore.db`) usando esclusivamente la dimensione del file (`length`). `CacheManager.isRootDbChanged()` confronta la dimensione attuale con quella salvata applicando una **soglia di 128 KB** (`SIGNATURE_CHANGE_THRESHOLD`): variazioni inferiori vengono ignorate per filtrare gli auto-checkpoint frequenti di SQLite. Se la differenza supera la soglia, l'app mostra la card "Nuovo backup rilevato" e lascia all'utente la scelta. Se l'utente aggiorna, viene chiamato `clearRootCache()` (non `clearCache()`, per proteggere la cache Lite). Per le signature non numeriche (fallback crypt14, formato `size_lastModified`), il confronto resta esatto.

5. **Rename chat Lite** (`AnalyticsRepository.notifyLiteRenameChat()`): La cache quick stats (`chat_quick_{id}_v4`) e slow stats (`chat_slow_{id}_v3`) della chat vengono invalidate completamente. Le distribuzioni (hourly, weekly, ecc.) contengono `senderBreakdown` con displayName risolti dal JID originale — in Lite Mode il JID cambia col rename, rendendo il patch inaffidabile. La global stats cache (`global_stats_v4`) e media stats vengono invece patchate in-place per il solo campo `mostActiveChat`/`displayName`.

6. **Elimina dati da AppDrawer** (`MainViewModel.fullReset()`): reset completo attivato dal bottone "Elimina dati" nel navigation drawer. Scopo equivalente ad Android Impostazioni → App → Elimina tutti i dati, ma specifico per modalità:
   - **Root** (`clearRoot = true`): chiama `WhatsAppRepository.cleanup()` (cancella `msgstore.db`, `wa.db`, file WAL/SHM, `whatsapp.key`, `msgstore.db.crypt14` da `filesDir` + invalida cache root) + elimina `filesDir/avatars/` ricorsivamente.
   - **Lite** (`clearLite = true`): chiama `AnalyticsRepository.fullResetLiteMode()` (cancella `lite_msgstore.db`, `lite_wa.db` + invalida cache lite) + elimina `filesDir/lite_media/` ricorsivamente (ZIP sorgente + video/audio estratti).
   - **Setup screen** (`isOnSetupScreen = true`): esegue entrambi i reset.
   - Al termine naviga a `SetupScreen` (se non già lì) tramite `popUpTo("app_graph") { inclusive = false }`. I file originali WhatsApp in `/data/user/*/com.whatsapp/` non vengono mai toccati. Non viene tentato alcun aggiornamento parziale (patch) del senderBreakdown, perché in Lite Mode i JID del mittente e della chat divergono nelle chat 1-1, rendendo il match inaffidabile. Al successivo accesso, getSlowChatStats ricalcola da zero leggendo wa_contacts aggiornata.

### Refresh reattivo in Lite Mode (`liteCacheVersion`)

Il `DashboardViewModel` usa `liteCacheVersion` (un `StateFlow<Long>` di timestamp esposto da `AnalyticsRepository`) per sapere quando ricaricare i dati. Al `init`:

```kotlin
loadStats(forceRefresh = false)  // usa la cache se disponibile

if (isLite) {
    val baselineVersion = analyticsRepository.liteCacheVersion.value
    viewModelScope.launch {
        analyticsRepository.liteCacheVersion.collect { version ->
            if (version > baselineVersion) {
                loadStats(forceRefresh = true)  // nuovo import → ricalcola
            }
        }
    }
}
```

Poiché `notifyLiteImportDone()` chiama già `clearCache()`, il primo `loadStats(forceRefresh = false)` non troverà cache e ricalcolerà comunque. Alle riaperture successive (senza nuovi import), la cache è presente e viene usata.

### Visualizzazione dei caricamenti asincroni (`DynamicLoadingPill`)

Per le operazioni asincrone a lungo termine (ad esempio la scansione dei file multimediali nella galleria o il caricamento delle anteprime dei messaggi nel radar), l'applicazione utilizza un componente overlay premium denominato `DynamicLoadingPill`.

Questo componente fornisce:
- **Design a pillola fluttuante**: un indicatore non bloccante posizionato nella parte inferiore dello schermo che evita di oscurare l'intera UI con loader bloccanti invasivi.
- **Percentuale in tempo reale**: mostra l'avanzamento esatto del caricamento (da 0% a 100%) in base alla progressione asincrona delle query o dell'elaborazione file.
- **Meccanismo anti-glitch**: mantiene in cache l'ultima percentuale valida non nulla anche quando il ViewModel azzera lo stato, garantendo un'animazione di sparizione fluida (300ms) senza improvvisi ridimensionamenti grafici del testo.
- **Annullamento asincrono**: un pulsante di cancellazione integrato che interrompe immediatamente l'esecuzione delle coroutine in background tramite cancellazione cooperativa del `Job`.

---

## 4. Filtri temporali

Tutte le schermate principali che espongono analisi temporali (Dashboard, Dettaglio chat, Log Chiamate, Media & Link, Radar Parole) supportano filtri temporali applicabili alle metriche. I filtri disponibili sono:

- **Tutto** — nessun filtro, tutti i messaggi
- **Ultimi 30 giorni**
- **Ultimi 6 mesi**
- **Ultimo anno**
- **Scegli** — apre un selettore di intervallo date inline (`DateRangePicker`) senza triggerare alcun reload dei dati; il caricamento avviene solo alla pressione del pulsante di conferma

Il `DateRangePicker` è un componente dedicato con un pulsante "Cerca" che diventa verde quando è attivo e rosso per annullare. I filtri rapidi e il date picker rimangono fissi in cima alla schermata mentre si scorre (sticky header).

Il picker accetta un parametro `minDate` (Unix timestamp in ms): se fornito, le date precedenti a quel valore vengono disabilitate nella selezione. Il `minDate` viene calcolato da `getFirstMessageTimestamp()` (disponibile su `MessageDao`, `LiteDatabase` e `LiteModeManager`) ed esposto come `StateFlow` nei ViewModel. Questo impedisce di selezionare intervalli che non contengono alcun messaggio, eliminando i risultati vuoti.

Lo stato dei filtri viene mantenuto in memoria tramite `FilterStateHolder` (singleton Hilt, con campi `globalTimeRange/globalCustomRange` e `chatTimeRange/chatCustomRange`), così il range selezionato viene preservato quando si passa tra schermate globali e chat-specifiche. L'UI dei filtri è centralizzata nel componente `TimeRangeFilterRow`, con auto-scroll del carosello al chip selezionato per rendere sempre visibile il filtro attivo. Una progress bar top coerente è presente su tutte le schermate con altezza riservata per ridurre i micro-spostamenti del layout durante i caricamenti. Il chip "Scegli" gestisce uno stato locale `showDatePicker` separato dal `selectedRange`: premere il chip mostra/nasconde il picker senza propagare alcuna chiamata a `setTimeRange()` e senza triggerare query al database.

Il modello dati per i filtri è `TimeRange` (enum con valori: `ALL_TIME`, `LAST_30_DAYS`, `LAST_6_MONTHS`, `LAST_YEAR`, `CUSTOM`) e `DateRange` (con campi `startTime: Long?` e `endTime: Long?`, entrambi Unix timestamp in millisecondi).

---

## 5. Dashboard — Panoramica globale

**Schermata:** `DashboardScreen` / `DashboardViewModel`  
**Modello dati:** `GlobalStats`

### Statistiche di riepilogo (card in cima)

Le card mostrano i valori principali per il periodo selezionato:

- Numero totale di messaggi
- Numero totale di chat
- Numero totale di contatti
- Numero totale di file multimediali scambiati
- Media di messaggi al giorno — calcolata dividendo il totale di messaggi per il numero di giorni nell'intervallo selezionato, non per i giorni dal primo messaggio in assoluto
- Nome della chat più attiva (risolto tramite `wa.db`)
- Ora del giorno in cui si scrive di più (0–23)
- Giorno della settimana in cui si scrive di più (0 = domenica, 6 = sabato)
- Streak più lunga: numero massimo di giorni consecutivi con almeno un messaggio, calcolato recuperando le date distinte con messaggi e contando le sequenze di giorni consecutivi (`calculateLongestStreak` in `AnalyticsRepository`)

### Grafici di attività (componente `InteractiveBarChart`)

Quattro schede selezionabili, ciascuna con un grafico a barre con scrolling orizzontale:

I grafici sono interattivi:
- **Tooltip (Bubble)**: toccando una barra appare una nuvoletta che mostra il totale dei messaggi e i primi 3 contributori (mittenti) per quel periodo, con pallini colorati coerenti.
- **Dettaglio esteso**: toccando la nuvoletta si apre un `ActivityDetailDialog` con la lista completa dei mittenti, i conteggi esatti e le percentuali di contribuzione.
- **Auto-dismiss**: toccare fuori dall'area del grafico chiude la selezione tramite il meccanismo `dismissSignal`.

### Previsione Traguardi (Milestone Prediction)

Card interattiva (disponibile solo in Root Mode) che stima la data di raggiungimento di traguardi futuri (es. 10.000, 50.000, 100.000 messaggi o un obiettivo personalizzato).
- **Logica**: Il sistema calcola il ritmo giornaliero dei messaggi basandosi sul periodo temporale selezionato.
- **Affidabilità**: Include un indicatore di confidenza ("Stima instabile") se il volume di dati è troppo basso o la velocità troppo irregolare.
- **Interattività**: Permette di inserire un obiettivo personalizzato e mostra una barra di progresso visiva verso il traguardo selezionato.

### Analisi testi — Sezione opt-in

Le analisi testuali non partono automaticamente perché richiedono più tempo. L'utente vede un pulsante "Avvia analisi" con un'icona informativa che spiega la durata stimata. Una volta avviata, l'analisi procede in background con un indicatore di avanzamento (`LoadingSection`). I risultati vengono cachati e mostrati automaticamente alle aperture successive senza richiedere di riavviare l'analisi.

**Notifiche di avanzamento (Root Mode)** — Durante l'analisi lessicale lenta in Root Mode, il `DashboardViewModel` avvia il `LexicalAnalysisService`, un Android foreground service registrato in `AndroidManifest` con tipo `dataSync`. Il servizio mostra una notifica di sistema con barra di avanzamento percentuale in tempo reale, aggiornata da `LexicalAnalysisManager` che riceve i callback dal processo di costruzione dell'indice lessicale. Il servizio si termina automaticamente al completamento dell'analisi o al suo annullamento.

È possibile scegliere quanti elementi visualizzare (10, 20, 30 o 50) tramite il componente `SelectableLimitChip`. Il pool interno è sempre di 50 elementi pre-caricati; il selettore filtra la visualizzazione senza nuove query.

**Parole più usate** — Componente `WordCloudView`. Le parole sono mostrate con dimensione proporzionale alla frequenza. Le stop word italiane e comuni sono escluse con filtro in memoria dopo la query SQL. Toccando una parola appare un tooltip con il conteggio esatto.

**Emoji più usate** — Componente `InteractiveEmojiCloud`. Analoga alla word cloud. Il parser supporta emoji con skin tone modifier, sequenze ZWJ (emoji composte), bandiere e keycap. Toccando un'emoji si apre `EmojiStickerPreviewModal`, un modal in stile light con backdrop che mostra l'emoji in grande e il conteggio.

**Sticker più usati** — Componente `InteractiveStickerCloud`. Mostra le miniature degli sticker con conteggio utilizzi. I dati vengono letti dalla tabella `message_media`. Toccando uno sticker si apre lo stesso modal di anteprima.

---

## 6. Le tue chat — Lista e ranking

**Schermata:** `ChatListScreen` / `ChatListViewModel`

Elenco di tutte le conversazioni ordinate per numero totale di messaggi in ordine decrescente. Per ogni chat viene mostrato:

- Avatar circolare del contatto o del gruppo. Gli avatar vengono estratti da `/data/user/{userId}/com.whatsapp/files/Avatars/` e caricati come immagini JPEG tramite la libreria Coil. Se non è disponibile un avatar, viene mostrata un'icona Person (per chat 1-a-1) o Group (per gruppi).
- L'utente corrente viene identificato con il JID speciale `me@s.whatsapp.net`, il cui avatar è estratto come `me.jpg` dalla stessa directory.
- Nome del contatto risolto tramite `wa.db` (non il numero di telefono grezzo da `jid.raw_string`).
- Numero di telefono del contatto, dove disponibile, mostrato accanto al nome tramite il componente `ContactNameWithPhone`. Il nome supporta scrolling orizzontale automatico con animazione per nomi lunghi, evitando troncamenti.
- Numero totale di messaggi.
- Indicatore di posizione nel ranking.

Molti elementi (come i nomi dei contatti lunghi) utilizzano il componente **AutoMarquee**, che attiva uno scorrimento orizzontale automatico se il testo eccede lo spazio disponibile.

### Azioni per chat (solo Lite Mode)

In Lite Mode, ogni elemento della lista chat espone due azioni:

- **Rinomina** (icona matita) — Apre un `AlertDialog` con un campo testo pre-compilato con il nome attuale. Il rename aggiorna solo il campo `subject` nel DB SQLite Lite; l'`importKey` rimane invariato, garantendo che il meccanismo di re-import continui a funzionare. Il feedback nella UI e' istantaneo (aggiornamento in-memory della lista senza reload dal DB). Validazione: il nome non puo' essere vuoto. Implementato in `LiteDbWriter.renameChat()` → `ChatListViewModel.renameChat()` → `ChatListScreen`.

- **Elimina** (icona cestino) — Conferma tramite dialog, poi rimuove chat + messaggi + media dal DB. Se era l'ultima chat, esegue un reset completo e naviga a Setup.

---

## 7. Dettaglio chat

**Schermata:** `ChatDetailScreen` / `ChatDetailViewModel`  
**Modelli dati:** `ChatStats` (statistiche veloci), `SlowChatStats` (analisi testuali)

L'apertura è istantanea grazie alla `QuickChatStats` cache per chat. Le statistiche lente vengono caricate progressivamente in background.

### Intestazione

- Avatar del contatto cliccabile. Toccando l'avatar si apre una visualizzazione a schermo intero con overlay nero. Toccando di nuovo si chiude. L'anteprima a schermo intero supporta pinch-to-zoom, rotazione a 90°, doppio tap e swipe-to-dismiss tramite `ZoomableImageViewer`.
- Nome del contatto e numero di telefono (componente `ContactNameWithPhone`). Il nome supporta scrolling orizzontale automatico (**AutoMarquee**) per nomi lunghi.
- Periodo analizzato: data del primo e dell'ultimo messaggio nel filtro selezionato, visualizzato tramite `DateRangeCard` con sfondo SurfaceVariant e scorrimento orizzontale automatico (**AutoMarquee**).

L'intestazione viene caricata in anticipo (`HeaderInfo` state) con shimmer placeholder, fornendo feedback visivo immediato. Il nome e l'avatar sono recuperati in background in parallelo alle statistiche, migliorando la percezione delle prestazioni.

### Statistiche di base

- Messaggi totali, suddivisi in inviati e ricevuti, con percentuale.
- Rapporto messaggi inviati/totali (`sentRatio`, valore 0.0–1.0).
- Tempo medio di risposta in minuti/ore. Il calcolo considera solo i cambi di mittente consecutivi (es. da `from_me=1` a `from_me=0` e viceversa) e scarta i gap superiori a 24 ore (86.400.000 ms) per non inquinare la media con periodi di inattività. Query con `LAG()` window function in `calculateAverageResponseTime` di `AnalyticsRepository`.
- Lunghezza media dei messaggi in caratteri.
- Messaggio più lungo.
- Numero totale di media, di cui audio e video separati.
- **Media parole per messaggio** — Due StatCard mostrano la media globale di parole per messaggio e il dettaglio suddiviso per mittente (inviati vs ricevuti). Calcolato da `sentWords` e `receivedWords` in `ChatStats` tramite tokenizzazione per spazi nel DAO.
- **Media emoji per messaggio** — Analoga alla card delle parole: media globale e breakdown per mittente (`sentEmojis`, `receivedEmojis`). Aggiornato dalla stessa pipeline temporale delle altre statistiche, quindi rispetta i filtri attivi.

### Chi scrive di più (`SenderBreakdown`)

Donut chart (grafico a ciambella) che visualizza la proporzione di messaggi per partecipante. Il dato si basa su `getSenderBreakdown` in `MessageDao`, che raggruppa per mittente (`from_me` + `sender_jid_row_id`). Nei gruppi con più di 10 partecipanti viene mostrato un pulsante "Mostra tutti" per espandere la lista. La legenda include nome e numero di telefono di ogni partecipante. I mittenti sono mostrati nell'ordine naturale del database (volume di messaggi decrescente).

Nota: nella sezione **Analisi testi → Per Persona** i chip di selezione mittente applicano un ordinamento locale che porta sempre "Tu" (`me@s.whatsapp.net`) in prima posizione. Questo sort avviene esclusivamente al livello UI (`sendersForChips` in `ChatDetailScreen`) e non altera il dato grezzo `senderBreakdown`, preservando l'ordine per volume in tutte le altre sezioni.

Nota: **Personalizzazione colori mittente** — Ogni mittente nei grafici a torta e nelle visualizzazioni di attività può avere un colore personalizzato dall'utente. Toccando il colore di un mittente si apre `SenderColorPickerDialog`, che permette di scegliere un colore alternativo. Il colore scelto sovrascrive l'assegnazione automatica di `SenderColors.getSenderColor` e viene persistito per quella chat tramite `SenderColorOverrideStore` (chiave `chatId|jid`). I colori personalizzati sopravvivono ai riavvii dell'app e sono indipendenti dalla cache delle statistiche.

### Grafici di attività

Stesse quattro schede della Dashboard (oraria, settimanale, mensile, annuale), filtrate sulla chat corrente. Implementate con `InteractiveBarChart` e `InteractiveHourlyHeatmap` con supporto al breakdown dei mittenti e dialog di dettaglio.

### Activity Breakdown e Colorazione Stabile

Questa funzionalità permette di vedere *chi* ha scritto in determinati momenti della giornata o del mese.

- **Logica di aggregazione**: Le query SQL nei DAO (es. `getHourlyDistributionWithBreakdown`) eseguono una `JOIN` con la tabella `jid` per raggruppare i messaggi non solo per tempo ma anche per mittente all'interno di ogni slot temporale.
- **Sistema di Colorazione (SenderColors)**: Per garantire che un utente abbia lo stesso colore in tutti i grafici e breakdown dell'app, è stato implementato un sistema di colorazione stabile (`getSenderColor` in `ui.theme`).
    - Il colore è determinato dalla posizione del mittente nel ranking principale della chat ("Chi scrive di più").
    - Se un mittente non è presente nel ranking (es. ha scritto pochissimi messaggi globalmente ma molti in un'ora specifica), il colore viene generato in modo deterministico dal JID tramite hash.
- **Interfaccia**: Il breakdown è visualizzabile tramite il tocco sulle barre dei grafici (anteprima veloce) e approfondibile tramite l' `ActivityDetailDialog`.

### Info Cards (Periodo, Risposta e Media)

- **Periodo di attività**: Data del primo e dell'ultimo messaggio nel filtro selezionato (`DateRangeCard`).
- **Tempo medio di risposta**: Il calcolo considera solo i cambi di mittente consecutivi (es. da `from_me=1` a `from_me=0` e viceversa) e scarta i gap superiori a 24 ore per non inquinare la media.
- **Media totale**: Conteggio totale dei file multimediali, con dettaglio audio e video.
- **Lunghezza media messaggi**: Lunghezza media in caratteri dei messaggi di testo.

### Chi inizia le conversazioni (`ConversationStarterBreakdown`)

Donut chart analogo che conta quante volte ogni partecipante ha inviato il primo messaggio dopo almeno 3 ore di silenzio (10.800.000 ms). La logica usa `LAG()` sulla colonna `timestamp` ordinata per tempo: se la differenza con il messaggio precedente è nulla (primo messaggio in assoluto) o supera 3 ore, si conta come "inizio conversazione". La spiegazione della definizione è mostrata in un `AlertDialog` modale accessibile tramite pulsante info. La breakdown è per mittente con nome e numero di telefono.

Nota tecnica rilevante: il totale della breakdown viene calcolato sommando i valori della breakdown stessa, non con una query separata, per evitare discrepanze con i filtri temporali.

### Previsione Traguardi

Presente anche nel dettaglio chat, fornisce una stima specifica per la conversazione corrente (solo in Root Mode), permettendo di vedere quanto manca a raggiungere cifre tonde di messaggi con quel particolare contatto.

### Evoluzione mensile

Grafico a barre con l'andamento mese per mese del numero di messaggi. Dati da `getMonthlyTrend` in `MessageDao`, che raggruppa per `strftime('%Y-%m', ...)` sul timestamp.

### Analisi testi per chat (opt-in)

Stessa logica della Dashboard, ma filtrata per chat. Include in aggiunta il filtro per mittente (switcher "Tutti" / "Per Persona") su emoji e sticker.

**Parole più usate** — `WordCloudView` con selettore limite e filtro per mittente.  
**Emoji più usate** — `InteractiveEmojiCloud` con switcher aggregato/per-persona.  
**Sticker più usati** — `InteractiveStickerCloud` con miniature da `message_media`.

### Log Chiamate

Riepilogo delle chiamate per la chat selezionata. I dati vengono letti dalla tabella `call_log` di `msgstore.db`. Mostra:

- Totale chiamate audio, video e perse.
- Durata totale e durata media.
- Card di dettaglio per ogni voce con avatar circolare e nome del contatto.
- Barra di ricerca lato client per filtrare le voci. La barra viene nascosta automaticamente se non ci sono risultati.

### Media & Link

Statistiche sui contenuti multimediali e sui link condivisi nella chat. Dati letti da `message_media` (per media) e da `message` con filtro `text_data LIKE 'http%'` e dalla tabella `message_link` (per i link). Mostra:

- Conteggio media per tipo MIME (immagini, video, audio, documenti), con dimensione totale.
- Link condivisi raggruppati per dominio, con conteggio e data dell'ultima condivisione per ogni URL. Il tracking della data di ultima condivisione è implementato in `getSharedUrlsWithTimestamps` che ritorna coppie `(testo, timestamp)`.
- Per ogni dominio: breakdown per mittente con avatar e nome.
- Bottom sheet espandibile con dettaglio domini per singolo contatto, con avatar nell'intestazione e pulsante info (`AnimatedInfoButton`) che spiega la schermata.
- Slider di intervallo date in cima al bottom sheet per filtrare i link nel periodo desiderato; compare solo quando i link hanno almeno 2 timestamp distinti.
- Anteprima link on-demand: icona chevron accanto a ogni URL espande una card compatta con titolo, immagine og e nome del sito, senza uscire dall'app. Il fetch avviene su `Dispatchers.IO` con cache in memoria (TTL 7 gg successo / 10 min fallimento). Implementata in `MediaStatsLinkPreview.kt`; logica di rete, parsing HTML/OG/Twitter card e decodifica entità HTML in `MediaStatsLinkPreviewResolver.kt`. Supporto nativo YouTube via oEmbed.
- Barra di ricerca lato client. La barra viene nascosta se non ci sono risultati.

### Cronologia gruppi (Group History Timeline)

Per le chat di gruppo è disponibile una timeline completa della cronologia del gruppo, accessibile tramite un pulsante nella schermata dettaglio. La timeline mostra in ordine cronologico tutti gli eventi rilevanti:

- **Azioni sui partecipanti:** join, leave, add, remove, promote, demote
- **Cambi nome gruppo**
- **Cambi descrizione gruppo** (nuovo)

Questi eventi sono estratti tramite query dedicate in `MessageDao` e consolidati in `AnalyticsRepository.getGroupHistory()`, che normalizza i timestamp e risolve i nomi/avatari dei partecipanti tramite batch JID resolution. La UI utilizza una `LazyColumn` a layout inverso per simulare la timeline chat, distinguendo visivamente tra eventi informativi (es. cambio descrizione) e azioni sui partecipanti tramite l'allineamento (`isLeftAligned`).

**Modelli dati:** La sealed class `GroupEvent` include i tipi `ParticipantAction`, `NameChange` e `DescriptionChange`. Il modello `GroupParticipantHistoryEntity` ha una colonna `textData` per tracciare le descrizioni. I costruttori di `GroupEventUiModel` richiedono i parametri `userJid`, `isLeftAligned` e `onNavigateToHistory`.

---

## 8. Media Gallery

**Schermata:** `MediaStatsScreen` / `MediaStatsViewModel`

Galleria multimediale per esplorare foto e video scambiati nelle chat, con accesso root per file protetti e supporto Scoped Storage.

### Funzionalità principali

- **Lista media** — Mostra tutti i file foto e video dalle tabelle `message_media` e `addon_message_media`, filtrando per MIME types image/* e video/* ed escludendo tipi di messaggio di sistema (20, 7).
- **Accesso root** — Quando il percorso normale del file non è accessibile (Scoped Storage), utilizza `RootFileFetcher` con `SuFileInputStream` per leggere i file protetti da directory root come `/data/user/*/com.whatsapp/files/Avatars/`.
- **Risoluzione percorsi** — Tenta prima il percorso standard, poi fallback a percorsi root comuni: `/sdcard/Android/media/com.whatsapp/WhatsApp` e `/sdcard/WhatsApp`.
- **Ordinamento** — Toggle tra ordine crescente e decrescente per data del media.
- **Lazy loading** — Caricamento a blocchi di 100 elementi alla volta con `LazyVerticalGrid`.
- **Anteprime video** — Supporto anteprime video tramite dipendenza `coil-video`.
- **Tracking** — Barra di progresso e stato di caricamento visibili durante il fetch.
- **Zoom immagini** — Anteprima a schermo intero con `ZoomableImageViewer` che supporta pinch-to-zoom, rotazione a 90°, doppio tap per zoom toggle e swipe-to-dismiss.

### Cache

Le statistiche media hanno cache dedicata con chiavi `media_global_v3` e `media_chat_v3`. L'invalidazione avviene automaticamente al cambio del database (SHA-256).

### Ottimizzazioni prestazionali

**Indice gzipped** — Un indice persistente e gzippato dei file multimediali viene salvato su disco. All'apertura della galleria, l'indice viene caricato in parallelo con la query SQL, eliminando completamente la scansione sequenziale del filesystem. Se l'indice non esiste o è obsoleto (mismatch SHA-256 del DB), viene eseguita una scansione one-time del filesystem per ricostruirlo.

**Supporto Sent** — `RootFileFetcher` tenta il lookup nella directory `/Sent/` per i media inviati, risolvendo correttamente i percorsi per i file multimediali inviati dall'utente.

**Risultato** — Tempo di caricamento passato da secondi a millisecondi nelle riaperture consecutive.

**Nota sticker in Lite Mode** — nella `MediaStatsScreen`, il conteggio dei media in Lite Mode include anche i messaggi sticker (`message_type = 20`), sommati ai media normali. Questo comportamento è controllato dal parametro `includeStickers` nel DAO, che viene abilitato automaticamente dal repository quando `isLiteMode = true`. In Root Mode il parametro è `false` e gli sticker restano esclusi dal conteggio media.

**Caricamento immagini in Lite Mode** — in Lite Mode le immagini non vengono estratte su disco: Coil usa `ZipFileFetcher` (file `ui/component/ZipFileFetcher.kt`) per decodificarle direttamente dall'archivio `source.zip` in RAM. I path nel DB hanno forma `zip://lite_chat_xxx/source.zip|IMG-2024.jpg`. I video/audio usano invece URI `content://` tramite `FileProvider` (authority `${applicationId}.fileprovider`), poiché i player esterni richiedono seek su file reali e non possono accedere a `filesDir` direttamente.

---

## 9. Radar Parole

Funzione di ricerca che conta le occorrenze di una parola o frase in una chat specifica (o globale), con breakdown per mittente. Implementata in `searchWordFrequency` di `MessageDao`.

### Caratteristiche principali:

- **Suggerimenti intelligenti** — Mentre l'utente digita (da 3 caratteri in poi), appaiono suggerimenti basati sulle parole più frequenti nell'indice lessicale (globale o per-chat). Questo evita di dover digitare interamente parole lunghe o comuni.
- **Supporto sottostringhe** — La ricerca trova tutte le occorrenze della parola digitata, anche se fa parte di una parola più lunga (es. "casa" troverà anche "casale").
- **Icona informativa** — Una "i" nella barra di ricerca spiega chiaramente il funzionamento dei suggerimenti e la logica delle sottostringhe tramite un dialog dedicato.
- **Ricerca esplicita** — La ricerca effettiva (con conteggio per mittente) parte solo alla pressione del pulsante di ricerca o al click su un suggerimento.
- **Ottimizzazione performance** — Pre-filtro SQL con `LIKE '%parola%'` prima del conteggio esatto, che riduce il numero di righe da processare in memoria di un fattore 10–100x.
- **Breakdown per mittente** — Mostra il conteggio per ogni partecipante, distinguendo `from_me=1` (l'utente) dal JID dell'interlocutore. Il mapping al nome del contatto avviene tramite `wa.db`.
- **Anteprima Messaggi** — Toccando il conteggio messaggi di un mittente, si apre una `ModalBottomSheet` che mostra l'elenco completo dei messaggi contenenti la parola cercata.
    - **Ordinamento dinamico**: Toggle tra "Più recenti" (default) e "Meno recenti".
    - **Paginazione**: Caricamento fluido in blocchi da 50 elementi con pulsante "Carica altri".
    - **Keyword Highlighting**: La parola cercata viene evidenziata in grassetto con il colore primario del tema.
    - **Context Awareness**: Visualizzazione automatica del nome della chat per i risultati in modalità globale; layout compatto per la modalità chat-specifica.
- **Opzioni di ricerca avanzata** — Riga di FilterChip sotto la barra di ricerca:
    - **Aa (Case Sensitive)**: permette di distinguere tra maiuscole e minuscole nel conteggio e nell'highlighting.
    - **ab sottolineato (Parola Esatta)**: utilizza confini di parola Unicode `(?<!\p{L})parola(?!\p{L})` per trovare solo termini isolati, escludendo le sottostringhe.
    - **?**: filtra i risultati mostrando solo i messaggi che contengono un punto interrogativo (filtro in memoria).
    - **Multi (Toggle a 3 stati)**: cicla tra tre modalità di ricerca:
        - *OFF*: ricerca della frase esatta.
        - *E (AND)*: messaggi che contengono TUTTE le parole inserite.
        - *O (OR)*: messaggi che contengono ALMENO UNA delle parole inserite.
    - **Icona informativa**: spostata nella riga dei chip per decongestionare l'area di input.

---

## 10. Privacy, Sicurezza e Build Automation

- **Dati WhatsApp locali**: i contenuti delle chat (messaggi, nomi, numeri di telefono, media) non lasciano mai il dispositivo. Il traffico cleartext è bloccato (`network_security_config.xml`, `cleartextTrafficPermitted="false"`). La connessione HTTPS è consentita esclusivamente per Google AdMob (annunci versione Free), Google UMP (consenso privacy) e Google Play Billing (acquisto Pro).
- Tutti i file estratti (chiave, database cifrato, database decifrato, avatar) sono salvati in `context.filesDir`, la directory privata dell'app non accessibile ad altre app senza root.
- La chiave di cifratura AES-256 non viene mai inclusa nei log di sistema e viene sottoposta a "wiping" (azzeramento in memoria) post-utilizzo.
- I contenuti dei messaggi, i nomi e i numeri di telefono non vengono loggati in produzione. Tutti i log standard (`Log.d`) vengono estirpati dal bytecode in fase di release tramite ProGuard/R8.
- I file temporanei vengono eliminati con sovrascrittura a zero prima della cancellazione (`secureDelete`), per evitare recupero da storage.
- **Anti-Tampering (`IntegrityChecker`)**: L'app controlla silenziosamente l'hash della propria firma in runtime. Se rileva una manomissione (es. APK ri-firmato da Lucky Patcher), non crasha ma imposta un flag interno di manomissione.
- **Feature Gating Distribuito (`FeatureGate`)**: La logica per lo sblocco di eventuali future funzioni premium è basata su costanti binarie oscurate e l'uso di funzioni Kotlin `inline`. Questo disperde il bytecode di validazione ovunque venga invocato, rendendo inefficace il patching centralizzato. Se l'`IntegrityChecker` segnala una manomissione, il sistema chiude silenziosamente gli accessi fingendo uno stato non-premium ("poisoning").
- **CI/CD e Signing**: Il file `.jks` contenente la chiave per firmare l'APK ufficiale non risiede più nel codice sorgente né nella macchina di sviluppo in chiaro, ma viene iniettato dinamicamente tramite GitHub Secrets all'interno del workflow `.github/workflows/release.yml`, garantendo build pulite e verificabili.

---

## 11. Gestione degli errori

Gli errori sono modellati con la sealed class `AppError` in `domain/model/AppError.kt`. I casi gestiti:

- **`RootNotAvailable`** — Root non presente o permessi negati. L'app mostra istruzioni per verificare Magisk.
- **`KeyFileNotFound`** — Il file `/data/data/com.whatsapp/files/key` non esiste. Può accadere se il percorso è cambiato con un aggiornamento di WhatsApp.
- **`Crypt14NotFound`** — Il file `msgstore.db.crypt14` non è presente. Emesso solo se anche il flusso Live DB ha fallito. Può indicare che il backup locale è disabilitato.
- **`Crypt15Detected`** — Rilevato un file `msgstore.db.crypt15` (backup E2E attivo). L'app mostra un messaggio che spiega come disattivare il backup E2E in WhatsApp.
- **`DecryptionFailed(reason)`** — La decifratura AES-GCM è fallita (solo flusso fallback crypt14). Causa più comune: la chiave non corrisponde al database (es. WhatsApp reinstallata). Nel flusso Live questo errore non si verifica.
- **`DatabaseError(message)`** — Il file SQLite è corrotto o ha uno schema non riconosciuto. Può accadere con versioni molto recenti di WhatsApp che modificano lo schema del database.
- **`UnknownError(throwable)`** — Errore imprevisto, con stacktrace.

Tutti gli errori vengono mostrati nella schermata di Setup come card con sfondo `errorContainer` (Material 3) e testo descrittivo.

---

## 12. Struttura dei dati sorgente

### Database `msgstore.db` (WhatsApp 2.23+)

Le tabelle principali usate dall'app:

**`message`** — tabella principale dei messaggi. Campi rilevanti:
- `_id`: chiave primaria
- `chat_row_id`: FK verso `chat._id`
- `from_me`: 0 = messaggio ricevuto, 1 = messaggio inviato
- `sender_jid_row_id`: FK verso `jid._id` del mittente. Attenzione: nelle chat 1-a-1 questo campo è sempre 0 (vedi sezione 12).
- `timestamp`: Unix timestamp in millisecondi (UTC)
- `message_type`: 0 = testo, 1 = foto, 2 = audio, 3 = video, 20 = sticker, altri valori per documenti, ecc. In Lite Mode, i messaggi `message_type = 20` vengono conteggiati come media nelle query statistiche.  
- `text_data`: contenuto testuale del messaggio (null per messaggi non testuali)
- `starred`: 1 se il messaggio ha la stella

**`chat`** — una riga per ogni conversazione.
- `jid_row_id`: FK verso `jid._id` dell'interlocutore o del gruppo
- `subject`: nome del gruppo (null per chat 1-a-1)

**`jid`** — identificatori WhatsApp.
- `raw_string`: JID completo, es. `393391234567@s.whatsapp.net` o `123456789@g.us` per i gruppi
- `server`: `s.whatsapp.net` per contatti, `g.us` per gruppi
- `cached_name`: nome in cache (spesso vuoto nelle versioni moderne)

**`message_media`** — allegati multimediali.
- `message_row_id`: FK verso `message._id`
- `mime_type`: tipo MIME (es. `image/jpeg`, `audio/ogg`, `video/mp4`)
- `file_size`: dimensione in byte
- `media_duration`: durata in millisecondi (per audio e video)

**`message_link`** — link estratti dai messaggi.

**`call_log`** — registro delle chiamate.

**`message_add_on`** — reazioni e modifiche (WhatsApp 2022+).
- `add_on_type`: 1 = reazione, 2 = modifica, 3 = visualizzazione unica

### Database `wa.db`

**`wa_contacts`** — rubrica contatti WhatsApp.
- `jid`: JID del contatto
- `display_name`: nome come appare in WhatsApp
- `number`: numero normalizzato

---

## 13. Comportamenti noti e quirk del database

### `sender_jid_row_id = 0` nelle chat 1-a-1

Nelle chat dirette (non di gruppo), WhatsApp imposta `sender_jid_row_id = 0` per **tutti** i messaggi, sia inviati che ricevuti. Un `JOIN jid ON sender_jid_row_id = j._id` non restituisce mai il mittente in questo contesto.

Il pattern corretto prevede:
- Per i messaggi di gruppo: `JOIN jid ON sender_jid_row_id = j._id` funziona normalmente.
- Per le chat 1-a-1: usare `from_me` per distinguere i messaggi inviati, e ricavare il JID dell'interlocutore facendo JOIN su `chat` e poi su `jid` tramite `chat.jid_row_id`.

Questo pattern è implementato in `MessageDao` nei metodi `searchWordFrequency`, `getLinkStatsBySender` e `getCallStats`. In `AnalyticsRepository` il JID speciale `me@s.whatsapp.net` viene mappato alla stringa "Tu" in tutti i contesti di risoluzione nomi.

### Identificazione chat 1-a-1 vs gruppo

Un JID che contiene `@g.us` è un gruppo; un JID con `@s.whatsapp.net` è una chat 1-a-1. Il campo `ChatStats.isGroup` è calcolato come `jid.contains("@g.us")`.

### Timestamp in UTC

Tutti i timestamp nel database sono in UTC. Le query che calcolano distribuzioni orarie e per giorno della settimana applicano la conversione a ora locale con `datetime(timestamp / 1000, 'unixepoch', 'localtime')` in SQLite.

### Schema versioni recenti

WhatsApp 2.23+ ha separato i dati in più tabelle rispetto allo schema monolitico delle versioni precedenti. Se l'app restituisce `DatabaseError` con un messaggio su colonne o tabelle non trovate, è probabile che WhatsApp abbia rilasciato un aggiornamento che modifica lo schema. In quel caso occorre verificare le query nei DAO e aggiornare i nomi di colonna o tabella.

---

## 14. Struttura della navigazione

L'app usa **Jetpack Navigation Compose** con un sistema a tre livelli di grafi annidati. Il `NavHost` è definito in `ui/navigation/AppNavigation.kt`.

```
app_graph  (NavHost root)
├── setup_graph
│   └── setup?autoNavigate={autoNavigate}
├── root_mode_graph
│   ├── overview_graph
│   │   └── dashboard
│   ├── chats_graph
│   │   ├── chat_list
│   │   └── chat_detail/{chatId}
│   │       ├── word_search/{chatId}
│   │       ├── calls_stats/{chatId}
│   │       ├── media_stats/{chatId}
│   │       └── group_history/{chatId}
│   ├── media_graph
│   │   └── media_stats
│   ├── radar_graph
│   │   └── word_search
│   └── calls_graph
│       └── calls_stats
└── lite_mode_graph
    ├── overview_graph
    │   └── dashboard
    ├── chats_graph
    │   ├── chat_list
    │   ├── lite_setup_add?zipUri={zipUri}
    │   └── chat_detail/{chatId}
    │       ├── word_search/{chatId}
    │       ├── calls_stats/{chatId}
    │       ├── media_stats/{chatId}
    │       └── group_history/{chatId}
    ├── media_graph
    │   └── media_stats
    └── radar_graph
        └── word_search
```

### Classi e costanti di navigazione

**`Screen`** (sealed class) — route string di ogni schermata:

| Oggetto | Route |
|---------|-------|
| `Screen.Setup` | `setup?autoNavigate={autoNavigate}` |
| `Screen.Dashboard` | `dashboard` |
| `Screen.ChatList` | `chat_list` |
| `Screen.ChatDetail` | `chat_detail/{chatId}` |
| `Screen.WordSearch` | `word_search` / `word_search/{chatId}` |
| `Screen.CallsStats` | `calls_stats` / `calls_stats/{chatId}` |
| `Screen.MediaStats` | `media_stats` / `media_stats/{chatId}` |
| `Screen.GroupHistory` | `group_history/{chatId}` |
| `Screen.LiteSetupAdd` | `lite_setup_add?zipUri={zipUri}` |

**`NavGraphRoute`** (object) — costanti per i grafi:

| Costante | Valore |
|----------|--------|
| `Setup` | `"setup_graph"` |
| `Root` | `"root_mode_graph"` |
| `Lite` | `"lite_mode_graph"` |
| `Overview` | `"overview_graph"` |
| `Chats` | `"chats_graph"` |
| `Media` | `"media_graph"` |
| `Radar` | `"radar_graph"` |
| `Calls` | `"calls_graph"` |

L'app utilizza una `BottomNavigationBar` Material 3 a 5 tab: **Panoramica**, **Chat**, **Media**, **Radar**, **Chiamate**. Ogni tab mantiene il proprio back stack indipendente tramite `saveState`/`restoreState`.

Il `NavHost` è strutturato con grafi annidati: un grafo Setup e un grafo Main contenente i 5 sotto-grafi per tab. Le schermate Radar, Chiamate e Media sono accessibili sia come radici di tab globali (senza `chatId`) sia come destinazioni chat-specifiche (con `chatId` nel path, es. `word_search/{chatId}`) all'interno del grafo Chat. Quando aperte da una chat, le schermate mostrano un header contestuale con nome chat, avatar e pulsante back tramite il componente `SimpleScreenHeader`. Il nome/avatar della chat è caricato da `AnalyticsRepository.getChatContextInfo(chatId)` e esposto come `StateFlow` nei rispettivi ViewModel (`WordSearchViewModel`, `CallsStatsViewModel`, `MediaStatsViewModel`).

Il `ChatDetailScreen` espone 3 card azione (Radar, Chiamate, Media) disposte in una riga orizzontale 1x3.

### Hamburger menu e navigation drawer

Tutte le schermate di primo livello espongono l'icona hamburger (`Icons.Default.Menu`) in alto a sinistra per aprire il `ModalNavigationDrawer` (componente `AppDrawer.kt`). Il `DrawerState` è iniettato nel composition tree da `AppScaffold` tramite `CompositionLocalProvider(LocalDrawerState provides drawerState)`.

Le schermate di dettaglio (back stack secondario) mostrano invece il pulsante freccia indietro (`ArrowBack`).

**Opzioni del drawer** (`AppDrawerContent`):

| Opzione | Comportamento |
|---------|---------------|
| Tema | `AlertDialog` con `RadioButton` per scegliere tra System/Light/Dark. Persistito via `AppPreferencesStore`. |
| Lingua | `AlertDialog` con lista lingue. Alla selezione: salva in `SharedPreferences` (`app_language_prefs`), chiama `MainViewModel.setAppLanguage()`, chiama `Activity.recreate()` per applicare il nuovo locale. |
| Elimina dati | `AlertDialog` di conferma. Alla conferma: chiama `MainViewModel.fullReset()` (reset completo specifico per modalità — vedi §3 punto 6) e naviga a `SetupScreen` se non già lì. |
| Info | `AlertDialog` statico con versione e descrizione app. |

`AppDrawerContent` riceve `isOnSetupScreen: Boolean` (calcolato da `AppScaffold` via `NavDestination.hierarchy`) e `onNavigateToSetup: () -> Unit` (lambda fornita da `AppScaffold` con `navController`). Il parametro `isOnSetupScreen` determina se il reset deve coprire entrambe le modalità.

**Implementazione per schermata**:

| Schermata | Meccanismo hamburger |
|-----------|---------------------|
| `SetupScreen` | `IconButton` con `LocalDrawerState.current` + `zIndex(1f)` per stare sopra al `Column` centrale |
| `DashboardScreen` | `IconButton` inline nell'header personalizzato |
| `ChatListScreen` | `SimpleScreenHeader(onBack = null)` — mostra hamburger quando `onBack` è null |
| `MediaStatsScreen` (tab globale) | `SimpleScreenHeader(onBack = null)` |
| `CallsStatsScreen` (tab globale) | `SimpleScreenHeader(onBack = null)` |
| `WordSearchScreen` (tab globale) | `SimpleScreenHeader(onBack = null)` |

`SimpleScreenHeader` gestisce automaticamente il toggle hamburger/back: se `onBack != null` mostra la freccia, altrimenti l'hamburger che apre il drawer via `LocalDrawerState`.

### Protezione back button: DoubleBackToExitHandler

**File**: `ui/component/DoubleBackToExitHandler.kt`

Le schermate tab di primo livello (ChatList, MediaStats, WordSearch, CallsStats) non hanno una destinazione precedente nel navigation stack. Senza protezione, il back button fisico chiuderebbe l'app immediatamente.

Il composable `DoubleBackToExitHandler` implementa il pattern standard Android "premi di nuovo per uscire":
- Intercetta il back fisico tramite `BackHandler`
- Alla prima pressione: mostra un `Toast` con messaggio "Premi di nuovo per uscire"
- Alla seconda pressione entro 2 secondi: chiama `Activity.finish()`
- Dopo 2 secondi: il contatore si resetta

**Dove e' applicato** (in `AppNavigation.kt`):

| Grafo | Schermata | DoubleBackToExitHandler |
|-------|-----------|------------------------|
| Root  | Dashboard | No — ha il suo `BackHandler` che naviga a Setup |
| Root  | ChatList (tab) | Si' |
| Root  | MediaStats (tab) | Si' |
| Root  | WordSearch (tab) | Si' |
| Root  | CallsStats (tab) | Si' |
| Lite  | Dashboard | No — ha il suo `BackHandler` che naviga a Setup |
| Lite  | ChatList (tab) | Si' |
| Lite  | MediaStats (tab) | Si' |
| Lite  | WordSearch (tab) | Si' |

**Non applicato** alle schermate di dettaglio (`sharedChatDetailRoutes`): MediaStats/{chatId}, WordSearch/{chatId}, CallsStats/{chatId}, ChatDetail, GroupHistory — queste usano `navController.popBackStack()` tramite il pulsante back nell'header e funzionano correttamente.

### Route condivise (`sharedChatDetailRoutes`)

Le route di dettaglio chat sono definite una volta sola nella funzione `sharedChatDetailRoutes()` e registrate in entrambi i grafi (Root e Lite):

- `chat_detail/{chatId}`
- `word_search/{chatId}`
- `calls_stats/{chatId}`
- `media_stats/{chatId}`
- `group_history/{chatId}`

Il back da queste schermate usa sempre `navController.popBackStack()`.

### Aggiunta chat in Lite (`LiteSetupAdd`)

Disponibile solo in Lite Mode dalla ChatList. Naviga a `Screen.LiteSetupAdd` (route `lite_setup_add?zipUri={zipUri}`), che apre `AddChatZipScreen` e lancia automaticamente il file picker (`OpenDocument`). Al completamento dell'import, `navController.popBackStack()` torna alla ChatList. Il completamento è notificato da `LiteSetupViewModel.state.isComplete`.

### Componenti UI condivisi estratti

Durante il refactor delle schermate principali sono stati estratti i seguenti componenti riusabili nel package `ui/component/`:

| Componente | File | Usato in |
|-----------|------|----------|
| `LoadingProgressSlot` | `ui/component/LoadingProgressSlot.kt` | Dashboard, MediaStats, ChatDetail, WordSearch, Calls |
| `SectionCard` | `ui/component/SectionCard.kt` | Dashboard, ChatDetail, DashboardTextAnalysisSection |
| `LimitSelector` | `ui/component/LimitSelector.kt` | DashboardTextAnalysisSection, ChatDetailScreen |

### Split schermate principali

Le schermate più pesanti sono state suddivise in file dedicati per migliorare leggibilità e manutenibilità, senza modificare comportamento o logica applicativa:

**Dashboard** (`ui/screen/dashboard/`):

| File | Contenuto |
|------|-----------|
| `DashboardHeader.kt` | Header con titolo, hamburger/back, indicatore modalità |
| `DashboardStatsGrid.kt` | Griglia 2×3 delle stat card principali |
| `DashboardTextAnalysisSection.kt` | Tab parole/emoji/sticker + selettore limite |
| `DashboardPreviewHost.kt` | Host modali anteprima emoji e sticker |

**MediaStats** (`ui/screen/media/`):

| File | Contenuto |
|------|-----------|
| `MediaStatsHeader.kt` | Header con info dialog |
| `MediaStatsSearchBar.kt` | Barra di ricerca con slot fisso anti-layout-shift |
| `MediaStatsGallerySheet.kt` | Bottom sheet galleria media con griglia, tab, selezione e condivisione |
| `MediaStatsLinkDomainComponents.kt` | Card link (`LinkStatCard`), chip dominio (`DomainChip`), sheet analisi domini (`DomainBreakdownSheet`) con slider date e info button |
| `MediaStatsLinkPreview.kt` | Composable `LinkPreviewSection` e `LinkPreviewFallback`: rendering stato loading/loaded/unavailable della card anteprima |
| `MediaStatsLinkPreviewResolver.kt` | Fetch rete, parsing HTML/OG/Twitter/oEmbed, decodifica entità HTML, cache in-memory con TTL differenziato successo/fallimento, metriche per dominio |

Dopo il refactor `MediaStatsScreen.kt` è sceso da ~2000 a **583 righe** fungendo da puro orchestratore.

### Componente di Scorrimento Rapido: ScrollToEdgeFab

**File**: `ui/component/ScrollToEdgeFab.kt`

Per migliorare l'esperienza utente (UX) nelle liste molto lunghe, è stato introdotto il componente riusabile `ScrollToEdgeFab`. Si tratta di un pulsante flottante compatto che consente di tornare rapidamente all'inizio (o alla fine, in caso di layout invertito) della lista o griglia con un singolo tocco.

- **Design e Animazioni**: Il pulsante è un `SmallFloatingActionButton` (Material 3) che utilizza i colori del tema (`primaryContainer` e `onPrimaryContainer`). L'apparizione e la scomparsa sono animate in modo fluido tramite una combinazione di dissolvenza e ridimensionamento (`fadeIn() + scaleIn()` / `fadeOut() + scaleOut()`).
- **Logica di Visibilità**: Per ottimizzare le prestazioni, la visibilità del pulsante è calcolata tramite `derivedStateOf` e appare solo quando l'utente supera una determinata soglia di scorrimento (default: 3 elementi).
- **Supporto Griglie e Liste**: Dispone di overload sia per `LazyListState` (liste) sia per `LazyGridState` (griglie).
- **Scorrimento Invertito (`reverseScroll`)**: Supporta liste con layout invertito (`reverseLayout = true` come nella Timeline dei gruppi). Se abilitato, l'icona si trasforma in una freccia verso il basso (`KeyboardArrowDown`) e il tocco esegue lo scorrimento rapido verso la fine visiva della lista (indice 0), ovvero verso i messaggi più recenti.
- **Schermate Integrate**:
  - `ChatListScreen`: Integrato con un offset verticale personalizzato in Lite Mode per evitare la sovrapposizione con il pulsante "Aggiungi ZIP".
  - `MediaStatsScreen`: Attivo sia sulla griglia della Media Gallery globale, sia nel Bottom Sheet della galleria per chat (con riposizionamento automatico all'indice 0 al cambio di tab Galleria/Sticker).
  - `CallsStatsScreen` e `WordSearchScreen`: Integrato nelle liste principali e nei bottom sheet di dettaglio per navigare agevolmente tra i log delle chiamate e i messaggi cercati.
  - `GroupHistoryScreen`: Configurato con `reverseScroll = true` per facilitare lo scorrimento verso i messaggi recenti nella timeline.

### Pulsante Informativo Animato Premium: AnimatedInfoButton

**File**: `ui/component/AnimatedInfoButton.kt`

Per catturare l'attenzione dell'utente ed incentivarne l'interazione con gli elementi informativi che spiegano definizioni e metriche, è stata implementata la componente interattiva premium `AnimatedInfoButton` che sostituisce i vecchi pulsanti statici.

- **Design e Animazioni**:
  - **Heartbeat scale**: l'icona respira delicatamente oscillando in modo continuo tra una scala di `0.95x` e `1.05x` con un'easing morbido (`FastOutSlowInEasing`) ogni 1000ms.
  - **Sonar ripple (echo wave)**: sul fondo dell'icona viene disegnato un anello circolare sfumato (`glowColor`) che si espande concentricamente (da `1.0x` a `1.8x`) svanendo da un'opacità di `0.4f` a `0.0f` ogni 2000ms con accelerazione `EaseOutQuad`.
- **Prestazioni**: Il rendering dell'echo wave avviene in modo ultra-performante direttamente sul canvas tramite `drawBehind` su area riservata `iconSize * 2`, prevenendo ricomposizioni non necessarie.
- **Integrazione Oculata**: L'animazione è applicata esclusivamente a pulsanti effettivamente interattivi (che aprono modali e dialog) per evitare di confondere l'utente, lasciando invece statici i Bullet Point puramente decorativi.
- **Schermate Integrate**:
  - `WordSearchScreen` (riga 305): spiega la logica di ricerca delle parole e dei suggerimenti.
  - `ChatDetailScreen` (riga 1248): integrato nella `SectionCard` per arricchire istantaneamente tutte le schede metriche (es. Conversation Starters, risposta media).
  - `MilestonePredictionCard` (riga 132): invita a scoprire i criteri della previsione traguardi.
  - `MediaStatsScreen` (righe 196 e 667): attivo sia nell'header che nella galleria media per informare sulle statistiche dei file e domini.

### Flussi principali di navigazione

**Avvio normale (app chiusa, nessun share)**
```
MainActivity.onCreate
    → SetupScreen (autoNavigate=true)
    → isRootReady || hasLiteDb?
        → sì: auto-naviga al mode graph corrispondente
        → no: l'utente sceglie manualmente
```

**Share da WhatsApp (app chiusa)**
```
Intent ACTION_SEND
    → MainActivity.handleIncomingIntent()
    → mainViewModel.setIncomingZipUri(uri)
    → SetupScreen (autoNavigate=true)
    → LaunchedEffect(incomingZipUri): liteViewModel.onZipSelected()
    → import ZIP → SenderSelectionDialog → conferma
    → liteState.isComplete = true
    → onSetupComplete() → naviga a lite_mode_graph
    → Dashboard Lite (dati freschi, cache invalidata)
```

**Back dalla Dashboard Lite**
```
DashboardScreen.onNavigateBack()
    → mainViewModel.setIncomingZipUri(null)  ← pulizia URI
    → navigate(Setup, autoNavigate=false) {
          popUpTo("app_graph") { inclusive = false }
      }
    → SetupScreen (autoNavigate=false)
    → shouldAutoNavigate = false → auto-nav bloccata
    → l'utente vede Setup con hasLiteDb=true
```

**Nuovo share su Setup con autoNavigate=false**
```
Intent → mainViewModel.setIncomingZipUri(nuovoUri)
    → SetupScreen: LaunchedEffect(incomingZipUri) scatta
    → shouldAutoNavigate = true  ← riabilitato
    → liteViewModel.onZipSelected(nuovoUri)
    → import → completamento → naviga a lite_mode_graph
```

---

## 15. Tutorial di onboarding

Un overlay a 7 pagine (`HorizontalPager`) mostra il tutorial interattivo al primo avvio e, in qualsiasi momento, dal menu hamburger.

### Trigger e stato

| Meccanismo | Comportamento |
|-----------|---------------|
| Primo avvio | `LaunchedEffect` in `AppNavigation.kt` rileva `hasSeenTutorial = false` → chiama `mainViewModel.requestTutorial()` |
| Voce drawer "Tutorial" | `DrawerItem` con `Icons.AutoMirrored.Outlined.HelpOutline` → `mainViewModel.requestTutorial()` |
| Completamento | `mainViewModel.completeTutorial()` → salva `tutorial_seen = true` in DataStore e chiude l'overlay |
| "Salta" | Chiude senza salvare il flag (il tutorial riappare al prossimo avvio) |

**Persistenza** — chiave `booleanPreferencesKey("tutorial_seen")` in `AppPreferencesStore`, stesso DataStore (`"app_preferences"`) delle altre preferenze. Esposta come `hasSeenTutorial: StateFlow<Boolean>` in `MainViewModel` (initialValue = `true` per evitare flash durante la raccolta iniziale).

**State flow tutorial**:
```
MainViewModel._showTutorial: MutableStateFlow<Boolean>
    requestTutorial()  → _showTutorial = true
    dismissTutorial()  → _showTutorial = false
    completeTutorial() → setTutorialSeen(true) + _showTutorial = false
```

**Overlay** — Dialog full-screen (`DialogProperties(usePlatformDefaultWidth = false)`) sovrapposto al `Box` principale in `AppNavigation.kt`, all'esterno dell'`AppScaffold`. Non interrompe il grafo di navigazione.

### Struttura delle 7 pagine

Ogni pagina è una `@Composable private fun` dedicata. Non esiste un template generico condiviso: ogni layout rispecchia la schermata reale corrispondente.

| # | Composable | Contenuto mockup |
|---|-----------|-----------------|
| 0 | `WelcomeTutorialPage` | Icona `Security` 120 dp in cerchio + nome app in `displaySmall` + tagline privacy. Sfondo gradiente `primaryContainer → surface`. |
| 1 | `ModesTutorialPage` | Due `ElevatedCard` affiancate: Root (`AdminPanelSettings`, `primaryContainer`) e Lite (`FolderZip`, `secondaryContainer`), con icona 48 dp, titolo e sottotitolo. |
| 2 | `ImportTutorialPage` | 4 passaggi numerati (`NumberedStep`) con frecce animate tra un passo e l'altro (`StepArrow` con `infiniteRepeatable`). |
| 3 | `DashboardTutorialPage` | 3 `FakeStatCard` (messaggi totali / media/giorno / streak) + grafico a barre settimanale 7 colonne reale con etichette L/M/M/G/V/S/D. |
| 4 | `RadarTutorialPage` | `OutlinedTextField` mock con parola cercata + riga 4 `FilterChip` (Aa / ab / ? / Multi) + sezione "Occorrenze per mittente" con 3 `SenderOccurrenceBar` + anteprima messaggio con parola evidenziata in `buildAnnotatedString` (`SpanStyle(fontWeight = Bold, color = tertiary, background = tertiaryContainer)`). |
| 5 | `MediaTutorialPage` | Tab bar "Galleria / Link" (`FakeTab`) + riga 4 `MediaCountChip` (Immagini / Video / Audio / Link con contatori) + griglia 3×2 di `Box` thumbnail colorate. |
| 6 | `ReadyTutorialPage` | `CheckCircle` 96 dp con animazione pulse-scala (`infiniteRepeatable`, `FastOutSlowInEasing`) + titolo + descrizione. |

### Barra di navigazione pager

In basso: indicatori dot animati (dot attivo = `width 20 dp`, inattivo = `8 dp`) + pulsante **Avanti** (diventa **Inizia** sull'ultima pagina). Il pulsante "Salta" è sempre visibile in alto a destra.

### File coinvolti

| File | Ruolo |
|------|-------|
| `ui/screen/tutorial/TutorialOverlay.kt` | Overlay completo e tutte le 7 composable pagina |
| `data/store/AppPreferencesStore.kt` | Chiave `tutorial_seen`, flow `hasSeenTutorial`, `setTutorialSeen()` |
| `ui/MainViewModel.kt` | `showTutorial: StateFlow`, `requestTutorial()`, `dismissTutorial()`, `completeTutorial()` |
| `ui/component/AppDrawer.kt` | Voce "Tutorial" con icona `HelpOutline` |
| `ui/navigation/AppNavigation.kt` | `LaunchedEffect` primo avvio + overlay Dialog sovrapposto all'`AppScaffold` |
| `res/values*/strings.xml` | Chiavi `tutorial_*` in 8 lingue |

---

## 16. Roadmap

### Pianificato (non ancora implementato)

- Confronto tra due periodi temporali
- Supporto WhatsApp Business
- Modalità "time capsule" (analisi di un anno fa)
- Supporto formato crypt15 con inserimento manuale della chiave a 64 hex
- Backup automatico delle statistiche (non dei messaggi)
- Supporto lingue export Lite Mode oltre l'italiano

---

> Se WhatsApp rilascia un aggiornamento che modifica il formato del database o i percorsi dei file, verificare i DAO in `data/db/dao/` e il `FileExtractor` in `data/root/`.

---

## 17. Internazionalizzazione (i18n)

Tutte le stringhe visibili nella UI sono esternalizzate in file di risorse Android (`res/values-*/strings.xml`). Non sono presenti stringhe hardcodate nelle classi Kotlin o Composable: ogni testo è referenziato tramite `stringResource(R.string.xxx)`.

**Lingue supportate**:

| Codice | Lingua |
|--------|--------|
| `values/` (default) | Inglese |
| `values-it/` | Italiano |
| `values-de/` | Tedesco |
| `values-es/` | Spagnolo |
| `values-fr/` | Francese |
| `values-hi/` | Hindi |
| `values-pt-rBR/` | Portoghese (Brasile) |
| `values-tr/` | Turco |

Le stringhe coprono navigazione, titoli, pulsanti, dialog, formati data/ora e label di accessibilità.

**Cambio lingua a runtime** — La lingua selezionata dall'utente viene persistita in `SharedPreferences` (`app_language_prefs`, chiave `app_language`, valori `"system"` / `"en"` / `"it"` / ecc.). `MainActivity.attachBaseContext()` legge questa preferenza in modo sincrono **prima** che il contesto Activity venga creato e applica il `Locale` tramite `Context.createConfigurationContext()`. Questo garantisce che tutti i contesti derivati — inclusi quelli creati internamente da `Dialog` e `ModalBottomSheet` in Compose — ereditino il locale corretto senza eccezioni. Al cambio di lingua da UI, `Activity.recreate()` riapplica il meccanismo.

---

## 18. Modalità Lite (non-root)

La **Modalità Lite** permette di analizzare le chat WhatsApp senza permessi root. Invece di leggere `msgstore.db` direttamente, l'utente importa uno o più **ZIP di esportazione WhatsApp** (generati da WhatsApp con "Esporta chat → Includi media"). Ogni ZIP aggiunge una chat al database locale, rendendo possibile l'analisi multi-chat anche in Lite Mode.

Questa sezione descrive l'architettura Lite. Il punto architetturale centrale è che **dopo la migrazione a SQLite, i DAO e `AnalyticsRepository` sono quasi identici** per Root e Lite Mode — il routing verso il database corretto è gestito da `ActiveDatabaseProvider` in modo trasparente.

---

### 16.1 — Architettura del database (`ActiveDatabaseProvider`)

**File**: `data/db/ActiveDatabaseProvider.kt`

`ActiveDatabaseProvider` implementa l'interfaccia `DatabaseProvider` ed è il punto di commutazione tra Root e Lite Mode:

```kotlin
override fun getMsgDb(): SQLiteDatabase =
    if (isLiteMode) liteDatabase.getOrCreate() else whatsAppDatabase.getMsgDb()

override fun getWaDb(): SQLiteDatabase? =
    if (isLiteMode) liteDatabase.getWaDb() else whatsAppDatabase.getWaDb()
```

Il flag `isLiteMode` è persistito in `SharedPreferences` (`whatstat_lite_prefs`) e sopravvive ai riavvii dell'app. `AnalyticsRepository` espone il flag come `StateFlow<Boolean>` leggendolo da `activeDatabaseProvider.isLiteMode`.

Il risultato pratico è che tutti i DAO (MessageDao, ChatDao, ecc.) eseguono le stesse query SQL sia su `msgstore.db` (Root) sia su `lite_msgstore.db` (Lite), **senza branch nel codice di business logic**. Le uniche differenze residue in `AnalyticsRepository` riguardano i limiti numerici (es. 50 elementi in Lite vs 20 in Root per le word cloud, data la dimensione tipicamente minore delle chat esportate).

---

### 16.2 — Database Lite (`LiteDatabase`)

**File**: `data/lite/LiteDatabase.kt`

`LiteDatabase` gestisce due file SQLite in `filesDir`:

- **`lite_msgstore.db`** — database principale, con schema compatibile con `msgstore.db` Root. Tabelle create: `jid`, `chat`, `message`, `message_media`. Gli indici SQL sono identici al Root per garantire le stesse prestazioni.
- **`lite_wa.db`** — stub minimale con la tabella `wa_contacts` (colonne: `_id`, `jid`, `display_name`). Serve per la risoluzione nomi in `AnalyticsRepository.resolveDisplayName()`. In Lite Mode `jid` contiene il nome del mittente grezzo dall'export (es. "Mario Rossi"), non un JID reale. Per le chat 1-1, `LiteDbWriter.upsertChat` popola `wa_contacts` con una riga sender→chatName, in modo che `resolveContactName` restituisca il nome della chat (derivato dal filename ZIP) invece del nome grezzo del mittente. Al rename, `LiteDbWriter.renameChat` aggiorna le righe corrispondenti in `wa_contacts` e in `jid` per tutti i mittenti della chat.

**Nota su `jid` in Lite Mode**: il campo `raw_string` nella tabella `jid` contiene il nome del mittente (es. `"Mario Rossi"`), non un JID reale WhatsApp. Questo è l'unico dato disponibile dall'export `.txt`. Per l'utente corrente, `raw_string` è il `ownerName` scelto durante il setup.

**Nota sugli sticker**: in Root Mode i messaggi sticker (`message_type=20`) vengono esclusi dalle query media perché mancano di `mime_type`. In Lite Mode vengono inclusi, poiché i file `.webp` estratti dallo ZIP hanno `mime_type='image/webp'` impostato da `LiteDbWriter`. Inoltre, le query sulle statistiche media accettano il parametro  `includeStickers` nel DAO: in Lite Mode il repository lo imposta automaticamente a `true`, in modo che il conteggio dei media nella `MediaStatsScreen` sia la somma di media normali e sticker, senza query aggiuntive.

---

### 16.3 — Panoramica del flusso di import

```
Utente tocca "Importa ZIP" (SetupScreen o "Aggiungi chat" in ChatList)
        ↓
LiteSetupScreen / AddChatZipScreen
        ↓
LiteSetupViewModel.onZipSelected(uri)
        ↓  (LiteImportService: foreground service con overlay di progresso in UI)
LiteModeManager.importAndProcess(uri, "Guest")  ← run 1: parsing preliminare
        ↓
FileExtractor.extractFullChatFolder(zipUri)
        →  filesDir/lite_media/lite_chat_{ts}/
              ├── source.zip          ← ZIP integrale (immagini/sticker/docs letti on-the-fly)
              ├── *.txt               ← file testo estratto per il parser
              └── VID-*.mp4, AUD-*.opus, ...  ← video/audio estratti per seeking player
        ↓
TextChatParser.parse(txtFile, "Guest")  →  ParseResult(messages, groupEvents)
        ↓  (risposta: identifiedSenders → UI mostra il picker "Chi sei tu?")
LiteMediaManager.extractMediaFromFolder(folder, messages)
        →  itera entry di source.zip; path virtuali zip:// per immagini, path fisici per video/audio
        ↓
LiteDbWriter.writeChat(...)  →  inserisce messaggi e media nel DB Lite
        ↓
[background] FileExtractor.slimSourceZip(folder)
        →  riscrive source.zip rimuovendo le entry video/audio già estratte su disco
        ↓
Utente sceglie il proprio nome dal picker
        ↓
LiteModeManager.finalizeOwnerSelection(ownerName)  ← run 2: aggiorna from_me nel DB
        ↓
analyticsRepository.setLiteMode(true)
analyticsRepository.notifyLiteImportDone()   ← bumpa LiteModeManager.importCompleted
        ↓
Navigazione a NavGraphRoute.Lite (ChatList con tutte le chat importate)
```

**Doppio processing**: la prima chiamata con `ownerName = "Guest"` serve solo per identificare i mittenti. Dopo la scelta dell'utente, `finalizeOwnerSelection()` aggiorna `from_me` nel DB SQLite per i messaggi del mittente selezionato — non è necessario rieseguire il parsing completo.

**Overlay di progresso**: durante il processing, `LiteImportService` (foreground service) invia gli aggiornamenti di stato a `LiteSetupViewModel` tramite `LiteModeManager.importProgress` (`StateFlow<LiteImportProgress>`). La `LiteSetupScreen` mostra un overlay con barra di progresso e label descrittiva (es. "Estrazione ZIP… 5%", "Lettura messaggi… 20%", "Salvataggio database… 60%").

---

### 16.4 — Struttura dello ZIP WhatsApp

WhatsApp esporta la chat come ZIP con questa struttura piatta:

```
Chat WhatsApp con Mario Rossi.zip
├── Chat WhatsApp con Mario Rossi.txt   ← export testuale
├── IMG-20240101-WA0001.jpg             ← media allegati
├── VID-20240215-WA0003.mp4
├── AUD-20240310-WA0007.opus
├── PTT-20240401-WA0002.opus            ← messaggi vocali
├── DOC-20240501-WA0001.pdf
└── ...
```

Il file `.txt` contiene una riga per ogni messaggio nel formato:
```
DD/MM/YY, HH:MM - NomeMittente: testo del messaggio
```
o, per messaggi con media:
```
28/07/24, 16:35 - Mario Rossi: IMG-20240728-WA0003.jpg (file allegato)
28/07/24, 16:35 - Mario Rossi: ‎<Media omesso>
```

Lo ZIP viene elaborato da `FileExtractor.extractFullChatFolder()` in modo **ibrido**:
- L'archivio intero viene copiato in `filesDir/lite_media/lite_chat_{ts}/source.zip`.
- Solo il file `.txt`, i video (`.mp4`, `.3gp`, `.mov`, `.m4v`) e gli audio (`.opus`, `.aac`, `.mp3`, `.m4a`, `.wav`) vengono estratti fisicamente su disco — i player nativi Android richiedono seek su file reali.
- Tutto il resto (immagini `.jpg`/`.webp`, documenti `.pdf`, ecc.) resta nello ZIP e viene letto on-the-fly da `ZipFileFetcher`.
- In background, `slimSourceZip()` riscrive lo ZIP rimuovendo le entry video/audio duplicate.

---

### 16.5 — Parsing del file di testo (`TextChatParser`)

**File**: `data/lite/TextChatParser.kt`

Il parser gestisce due formati di export: **Android** (predefinito) e **iOS**. La detezione avviene automaticamente controllando se il file TXT si chiama `_chat.txt` (iOS) o meno.

| Regex | Scopo |
|-------|-------|
| `headerRegex` | Riga inizio messaggio Android: `DD/MM/YY, HH:MM - Mittente: body` |
| `iosHeaderRegex` | Riga inizio messaggio iOS: `[DD/MM/YY, HH:MM] Mittente: body` |
| `systemEventRegex` | Evento di sistema Android senza due punti |
| `iosSystemEventRegex` | Evento di sistema iOS senza due punti |

**Formato Android** (esempio):
```
DD/MM/YY, HH:MM - NomeMittente: testo del messaggio
28/07/24, 16:35 - Mario Rossi: IMG-20240728-WA0003.jpg (file allegato)
```

**Formato iOS** (esempio):
```
[DD/MM/YY, HH:MM] NomeMittente: testo del messaggio
[28/07/24, 16:35] Mario Rossi: <allegato: 00000041-STICKER-2022-12-01-14-16-17.webp>
```

**Differenze iOS**:
- I timestamp sono racchiusi in parentesi quadre `[]`
- WhatsApp iOS antepone `\u200E` (LTR Mark) ai messaggi; il parser lo rimuove
- I media sono indicati come `<etichetta: filename>` invece che inline
- Gli eventi di sistema appaiono come messaggi con mittente (es. "Tu: Hai cambiato il nome del gruppo")

**Rilevamento media** (`hasMedia`): un messaggio è considerato multimediale se il `body` contiene una delle seguenti stringhe (supportate in tutte le 8 localizzazioni):

- IT: `"omesso"`, `"omessi"`, `"omessa"`, `"allegato"`
- EN: `"omitted"`, `"attached"`
- DE: `"weggelassen"`, `"ausgelassen"`, `"angehängt"`
- ES: `"omitido"`, `"omitida"`, `"adjunto"`, `"adjunta"`
- FR: `"omis"`, `"omise"`, `"joint"`, `"jointe"`
- PT: `"oculta"`, `"oculto"`, `"ocultada"`, `"ocultado"`, `"anexo"`, `"anexa"`
- TR: `"atlandı"`, `"eklendi"`
- HI: `"छुपा"`, `"जोड़ा"`

- `"<Media"`
- `"PTE-"` (messaggi vocali)

In iOS, la funzione `stripMediaPlaceholders()` estrae prima il filename dai tag `<etichetta: filename>` preservandolo nel contenuto.

**Punto critico**: il campo `content` è sempre popolato con `cleanBody` anche per i messaggi media. Questo è fondamentale per permettere a `LiteMediaManager` di recuperare il nome file (es. `IMG-20240728-WA0003.jpg`) dal testo del messaggio per associare il file fisico al mittente corretto.

**Modello `ParsedMessage`**:
```kotlin
data class ParsedMessage(
    val timestamp: LocalDateTime,
    val senderName: String,        // nome grezzo dall'export, es. "Mario Rossi"
    val content: String,           // testo (o nome file per i media)
    val isCurrentUser: Boolean,    // true se senderName == ownerName
    val isSystemMessage: Boolean,
    val linkCount: Int,
    val emojiCount: Int,
    val hasMedia: Boolean
)
```

Il `senderName` viene ripulito dal carattere Unicode LTR Mark (`\u200E`) che WhatsApp inserisce. Il campo `isCurrentUser` viene impostato definitivamente da `LiteModeManager.finalizeOwnerSelection()` tramite aggiornamento SQL su `message.from_me`.

**Righe multi-riga**: le righe che non corrispondono all'`headerRegex` vengono accodate al messaggio corrente tramite `appendToCurrent()`.

---

### 16.6 — Associazione media → mittente (`LiteMediaManager`)

**File**: `data/lite/LiteMediaManager.kt`

Questo componente itera sulle **entry di `source.zip`** (tramite `ZipFile`, non sul filesystem) e, per ciascuna, cerca il messaggio corrispondente nell’elenco dei messaggi parsati.

**Algoritmo di associazione**:
1. Per ogni `ParsedMessage`, `MEDIA_FILENAME_REGEX` viene applicata al campo `content` per trovare il nome del file media menzionato.
2. Viene costruita una mappa `fileNameToMsgInfo: Map<String, Pair<senderName?, timestamp>>` con due chiavi per ogni match: nome completo (con estensione) e nome senza estensione.
3. Quando si itera sui file fisici nella cartella estratta, il nome del file (con e senza estensione) viene cercato nella mappa.
4. Se trovato: `senderName` e `timestamp` provengono dal messaggio corrispondente.
5. Se non trovato: `senderName = null`, `timestamp = file.lastModified()` (fallback).

**`MEDIA_FILENAME_REGEX`** (definita in `LiteMetricsCalculator`):
```kotlin
Regex("(?:(?:IMG|VID|AUD|PTT|DOC|STK)-\\d{8}-WA\\d{4,}|\\d{8}-[A-Z]+-\\d{4}-\\d{2}-\\d{2}-\\d{2}-\\d{2}-\\d{2})(?:\\.[a-z0-9]+)?", RegexOption.IGNORE_CASE)
```

Il secondo pattern (`\\d{8}-[A-Z]+-...`) gestisce i file iOS (es. `00000041-STICKER-2022-12-01-14-16-17.webp`).

**Classificazione tipo media e path salvato nel DB**:

| Estensione file | Tipo | Path in `message_media.file_path` |
|-----------------|------|-----------------------------------|
| `.jpg`, `.jpeg`, `.png` | `IMAGE` | `zip://lite_chat_xxx/source.zip\|IMG-2024.jpg` |
| `.gif` | `VIDEO` | `zip://lite_chat_xxx/source.zip\|GIF-2024.gif` (vedi nota GIF) |
| `.webp` (non STK-, non STICKER) | `IMAGE` | `zip://lite_chat_xxx/source.zip\|foto.webp` |
| `.webp` (STK- o contiene "STICKER") | `STICKER` | `zip://lite_chat_xxx/source.zip\|STK-2024.webp` |
| `.pdf` | `DOCUMENT` | `zip://lite_chat_xxx/source.zip\|doc.pdf` |
| altro | `DOCUMENT` | `zip://lite_chat_xxx/source.zip\|altro.xxx` |

**Nota GIF**: Le GIF vengono classificate come `VIDEO` in Lite Mode per consentire la riproduzione via Coil Video. Il MIME salvato è `image/gif` per compatibilità con il rendering.

**Nota sticker iOS**: In Lite Mode i file `.webp` con prefisso `STK-` o che contengono "STICKER" nel nome vengono classificati come `STICKER` e mostrati nel tab dedicato della `MediaStatsScreen`. La funzione `isLiteSticker()` riconosce entrambi i formati.

**Percorsi su disco**: Video (`.mp4`, `.3gp`, `.mov`, `.m4v`) e audio (`.opus`, `.aac`, `.mp3`, `.m4a`, `.wav`) richiedono seek su file reali, vengono quindi estratti su disco con path assoluti. Il `FileProvider` (`${applicationId}.fileprovider`) espone questi file ai player esterni.

---

### 16.7 — Coordinatore (`LiteModeManager`)

**File**: `data/lite/LiteModeManager.kt`

Singleton Hilt che orchestra l'intero flusso di importazione. Metodi principali:

| Metodo | Descrizione |
|--------|-------------|
| `importAndProcess(zipUri, ownerName)` | Pipeline completa: estrazione → parsing → fingerprint → media → scrittura SQLite |
| `finalizeOwnerSelection(ownerName)` | Aggiorna `from_me` nel DB per i messaggi del mittente scelto |
| `getFirstMessageTimestamp()` | Timestamp del messaggio più antico nel DB Lite (usato per `minDate` del DateRangePicker) |
| `notifyImportDone()` | Bumpa `importCompleted` StateFlow per notificare i ViewModel |
| `clearCache()` | Cancella `lite_msgstore.db` e `lite_wa.db` |
| `upsertChat` | Scrive la chat nel DB. Per le chat 1-1, popola anche `wa_contacts` con la mappatura sender→chatName per permettere a `resolveContactName` di restituire il nome corretto. |
| `renameChat` | Aggiorna il nome della chat. Per le chat 1-1, aggiorna anche `wa_contacts` e `jid.raw_string` per tutti i mittenti della chat, in modo che le slow stats ricalcolate riflettano il nuovo nome. |
| `slimSourceZip(folder)` | **[background]** Riscrive `source.zip` senza le entry video/audio già estratte. Chiamato in fire-and-forget dopo `writeChat`. |

#### Estrazione nome chat (`normalizeExportName`)

Il nome della chat viene estratto dal nome del file ZIP/TXT rimuovendo il prefisso standard di WhatsApp. La regex è **ancorata alla parola "WhatsApp"** per evitare match accidentali dentro i nomi dei contatti.

**Regex attuale**:
```kotlin
Regex(
    """.*\bWhatsApp\b.*?\b(con|with|avec|mit|com|met|med|с|z\s+użytkownikiem|z\s+grupą)\s+""",
    RegexOption.IGNORE_CASE
)
```

**Lingue supportate**: IT (`con`), EN (`with`), FR (`avec`), DE (`mit`), PT (`com`), RU (`с`), PL (`z użytkownikiem`/`z grupą`), NL/AF (`met`), NO/SE/DA (`med`).

Se "WhatsApp" non è presente nel nome del file (es. file rinominato manualmente), il nome viene restituito intero.

#### Content fingerprint (deduplicazione chat omonime)

Quando più chat condividono lo stesso nome contatto (es. stesso contatto su numeri diversi), il fingerprint evita la sovrascrittura accidentale.

**Come funziona**:
1. `computeContentFingerprint()` calcola un hash MD5 dei timestamp dei primi 10 messaggi
2. I primi 6 caratteri hex dell'hash diventano il fingerprint (16^6 = ~16M combinazioni)
3. Il fingerprint viene incluso nell'`importKey`: `"dm:matilde_barbaro:a3f7c1"`
4. `importKey` è una colonna UNIQUE nella tabella `chat`: re-import dello stesso ZIP → stessa chiave → sovrascrittura (UPDATE); ZIP diverso dello stesso contatto → fingerprint diverso → nuova chat (INSERT)

La disambiguazione del display name è delegata a `LiteDbWriter.disambiguateDisplayName()`: se il nome esiste già nel DB, appende un suffisso progressivo `(2)`, `(3)`, ecc.

---

### 16.8 — Invalidazione e ricarica dei ViewModel

Quando viene importato un nuovo ZIP, `notifyLiteImportDone()` chiama `liteModeManager.notifyImportDone()` che bumpa `importCompleted: StateFlow<Long>`. Ogni ViewModel che consuma dati Lite osserva questa `StateFlow` nel proprio `init` block e reagisce:

| ViewModel | Reazione al bump di `importCompleted` |
|-----------|---------------------------------------|
| `ChatDetailViewModel` | Chiama `loadChatStats()` direttamente (skippa la prima emissione per evitare doppio carico) |
| `MediaStatsViewModel` | Resetta `initialized = false` e chiama subito `loadStats()` |
| `WordSearchViewModel` | Resetta `initialized = false` (la `LaunchedEffect` richiamerà `initialize()` alla prossima navigazione) |

Quando si passa da Root a Lite Mode o viceversa, `FilterStateHolder.resetAll()` viene chiamato da `AnalyticsRepository.setLiteMode()` per azzerare i filtri temporali attivi ed evitare stati persistenti inconsistenti tra le due modalità.

---

### 16.9 — Navigazione in Lite Mode

**File**: `ui/navigation/AppNavigation.kt`

La Lite Mode ha un proprio grafo di navigazione `NavGraphRoute.Lite`, con la stessa struttura a 5 tab del Root Mode. Questo elimina la necessità di casi speciali (come il vecchio `chatId = -1L`) e rende la UX identica nelle due modalità.

- **Dopo il primo import**: `LiteSetupViewModel` imposta `isComplete = true` → la navigazione usa `popUpTo(NavGraphRoute.Setup)` e naviga a `NavGraphRoute.Lite`.
- **ChatList in Lite Mode**: mostra tutte le chat importate con pulsante "Aggiungi chat" che naviga a `Screen.LiteSetupAdd` (route `lite_setup_add?zipUri={zipUri}`). Ogni chat ha le azioni rename e delete.
- **Tasto back dalla Dashboard**: `BackHandler` chiama `onNavigateBack()` → `mainViewModel.resetLiteMode()` (imposta `isLiteMode = false` in `ActiveDatabaseProvider` + cancella DB Lite) → naviga a Setup con `popUpTo("app_graph") { inclusive = false }`.
- **Condivisione ZIP diretta**: se l'app riceve un `Intent.ACTION_SEND` con URI di uno ZIP, la navigazione porta direttamente a `LiteSetupAdd` con l'URI pre-compilato.

---

### 16.10 — Limitazioni rispetto alla Root Mode

| Funzionalità | Root Mode | Lite Mode |
|--------------|-----------|-----------|
| Numero di chat analizzabili | Tutte (da `msgstore.db`) | Multiple (un import ZIP per chat) |
| Risoluzione nomi contatti | Sì (da `wa.db`) | No (nome grezzo dall'export `.txt`) |
| Chiamate | Sì (tabella `call_log`) | No (nessun dato nel `.txt`) |
| Sticker tab analisi testi | Sì | No (nascosto in Lite Mode) |
| Conteggio sticker nei media | Separato dai media | Incluso nel conteggio media (tipo 20 sommato a media normali) |
| Milestone prediction | Sì | No (nascosta in Lite Mode) |
| Rank badge in ChatList | Sì | No (nascosto in Lite Mode) |
| Filtro media per mittente | Esatto (JID) | Euristico (filename-match) |
| Timestamp media | Esatto (DB) | Derivato dal messaggio; fallback a `file.lastModified()` |
| `isCurrentUser` | `from_me=1` da DB WhatsApp | Aggiornato da `finalizeOwnerSelection()` |
| Analisi lessicale incrementale | Sì (indice gzipped delta) | No (query SQL diretta ogni volta) |
| Supporto lingue export | N/A | Supporto multilingua completo per le 8 localizzazioni (italiano, inglese, tedesco, spagnolo, francese, portoghese, turco, hindi) |
| Mappatura GIF | N/A | Classificate ed esposte come VIDEO nel DB e nella galleria (supportate in riproduzione da Coil Video) |
| Classificazione Sticker iOS | N/A | Riconoscimento ed inclusione degli sticker iOS (pattern `STICKER`) nella corretta tab Galleria |

---

### 16.11 — File coinvolti (riepilogo)

| File | Ruolo |
|------|-------|
| `data/lite/TextChatParser.kt` | Parsing del file `.txt` WhatsApp |
| `data/lite/LiteMediaManager.kt` | Scansione media e associazione file → mittente |
| `data/lite/LiteDatabase.kt` | Gestione dei database SQLite Lite (`lite_msgstore.db`, `lite_wa.db`) |
| `data/lite/LiteDbWriter.kt` | Scrittura chat/messaggi/media su SQLite, rename chat, disambiguazione nomi |
| `data/lite/LiteModeManager.kt` | Coordinatore: parsing, fingerprint, normalizzazione nomi, scrittura DB, progress |
| `data/db/ActiveDatabaseProvider.kt` | Routing trasparente Root vs Lite per tutti i DAO |
| `data/root/FileExtractor.kt` | Estrazione ZIP ibrida: copia `source.zip` + estrae txt/video/audio su disco. `slimSourceZip()` rimuove i duplicati in background. |
| `ui/component/ZipFileFetcher.kt` | Coil `Fetcher` custom: decodifica immagini/sticker direttamente da `source.zip` in RAM tramite path virtuali `zip://`. |
| `data/repository/AnalyticsRepository.kt` | Espone `isLiteMode`, `liteCacheVersion`, `notifyLiteImportDone()`. Garantisce che `isLiteMode` sia attivo prima di qualsiasi operazione di cache; al rename in Lite Mode invalida `chat_slow_{id}` invece di tentare una patch del `senderBreakdown`. |
| `data/store/SenderColorOverrideStore.kt` | Persistenza DataStore dei colori personalizzati per mittente. Chiave: `chatId|jid`, valore: ARGB. |
| `service/LiteImportService.kt` | Foreground service per progress overlay durante l'import |
| `ui/screen/setup/LiteSetupScreen.kt` | UI onboarding Lite (primo import) |
| `ui/screen/setup/LiteSetupViewModel.kt` | Logica onboarding (doppio processing + sender selection) |
| `ui/screen/chatlist/ChatListScreen.kt` | Lista chat con azioni rename/delete e pulsante "Aggiungi chat" |
| `ui/screen/chatdetail/ChatDetailViewModel.kt` | Logica dettaglio chat. Mantiene `senderWordsPool`, `senderEmojisPool`, `senderStickersPool` per evitare query DB al cambio limite in modalità mittente singolo. |
| `ui/screen/chatlist/ChatListViewModel.kt` | Logica rename/delete chat |
| `ui/navigation/AppNavigation.kt` | Grafo `NavGraphRoute.Lite`, navigazione post-import, condivisione ZIP |

---

## 19. Linee Guida per Assistenti AI

Le regole operative per gli assistenti AI sono definite in `AGENTS.md` nella root del progetto.
Quel file è la fonte di verità — questa sezione è deprecata.

---

## 20. Monetizzazione: Ads

WhatStat usa un modello **Free con pubblicità** all'interno di un'unica app su Google Play. Non esistono versioni Pro separate: tutte le funzionalità sono disponibili gratuitamente con annunci AdMob.

### Modello di monetizzazione

| Versione | Accesso | Pubblicità | Layout |
|----------|---------|-----------|--------|
| Free | Tutte le funzionalità | Banner, native ads, interstitial occasionali | Come oggi, con spazi pub |

### Architettura: package `ads`

**`ads/AdsRepository`** — singleton `@Inject` che centralizza:
- gestione consenso UMP (`UserMessagingPlatform`)
- inizializzazione `MobileAds`
- precaricamento e mostraggio interstitial
- pubblicazione di `AdsUiState` come `StateFlow`
- cooldown interstitial (fisso: 12 minuti)
- flag `isTestBuild` (confronta `BuildConfig.ADMOB_APP_ID` con il demo ID Google)

```kotlin
val areAdsEnabled = canRequestAds && isMobileAdsInitialized && !easterEggActive
```

`AdsRepository` gestisce lo stato ads, inclusi interstitial classici e rewarded interstitial.

**`ads/AdsUiState`** — data class esposta come `StateFlow` da `MainViewModel.adsUiState`:

| Campo | Tipo | Significato |
|-------|------|-------------|
| `areAdsEnabled` | `Boolean` | Condizione master: `canRequestAds && isMobileAdsInitialized` |
| `canRequestAds` | `Boolean` | Consenso UMP valido (bypass automatico in test build) |
| `isPrivacyOptionsRequired` | `Boolean` | UMP richiede il pulsante "Opzioni privacy" nell'AppDrawer |
| `isMobileAdsInitialized` | `Boolean` | `MobileAds.initialize()` completato |
| `shouldShowBanner` | `Boolean` | Alias di `areAdsEnabled` (usato da `AppScaffold`) |
| `easterEggActive` | `Boolean` | Easter egg attivo (disattiva ads e sblocca insight) |
| `rewardedTabsUnlocked` | `Boolean` | Sblocco rewarded attivo per tab daily/monthly/yearly |
| `rewardedPredictionUnlocked` | `Boolean` | Sblocco rewarded attivo per card prediction |

### Flusso consenso UMP

All'avvio (`MainActivity.requestAdsConsent()`), `AdsRepository.requestConsent()` chiama:
1. `consentInformation.requestConsentInfoUpdate()` — verifica se il consenso è necessario
2. `UserMessagingPlatform.loadAndShowConsentFormIfRequired()` — mostra il form UMP se richiesto dalla zona geografica (EEA)
3. Se `canRequestAds()` è `true` dopo il form → `initializeMobileAdsIfPossible()`

In test build (`isTestBuild = true`), il consenso UMP è bypassato (`DEBUG_GEOGRAPHY_DISABLED`) e `MobileAds` viene inizializzato immediatamente, senza attendere callbacks.

### Flag `isTestBuild` e configurazione ID

Gli ID AdMob sono configurati in `local.properties` (non committato) e iniettati in `BuildConfig` via Gradle. Il repo contiene i **demo ID Google** come default:

```
# local.properties (non committare mai)
ADMOB_APP_ID=<real_app_id>
ADMOB_BANNER_UNIT_ID=<real_banner_unit_id>
ADMOB_NATIVE_UNIT_ID=<real_native_unit_id>
ADMOB_INTERSTITIAL_UNIT_ID=<real_interstitial_unit_id>
ADMOB_REWARDED_INTERSTITIAL_UNIT_ID=<real_rewarded_interstitial_unit_id>
```

`isTestBuild` è calcolato automaticamente: `BuildConfig.ADMOB_APP_ID == "ca-app-pub-3940256099942544~3347511713"`. Nessun toggle manuale — basta inserire gli ID reali in `local.properties` per disabilitare il bypass test.

### Placement pubblicitari

**Banner adattivo sopra la bottom navigation** — `ui/component/ads/BottomBannerAd.kt`, inserito in `AppScaffold.bottomBar` tramite una `Column` con `HorizontalDivider`. Visibile solo quando `adsUiState.shouldShowBanner = true`. Assente in SetupScreen e durante import Lite. Presente anche:
- `SetupScreen` — fissato in basso tramite outer `Box(Alignment.BottomCenter)`
- Bottom sheet Domain Analysis e Media Gallery in `MediaStatsScreen` — banner fisso in fondo alla sheet (layout `Column` + `weight(1f)`)
- `ZoomableImageGallery` — sovrapposto in basso al viewer fullscreen con `navigationBarsPadding()`

**Native ads** — `ui/component/ads/NativeAdCard.kt`, card `AndroidView` che ospita una `NativeAdView`. Visibili quando `areAdsEnabled = true`. Placement:

| Schermata | Posizione |
|-----------|-----------|
| `DashboardScreen` | Dopo la grid `StatCard`, prima di `ActivityRecapCard` |
| `ChatListScreen` | Dopo la 6ª chat, poi ogni 12 chat (solo se >= 10 chat; nascosto durante ricerca attiva) |
| `ChatDetailScreen` | Dopo le prime card statistiche, prima delle sezioni opt-in |
| `MediaStatsScreen` | Dopo la 3ª `LinkStatCard` (non nella griglia immagini) |
| `WordSearchScreen` | Dopo i risultati (solo dopo ricerca avvenuta, >= 10 risultati) |
| `CallsStatsScreen` | Dopo il riepilogo alto livello, prima della lista dettagliata |

**Interstitial** — caricati in anticipo da `AdsRepository.preloadInterstitialIfPossible()`, mostrati da `tryShowInterstitialAfterLongOperation()`. Gate: cooldown 12 minuti, `canRequestAds = true`, `isMobileAdsInitialized = true`, `easterEggActive = false`. Trigger validi:

| Source | Momento |
|--------|---------|
| `root_analysis_completed` | Analisi Root completata, prima di navigare alla Dashboard |
| `lite_import_completed` | Import ZIP Lite completato |
| `dashboard_arrived` | Prima apertura Dashboard nella sessione (cold start) |
| `chat_detail_enter` | Apertura di una chat in `ChatDetailScreen` |

Trigger esplicitamente vietati: back, cambio tab, apertura media, progress in corso, errori. Il cooldown da 12 minuti garantisce che qualsiasi trigger consecutivo venga silenziosamente ignorato.

**Rewarded interstitial (insight avanzati)** — caricati da `AdsRepository.preloadRewardedInterstitialIfPossible()`, mostrati su richiesta utente da Dashboard/ChatDetail per sbloccare sezioni bloccate.

Unlock separati:

| Unlock | Sezioni sbloccate | Durata |
|--------|-------------------|--------|
| `tabs` | tab `daily`, `monthly`, `yearly` in `ActivityRecapCard` | 2 ore |
| `prediction` | card `MilestonePredictionCard` (`prediction_title`) | 2 ore |

Dettagli comportamento:
- i due unlock sono indipendenti (sbloccare tabs non sblocca prediction)
- stato persistente su DataStore: resta valido anche dopo chiusura/riavvio app entro TTL
- easter egg attivo: entrambi gli unlock risultano attivi automaticamente
- toast localizzati distinti per successo tabs vs successo prediction

### AppDrawer — voci Privacy

`AppDrawerContent` espone:

| Voce | Condizione di visibilità | Comportamento |
|------|--------------------------|---------------|
| "Opzioni privacy" | `adsUiState.isPrivacyOptionsRequired` | Chiama `mainViewModel.showAdsPrivacyOptions(activity)` → UMP form |

### ProGuard

Il file `proguard-rules.pro` include regole esplicite `-keep` per:
- `com.google.android.gms.ads.**`
- `com.google.android.gms.internal.ads.**`
- `com.google.android.ump.**`

Senza queste regole, le classi vengono strippate da `-repackageclasses '' -overloadaggressively` causando crash a runtime in release.

### File coinvoluti (riepilogo)

| File | Ruolo |
|------|-------|
| `ads/AdsRepository.kt` | Centralizza consenso UMP, init MobileAds, interstitial e rewarded interstitial, state |
| `ads/RewardedUnlockStore.kt` | Persistenza DataStore TTL unlock rewarded (`tabs`, `prediction`) |
| `ads/AdsUiState.kt` | Data class stato ads |
| `ui/MainViewModel.kt` | Espone `adsUiState` come `StateFlow` |
| `ui/component/ActivityCard.kt` | Lock rewarded per tab daily/monthly/yearly |
| `ui/component/MilestonePredictionCard.kt` | Lock rewarded per card prediction |
| `ui/screen/dashboard/DashboardScreen.kt` | CTA unlock rewarded + toast localizzati |
| `ui/screen/chatdetail/ChatDetailScreen.kt` | CTA unlock rewarded + toast localizzati |
| `ui/screen/home/AppScaffold.kt` | Inserisce `BottomBannerAd` in `bottomBar` |
| `ui/component/AppDrawer.kt` | Voce Privacy options nel drawer |
| `ui/navigation/AppNavigation.kt` | `areAdsEnabled` letto con `collectAsState()` in ogni lambda composable |
| `app/proguard-rules.pro` | Regole `-keep` per AdMob e UMP |
| `AndroidManifest.xml` | `meta-data` App ID AdMob, permesso `INTERNET` |
| `res/xml/network_security_config.xml` | HTTPS consentito, cleartext bloccato |