# Piano di Implementazione — Sistema Tutorial Onboarding

> Sistema di tutorial interattivo con backdrop, frecce direzionali e step-by-step per ogni schermata dell'app.

---

## 1. Panoramica del Sistema

### Obiettivo

Fornire all'utente una guida interattiva che illustra le funzionalità principali di ogni schermata tramite un overlay semi-trasparente con spotlight (buchi) che evidenziano gli elementi interagibili, accompagnati da tooltip con frecce direzionali.

### Comportamento desiderato

1. L'utente è in una qualsiasi schermata supportata (Dashboard, ChatList, ecc.)
2. Preme il pulsante **"Aiuto"** (o icona lampadina) nella **bottom tab bar**
3. Si attiva **immediatamente** l'overlay tutorial per la schermata corrente
4. Lo spotlight (cutout nel backdrop) si posiziona sull'elemento corrente
5. Un tooltip con freccia indica l'elemento e spiega la funzionalità
6. L'utente naviga con i pulsanti **"Indietro" / "Avanti" / "Chiudi"**
7. Al termine → feedback di completamento + stato salvato per quella schermata

---

## 2. Architettura del Sistema

### 2.1 Struttura dei moduli

```
ui/
  tutorial/
    TutorialState.kt           # Stato e step del tutorial corrente
    TutorialOverlay.kt         # Composable overlay (backdrop + spotlight + tooltip)
    TutorialTooltip.kt         # Componente tooltip con freccia direzionale
    TutorialArrow.kt           # Freccia SVG direzionale
    TutorialScreen.kt          # Wrapper che inietta l'overlay nella schermata target
    TutorialManager.kt         # Singleton che orchestra tutorial e stato di completamento
    data/
      TutorialSteps.kt        # Definizione degli step per ogni schermata
      ScreenTutorialData.kt    # Dati: title, description, target composable key
```

### 2.2 Dettaglio dei componenti

#### `TutorialOverlay`

- Box a schermo intero con `Modifier.drawBehind` o Canvas
- Colore backdrop: `Color.Black.copy(alpha = 0.75f)`
- Spotlight: hole cutout rettangolare (o circolare) disegnato nel Canvas tramite `drawRoundRect` con `BlendMode.Clear`
- Animazione spotlight: transizione suave da un target al successivo con `animateContentSize` / `animateFloatAsState`

#### `TutorialTooltip`

- Card con angoli arrotondati (`RoundedCornerShape(12.dp)`)
- Supporto 4 direzioni della freccia: `Up`, `Down`, `Left`, `Right`
- Freccia disegnata con `Path` + `drawPath` (triangolo)
- Testo titolo (bold) + descrizione
- Posizionamento: calcolato in base alla direzione della freccia e alla posizione del target sullo schermo
- Pulsanti: "Indietro", "Avanti" ("Fine" se ultimo step), "Salta"

#### `TutorialBottomSheet`

- Non più necessaria (rimossa)

---

## 3. Definizione degli Step — ScreenTutorialData

Ogni schermata ha una lista di `TutorialStep`:

```kotlin
data class TutorialStep(
    val id: String,                       // es. "dashboard_1"
    val titleResId: Int,                   // R.string.tutorial_dashboard_1_title
    val descriptionResId: Int,            // R.string.tutorial_dashboard_1_desc
    val targetKey: String,                // key del composable target (usato con onGloballyPositioned)
    val arrowDirection: ArrowDirection,   // posizione della freccia rispetto al target
    val highlightShape: HighlightShape = HighlightShape.ROUNDED_RECT  // ROUNDED_RECT | CIRCLE | FULL_WIDTH
)

enum class ArrowDirection { TOP, BOTTOM, LEFT, RIGHT }
enum class HighlightShape { ROUNDED_RECT, CIRCLE, FULL_WIDTH }
```

### 3.1 Step proposti per ogni schermata

#### SetupScreen

1. **Pulsante "Inizia analisi"** — Arrow: Bottom, shape: ROUNDED_RECT
2. **Indicatori di stato (4 step)** — Arrow: Left, shape: ROUNDED_RECT (singolo riquadro)
3. **Info sulla privacy** — Arrow: Top, shape: ROUNDED_RECT

#### DashboardScreen

1. **Filtri temporali (TimeRangeFilterRow)** — Arrow: Top, shape: FULL_WIDTH
2. **Card statistiche** — Arrow: Left, shape: ROUNDED_RECT
3. **Grafico a barre (InteractiveBarChart)** — Arrow: Left, shape: ROUNDED_RECT
4. **Tab switcher grafico** — Arrow: Bottom, shape: ROUNDED_RECT
5. **Pulsante "Analizza le singole chat"** — Arrow: Top, shape: ROUNDED_RECT
6. **Sezione parole/emoji (WordCloudView)** — Arrow: Left, shape: ROUNDED_RECT

#### ChatListScreen

1. **Campo ricerca** — Arrow: Bottom, shape: ROUNDED_RECT
2. **Lista chat** — Arrow: Left, shape: FULL_WIDTH
3. **Elemento chat singolo** — Arrow: Left, shape: ROUNDED_RECT (spotlight su un item)
4. **Badge conteggio messaggi** — Arrow: Right, shape: CIRCLE

#### ChatDetailScreen

1. **Filtri temporali** — Arrow: Top, shape: FULL_WIDTH
2. **Tab switcher (Stat/Parole/Media)** — Arrow: Bottom, shape: FULL_WIDTH
3. **Card statistiche chat** — Arrow: Left, shape: ROUNDED_RECT
4. **Grafici attività** — Arrow: Left, shape: ROUNDED_RECT
5. **Parole/emoji** — Arrow: Bottom, shape: ROUNDED_RECT

#### MediaStatsScreen

1. **Filtri temporali** — Arrow: Top, shape: FULL_WIDTH
2. **Tab Media/Tipologia** — Arrow: Bottom, shape: FULL_WIDTH
3. **Griglia media** — Arrow: Left, shape: FULL_WIDTH
4. **Filtri media (Foto/Video/etc)** — Arrow: Right, shape: ROUNDED_RECT

#### CallsStatsScreen

1. **Filtri temporali** — Arrow: Top, shape: FULL_WIDTH
2. **Card riepilogo chiamate** — Arrow: Left, shape: ROUNDED_RECT
3. **Lista chiamate** — Arrow: Left, shape: FULL_WIDTH
4. **Tipo chiamata (persone/video)** — Arrow: Bottom, shape: ROUNDED_RECT

#### WordSearchScreen

1. **Campo di ricerca** — Arrow: Bottom, shape: ROUNDED_RECT
2. **Risultati in tempo reale** — Arrow: Left, shape: FULL_WIDTH
3. **Filtri avanzati** — Arrow: Right, shape: ROUNDED_RECT

---

## 4. Gestione dello stato di completamento

### 4.1 PersistentStorage (DataStore)

```kotlin
data class TutorialCompletionState(
    val completedScreens: Set<String> = emptySet(),  // es. setOf("dashboard", "chatlist")
    val lastStepReached: Map<String, Int> = emptyMap(),  // screen -> last step index
)
```

- Salvato in `PreferencesDataStore` nella directory privata dell'app
- letto in `TutorialManager` all'avvio
- aggiornato ad ogni step completato e alla fine del tutorial

### 4.2 Reset tutorial

- Accessibile dal menu impostazioni (icona ingranaggio nella bottom sheet) o dalla bottom sheet stessa
- Tasto "Ripristina tutorial" → pulisce lo stato e riapre la bottom sheet

---

## 5. Integrazione nelle schermate esistenti

### 5.1 Integrazione nelle schermate esistenti

Ogni schermata include direttamente l'overlay:

```kotlin
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = viewModel(),
    tutorialManager: TutorialManager = LocalTutorialManager.current  // injected
) {
    val tutorialState by tutorialManager.tutorialState.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        // ... contenuto normale della schermata ...

        // Overlay tutorial — attivo se l'utente preme "Aiuto" E siamo sulla Dashboard
        if (tutorialState.isTutorialActive && tutorialState.currentScreen == Screen.DASHBOARD) {
            TutorialOverlay(
                steps = DashboardTutorialSteps.steps,
                currentStep = tutorialState.currentStep,
                onNext = { tutorialManager.nextStep() },
                onPrevious = { tutorialManager.previousStep() },
                onClose = { tutorialManager.closeTutorial() },
                onComplete = { tutorialManager.completeTutorial(Screen.DASHBOARD) }
            )
        }
    }
}
```

### 5.2 Target positioning

- Ogni composable target espone la sua posizione tramite `Modifier.onGloballyPositioned { coordinates -> ... }`
- Le coordinate vengono fornite al `TutorialManager` che calcola il bounding box dello spotlight
- Suggerimento: usare `onGloballyPositioned` con stato globale nel `TutorialManager`

### 5.3 Navigazione schermata durante tutorial

- Durante un tutorial attivo, la navigazione normale è disabilitata (tap al di fuori del tooltip non naviga)
- L'overlay intercetta tutti i tap al di fuori del tooltip (non-pass-through)
- I FAB e bottoni interattivi non sono cliccabili durante il tutorial (vengono "coperti" dall'overlay)

---

## 6. UX / UI — Regole di Design

### 6.1 Posizionamento tooltip

| Direzione freccia | Posizione tooltip     | Offset default |
| ----------------- | --------------------- | -------------- |
| TOP               | Sotto il target       | 16.dp          |
| BOTTOM            | Sopra il target       | 16.dp          |
| LEFT              | A destra del target   | 16.dp          |
| RIGHT             | A sinistra del target | 16.dp          |

Se il tooltip fuoriesce dallo schermo, invertirne la posizione automaticamente.

### 6.2 Animazioni

- **Backdrop**: fade in 300ms `tween`
- **Spotlight**: size animation 400ms `spring(dampingRatio = 0.7f)`
- **Tooltip**: slide + fade in 350ms `tween`
- **Transizione step**: fade out tooltip (150ms) → reposiziona spotlight → fade in tooltip (200ms)

### 6.3 Colori e tipografia

```kotlin
// Backdrop
val backdropColor = Color.Black.copy(alpha = 0.75f)

// Spotlight border (glow attorno al buco)
val spotlightBorderColor = Color.White.copy(alpha = 0.3f)
val spotlightBorderWidth = 2.dp

// Tooltip
val tooltipBackground = MaterialTheme.colorScheme.surface
val tooltipContentColor = MaterialTheme.colorScheme.onSurface
val tooltipTitleStyle = MaterialTheme.typography.titleSmall
val tooltipBodyStyle = MaterialTheme.typography.bodySmall
val tooltipArrowColor = MaterialTheme.colorScheme.primary
```

### 6.4 Accessibilità

- Ogni tooltip ha un `contentDescription` per TalkBack
- Il pulsante "Salta" è sempre visibile e raggiungibile tramite focus
- L'overlay backdrop non deve mai essere completamente nero (mantenere `alpha < 0.9`)

---

## 7. Navigazione e bottom bar

### 7.1 Posizionamento del pulsante aiuto

- Il pulsante **"Aiuto"** è posizionato nella **bottom tab bar**
- Icona: `Icons.Filled.HelpOutline` o `Icons.Filled.Lightbulb`
- Label: "Aiuto"
- Cliccando → `tutorialManager.startTutorialForScreen(currentScreen)`

---

## 8. Dipendenze e librerie

### Nuove dipendenze necessarie

```kotlin
// Già presenti nel progetto (verificare build.gradle.kts)
implementation("androidx.compose.material3:material3")
implementation("androidx.compose.animation:animation")
implementation("androidx.datastore:datastore-preferences")
implementation("androidx.lifecycle:lifecycle-viewmodel-compose")
```

### Nessuna nuova dipendenza esterna richiesta

- Il sistema è interamente costruito con Compose puro
- Canvas per il backdrop con spotlight cutout
- Path per le frecce
- DataStore per la persistenza

---

## 9. Piano di implementazione step-by-step

### Fase 1 — Struttura base e stato

1. Creare `TutorialStep` e `ScreenTutorialData` (data model)
2. Creare `TutorialCompletionState` con DataStore
3. Creare `TutorialManager` (ViewModel singleton)
4. Creare stato in `AppScaffold` / `MainViewModel`

### Fase 2 — Overlay e Tooltip

5. Implementare `TutorialArrow` (SVG path)
6. Implementare `TutorialTooltip` (Card + freccia + bottoni)
7. Implementare `TutorialOverlay` (backdrop + spotlight Canvas)
8. Definire gli step per una schermata campione (Dashboard)

### Fase 3 — Integrazione

9. Aggiungere pulsante Aiuto nella bottom `NavigationBar`
10. Collegare il pulsante a `tutorialManager.startTutorialForScreen(currentScreen)`
11. Integrare `TutorialOverlay` in ogni schermata supportata
12. Implementare `onGloballyPositioned` per il target positioning
13. Collegare `TutorialManager` → aggiornamento stato completamento

### Fase 4 — Tutte le schermate

14. Definire step per `ChatListScreen`
15. Definire step per `ChatDetailScreen`
16. Definire step per `MediaStatsScreen`
17. Definire step per `CallsStatsScreen`
18. Definire step per `WordSearchScreen`
19. Definire step per `SetupScreen`

### Fase 5 — Polish

20. Animazioni di transizione tra step
21. Gestione edge case (posizionamento tooltip fuori schermo)
22. Reset tutorial dalle impostazioni
23. Test accessibilità TalkBack

---

## 10. File da creare

| File                                               | Fase  |
| -------------------------------------------------- | ----- |
| `ui/tutorial/data/TutorialStep.kt`                 | 1     |
| `ui/tutorial/data/ScreenTutorialData.kt`           | 1     |
| `ui/tutorial/TutorialArrow.kt`                     | 2     |
| `ui/tutorial/TutorialTooltip.kt`                   | 2     |
| `ui/tutorial/TutorialOverlay.kt`                   | 2     |
| `ui/tutorial/TutorialBottomSheet.kt`               | 5     |
| `ui/tutorial/tutorials/DashboardTutorialSteps.kt`  | 4     |
| `ui/tutorial/tutorials/ChatListTutorialSteps.kt`   | 4     |
| `ui/tutorial/tutorials/ChatDetailTutorialSteps.kt` | 4     |
| `ui/tutorial/tutorials/MediaTutorialSteps.kt`      | 4     |
| `ui/tutorial/tutorials/CallsTutorialSteps.kt`      | 4     |
| `ui/tutorial/tutorials/WordSearchTutorialSteps.kt` | 4     |
| `ui/tutorial/tutorials/SetupTutorialSteps.kt`      | 4     |
| `ui/screen/dashboard/DashboardScreenWrapper.kt`    | 3     |
| `res/values/strings_tutorial.xml` (nuovo)          | tutte |

---

## 11. Considerazioni aggiuntive

### 11.1 Prima installazione vs utenti esistenti

- Per i nuovi utenti: proporre il tutorial dopo il primo accesso alla Dashboard (dopo il setup)
- Per gli utenti esistenti: il tutorial non parte automaticamente, ma è sempre accessibile dal pulsante Aiuto nella bottom bar
- Mostrare un **badge "NEW"** accanto al pulsante Aiuto per i primi 3 accessi dopo l'installazione

### 11.2 Tutorial parziale

- L'utente può chiudere il tutorial in qualsiasi momento
- Lo stato parziale viene salvato: se riapre lo stesso tutorial, riprende dallo step dove l'aveva lasciato
- Il badge "Completato" appare solo dopo aver attraversato tutti gli step di quella schermata

### 11.3 Localizzazione

- Tutte le stringhe del tutorial in `strings_tutorial.xml`
- Supportare inglese e italiano (e traduzione automatica come il resto dell'app)

### 11.4 Schermate non supportate

- Se l'utente preme "Aiuto" in una schermata che non ha step definiti, mostrare un messaggio informativo ("Tutorial non disponibile per questa schermata") tramite snackbar o toast breve
