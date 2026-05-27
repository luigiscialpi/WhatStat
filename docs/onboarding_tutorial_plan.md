# Piano di Sviluppo: Tutorial Onboarding

## Obiettivo

Introdurre un tutorial guidato (4-5 schermate, stile pager con "Avanti" / "Salta") che:
1. Appare automaticamente al primo avvio dopo l'installazione.
2. E' riaccessibile in qualsiasi momento dal menu hamburger (AppDrawer).

---

## Architettura

### Posizionamento nel grafo di navigazione

Il tutorial NON viene inserito come nuovo grafo nella navigazione. Viene mostrato come **full-screen dialog/overlay** sopra `SetupScreen` al primo avvio — e come dialog modale quando aperto dal drawer.

Motivo: inserire un grafo aggiuntivo fra `app_graph` e `setup_graph` complicherebbe il flusso di share intent, auto-navigazione e back stack. Un overlay Composable evita effetti collaterali.

```
Primo avvio:
  MainActivity.onCreate → AppNavigation → SetupScreen
      → check hasSeenTutorial == false
      → mostra TutorialOverlay (full-screen)
      → utente completa/salta → salva flag → prosegue con Setup

Da drawer:
  AppDrawer → voce "Tutorial" → showTutorial = true
      → TutorialOverlay (full-screen dialog)
```

### Persistenza flag "tutorial visto"

Aggiungere una chiave booleana a `AppPreferencesStore`:

```kotlin
// AppPreferencesStore.kt
companion object {
    private val TUTORIAL_SEEN_KEY = booleanPreferencesKey("tutorial_seen")
}

val hasSeenTutorial: Flow<Boolean> =
    dataStore.data.map { it[TUTORIAL_SEEN_KEY] ?: false }

suspend fun setTutorialSeen() {
    dataStore.edit { it[TUTORIAL_SEEN_KEY] = true }
}
```

---

## Contenuto delle Schermate (7 pagine)

| # | Titolo | Contenuto | Stile visivo |
|---|--------|-----------|--------------|
| 1 | Benvenuto in WhatStat | Analizza le tue chat WhatsApp in modo sicuro. Nessun dato lascia il dispositivo. | Hero illustration: icona app grande con "scudo" animato (pulse glow). Testo centrato sotto. Background gradient radiale `primaryContainer → surface`. |
| 2 | Due Modalita' | **Root**: analisi completa dal DB live. **Lite**: importa un export ZIP senza root. | Due card Material 3 affiancate (Filled + Outlined), icona `AdminPanelSettings` per Root e `FolderZip` per Lite. Card con `ElevatedCard` + ombra soft. |
| 3 | Come importare una chat | Guida passo-passo stile numerato: 1) Apri WhatsApp 2) Tocca "..." → Esporta chat 3) Scegli "Includi media" 4) Condividi con WhatStat o salva ZIP. | Layout verticale a step numerati con `NumberedStepRow` (cerchietto colorato + testo). Icona WhatsApp verde in cima. Evidenziazione bold su "Esporta chat" e "Includi media". Freccia animata verso il basso tra gli step. |
| 4 | Dashboard e Statistiche | Messaggi totali, media/giorno, grafici interattivi a barre, breakdown per mittente con donut chart, streak piu' lunga. | Mini-mockup stilizzato: 3 StatCard finte in riga (numeri placeholder con `shimmer` statico decorativo) + barre colorate sotto. Palette `primary`/`secondary`/`tertiary`. |
| 5 | Radar Parole | Cerca qualsiasi parola: occorrenze per mittente, anteprima messaggi, filtri avanzati (case sensitive, parola esatta, AND/OR). | Illustrazione: barra di ricerca stilizzata con lente + sotto una word cloud decorativa con 5-6 parole a dimensioni diverse. Colori `tertiary`/`secondary`. |
| 6 | Galleria Media | Esplora foto, video, audio e link condivisi. Galleria con zoom, breakdown per dominio, statistiche per tipo MIME. | Griglia 2x2 di thumbnail placeholder (rounded corners, colori pastello) + icona play su una cella. Badge contatore in alto a destra. |
| 7 | Pronto! | Tocca "Inizia" per configurare la tua prima analisi. Puoi rileggere questo tutorial dal menu in qualsiasi momento. | Checkmark animato (scale-in + confetti particles leggeri). Pulsante "Inizia" grande e prominente (`FilledTonalButton` con `primary`). Nota in `bodySmall` sul menu. |

---

## Linee Guida UX/UI

### Principi di design

1. **Hero-first layout** — Ogni pagina dedica il 55-60% superiore all'illustrazione/mockup e il 40% inferiore a titolo + descrizione. L'utente "capisce" prima visivamente, poi legge.

2. **Gerarchia tipografica forte** — Titolo in `headlineMedium` + `FontWeight.Bold`. Descrizione in `bodyLarge` con `color = onSurfaceVariant`. Parole chiave in **bold** o colore `primary` inline tramite `AnnotatedString`.

3. **Colore come guida** — Ogni pagina ha un accento cromatico dominante coerente col tema Material 3:
   - Pag. 1: `primary` (brand identity)
   - Pag. 2: `secondary` + `tertiary` (dualita' Root/Lite)
   - Pag. 3: verde WhatsApp (`#25D366`) come accento
   - Pag. 4-5: `primary` (feature core)
   - Pag. 6: `tertiaryContainer` (media = contenuto visivo)
   - Pag. 7: `primary` (call to action)

4. **Micro-animazioni** — Niente animazioni pesanti. Solo:
   - Dot indicator: `animateDpAsState` sulla dimensione
   - Transizione pagina: default `HorizontalPager` spring
   - Pagina finale: scale-in sul checkmark (300ms `FastOutSlowInEasing`)
   - Freccia step (pag. 3): `infiniteTransition` con traslazione Y di 4dp

5. **Accessibilita'** — Tutte le illustrazioni hanno `contentDescription`. Contrasto minimo 4.5:1 su testo. Pulsanti con dimensione minima touch 48dp.

6. **Dark mode** — Le illustrazioni usano `colorScheme` dinamico, non colori hardcoded. In dark mode il gradient di sfondo diventa `surfaceContainerLow → surface`.

### Pattern visivi per pagina

**Pagina "Come importare" (pag. 3)** — La piu' importante per l'utente nuovo:

```
┌─────────────────────────────┐
│      [icona WhatsApp]       │
│                             │
│  ① Apri WhatsApp            │
│     ↓                       │
│  ② Tocca ... → Esporta chat │
│     ↓                       │
│  ③ Scegli "Includi media"   │
│     ↓                       │
│  ④ Condividi con WhatStat   │
│     oppure salva lo ZIP     │
└─────────────────────────────┘
│  [•  •  ●  •  •  •  •]     │
│         [Avanti →]          │
└─────────────────────────────┘
```

Ogni step e' un composable `NumberedStep(number, text, highlightWords)`:
- Cerchio numerato in `primaryContainer` con numero in `onPrimaryContainer`
- Testo con keyword in bold/primary via `buildAnnotatedString`
- Linea verticale tratteggiata tra i cerchi (Canvas `drawLine` con `pathEffect = dashPathEffect`)

**Pagina "Galleria Media" (pag. 6)** — Mostra il valore visivo:

```
┌─────────────────────────────┐
│  ┌──────┐  ┌──────┐        │
│  │ 🖼️   │  │ ▶️   │        │
│  └──────┘  └──────┘        │
│  ┌──────┐  ┌──────┐        │
│  │ 🎵   │  │ 📄   │        │
│  └──────┘  └──────┘        │
│         [42 media]          │
│                             │
│  Galleria Media             │
│  Esplora foto, video, audio │
│  e link. Zoom, breakdown    │
│  per dominio, stats MIME.   │
└─────────────────────────────┘
```

Griglia finta con `ElevatedCard` arrotondati, icona tipo MIME centrata, colori pastello diversi per cella. Badge numerico in overlay.

---

## Componenti UI

### `TutorialScreen.kt`

File: `ui/screen/tutorial/TutorialScreen.kt`

```kotlin
@Composable
fun TutorialScreen(
    onComplete: () -> Unit,  // callback alla chiusura
)
```

Implementazione:
- `HorizontalPager` (Compose Foundation — gia' disponibile via BOM 2025.05)
- `PagerState` per tracciare la pagina corrente
- Row di dot indicators in basso (custom composable `PageIndicator`)
- Pulsante "Salta" in alto a destra (naviga direttamente a `onComplete`)
- Pulsante "Avanti" in basso; diventa "Inizia" sull'ultima pagina

### `TutorialPage.kt`

File: `ui/screen/tutorial/TutorialPage.kt`

Composable riusabile per ogni pagina:
```kotlin
@Composable
fun TutorialPage(
    title: String,
    description: AnnotatedString,       // supporta bold/colori inline
    accentColor: Color,                  // colore dominante della pagina
    illustration: @Composable () -> Unit, // slot composable per illustrazione custom
)
```

Layout: illustrazione (slot composable) in alto (55-60%), titolo + descrizione sotto (40-45%). Background con gradient radiale `accentColor.copy(alpha=0.08f)` → `surface`. Padding laterale 24dp.

### `PageIndicator.kt`

File: `ui/screen/tutorial/PageIndicator.kt`

Row di cerchi: cerchio pieno per pagina corrente, cerchio vuoto per le altre. Animazione di transizione con `animateDpAsState`.

---

## Integrazione

### 1. SetupScreen — primo avvio

In `SetupScreen.kt` (o nel composable wrapper in `AppNavigation.kt`), aggiungere:

```kotlin
val hasSeenTutorial by appPreferencesStore.hasSeenTutorial.collectAsState(initial = true)
var showTutorial by remember { mutableStateOf(false) }

LaunchedEffect(hasSeenTutorial) {
    if (!hasSeenTutorial) {
        showTutorial = true
    }
}

if (showTutorial) {
    TutorialScreen(
        onComplete = {
            showTutorial = false
            scope.launch { appPreferencesStore.setTutorialSeen() }
        },
    )
}
```

`initial = true` evita il flash del tutorial prima che DataStore restituisca il valore reale.

### 2. AppDrawer — voce "Tutorial"

In `AppDrawerContent`, aggiungere un `DrawerItem` con icona `Icons.Outlined.HelpOutline` e label da stringa `R.string.drawer_tutorial`. Al click:
- Chiude il drawer
- Imposta un flag `showTutorial = true` in `MainViewModel` (esposto come `StateFlow<Boolean>`)

In `AppScaffold` (o `AppNavigation`), osservare il flag e mostrare `TutorialScreen` come full-screen overlay:

```kotlin
val showTutorial by mainViewModel.showTutorial.collectAsState()

if (showTutorial) {
    TutorialScreen(
        onComplete = { mainViewModel.dismissTutorial() }
    )
}
```

### 3. MainViewModel

Aggiungere:
```kotlin
private val _showTutorial = MutableStateFlow(false)
val showTutorial: StateFlow<Boolean> = _showTutorial.asStateFlow()

fun requestTutorial() { _showTutorial.value = true }
fun dismissTutorial() { _showTutorial.value = false }
```

---

## Stringhe (i18n)

Aggiungere in `res/values/strings.xml` (e traduzioni corrispondenti):

```xml
<string name="drawer_tutorial">Tutorial</string>
<string name="tutorial_skip">Skip</string>
<string name="tutorial_next">Next</string>
<string name="tutorial_start">Get Started</string>
<string name="tutorial_title_1">Welcome to WhatStat</string>
<string name="tutorial_desc_1">Analyze your WhatsApp chats safely.\nYour data never leaves your device.</string>
<string name="tutorial_title_2">Two Modes</string>
<string name="tutorial_desc_2">Root: full analysis from the live database.\nLite: import a WhatsApp export ZIP — no root needed.</string>
<string name="tutorial_title_3">How to Export a Chat</string>
<string name="tutorial_desc_3_step1">Open WhatsApp on your device.</string>
<string name="tutorial_desc_3_step2">Open a chat, tap the three dots (…) at the top right, then tap Export chat.</string>
<string name="tutorial_desc_3_step3">Choose Include media (recommended!), then tap Share.</string>
<string name="tutorial_desc_3_step4">Select WhatStat from the share menu, or save the ZIP and import it manually.</string>
<string name="tutorial_title_4">Dashboard &amp; Stats</string>
<string name="tutorial_desc_4">Total messages, daily averages, interactive bar charts, sender breakdown with donut charts, and longest streak.</string>
<string name="tutorial_title_5">Word Radar</string>
<string name="tutorial_desc_5">Search any word across chats: occurrences by sender, message previews, and advanced filters (case sensitive, exact word, AND/OR).</string>
<string name="tutorial_title_6">Media Gallery</string>
<string name="tutorial_desc_6">Browse photos, videos, audio, and shared links. Full-screen zoom, domain breakdown, and per-type statistics.</string>
<string name="tutorial_title_7">Ready!</string>
<string name="tutorial_desc_7">Tap "Get Started" to set up your first analysis.\nYou can revisit this tutorial from the menu anytime.</string>
```

Traduzioni italiane corrispondenti:

```xml
<string name="drawer_tutorial">Tutorial</string>
<string name="tutorial_skip">Salta</string>
<string name="tutorial_next">Avanti</string>
<string name="tutorial_start">Inizia</string>
<string name="tutorial_title_1">Benvenuto in WhatStat</string>
<string name="tutorial_desc_1">Analizza le tue chat WhatsApp in sicurezza.\nI tuoi dati non lasciano mai il dispositivo.</string>
<string name="tutorial_title_2">Due Modalita\'</string>
<string name="tutorial_desc_2">Root: analisi completa dal database live.\nLite: importa un export ZIP di WhatsApp — nessun root necessario.</string>
<string name="tutorial_title_3">Come esportare una chat</string>
<string name="tutorial_desc_3_step1">Apri WhatsApp sul tuo dispositivo.</string>
<string name="tutorial_desc_3_step2">Apri una chat, tocca i tre puntini (...) in alto a destra, poi tocca Esporta chat.</string>
<string name="tutorial_desc_3_step3">Scegli Includi media (consigliato!), poi tocca Condividi.</string>
<string name="tutorial_desc_3_step4">Seleziona WhatStat dal menu di condivisione, oppure salva lo ZIP e importalo manualmente.</string>
<string name="tutorial_title_4">Dashboard e Statistiche</string>
<string name="tutorial_desc_4">Messaggi totali, media giornaliera, grafici a barre interattivi, breakdown per mittente con grafici a torta e streak piu\' lunga.</string>
<string name="tutorial_title_5">Radar Parole</string>
<string name="tutorial_desc_5">Cerca qualsiasi parola nelle chat: occorrenze per mittente, anteprima messaggi e filtri avanzati (maiuscole, parola esatta, AND/OR).</string>
<string name="tutorial_title_6">Galleria Media</string>
<string name="tutorial_desc_6">Esplora foto, video, audio e link condivisi. Zoom a schermo intero, breakdown per dominio e statistiche per tipo.</string>
<string name="tutorial_title_7">Pronto!</string>
<string name="tutorial_desc_7">Tocca "Inizia" per configurare la tua prima analisi.\nPuoi rileggere questo tutorial dal menu in qualsiasi momento.</string>
```

---

## Illustrazioni

Approccio scelto: **Composable illustrations** — disegnate direttamente in Compose (Canvas + Material icons + shape layers). Zero asset bitmap, scalabili, coerenti col tema chiaro/scuro, peso zero sull'APK.

Ogni pagina ha un composable dedicato in `ui/screen/tutorial/illustrations/`:

| Pagina | File | Tecnica |
|--------|------|---------|
| 1 - Benvenuto | `WelcomeIllustration.kt` | Icona app scalata + cerchio pulsante con `drawCircle` + gradient radiale |
| 2 - Modalita' | `ModesIllustration.kt` | Due `ElevatedCard` con icone Material (`AdminPanelSettings`, `FolderZip`) |
| 3 - Import | `ImportStepsIllustration.kt` | Composable `NumberedStep` ripetuto 4x con linea tratteggiata Canvas |
| 4 - Dashboard | `DashboardIllustration.kt` | 3 fake StatCard + 5 barre colorate animate (altezza randomizzata) |
| 5 - Radar | `RadarIllustration.kt` | `TextField` decorativo + 6 `Text` a dimensioni/posizioni diverse (word cloud) |
| 6 - Media | `MediaGalleryIllustration.kt` | Griglia 2x2 `ElevatedCard` con icone MIME + badge numerico overlay |
| 7 - Pronto | `ReadyIllustration.kt` | Checkmark con `scale` animation + particelle circolari in dissolvenza |

### Componente riusabile: `NumberedStep`

```kotlin
@Composable
fun NumberedStep(
    number: Int,
    text: AnnotatedString,
    isLast: Boolean = false,
    accentColor: Color = MaterialTheme.colorScheme.primary,
)
```

Cerchio 28dp con numero centrato. Linea verticale tratteggiata sotto (se `!isLast`) con `PathEffect.dashPathEffect(floatArrayOf(8f, 8f))`. Testo con keyword evidenziate in `fontWeight = Bold` + `color = accentColor`.

---

## File da creare/modificare

| Azione | File |
|--------|------|
| Creare | `ui/screen/tutorial/TutorialScreen.kt` |
| Creare | `ui/screen/tutorial/TutorialPage.kt` |
| Creare | `ui/screen/tutorial/PageIndicator.kt` |
| Creare | `ui/screen/tutorial/illustrations/WelcomeIllustration.kt` |
| Creare | `ui/screen/tutorial/illustrations/ModesIllustration.kt` |
| Creare | `ui/screen/tutorial/illustrations/ImportStepsIllustration.kt` |
| Creare | `ui/screen/tutorial/illustrations/DashboardIllustration.kt` |
| Creare | `ui/screen/tutorial/illustrations/RadarIllustration.kt` |
| Creare | `ui/screen/tutorial/illustrations/MediaGalleryIllustration.kt` |
| Creare | `ui/screen/tutorial/illustrations/ReadyIllustration.kt` |
| Creare | `ui/screen/tutorial/illustrations/NumberedStep.kt` |
| Modificare | `data/store/AppPreferencesStore.kt` — aggiungere `TUTORIAL_SEEN_KEY` |
| Modificare | `ui/MainViewModel.kt` — aggiungere `showTutorial` + `requestTutorial()` + `dismissTutorial()` |
| Modificare | `ui/component/AppDrawer.kt` — aggiungere voce "Tutorial" |
| Modificare | `ui/navigation/AppNavigation.kt` — overlay tutorial al primo avvio + dal drawer |
| Modificare | `res/values/strings.xml` (+ tutte le 8 localizzazioni) — stringhe tutorial |

---

## Dipendenze

Nessuna nuova dipendenza. `HorizontalPager` e `PagerState` sono inclusi in `androidx.compose.foundation` (gia' disponibile via `compose-bom:2025.05.00`).

---

## Vincoli rispettati

- Nessuna query DB sul main thread
- Nessun import wildcard
- Import lessicografici
- Nessun riferimento fully-qualified inline
- Stringhe esternalizzate in `strings.xml`
- Pattern MVVM (flag in ViewModel, stato in DataStore)
- Nessun `if (isLite)` nel tutorial — agnostico della modalita'
- `ktlint` compliant

---

## Sequenza implementativa

1. `AppPreferencesStore` — aggiungere chiave + metodi
2. `MainViewModel` — aggiungere stato tutorial
3. `NumberedStep.kt` — componente riusabile per la pagina import
4. Illustrazioni (7 file in `illustrations/`) — composable decorativi
5. `TutorialPage.kt` + `PageIndicator.kt` — componenti base
6. `TutorialScreen.kt` — pager + logica navigazione interna
7. `AppNavigation.kt` — overlay primo avvio
8. `AppDrawer.kt` — voce menu
9. Stringhe i18n (tutte le 8 lingue)
10. Test manuale + `./gradlew ktlintCheck`
