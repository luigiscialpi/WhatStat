# Piano avanzato: separazione pulita Root / Lite

Data: 2026-05-08  
Branch analizzato: `lite-mode` (`4652dcad`)  
Baseline: `master` (`7f8b8580`)  
Diff baseline: `50 files changed, 4994 insertions(+), 668 deletions(-)`

## Obiettivo

Separare alla base le due modalita dell'app, Root e Lite, in modo che non possano interferire tra loro in navigazione, database, cache, media resolver e lifecycle dei ViewModel.

La UI analitica deve preservare l'esperienza visuale gia sviluppata per ciascuna modalita. Questo significa: le parti Lite che oggi sono diverse dalla Root devono rimanere diverse, se sono volute; non bisogna "appiattire" Lite su Root ne Root su Lite. La separazione da fare e architetturale: sorgente dati, import, storage, risoluzione file, cache, capability e navigazione non devono interferire e non devono dipendere da un booleano `isLiteMode` propagato ovunque.

## Risultato della verifica contro `master`

`master` contiene solo la modalita Root. Il branch `lite-mode` ha introdotto Lite in modo incrementale, ma oggi la separazione non e ancora netta: alcune parti sono buone da salvare, altre sono innestate nel percorso Root e rischiano regressioni o navigazione instabile.

### Modifiche aggiunte per Lite

| Area | File principali | Cosa e cambiato |
|---|---|---|
| Import ZIP | `AndroidManifest.xml`, `MainActivity.kt`, `service/LiteImportService.kt` | Intent `ACTION_SEND`, `singleTask`, foreground service per import ZIP, deduplica URI. |
| DB Lite | `data/lite/LiteDatabase.kt`, `LiteDbWriter.kt` | DB SQLite interno `lite_msgstore.db` e `lite_wa.db`, schema compatibile con parte dei DAO Root, upsert chat, scrittura messaggi/media. |
| Parser Lite | `TextChatParser.kt`, `LiteMediaManager.kt`, `FileExtractor.kt` | Parsing export `.txt`, estrazione ZIP in `filesDir/lite_media`, associazione media/messaggi. |
| Switch DB | `DatabaseProvider.kt`, `ActiveDatabaseProvider.kt`, `WhatsAppDatabase.kt`, `AppModule.kt` | I DAO ricevono un provider attivo che sceglie Root o Lite tramite SharedPreferences. |
| Repository | `AnalyticsRepository.kt`, `CacheManager.kt` | Stato `isLiteMode`, `liteCacheVersion`, reset cache, branch per limiti, media path e refresh. |
| DAO | `ChatDao.kt`, `MessageDao.kt`, `ContactDao.kt` | Query rese piu tolleranti al DB Lite, stub, limiti diversi e branch in `ChatDao`. |
| Navigazione | `AppNavigation.kt`, `BottomNavigationBar.kt`, `AppScaffold.kt`, `MainViewModel.kt` | Graph Setup/Main unico con branch Lite, route `lite_setup_add`, bottom bar filtrata in Lite. |
| UI | `SetupScreen.kt`, `LiteSetupViewModel.kt`, `DashboardScreen.kt`, `ChatListScreen.kt`, `ChatDetailScreen.kt`, `MediaStatsScreen.kt`, `WordSearchViewModel.kt` | Tab Lite/Root nel setup, import aggiuntivo, delete chat Lite, badge/label/tab/FAB condizionali. |
| Modelli | `Stats.kt`, `QueryModels.kt`, `EmojiStat.kt`, `GroupHistoryEntities.kt` | Campi/serializzazione per cache e Lite, incluso `GlobalStats.isLiteMode`. |
| Debug/release | `App.kt`, `DebugLogManager.kt`, `build.gradle.kts`, `.gitignore` | Debug uncaught exceptions e signing release: utili, ma non devono restare mischiati alla separazione Lite. |
| Docs | `LiteMode_Development_Plan.md`, `Lite_DB_Migration_Plan.md`, `WhatStat_Explained.md` | Documentazione precedente della migrazione Lite. |

### Problema principale

La Lite e stata integrata come "modalita attiva globale" invece che come contesto applicativo separato. Il risultato e un singleton condiviso che cambia comportamento in base a `isLiteMode`.

Punti critici trovati:

- `ActiveDatabaseProvider` usa `SharedPreferences("is_lite_mode")` e decide a runtime quale DB restituire.
- `AnalyticsRepository` contiene stato di modalita, reset Lite, versioning Lite, limiti diversi e media path diversi.
- Diversi ViewModel osservano `liteCacheVersion` per ricaricarsi.
- `ChatDao` controlla se il provider e `ActiveDatabaseProvider` per scegliere una query diversa.
- La UI riceve `isLiteMode` e cambia layout, tab, badge, FAB, back button e sticker handling.
- `GlobalStats` contiene `isLiteMode`, quindi un dettaglio infrastrutturale entra nel dominio.
- Il Graph di navigazione e unico e contiene route Lite speciali dentro il main graph.

La direzione "DB Lite compatibile coi DAO Root" e corretta, ma il dispatch tramite booleano globale e la parte fragile.

## Decisione architetturale

Creare una separazione a contesti:

- `RootSession`: sessione basata su database WhatsApp decifrati/estratti.
- `LiteSession`: sessione basata su ZIP importati e DB SQLite interno.
- `Analytics UI`: schermate e componenti riusati dove conviene, ma con le varianti visuali Root/Lite gia sviluppate preservate e dichiarate in un contratto esplicito.
- `Setup/import`: flussi separati, perche Root e Lite hanno onboarding e requisiti diversi.

Quindi: si, conviene creare una sezione Lite dedicata per import, storage e sessione. No, non conviene duplicare tutta Dashboard, ChatList, ChatDetail, Media, Radar e componenti grafici solo per cambiare la sorgente dati. Conviene invece separare shell/contratti/configurazioni: la vista Lite puo continuare ad avere badge, tab, azioni e testi Lite-specific, ma questi devono arrivare da una `PresentationSpec` o da wrapper dedicati, non da `if (isLiteMode)` sparsi nei composable.

Room: non conviene introdurlo adesso solo per Lite. L'app legge gia database SQLite esterni con query custom complesse; usare Room solo per Lite creerebbe due stack dati diversi. Meglio mantenere `lite_msgstore.db` raw SQLite con schema compatibile e, se serve piu avanti, usare Room solo per metadati Lite non interrogati dagli analytics DAO.

## Architettura target

```
Setup / Intent Router
        |
        v
ModeSessionStore
        |   (activeSessionId seleziona la vista corrente, non cancella le altre)
        |
        +-- RootSession(sessionId, RootDatabaseProvider, RootMediaResolver, RootCacheStore)
        |
        +-- LiteSession(sessionId, LiteDatabaseProvider, LiteMediaResolver, LiteCacheStore)
                    |
                    +-- LiteImporter: ZIP -> Parser -> Writer -> lite_msgstore.db

AnalyticsRepository(session)
        |
        +-- ChatDao(session.databaseProvider)
        +-- MessageDao(session.databaseProvider)
        +-- ContactDao(session.databaseProvider)
        +-- MediaResolver(session)
        +-- CacheStore(session)

AnalyticsGraph(sessionId)
        |
        +-- DashboardScreen
        +-- ChatListScreen
        +-- ChatDetailScreen
        +-- MediaStatsScreen
        +-- WordSearchScreen
        +-- CallsStatsScreen
```

Regola: la modalita viene scelta quando nasce una sessione. Dopo, i ViewModel ricevono una sessione concreta, non un flag. Cambiare modalita non deve chiudere o sporcare l'altra sessione: deve solo cambiare la sessione attiva nella UI. RootSession e LiteSession possono coesistere, con DB/cache/media/back stack logico separati. La chiusura o cancellazione di una sessione avviene solo con un'azione esplicita dell'utente.

## Componenti da introdurre

### 1. Session model

Nuovo package consigliato: `data/session`.

```kotlin
enum class AppMode { ROOT, LITE }

data class SessionId(val value: String)

interface AppSession {
    val id: SessionId
    val mode: AppMode
    val databaseProvider: DatabaseProvider
    val mediaResolver: MediaResolver
    val cacheStore: CacheStore
    val navigationSpec: NavigationSpec
}
```

Il tipo `AppMode` puo esistere, ma deve rimanere ai confini: setup, session factory, intent router. Non deve comparire nelle schermate analitiche, nei DAO o nei modelli di statistiche.

### 2. Provider DB non mutabili

Rimuovere `ActiveDatabaseProvider`.

Creare:

- `RootDatabaseProvider(WhatsAppDatabase) : DatabaseProvider`
- `LiteDatabaseProvider(LiteDatabase) : DatabaseProvider`
- `AnalyticsRepositoryFactory.create(sessionId)`
- `DaoFactory.create(provider)`

I DAO non devono essere singleton globali se il loro provider dipende dalla sessione. Devono essere creati per sessione.

### 3. Cache per sessione

Sostituire la cache globale con namespace per sessione.

Esempio:

```
cache/
  root/<root-db-fingerprint>/
    global_stats.json
    word_index_global.gz
    word_index_chat_<id>.gz

  lite/<lite-db-id>/
    global_stats.json
    word_index_global.gz
    word_index_chat_<id>.gz
```

Il `liteCacheVersion` va rimosso. Al suo posto:

- `SessionRevisionStore` per invalidare la sessione corrente.
- oppure ricreazione del `sessionId` quando l'import cambia il DB.

La regola importante e che una cache Root non puo mai essere letta da Lite, e viceversa.

### 4. Media resolver

Spostare la differenza Root/Lite fuori da `AnalyticsRepository.getMediaFiles()`.

```kotlin
interface MediaResolver {
    suspend fun resolve(stat: MediaStat): File?
    suspend fun buildIndexIfNeeded()
}
```

Implementazioni:

- `RootMediaResolver`: cerca nelle directory WhatsApp reali e gestisce `Sent/`.
- `LiteMediaResolver`: risolve da `filesDir/lite_media`.

`AnalyticsRepository` deve chiedere `mediaResolver.resolve(stat)`, non controllare la modalita.

### 5. Navigation spec

Creare due graph root separati, ma con schermate condivise:

- `RootModeGraph(sessionId)`
- `LiteModeGraph(sessionId)`
- entrambi montano `AnalyticsGraph(sessionId)`

Questo evita che route Lite come `lite_setup_add` vivano dentro il graph principale condiviso. Per il refresh dati:

- Root: azione "nuova analisi" / setup Root.
- Lite: azione "importa ZIP".

La UI analitica mantiene le differenze visuali gia progettate per Root e Lite. Se serve un'azione specifica Lite, come importare un nuovo ZIP dalla lista chat, deve arrivare da uno slot definito da `NavigationSpec` / `PresentationSpec`, non con `if (isLiteMode)` dentro la schermata.

### 6. Mode switcher e coesistenza Root/Lite

Lo scopo del refactor e permettere anche a un utente root di usare la parte Lite e passare liberamente tra le due senza contaminare stato, cache o navigazione.

Serve quindi un `ModeSessionStore` con almeno:

```kotlin
data class ModeSessionState(
    val activeSessionId: SessionId?,
    val rootSession: RootSession?,
    val liteSession: LiteSession?
)
```

Comportamento desiderato:

- Root e Lite possono essere entrambe configurate nello stesso momento.
- Lo switch Root -> Lite seleziona `liteSession` come sessione attiva e apre `LiteModeGraph`.
- Lo switch Lite -> Root seleziona `rootSession` come sessione attiva e apre `RootModeGraph`.
- Lo switch non cancella DB, cache, media, repository o stato salvato dell'altra modalita.
- Ogni modalita ha il proprio namespace cache e il proprio provider DB immutabile.
- L'utente puo cancellare solo i dati Lite o solo la sessione Root con azioni dedicate.

Importante: non deve piu esistere un "DB attivo globale" condiviso da tutti i DAO singleton. Deve esistere una sessione attiva per la UI, mentre i dati delle altre sessioni restano persistiti e isolati.

### 7. Capability senza booleani sparsi

Non usare `isLiteMode` per decidere localmente cosa mostrare o nascondere. Usare una policy centralizzata che descriva esplicitamente la presentazione di Root e Lite.

```kotlin
sealed interface FeatureAvailability {
    data object Available : FeatureAvailability
    data class Empty(val reason: EmptyReason) : FeatureAvailability
}

data class NavigationSpec(
    val tabs: List<AnalyticsTab>,
    val actions: List<AnalyticsAction>
)

data class PresentationSpec(
    val modeBadge: ModeBadge?,
    val showImportZipAction: Boolean,
    val showCallsTab: Boolean,
    val showStickerAnalysis: Boolean,
    val showImportTimestampInChatList: Boolean,
    val mediaGrouping: MediaGrouping
)
```

Questa policy deve riflettere la UI attuale desiderata. Se oggi Lite nasconde le chiamate, mostra un FAB per aggiungere ZIP, separa gli sticker nella galleria o mostra metadati di import, quelle scelte restano. Cambia solo il punto in cui vengono decise.

## Piano di migrazione

### Regola operativa - Sviluppo a checkpoint

Il refactor non deve essere implementato in un unico blocco grande. Ogni fase deve essere trattata come uno step verificabile e comunicabile.

Regole operative:

- procedere per step piccoli, idealmente allineati alle fasi e ai commit consigliati;
- prima di iniziare uno step, dichiarare cosa verra toccato e quale risultato ci si aspetta;
- alla fine di ogni step, fornire un aggiornamento sintetico con:
  - file modificati;
  - cosa e stato completato;
  - cosa resta da fare;
  - eventuali rischi o decisioni aperte;
  - test/check eseguiti o non eseguiti;
- se durante uno step emerge una scelta architetturale ambigua, fermarsi e chiedere chiarimento prima di procedere;
- non interrompere l'utente per dubbi minori o decisioni locali: fare una scelta ragionevole, annotarla nell'aggiornamento di fine step e proseguire;
- chiedere conferma solo quando la scelta cambia architettura, UX attesa, dati persistenti, compatibilita Root/Lite o rischia di rendere costoso tornare indietro;
- non mischiare nello stesso step modifiche infrastrutturali, UI, cache, DB e documentazione se possono essere separate;
- mantenere `docs/Lite_Mode_Explained.md` aggiornato insieme agli step che cambiano il comportamento Lite;
- evitare refactor "a cascata" non necessari: ogni step deve avere uno scopo leggibile e reversibile.

Obiettivo pratico: una futura chat deve poter leggere gli aggiornamenti step-by-step e capire dove il refactor e arrivato, senza dover dedurre tutto da un diff enorme.

### Fase 0 - Congelare l'audit

1. Salvare questo piano.
2. Creare un branch nuovo da `master`, non continuare chirurgicamente sul branch `lite-mode`.
3. Usare `lite-mode` come sorgente da cui recuperare pezzi selezionati.
4. Separare i commit non-Lite:
   - signing release in `build.gradle.kts`;
   - `DebugLogManager`;
   - fix `SetupService.stopForeground`;
   - ottimizzazione lessicale streaming.

### Fase 0.1 - Documentazione viva della Lite

Creare un file dedicato in `docs`, in italiano, con stile simile a `WhatStat_Explained.md`.

Nome consigliato:

```text
docs/Lite_Mode_Explained.md
```

Questo file deve essere aggiornato durante il refactor, non scritto solo alla fine. Deve servire come contesto tecnico-funzionale per una futura chat o per un futuro sviluppatore che riprende la parte Lite.

Contenuti minimi:

- cosa risolve la modalita Lite e in cosa differisce dalla Root;
- flusso utente: import ZIP, scelta mittente, import aggiuntivi, cancellazione chat;
- formato dati in ingresso: export WhatsApp `.zip`, `.txt`, media allegati;
- pipeline tecnica: `Intent` / picker -> estrazione ZIP -> parser -> media manager -> writer SQLite;
- struttura dei file Lite: DB, media importati, cache, metadati;
- schema ad alto livello di `lite_msgstore.db` e differenze inevitabili rispetto a `msgstore.db`;
- come vengono gestiti chat multiple, reimport, owner/from_me, media e sticker;
- come la Lite viene separata dalla Root tramite sessioni, provider DB, cache e media resolver;
- quali schermate/varianti UI Lite esistono e perche;
- limiti noti della Lite: dati non disponibili nell'export, chiamate, contatti reali, JID, eventi non presenti nel `.txt`;
- checklist per aggiungere future feature Lite senza contaminare Root.

Regola pratica: ogni fase che modifica comportamento Lite, schema Lite, import, cache, media, navigazione o UI Lite deve aggiornare anche `Lite_Mode_Explained.md` nello stesso ciclo di lavoro.

### Fase 1 - Portare solo il dominio Lite isolato

Recuperare e adattare:

- `data/lite/TextChatParser.kt`
- `data/lite/LiteMediaManager.kt`
- `data/lite/LiteDatabase.kt`
- `data/lite/LiteDbWriter.kt`
- `data/lite/LiteModeManager.kt`, rinominato in `LiteImportManager`
- `service/LiteImportService.kt`
- `FileExtractor.extractFullChatFolder()`

Non recuperare:

- `ActiveDatabaseProvider`
- `AnalyticsRepository.isLiteMode`
- `GlobalStats.isLiteMode`
- branch UI con `isLiteMode`
- `liteCacheVersion`

### Fase 2 - Rendere il DB Lite compatibile senza branch DAO

Obiettivo: i DAO fanno la stessa query su Root e Lite.

Azioni:

1. Completare lo schema Lite con tutte le colonne/tabelle che i DAO leggono.
2. Se una tabella Root non esiste in Lite, creare stub vuoto coerente.
3. Evitare colonne Lite-only nelle query comuni (`import_timestamp` non deve entrare in `ChatDao.getChatList()`).
4. Spostare metadati Lite in tabella separata:

```sql
CREATE TABLE lite_chat_metadata (
    chat_row_id INTEGER PRIMARY KEY,
    import_key TEXT UNIQUE,
    import_timestamp INTEGER,
    source_display_name TEXT,
    source_hash TEXT
);
```

5. Usare `chat._id` reali anche in Lite. Niente sentinel come `chatId = -1L`.

### Fase 3 - Repository per sessione

Creare un repository analytics istanziato per sessione:

```kotlin
class AnalyticsRepository(
    private val session: AppSession,
    private val messageDao: MessageDao,
    private val chatDao: ChatDao,
    private val contactDao: ContactDao
)
```

Da rimuovere da `AnalyticsRepository`:

- `setLiteMode`
- `resetLiteMode`
- `fullResetLiteMode`
- `notifyLiteImportDone`
- `liteCacheVersion`
- tutti i controlli `activeDatabaseProvider.isLiteMode`
- risoluzione path media specifica Lite/Root

Limiti come `20` o `50` non devono dipendere dalla modalita. Devono essere preferenze UI o parametri espliciti.

### Fase 4 - Navigation pulita

Introdurre:

- `SetupGraph`
- `RootSetupGraph`
- `LiteImportGraph`
- `RootModeGraph(sessionId)`
- `LiteModeGraph(sessionId)`
- `AnalyticsGraph(sessionId)` condiviso

`MainActivity` deve limitarsi a raccogliere intent ZIP e passarli a un `ImportIntentRouter`.

Regole:

- Se non esiste sessione e arriva ZIP: aprire Setup Lite/import.
- Se esiste sessione Lite e arriva ZIP: aprire import Lite nella sessione Lite.
- Se esiste sessione Root e arriva ZIP: aprire il flusso di import Lite senza toccare la RootSession. A fine import, l'utente deve poter scegliere se passare subito alla LiteSession o tornare alla RootSession. In nessun caso l'arrivo dello ZIP deve sostituire il provider DB della sessione Root o invalidarne cache/navigazione.
- Se esistono gia RootSession e LiteSession: mostrare un selettore modalita persistente (es. nella Setup/home shell o in un menu top-level) per passare liberamente da una all'altra.

### Fase 5 - UI con varianti dichiarate

Obiettivo: preservare le differenze visuali Lite gia sviluppate, ma renderle dichiarative e non dipendenti da booleani sparsi.

Mantenere, se sono ancora desiderate nel prodotto:

- badge "MODALITA LITE";
- bottom bar filtrata da `isLiteMode`;
- tab sticker/chiamate nascoste via `if (isLiteMode)`;
- label extra "Esportata il" visibile solo in Lite;
- FAB Lite hardcoded in `ChatListScreen`;
- separazione sticker/galleria media in Lite;
- path sticker/media Lite risolti dai file importati.

Ma spostare queste decisioni in:

- `RootPresentationSpec`;
- `LitePresentationSpec`;
- slot composable opzionali (`primaryAction`, `secondaryHeaderContent`, `mediaTabsSpec`);
- `MediaResolver` per i path file;
- `NavigationSpec` per tab e route disponibili.

Sostituire con:

- niente `isLiteMode` nei componenti di basso livello;
- niente `isLiteMode` nei domain model;
- niente reset DB/navigation in un back button generico;
- niente query o media path diversi dentro `AnalyticsRepository`;
- differenze visuali esplicite, testabili e centralizzate.

### Fase 6 - Setup separato

Tenere Setup come punto di scelta, ma separare i ViewModel:

- `RootSetupViewModel`: root, manual file, session restore root.
- `LiteImportViewModel`: ZIP, sender selection, import progress.
- `SetupViewModel`: solo coordinamento della scelta iniziale e dello switch tra sessioni gia esistenti.

`SetupViewModel` non deve osservare `analyticsRepository.isLiteMode`.

Quando entrambe le sessioni esistono, la UI di ingresso non deve chiedere di "rifare setup"; deve mostrare accesso rapido a Root e Lite, piu le azioni dedicate:

- aggiorna/ricrea sessione Root;
- importa nuova chat Lite;
- cancella dati Lite;
- cancella sessione Root.

### Fase 7 - Gestione dati Lite

Struttura consigliata:

```
files/
  root/
    current/
      msgstore.db
      wa.db
      avatars/

  lite/
    lite_msgstore.db
    lite_wa.db
    media/
      <import_key_or_chat_id>/
```

Per reimport:

1. Calcolare `import_key` stabile.
2. Aprire transazione.
3. Cancellare messaggi/media della chat gia esistente.
4. Inserire nuovi messaggi/media.
5. Aggiornare `lite_chat_metadata`.
6. Commit.
7. Solo dopo il commit cancellare vecchie cartelle media.

Per delete:

1. Cancellare solo la chat Lite richiesta.
2. Se non restano chat, chiudere la sessione Lite e rimuovere DB/media/cache Lite.
3. Non toccare mai dati Root.

### Fase 8 - Test minimi obbligatori

Non risultano directory `app/src/test` o `app/src/androidTest` nel branch analizzato. Prima della migrazione finale vanno introdotti almeno:

1. Test parser export WhatsApp:
   - data italiana `dd/MM/yy`;
   - messaggi multilinea;
   - media omesso/allegato;
   - eventi gruppo.

2. Test DB Lite:
   - crea DB Lite;
   - importa 2 chat;
   - reimport stessa chat senza duplicati;
   - delete chat;
   - query `ChatDao`, `MessageDao`, `ContactDao` senza crash.

3. Test cache:
   - Root e Lite con stesso `chatId` numerico non condividono file cache;
   - nuova import Lite invalida solo cache Lite.

4. Test repository:
   - nessun metodo analytics cambia comportamento root rispetto a `master`;
   - chiamate su Lite restituiscono vuoto, non crash.

5. Test navigazione:
   - Root setup -> main -> back/refresh non apre Lite;
   - Lite import -> main -> import aggiuntivo non apre Root;
   - intent ZIP durante Root apre import Lite senza mutare RootSession;
   - dopo import ZIP da Root, l'utente puo scegliere se andare alla LiteSession o tornare alla RootSession;
   - switch Root/Lite ripetuto non condivide cache, ViewModel o provider DB.

## Regole di accettazione

- `rg "isLiteMode|liteCacheVersion|ActiveDatabaseProvider" app/src/main/java` non deve trovare occorrenze fuori da eventuali migrazioni temporanee.
- I DAO non devono conoscere `Root`, `Lite`, `AppMode` o `ActiveDatabaseProvider`.
- I modelli dominio (`GlobalStats`, `ChatStats`, `MediaStat`) non devono contenere la modalita.
- `AnalyticsRepository` non deve contenere branch Root/Lite.
- La bottom navigation deve essere definita da `NavigationSpec`, non da booleani nei composable.
- Le cache devono essere fisicamente separate per sessione.
- RootSession e LiteSession possono coesistere e lo switch tra loro non cancella ne ricrea i dati dell'altra modalita.
- Root deve funzionare anche se `lite_msgstore.db` non esiste.
- Lite deve funzionare anche se i database Root non sono mai stati aperti.
- Nessun ViewModel deve osservare un bus globale Lite per ricaricare dati Root.
- Le schermate analytics devono preservare le differenze visuali attuali di Root e Lite; la separazione richiesta e tecnica/architetturale, non un allineamento grafico forzato.

## Cosa salvare, riscrivere o scartare dal branch Lite

| Stato | Elementi |
|---|---|
| Salvare quasi integralmente | `TextChatParser`, `LiteMediaManager`, logica base `LiteDatabase`, logica base `LiteDbWriter`, `LiteImportService`, intent ZIP, estrazione ZIP persistente. |
| Salvare ma spostare | `CacheManager.clearCache(resetWordIndexes)`, streaming lessicale in `MessageDao`, stub DB Lite, import progress. |
| Riscrivere | `ActiveDatabaseProvider`, `AnalyticsRepository` stato Lite, `MainViewModel`, `AppNavigation`, `SetupViewModel`, refresh via `liteCacheVersion`. |
| Preservare ma centralizzare nella UI analytics | badge Lite, tab nascoste/mostrate, label import timestamp, FAB import ZIP, separazione sticker/media, testi Lite-specific. Devono vivere in `PresentationSpec`/slot dedicati, non in booleani sparsi. |
| Tenere separati da questa migrazione | signing release, debug uncaught exception logger, doc update generici, `android-sdk/.knownPackages`. |

## Sequenza consigliata dei commit

1. `docs: add dual mode separation plan`
2. `docs(lite): add lite mode explained`
3. `refactor(session): introduce app session contracts`
4. `refactor(db): replace active database provider with session providers`
5. `feat(lite): add isolated lite import pipeline`
6. `feat(lite-db): add root-compatible lite sqlite schema`
7. `refactor(analytics): scope repository and cache by session`
8. `refactor(nav): split root and lite mode graphs`
9. `refactor(ui): centralize root/lite presentation variants`
10. `test(lite): cover parser database and import flows`
11. `test(root): protect root analytics parity with master`

## Conclusione

La base migliore non e duplicare tutta l'app Lite, ma creare due modalita come sessioni indipendenti e poi farle convergere su componenti analytics comuni con varianti visuali dichiarate. In questo modo Root e Lite hanno database, cache, media, import e navigazione separati; la Lite mantiene le parti visuali gia sviluppate; e il codice smette di dipendere da un booleano globale propagato ovunque.
