# Piano: Card Unificata "Parole / Emoji / Sticker" con ScrollableTabRow

**Data**: 17 aprile 2026  
**Stato**: Pianificazione  
**Scope**: `ChatDetailScreen.kt`, `DashboardScreen.kt`

---

## Obiettivo

Sostituire le 3 `SectionCard` separate ("Parole più usate", "Emoji più usate", "Sticker più usati") con **una singola card** che usa il pattern `ScrollableTabRow` già in uso in `ActivityRecapCard` (`ActivityCard.kt`).

Il risultato visivo sarà identico al selettore distribuzione oraria/settimanale/giornaliera/mensile/annuale.

---

## Pattern di riferimento (ActivityCard.kt)

```kotlin
var selectedTab by remember { mutableIntStateOf(0) }
val tabs = listOf("Oraria", "Settimanale", "Giornaliera", "Mensile", "Annuale")

ScrollableTabRow(
    selectedTabIndex = selectedTab,
    containerColor = Color.Transparent,
    contentColor = MaterialTheme.colorScheme.primary,
    edgePadding = 16.dp,
    divider = {},
    indicator = { tabPositions ->
        if (selectedTab < tabPositions.size) {
            TabRowDefaults.SecondaryIndicator(
                modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
) {
    tabs.forEachIndexed { index, title ->
        Tab(
            selected = selectedTab == index,
            onClick = { selectedTab = index },
            text = { Text(title, fontSize = 12.sp) }
        )
    }
}
// Sotto la tab row: contenuto condizionale sul valore di selectedTab
```

---

## 1. ChatDetailScreen.kt

### 1.1 Import da aggiungere

```kotlin
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.unit.sp
```

### 1.2 Spostamento `topStickers` al top level

`topStickers` è attualmente raccolto *dentro* l'`item {}` della sezione sticker (riga ~550):
```kotlin
val topStickers by viewModel.topStickers.collectAsState()
```
Va spostato assieme agli altri `collectAsState()` al top della funzione composable (riga ~96-117), affianco a `stickerViewMode`, `topStickerLimit`, ecc.

### 1.3 Sostituzione dei 3 `item {}` con uno solo

Rimuovere i 3 blocchi `item { SectionCard("Parole...") }`, `item { SectionCard("Emoji...") }`, `item { SectionCard("Sticker...") }` e sostituirli con:

```kotlin
item {
    val tabs = listOf("Parole", "Emoji", "Sticker")
    var selectedTab by remember { mutableIntStateOf(0) }

    SectionCard(
        title = "Analisi testi",
        trailingContent = {
            // Chips limite: cambiano a seconda del tab selezionato
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val limits = when (selectedTab) {
                    0 -> listOf(5, 10, 20, 30, 50)
                    1 -> listOf(3, 5, 10, 20, 30, 50)
                    else -> listOf(5, 10, 20, 30, 50)
                }
                val currentLimit = when (selectedTab) {
                    0 -> topWordsLimit
                    1 -> topEmojiLimit
                    else -> topStickerLimit
                }
                val onLimitChange: (Int) -> Unit = when (selectedTab) {
                    0 -> { l -> viewModel.setTopWordsLimit(l) }
                    1 -> { l -> viewModel.setTopEmojiLimit(l) }
                    else -> { l -> viewModel.setTopStickerLimit(l) }
                }
                limits.forEach { limit ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (currentLimit == limit) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                            .clickable { onLimitChange(limit) }
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            limit.toString(),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (currentLimit == limit) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    ) {
        // ── Tab Row ──────────────────────────────────────────────────
        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.primary,
            edgePadding = 0.dp,
            divider = {},
            modifier = Modifier.padding(bottom = 12.dp),
            indicator = { tabPositions ->
                if (selectedTab < tabPositions.size) {
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title, fontSize = 12.sp) }
                )
            }
        }

        // ── Switcher Tutti / Per Persona ─────────────────────────────
        val currentViewModeIsIndividual = when (selectedTab) {
            0 -> wordViewMode == ChatDetailViewModel.WordViewMode.INDIVIDUAL
            1 -> emojiViewMode == ChatDetailViewModel.EmojiViewMode.INDIVIDUAL
            else -> stickerViewMode == ChatDetailViewModel.StickerViewMode.INDIVIDUAL
        }
        val onSwitchMode: (Boolean) -> Unit = { toIndividual ->
            when (selectedTab) {
                0 -> viewModel.setWordViewMode(
                    if (toIndividual) ChatDetailViewModel.WordViewMode.INDIVIDUAL
                    else ChatDetailViewModel.WordViewMode.AGGREGATED
                )
                1 -> viewModel.setEmojiViewMode(
                    if (toIndividual) ChatDetailViewModel.EmojiViewMode.INDIVIDUAL
                    else ChatDetailViewModel.EmojiViewMode.AGGREGATED
                )
                else -> viewModel.setStickerViewMode(
                    if (toIndividual) ChatDetailViewModel.StickerViewMode.INDIVIDUAL
                    else ChatDetailViewModel.StickerViewMode.AGGREGATED
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf(false to "Tutti", true to "Per Persona").forEach { (isIndividual, label) ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (currentViewModeIsIndividual == isIndividual) MaterialTheme.colorScheme.primary
                            else Color.Transparent
                        )
                        .clickable { onSwitchMode(isIndividual) }
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (currentViewModeIsIndividual == isIndividual) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // ── Chips mittente (solo in modalità INDIVIDUAL) ─────────────
        if (currentViewModeIsIndividual) {
            stats?.let { s ->
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    items(s.senderBreakdown) { sender ->
                        val isSelected = when (selectedTab) {
                            0 -> selectedSenderWords?.jid == sender.jid
                            1 -> selectedSenderEmojis?.jid == sender.jid
                            else -> selectedSenderStickers?.jid == sender.jid
                        }
                        val onSelect: () -> Unit = when (selectedTab) {
                            0 -> { { viewModel.setSelectedSenderForWords(sender) } }
                            1 -> { { viewModel.setSelectedSenderForEmojis(sender) } }
                            else -> { { viewModel.setSelectedSenderForStickers(sender) } }
                        }
                        FilterChip(
                            selected = isSelected,
                            onClick = onSelect,
                            label = { Text(sender.displayName) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }
            }
        }

        // ── Contenuto del tab ────────────────────────────────────────
        when (selectedTab) {
            0 -> { /* Parole */
                val currentWords = if (wordViewMode == ChatDetailViewModel.WordViewMode.AGGREGATED)
                    s.topWords.take(topWordsLimit.toInt()) else senderTopWords
                when {
                    (wordViewMode == ChatDetailViewModel.WordViewMode.INDIVIDUAL && isSenderWordsLoading) ||
                    (wordViewMode == ChatDetailViewModel.WordViewMode.AGGREGATED && isLoadingSlow && currentWords.isEmpty()) ->
                        LoadingSection(progress = if (wordViewMode == ChatDetailViewModel.WordViewMode.AGGREGATED) progressSlow else null)
                    currentWords.isEmpty() ->
                        Text("Nessuna parola trovata", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 24.dp))
                    else ->
                        InteractiveWordCloud(words = currentWords, onWordClick = { word, count ->
                            tooltipTitle = word; tooltipValue = "%,d volte".format(count); showTooltip = true
                        })
                }
            }
            1 -> { /* Emoji */
                val currentEmojis = if (emojiViewMode == ChatDetailViewModel.EmojiViewMode.AGGREGATED)
                    s.topEmojis.take(topEmojiLimit.toInt()) else senderTopEmojis
                when {
                    (emojiViewMode == ChatDetailViewModel.EmojiViewMode.INDIVIDUAL && isSenderEmojisLoading) ||
                    (emojiViewMode == ChatDetailViewModel.EmojiViewMode.AGGREGATED && isLoadingSlow && currentEmojis.isEmpty()) ->
                        LoadingSection(progress = if (emojiViewMode == ChatDetailViewModel.EmojiViewMode.AGGREGATED) progressSlow else null)
                    currentEmojis.isEmpty() ->
                        Text("Nessuna emoji trovata", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 24.dp))
                    else ->
                        InteractiveEmojiCloud(emojis = currentEmojis, onEmojiClick = { emoji, count ->
                            tooltipTitle = emoji; tooltipValue = "%,d volte".format(count); showTooltip = true
                        })
                }
            }
            2 -> { /* Sticker */
                val currentStickers = if (stickerViewMode == ChatDetailViewModel.StickerViewMode.AGGREGATED)
                    topStickers.take(topStickerLimit) else senderTopStickers
                when {
                    isStickerLoading ->
                        LoadingSection()
                    currentStickers.isEmpty() ->
                        Text("Nessuno sticker trovato", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 24.dp))
                    else ->
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            items(currentStickers) { sticker ->
                                StickerItem(fileName = sticker.fileName, count = sticker.count)
                            }
                        }
                }
            }
        }
    }
}
```

> **Nota**: il blocco `when (selectedTab)` per il contenuto presuppone di essere dentro `stats?.let { s -> ... }` (come già avviene per il blocco parole). La variabile `s` sarà disponibile.

---

## 2. DashboardScreen.kt

### 2.1 Import da aggiungere

Stessi import Tab di ChatDetailScreen (se non già presenti):
```kotlin
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.unit.sp
```

### 2.2 Sostituzione dei 3 `item {}` con uno solo

I 3 blocchi separati "Parole più usate", "Emoji più usate", "Sticker più usati" vengono sostituiti da:

```kotlin
item {
    val tabs = listOf("Parole", "Emoji", "Sticker")
    var selectedTab by remember { mutableIntStateOf(0) }

    SectionCard(title = "Analisi testi") {
        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.primary,
            edgePadding = 0.dp,
            divider = {},
            modifier = Modifier.padding(bottom = 12.dp),
            indicator = { tabPositions ->
                if (selectedTab < tabPositions.size) {
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title, fontSize = 12.sp) }
                )
            }
        }

        when (selectedTab) {
            0 -> InteractiveWordCloud(
                    words = globalStats.topWords.take(20),
                    onWordClick = { _, _ -> }
                 )
            1 -> InteractiveEmojiCloud(
                    emojis = globalStats.topEmojis.take(20),
                    onEmojiClick = { _, _ -> }
                 )
            2 -> LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                 ) {
                     items(globalStats.topStickers.take(20)) { sticker ->
                         DashboardStickerItem(fileName = sticker.fileName, count = sticker.count)
                     }
                 }
        }
    }
}
```

> **Nota**: nella Dashboard non c'è switcher Tutti/Per Persona né chips limite — la vista è sempre aggregata e fissa a 20 elementi, come avveniva nelle 3 card separate.

---

## 3. File da modificare

| File | Tipo modifica |
|---|---|
| `app/src/main/java/com/whatstat/reborn/ui/screen/chatdetail/ChatDetailScreen.kt` | Spostare `topStickers` al top-level; rimuovere 3 `item {}` (righe ~370–658); aggiungere 1 `item {}` unificato; aggiungere 5 import |
| `app/src/main/java/com/whatstat/reborn/ui/screen/dashboard/DashboardScreen.kt` | Rimuovere 3 `item {}` separati; aggiungere 1 `item {}` unificato; aggiungere 5 import |

---

## 4. Non sono necessarie modifiche a

- `ChatDetailViewModel.kt` — gli state flow esistenti rimangono invariati
- `DashboardViewModel.kt` — nessuna modifica
- `AnalyticsRepository.kt` — nessuna modifica
- Qualsiasi DAO o modello dati

---

## 5. Rischi / Attenzioni

| Rischio | Mitigazione |
|---|---|
| `s` (da `stats?.let { s ->`) non disponibile nel blocco parole | Il blocco parole è già dentro `stats?.let { s -> ... }`; la card unificata va messa allo stesso livello |
| `topStickers` raccolto inside item | Spostarlo al top-level insieme agli altri `collectAsState()` prima di procedere |
| Import `tabIndicatorOffset` — l'estensione può stare su `TabRowDefaults` o su `Modifier` a seconda della versione M3 | Verificare che la funzione esista; `ActivityCard.kt` già la usa, quindi l'import è già nel gradle |
| La Dashboard usa `globalStats` che è nullable | Il blocco è già dentro un `globalStats?.let { ... }` — mantenerlo |
