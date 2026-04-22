# Piano di Refactoring UX/UI: Bottom Navigation

Questo piano, aggiornato in base alle linee guida di `docs/refactor_plan_b.md` e di `docs/refactor_ui.md`, descrive l'implementazione del refactoring della navigazione e la standardizzazione della UX per WhatStat Reborn. L'obiettivo è passare da una navigazione "piatta" a una basata su un **AppScaffold** con `BottomNavigationBar` a stack indipendenti, uniformando parallelamente gli stati dell'interfaccia.

## Proposed Changes

---

### UI / Componenti di Base

#### [NEW] `com/whatstat/reborn/ui/navigation/BottomNavigationBar.kt`

- Creazione della Bottom Navigation Bar con le 5 tab: Panoramica, Chat, Media, Radar, Chiamate (usando Material 3 `NavigationBar`).
- Logica di selezione reattiva basata sulla route di `currentBackStackEntry`.

#### [NEW] `com/whatstat/reborn/ui/screen/home/AppScaffold.kt`

- Nuovo entry point principale.
- Integra lo `Scaffold` con all'interno la `BottomNavigationBar`.
- Espone una logica per nascondere la barra nelle schermate pre-main (come il `Setup`).

---

### Ristrutturazione del NavHost

#### [MODIFY] `com/whatstat/reborn/ui/navigation/AppNavigation.kt`

- Riorganizzazione in macro-fasi: `Setup` e `Main` (tabbed shell).
- Definizione di grafi top-level individuali per ciascuna tab (`OverviewGraph`, `ChatsGraph` ecc.).
- Routing per il tab `Chats`: `ChatList` -> `ChatDetail`.
- Risolto: **Transizioni**: Le transizioni di slide orizzontale resteranno solo per push/pop in profondità (es. List -> Detail), mentre il cambio di tab avrà transizioni più discrete (fade) per evitare glitch visivi tipici di Navigation Compose.
- Risolto: **Azioni chat-specific**: Manterremo la possibilità di lanciare `Media`, `WordSearch` e `Calls` dal `ChatDetail` passando il `chatId`, preservando il funzionamento esistente gestito già da `AppNavigation.kt`.

---

### Modifiche alle Schermate

#### [MODIFY] `com/whatstat/reborn/ui/screen/dashboard/DashboardScreen.kt`

- Rimuovere i 4 pulsanti in fondo ("Lista Chat", "Media & Link", "Radar Parole", "Log Chiamate") e ripulire il padding.

#### [MODIFY] `com/whatstat/reborn/ui/screen/chatdetail/ChatDetailScreen.kt`

- Rimuovere il pulsante "Analisi Chat" a fondo pagina.
- Il ritorno avverrà rigorosamente tramite back nativo (freccia in alto o gesture di sistema).

#### [MODIFY] `com/whatstat/reborn/ui/screen/media/MediaStatsScreen.kt`

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

- Flusso root: Avvio `Setup` (nessuna bar) -> Completamento -> Appare `AppScaffold` con Bottom Bar esplorabile.
- Cambio tra tab multiple (es. Chat -> Media -> Radar) con transizione pulita (fade).
- Navigazione di backstack: Apri `ChatDetail`, passa alla tab Radar, ritorna alla tab Chat -> `ChatDetail` deve riapparire nel punto in cui era.
- Verifica del back nativo all'interno del tab `Chat` (`ChatDetail` -> `ChatList`).
- Verifica delle navigazioni chat-specific passando da `ChatDetail` ai log chiamate per singolo utente.
- Controllo integrità visiva dei Design Tokens su componenti globali.
