---
name: UI navigation refactor
overview: Riorganizzare la navigazione con una Bottom Navigation permanente (5 tab) e stack indipendenti per tab, mantenendo performance (quick/slow stats + cache) e uniformando pattern UX (back, filtri, loading/error/empty).
todos:
  - id: nav-shell
    content: Progettare `AppScaffold` con Bottom Navigation (5 tab) e regole di visibilità (no bar su Setup).
    status: pending
  - id: nav-graphs
    content: Ristrutturare `AppNavigation.kt` in grafi top-level per tab + navigate pattern con save/restore state per stack indipendenti.
    status: pending
  - id: chat-back
    content: "Aggiornare flusso Chat: `ChatList -> ChatDetail` con back standard; rimuovere CTA “Analisi Chat” dal dettaglio."
    status: pending
  - id: media-subtabs
    content: Valutare/implementare sotto-tab “Media/Link” in `MediaStatsScreen` per aumentare scopribilità.
    status: pending
  - id: ux-consistency
    content: Uniformare error/empty/loading e interazione filtri tra schermate (almeno Dashboard + ChatDetail + Media/Calls/WordSearch).
    status: pending
  - id: design-tokens
    content: Introdurre token minimi di spacing/corner e applicarli ai componenti più visibili per coerenza UI.
    status: pending
isProject: false
---

## Obiettivi UX/UI (derivati da `docs/refactor_ui.md` + doc funzionale)
- Rendere **scopribili** le sezioni oggi “nascoste” (Media, Radar, Chiamate) con **Bottom Navigation** persistente.
- Allineare la gerarchia: tab = sezioni di primo livello; **back nativo** per tornare indietro nello stack della tab (rimuovere “Analisi Chat” nel dettaglio chat).
- Preservare comportamento attuale di **filtri temporali** (sticky header, `TimeRangeFilterRow`, stato persistito via `FilterStateHolder`) e di **performance percepita** (quick stats immediate + slow stats progressive/opt-in).

## Stato attuale (dalla codebase)
- Navigazione centralizzata in [`app/src/main/java/com/whatstat/reborn/ui/navigation/AppNavigation.kt`](app/src/main/java/com/whatstat/reborn/ui/navigation/AppNavigation.kt) con un unico `NavHost` e route `Screen`.
- Entry point in [`app/src/main/java/com/whatstat/reborn/MainActivity.kt`](app/src/main/java/com/whatstat/reborn/MainActivity.kt).
- Schermate principali:
  - Setup: [`ui/screen/setup/SetupScreen.kt`](app/src/main/java/com/whatstat/reborn/ui/screen/setup/SetupScreen.kt)
  - Dashboard (Panoramica): [`ui/screen/dashboard/DashboardScreen.kt`](app/src/main/java/com/whatstat/reborn/ui/screen/dashboard/DashboardScreen.kt)
  - Chat list: [`ui/screen/chatlist/ChatListScreen.kt`](app/src/main/java/com/whatstat/reborn/ui/screen/chatlist/ChatListScreen.kt)
  - Chat detail: [`ui/screen/chatdetail/ChatDetailScreen.kt`](app/src/main/java/com/whatstat/reborn/ui/screen/chatdetail/ChatDetailScreen.kt)
  - Media & Link: [`ui/screen/media/MediaStatsScreen.kt`](app/src/main/java/com/whatstat/reborn/ui/screen/media/MediaStatsScreen.kt)
  - Radar parole: [`ui/screen/wordsearch/WordSearchScreen.kt`](app/src/main/java/com/whatstat/reborn/ui/screen/wordsearch/WordSearchScreen.kt)
  - Log chiamate: [`ui/screen/calls/CallsStatsScreen.kt`](app/src/main/java/com/whatstat/reborn/ui/screen/calls/CallsStatsScreen.kt)
- Tema Material 3 in [`ui/theme/Theme.kt`](app/src/main/java/com/whatstat/reborn/ui/theme/Theme.kt) (colori+tipografia centralizzati; spacing/shapes spesso hardcoded).

## Scelta architetturale proposta (focalizzata su “fattibile”)
- Usare **Navigation Compose 2.8.5** già presente per ottenere “stack per tab” con **un solo `NavController`** + **top-level graphs** per tab.
  - Pattern: `navigate(tabRoute) { launchSingleTop=true; restoreState=true; popUpTo(start) { saveState=true } }`.
- Separare la nav in due macro-fasi:
  - **Pre-main**: `Setup` (senza bottom bar).
  - **Main (tabbed shell)**: bottom bar visibile con 5 tab e loro root.

## Interventi principali
1. **Introdurre un “AppScaffold” (shell) con Bottom Navigation**
   - Nuovi componenti UI dedicati alla bottom bar (Material 3 `NavigationBar`) e al mapping route→tab.
   - Mostrare/nascondere la bottom bar in base alla destinazione (es. nascosta in `Setup`).

2. **Rifattorizzare `AppNavigation.kt` in grafi per tab**
   - Creare 5 grafi top-level: `Overview`, `Chats`, `Media`, `Radar`, `Calls`.
   - Dentro ogni tab, definire lo stack interno (es. `Chats`: `ChatList -> ChatDetail`).
   - Mantenere i percorsi esistenti per compatibilità dove possibile (es. `chat_detail/{chatId}`), e aggiungere route “root tab” stabili.

3. **Aggiornare entry points e navigazioni cross-screen**
   - `MainActivity.kt`: mantenere `rememberNavController()` ma delegare a `AppScaffold`.
   - `ChatDetailScreen`: rimuovere CTA “Analisi Chat” (come da brief) e affidarsi a back standard.
   - Navigazioni verso `WordSearch/Calls/Media` da contesto chat: continuare a passare `chatId` opzionale come oggi (già supportato in `AppNavigation.kt`).

4. **Media & Link: valutazione sotto-tab orizzontali**
   - In `MediaStatsScreen.kt`, introdurre una UI tipo `TabRow` / `PrimaryTabRow` per alternare “Media” e “Link” senza “tap aggiuntivi invisibili”.
   - Obiettivo: rendere evidente che la sezione contiene due viste distinte.

5. **Hardening UX: rendere coerenti error/empty/loading e filtri**
   - Allineare il pattern “errore visibile + retry” oltre Dashboard (es. Chat detail oggi sembra non renderizzare l’errore).
   - Standardizzare quando i filtri sono interattivi (es. renderizzare `TimeRangeFilterRow` anche durante load con stato disabilitato, evitando differenze tra schermate).
   - (Facoltativo ma consigliato) introdurre un piccolo “screen state contract” per ogni screen (anche senza una sealed class globale) così ogni screen ha: `isLoading`, `error?`, `content`, `emptyState` in modo consistente.

6. **Design system leggero (incrementale, non-bloccante)**
   - Aggiungere token minimi riusabili (es. `Spacing`, `CornerRadius`) e sostituire gradualmente i `dp` hardcoded nei componenti ad alta visibilità: `StatCard`, `TimeRangeFilterRow`, `LoadingSection`, `SimpleScreenHeader`, chart.
   - Risultato: UI più coerente e più semplice da manutenere senza “big bang refactor”.

## Test plan (pratico, orientato a UX)
- Flusso primo avvio: `Setup -> Main tabs` (bottom bar compare solo dopo setup completato).
- Switching tab:
  - apri `ChatList -> ChatDetail`, passa a `Media`, torna a `Chat`: **deve rimanere su `ChatDetail`** (restoreState).
- Back behavior:
  - in `ChatDetail`, back torna a `ChatList` (e non a “Dashboard” in modo inatteso).
- `chatId` opzionale:
  - da `ChatDetail` apri `Radar/Chiamate/Media` (chat-specific) e verifica che UI/filtri si comportino come ora.
- Filtri temporali: selezione rapida + custom; persistenza cross-screen tramite [`ui/state/FilterStateHolder.kt`](app/src/main/java/com/whatstat/reborn/ui/state/FilterStateHolder.kt).
- Stati: error simulabile (es. database non accessibile) deve produrre un messaggio e una via di recovery, non un “vuoto”.

## Rischi / attenzioni
- Transizioni globali del `NavHost`: con tab switching potrebbero risultare “strane”. Valutare transizioni più discrete sui cambi tab e mantenere le esistenti per push/pop nello stack.
- Minify/shrink in release: evitare dipendenze nuove non necessarie; il pattern proposto usa librerie già presenti.
- Inconsistenze attuali tra schermate (opt-in slow stats vs auto-load): il refactor nav non deve cambiare semantica; i miglioramenti UX vanno introdotti come step separato (evita regressioni).
