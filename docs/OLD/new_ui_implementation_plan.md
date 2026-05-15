# Piano di Refactoring UX/UI: Bottom Navigation

Questo piano, aggiornato in base alle linee guida di `docs/refactor_plan_b.md` e di `docs/refactor_ui.md`, descrive l'implementazione del refactoring della navigazione e la standardizzazione della UX per WhatStat. L'obiettivo è passare da una navigazione "piatta" a una basata su un **AppScaffold** con `BottomNavigationBar` a stack indipendenti, uniformando parallelamente gli stati dell'interfaccia.

## Decisioni Architetturali

Le seguenti scelte sono vincolanti per l'intera implementazione e risolvono le ambiguità dei documenti sorgente.

| Decisione | Scelta | Motivazione |
|---|---|---|
| Dove vivono le schermate chat-specific (con `chatId`) | Push nello stack della tab **Chats** (es. `ChatDetail → MediaStats(chatId)` resta dentro `ChatsGraph`) | Il back torna a `ChatDetail`; l'utente non perde contesto. Le tab globali mantengono il proprio stato indipendente. |
| Route duplicate per composable con/senza `chatId` | Stesso composable, **due route**: una nel grafo della tab root (senza `chatId`, globale) e una nel `ChatsGraph` (con `chatId`, chat-specific) | Evita navigazione cross-tab che romperebbe lo stack e confonderebbe l'utente. |
| Meccanismo Setup → Main | **Singolo `NavHost`** con bottom bar mostrata/nascosta in base alla route corrente. Dopo il completamento del setup: `navController.navigate(mainGraphRoute) { popUpTo(Screen.Setup) { inclusive = true } }`. La `BottomNavigationBar` controlla `currentBackStackEntry` e si nasconde se la route appartiene al flusso Setup. | Approccio standard Navigation Compose; evita la complessità di NavHost multipli. |
| ActionCard in `ChatDetailScreen` | Rimuovere **solo** "Tutte le Chat" (l'ultimo pulsante, che oggi funge impropriamente da back). **Mantenere** Radar Parole, Log Chiamate e Media & Link — servono per la navigazione chat-specific con `chatId`. | Senza questi 3 pulsanti l'utente non avrebbe modo di accedere a media/chiamate/radar filtrati sulla singola chat. |
| Gestione filtri tra tab | `FilterStateHolder` resta invariato (contesto `global` + contesto `chat`). Il `saveState/restoreState` di Navigation Compose preserva lo stato UI dei composable, mentre `FilterStateHolder` preserva lo stato dei filtri in modo indipendente. Nessuna interferenza. |
| Header contestuale nelle schermate chat-specific | Le schermate pushate dal `ChatDetail` con `chatId` (Radar, Media, Chiamate) mostrano un **header arricchito** con nome contatto + avatar (es. "Radar Parole · Mario Rossi") e la **freccia ← di back**. Le stesse schermate in versione globale (root di tab) mostrano solo il titolo senza freccia. | Disambigua visivamente la vista chat-specific da quella globale: l'utente capisce di essere "dentro" la tab Chat anche se lo schermo mostra un Radar/Media/Chiamate. Evita la confusione con la tab omonima nella bottom bar. |

---

## Proposed Changes

---

### UI / Componenti di Base

#### [NEW] `com/whatstat/ui/navigation/BottomNavigationBar.kt`

- Creazione della Bottom Navigation Bar con le 5 tab: Panoramica, Chat, Media, Radar, Chiamate (usando Material 3 `NavigationBar`).
- Logica di selezione reattiva basata sulla route di `currentBackStackEntry`.

#### [NEW] `com/whatstat/ui/screen/home/AppScaffold.kt`

- Nuovo entry point principale. `MainActivity` delega a `AppScaffold` che wrappa il `NavHost` esistente.
- Integra lo `Scaffold` con all'interno la `BottomNavigationBar`.
- Visibilità della bottom bar: osserva `navController.currentBackStackEntryAsState()` e nasconde la barra quando la route corrente è `Screen.Setup`. Non servono NavHost multipli.
- Pseudo-codice:
  ```kotlin
  val navBackStackEntry by navController.currentBackStackEntryAsState()
  val showBottomBar = navBackStackEntry?.destination?.route?.startsWith("setup") != true
  Scaffold(
      bottomBar = { if (showBottomBar) BottomNavigationBar(navController) }
  ) { ... NavHost(...) }
  ```

---

### Ristrutturazione del NavHost

#### [MODIFY] `com/whatstat/ui/navigation/AppNavigation.kt`

- Riorganizzazione in macro-fasi: `Setup` e `Main` (tabbed shell).
- Definizione di grafi top-level individuali per ciascuna tab (`OverviewGraph`, `ChatsGraph` ecc.).
- Routing per il tab `Chats`: `ChatList` -> `ChatDetail`.
- Risolto: **Transizioni**: Le transizioni di slide orizzontale resteranno solo per push/pop in profondità (es. List -> Detail), mentre il cambio di tab avrà transizioni più discrete (fade) per evitare glitch visivi tipici di Navigation Compose.
- Risolto: **Azioni chat-specific**: Le schermate lanciate dal `ChatDetail` con `chatId` (Media, WordSearch, Calls) vengono pushate **nello stack della tab Chats** (`ChatsGraph`), non nelle rispettive tab globali. Questo significa che:
  - `ChatsGraph` conterrà le route: `chat_list`, `chat_detail/{chatId}`, `word_search/{chatId}`, `calls_stats/{chatId}`, `media_stats/{chatId}`.
  - Le tab globali Media, Radar e Chiamate conterranno le route **senza** `chatId` (es. `media_stats`, `word_search`, `calls_stats`).
  - Lo stesso composable (es. `MediaStatsScreen`) viene riusato in entrambi i contesti; ciò che cambia è solo la route e la presenza/assenza del parametro `chatId`.

---

### Header Contestuale per Schermate Chat-Specific

#### [MODIFY] `WordSearchScreen.kt`, `CallsStatsScreen.kt`, `MediaStatsScreen.kt`

- Quando `chatId` è presente, l'header della schermata deve mostrare:
  - **Freccia ← di back** (navigazione a `ChatDetail`).
  - **Titolo arricchito**: "Radar Parole · **{nomeContatto}**" (o Media & Link · ... / Log Chiamate · ...).
  - **Avatar** del contatto accanto al titolo (mini, coerente con lo stile di `ChatDetailScreen`).
- Quando `chatId` è assente (schermata globale, root di tab):
  - **Nessuna freccia** ← (è la root della tab, il back di sistema esce dalla tab).
  - **Titolo semplice**: "Radar Parole" / "Media & Link" / "Log Chiamate".
  - Nessun avatar.
- Questo pattern disambigua visivamente la vista filtrata-per-chat dalla vista globale, anche quando la bottom bar mostra una tab omonima non evidenziata.

---

### Modifiche alle Schermate

#### [MODIFY] `com/whatstat/ui/screen/dashboard/DashboardScreen.kt`

- Rimuovere i 4 pulsanti in fondo ("Lista Chat", "Media & Link", "Radar Parole", "Log Chiamate") e ripulire il padding.

#### [MODIFY] `com/whatstat/ui/screen/chatdetail/ChatDetailScreen.kt`

- Rimuovere **solo** il pulsante "Tutte le Chat" (ultimo `ActionCard` del grid 2×2, quello con icona Group e label "Altre conversazioni") che oggi funge impropriamente da pulsante back.
- **Mantenere** i 3 `ActionCard` rimanenti (Radar Parole, Log Chiamate, Media & Link) perché sono l'unico punto d'accesso alle schermate chat-specific con `chatId`. Riorganizzare il layout da griglia 2×2 a una riga 1×3 o a un layout più adatto a 3 elementi.
- Il ritorno alla `ChatList` avverrà rigorosamente tramite back nativo (freccia in alto o gesture di sistema).

#### [MODIFY] `com/whatstat/ui/screen/media/MediaStatsScreen.kt`

- Introduzione di una UI `TabRow`/`PrimaryTabRow` superiore.
- Verranno create due sotto-tab "Media" e "Link" separate, migliorando la chiarezza ed evitando scroll eccessivi.

---

### Hardening UX & Design System (Novità da Plan B)

#### [MODIFY] Varie Schermate (Design Tokens & Stati UI)

- **Stati UI**: Standardizzare il pattern per `error`, `empty` e `loading` (assicurando che la schermata mostri sempre un messaggio d'errore e un'azione di retry, specialmente nel `ChatDetail`).
- **Coerenza filtri**: Assicurarsi che `TimeRangeFilterRow` sia sempre visibile (magari disabilitato) anche durante i caricamenti, per annullare sbalzi di layout (layout shifts).
- **Design Tokens Minimi**: Sostituzione mirata di dimensioni padding/corner hardcoded (`dp`) con token semantici di base. Modifiche a componenti chiave: `StatCard`, `TimeRangeFilterRow`, `LoadingSection`, `SimpleScreenHeader`.

---

## Verification Plan

### Automated/Manual Verification

#### Flusso Setup → Main
- Avvio `Setup` (bottom bar **non** visibile) → Completamento → Appare `AppScaffold` con Bottom Bar esplorabile.
- Verifica che la route Setup sia rimossa dallo stack (`inclusive = true`) e che il back di sistema esca dall'app, non torni al Setup.

#### Switching tra tab
- Cambio tra tab multiple (es. Chat → Media → Radar) con transizione fade pulita.
- Tap ripetuto sulla tab corrente: deve tornare alla root della tab (es. se sei su `ChatDetail`, tap su tab Chat → torna a `ChatList`).

#### Backstack e restoreState
- Apri `ChatDetail`, passa alla tab Radar, ritorna alla tab Chat → `ChatDetail` deve riapparire nel punto in cui era (scroll position inclusa).
- Apri tab Media (globale), scorri in basso, passa a tab Chiamate, torna a Media → lo scroll deve essere preservato.

#### Back nativo nella tab Chat
- `ChatDetail` → back → `ChatList`.
- `ChatDetail` → `MediaStats(chatId)` → back → `ChatDetail` (non `ChatList`).
- `ChatDetail` → `CallsStats(chatId)` → back → `ChatDetail`.
- `ChatDetail` → `WordSearch(chatId)` → back → `ChatDetail`.

#### Navigazione chat-specific (flusso critico)
- Da `ChatDetail`, tap su "Media & Link" → apre `MediaStats` con dati filtrati per chat. Verificare che la tab attiva nella bottom bar resti **Chats** (non Media).
- Da `ChatDetail`, tap su "Radar Parole" → apre `WordSearch(chatId)`. Verificare che la tab attiva resti **Chats**.
- Da `ChatDetail`, tap su "Log Chiamate" → apre `CallsStats(chatId)`. Verificare che la tab attiva resti **Chats**.

#### Header contestuale (disambiguazione visiva)
- Da `ChatDetail(Mario Rossi)`, tap su "Radar Parole" → l'header deve mostrare "Radar Parole · Mario Rossi" con avatar e freccia ← di back.
- Tap sulla tab "Radar" nella bottom bar → l'header deve mostrare solo "Radar Parole" senza avatar e senza freccia ←.
- Verificare la stessa distinzione su `MediaStatsScreen` e `CallsStatsScreen` (con e senza `chatId`).

#### Filtri temporali (non-interferenza tra contesti)
- Su `ChatDetail` seleziona filtro "Ultimi 7 giorni". Passa alla tab Panoramica e seleziona "Ultimo anno". Torna alla tab Chat → il filtro deve essere ancora "Ultimi 7 giorni" (contesto `chat` indipendente da `global` nel `FilterStateHolder`).
- Su `Dashboard` seleziona filtro "Ultimi 30 giorni". Apri una chat, cambia filtro a "Ultimi 3 mesi". Torna a Dashboard → deve mostrare ancora "Ultimi 30 giorni".

#### UI e Design System
- Controllo integrità visiva dei Design Tokens su componenti globali (`StatCard`, `TimeRangeFilterRow`, `LoadingSection`, `SimpleScreenHeader`).
- Verifica che `TimeRangeFilterRow` appaia (disabilitato) durante il loading su tutte le schermate che lo usano.
- Simulare errore (es. database non accessibile) → ogni schermata deve mostrare messaggio d'errore + azione retry, mai un vuoto bianco.
