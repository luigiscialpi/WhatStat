# WhatStat - Piano di sviluppo: feature di engagement (Highlights e Badge)

Data: 2026-09-24
Stato: pronto per l'implementazione. Release A completa; B dettagliata; C e Restyling da rifinire.

Questo documento sostituisce integralmente la versione precedente dello stesso file (Insight/Recap/Vico). E' scritto per un esecutore che NON conosce il codice: ogni blocco di codice o istruzione va copiato cosi' com'e', senza reinterpretarlo.

## Indice

0. Regole per chi esegue
1. Decisioni prese
2. Fatti verificati / trappole
3. Prerequisito bloccante
4. Release A (fondamenta: Highlights + Badge, dati gia' disponibili)
5. Release B (metriche di risposta, card su persone, condivisione)
6. Release C (grafico evoluzione, pulizia Vico)
7. Restyling grafici esistenti
8. Fuori scope
9. Checklist di chiusura

---

## 0. Regole per chi esegue

- Se un simbolo citato qui (classe, funzione, file, riga) non si trova nel codice, FERMATI e chiedi. Non inventare un'alternativa.
- I numeri di riga in questo piano sono approssimativi: cerca sempre per nome di simbolo, non fidarti solo del numero.
- I blocchi marcati `[VERIFICATO]` sono stati compilati, testati (19 test JUnit) e passano ktlint in un progetto isolato con Kotlin 2.0.21 / ktlint 1.0.1. Copiali identici, non modificarli "a occhio".
- Gli altri blocchi di codice sono riferimento completo ma non compilato: possono contenere un piccolo refuso, verificali con la build.
- Regole ktlint valide per tutto il piano (1.0.1, stile `ktlint_official`):
  - Funzione/costruttore con 2 o piu' parametri: un parametro per riga, con virgola finale. Un solo parametro resta su una riga.
  - Il lato destro di una assegnazione multi-riga va a capo subito dopo `=`.
  - Layout `@Inject constructor` con il corpo indentato di 8 spazi (vedi esempio in BadgeUnlockStore, A5).
  - Riga massima 140 caratteri. Import non usati fanno fallire la build.
  - Import in ordine alfabetico ASCII, con `java.*`/`javax.*` in fondo. Niente wildcard.
  - `when` con branch multi-riga usa sempre le graffe.
- Dopo OGNI task che tocca Kotlin/Compose, esegui in ordine e considera valido solo l'esito di Gradle (non fidarti del solo language server dell'IDE):
  1. `./gradlew ktlintFormat`
  2. `./gradlew ktlintCheck`
  3. `./gradlew :app:compileDebugKotlin :app:compileReleaseKotlin`
  4. `./gradlew :app:testDebugUnitTest`
- Non fare mai commit. Non toccare git in nessun modo.
- Non toccare mai: il costruttore di `AnalyticsRepository`, le funzioni `getAverageResponseTime` / `getConversationStarters` gia' esistenti, `di/AppModule.kt`.
- Regole per le stringhe utente: vedi sezione A8.

---

## 1. Decisioni prese

Tabella delle scelte di prodotto/architettura gia' fatte. Il proprietario del progetto puo' cambiarle; chi esegue NON deve cambiarle di sua iniziativa.

| Argomento | Decisione |
|---|---|
| Nomi | "Highlights" (schermata a pagine/carosello) e "Badge" (schermata a lista) |
| Punto di ingresso | Due `ActionCard` nella Dashboard, prima della card Milestone. Nessuna tab nuova, nessun pallino di notifica. |
| Dati Release A | Solo `GlobalStats` in modalita' ALL_TIME, zero query nuove |
| Cosa si conta | Solo i messaggi inviati da me (breakdown per `me@s.whatsapp.net`), tranne il record di serie consecutiva (quello conta anche i ricevuti) |
| Badge v1 | 9 totali: NIGHT_OWL / EARLY_BIRD al 10% con minimo 500 messaggi; CHATTERBOX a 1k/10k/50k/100k messaggi; MARATHON a 30/100/365 giorni |
| Sblocco badge | Permanente ("sticky"), separato per modalita' (Root/Lite), senza data di sblocco salvata. Si azzera solo con il reset completo (`fullReset`). |
| Frasi ironiche (quip) | 3 varianti per card, stabili nell'arco della stessa giornata, tradotte in tutte le 13 locale |
| Tempi di risposta | Solo Root Mode (in Lite non sono disponibili) |
| Condivisione immagine | Solo per le card senza nomi di persone, arriva in Release B |
| Telemetria | `badge_unlocked` (badge_id, is_initial) in Release A; `highlight_shared` (card_id) in Release B |
| Grafici | Canvas/Compose nativo, non Vico. Vico va rimosso in Release C (e' dichiarato in `build.gradle.kts` ma non importato da nessun file) |
| Remote Config | Nessun flag di attivazione: la feature e' sempre attiva |

---

## 2. Fatti verificati / trappole

Verificati leggendo il codice attuale. Non ri-verificarli, sono gia' certi.

1. `GlobalStats.isLiteMode` vale SEMPRE `false`, indipendentemente dalla modalita' reale: usa invece `analyticsRepository.isLiteMode.value`.
2. `AnalyticsRepository.getQuickGlobalStats` di default usa `TimeRange.CUSTOM`: passa sempre esplicitamente `timeRange = TimeRange.ALL_TIME`, e' l'unico range che sfrutta la cache.
3. Le distribuzioni orarie/settimanali di `GlobalStats` contengono TUTTI i mittenti: per isolare i soli messaggi miei, passa sempre da `MyActivity` (A3), mai dal campo `count` grezzo del bucket.
4. Le ore sono in orario locale. `dayOfWeek` segue la convenzione 0 = domenica ... 6 = sabato.
5. `GlobalStats.longestStreak` e' il RECORD storico di giorni consecutivi (UTC, messaggi inviati o ricevuti), non la serie corrente.
6. Non esiste, oggi, una lista di `ChatStats` per tutte le chat: le funzioni B usano `ChatDao`/`ContactDao`/`MessageDao` direttamente, non un elenco gia' pronto.
7. Le cache DataStore `stats_cache_root`/`stats_cache_lite` (usate da `CacheManager`) vengono svuotate a ogni import, refresh e "Elimina cache": per questo lo sblocco badge NON puo' vivere li', serve un DataStore separato (A5).
8. `ActionCard` in `ChatDetailScreen.kt` e' privata: va estratta in un componente condiviso (A1), non duplicata.
9. Esiste gia' una funzione privata `formatDuration` in due file diversi (`ChatDetailScreen.kt` e la schermata Chiamate): la nuova funzione condivisa si chiama `formatReplyDuration` per non confliggere.
10. In Lite Mode i timestamp hanno risoluzione al minuto, non al millisecondo.
11. Niente funzioni finestra SQL (`LAG`/`OVER`): non sono supportate da SQLite su API 26-29.
12. La telemetria passa da un allowlist Remote Config (`tracking_allowed_events_csv`): un evento non in lista viene scartato in silenzio. `bundleOf` accetta solo stringhe come valore.
13. Convenzioni Compose del progetto: `collectAsState()` (non `collectAsStateWithLifecycle`), import di `hiltViewModel` da `androidx.hilt.navigation.compose`.
14. La navigazione duplica le route tra i grafi Root e Lite (stesso `NavHost`, pattern gia' esistente per `Screen.Dashboard`): le nuove route seguono lo stesso schema.
15. Codice morto individuato: il blocco `ChartColors` in `ui/theme/Color.kt` (righe 92-115) e il file `ui/component/HourlyHeatmap.kt` non risultano usati da nessuna schermata.
16. `MessageDao.getMonthlyTrend` raggruppa in UTC, non locale.
17. Nel progetto non esiste ancora nessun `<string-array>` ne' `<plurals>`: questo piano introduce i primi.

---

## 3. Prerequisito bloccante

Prima di iniziare la Release A, verifica manualmente `ChatDetailScreen` su un emulatore API 28 (il minimo supportato in questo range di test). Se qualcosa non funziona gia' oggi, apri un task separato per quello: non provare a sistemarlo dentro questo piano.

---

## 4. Release A - Fondamenta (Highlights + Badge, dati gia' disponibili)

Dodici task, A1-A12. Ordine consigliato: A1-A8 (modelli, logica, stringhe) prima di A9-A11 (UI), A12 per ultimo.

### A1 - ActionCard condivisa

**Obiettivo:** estrarre la `ActionCard` privata di `ChatDetailScreen.kt` in un componente condiviso, riusabile dalla Dashboard.

**File:**
- `app/src/main/java/com/whatstats/ui/component/ActionCard.kt` (nuovo)
- `app/src/main/java/com/whatstats/ui/screen/chatdetail/ChatDetailScreen.kt` (modifica)

**Cosa fare:** crea il nuovo file con questo contenuto (stesso corpo della funzione privata esistente, resa pubblica, con gli import FQN sostituiti da import espliciti):

```kotlin
package com.whatstats.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
@Suppress("FunctionName")
fun ActionCard(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                textAlign = TextAlign.Center,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                textAlign = TextAlign.Center,
            )
        }
    }
}
```

In `ChatDetailScreen.kt`:
1. Cerca la funzione privata `ActionCard` (annotata `@Composable @Suppress("FunctionName") private fun ActionCard(`) e cancellala per intero.
2. Aggiungi `import com.whatstats.ui.component.ActionCard` (rispetta l'ordine alfabetico degli import, va vicino a `import com.whatstats.ui.component.ActivityRecapCard`).
3. Le chiamate esistenti a `ActionCard(...)` restano identiche: la firma non cambia.

**Attenzione:** non toccare la logica interna, e' un puro spostamento di file. Se `ChatDetailScreen.kt` non usa piu' `androidx.compose.ui.graphics.vector.ImageVector` o `androidx.compose.ui.text.style.TextAlign` per fully-qualified reference dopo la rimozione, verifica che restino comunque importati se usati altrove nello stesso file.

**Fatto quando:** `ActionCard` esiste solo in `ui/component/ActionCard.kt`, `ChatDetailScreen.kt` compila e le card in ChatDetail appaiono identiche a prima.

---

### A2 - Modelli dati [VERIFICATO]

**Obiettivo:** definire le forme dati di Highlight e Badge.

**File:**
- `app/src/main/java/com/whatstats/domain/model/Highlight.kt` (nuovo)
- `app/src/main/java/com/whatstats/domain/model/BadgeModels.kt` (nuovo)

**Cosa fare:** copia identici questi due file.

```kotlin
package com.whatstats.domain.model

/**
 * Una card della schermata Highlights. Ogni variante porta solo i dati che mostra.
 * [key] e' stabile: fa da seed per la variante di testo e finisce in telemetria.
 */
sealed interface Highlight {
    val key: String

    data class MessagesSent(val count: Int) : Highlight {
        override val key = "messages_sent"
    }

    data class PeakHour(val hour: Int) : Highlight {
        override val key = "peak_hour"
    }

    /** 0 = domenica ... 6 = sabato (stessa convenzione di DayCount.dayOfWeek). */
    data class TopWeekday(val dayOfWeek: Int) : Highlight {
        override val key = "top_weekday"
    }

    /** Percentuale intera (0..100) dei MIEI messaggi inviati tra 00:00 e 04:59. */
    data class NightShare(val percent: Int) : Highlight {
        override val key = "night_share"
    }

    /** Record storico di giorni consecutivi con almeno un messaggio (GlobalStats.longestStreak). */
    data class RecordStreak(val days: Int) : Highlight {
        override val key = "record_streak"
    }
}
```

```kotlin
package com.whatstats.domain.model

/**
 * Catalogo badge v1.
 * [key] e' l'id salvato in DataStore e inviato in telemetria: MAI rinominarlo dopo il rilascio.
 * [target] e' la soglia: percentuale per NIGHT_OWL/EARLY_BIRD, messaggi per CHATTERBOX, giorni per MARATHON.
 */
enum class BadgeId(
    val key: String,
    val target: Int,
) {
    NIGHT_OWL("night_owl", 10),
    EARLY_BIRD("early_bird", 10),
    CHATTERBOX_1K("chatterbox_1k", 1_000),
    CHATTERBOX_10K("chatterbox_10k", 10_000),
    CHATTERBOX_50K("chatterbox_50k", 50_000),
    CHATTERBOX_100K("chatterbox_100k", 100_000),
    MARATHON_30("marathon_30", 30),
    MARATHON_100("marathon_100", 100),
    MARATHON_365("marathon_365", 365),
}

/**
 * Stato di un badge per la UI.
 * [progress] va da 0f a 1f. Vale 1f solo se [unlocked] e' true.
 */
data class BadgeProgress(
    val id: BadgeId,
    val progress: Float,
    val unlocked: Boolean,
)
```

**Fatto quando:** i due file compilano senza modifiche.

---

### A3 - Engine puri [VERIFICATO]

**Obiettivo:** logica di calcolo Highlights/Badge, pura (nessun accesso a DB o DataStore), testabile con JUnit.

**File (tutti nuovi, package `com.whatstats.domain.engagement`):**
- `MyActivity.kt`
- `HighlightEngine.kt`
- `BadgeEngine.kt`
- `CopyVariantPicker.kt`

Copia identici i quattro file.

```kotlin
package com.whatstats.domain.engagement

import com.whatstats.domain.model.GlobalStats

/**
 * Estrae dalle distribuzioni di GlobalStats SOLO i messaggi inviati dall'utente.
 * Le distribuzioni globali contengono tutti i mittenti: il totale "count" di un bucket
 * NON e' il numero di messaggi dell'utente.
 */
object MyActivity {
    /** JID che MessageDao usa per i messaggi con from_me = 1. */
    const val ME_JID = "me@s.whatsapp.net"

    /** Indice = ora locale 0..23, valore = messaggi inviati da me in quell'ora. */
    fun byHour(stats: GlobalStats): IntArray {
        val result = IntArray(24)
        stats.hourlyDistribution.forEach { bucket ->
            if (bucket.hour in 0..23) {
                result[bucket.hour] += bucket.senderBreakdown.filter { it.jid == ME_JID }.sumOf { it.count }
            }
        }
        return result
    }

    /** Indice = giorno 0 (domenica) .. 6 (sabato), valore = messaggi inviati da me. */
    fun byWeekday(stats: GlobalStats): IntArray {
        val result = IntArray(7)
        stats.weeklyDistribution.forEach { bucket ->
            if (bucket.dayOfWeek in 0..6) {
                result[bucket.dayOfWeek] += bucket.senderBreakdown.filter { it.jid == ME_JID }.sumOf { it.count }
            }
        }
        return result
    }
}
```

```kotlin
package com.whatstats.domain.engagement

import com.whatstats.domain.model.GlobalStats
import com.whatstats.domain.model.Highlight

/**
 * Costruisce le card Highlights da GlobalStats ALL_TIME.
 * Funzione pura: nessun accesso a DB o DataStore, testabile con JUnit.
 * Lista vuota = la UI mostra lo stato vuoto.
 */
object HighlightEngine {
    /** Sotto questa soglia di messaggi inviati le card "su di te" non hanno senso. */
    const val MIN_MY_MESSAGES = 100

    /** Da 2 in su: la stringa "%1$d giorni" resta sempre al plurale. */
    const val MIN_STREAK_DAYS = 2

    const val MIN_NIGHT_SHARE_PERCENT = 5

    /** 00:00-04:59 locale. */
    val NIGHT_HOURS = 0..4

    fun build(stats: GlobalStats): List<Highlight> {
        val result = mutableListOf<Highlight>()
        val byHour = MyActivity.byHour(stats)
        val myTotal = byHour.sum()
        if (myTotal >= MIN_MY_MESSAGES) {
            result += Highlight.MessagesSent(myTotal)
            // maxBy restituisce il PRIMO massimo: a parita' vince l'ora piu' bassa
            result += Highlight.PeakHour(byHour.indices.maxBy { byHour[it] })
            val byWeekday = MyActivity.byWeekday(stats)
            if (byWeekday.sum() > 0) {
                result += Highlight.TopWeekday(byWeekday.indices.maxBy { byWeekday[it] })
            }
            val night = NIGHT_HOURS.sumOf { byHour[it] }
            // Long per evitare overflow su archivi enormi
            val nightPercent = (night.toLong() * 100 / myTotal).toInt()
            if (nightPercent >= MIN_NIGHT_SHARE_PERCENT) {
                result += Highlight.NightShare(nightPercent)
            }
        }
        if (stats.longestStreak >= MIN_STREAK_DAYS) {
            result += Highlight.RecordStreak(stats.longestStreak)
        }
        return result
    }
}
```

```kotlin
package com.whatstats.domain.engagement

import com.whatstats.domain.model.BadgeId
import com.whatstats.domain.model.BadgeProgress
import com.whatstats.domain.model.GlobalStats

/**
 * Valuta i badge da GlobalStats ALL_TIME.
 * Funzione pura: nessun accesso a DB o DataStore, testabile con JUnit.
 * Tutti i confronti di sblocco sono su interi (Long): mai sui Float del progresso.
 */
object BadgeEngine {
    /** NIGHT_OWL ed EARLY_BIRD richiedono anche questo volume minimo di messaggi inviati. */
    const val MIN_MY_MESSAGES_FOR_TIME_SHARE = 500

    /** Un badge bloccato non mostra mai la barra piena, anche se l'arrotondamento Float darebbe 1f. */
    private const val MAX_PARTIAL_PROGRESS = 0.99f

    /** 00:00-04:59 locale. */
    val NIGHT_HOURS = 0..4

    /** 05:00-07:59 locale. */
    val EARLY_HOURS = 5..7

    /**
     * @param alreadyUnlocked chiavi [BadgeId.key] gia' salvate in BadgeUnlockStore:
     * restano sbloccate anche se oggi i numeri non bastano piu'.
     */
    fun evaluate(
        stats: GlobalStats,
        alreadyUnlocked: Set<String>,
    ): List<BadgeProgress> {
        val byHour = MyActivity.byHour(stats)
        val myTotal = byHour.sum().toLong()
        val night = NIGHT_HOURS.sumOf { byHour[it] }.toLong()
        val early = EARLY_HOURS.sumOf { byHour[it] }.toLong()
        val streak = stats.longestStreak.toLong()
        return BadgeId.entries.map { id ->
            val score =
                when (id) {
                    BadgeId.NIGHT_OWL -> share(night, myTotal, id.target, MIN_MY_MESSAGES_FOR_TIME_SHARE)
                    BadgeId.EARLY_BIRD -> share(early, myTotal, id.target, MIN_MY_MESSAGES_FOR_TIME_SHARE)
                    BadgeId.CHATTERBOX_1K,
                    BadgeId.CHATTERBOX_10K,
                    BadgeId.CHATTERBOX_50K,
                    BadgeId.CHATTERBOX_100K,
                    -> allOf(Goal(myTotal, id.target.toLong()))
                    BadgeId.MARATHON_30,
                    BadgeId.MARATHON_100,
                    BadgeId.MARATHON_365,
                    -> allOf(Goal(streak, id.target.toLong()))
                }
            val sticky = id.key in alreadyUnlocked
            BadgeProgress(
                id = id,
                progress = if (sticky) 1f else score.progress,
                unlocked = sticky || score.reached,
            )
        }
    }

    /** Almeno [minTotal] eventi E quota part/total >= targetPercent%. */
    private fun share(
        part: Long,
        total: Long,
        targetPercent: Int,
        minTotal: Int,
    ): Score =
        allOf(
            Goal(total, minTotal.toLong()),
            Goal(part * 100, total * targetPercent),
        )

    private fun allOf(vararg goals: Goal): Score {
        val reached = goals.all { it.reached }
        val progress = if (reached) 1f else goals.minOf { it.partial }
        return Score(reached, progress)
    }

    private class Goal(
        val value: Long,
        val target: Long,
    ) {
        val reached: Boolean
            get() = value >= target

        val partial: Float
            get() = if (target <= 0L) 1f else (value.toFloat() / target).coerceIn(0f, MAX_PARTIAL_PROGRESS)
    }

    private class Score(
        val reached: Boolean,
        val progress: Float,
    )
}
```

```kotlin
package com.whatstats.domain.engagement

/**
 * Sceglie in modo deterministico una variante di testo per una card.
 * Stessa card + stesso giorno = stessa variante: niente cambi a ogni recomposition.
 */
object CopyVariantPicker {
    fun pick(
        cardKey: String,
        daySeed: Long,
        variantCount: Int,
    ): Int {
        if (variantCount <= 1) return 0
        // mod (non %) restituisce sempre un valore in 0 until variantCount, anche con hashCode negativo
        return "$cardKey|$daySeed".hashCode().mod(variantCount)
    }
}
```

**Fatto quando:** i quattro file compilano. La verifica funzionale e' nei test di A4.

---

### A4 - Test JUnit degli engine [VERIFICATO]

**Obiettivo:** copertura di test per gli engine puri di A3.

**File (tutti nuovi, in `app/src/test/java/com/whatstats/domain/engagement/`):**
- `EngagementTestFixtures.kt`
- `HighlightEngineTest.kt`
- `BadgeEngineTest.kt`
- `CopyVariantPickerTest.kt`

Copia identici i quattro file.

```kotlin
package com.whatstats.domain.engagement

import com.whatstats.data.db.model.DayCount
import com.whatstats.data.db.model.HourlyCount
import com.whatstats.data.db.model.SenderStat
import com.whatstats.domain.model.GlobalStats

/** Un altro mittente in ogni bucket: verifica che gli engine contino SOLO i miei messaggi. */
private const val OTHER_JID = "391234567890@s.whatsapp.net"
private const val OTHER_COUNT = 7

/**
 * GlobalStats minimale per i test engagement.
 * [myByHour]: ora -> messaggi inviati da me. [myByWeekday]: giorno (0 = domenica) -> messaggi inviati da me.
 */
internal fun globalStats(
    myByHour: Map<Int, Int> = emptyMap(),
    myByWeekday: Map<Int, Int> = emptyMap(),
    longestStreak: Int = 0,
): GlobalStats =
    GlobalStats(
        totalMessages = 0,
        totalChats = 0,
        totalContacts = 0,
        totalMediaFiles = 0,
        oldestMessageDate = 0L,
        mostActiveChat = "",
        mostActiveHour = 0,
        mostActiveDayOfWeek = 0,
        averageMessagesPerDay = 0f,
        longestStreak = longestStreak,
        hourlyDistribution =
            myByHour.map { (hour, count) ->
                HourlyCount(hour = hour, count = count + OTHER_COUNT, senderBreakdown = breakdown(count))
            },
        weeklyDistribution =
            myByWeekday.map { (day, count) ->
                DayCount(dayOfWeek = day, count = count + OTHER_COUNT, senderBreakdown = breakdown(count))
            },
    )

private fun breakdown(myCount: Int): List<SenderStat> =
    listOf(
        SenderStat(jid = MyActivity.ME_JID, displayName = "You", count = myCount),
        SenderStat(jid = OTHER_JID, displayName = "Other", count = OTHER_COUNT),
    )
```

```kotlin
package com.whatstats.domain.engagement

import com.whatstats.domain.model.Highlight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HighlightEngineTest {
    @Test
    fun noDataGivesNoCards() {
        assertTrue(HighlightEngine.build(globalStats()).isEmpty())
    }

    @Test
    fun belowMinimumMessagesOnlyStreakCardIsBuilt() {
        val stats = globalStats(myByHour = mapOf(10 to 99), longestStreak = 5)
        assertEquals(listOf(Highlight.RecordStreak(5)), HighlightEngine.build(stats))
    }

    @Test
    fun countsOnlyMyMessages() {
        val stats = globalStats(myByHour = mapOf(9 to 60, 21 to 40))
        assertEquals(Highlight.MessagesSent(100), HighlightEngine.build(stats).first())
    }

    @Test
    fun peakHourTiePicksEarliestHour() {
        val stats = globalStats(myByHour = mapOf(8 to 50, 20 to 50))
        assertTrue(Highlight.PeakHour(8) in HighlightEngine.build(stats))
    }

    @Test
    fun topWeekdayUsesZeroForSunday() {
        val stats = globalStats(myByHour = mapOf(12 to 100), myByWeekday = mapOf(0 to 70, 3 to 30))
        assertTrue(Highlight.TopWeekday(0) in HighlightEngine.build(stats))
    }

    @Test
    fun nightShareNeedsAtLeastFivePercent() {
        val below = globalStats(myByHour = mapOf(2 to 4, 12 to 96))
        assertTrue(HighlightEngine.build(below).none { it is Highlight.NightShare })
        val exact = globalStats(myByHour = mapOf(2 to 5, 12 to 95))
        assertTrue(Highlight.NightShare(5) in HighlightEngine.build(exact))
    }

    @Test
    fun streakOfOneDayIsHidden() {
        val stats = globalStats(myByHour = mapOf(12 to 100), longestStreak = 1)
        assertTrue(HighlightEngine.build(stats).none { it is Highlight.RecordStreak })
    }
}
```

```kotlin
package com.whatstats.domain.engagement

import com.whatstats.domain.model.BadgeId
import com.whatstats.domain.model.BadgeProgress
import com.whatstats.domain.model.GlobalStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BadgeEngineTest {
    private fun evaluate(
        stats: GlobalStats,
        alreadyUnlocked: Set<String> = emptySet(),
    ): Map<BadgeId, BadgeProgress> = BadgeEngine.evaluate(stats, alreadyUnlocked).associateBy { it.id }

    @Test
    fun returnsEveryBadgeInCatalogOrder() {
        assertEquals(BadgeId.entries.toList(), evaluate(globalStats()).keys.toList())
    }

    @Test
    fun emptyStatsNeverUnlockAndNeverProduceNaN() {
        evaluate(globalStats()).values.forEach { badge ->
            assertFalse(badge.id.name, badge.unlocked)
            assertFalse(badge.id.name, badge.progress.isNaN())
        }
    }

    @Test
    fun chatterboxUnlocksExactlyAtThreshold() {
        val below = evaluate(globalStats(myByHour = mapOf(12 to 999))).getValue(BadgeId.CHATTERBOX_1K)
        assertFalse(below.unlocked)
        assertTrue(below.progress < 1f)
        val exact = evaluate(globalStats(myByHour = mapOf(12 to 1_000)))
        assertTrue(exact.getValue(BadgeId.CHATTERBOX_1K).unlocked)
        assertEquals(1f, exact.getValue(BadgeId.CHATTERBOX_1K).progress, 0f)
        assertFalse(exact.getValue(BadgeId.CHATTERBOX_10K).unlocked)
    }

    @Test
    fun lockedBadgeNeverShowsFullBar() {
        val badge = evaluate(globalStats(myByHour = mapOf(12 to 99_999))).getValue(BadgeId.CHATTERBOX_100K)
        assertFalse(badge.unlocked)
        assertTrue(badge.progress < 1f)
    }

    @Test
    fun nightOwlNeedsBothVolumeAndShare() {
        // 10% di notte ma solo 499 messaggi: bloccato
        val lowVolume = evaluate(globalStats(myByHour = mapOf(1 to 50, 12 to 449)))
        assertFalse(lowVolume.getValue(BadgeId.NIGHT_OWL).unlocked)
        // 500 messaggi, 49 di notte (9.8%): bloccato
        val lowShare = evaluate(globalStats(myByHour = mapOf(1 to 49, 12 to 451)))
        assertFalse(lowShare.getValue(BadgeId.NIGHT_OWL).unlocked)
        // 500 messaggi, 50 di notte (10%): sbloccato
        val ok = evaluate(globalStats(myByHour = mapOf(1 to 50, 12 to 450)))
        assertTrue(ok.getValue(BadgeId.NIGHT_OWL).unlocked)
    }

    @Test
    fun earlyBirdCountsOnlyFiveToSeven() {
        val outside = evaluate(globalStats(myByHour = mapOf(4 to 100, 8 to 100, 12 to 300)))
        assertFalse(outside.getValue(BadgeId.EARLY_BIRD).unlocked)
        val inside = evaluate(globalStats(myByHour = mapOf(7 to 50, 12 to 450)))
        assertTrue(inside.getValue(BadgeId.EARLY_BIRD).unlocked)
    }

    @Test
    fun marathonUsesLongestStreak() {
        val result = evaluate(globalStats(longestStreak = 100))
        assertTrue(result.getValue(BadgeId.MARATHON_30).unlocked)
        assertTrue(result.getValue(BadgeId.MARATHON_100).unlocked)
        assertFalse(result.getValue(BadgeId.MARATHON_365).unlocked)
    }

    @Test
    fun storedBadgeStaysUnlockedEvenIfStatsDrop() {
        val badge = evaluate(globalStats(), alreadyUnlocked = setOf(BadgeId.MARATHON_365.key)).getValue(BadgeId.MARATHON_365)
        assertTrue(badge.unlocked)
        assertEquals(1f, badge.progress, 0f)
    }
}
```

```kotlin
package com.whatstats.domain.engagement

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CopyVariantPickerTest {
    @Test
    fun zeroOrOneVariantAlwaysReturnsZero() {
        assertEquals(0, CopyVariantPicker.pick("peak_hour", 20_000L, 0))
        assertEquals(0, CopyVariantPicker.pick("peak_hour", 20_000L, 1))
    }

    @Test
    fun resultIsAlwaysInRange() {
        (0L until 1_000L).forEach { day ->
            val index = CopyVariantPicker.pick("messages_sent", day, 3)
            assertTrue("day=$day index=$index", index in 0 until 3)
        }
    }

    @Test
    fun sameInputGivesSameVariant() {
        val first = CopyVariantPicker.pick("night_share", 19_999L, 3)
        repeat(10) { assertEquals(first, CopyVariantPicker.pick("night_share", 19_999L, 3)) }
    }

    @Test
    fun variantChangesAcrossDays() {
        val variants = (0L until 30L).map { day -> CopyVariantPicker.pick("record_streak", 20_000L + day, 3) }.toSet()
        assertTrue(variants.size > 1)
    }
}
```

**Fatto quando:** `./gradlew :app:testDebugUnitTest` passa tutti i test nuovi (19 test totali tra i tre file).

---

### A5 - BadgeUnlockStore

**Obiettivo:** persistere quali badge sono gia' stati sbloccati, separatamente per Root e Lite, in un DataStore dedicato che NON viene mai svuotato dalla cache stats.

**File:** `app/src/main/java/com/whatstats/data/preferences/BadgeUnlockStore.kt` (nuovo)

**Cosa fare:** copia questo contenuto (riferimento completo, non compilato: verifica con la build).

```kotlin
package com.whatstats.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private val Context.badgeUnlockDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "badge_unlocks",
)

private val KEY_UNLOCKED_ROOT = stringSetPreferencesKey("unlocked_ids_root")
private val KEY_UNLOCKED_LITE = stringSetPreferencesKey("unlocked_ids_lite")
private val KEY_INITIALIZED_ROOT = booleanPreferencesKey("initialized_root")
private val KEY_INITIALIZED_LITE = booleanPreferencesKey("initialized_lite")

/**
 * Badge gia' sbloccati, separati per Root e Lite.
 * DataStore dedicato: NON sta in stats_cache_root/stats_cache_lite, che vengono svuotati a ogni import/refresh.
 * Si azzera solo con MainViewModel.fullReset.
 */
@Singleton
class BadgeUnlockStore
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        /** [initialized] = false finche' la schermata Badge non e' mai stata aperta in questa modalita' (serve per is_initial). */
        data class Snapshot(
            val initialized: Boolean,
            val unlockedIds: Set<String>,
        )

        private val dataStore = context.badgeUnlockDataStore

        suspend fun read(isLiteMode: Boolean): Snapshot {
            val prefs = dataStore.data.first()
            return Snapshot(
                initialized = prefs[initializedKey(isLiteMode)] ?: false,
                unlockedIds = prefs[unlockedKey(isLiteMode)] ?: emptySet(),
            )
        }

        /** Aggiunge [ids] a quelli gia' salvati (mai rimuove) e marca la modalita' come inizializzata. */
        suspend fun markUnlocked(
            isLiteMode: Boolean,
            ids: Set<String>,
        ) {
            dataStore.edit { prefs ->
                prefs[unlockedKey(isLiteMode)] = (prefs[unlockedKey(isLiteMode)] ?: emptySet()) + ids
                prefs[initializedKey(isLiteMode)] = true
            }
        }

        suspend fun clear(isLiteMode: Boolean) {
            dataStore.edit { prefs ->
                prefs.remove(unlockedKey(isLiteMode))
                prefs.remove(initializedKey(isLiteMode))
            }
        }

        private fun unlockedKey(isLiteMode: Boolean) = if (isLiteMode) KEY_UNLOCKED_LITE else KEY_UNLOCKED_ROOT

        private fun initializedKey(isLiteMode: Boolean) = if (isLiteMode) KEY_INITIALIZED_LITE else KEY_INITIALIZED_ROOT
    }
```

**Attenzione:** questa classe usa `@Inject constructor`: NON serve aggiungere nulla in `di/AppModule.kt` (vedi sezione 0 e il fatto 7 in sezione 2).

**Fatto quando:** il file compila. Il comportamento reale si verifica insieme ad A6/A10.

---

### A6 - MainViewModel: azzerare i badge nel reset completo

**Obiettivo:** far si' che `fullReset` azzeri anche i badge sbloccati, per la modalita' pulita.

**File:** `app/src/main/java/com/whatstats/ui/MainViewModel.kt` (modifica)

**Cosa fare:**
1. Aggiungi l'import `com.whatstats.data.preferences.BadgeUnlockStore`.
2. Aggiungi un parametro al costruttore, subito dopo il parametro `context` esistente: `private val badgeUnlockStore: BadgeUnlockStore,`.
3. Dentro `fullReset`, nel ramo `if (clearRoot)` dopo la riga `File(context.filesDir, "avatars").deleteRecursively()`, aggiungi `badgeUnlockStore.clear(isLiteMode = false)`.
4. Nel ramo `if (clearLite)`, dopo `File(context.filesDir, "lite_media").deleteRecursively()`, aggiungi `badgeUnlockStore.clear(isLiteMode = true)`.

**Attenzione:** NON toccare `resetLiteMode()` (chiama solo `analyticsRepository.resetLiteMode()`, resta cosi'). `MainViewModel` non ha un `@Provides` in `AppModule.kt`, quindi il nuovo parametro viene risolto automaticamente da Hilt.

**Fatto quando:** dopo `fullReset(clearRoot = true, clearLite = false)`, `badgeUnlockStore.read(isLiteMode = false)` torna uno snapshot vuoto; quello Lite resta intatto (e viceversa).

---

### A7 - Telemetria badge_unlocked

**Obiettivo:** inviare un evento quando un badge passa da bloccato a sbloccato.

**File:**
- `app/src/main/java/com/whatstats/telemetry/TelemetryRepository.kt` (modifica)
- `docs/Firebase/Firebase_Analytics_Forced_Update_Plan.md` (modifica, sezione "7. Event taxonomy v1")

**Cosa fare:** in `TelemetryRepository`, subito dopo la funzione `trackMediaShare` esistente, con la stessa indentazione e forma, aggiungi:

```kotlin
    fun trackBadgeUnlocked(
        badgeId: String,
        isInitial: Boolean,
    ) {
        logIfAllowed(
            eventName = "badge_unlocked",
            extra =
                bundleOf(
                    "badge_id" to badgeId,
                    "is_initial" to isInitial.toString(),
                ),
        )
    }
```

Nel documento Firebase:
- dopo la riga dell'evento `media_share` aggiungi: `` - `badge_unlocked` (badge sbloccato; is_initial=true se gia' raggiunto alla prima valutazione) ``
- dopo la riga dei parametri `` - `media_share`: media_type `` aggiungi: `` - `badge_unlocked`: badge_id, is_initial ``

**Attenzione:** se la Remote Config `tracking_allowed_events_csv` e' non vuota, aggiungi `badge_unlocked` anche li' (altrimenti l'evento viene scartato in silenzio, vedi fatto 12 in sezione 2). Non aggiungerlo invece all'elenco `minimalEvents`.

**Fatto quando:** compila; se hai accesso a Remote Config di test, l'evento compare nel debug view di Firebase.

---

### A8 - Stringhe utente

**Obiettivo:** tutte le stringhe della UI Release A, in tutte le 13 locale del progetto (`values`, `values-de`, `values-es`, `values-fr`, `values-hi`, `values-id`, `values-in`, `values-it`, `values-kk`, `values-pt-rBR`, `values-ro`, `values-ru`, `values-tr`).

**File:** `app/src/main/res/values*/strings.xml` (13 file, tutti da modificare)

**Regole di scrittura, valide anche per B9:**
- Incolla ogni blocco di ogni locale appena prima di `</resources>` nel file di quella cartella.
- `engagement_percent_value` va SOLO nel file `values` di default (non e' tradotta, e' un formato tecnico): `<string name="engagement_percent_value" translatable="false">%1$d%%</string>`.
- Un apostrofo dentro un valore XML si scrive `\'`. Un simbolo percento letterale in una stringa con placeholder si scrive `%%`. Nessun valore inizia con `?` o `@`.
- Ogni `<string-array>` ha esattamente 3 `<item>` in OGNI locale, nessuna eccezione.
- Gli argomenti di formato (`%1$d`, `%1$s`, ecc.) sono identici per la stessa chiave in tutte le locale: non aggiungerne o toglierne traducendo.
- I 5 array si chiamano: `highlight_messages_sent_quips`, `highlight_peak_hour_quips`, `highlight_top_weekday_quips`, `highlight_night_share_quips`, `highlight_record_streak_quips`.

Le 23 chiavi Release A, nell'ordine in cui vanno scritte (piu' i 5 array subito dopo la 23esima):

1. `highlights_entry_title`
2. `highlights_entry_sub`
3. `badges_entry_title`
4. `badges_entry_sub`
5. `highlights_title`
6. `highlights_empty`
7. `highlight_messages_sent_title`
8. `highlight_peak_hour_title`
9. `highlight_top_weekday_title`
10. `highlight_night_share_title`
11. `highlight_record_streak_title`
12. `highlight_streak_value`
13. `badges_title`
14. `badge_new_label`
15. `badge_unlocked_label`
16. `badge_night_owl_title`
17. `badge_night_owl_desc`
18. `badge_early_bird_title`
19. `badge_early_bird_desc`
20. `badge_chatterbox_title`
21. `badge_chatterbox_desc`
22. `badge_marathon_title`
23. `badge_marathon_desc`

Sotto, i contenuti locale per locale. Le 5 righe "Quips" nell'ordine: messages_sent / peak_hour / top_weekday / night_share / record_streak (3 varianti separate da `|`, una per `<item>`). Le chiavi B1-B9 sono per la Release B (sezione 5), incluse qui per completezza di traduzione.

#### values (EN, default)

1. Highlights
2. Fun facts about you
3. Badges
4. Unlock achievements
5. Your highlights
6. Not enough messages yet. Come back after chatting a bit more.
7. Messages you sent
8. Your peak hour
9. Your most active day
10. Sent after midnight
11. Your longest streak
12. `%1$d days`
13. Badges
14. New
15. Unlocked
16. Night owl
17. `At least %1$d%% of your messages sent between 00:00 and 04:59 (min. %2$d messages)`
18. Early bird
19. `At least %1$d%% of your messages sent between 05:00 and 07:59 (min. %2$d messages)`
20. Chatterbox
21. `Send %1$s messages`
22. Marathoner
23. `%1$d days in a row with at least one message (sent or received)`

Quips:
- Your thumbs deserve a vacation. | That's a lot of typing. | Keyboard warrior confirmed.
- That's when you're unstoppable. | Everyone knows when to find you. | Your phone's rush hour.
- The day your chats come alive. | Mark it on the calendar. | Your chattiest day.
- Sleep is overrated, apparently. | The night belongs to you. | Best ideas come after midnight?
- Consistency, expert level. | Not a single day off. | Your chats never sleep.

B keys (EN): B1 Replies to you the fastest | B2 You reply the fastest to | B3 You start the most chats with | B4 `Average reply: %1$s` | B5 `%1$d%% of conversations started by you` | B6 Icebreaker | B7 `Start at least %1$d%% of your one-to-one conversations (min. %2$d conversations)` | B8 Quick draw | B9 `At least %1$d%% of your one-to-one replies within 5 minutes (min. %2$d replies)`

#### values-it

1. Highlights
2. Curiosita' su di te
3. Badge
4. Sblocca traguardi
5. I tuoi highlights
6. Ancora pochi messaggi. Torna dopo aver chattato un po\' di piu'.
7. Messaggi che hai inviato
8. La tua ora di punta
9. Il tuo giorno piu' attivo
10. Inviati dopo mezzanotte
11. La tua serie piu' lunga
12. `%1$d giorni`
13. Badge
14. Nuovo
15. Sbloccato
16. Nottambulo
17. `Almeno il %1$d%% dei tuoi messaggi inviato tra le 00:00 e le 04:59 (min. %2$d messaggi)`
18. Mattiniero
19. `Almeno il %1$d%% dei tuoi messaggi inviato tra le 05:00 e le 07:59 (min. %2$d messaggi)`
20. Chiacchierone
21. `Invia %1$s messaggi`
22. Maratoneta
23. `%1$d giorni di fila con almeno un messaggio (inviato o ricevuto)`

Quips:
- I tuoi pollici meritano una vacanza. | Altro che romanzo. | Tastiera sotto sforzo, confermato.
- E\' l\'ora in cui sei inarrestabile. | Tutti sanno quando trovarti. | L\'ora di punta del tuo telefono.
- Il giorno in cui le tue chat si accendono. | Segnatelo sul calendario. | Il tuo giorno piu' chiacchierone.
- Dormire e\' sopravvalutato, a quanto pare. | La notte e\' tua. | Le idee migliori arrivano dopo mezzanotte?
- Costanza da professionista. | Nemmeno un giorno di pausa. | Le tue chat non dormono mai.

B keys (IT): B1 Ti risponde piu' in fretta | B2 Rispondi piu' in fretta a | B3 Inizi piu' chat con | B4 `Risposta media: %1$s` | B5 `Il %1$d%% delle conversazioni le inizi tu` | B6 Rompighiaccio | B7 `Inizia tu almeno il %1$d%% delle conversazioni nelle chat 1-a-1 (min. %2$d conversazioni)` | B8 Fulmine | B9 `Almeno il %1$d%% delle tue risposte nelle chat 1-a-1 entro 5 minuti (min. %2$d risposte)`

#### values-de (du)

1. Highlights
2. Fun Facts uber dich
3. Abzeichen
4. Erfolge freischalten
5. Deine Highlights
6. Noch zu wenige Nachrichten. Schau wieder vorbei, wenn du mehr gechattet hast.
7. Von dir gesendete Nachrichten
8. Deine Stosszeit
9. Dein aktivster Tag
10. Nach Mitternacht gesendet
11. Deine langste Serie
12. `%1$d Tage`
13. Abzeichen
14. Neu
15. Freigeschaltet
16. Nachteule
17. `Mindestens %1$d%% deiner Nachrichten zwischen 00:00 und 04:59 gesendet (mind. %2$d Nachrichten)`
18. Fruhaufsteher
19. `Mindestens %1$d%% deiner Nachrichten zwischen 05:00 und 07:59 gesendet (mind. %2$d Nachrichten)`
20. Plaudertasche
21. `Sende %1$s Nachrichten`
22. Marathon
23. `%1$d Tage in Folge mit mindestens einer Nachricht (gesendet oder empfangen)`

Quips:
- Deine Daumen haben Urlaub verdient. | Das ist eine Menge Tipperei. | Tastatur-Profi bestatigt.
- Da bist du nicht zu bremsen. | Alle wissen, wann sie dich erreichen. | Die Rushhour deines Handys.
- Der Tag, an dem deine Chats aufbluhen. | Rot im Kalender markieren. | Dein gesprachigster Tag.
- Schlaf wird uberbewertet, offenbar. | Die Nacht gehort dir. | Die besten Ideen kommen nach Mitternacht?
- Bestandigkeit auf Profi-Niveau. | Kein einziger freier Tag. | Deine Chats schlafen nie.

B keys (DE): B1 Antwortet dir am schnellsten | B2 Deine schnellsten Antworten gehen an | B3 Die meisten Chats beginnst du mit | B4 `Durchschnittliche Antwort: %1$s` | B5 `%1$d%% der Unterhaltungen beginnst du` | B6 Eisbrecher | B7 `Beginne mindestens %1$d%% deiner Einzelunterhaltungen (mind. %2$d Unterhaltungen)` | B8 Blitzschnell | B9 `Mindestens %1$d%% deiner Antworten in Einzelchats innerhalb von 5 Minuten (mind. %2$d Antworten)`

#### values-es (tu)

1. Destacados
2. Curiosidades sobre ti
3. Insignias
4. Desbloquea logros
5. Tus destacados
6. Todavia hay pocos mensajes. Vuelve cuando hayas chateado un poco mas.
7. Mensajes que enviaste
8. Tu hora punta
9. Tu dia mas activo
10. Enviados despues de medianoche
11. Tu racha mas larga
12. `%1$d dias`
13. Insignias
14. Nuevo
15. Desbloqueado
16. Buho nocturno
17. `Al menos el %1$d%% de tus mensajes enviado entre las 00:00 y las 04:59 (min. %2$d mensajes)`
18. Madrugador
19. `Al menos el %1$d%% de tus mensajes enviado entre las 05:00 y las 07:59 (min. %2$d mensajes)`
20. Parlanchin
21. `Envia %1$s mensajes`
22. Maratonista
23. `%1$d dias seguidos con al menos un mensaje (enviado o recibido)`

Quips:
- Tus pulgares merecen vacaciones. | Eso es escribir mucho. | Guerrero del teclado confirmado.
- Es cuando eres imparable. | Todos saben cuando encontrarte. | La hora punta de tu movil.
- El dia en que tus chats cobran vida. | Marcalo en el calendario. | Tu dia mas conversador.
- Dormir esta sobrevalorado, al parecer. | La noche es tuya. | Tus mejores ideas llegan despues de medianoche?
- Constancia nivel experto. | Ni un solo dia libre. | Tus chats nunca duermen.

B keys (ES): B1 Quien te responde mas rapido | B2 Respondes mas rapido a | B3 Inicias mas chats con | B4 `Respuesta media: %1$s` | B5 `Inicias tu el %1$d%% de las conversaciones` | B6 Rompehielos | B7 `Inicia al menos el %1$d%% de tus conversaciones individuales (min. %2$d conversaciones)` | B8 Rayo | B9 `Al menos el %1$d%% de tus respuestas en chats individuales en menos de 5 minutos (min. %2$d respuestas)`

#### values-fr (vous)

1. Temps forts
2. Anecdotes sur vous
3. Badges
4. Debloquez des succes
5. Vos temps forts
6. Pas encore assez de messages. Revenez apres avoir un peu plus discute.
7. Messages que vous avez envoyes
8. Votre heure de pointe
9. Votre jour le plus actif
10. Envoyes apres minuit
11. Votre plus longue serie
12. `%1$d jours`
13. Badges
14. Nouveau
15. Debloque
16. Oiseau de nuit
17. `Au moins %1$d%% de vos messages envoyes entre 00:00 et 04:59 (min. %2$d messages)`
18. Leve-tot
19. `Au moins %1$d%% de vos messages envoyes entre 05:00 et 07:59 (min. %2$d messages)`
20. Bavard
21. `Envoyez %1$s messages`
22. Marathonien
23. `%1$d jours d\'affilee avec au moins un message (envoye ou recu)`

Quips:
- Vos pouces meritent des vacances. | Ca fait beaucoup de frappe. | Pro du clavier, c\'est confirme.
- C\'est la que vous etes inarretable. | Tout le monde sait quand vous trouver. | L\'heure de pointe de votre telephone.
- Le jour ou vos discussions s\'animent. | A noter dans le calendrier. | Votre jour le plus bavard.
- Le sommeil est surfait, apparemment. | La nuit vous appartient. | Vos meilleures idees arrivent apres minuit ?
- Regularite niveau expert. | Pas un seul jour de pause. | Vos discussions ne dorment jamais.

B keys (FR): B1 Vous repond le plus vite | B2 Vous repondez le plus vite a | B3 Vous lancez le plus de discussions avec | B4 `Reponse moyenne : %1$s` | B5 `Vous lancez %1$d%% des conversations` | B6 Brise-glace | B7 `Lancez au moins %1$d%% de vos conversations individuelles (min. %2$d conversations)` | B8 Eclair | B9 `Au moins %1$d%% de vos reponses en discussion individuelle en moins de 5 minutes (min. %2$d reponses)`

#### values-pt-rBR (voce)

1. Destaques
2. Curiosidades sobre voce
3. Medalhas
4. Desbloqueie conquistas
5. Seus destaques
6. Ainda ha poucas mensagens. Volte depois de conversar um pouco mais.
7. Mensagens que voce enviou
8. Seu horario de pico
9. Seu dia mais ativo
10. Enviadas depois da meia-noite
11. Sua maior sequencia
12. `%1$d dias`
13. Medalhas
14. Nova
15. Desbloqueada
16. Coruja noturna
17. `Pelo menos %1$d%% das suas mensagens enviadas entre 00:00 e 04:59 (min. %2$d mensagens)`
18. Madrugador
19. `Pelo menos %1$d%% das suas mensagens enviadas entre 05:00 e 07:59 (min. %2$d mensagens)`
20. Tagarela
21. `Envie %1$s mensagens`
22. Maratonista
23. `%1$d dias seguidos com pelo menos uma mensagem (enviada ou recebida)`

Quips:
- Seus polegares merecem ferias. | Isso e muita digitacao. | Guerreiro do teclado confirmado.
- E quando voce fica imparavel. | Todo mundo sabe quando te encontrar. | O horario de pico do seu celular.
- O dia em que suas conversas ganham vida. | Marque no calendario. | Seu dia mais falante.
- Dormir e superestimado, pelo visto. | A noite e sua. | Suas melhores ideias chegam depois da meia-noite?
- Constancia nivel expert. | Nem um dia de folga. | Suas conversas nunca dormem.

B keys (PT-BR): B1 Quem te responde mais rapido | B2 Voce responde mais rapido a | B3 Voce inicia mais conversas com | B4 `Resposta media: %1$s` | B5 `Voce inicia %1$d%% das conversas` | B6 Quebra-gelo | B7 `Inicie pelo menos %1$d%% das suas conversas individuais (min. %2$d conversas)` | B8 Relampago | B9 `Pelo menos %1$d%% das suas respostas em conversas individuais em ate 5 minutos (min. %2$d respostas)`

#### values-ro (tu)

1. Momente
2. Curiozitati despre tine
3. Insigne
4. Deblocheaza realizari
5. Momentele tale
6. Inca sunt prea putine mesaje. Revino dupa ce mai conversezi putin.
7. Mesaje trimise de tine
8. Ora ta de varf
9. Cea mai activa zi a ta
10. Trimise dupa miezul noptii
11. Cea mai lunga serie a ta
12. `Zile: %1$d`
13. Insigne
14. Nou
15. Deblocat
16. Bufnita de noapte
17. `Cel putin %1$d%% din mesajele tale trimise intre 00:00 si 04:59 (min. %2$d de mesaje)`
18. Matinal
19. `Cel putin %1$d%% din mesajele tale trimise intre 05:00 si 07:59 (min. %2$d de mesaje)`
20. Vorbaret
21. `Trimite %1$s de mesaje`
22. Maratonist
23. `%1$d de zile la rand cu cel putin un mesaj (trimis sau primit)`

Quips:
- Degetele tale merita o vacanta. | Asta inseamna mult tastat. | Razboinic al tastaturii, confirmat.
- Atunci esti de neoprit. | Toata lumea stie cand sa te gaseasca. | Ora de varf a telefonului tau.
- Ziua in care chat-urile tale prind viata. | Noteaza-ti-o in calendar. | Cea mai vorbareata zi a ta.
- Somnul e supraevaluat, se pare. | Noaptea e a ta. | Cele mai bune idei vin dupa miezul noptii?
- Consecventa de nivel expert. | Nicio zi libera. | Chat-urile tale nu dorm niciodata.

B keys (RO): B1 Iti raspunde cel mai repede | B2 Cele mai rapide raspunsuri ale tale merg la | B3 Incepi cele mai multe conversatii cu | B4 `Raspuns mediu: %1$s` | B5 `Incepi tu %1$d%% dintre conversatii` | B6 Spargatorul de gheata | B7 `Incepe tu cel putin %1$d%% dintre conversatiile individuale (min. %2$d de conversatii)` | B8 Fulger | B9 `Cel putin %1$d%% dintre raspunsurile tale in chat-uri individuale in maximum 5 minute (min. %2$d de raspunsuri)`

#### values-ru (VY / formale)

1. Highlights
2. Interesnye fakty o vas
3. Znachki
4. Otkryvayte dostizheniya
5. Vashi highlights
6. Poka slishkom malo soobshcheniy. Vozvrashchaytes, kogda poobshchaetes pobolshe.
7. Otpravleno vami soobshcheniy
8. Vash chas pik
9. Vash samyy aktivnyy den
10. Otpravleno posle polunochi
11. Vasha samaya dlinnaya seriya
12. `Dney: %1$d`
13. Znachki
14. Novyy
15. Poluchen
16. Sova
17. `Ne menee %1$d%% vashikh soobshcheniy otpravleno s 00:00 do 04:59 (min. %2$d soobshcheniy)`
18. Zhavoronok
19. `Ne menee %1$d%% vashikh soobshcheniy otpravleno s 05:00 do 07:59 (min. %2$d soobshcheniy)`
20. Boltun
21. `Otpravte %1$s soobshcheniy`
22. Marafonets
23. `%1$d dney podryad khotya by s odnim soobshcheniem (otpravlennym ili poluchennym)`

Quips:
- Vashim palcam nuzhen otpusk. | Eto ochen mnogo teksta. | Master klaviatury, podtverzhdeno.
- V eto vremya vas ne ostanovit. | Vse znayut, kogda vas nayti. | Chas pik vashego telefona.
- Den, kogda vashi chaty ozhivayut. | Otmette ego v kalendare. | Vash samyy razgovorchivyy den.
- Son pereotsenen, sudya po vsemu. | Noch prinadlezhit vam. | Luchshie idei prikhodyat posle polunochi?
- Postoyanstvo urovnya ekspert. | Ni odnogo vykhodnogo. | Vashi chaty nikogda ne spyat.

B keys (RU): B1 Bystree vsekh otvechaet vam | B2 Bystree vsego vy otvechaete | B3 Chashche vsego vy nachinaete chat s | B4 `Sredniy otvet: %1$s` | B5 `Vy nachinaete %1$d%% razgovorov` | B6 Ledokol | B7 `Nachinayte ne menee %1$d%% lichnykh razgovorov (min. %2$d razgovorov)` | B8 Molniya | B9 `Ne menee %1$d%% vashikh otvetov v lichnykh chatakh za 5 minut (min. %2$d otvetov)`

**Attenzione RU:** il progetto usa caratteri cirillici reali nel file `values-ru/strings.xml` (i testi sopra sono traslitterati solo per leggibilita' in questo documento ASCII). Scrivi in cirillico vero nell'XML, es. voce 1 = "Хайлайты", voce 16 = "Сова", ecc. Segui la stessa logica di traduzione, non la traslitterazione.

#### values-kk (Siz / formale)

**Attenzione KK:** stesso discorso del russo: scrivi in caratteri kazaki reali (script cirillico kazako) nell'XML. In questo documento il contenuto e' descritto in italiano per permettere la traduzione, non da copiare come testo latino.

Contenuto da tradurre in kazako, stesso ordine e stessi placeholder delle altre locale:
1. Highlights (o traduzione equivalente "Untulmas saattar")
2-23: stessa lista semantica di EN/IT sopra, registro formale "Siz".
Quips: stesso tono leggero delle altre lingue, 3 varianti per array.
B keys: stessa lista semantica B1-B9.

#### values-hi (aap / formale)

**Attenzione HI:** scrivi in devanagari reale nell'XML, non in traslitterazione latina.

Contenuto da tradurre in hindi, stesso ordine e placeholder, registro formale "aap". Stessa lista semantica delle altre locale per le 23 chiavi, i 5 array di quip (3 varianti ciascuno) e le chiavi B1-B9.

#### values-id e values-in (Anda / formale, contenuto identico in entrambi i file)

1. Sorotan
2. Fakta seru tentang Anda
3. Lencana
4. Buka pencapaian
5. Sorotan Anda
6. Pesan masih terlalu sedikit. Kembalilah setelah mengobrol lebih banyak.
7. Pesan yang Anda kirim
8. Jam tersibuk Anda
9. Hari teraktif Anda
10. Dikirim setelah tengah malam
11. Rentetan terpanjang Anda
12. `%1$d hari`
13. Lencana
14. Baru
15. Terbuka
16. Burung hantu malam
17. `Minimal %1$d%% pesan Anda dikirim antara 00:00 dan 04:59 (min. %2$d pesan)`
18. Bangun pagi
19. `Minimal %1$d%% pesan Anda dikirim antara 05:00 dan 07:59 (min. %2$d pesan)`
20. Si cerewet
21. `Kirim %1$s pesan`
22. Maraton
23. `%1$d hari berturut-turut dengan minimal satu pesan (dikirim atau diterima)`

Quips:
- Jempol Anda butuh liburan. | Itu banyak sekali mengetik. | Pejuang keyboard, terkonfirmasi.
- Saat itulah Anda tak terhentikan. | Semua orang tahu kapan bisa menemukan Anda. | Jam sibuk ponsel Anda.
- Hari ketika obrolan Anda hidup. | Tandai di kalender. | Hari Anda paling banyak bicara.
- Tidur itu dilebih-lebihkan, rupanya. | Malam adalah milik Anda. | Ide terbaik Anda datang setelah tengah malam?
- Konsistensi tingkat ahli. | Tanpa satu hari pun libur. | Obrolan Anda tidak pernah tidur.

B keys (ID/IN): B1 Paling cepat membalas Anda | B2 Anda paling cepat membalas | B3 Anda paling sering memulai obrolan dengan | B4 `Rata-rata balasan: %1$s` | B5 `Anda memulai %1$d%% percakapan` | B6 Pemecah kebekuan | B7 `Mulai minimal %1$d%% percakapan pribadi Anda (min. %2$d percakapan)` | B8 Kilat | B9 `Minimal %1$d%% balasan Anda di chat pribadi dalam 5 menit (min. %2$d balasan)`

#### values-tr (sen)

**Attenzione TR:** in turco il simbolo percento precede il numero. Le chiavi con placeholder percentuale usano `%%%1$d` (percento letterale prima del placeholder), non `%1$d%%`.

1. One cikanlar
2. Seninle ilgili eglenceli bilgiler
3. Rozetler
4. Basarimlarin kilidini ac
5. One cikanlarin
6. Henuz yeterince mesaj yok. Biraz daha sohbet ettikten sonra geri gel.
7. Gonderdigin mesajlar
8. En yogun saatin
9. En aktif gunun
10. Gece yarisindan sonra gonderilenler
11. En uzun serin
12. `%1$d gun`
13. Rozetler
14. Yeni
15. Kazanildi
16. Gece kusu
17. `Mesajlarinin en az %%%1$d kadari 00:00 ile 04:59 arasinda gonderildi (en az %2$d mesaj)`
18. Erkenci
19. `Mesajlarinin en az %%%1$d kadari 05:00 ile 07:59 arasinda gonderildi (en az %2$d mesaj)`
20. Geveze
21. `%1$s mesaj gonder`
22. Maratoncu
23. `Art arda %1$d gun en az bir mesajla (gonderilen veya alinan)`

Quips:
- Basparmaklarin tatili hak ediyor. | Bu epey fazla yazi. | Klavye savascisi, onaylandi.
- Bu saatte seni kimse duramaz. | Herkes seni ne zaman bulacagini biliyor. | Telefonunun yogun saati.
- Sohbetlerinin canlandigi gun. | Takvime isaretle. | En konuskan gunun.
- Uyku abartiliyor, gorunuse gore. | Gece senin. | En iyi fikirlerin gece yarisindan sonra mi geliyor?
- Tutarlilik: uzman seviyesi. | Tek bir gun bile ara yok. | Sohbetlerin hic uyumuyor.

B keys (TR): B1 Sana en hizli yanit veren | B2 En hizli yanit verdigin kisi | B3 En cok sohbeti baslattigin kisi | B4 `Ortalama yanit: %1$s` | B5 `Sohbetlerin %%%1$d kadarini sen baslatiyorsun` | B6 Buz kirici | B7 `Birebir sohbetlerinin en az %%%1$d kadarini sen baslat (en az %2$d sohbet)` | B8 Simsek | B9 `Birebir sohbetlerdeki yanitlarinin en az %%%1$d kadari 5 dakika icinde (en az %2$d yanit)`

**Attenzione generale su TR/DE/RO/RU/ecc.:** i testi sopra per DE/ES/FR/PT-BR/RO/RU/TR sono scritti senza accenti/diacritici per restare in ASCII in questo documento markdown, ma l'XML finale deve usare i caratteri accentati reali della lingua (es. tedesco "uber" -> "uber" con umlaut vero, francese "Debloquez" -> con accenti veri, ecc.). Traduci il contenuto semantico rispettando l'ortografia corretta della lingua nell'XML, questo documento fa da riferimento di significato e struttura, non da copia carattere-per-carattere per le lingue accentate.

**Fatto quando:** i 13 file XML compilano (`./gradlew :app:compileDebugKotlin`, che valida anche le risorse), ogni array ha 3 item ovunque, `ktlintCheck` non tocca XML ma verifica comunque che l'app compili in tutte le configurazioni.

---

### A9 - Schermata Highlights

**Obiettivo:** ViewModel + schermata a carosello (pager) per le card Highlights.

**File:**
- `app/src/main/java/com/whatstats/ui/screen/highlights/HighlightsViewModel.kt` (nuovo)
- `app/src/main/java/com/whatstats/ui/screen/highlights/HighlightsScreen.kt` (nuovo)

**HighlightsViewModel - cosa fare:**

Import necessari: `androidx.lifecycle.ViewModel`, `androidx.lifecycle.viewModelScope`, `com.whatstats.data.db.model.TimeRange`, `com.whatstats.data.repository.AnalyticsRepository`, `com.whatstats.domain.engagement.HighlightEngine`, `com.whatstats.domain.model.Highlight`, `dagger.hilt.android.lifecycle.HiltViewModel`, `kotlinx.coroutines.flow.MutableStateFlow`, `kotlinx.coroutines.flow.StateFlow`, `kotlinx.coroutines.flow.asStateFlow`, `kotlinx.coroutines.launch`, `javax.inject.Inject`.

```kotlin
@HiltViewModel
class HighlightsViewModel
    @Inject
    constructor(
        private val analyticsRepository: AnalyticsRepository,
    ) : ViewModel() {
        sealed interface UiState {
            data object Loading : UiState

            data class Error(val message: String?) : UiState

            data class Success(val highlights: List<Highlight>) : UiState
        }

        private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
        val uiState: StateFlow<UiState> = _uiState.asStateFlow()

        init {
            load()
        }

        private fun load() {
            viewModelScope.launch {
                _uiState.value =
                    try {
                        // ALL_TIME esplicito: il default e' CUSTOM e salterebbe la cache
                        val stats = analyticsRepository.getQuickGlobalStats(timeRange = TimeRange.ALL_TIME)
                        UiState.Success(HighlightEngine.build(stats))
                    } catch (e: Exception) {
                        UiState.Error(e.message)
                    }
            }
        }
    }
```

**HighlightsScreen - cosa fare:** `@file:Suppress("FunctionName")`, package `com.whatstats.ui.screen.highlights`.

Import: `androidx.annotation.ArrayRes`; `androidx.compose.foundation.background`; da `foundation.layout`: `Arrangement`, `Box`, `Column`, `PaddingValues`, `Row`, `Spacer`, `fillMaxSize`, `fillMaxWidth`, `height`, `padding`, `size`, `statusBarsPadding`, `width`; `androidx.compose.foundation.pager.HorizontalPager`, `androidx.compose.foundation.pager.rememberPagerState`; `androidx.compose.foundation.shape.CircleShape`, `androidx.compose.foundation.shape.RoundedCornerShape`; `androidx.compose.material.icons.Icons`, `androidx.compose.material.icons.outlined.Bedtime`, `CalendarMonth`, `ChatBubbleOutline`, `LocalFireDepartment`, `Schedule`; `androidx.compose.material3.Card`, `CardDefaults`, `Icon`, `MaterialTheme`, `Text`; `androidx.compose.runtime.Composable`, `collectAsState`, `getValue`, `remember`; `androidx.compose.ui.Alignment`, `androidx.compose.ui.Modifier`, `androidx.compose.ui.draw.clip`; `androidx.compose.ui.graphics.Color`, `androidx.compose.ui.graphics.vector.ImageVector`; `androidx.compose.ui.res.stringArrayResource`, `stringResource`; `androidx.compose.ui.text.font.FontWeight`, `androidx.compose.ui.text.style.TextAlign`, `TextOverflow`; `androidx.compose.ui.unit.dp`; `androidx.hilt.navigation.compose.hiltViewModel`; `com.whatstats.R`; `com.whatstats.domain.engagement.CopyVariantPicker`; `com.whatstats.domain.model.Highlight`; `com.whatstats.ui.component.LoadingSection`, `SimpleScreenHeader`; `java.time.LocalDate`.

```kotlin
private val WEEKDAY_NAMES =
    listOf(
        R.string.day_sunday,
        R.string.day_monday,
        R.string.day_tuesday,
        R.string.day_wednesday,
        R.string.day_thursday,
        R.string.day_friday,
        R.string.day_saturday,
    )

@Composable
fun HighlightsScreen(
    onNavigateBack: () -> Unit,
    viewModel: HighlightsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .background(MaterialTheme.colorScheme.background),
    ) {
        SimpleScreenHeader(title = stringResource(R.string.highlights_title), onBack = onNavigateBack)
        when (val state = uiState) {
            HighlightsViewModel.UiState.Loading -> LoadingSection()
            is HighlightsViewModel.UiState.Error -> {
                CenteredMessage(
                    text = stringResource(R.string.error_with_detail, state.message ?: ""),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            is HighlightsViewModel.UiState.Success -> {
                if (state.highlights.isEmpty()) {
                    CenteredMessage(
                        text = stringResource(R.string.highlights_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    HighlightsPager(highlights = state.highlights)
                }
            }
        }
    }
}
```

`HighlightsPager`: `rememberPagerState(pageCount = { highlights.size })`; `daySeed = remember { LocalDate.now().toEpochDay() }`; layout `Column(Modifier.fillMaxSize()) { HorizontalPager(state = pagerState, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 24.dp), pageSpacing = 16.dp) { page -> HighlightCard(highlights[page], daySeed) }; PagerDots(pageCount = highlights.size, currentPage = pagerState.currentPage) }`.

`PagerDots(pageCount, currentPage)`: `Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.Center)`; `repeat(pageCount) { index -> Box(Modifier.padding(horizontal = 4.dp).height(8.dp).width(if (index == currentPage) 20.dp else 8.dp).clip(CircleShape).background(if (index == currentPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) }`.

`HighlightCard(highlight, daySeed)`: `content = highlightContent(highlight)`; `quips = stringArrayResource(content.quipsRes)`; `quip = if (quips.isEmpty()) "" else quips[CopyVariantPicker.pick(highlight.key, daySeed, quips.size)]`. `Card(Modifier.fillMaxSize().padding(vertical = 24.dp), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant))`, dentro `Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center)`:
- `Icon(content.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))`
- `Spacer(Modifier.height(16.dp))`
- `Text(content.title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)`
- `Spacer(Modifier.height(8.dp))`
- `Text(content.value, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)`
- `Spacer(Modifier.height(16.dp))`
- `Text(quip, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)`

```kotlin
private class HighlightContent(
    val icon: ImageVector,
    val title: String,
    val value: String,
    @ArrayRes val quipsRes: Int,
)

@Composable
private fun highlightContent(highlight: Highlight): HighlightContent =
    when (highlight) {
        is Highlight.MessagesSent -> {
            HighlightContent(
                icon = Icons.Outlined.ChatBubbleOutline,
                title = stringResource(R.string.highlight_messages_sent_title),
                value = "%,d".format(highlight.count),
                quipsRes = R.array.highlight_messages_sent_quips,
            )
        }
        is Highlight.PeakHour -> {
            HighlightContent(
                icon = Icons.Outlined.Schedule,
                title = stringResource(R.string.highlight_peak_hour_title),
                value = "%02d:00".format(highlight.hour),
                quipsRes = R.array.highlight_peak_hour_quips,
            )
        }
        is Highlight.TopWeekday -> {
            HighlightContent(
                icon = Icons.Outlined.CalendarMonth,
                title = stringResource(R.string.highlight_top_weekday_title),
                value = stringResource(WEEKDAY_NAMES[highlight.dayOfWeek]),
                quipsRes = R.array.highlight_top_weekday_quips,
            )
        }
        is Highlight.NightShare -> {
            HighlightContent(
                icon = Icons.Outlined.Bedtime,
                title = stringResource(R.string.highlight_night_share_title),
                value = stringResource(R.string.engagement_percent_value, highlight.percent),
                quipsRes = R.array.highlight_night_share_quips,
            )
        }
        is Highlight.RecordStreak -> {
            HighlightContent(
                icon = Icons.Outlined.LocalFireDepartment,
                title = stringResource(R.string.highlight_record_streak_title),
                value = stringResource(R.string.highlight_streak_value, highlight.days),
                quipsRes = R.array.highlight_record_streak_quips,
            )
        }
    }

@Composable
private fun CenteredMessage(
    text: String,
    color: Color,
) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = color, textAlign = TextAlign.Center)
    }
}
```

**Fatto quando:** la schermata compila e mostra le card con swipe funzionante.

---

### A10 - Schermata Badge

**Obiettivo:** ViewModel + schermata a lista per i badge, con marcatura "Nuovo" alla prima apertura in cui si sbloccano.

**File:**
- `app/src/main/java/com/whatstats/ui/screen/badges/BadgesViewModel.kt` (nuovo)
- `app/src/main/java/com/whatstats/ui/screen/badges/BadgesScreen.kt` (nuovo)

**BadgesViewModel - cosa fare:** stessi import di `HighlightsViewModel` piu' `com.whatstats.data.preferences.BadgeUnlockStore`, `com.whatstats.domain.engagement.BadgeEngine`, `com.whatstats.domain.model.BadgeProgress`, `com.whatstats.telemetry.TelemetryRepository`.

```kotlin
@HiltViewModel
class BadgesViewModel
    @Inject
    constructor(
        private val analyticsRepository: AnalyticsRepository,
        private val badgeUnlockStore: BadgeUnlockStore,
        private val telemetryRepository: TelemetryRepository,
    ) : ViewModel() {
        sealed interface UiState {
            data object Loading : UiState

            data class Error(val message: String?) : UiState

            /** [newlyUnlocked] = chiavi sbloccate in QUESTA apertura: mostrano il chip "Nuovo". */
            data class Success(
                val badges: List<BadgeProgress>,
                val newlyUnlocked: Set<String>,
            ) : UiState
        }

        private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
        val uiState: StateFlow<UiState> = _uiState.asStateFlow()

        init {
            load()
        }

        private fun load() {
            viewModelScope.launch {
                _uiState.value =
                    try {
                        // NON usare GlobalStats.isLiteMode: vale sempre false
                        val isLite = analyticsRepository.isLiteMode.value
                        val stats = analyticsRepository.getQuickGlobalStats(timeRange = TimeRange.ALL_TIME)
                        val stored = badgeUnlockStore.read(isLiteMode = isLite)
                        val badges = BadgeEngine.evaluate(stats, stored.unlockedIds)
                        val unlockedNow = badges.filter { it.unlocked }.map { it.id.key }.toSet()
                        val newlyUnlocked = unlockedNow - stored.unlockedIds
                        if (newlyUnlocked.isNotEmpty() || !stored.initialized) {
                            badgeUnlockStore.markUnlocked(isLiteMode = isLite, ids = newlyUnlocked)
                            newlyUnlocked.forEach { key ->
                                telemetryRepository.trackBadgeUnlocked(badgeId = key, isInitial = !stored.initialized)
                            }
                        }
                        UiState.Success(badges, newlyUnlocked)
                    } catch (e: Exception) {
                        UiState.Error(e.message)
                    }
            }
        }
    }
```

**BadgesScreen - cosa fare:** stessa shell di `HighlightsScreen` (header, Loading/Error/Empty).

Import aggiuntivi rispetto ad Highlights: `androidx.compose.foundation.lazy.LazyColumn`, `androidx.compose.foundation.lazy.items`; `androidx.compose.material.icons.outlined.Forum`, `Lock`, `WbSunny` (oltre a `Bedtime`, `LocalFireDepartment` gia' visti); `androidx.compose.material3.LinearProgressIndicator`, `Surface`; `com.whatstats.domain.engagement.BadgeEngine`, `com.whatstats.domain.model.BadgeId`, `com.whatstats.domain.model.BadgeProgress`.

Stato Success:
```kotlin
LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
) {
    items(items = state.badges, key = { it.id.key }) { badge ->
        BadgeRow(badge = badge, isNew = badge.id.key in state.newlyUnlocked)
    }
}
```

`BadgeRow(badge, isNew)`: `Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)))`, dentro `Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically)`:
- `Icon(if (badge.unlocked) badgeText(badge.id).icon else Icons.Outlined.Lock, contentDescription = null, tint = if (badge.unlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(36.dp))`
- `Spacer(Modifier.width(16.dp))`
- `Column(Modifier.weight(1f))`:
  - `Row { Text(badgeText(badge.id).title, style = titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f, fill = false)); if (isNew) { Spacer(Modifier.width(8.dp)); NewChip() } }`
  - `Spacer(Modifier.height(4.dp))`
  - `Text(badgeText(badge.id).description, style = bodySmall, color = onSurfaceVariant)`
  - `Spacer(Modifier.height(8.dp))`
  - se `badge.unlocked`: `Text(stringResource(R.string.badge_unlocked_label), style = labelMedium, color = primary)`
  - altrimenti (il commento sta sopra la riga, non dentro gli argomenti del `Text`):
    ```kotlin
    Row(verticalAlignment = Alignment.CenterVertically) {
        LinearProgressIndicator(progress = { badge.progress }, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        // arrotonda per difetto: non mostrare mai 100% su un badge ancora bloccato
        Text(
            stringResource(R.string.engagement_percent_value, (badge.progress * 100).toInt()),
            style = MaterialTheme.typography.labelSmall,
        )
    }
    ```

`NewChip()`: `Surface(color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(8.dp)) { Text(stringResource(R.string.badge_new_label), style = labelSmall, color = onPrimary, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)) }`.

```kotlin
private class BadgeText(
    val icon: ImageVector,
    val title: String,
    val description: String,
)

@Composable
private fun badgeText(id: BadgeId): BadgeText =
    when (id) {
        BadgeId.NIGHT_OWL -> {
            BadgeText(
                icon = Icons.Outlined.Bedtime,
                title = stringResource(R.string.badge_night_owl_title),
                description =
                    stringResource(
                        R.string.badge_night_owl_desc,
                        id.target,
                        BadgeEngine.MIN_MY_MESSAGES_FOR_TIME_SHARE,
                    ),
            )
        }
        BadgeId.EARLY_BIRD -> {
            BadgeText(
                icon = Icons.Outlined.WbSunny,
                title = stringResource(R.string.badge_early_bird_title),
                description =
                    stringResource(
                        R.string.badge_early_bird_desc,
                        id.target,
                        BadgeEngine.MIN_MY_MESSAGES_FOR_TIME_SHARE,
                    ),
            )
        }
        BadgeId.CHATTERBOX_1K,
        BadgeId.CHATTERBOX_10K,
        BadgeId.CHATTERBOX_50K,
        BadgeId.CHATTERBOX_100K,
        -> {
            BadgeText(
                icon = Icons.Outlined.Forum,
                title = stringResource(R.string.badge_chatterbox_title),
                description = stringResource(R.string.badge_chatterbox_desc, "%,d".format(id.target)),
            )
        }
        BadgeId.MARATHON_30,
        BadgeId.MARATHON_100,
        BadgeId.MARATHON_365,
        -> {
            BadgeText(
                icon = Icons.Outlined.LocalFireDepartment,
                title = stringResource(R.string.badge_marathon_title),
                description = stringResource(R.string.badge_marathon_desc, id.target),
            )
        }
    }
```

`CenteredMessage`: copia privata identica a quella di `HighlightsScreen` (non condivisa tra i due file, per semplicita').

**Fatto quando:** la schermata mostra tutti i 9 badge con icona, titolo, descrizione e stato corretto (barra o etichetta "Sbloccato").

---

### A11 - Navigazione e ingresso dalla Dashboard

**Obiettivo:** collegare le due nuove schermate al resto dell'app.

**File:**
- `app/src/main/java/com/whatstats/ui/navigation/AppNavigation.kt` (modifica)
- `app/src/main/java/com/whatstats/ui/screen/dashboard/DashboardScreen.kt` (modifica)

**AppNavigation.kt - cosa fare:**

1. Dentro `sealed class Screen`, subito dopo la chiusura di `GroupHistory` e prima della graffa finale della classe, aggiungi:
   ```kotlin
   data object Highlights : Screen("highlights")

   data object Badges : Screen("badges")
   ```
2. Subito PRIMA del commento `// ── Route condivise ChatDetail (usate da entrambi i graph) ─────────────────────`, aggiungi:
   ```kotlin
   // ── Route engagement (Highlights / Badges), registrate in entrambi i graph ─────

   private fun NavGraphBuilder.sharedEngagementRoutes(navController: NavHostController) {
       composable(Screen.Highlights.route) {
           HighlightsScreen(onNavigateBack = { navController.popBackStack() })
       }
       composable(Screen.Badges.route) {
           BadgesScreen(onNavigateBack = { navController.popBackStack() })
       }
   }
   ```
3. In ENTRAMBI i blocchi `navigation(startDestination = Screen.Dashboard.route, route = NavGraphRoute.OVERVIEW) { ... }` (uno in `rootModeGraph`, uno in `liteModeGraph`), aggiungi `sharedEngagementRoutes(navController)` subito dopo il blocco `composable(Screen.Dashboard.route) { ... }`.
4. In ENTRAMBE le chiamate a `DashboardScreen(...)`, dopo il parametro `onNavigateToChatDetail = { ... }`, aggiungi:
   ```kotlin
   onNavigateToHighlights = {
       navController.navigate(Screen.Highlights.route) { launchSingleTop = true }
   },
   onNavigateToBadges = {
       navController.navigate(Screen.Badges.route) { launchSingleTop = true }
   },
   ```
5. Aggiungi gli import `com.whatstats.ui.screen.badges.BadgesScreen` e `com.whatstats.ui.screen.highlights.HighlightsScreen` (ordine alfabetico).

**DashboardScreen.kt - cosa fare:**

1. Aggiungi i parametri `onNavigateToHighlights: () -> Unit = {},` e `onNavigateToBadges: () -> Unit = {},` subito dopo `onNavigateToChatDetail: (Long) -> Unit = {},`.
2. Subito PRIMA del commento `// ── Milestone Prediction (Root Only) ──────────────────────`, dentro il `LazyColumn`, aggiungi un nuovo `item`:
   ```kotlin
   // ── Engagement: Highlights e Badge ──────────────────────────────
   item {
       Row(
           modifier = Modifier.fillMaxWidth(),
           horizontalArrangement = Arrangement.spacedBy(12.dp),
       ) {
           ActionCard(
               modifier = Modifier.weight(1f),
               title = stringResource(R.string.highlights_entry_title),
               subtitle = stringResource(R.string.highlights_entry_sub),
               icon = Icons.Outlined.AutoAwesome,
               onClick = {
                   mainVm.trackClick("dashboard", "open_highlights", "engagement_entry")
                   onNavigateToHighlights()
               },
           )
           ActionCard(
               modifier = Modifier.weight(1f),
               title = stringResource(R.string.badges_entry_title),
               subtitle = stringResource(R.string.badges_entry_sub),
               icon = Icons.Outlined.EmojiEvents,
               onClick = {
                   mainVm.trackClick("dashboard", "open_badges", "engagement_entry")
                   onNavigateToBadges()
               },
           )
       }
   }
   ```
3. Aggiungi gli import `androidx.compose.material.icons.outlined.AutoAwesome`, `androidx.compose.material.icons.outlined.EmojiEvents`, `com.whatstats.ui.component.ActionCard` (solo se non gia' presenti dopo A1).

**Attenzione:** i due blocchi OVERVIEW e le due chiamate a `DashboardScreen` sono duplicati per design (Root/Lite condividono lo stesso `NavHost` con route duplicate, vedi fatto 14 in sezione 2): applica la modifica a entrambe le occorrenze, non solo alla prima che trovi.

**Fatto quando:** le due card compaiono nella Dashboard, entrambe portano alla rispettiva schermata, il tasto indietro torna alla Dashboard, in Root Mode e in Lite Mode.

---

### A12 - Test manuali di chiusura Release A

Da eseguire su un device/emulatore reale, in Root Mode e in Lite Mode:

- Le 2 card compaiono nella Dashboard.
- Highlights: lo swipe funziona, i pallini si aggiornano, riaprendo la schermata nello stesso giorno compare la stessa frase ironica.
- Con pochi messaggi compare lo stato vuoto.
- Badge: alla prima apertura compare "Nuovo" sui badge gia' raggiunti; riaprendo, nessuno.
- "Elimina cache": i badge restano sbloccati.
- Reset completo (`fullReset`): i badge si azzerano.
- Root e Lite mantengono insiemi di sblocco separati.
- Il tasto indietro riporta alla Dashboard.

---

## 5. Release B - Metriche di risposta, card su persone, condivisione

Dodici task, B1-B12. Riferimento completo, non compilato: verifica sempre con la build.

### B1 - MessageDao: scansione grezza per le metriche di risposta

**Obiettivo:** una funzione che scorre tutti i messaggi utili in ordine, senza caricarli tutti in memoria e senza funzioni finestra SQL.

**File:** `app/src/main/java/com/whatstats/data/db/dao/MessageDao.kt` (modifica)

**Cosa fare:** aggiungi questo metodo (indentazione 8 spazi, coerente con lo stile del file):

```kotlin
        /**
         * Scorre tutti i messaggi utili ordinati per chat e tempo, senza caricarli in memoria.
         * Niente window function (LAG/OVER): su Android 8-10 SQLite non le supporta.
         * Bloccante: chiamare solo da Dispatchers.IO.
         */
        fun forEachReplyStatsRow(onRow: (chatId: Long, fromMe: Boolean, timestamp: Long) -> Unit) {
            val query =
                """
                SELECT chat_row_id, from_me, timestamp
                FROM message
                WHERE message_type NOT IN (7, 15, 90)
                  AND timestamp > 0
                ORDER BY chat_row_id, timestamp, _id
                """.trimIndent()
            database.getMsgDb().rawQuery(query, null).use { cursor ->
                while (cursor.moveToNext()) {
                    onRow(cursor.getLong(0), cursor.getInt(1) == 1, cursor.getLong(2))
                }
            }
        }
```

**Fatto quando:** la funzione compila ed e' invocabile; il calcolo effettivo dei conteggi vive in B2.

---

### B2 - Modelli e ReplyStatsAccumulator

**Obiettivo:** calcolare, per ogni chat, quante volte l'utente e l'interlocutore rispondono, in quanto tempo, e chi inizia la conversazione, in un singolo passaggio sequenziale sui dati di B1.

**File:**
- `app/src/main/java/com/whatstats/domain/model/ChatReplyStats.kt` (nuovo)
- `app/src/main/java/com/whatstats/domain/engagement/ReplyStatsAccumulator.kt` (nuovo)
- `app/src/test/java/com/whatstats/domain/engagement/ReplyStatsAccumulatorTest.kt` (nuovo)

**ChatReplyStats.kt - cosa fare:**

```kotlin
package com.whatstats.domain.model

import kotlinx.serialization.Serializable

/** Se cambi i campi, cambia anche la chiave cache (reply_stats_v2, vedi B3). */
@Serializable
data class ChatReplyStats(
    val chatId: Long,
    val myReplyCount: Int,
    val myReplySumMs: Long,
    val myFastReplyCount: Int,
    val theirReplyCount: Int,
    val theirReplySumMs: Long,
    val iStarted: Int,
    val theyStarted: Int,
)

data class NamedReplyStats(
    val name: String,
    val stats: ChatReplyStats,
)
```

**ReplyStatsAccumulator.kt - cosa fare:**

```kotlin
package com.whatstats.domain.engagement

import com.whatstats.domain.model.ChatReplyStats

/**
 * Accumula le metriche di risposta scorrendo le righe in ordine (chat, poi tempo).
 * Regole:
 * - risposta = from_me diverso dal messaggio precedente, con 0 < gap < 24h.
 * - nuova conversazione = primo messaggio della chat, oppure gap > 3h dal precedente.
 * - nessuna funzione finestra: tutto calcolato con variabili locali durante lo scorrimento.
 */
class ReplyStatsAccumulator {
    private val results = mutableListOf<ChatReplyStats>()

    private var chatId = NO_CHAT
    private var hasPrevious = false
    private var previousFromMe = false
    private var previousTimestamp = 0L

    private var myReplyCount = 0
    private var myReplySumMs = 0L
    private var myFastReplyCount = 0
    private var theirReplyCount = 0
    private var theirReplySumMs = 0L
    private var iStarted = 0
    private var theyStarted = 0

    fun add(
        rowChatId: Long,
        fromMe: Boolean,
        timestamp: Long,
    ) {
        if (rowChatId != chatId) {
            flush()
            chatId = rowChatId
        }
        val gap = timestamp - previousTimestamp
        if (!hasPrevious || gap > CONVERSATION_GAP_MS) {
            if (fromMe) iStarted++ else theyStarted++
        }
        if (hasPrevious && fromMe != previousFromMe && gap > 0 && gap < MAX_REPLY_GAP_MS) {
            if (fromMe) {
                myReplyCount++
                myReplySumMs += gap
                if (gap < FAST_REPLY_MS) myFastReplyCount++
            } else {
                theirReplyCount++
                theirReplySumMs += gap
            }
        }
        hasPrevious = true
        previousFromMe = fromMe
        previousTimestamp = timestamp
    }

    fun finish(): List<ChatReplyStats> {
        flush()
        return results.toList()
    }

    private fun flush() {
        if (chatId != NO_CHAT) {
            results +=
                ChatReplyStats(
                    chatId = chatId,
                    myReplyCount = myReplyCount,
                    myReplySumMs = myReplySumMs,
                    myFastReplyCount = myFastReplyCount,
                    theirReplyCount = theirReplyCount,
                    theirReplySumMs = theirReplySumMs,
                    iStarted = iStarted,
                    theyStarted = theyStarted,
                )
        }
        chatId = NO_CHAT
        hasPrevious = false
        previousFromMe = false
        previousTimestamp = 0L
        myReplyCount = 0
        myReplySumMs = 0L
        myFastReplyCount = 0
        theirReplyCount = 0
        theirReplySumMs = 0L
        iStarted = 0
        theyStarted = 0
    }

    companion object {
        const val CONVERSATION_GAP_MS = 10_800_000L
        const val MAX_REPLY_GAP_MS = 86_400_000L

        /** Se lo cambi, aggiorna anche badge_quick_draw_desc. */
        const val FAST_REPLY_MS = 300_000L

        private const val NO_CHAT = Long.MIN_VALUE
    }
}
```

**ReplyStatsAccumulatorTest.kt - cosa fare:**

```kotlin
package com.whatstats.domain.engagement

import com.whatstats.domain.model.ChatReplyStats
import org.junit.Assert.assertEquals
import org.junit.Test

class ReplyStatsAccumulatorTest {
    private fun accumulate(vararg rows: Triple<Long, Boolean, Long>): List<ChatReplyStats> {
        val accumulator = ReplyStatsAccumulator()
        rows.forEach { (chatId, fromMe, timestamp) -> accumulator.add(chatId, fromMe, timestamp) }
        return accumulator.finish()
    }

    @Test
    fun emptyInputGivesEmptyList() {
        assertEquals(emptyList<ChatReplyStats>(), accumulate())
    }

    @Test
    fun alternatingMessagesCountRepliesBothWays() {
        val result =
            accumulate(
                Triple(1L, false, 0L),
                Triple(1L, true, 60_000L),
                Triple(1L, false, 180_000L),
            ).single()
        assertEquals(1, result.myReplyCount)
        assertEquals(60_000L, result.myReplySumMs)
        assertEquals(1, result.myFastReplyCount)
        assertEquals(1, result.theirReplyCount)
        assertEquals(120_000L, result.theirReplySumMs)
        assertEquals(0, result.iStarted)
        assertEquals(1, result.theyStarted)
    }

    @Test
    fun consecutiveMessagesFromSameSenderAreNotReplies() {
        val result = accumulate(Triple(1L, true, 0L), Triple(1L, true, 1_000L)).single()
        assertEquals(0, result.myReplyCount)
        assertEquals(1, result.iStarted)
    }

    @Test
    fun gapOverThreeHoursStartsNewConversation() {
        val result =
            accumulate(
                Triple(1L, true, 0L),
                Triple(1L, false, 4 * 3_600_000L),
            ).single()
        assertEquals(1, result.theyStarted)
        assertEquals(1, result.iStarted)
        assertEquals(1, result.myReplyCount)
        assertEquals(0, result.myFastReplyCount)
    }

    @Test
    fun gapOfTwentyFourHoursOrMoreIsNotAReply() {
        val result = accumulate(Triple(1L, true, 0L), Triple(1L, false, 86_400_000L)).single()
        assertEquals(0, result.theirReplyCount)
    }

    @Test
    fun sameTimestampIsNotAReply() {
        val result = accumulate(Triple(1L, true, 60_000L), Triple(1L, false, 60_000L)).single()
        assertEquals(0, result.theirReplyCount)
    }

    @Test
    fun chatChangeResetsState() {
        val result = accumulate(Triple(1L, false, 0L), Triple(2L, true, 1_000L)).associateBy { it.chatId }
        assertEquals(setOf(1L, 2L), result.keys)
        assertEquals(0, result.getValue(2L).myReplyCount)
        assertEquals(1, result.getValue(2L).iStarted)
    }
}
```

**Fatto quando:** `./gradlew :app:testDebugUnitTest` passa anche questi 7 nuovi test.

---

### B3 - Cache per le metriche di risposta

**Obiettivo:** evitare di ricalcolare la scansione completa (B1+B2) a ogni apertura.

**File:** `app/src/main/java/com/whatstats/data/cache/CacheManager.kt` (modifica)

**Cosa fare:**
1. Nella companion object, aggiungi `private val KEY_REPLY_STATS = stringPreferencesKey("reply_stats_v1")`, vicino alle altre chiavi.
2. Aggiungi `getReplyStats(): List<ChatReplyStats>?` e `saveReplyStats(stats: List<ChatReplyStats>)`, copiando esattamente la forma di `getChatList()`/`saveChatList()` gia' esistenti (stessa serializzazione, stesso pattern try/decode).

**Attenzione:** non serve nessuna invalidazione manuale: le funzioni `clear`/`clearRoot`/`clearLite` gia' esistenti svuotano l'intero namespace, la nuova chiave viene ripulita insieme alle altre automaticamente.

**Fatto quando:** `getReplyStats()` torna `null` dopo un `clear`, e i valori salvati dopo `saveReplyStats`.

---

### B4 - EngagementRepository

**Obiettivo:** orchestrare scansione, cache e arricchimento con nomi contatto, per le metriche di risposta.

**File:** `app/src/main/java/com/whatstats/data/repository/EngagementRepository.kt` (nuovo)

**Cosa fare:**

```kotlin
package com.whatstats.data.repository

import com.whatstats.data.cache.CacheManager
import com.whatstats.data.db.dao.ChatDao
import com.whatstats.data.db.dao.ContactDao
import com.whatstats.data.db.dao.MessageDao
import com.whatstats.domain.engagement.ReplyStatsAccumulator
import com.whatstats.domain.model.ChatReplyStats
import com.whatstats.domain.model.NamedReplyStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EngagementRepository
    @Inject
    constructor(
        private val messageDao: MessageDao,
        private val chatDao: ChatDao,
        private val contactDao: ContactDao,
        private val cacheManager: CacheManager,
    ) {
        private val mutex = Mutex()

        suspend fun getReplyStats(): List<ChatReplyStats> =
            withContext(Dispatchers.IO) {
                mutex.withLock {
                    cacheManager.getReplyStats() ?: computeReplyStats().also { cacheManager.saveReplyStats(it) }
                }
            }

        suspend fun getOneToOneReplyStats(): List<NamedReplyStats> =
            withContext(Dispatchers.IO) {
                val chats =
                    (cacheManager.getChatList() ?: chatDao.getChatList())
                        .filter { !it.isGroup && !it.jid.endsWith("@broadcast") && !it.jid.endsWith("@newsletter") }
                val chatsById = chats.associateBy { it.chatId }
                val names = contactDao.resolveContactNames(chats.map { it.jid })
                getReplyStats().mapNotNull { stats ->
                    val chat = chatsById[stats.chatId] ?: return@mapNotNull null
                    NamedReplyStats(name = names[chat.jid] ?: chat.displayName, stats = stats)
                }
            }

        private fun computeReplyStats(): List<ChatReplyStats> {
            val accumulator = ReplyStatsAccumulator()
            messageDao.forEachReplyStatsRow { chatId, fromMe, timestamp -> accumulator.add(chatId, fromMe, timestamp) }
            return accumulator.finish()
        }
    }
```

**Fatto quando:** compila; una chiamata reale su un DB di test torna una lista di `ChatReplyStats` coerente col numero di chat.

---

### B5 - Highlight su persone

**Obiettivo:** estendere `Highlight` e `HighlightEngine` con card che nominano una persona (chi risponde piu' veloce, a chi rispondi piu' veloce, con chi rompi il ghiaccio piu' spesso).

**File:**
- `app/src/main/java/com/whatstats/domain/model/Highlight.kt` (modifica)
- `app/src/main/java/com/whatstats/domain/engagement/HighlightEngine.kt` (modifica)
- `app/src/test/java/com/whatstats/domain/engagement/EngagementTestFixtures.kt` (modifica)
- `app/src/test/java/com/whatstats/domain/engagement/HighlightEngineTest.kt` (modifica, nuovi test)

**Highlight.kt - cosa fare:**
1. Nell'interfaccia `Highlight`, aggiungi `/** true = nessun nome di persona: si puo' condividere. */ val shareable: Boolean`.
2. Nelle 5 varianti esistenti (A2), aggiungi `override val shareable = true`.
3. Aggiungi le 3 varianti nuove, tutte con `shareable = false`:
   ```kotlin
   /** Solo Root: in Lite i timestamp non permettono di calcolare tempi di risposta affidabili. */
   data class FastestReplier(val name: String, val averageMs: Long) : Highlight {
       override val key = "fastest_replier"
       override val shareable = false
   }

   data class YouReplyFastest(val name: String, val averageMs: Long) : Highlight {
       override val key = "you_reply_fastest"
       override val shareable = false
   }

   data class YouStartMost(val name: String, val percent: Int) : Highlight {
       override val key = "you_start_most"
       override val shareable = false
   }
   ```

**HighlightEngine.kt - cosa fare:** aggiungi le costanti `const val MIN_PERSON_SAMPLES = 20`, `const val MIN_START_SHARE_PERCENT = 50`, e la funzione:

```kotlin
fun buildPersonHighlights(
    chats: List<NamedReplyStats>,
    includeReplyTimes: Boolean,
): List<Highlight> {
    val result = mutableListOf<Highlight>()
    if (includeReplyTimes) {
        chats
            .filter { it.stats.theirReplyCount >= MIN_PERSON_SAMPLES }
            .minByOrNull { it.stats.theirReplySumMs / it.stats.theirReplyCount }
            ?.let { result += Highlight.FastestReplier(it.name, it.stats.theirReplySumMs / it.stats.theirReplyCount) }
        chats
            .filter { it.stats.myReplyCount >= MIN_PERSON_SAMPLES }
            .minByOrNull { it.stats.myReplySumMs / it.stats.myReplyCount }
            ?.let { result += Highlight.YouReplyFastest(it.name, it.stats.myReplySumMs / it.stats.myReplyCount) }
    }
    chats
        .filter { it.stats.iStarted + it.stats.theyStarted >= MIN_PERSON_SAMPLES }
        .maxByOrNull { it.stats.iStarted * 100 / (it.stats.iStarted + it.stats.theyStarted) }
        ?.let {
            val percent = it.stats.iStarted * 100 / (it.stats.iStarted + it.stats.theyStarted)
            if (percent >= MIN_START_SHARE_PERCENT) result += Highlight.YouStartMost(it.name, percent)
        }
    return result
}
```

**Attenzione:** a parita' di valore, `minByOrNull`/`maxByOrNull` restituiscono il PRIMO elemento della lista in quell'ordine: se serve un ordine deterministico, ordina prima `chats` per nome.

**EngagementTestFixtures.kt - aggiungi:**
```kotlin
internal fun replyStats(
    chatId: Long = 1L,
    myReplyCount: Int = 0,
    myReplySumMs: Long = 0L,
    myFastReplyCount: Int = 0,
    theirReplyCount: Int = 0,
    theirReplySumMs: Long = 0L,
    iStarted: Int = 0,
    theyStarted: Int = 0,
): ChatReplyStats =
    ChatReplyStats(
        chatId = chatId,
        myReplyCount = myReplyCount,
        myReplySumMs = myReplySumMs,
        myFastReplyCount = myFastReplyCount,
        theirReplyCount = theirReplyCount,
        theirReplySumMs = theirReplySumMs,
        iStarted = iStarted,
        theyStarted = theyStarted,
    )
```

**Nuovi test in HighlightEngineTest.kt:**
```kotlin
@Test
fun personCardsRespectThresholdsAndMode() {
    val anna = NamedReplyStats("Anna", replyStats(chatId = 1, theirReplyCount = 20, theirReplySumMs = 20 * 60_000L))
    val bruno = NamedReplyStats("Bruno", replyStats(chatId = 2, theirReplyCount = 19, theirReplySumMs = 19 * 1_000L))
    val result = HighlightEngine.buildPersonHighlights(listOf(anna, bruno), includeReplyTimes = true)
    assertEquals(listOf(Highlight.FastestReplier("Anna", 60_000L)), result)
    assertTrue(HighlightEngine.buildPersonHighlights(listOf(anna, bruno), includeReplyTimes = false).isEmpty())
}

@Test
fun youStartMostNeedsHalfOfConversations() {
    val carla = NamedReplyStats("Carla", replyStats(chatId = 1, iStarted = 10, theyStarted = 10))
    assertEquals(
        listOf(Highlight.YouStartMost("Carla", 50)),
        HighlightEngine.buildPersonHighlights(listOf(carla), includeReplyTimes = false),
    )
    val dario = NamedReplyStats("Dario", replyStats(chatId = 2, iStarted = 9, theyStarted = 11))
    assertTrue(HighlightEngine.buildPersonHighlights(listOf(dario), includeReplyTimes = false).isEmpty())
}
```

Aggiungi anche `import com.whatstats.domain.model.NamedReplyStats` in cima al file test.

**Fatto quando:** i test passano; `HighlightEngine` compila con le nuove varianti.

---

### B6 - Badge su persone (ICEBREAKER, QUICK_DRAW)

**Obiettivo:** aggiungere 2 badge basati sulle metriche di risposta 1-a-1, uno dei quali solo Root.

**File:**
- `app/src/main/java/com/whatstats/domain/model/BadgeModels.kt` (modifica)
- `app/src/main/java/com/whatstats/domain/engagement/BadgeEngine.kt` (modifica)
- `app/src/test/java/com/whatstats/domain/engagement/BadgeEngineTest.kt` (modifica)

**BadgeModels.kt - cosa fare:** `BadgeId` guadagna `val rootOnly: Boolean = false`, e due nuove entry:
```kotlin
ICEBREAKER("icebreaker", 70),
QUICK_DRAW("quick_draw", 50, rootOnly = true),
```
Aggiorna il KDoc di `target`: per questi due badge e' una percentuale, non un conteggio assoluto.

**BadgeEngine.kt - cosa fare:**
1. Aggiungi `const val MIN_CONVERSATIONS_FOR_ICEBREAKER = 20` e `const val MIN_REPLIES_FOR_QUICK_DRAW = 50`.
2. Cambia la firma di `evaluate`:
   ```kotlin
   fun evaluate(
       stats: GlobalStats,
       oneToOne: List<ChatReplyStats>,
       isLiteMode: Boolean,
       alreadyUnlocked: Set<String>,
   ): List<BadgeProgress> {
   ```
3. Calcola gli aggregati prima del `map`:
   ```kotlin
   val iStarted = oneToOne.sumOf { it.iStarted.toLong() }
   val conversations = oneToOne.sumOf { it.iStarted.toLong() + it.theyStarted }
   val myReplies = oneToOne.sumOf { it.myReplyCount.toLong() }
   val myFastReplies = oneToOne.sumOf { it.myFastReplyCount.toLong() }
   ```
4. Filtra il catalogo prima di mapparlo: `BadgeId.entries.filter { !(it.rootOnly && isLiteMode) }.map { id -> ... }`.
5. Nel `when`, aggiungi:
   ```kotlin
   BadgeId.ICEBREAKER -> share(iStarted, conversations, id.target, MIN_CONVERSATIONS_FOR_ICEBREAKER)
   BadgeId.QUICK_DRAW -> share(myFastReplies, myReplies, id.target, MIN_REPLIES_FOR_QUICK_DRAW)
   ```

**BadgeEngineTest.kt - cosa fare:** l'helper `evaluate` diventa:
```kotlin
private fun evaluate(
    stats: GlobalStats,
    oneToOne: List<ChatReplyStats> = emptyList(),
    isLiteMode: Boolean = false,
    alreadyUnlocked: Set<String> = emptySet(),
): Map<BadgeId, BadgeProgress> =
    BadgeEngine.evaluate(stats = stats, oneToOne = oneToOne, isLiteMode = isLiteMode, alreadyUnlocked = alreadyUnlocked)
        .associateBy { it.id }
```
Tutti i test esistenti restano validi (chiamano `evaluate(stats)` o `evaluate(stats, alreadyUnlocked = ...)`, i nuovi parametri hanno default). Aggiungi:
```kotlin
@Test
fun rootOnlyBadgesAreHiddenInLite() {
    assertTrue(BadgeId.QUICK_DRAW in evaluate(globalStats(), isLiteMode = false).keys)
    assertFalse(BadgeId.QUICK_DRAW in evaluate(globalStats(), isLiteMode = true).keys)
}

@Test
fun icebreakerUsesOneToOneTotals() {
    val oneToOne =
        listOf(
            replyStats(chatId = 1, iStarted = 10, theyStarted = 5),
            replyStats(chatId = 2, iStarted = 4, theyStarted = 1),
        )
    assertTrue(evaluate(globalStats(), oneToOne = oneToOne).getValue(BadgeId.ICEBREAKER).unlocked)
    val locked = listOf(replyStats(chatId = 1, iStarted = 19, theyStarted = 0))
    assertFalse(evaluate(globalStats(), oneToOne = locked).getValue(BadgeId.ICEBREAKER).unlocked)
}

@Test
fun quickDrawNeedsHalfRepliesUnderFiveMinutes() {
    val unlocked = listOf(replyStats(chatId = 1, myReplyCount = 50, myFastReplyCount = 25))
    assertTrue(evaluate(globalStats(), oneToOne = unlocked).getValue(BadgeId.QUICK_DRAW).unlocked)
    val locked = listOf(replyStats(chatId = 1, myReplyCount = 50, myFastReplyCount = 24))
    assertFalse(evaluate(globalStats(), oneToOne = locked).getValue(BadgeId.QUICK_DRAW).unlocked)
}
```

**Fatto quando:** tutti i test (vecchi e nuovi) passano.

---

### B7 - formatReplyDuration condivisa

**Obiettivo:** estrarre la formattazione di durata (oggi duplicata e privata in 2 file) in una funzione condivisa.

**File:**
- `app/src/main/java/com/whatstats/ui/component/DurationFormat.kt` (nuovo)
- `app/src/main/java/com/whatstats/ui/screen/chatdetail/ChatDetailScreen.kt` (modifica)

**Cosa fare:** nuovo file:
```kotlin
package com.whatstats.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.whatstats.R
import java.util.concurrent.TimeUnit

@Composable
fun formatReplyDuration(ms: Long): String {
    if (ms <= 0) return "-"
    val hours = TimeUnit.MILLISECONDS.toHours(ms)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
    return when {
        hours > 0 -> "${hours}${stringResource(R.string.unit_hours)} ${minutes}${stringResource(R.string.unit_minutes)}"
        minutes > 0 -> "$minutes ${stringResource(R.string.unit_min_full)}"
        else -> stringResource(R.string.less_than_one_min)
    }
}
```

In `ChatDetailScreen.kt`: cancella la funzione privata `formatDuration`, cambia l'unica chiamata (`value = formatDuration(s.averageResponseTimeMs)`) in `value = formatReplyDuration(s.averageResponseTimeMs)`, aggiungi l'import di `formatReplyDuration`, rimuovi l'import di `java.util.concurrent.TimeUnit` se non e' piu' usato altrove nel file.

**Attenzione:** NON toccare la `formatDuration` privata della schermata Chiamate, resta com'e' (e' un secondo file, fuori scope di questo task).

**Fatto quando:** `ChatDetailScreen` compila e mostra lo stesso valore di prima per il tempo di risposta medio.

---

### B8 - Telemetria highlight_shared

**Obiettivo:** tracciare quando l'utente condivide una card come immagine.

**File:**
- `app/src/main/java/com/whatstats/telemetry/TelemetryRepository.kt` (modifica)
- `docs/Firebase/Firebase_Analytics_Forced_Update_Plan.md` (modifica)

**Cosa fare:** aggiungi, con la stessa forma di A7:
```kotlin
    fun trackHighlightShared(cardId: String) {
        logIfAllowed(eventName = "highlight_shared", extra = bundleOf("card_id" to cardId))
    }
```
Nel documento Firebase, dopo la riga evento di `badge_unlocked`: `` - `highlight_shared` (card highlight condivisa come immagine, Release B) ``; dopo la riga parametri di `badge_unlocked`: `` - `highlight_shared`: card_id ``. Aggiungi l'evento alla RC allowlist se non vuota.

**Fatto quando:** compila.

---

### B9 - Stringhe Release B

Le 9 chiavi B1-B9 in tutte le 13 locale sono gia' elencate dentro A8 (sotto ogni locale, riga "B keys"). Aggiungile insieme o subito dopo le chiavi A, seguendo le stesse regole di escaping/formato di A8.

---

### B10 - ViewModel: integrare le persone

**Obiettivo:** far apparire le card su persone in `HighlightsViewModel`/`BadgesViewModel`.

**File:**
- `app/src/main/java/com/whatstats/ui/screen/highlights/HighlightsViewModel.kt` (modifica)
- `app/src/main/java/com/whatstats/ui/screen/badges/BadgesViewModel.kt` (modifica)

**HighlightsViewModel - cosa fare:** aggiungi al costruttore `private val engagementRepository: EngagementRepository,` e `private val telemetryRepository: TelemetryRepository,`. In `load()`:
```kotlin
// le card sulle persone sono un extra: se falliscono, le card base restano visibili
val people = try { engagementRepository.getOneToOneReplyStats() } catch (e: Exception) { emptyList() }
val personCards = HighlightEngine.buildPersonHighlights(people, includeReplyTimes = !isLite)
UiState.Success(HighlightEngine.build(stats) + personCards)
```
(serve anche leggere `isLite = analyticsRepository.isLiteMode.value` prima, come in `BadgesViewModel`). Aggiungi anche:
```kotlin
fun onHighlightShared(cardKey: String) {
    telemetryRepository.trackHighlightShared(cardId = cardKey)
}
```

**BadgesViewModel - cosa fare:** aggiungi al costruttore `private val engagementRepository: EngagementRepository,`. In `load()`, prima di chiamare `BadgeEngine.evaluate`:
```kotlin
val oneToOne = try { engagementRepository.getOneToOneReplyStats().map { it.stats } } catch (e: Exception) { emptyList() }
val badges = BadgeEngine.evaluate(stats = stats, oneToOne = oneToOne, isLiteMode = isLite, alreadyUnlocked = stored.unlockedIds)
```

**Fatto quando:** compila; con dati reali compaiono le card/badge su persone dove i requisiti sono soddisfatti.

---

### B11 - UI: dettaglio, condivisione, nuove icone

**Obiettivo:** mostrare le nuove varianti di Highlight, aggiungere il pulsante di condivisione immagine solo dove ammesso.

**File:**
- `app/src/main/java/com/whatstats/ui/screen/highlights/HighlightsScreen.kt` (modifica)
- `app/src/main/java/com/whatstats/ui/screen/badges/BadgesScreen.kt` (modifica)

**HighlightsScreen.kt - cosa fare:**
1. `HighlightContent` guadagna `val detail: String? = null`; `quipsRes` diventa `Int?`.
2. `quips = content.quipsRes?.let { stringArrayResource(it) } ?: emptyArray()`. Mostra il testo di `detail` (stile `titleSmall`) solo se non nullo; mostra la quip solo se non vuota.
3. Aggiungi in fondo alla card una filigrana `Text(stringResource(R.string.app_name), style = labelMedium, color = onSurfaceVariant)`.
4. Nuovi branch nel `when`:
   - `FastestReplier`: icona `Icons.Outlined.Speed`, titolo `highlight_fastest_replier_title`, valore = nome, `quipsRes = null`, `detail = stringResource(R.string.highlight_reply_average_detail, formatReplyDuration(highlight.averageMs))`.
   - `YouReplyFastest`: come sopra ma icona `Icons.Outlined.Bolt`, titolo `highlight_you_reply_fastest_title`.
   - `YouStartMost`: icona `Icons.Outlined.WavingHand`, titolo `highlight_you_start_most_title`, valore = nome, `detail = stringResource(R.string.highlight_you_start_most_detail, highlight.percent)`.
5. Nuovo componente `HighlightPage(highlight, daySeed, onShared: (String) -> Unit)` che sostituisce la singola `HighlightCard` dentro il pager:
   - `context = LocalContext.current`, `scope = rememberCoroutineScope()`, `graphicsLayer = rememberGraphicsLayer()`, `var lastShareClickTime by remember { mutableLongStateOf(0L) }`.
   - Layout: `Column { Box(Modifier.weight(1f).drawWithContent { graphicsLayer.record { this@drawWithContent.drawContent() }; drawLayer(graphicsLayer) }.background(MaterialTheme.colorScheme.background)) { HighlightCard(highlight, daySeed) }; if (highlight.shareable) OutlinedButton(onClick = { ... }) { Icon(Icons.Outlined.Share, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.share)) } }`.
   - `onClick`: debounce 1500 ms su `lastShareClickTime`, poi `scope.launch { val bitmap = graphicsLayer.toImageBitmap().asAndroidBitmap(); val uri = writeHighlightPng(context, bitmap); val intent = Intent(Intent.ACTION_SEND).apply { type = "image/png"; putExtra(Intent.EXTRA_STREAM, uri); clipData = ClipData.newRawUri("", uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }; context.startActivity(Intent.createChooser(intent, null)); onShared(highlight.key) }`.
6. Funzione privata:
   ```kotlin
   private suspend fun writeHighlightPng(context: Context, bitmap: Bitmap): Uri =
       withContext(Dispatchers.IO) {
           val dir = File(context.cacheDir, "highlight_share")
           dir.deleteRecursively()
           dir.mkdirs()
           val file = File(dir, "highlight_${System.currentTimeMillis()}.png")
           file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
           FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
       }
   ```
   Usa la cartella `highlight_share`, non `share`, per non collidere con altre cache di condivisione gia' esistenti.
7. `HighlightsPager` prende anche `onShared: (String) -> Unit` e lo passa a `HighlightPage`; `HighlightsScreen` lo collega a `viewModel::onHighlightShared`.
8. Import da aggiungere: `android.content.ClipData`, `android.content.Context`, `android.content.Intent`, `android.graphics.Bitmap`, `android.net.Uri`, `androidx.compose.material.icons.outlined.Share`, `Speed`, `Bolt`, `WavingHand`, `androidx.compose.material3.OutlinedButton`, `androidx.compose.runtime.mutableLongStateOf`, `rememberCoroutineScope`, `setValue`, `androidx.compose.ui.draw.drawWithContent`, `androidx.compose.ui.graphics.asAndroidBitmap`, `androidx.compose.ui.graphics.layer.drawLayer`, `androidx.compose.ui.graphics.rememberGraphicsLayer`, `androidx.compose.ui.platform.LocalContext`, `androidx.core.content.FileProvider`, `kotlinx.coroutines.Dispatchers`, `launch`, `withContext`, `java.io.File`, `com.whatstats.ui.component.formatReplyDuration`.

**BadgesScreen.kt - cosa fare:** aggiungi i branch nel `when(id)` di `badgeText`:
- `ICEBREAKER`: icona `Icons.Outlined.AcUnit`, titolo `badge_icebreaker_title`, descrizione `badge_icebreaker_desc` con argomenti `(id.target, BadgeEngine.MIN_CONVERSATIONS_FOR_ICEBREAKER)`.
- `QUICK_DRAW`: icona `Icons.Outlined.Bolt`, titolo `badge_quick_draw_title`, descrizione `badge_quick_draw_desc` con argomenti `(id.target, BadgeEngine.MIN_REPLIES_FOR_QUICK_DRAW)`.

**Fatto quando:** le card su persone appaiono correttamente, il pulsante condividi compare solo su quelle senza nome, il PNG si apre nel foglio di condivisione Android.

---

### B12 - Test manuali di chiusura Release B

- Le card su persone appaiono in Root; in Lite compare solo `YouStartMost`.
- Solo le card senza nome mostrano il pulsante condividi.
- Il PNG condiviso si apre correttamente nel chooser Android.
- `QUICK_DRAW` e' nascosto in Lite Mode.
- Il primo calcolo delle metriche di risposta puo' essere lento su archivi grandi; dopo la prima volta e' cache.

---

## 6. Release C - Evoluzione mensile e pulizia Vico

Da rifinire ulteriormente rispetto ad A e B: qui sotto solo la traccia, alcune decisioni sono segnate esplicitamente "da approfondire".

### C1 - Grafico a linea per l'evoluzione mensile

- Componente Canvas/Compose nativo `MonthlyTrendChart(points: List<MonthlyCount>)` in `ChatDetailScreen`, NON Vico.
- I mesi senza dati vanno riempiti con valore 0, non saltati (altrimenti la linea si comprime sui mesi con dati).
- `MessageDao.getMonthlyTrend` raggruppa in UTC (fatto 16, sezione 2): da approfondire se serve passare a locale (aggiungendo `'localtime'` alla query) e, in tal caso, cambiare la chiave della cache statistiche della chat per invalidare i dati vecchi.
- Posizionamento nella schermata e criteri di eventuale sblocco con rewarded ad: da approfondire.

### C2 - Tempo di risposta mensile per singola chat

- Nuovo metodo DAO filtrato per `chatId`, stesso principio di scansione sequenziale di B1/B2 (niente `LAG`/`OVER`).
- Raggruppamento per mese locale puro in Kotlin, via `Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault())` convertito a `YearMonth`, non in SQL.
- Media pesata = somma dei tempi / conteggio risposte. Solo Root Mode.

### C3 - Rimozione Vico

- Rimuovi le dipendenze `com.patrykandpatrick.vico:compose-m3:1.13.1` e `:core:1.13.1` da `app/build.gradle.kts` (circa righe 198-200).
- Esegui `grep -rn "vico" app/src/` per confermare che non resti nessun riferimento prima di rimuovere le dipendenze.

### C4 - Da approfondire

- Classifica basata sul tasso di utilizzo emoji.
- Richiesta di recensione in-app dopo lo sblocco di un badge (puo' riusare `ui/review/InAppReviewRules` gia' esistente, da verificare se e' il punto di innesto giusto).

---

## 7. Restyling grafici esistenti (indipendente dalle Release A/B/C)

**Obiettivo:** animare l'ingresso delle barre di `InteractiveBarChart` e `InteractiveHourlyHeatmap`, senza toccare tooltip/interazione gia' funzionanti.

**File:** `app/src/main/java/com/whatstats/ui/component/InteractiveChart.kt` (modifica)

**Cosa fare:** dopo l'eventuale return anticipato di ciascun composable, aggiungi:
```kotlin
val growth = remember { Animatable(0f) }
LaunchedEffect(data) {
    growth.snapTo(0f)
    growth.animateTo(1f, animationSpec = tween(durationMillis = 450))
}
```
(in `InteractiveHourlyHeatmap` la chiave di `LaunchedEffect` e' il parametro dati di quel composable, es. `hourlyData`, non letteralmente `data`). Poi moltiplica SOLO l'altezza gia' calcolata per `growth.value`:
- in `InteractiveHourlyHeatmap`: `.height((44 * ratio * growth.value).dp.coerceAtLeast(2.dp))`
- in `InteractiveBarChart`: `.height((barHeight.value * ratio * growth.value).dp.coerceAtLeast(2.dp))`

Import da aggiungere: `androidx.compose.animation.core.Animatable`, `androidx.compose.animation.core.tween`.

**Attenzione:** prima di toccare `ui/component/HourlyHeatmap.kt` (fatto 15, sezione 2) e il blocco `ChartColors` in `ui/theme/Color.kt` (righe 92-115), esegui `grep -rn "HourlyHeatmap(\|ChartColors"` sull'intero modulo `app/src/main` per confermare che non siano usati da nessuna schermata. Se confermato, cancellali (rimozione, non li animare).

**Fatto quando:** cambiando il filtro temporale su Dashboard o ChatDetail le barre si animano invece di scattare istantaneamente; nessuna regressione sui tooltip esistenti; il codice morto confermato e' stato rimosso.

---

## 8. Fuori scope

- Statistiche sui vocali.
- Highlight per singola chat dentro `ChatDetailScreen` (questo piano copre solo Highlights/Badge globali).
- Badge con rarita'/tier (Bronze/Silver/Gold).
- Flag Remote Config per attivare/disattivare la feature.
- Data di sblocco salvata per i badge (solo stato sbloccato/bloccato).
- Nuova tab nella `BottomNavigationBar`.
- Pallino "nuovo" sulla card di ingresso in Dashboard.
- Tap-per-aprire-la-chat sulle card che citano una persona.

---

## 9. Checklist di chiusura del piano

- [ ] Release A: le 2 card di ingresso compaiono in Dashboard, in Root e in Lite.
- [ ] Release A: `ktlintCheck` e compilazione debug/release puliti su tutti i file toccati.
- [ ] Release A: i 19 test JUnit degli engine passano.
- [ ] Release A: `fullReset` azzera i badge; "Elimina cache" NON li tocca.
- [ ] Release A: nessuna stringa utente hardcoded fuori da `strings.xml`; tutti gli array hanno 3 item in tutte le 13 locale.
- [ ] Release B: le card su persone rispettano le soglie e la differenza Root/Lite.
- [ ] Release B: la condivisione immagine funziona solo sulle card senza nome; il PNG e' leggibile nel chooser.
- [ ] Release B: `QUICK_DRAW` non compare in Lite Mode.
- [ ] Release C: grafico evoluzione mensile con Canvas nativo, non Vico; dipendenze Vico rimosse.
- [ ] Restyling: animazione barre/heatmap attiva; codice morto (`ChartColors`, `HourlyHeatmap.kt`) verificato e rimosso se confermato orfano.
- [ ] Nessun commit e' stato fatto durante l'esecuzione di questo piano senza autorizzazione esplicita.
