# Navigazione e Cache — WhatStat Reborn

## 1. Struttura della Navigazione

L'app usa **Jetpack Navigation Compose** con un sistema a tre livelli di grafi annidati.

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

### 1.1 Classi e Costanti

**`Screen`** (sealed class) — definisce le route string di ogni schermata:

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

---

## 2. Flusso di Avvio

Il `NavHost` parte sempre da `NavGraphRoute.Setup` (`setup_graph`). Al primo avvio, `SetupScreen` controlla se esiste già un DB valido:

- **Modalità Root** → `state.isRootReady = true` → auto-naviga a `root_mode_graph`
- **Modalità Lite** → `state.hasLiteDb = true` → auto-naviga a `lite_mode_graph`
- **Primo avvio** → nessun DB trovato → l'utente sceglie tra tab Root e tab Lite

L'auto-navigazione avviene solo se `autoNavigate = true` (vedi §4).

---

## 3. Gestione dell'Intent di Condivisione da WhatsApp

Quando l'utente condivide un file ZIP direttamente da WhatsApp, l'app riceve un `Intent` con `ACTION_SEND`. Il flusso è:

### 3.1 Ricezione dell'Intent

**`MainActivity`** intercetta l'intent in `onCreate()` e `onNewIntent()` chiamando `handleIncomingIntent()`.

```
Intent ACTION_SEND
    → handleIncomingIntent()
    → uri validata (tipo ZIP)
    → guard anti-duplicati (stesso URI entro 8s)
    → mainViewModel.setIncomingZipUri(uri)
    → intent.action = null  ← pulizia per evitare re-delivery
```

Il `MainViewModel` espone l'URI via `incomingZipUri: StateFlow<String?>`.

### 3.2 Intercettazione in AppNavigation

In `AppNavigation`, un `LaunchedEffect(Unit)` osserva il flow dell'URI. Se arriva un URI mentre non siamo su Setup, naviga direttamente a Setup con `autoNavigate = true`:

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

> **Nota:** Non si usa `popBackStack(NavGraphRoute.Setup)` perché `setup_graph` potrebbe non essere nel backstack come destinazione navigabile (ad es. quando l'app era in background sulla Dashboard). `navigate()` garantisce sempre l'arrivo su Setup.

### 3.3 Elaborazione in SetupScreen

`SetupScreen` riceve `incomingZipUri` come parametro reattivo (tramite `collectAsState()` dentro il composable della route). Quando l'URI cambia:

```
LaunchedEffect(incomingZipUri)
    → URI != null && URI != processedUri (guard anti-duplicati)
    → processedUri = incomingZipUri
    → awaitLiteImport = true
    → shouldAutoNavigate = true
    → hasNavigated = false
    → liteViewModel.onZipSelected(uri)
    → onUriConsumed() → mainViewModel.setIncomingZipUri(null)
```

### 3.4 Completamento dell'Import

Quando `liteState.isComplete = true` con `awaitLiteImport = true`:

```
LaunchedEffect(liteState.isComplete, awaitLiteImport)
    → liteViewModel.consumeImportComplete()
    → awaitLiteImport = false
    → hasNavigated = true
    → onSetupComplete()
        → navController.navigate(lite_mode_graph) {
              popUpTo(setup_graph) { inclusive = false }
          }
```

**Nota:** Setup rimane nello stack (inclusive = false) per permettere il ritorno.

---

## 4. Il Parametro `autoNavigate`

Questo è il meccanismo centrale per distinguere il **primo avvio** dal **ritorno da Dashboard**.

### 4.1 Quando è `true` (default)

- Al primo avvio dell'app
- Quando si condivide da WhatsApp (import nuovo)
- Quando si avvia manualmente un'analisi Root (checkAndStart)
- Quando si importa uno ZIP dal picker manuale

Con `autoNavigate = true`, se `state.isComplete = true`, SetupScreen naviga automaticamente alla Dashboard.

### 4.2 Quando è `false`

Quando l'utente preme **back dalla Dashboard**, entrambe le modalità navigano verso Setup con `autoNavigate = false`:

```kotlin
// Dashboard Lite:
navController.navigate(Screen.Setup.createRoute(autoNavigate = false)) {
    popUpTo("app_graph") { inclusive = false }
    launchSingleTop = true
}

// Dashboard Root:
repository.resetState()
navController.navigate(Screen.Setup.createRoute(autoNavigate = false)) {
    popUpTo("app_graph") { inclusive = false }
    launchSingleTop = true
}
```

Con `autoNavigate = false`, SetupScreen **blocca l'auto-nav**:

```kotlin
if (!shouldAutoNavigate) return@LaunchedEffect
```

In questo modo l'utente vede la schermata Setup con tutti i dati già caricati, può scegliere se:
- Premere "Vai alla dashboard" (navigazione diretta, senza auto-nav)
- Importare una nuova chat ZIP
- Avviare una nuova analisi Root (che riabilita `shouldAutoNavigate = true`)

### 4.3 Riabilitazione automatica di `shouldAutoNavigate`

`shouldAutoNavigate` torna `true` in tre casi:
1. Arriva un nuovo URI da share WhatsApp (`LaunchedEffect(incomingZipUri)`)
2. L'utente preme "Avvia analisi" Root (`checkAndStart()`)
3. L'utente sceglie un file ZIP dal picker (`zipLauncher` callback)

---

## 5. Navigazione tra Sezioni (Bottom Bar)

Le sezioni (Dashboard, Chat, Media, Radar, Calls) sono grafi **paralleli** dello stesso mode graph, non stack l'uno sull'altro. La bottom bar cambia grafo direttamente.

Conseguenza: il pulsante back non è mostrato nella `ChatList` (né in Root né in Lite), perché non esiste un elemento precedente nello stack da cui provenire.

Per le schermate di dettaglio (ChatDetail, WordSearch per chat, ecc.) la navigazione **è** su stack e il back funziona con `navController.popBackStack()`.

### Protezione back button fisico (`DoubleBackToExitHandler`)

**File**: `ui/component/DoubleBackToExitHandler.kt`

Le schermate tab di primo livello (ChatList, MediaStats, WordSearch, CallsStats) non hanno una destinazione precedente nel navigation stack. Senza protezione, il back button fisico chiuderebbe l'app.

Il composable `DoubleBackToExitHandler` intercetta il back fisico con `BackHandler`:
- Prima pressione: mostra `Toast` "Premi di nuovo per uscire"
- Seconda pressione entro 2 secondi: `Activity.finish()`
- Dopo 2 secondi: il contatore si resetta

Applicato in `AppNavigation.kt` a tutte le composable tab-level di entrambi i grafi (Root e Lite). **Non applicato** a:
- Dashboard (ha il suo `BackHandler` → Setup)
- Schermate di dettaglio in `sharedChatDetailRoutes` (usano `popBackStack()`)

---

## 6. Route condivise (sharedChatDetailRoutes)

Le route di dettaglio chat sono definite una volta sola nella funzione `sharedChatDetailRoutes()` e registrate in entrambi i grafi (Root e Lite):

- `chat_detail/{chatId}`
- `word_search/{chatId}`
- `calls_stats/{chatId}`
- `media_stats/{chatId}`
- `group_history/{chatId}`

Il back da queste schermate usa sempre `navController.popBackStack()`.

---

## 7. Aggiunta Chat in Lite (`LiteSetupAdd`)

Disponibile solo in Lite mode dalla ChatList. Apre `AddChatZipScreen` che lancia automaticamente il file picker (`OpenDocument`). Al completamento, `navController.popBackStack()` torna alla ChatList. Il ViewModel (`LiteSetupViewModel`) notifica il completamento tramite `state.isComplete`.

---

## 8. Sistema di Cache

### 8.1 Architettura

La cache usa **DataStore Preferences** (Jetpack) con due namespace separati:

| Namespace | DataStore name |
|-----------|---------------|
| Root | `stats_cache_root` |
| Lite | `stats_cache_lite` |

Il `CacheManager` seleziona il DataStore corretto in base a `ActiveDatabaseProvider.isLiteMode`:

```kotlin
private val dataStore: DataStore<Preferences>
    get() = if (activeDb.isLiteMode) context.liteCacheDataStore else context.rootCacheDataStore
```

### 8.2 Tipi di Cache

| Chiave | Contenuto |
|--------|-----------|
| `global_stats_v3` | `GlobalStats` (quick stats: conteggi, distribuzioni orarie/settimanali/mensili) |
| `global_slow_v4` | Slow stats (top parole, emoji, sticker) |
| `chat_quick_{id}` | Stats rapide per singola chat |
| `chat_slow_{id}` | Stats lente per singola chat |

La cache quick è usata solo per `TimeRange.ALL_TIME`. Filtri temporali personalizzati bypassano sempre la cache.

### 8.3 Invalidazione Cache

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
   Chiamato da `ChatListViewModel.deleteChat()` quando dopo la cancellazione restano ancora altre chat. Se viene eliminata l'ultima chat, viene invece chiamato `fullResetLiteMode()` che esegue un reset completo.

3. **Reset sessione Root** (`AnalyticsRepository.resetState()`): cancella la cache Root quando si torna a Setup dalla Dashboard Root.

4. **DB Root modificato** (rilevato da hash SHA-256): `CacheManager` confronta l'hash del `msgstore.db` corrente con quello salvato e invalida automaticamente se diverso.

### 8.4 Refresh Lite nel DashboardViewModel

Il `DashboardViewModel` usa `liteCacheVersion` (un `StateFlow<Long>` di timestamp) per sapere quando ricaricare. Al `init`:

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

Poiché `notifyLiteImportDone()` chiama già `clearCache()`, il primo `loadStats(forceRefresh = false)` non troverà cache e ricalcolerà comunque. Al rientro successivo (senza nuovi import), la cache è presente e viene usata.

---

## 9. Diagramma dei Flussi Principali

### Avvio normale (app chiusa, nessun share)

```
MainActivity.onCreate
    → SetupScreen (autoNavigate=true)
    → isRootReady || hasLiteDb?
        → sì: auto-naviga al mode graph corrispondente
        → no: l'utente sceglie manualmente
```

### Share da WhatsApp (app chiusa)

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

### Back dalla Dashboard Lite

```
DashboardScreen.onNavigateBack()
    → mainViewModel.setIncomingZipUri(null)  ← pulizia URI
    → navigate(Setup, autoNavigate=false) {
          popUpTo("app_graph") { inclusive = false }
      }
    → SetupScreen (autoNavigate=false)
    → shouldAutoNavigate = false → NAV-CHECK bloccato
    → l'utente vede Setup con hasLiteDb=true
    → può premere "Vai alla dashboard" o condividere un nuovo ZIP
```

### Nuovo share su Setup con autoNavigate=false

```
Intent → mainViewModel.setIncomingZipUri(nuovoUri)
    → SetupScreen: LaunchedEffect(incomingZipUri) scatta (URI reattivo)
    → shouldAutoNavigate = true  ← riabilitato
    → liteViewModel.onZipSelected(nuovoUri)
    → import → completamento → naviga a lite_mode_graph
    → Dashboard Lite aggiornata
```
