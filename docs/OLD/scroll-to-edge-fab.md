# Scroll-to-Edge FAB — Piano di sviluppo

> Pulsante flottante compatto per tornare in cima (o in fondo per GroupHistory) nelle schermate con liste lunghe.

---

## Panoramica

Componente riusabile `ScrollToEdgeFab` in `ui/component/`. Appare con animazione fade+scale dopo soglia di scroll, scompare quando l'utente e' gia' in prossimita' dell'estremo.

---

## Step 1 — Componente `ScrollToEdgeFab`

### File: `ui/component/ScrollToEdgeFab.kt` [NEW]

**Comportamento:**
- Appare quando `firstVisibleItemIndex > threshold` (default: 3)
- Animazione ingresso/uscita: `AnimatedVisibility` con `fadeIn + scaleIn` / `fadeOut + scaleOut`
- Click: `animateScrollToItem(0)` (o ultimo item per reverseLayout)
- Debounce implicito: la coroutine di scroll cancella automaticamente la precedente

**Design (coerente con lo stile app):**
- `SmallFloatingActionButton` (Material3)
- `containerColor = MaterialTheme.colorScheme.primaryContainer`
- `contentColor = MaterialTheme.colorScheme.onPrimaryContainer`
- Icona: `Icons.Outlined.KeyboardArrowUp` (o `KeyboardArrowDown` per reverseLayout)
- Corner radius: circolare (default SmallFAB)
- Elevazione: default Material3 (6dp)
- Posizionamento: `Alignment.BottomEnd`, padding `end = 20.dp, bottom = 24.dp`

**Firma proposta (overload per LazyListState e LazyGridState):**

```kotlin
// Overload per LazyColumn
@Composable
fun ScrollToEdgeFab(
    listState: LazyListState,
    modifier: Modifier = Modifier,
    threshold: Int = 3,
    reverseScroll: Boolean = false,
)

// Overload per LazyVerticalGrid
@Composable
fun ScrollToEdgeFab(
    gridState: LazyGridState,
    modifier: Modifier = Modifier,
    threshold: Int = 3,
)
```

**Logica visibilita' (derivedStateOf per performance):**

```kotlin
val showButton by remember {
    derivedStateOf {
        listState.firstVisibleItemIndex > threshold
    }
}
```

---

## Step 2 — Integrazione per schermata

Ogni schermata richiede:
1. Assicurare che esista un `listState` / `gridState` esplicito
2. Wrappare il contenuto in un `Box` (se non gia' presente)
3. Aggiungere `ScrollToEdgeFab` dentro il Box, allineato `BottomEnd`

### 2.1 ChatListScreen

- **File:** `ui/screen/chatlist/ChatListScreen.kt`
- **Stato:** `listState` gia' presente (aggiunto di recente)
- **Conflitto FAB:** In Lite Mode esiste gia' un `ExtendedFloatingActionButton` in `BottomEnd`
- **Soluzione:** Posizionare lo scroll FAB con `bottom = if (isLiteMode) 88.dp else 24.dp` per stare sopra il FAB Lite
- **Box wrapper:** gia' presente (riga 100)

### 2.2 MediaStatsScreen — Lista principale

- **File:** `ui/screen/media/MediaStatsScreen.kt`
- **Stato:** `currentListState` gia' presente (`globalListState` / `breakdownListState`)
- **Box wrapper:** gia' presente (riga 178)
- **Note:** Il FAB deve apparire solo quando la lista e' visibile (`!isLoading && displayStats.isNotEmpty()`)

### 2.3 MediaStatsScreen — MediaGallerySheet (bottomsheet galleria)

- **File:** `ui/screen/media/MediaStatsScreen.kt`, funzione `MediaGallerySheet`
- **Stato:** `LazyVerticalGrid` senza stato esplicito — servira' aggiungere `rememberLazyGridState()`
- **Box wrapper:** da aggiungere attorno alla griglia
- **Note:** Usare overload per `LazyGridState`

### 2.4 CallsStatsScreen

- **File:** `ui/screen/calls/CallsStatsScreen.kt`
- **Stato:** `LazyColumn` senza stato esplicito — servira' aggiungere `rememberLazyListState()`
- **Box wrapper:** da aggiungere. La Column principale diventa un Box con Column + FAB overlay
- **Ristrutturazione:** Wrappare il blocco dal `TimeRangeFilterRow` in giu' in un Box

### 2.5 WordSearchScreen — Lista risultati

- **File:** `ui/screen/wordsearch/WordSearchScreen.kt`
- **Stato:** `LazyColumn` (riga 339) senza stato esplicito — servira' aggiungere `rememberLazyListState()`
- **Box wrapper:** La `Column` principale non e' wrappata in Box — servira' ristrutturazione
- **Note:** FAB visibile solo quando `results.isNotEmpty()`

### 2.6 GroupHistoryScreen

- **File:** `ui/screen/history/GroupHistoryScreen.kt`
- **Stato:** `LazyColumn` con `reverseLayout = true`, senza stato esplicito — servira' aggiungere `rememberLazyListState()`
- **Direzione:** `reverseScroll = true` — l'icona diventa `KeyboardArrowDown`, lo scroll va all'ultimo item (= eventi piu' recenti, in fondo visivamente)
- **Box wrapper:** da aggiungere attorno alla LazyColumn

---

## Step 3 — Verifica

- `./gradlew ktlintCheck compileDebugKotlin`
- Verifica visuale su device: apparizione/scomparsa fluida, posizionamento corretto, nessun conflitto con FAB esistenti

---

## Riepilogo file coinvolti

| File | Azione | Note |
|------|--------|------|
| `ui/component/ScrollToEdgeFab.kt` | NEW | Componente riusabile |
| `ui/screen/chatlist/ChatListScreen.kt` | MODIFY | Aggiunta FAB, offset per Lite Mode |
| `ui/screen/media/MediaStatsScreen.kt` | MODIFY | FAB su lista principale + galleria bottomsheet |
| `ui/screen/calls/CallsStatsScreen.kt` | MODIFY | Aggiunta listState + FAB |
| `ui/screen/wordsearch/WordSearchScreen.kt` | MODIFY | Aggiunta listState + FAB |
| `ui/screen/history/GroupHistoryScreen.kt` | MODIFY | Aggiunta listState + FAB reverse |

---

## Ordine esecuzione consigliato

1. Creare `ScrollToEdgeFab.kt` (componente base)
2. ChatListScreen (il piu' semplice, listState gia' pronto)
3. MediaStatsScreen lista principale (listState gia' pronto)
4. CallsStatsScreen (aggiungere listState)
5. WordSearchScreen (aggiungere listState)
6. GroupHistoryScreen (reverse, aggiungere listState)
7. MediaGallerySheet (gridState, overload griglia)
8. Build finale + verifica ktlint
