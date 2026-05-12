# Piano implementativo - Previsione Traguardi

Questo documento guida l'implementazione completa della feature "Previsione Traguardi" in WhatStat Reborn.

L'obiettivo e permettere all'app di stimare quando verra raggiunto un prossimo traguardo di messaggi, sia nella Dashboard globale sia nel Dettaglio Chat.

La feature deve essere implementata in modo conservativo, senza cambiare il significato delle statistiche esistenti e senza introdurre query lente inutili.

## Regola fondamentale

Non confondere mai questi due valori:

- `currentCount`: numero totale di messaggi all-time, usato per scegliere il traguardo e calcolare il progresso.
- `rateCount`: numero di messaggi nel periodo selezionato, usato solo per calcolare il ritmo giornaliero.

Esempio corretto:

```text
Messaggi totali all-time: 42.000
Messaggi negli ultimi 30 giorni: 900
Ritmo: 30 messaggi/giorno
Target: 50.000
Mancanti: 8.000
Previsione: oggi + 8.000 / 30 giorni
```

Esempio sbagliato:

```text
Messaggi negli ultimi 30 giorni: 900
Target: 1.000
```

Questo e sbagliato perche il filtro temporale non deve ridefinire il traguardo totale della chat o della dashboard.

## Stato attuale della codebase

File rilevanti:

```text
app/src/main/java/com/whatstat/reborn/domain/model/Stats.kt
app/src/main/java/com/whatstat/reborn/data/repository/AnalyticsRepository.kt
app/src/main/java/com/whatstat/reborn/data/db/dao/MessageDao.kt
app/src/main/java/com/whatstat/reborn/data/cache/CacheManager.kt
app/src/main/java/com/whatstat/reborn/ui/screen/dashboard/DashboardScreen.kt
app/src/main/java/com/whatstat/reborn/ui/screen/chatdetail/ChatDetailScreen.kt
app/src/main/java/com/whatstat/reborn/ui/screen/dashboard/DashboardViewModel.kt
app/src/main/java/com/whatstat/reborn/ui/screen/chatdetail/ChatDetailViewModel.kt
app/src/main/res/values/strings.xml
app/src/main/res/values-it/strings.xml
```

Metodi DAO gia disponibili:

```kotlin
messageDao.getTotalMessageCount(startTime: Long? = null, endTime: Long? = null): Int
messageDao.getMessageCountForChat(chatId: Long, startTime: Long? = null, endTime: Long? = null): ChatMessageCount
messageDao.getOldestTimestamp(): Long
messageDao.getFirstMessageTimestamp(chatId: Long, startTime: Long? = null, endTime: Long? = null): Long
messageDao.getLastMessageTimestamp(chatId: Long, startTime: Long? = null, endTime: Long? = null): Long
```

Nota importante:

- `GlobalStats.totalMessages` oggi segue il filtro temporale.
- `ChatStats.totalMessages` oggi segue il filtro temporale.
- La nuova feature deve mantenere questo comportamento.
- Le prediction devono usare internamente anche il totale all-time, senza alterare `totalMessages`.

## Risultato atteso

Al termine dell'implementazione:

- la Dashboard mostra una card "Previsione traguardi" (solo in modalità Root);
- il Dettaglio Chat mostra la stessa card dopo la sezione "Density Stats" (sia Root che Lite);
- ogni card propone 2 traguardi futuri automatici e un terzo target personalizzato inseribile dall'utente;
- l'utente puo selezionare il traguardo con chip orizzontali;
- quando l'utente inserisce il target personalizzato, deve comparire la tastiera numerica;
- la card mostra data stimata, giorni rimanenti, progresso e ritmo usato;
- se il ritmo e zero o non sufficiente, mostra uno stato incalcolabile;
- i filtri temporali influenzano solo il ritmo, non il conteggio corrente;
- Root mode e Lite mode continuano a funzionare tramite repository/DAO condivisi.

## Fase 1 - Modelli dominio

Modificare:

```text
app/src/main/java/com/whatstat/reborn/domain/model/Stats.kt
```

### 1.1 Aggiungere `PredictionConfidence`

Aggiungere vicino agli altri model:

```kotlin
@Serializable
enum class PredictionConfidence {
    NONE,
    LOW,
    MEDIUM,
    HIGH
}
```

### 1.2 Aggiungere `MilestonePrediction`

Aggiungere:

```kotlin
@Serializable
data class MilestonePrediction(
    val targetCount: Long,
    val estimatedDate: Long?,
    val daysRemaining: Long?,
    val progress: Float,
    val dailyRate: Float,
    val confidence: PredictionConfidence
)
```

Campi:

- `targetCount`: traguardo futuro, sempre maggiore del conteggio all-time.
- `estimatedDate`: timestamp in millisecondi, `null` se incalcolabile.
- `daysRemaining`: giorni stimati, `null` se incalcolabile.
- `progress`: valore tra `0f` e `1f`.
- `dailyRate`: messaggi/giorno usati per la previsione.
- `confidence`: confidenza qualitativa da mostrare o usare in UI.

### 1.3 Aggiungere `MilestonePredictionState`

Aggiungere:

```kotlin
@Serializable
data class MilestonePredictionState(
    val currentCount: Long = 0L,
    val dailyRate: Float = 0f,
    val confidence: PredictionConfidence = PredictionConfidence.NONE,
    val suggestedPredictions: List<MilestonePrediction> = emptyList()
)
```

Campi:

- `currentCount`: conteggio all-time usato come base dei traguardi.
- `dailyRate`: ritmo gia calcolato dal repository/calculator.
- `confidence`: confidenza del ritmo.
- `suggestedPredictions`: i 2 target automatici da mostrare come chip.

Questo stato serve anche per il target personalizzato. La UI deve calcolare il target custom in memoria usando `currentCount` e `dailyRate`, senza richiamare repository o DAO.

### 1.4 Estendere `GlobalStats`

Aggiungere un campo con default:

```kotlin
val milestonePredictionState: MilestonePredictionState = MilestonePredictionState()
```

Suggerimento di posizione: dopo `averageMessagesPerDay`.

Non rimuovere o rinominare campi esistenti.

### 1.5 Estendere `ChatStats`

Aggiungere un campo con default:

```kotlin
val milestonePredictionState: MilestonePredictionState = MilestonePredictionState()
```

Suggerimento di posizione: dopo `totalMessages`.

Il default e importante per ridurre il numero di punti da aggiornare e per non rompere costruttori parziali.

## Fase 2 - Calculator puro e testabile

Creare un nuovo file:

```text
app/src/main/java/com/whatstat/reborn/domain/prediction/MilestonePredictionCalculator.kt
```

Contenuto consigliato:

```kotlin
package com.whatstat.reborn.domain.prediction

import com.whatstat.reborn.domain.model.MilestonePrediction
import com.whatstat.reborn.domain.model.PredictionConfidence
import kotlin.math.ceil
import kotlin.math.pow

object MilestonePredictionCalculator {
    private const val DAY_MS = 86_400_000L
    private const val DEFAULT_SUGGESTIONS = 2
    private const val CUSTOM_TARGET_MAX = 999_999_999L

    fun buildState(
        currentCount: Long,
        rateCount: Long,
        rateStartTimestamp: Long?,
        rateEndTimestamp: Long?,
        nowTimestamp: Long = System.currentTimeMillis(),
        maxSuggestions: Int = DEFAULT_SUGGESTIONS
    ): MilestonePredictionState {
        // implementare seguendo le regole sotto
    }

    fun calculateForTarget(
        currentCount: Long,
        targetCount: Long,
        dailyRate: Float,
        confidence: PredictionConfidence,
        nowTimestamp: Long = System.currentTimeMillis()
    ): MilestonePrediction {
        // implementare seguendo le regole sotto
    }

    fun generateTargets(
        currentCount: Long,
        maxTargets: Int = DEFAULT_SUGGESTIONS
    ): List<Long> {
        // implementare seguendo le regole sotto
    }
}
```

### 2.1 Regole per `buildState`

Implementare cosi:

1. Se `currentCount < 0`, trattarlo come `0`.
2. Generare i target con `generateTargets(currentCount)`.
3. Calcolare i giorni del periodo:

```kotlin
val effectiveEnd = rateEndTimestamp ?: nowTimestamp
val effectiveStart = rateStartTimestamp ?: nowTimestamp
val rateDays = ((effectiveEnd - effectiveStart).toDouble() / DAY_MS).coerceAtLeast(1.0)
```

4. Calcolare:

```kotlin
val dailyRate = rateCount.coerceAtLeast(0).toDouble() / rateDays
```

5. Calcolare la confidenza con `confidenceFor(...)`.
6. Creare `suggestedPredictions` chiamando `calculateForTarget(...)` per ogni target generato.
7. Restituire:

```kotlin
MilestonePredictionState(
    currentCount = safeCurrentCount,
    dailyRate = dailyRate.toFloat(),
    confidence = confidence,
    suggestedPredictions = suggestedPredictions
)
```

La lista `suggestedPredictions` deve contenere al massimo 2 elementi. Il terzo traguardo e quello personalizzato inserito dall'utente.

### 2.2 Regole per `calculateForTarget`

Implementare cosi:

1. Se `targetCount <= currentCount`, restituire una prediction senza data:

```kotlin
estimatedDate = null
daysRemaining = null
progress = 1f
dailyRate = dailyRate
confidence = PredictionConfidence.NONE
```

La UI usera questa condizione per mostrare "Traguardo gia raggiunto".

2. Se `dailyRate <= 0f`, restituire prediction con:

```kotlin
estimatedDate = null
daysRemaining = null
progress = currentCount / targetCount
dailyRate = 0f
confidence = PredictionConfidence.NONE
```

3. Altrimenti:

```kotlin
remaining = targetCount - currentCount
days = ceil(remaining / dailyRate).toLong().coerceAtLeast(1L)
```

4. Calcolare sempre la data quando e tecnicamente rappresentabile:

```kotlin
val maxSafeDays = (Long.MAX_VALUE - nowTimestamp) / DAY_MS
val estimatedDate = if (days <= maxSafeDays) {
    nowTimestamp + days * DAY_MS
} else {
    null
}
```

Non introdurre limiti arbitrari tipo "oltre 20 anni". Se la data e calcolabile, va mostrata anche se lontana.

5. Se `estimatedDate == null` per overflow, la UI mostrera uno stato tecnico tipo "Data non disponibile", non "Molto lontano".
6. `progress` deve essere sempre:

```kotlin
(currentCount.toFloat() / targetCount.toFloat()).coerceIn(0f, 1f)
```

7. Non fare query nel calculator.

### 2.3 Regole per `generateTargets`

Per `currentCount < 1_000`, usare step da 500:

```text
500, 1.000, 1.500, 2.000, 2.500...
```

Per `currentCount >= 1_000`, generare "nice numbers" basati su `1, 2, 5 * 10^n`:

```text
1.000
2.000
5.000
10.000
20.000
50.000
100.000
200.000
500.000
1.000.000
...
```

Poi:

- filtrare solo target `> currentCount`;
- ordinare crescente;
- prendere i primi 2;
- evitare duplicati.

Esempi attesi:

```text
currentCount = 0       -> 500, 1000
currentCount = 499     -> 500, 1000
currentCount = 500     -> 1000, 1500
currentCount = 999     -> 1000, 1500
currentCount = 1000    -> 2000, 5000
currentCount = 42000   -> 50000, 100000
currentCount = 100000  -> 200000, 500000
```

Il terzo target non deve essere generato automaticamente: e il target personalizzato dell'utente.

### 2.4 Regole per `confidence`

Implementare una funzione privata:

```kotlin
private fun confidenceFor(rateCount: Long, rateDays: Double, dailyRate: Double): PredictionConfidence
```

Regole semplici:

```text
dailyRate <= 0            -> NONE
rateCount < 10            -> LOW
rateDays < 7              -> LOW
rateCount >= 100 && rateDays >= 30 -> HIGH
altrimenti                -> MEDIUM
```

Non bloccare la previsione per confidenza bassa: la UI puo mostrarla con testo piu prudente.

## Fase 3 - Test unitari del calculator

Creare:

```text
app/src/test/java/com/whatstat/reborn/domain/prediction/MilestonePredictionCalculatorTest.kt
```

Usare JUnit 4, gia presente in `app/build.gradle.kts`.

Test minimi obbligatori:

1. `generateTargets` con `0`, `499`, `500`, `999`, `1000`, `42000`, `100000`.
2. `buildState` con ritmo zero:
   - `estimatedDate == null`;
   - `daysRemaining == null`;
   - `confidence == NONE`.
3. `buildState` con 100 messaggi in 10 giorni e current 900:
   - target 1000;
   - dailyRate circa 10;
   - daysRemaining circa 10.
4. `buildState` con filtro recente:
   - `currentCount = 42000`;
   - `rateCount = 900`;
   - periodo 30 giorni;
   - primo target 50000;
   - non deve proporre 1000 o 1500.
5. `calculateForTarget` con target personalizzato valido:
   - `currentCount = 42000`;
   - `targetCount = 123456`;
   - `dailyRate = 30f`;
   - nessuna query o dipendenza esterna;
   - `estimatedDate != null`.
6. `calculateForTarget` con target `<= currentCount`:
   - `estimatedDate == null`;
   - `daysRemaining == null`;
   - `progress == 1f`.
7. `calculateForTarget` con data molto futura ma rappresentabile:
   - deve restituire `estimatedDate != null`;
   - non deve sostituire la data con "Molto lontano".
8. `progress` sempre tra `0f` e `1f`.

Comando di verifica:

```powershell
.\gradlew.bat testDebugUnitTest
```

Se nel progetto la variante non e `Debug`, usare:

```powershell
.\gradlew.bat test
```

## Fase 4 - Integrazione in `AnalyticsRepository`

Modificare:

```text
app/src/main/java/com/whatstat/reborn/data/repository/AnalyticsRepository.kt
```

Aggiungere import:

```kotlin
import com.whatstat.reborn.domain.prediction.MilestonePredictionCalculator
```

### 4.1 Helper privato per range rate

Aggiungere una funzione privata o locale per calcolare start/end del periodo usato per il ritmo:

```kotlin
private fun resolveRateWindow(
    startTime: Long?,
    endTime: Long?,
    fallbackStart: Long,
    now: Long
): Pair<Long?, Long?> {
    val rateStart = startTime ?: fallbackStart.takeIf { it > 0L }
    val rateEnd = endTime ?: now
    return rateStart to rateEnd
}
```

Questa funzione serve per:

- `ALL_TIME`: start = primo messaggio, end = ora;
- filtro temporale: start/end = filtro;
- custom con end nullo: end = ora.

### 4.2 Dashboard: integrare in `getQuickGlobalStats`

Dentro `getQuickGlobalStats(...)`:

1. Calcolare `now` una sola volta all'inizio del blocco IO:

```kotlin
val now = System.currentTimeMillis()
```

2. Nel caso cache `ALL_TIME`, non restituire direttamente il cached stats. Enrich prima:

```kotlin
val predictionState = MilestonePredictionCalculator.buildState(
    currentCount = cached.totalMessages.toLong(),
    rateCount = cached.totalMessages.toLong(),
    rateStartTimestamp = cached.oldestMessageDate.takeIf { it > 0L },
    rateEndTimestamp = now,
    nowTimestamp = now
)
return@withContext cached.copy(milestonePredictionState = predictionState)
```

3. Nel blocco normale, aggiungere una query per il totale all-time solo quando il filtro e attivo:

```kotlin
val dAllTimeTotalMessages = async {
    if (startTime == null && endTime == null) {
        null
    } else {
        messageDao.getTotalMessageCount(null, null)
    }
}
```

4. Dopo aver ottenuto `totalMessages` e `oldestTs`, calcolare:

```kotlin
val currentCountAllTime = dAllTimeTotalMessages.await() ?: totalMessages
val (rateStart, rateEnd) = resolveRateWindow(
    startTime = startTime,
    endTime = endTime,
    fallbackStart = oldestTs,
    now = now
)
val predictionState = MilestonePredictionCalculator.buildState(
    currentCount = currentCountAllTime.toLong(),
    rateCount = totalMessages.toLong(),
    rateStartTimestamp = rateStart,
    rateEndTimestamp = rateEnd,
    nowTimestamp = now
)
```

5. Passare `milestonePredictionState = predictionState` nel costruttore `GlobalStats`.

Attenzione:

- `totalMessages` nel `GlobalStats` deve restare il conteggio filtrato.
- `currentCountAllTime` deve essere usato solo per le prediction.

### 4.3 Chat Detail: integrare in `getQuickChatStats`

Dentro `getQuickChatStats(...)`:

1. Calcolare `now` una sola volta.
2. Nel caso cache `ALL_TIME`, enrich prima di restituire:

```kotlin
val predictionState = MilestonePredictionCalculator.buildState(
    currentCount = cached.totalMessages.toLong(),
    rateCount = cached.totalMessages.toLong(),
    rateStartTimestamp = cached.firstMessageDate.takeIf { it > 0L },
    rateEndTimestamp = now,
    nowTimestamp = now
)
return@withContext cached.copy(milestonePredictionState = predictionState)
```

3. Nel blocco normale, aggiungere:

```kotlin
val dAllTimeMsgCount = async {
    if (startTime == null && endTime == null) {
        null
    } else {
        messageDao.getMessageCountForChat(chatId, null, null)
    }
}
val dAllTimeFirstTs = async {
    if (startTime == null && endTime == null) {
        null
    } else {
        messageDao.getFirstMessageTimestamp(chatId, null, null)
    }
}
```

4. Dopo `msgCount`, `dFirstTs.await()` e `dLastTs.await()`, calcolare:

```kotlin
val firstTs = dFirstTs.await()
val lastTs = dLastTs.await()
val allTimeCount = dAllTimeMsgCount.await()?.total ?: msgCount.total
val allTimeFirstTs = dAllTimeFirstTs.await() ?: firstTs
val (rateStart, rateEnd) = resolveRateWindow(
    startTime = startTime,
    endTime = endTime,
    fallbackStart = allTimeFirstTs,
    now = now
)
val predictionState = MilestonePredictionCalculator.buildState(
    currentCount = allTimeCount.toLong(),
    rateCount = msgCount.total.toLong(),
    rateStartTimestamp = rateStart,
    rateEndTimestamp = rateEnd,
    nowTimestamp = now
)
```

5. Usare `firstTs` e `lastTs` nel costruttore, invece di chiamare `await()` una seconda volta.
6. Passare `milestonePredictionState = predictionState` nel costruttore `ChatStats`.

Attenzione:

- Il `ChatStats.totalMessages` deve rimanere filtrato.
- La prediction deve usare `allTimeCount`.
- Non calcolare la media da `firstMessageDate` a `lastMessageDate`; per `ALL_TIME` usare da primo messaggio a `now`, cosi le chat inattive non vengono sovrastimate.

## Fase 5 - Cache

Modificare solo se necessario:

```text
app/src/main/java/com/whatstat/reborn/data/cache/CacheManager.kt
```

Scelta consigliata:

- non salvare `milestonePredictionState` nei model cached;
- lasciare `CachedGlobalStats` e `CachedQuickChatStats` senza questo campo;
- ricalcolare sempre le prediction in `AnalyticsRepository` dopo aver letto la cache.

Motivo:

- le prediction dipendono da `now`;
- una data prevista salvata in cache diventa rapidamente vecchia;
- il codice cache usa `ignoreUnknownKeys`, quindi aggiungere campi al domain model non obbliga a serializzarli.

Verifica importante:

- se il compiler richiede nuovi argomenti nei costruttori `GlobalStats(...)` o `ChatStats(...)` dentro `CacheManager`, aggiungere `milestonePredictionState = MilestonePredictionState()` oppure affidarsi al default se la posizione del campo lo consente.

## Fase 6 - Componente UI riutilizzabile

Creare:

```text
app/src/main/java/com/whatstat/reborn/ui/component/MilestonePredictionCard.kt
```

### 6.1 API del composable

Creare:

```kotlin
@Composable
fun MilestonePredictionCard(
    state: MilestonePredictionState,
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.prediction_title),
    periodLabel: String? = null
)
```

Parametri:

- `state`: stato da `GlobalStats` o `ChatStats`, con target suggeriti e dati necessari per il target personalizzato.
- `title`: default localizzato.
- `periodLabel`: testo facoltativo tipo "Basato sugli ultimi 30 giorni".

### 6.2 Stato interno

Usare:

```kotlin
var selectedMode by remember(state.currentCount) { mutableStateOf(PredictionSelection.Suggested(0)) }
var customTargetText by rememberSaveable(state.currentCount) { mutableStateOf("") }
```

Aggiungere un tipo privato nel file UI:

```kotlin
private sealed interface PredictionSelection {
    data class Suggested(val index: Int) : PredictionSelection
    data object Custom : PredictionSelection
}
```

Il target selezionato si risolve cosi:

- se `Suggested(index)`, usare `state.suggestedPredictions.getOrNull(index)`;
- se `Custom`, parsare `customTargetText` e chiamare `MilestonePredictionCalculator.calculateForTarget(...)`;
- non chiamare mai repository, ViewModel o DAO quando cambia `customTargetText`.

Se `state.suggestedPredictions` e vuota e `state.dailyRate <= 0f`:

- mostrare card con titolo;
- mostrare `R.string.prediction_unavailable`;
- mostrare comunque il chip "Personalizzato" se si vuole permettere all'utente di inserire un target, ma la previsione restera incalcolabile finche il ritmo e zero.

### 6.3 Layout

Usare lo stile esistente:

- `Card`;
- `CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))`;
- `RoundedCornerShape(16.dp)`;
- padding interno `20.dp`;
- `IconButton` con `Icons.Outlined.Info` in alto a destra per spiegazioni;
- `FilterChip` per selezionare target;
- `OutlinedTextField` per il target personalizzato;
- `LinearProgressIndicator` per progresso.

Non creare una nuova estetica. La card deve sembrare parte della UI corrente.

### 6.4 Contenuto della card

Mostrare:

1. Titolo: "Previsione traguardi".
2. Chip target:

```text
50k
100k
Personalizzato
```

3. Campo target personalizzato:

- mostrarlo solo quando e selezionato il chip "Personalizzato";
- accettare solo cifre;
- rifiutare valori `<= state.currentCount`;
- rifiutare valori maggiori di `CUSTOM_TARGET_MAX`, cioe `999.999.999`;
- usare tastiera numerica:

```kotlin
keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
```

Sanitizzazione consigliata:

```kotlin
val sanitized = input.filter { it.isDigit() }.take(9)
customTargetText = sanitized
```

4. Target completo:

```text
50.000 messaggi
```

5. Data stimata:

- se `estimatedDate != null`: formato mese anno, esempio "Ottobre 2026";
- se `estimatedDate == null && daysRemaining != null`: "Data non disponibile";
- se `daysRemaining == null`: "Incalcolabile".

Non mostrare "Molto lontano": se la data e calcolabile, va mostrata anche quando e molto futura.

6. Giorni rimanenti:

- mostrarli solo se `daysRemaining != null`;
- per valori molto grandi, si puo mostrare anni approssimati.

7. Ritmo:

```text
Basato su 30,0 messaggi/giorno
```

8. Confidenza:

- `LOW`: mostrare testo prudente, ad esempio "Stima poco stabile";
- `MEDIUM`/`HIGH`: opzionale;
- `NONE`: stato incalcolabile.

### 6.5 Formatting locale

Usare:

```kotlin
NumberFormat.getNumberInstance()
```

per conteggi grandi.

Usare:

```kotlin
SimpleDateFormat("MMMM yyyy", Locale.getDefault())
```

per la data.

Capitalizzare il mese solo se necessario.

### 6.6 Accessibilita

La progress bar deve avere semantics:

```kotlin
Modifier.semantics {
    progressBarRangeInfo = ProgressBarRangeInfo(
        current = selected.progress,
        range = 0f..1f
    )
}
```

I chip devono avere testo chiaro.

## Fase 7 - Stringhe localizzate

Modificare:

```text
app/src/main/res/values/strings.xml
app/src/main/res/values-it/strings.xml
```

### 7.1 Inglese fallback

In `values/strings.xml` aggiungere:

```xml
<string name="prediction_title">Milestone prediction</string>
<string name="prediction_unavailable">Not enough activity to estimate a date</string>
<string name="prediction_uncalculable">Uncalculable</string>
<string name="prediction_date_unavailable">Date unavailable</string>
<string name="prediction_custom_target">Custom</string>
<string name="prediction_custom_target_label">Target messages</string>
<string name="prediction_custom_reached">This milestone has already been reached</string>
<string name="prediction_custom_invalid">Enter a positive whole number</string>
<string name="prediction_custom_too_large">Choose a number below 1,000,000,000</string>
<string name="prediction_target_messages">%1$s messages</string>
<string name="prediction_days_remaining">%1$d days remaining</string>
<string name="prediction_years_remaining">About %1$d years remaining</string>
<string name="prediction_rate">Based on %1$.1f messages/day</string>
<string name="prediction_low_confidence">Estimate based on limited activity</string>
<string name="prediction_info_title">Prediction info</string>
<string name="prediction_info_content">This section estimates when you will reach a specific total message milestone.\n\nThe calculation is based on your average daily rate. If you apply a time filter, the prediction will use the rate from that specific period to adapt to your recent changes.\n\nThe message count and progress toward the milestone always refer to the chat\'s all-time total.</string>
<string name="prediction_info_button">I understand</string>
```

### 7.2 Italiano

In `values-it/strings.xml` aggiungere:

```xml
<string name="prediction_title">Previsione traguardi</string>
<string name="prediction_unavailable">Attivita insufficiente per stimare una data</string>
<string name="prediction_uncalculable">Incalcolabile</string>
<string name="prediction_date_unavailable">Data non disponibile</string>
<string name="prediction_custom_target">Personalizzato</string>
<string name="prediction_custom_target_label">Messaggi target</string>
<string name="prediction_custom_reached">Questo traguardo e gia stato raggiunto</string>
<string name="prediction_custom_invalid">Inserisci un numero intero positivo</string>
<string name="prediction_custom_too_large">Scegli un numero sotto 1.000.000.000</string>
<string name="prediction_target_messages">%1$s messaggi</string>
<string name="prediction_days_remaining">%1$d giorni rimanenti</string>
<string name="prediction_years_remaining">Circa %1$d anni rimanenti</string>
<string name="prediction_rate">Basato su %1$.1f messaggi/giorno</string>
<string name="prediction_low_confidence">Stima basata su poca attivita</string>
<string name="prediction_info_title">Informazioni previsione</string>
<string name="prediction_info_content">Questa sezione stima quando raggiungerai un determinato traguardo di messaggi totali.\n\nIl calcolo si basa sul tuo ritmo medio giornaliero. Se applichi un filtro temporale, la previsione utilizzerà il ritmo di quel periodo specifico per adattarsi ai tuoi cambiamenti recenti.\n\nIl conteggio dei messaggi e il progresso verso il traguardo restano sempre riferiti al totale storico della chat.</string>
<string name="prediction_info_button">Ho capito</string>
```

Non usare nomi diversi tra `values` e `values-it`.
**Importante**: Le nuove stringhe devono essere tradotte e inserite in tutti i file `strings.xml` supportati dal progetto (de, es, fr, hi, pt-rBR, tr) per mantenere la coerenza dell'internazionalizzazione.

## Fase 8 - Inserimento nella Chat Detail

Modificare:

```text
app/src/main/java/com/whatstat/reborn/ui/screen/chatdetail/ChatDetailScreen.kt
```

### 8.1 Import

Aggiungere:

```kotlin
import com.whatstat.reborn.ui.component.MilestonePredictionCard
```

### 8.2 Posizione

Inserire un nuovo `item` subito dopo il blocco "Density Stats" e prima della sezione "Distribuzione messaggi".

Esempio:

```kotlin
item {
    MilestonePredictionCard(
        state = s.milestonePredictionState,
        periodLabel = selectedRange.label
    )
}
```

Se `selectedRange.label` non e adatto per tutte le lingue, passare `null` nella prima versione. La card e gia utile con il ritmo giornaliero.

### 8.3 Verifica ViewModel

`ChatDetailViewModel` usa `quickStats.copy(...)` per aggiungere avatar e poi slow stats.

Non serve aggiungere codice specifico se `milestonePredictionState` e un campo di `ChatStats`, perche `copy(...)` conserva i campi non sovrascritti.

Controllare comunque che nessun `copy(...)` imposti accidentalmente `milestonePredictionState = MilestonePredictionState()`.

## Fase 9 - Inserimento nella Dashboard

Modificare:

```text
app/src/main/java/com/whatstat/reborn/ui/screen/dashboard/DashboardScreen.kt
```

### 9.1 Import

Aggiungere:

```kotlin
import com.whatstat.reborn.ui.component.MilestonePredictionCard
```

### 9.2 Posizione (Solo Root Mode)

La card deve essere visibile solo se `!isLiteMode`. Inserirla nella parte alta della lista, subito dopo le card principali:

```text
Messaggi totali
Media/giorno
Chat totali
Contatti
```

La posizione consigliata e dopo il blocco che mostra `stat_total_messages` e `stat_avg_per_day`, prima dei grafici.

Esempio:

```kotlin
item {
    MilestonePredictionCard(
        state = s.milestonePredictionState,
        periodLabel = selectedRange.label
    )
}
```

Come per Chat Detail, se `selectedRange.label` non e coerente con la localizzazione, passare `null`.

## Fase 10 - Verifiche manuali

Eseguire questi scenari:

### 10.1 Dashboard all-time

Filtro: Tutto.

Verificare:

- la card appare;
- i target sono maggiori del totale messaggi;
- la progress bar e coerente;
- la data non e nel passato se il ritmo e positivo.

### 10.2 Dashboard ultimi 30 giorni

Filtro: 30 giorni.

Verificare:

- le statistiche principali restano filtrate;
- i target restano basati sul totale all-time;
- il ritmo cambia rispetto ad all-time.

### 10.3 Chat detail all-time

Aprire una chat con molti messaggi.

Verificare:

- target futuri corretti;
- data stimata visibile;
- nessun crash quando arrivano le slow stats.

### 10.4 Chat con pochi messaggi

Aprire una chat con 0, 1 o pochissimi messaggi.

Verificare:

- niente crash;
- stato "Incalcolabile" o confidenza bassa;
- nessun target gia raggiunto.

### 10.5 Filtro senza messaggi

Applicare un filtro temporale in cui non ci sono messaggi.

Verificare:

- target basati sul totale all-time;
- dailyRate = 0;
- stato incalcolabile.

### 10.6 Lite mode

Importare o usare dati Lite.

Verificare:

- la card appare anche in Lite mode;
- non ci sono chiamate a codice specifico Root;
- le prediction cambiano dopo nuovo import se la cache viene aggiornata.

## Fase 11 - Build e test

Eseguire:

```powershell
.\gradlew.bat testDebugUnitTest
```

Poi:

```powershell
.\gradlew.bat assembleDebug
```

Se `testDebugUnitTest` non esiste, eseguire:

```powershell
.\gradlew.bat test
```

Correggere tutti gli errori di compilazione prima di considerare finita la feature.

## Errori da evitare

### Errore 1 - Usare il conteggio filtrato come conteggio corrente

Sbagliato:

```kotlin
currentCount = totalMessages // quando totalMessages e filtrato
```

Corretto:

```kotlin
currentCount = allTimeTotalMessages
rateCount = filteredTotalMessages
```

### Errore 2 - Salvare le prediction in cache

Sbagliato:

```kotlin
CachedGlobalStats(milestonePredictionState = ...)
```

Corretto:

```kotlin
// Cache salva solo statistiche stabili.
// Repository ricalcola le prediction al momento della lettura.
```

### Errore 3 - Calcolare il ritmo fino all'ultimo messaggio

Sbagliato:

```kotlin
days = lastMessageDate - firstMessageDate
```

Corretto:

```kotlin
days = now - firstMessageDate
```

Motivo: una chat ferma da mesi non deve sembrare ancora attiva.

### Errore 4 - Proporre target gia raggiunti

Sbagliato:

```text
current = 10.000 -> target = 10.000
```

Corretto:

```text
current = 10.000 -> target = 20.000
```

### Errore 5 - Creare UI duplicata in Dashboard e Chat Detail

Sbagliato:

```text
PredictionCard privata in DashboardScreen
PredictionCard privata diversa in ChatDetailScreen
```

Corretto:

```text
ui/component/MilestonePredictionCard.kt
```

### Errore 6 - Fare query mentre l'utente digita il target custom

Sbagliato:

```kotlin
onValueChange = {
    viewModel.recalculatePredictionFromRepository(it)
}
```

Corretto:

```kotlin
onValueChange = {
    customTargetText = sanitizeDigits(it)
    // calcolo solo in memoria con MilestonePredictionCalculator.calculateForTarget(...)
}
```

Il target personalizzato non deve mai generare query SQL o ricaricamenti delle statistiche.

### Errore 7 - Nascondere date future calcolabili

Sbagliato:

```text
days > 20 anni -> "Molto lontano"
```

Corretto:

```text
se estimatedDate e calcolabile -> mostrare mese e anno
se estimatedDate non e rappresentabile per overflow -> "Data non disponibile"
```

## Checklist finale

- [ ] `PredictionConfidence` creato.
- [ ] `MilestonePrediction` creato.
- [ ] `MilestonePredictionState` creato.
- [ ] `GlobalStats` contiene `milestonePredictionState`.
- [ ] `ChatStats` contiene `milestonePredictionState`.
- [ ] `MilestonePredictionCalculator` creato.
- [ ] `MilestonePredictionCalculator.calculateForTarget(...)` creato per il target personalizzato.
- [ ] Test unitari del calculator aggiunti.
- [ ] `getQuickGlobalStats` arricchisce stats e cache con prediction ricalcolate.
- [ ] `getQuickChatStats` arricchisce stats e cache con prediction ricalcolate.
- [ ] Il conteggio all-time e separato dal conteggio filtrato.
- [ ] `MilestonePredictionCard` creata in `ui/component`.
- [ ] La card mostra 2 target automatici e un chip "Personalizzato".
- [ ] Il campo custom accetta solo numeri interi positivi.
- [ ] Il campo custom usa tastiera numerica.
- [ ] Il target custom viene calcolato in memoria, senza query.
- [ ] Le date future calcolabili vengono mostrate anche se molto lontane.
- [ ] Stringhe aggiunte in `values` e `values-it`.
- [ ] Card aggiunta in Dashboard.
- [ ] Card aggiunta in Chat Detail.
- [ ] Test unitari eseguiti.
- [ ] `assembleDebug` eseguito.
- [ ] Verificati filtri, chat vuote, Lite mode e cache.

## Criterio di accettazione

La feature e accettabile solo se, con un filtro attivo, il ritmo cambia ma i target restano basati sul totale all-time.

Questo e il comportamento piu importante da preservare.
