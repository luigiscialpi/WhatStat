# WhatStat - Piano di ottimizzazione prestazioni (DB grandi)

Data: 2026-11-27
Stato: BOZZA da approvare. Nessuna modifica al codice di produzione fatta. D4 e D5 decise. D6 e D8 rimandate.
Riferimento codice: branch `master`, commit `0e05756`.
Task o Jira: da associare.

Obiettivo: DB da circa 1M messaggi. Oggi Dashboard, ChatDetailScreen e "Analisi lessicale" durano minuti. Il dato viene dall'utente, non e' ancora misurato su device.

Il documento e' scritto per un esecutore (AI o persona) che NON conosce il codice. La sezione 6 definisce i test che l'AI esegue dopo ogni fase.

## Indice

0. Regole per chi esegue
1. Obiettivo e perimetro
2. Decisioni
3. Fatti verificati e trappole
4. Baseline misurata
5. Piano di sviluppo (Fasi 0-3)
6. Piano di test
7. Fuori scope
8. Registro risultati
9. Checklist di chiusura

---

## 0. Regole per chi esegue

- Simbolo citato non trovato nel codice: FERMATI e chiedi. Non inventare alternative.
- Numeri di riga approssimativi (`~`). Cerca sempre per nome di simbolo.
- Una fase = una serie di patch piccole. Ogni patch: unified diff `git apply`-ready, provata con `git apply --check` su clone pulito. Consegna in sequenza, incrementale. Mai cumulativa su repo gia' patchata.
- Mai commit senza permesso esplicito. Commit in inglese.
- Solo ASCII. Niente emoji. Import in ordine lessicografico, niente wildcard, niente riferimenti fully-qualified inline. Stringhe utente solo in `strings.xml`.
- Dopo ogni task che tocca Kotlin, in ordine:
  1. `./gradlew ktlintFormat`
  2. `./gradlew ktlintCheck`
  3. `./gradlew :app:compileDebugKotlin :app:compileReleaseKotlin`
  4. `./gradlew :app:testDebugUnitTest`
- Niente query pesanti sul main thread.
- Output numerici INVARIATI. Non cambiare semantica mentre ottimizzi (sezione 2, D1).
- Mai modificare oracolo, golden o test per far passare un test (sezione 6.7).
- Mai marcare PASS un test non eseguito (sezione 6.7).
- Non toccare: `import` Lite (`LiteDbWriter.writeChat`), formato file indice `.gz`, chiavi DataStore (salvo Fase 3).

---

## 1. Obiettivo e perimetro

Ridurre i tempi di lettura. Nessun numero mostrato cambia.

Dentro:
- Analisi lessicale (parole ed emoji), globale e per chat.
- Query delle distribuzioni (oraria, settimanale, giorno del mese, mese dell'anno, anno, trend mensile).
- Memoria usata durante l'analisi.
- Progress e cancellazione dell'analisi.

Fuori: scrittura dell'import Lite. Gia' ottimizzata: transazione unica, prepared statement, indici creati dopo l'insert.

---

## 2. Decisioni

| ID | Argomento | Decisione | Stato |
|---|---|---|---|
| D1 | Semantica | Tutti gli output identici al codice attuale. Prova: sezione 6. | Decisa |
| D2 | File indice `.gz` e chiavi cache | Invariati in Fase 1 e 2. Nessun bump di versione. | Decisa |
| D3 | Soglie di accettazione | Relative alla baseline misurata nello stesso ambiente. Mai assolute. | Decisa |
| D4 | Worker paralleli lessicale | 3. Il pool WAL di Android ha tipicamente 4 connessioni: ne resta 1 per la UI. Verificare su device che il valore regga. | Decisa |
| D5 | Parimerito nelle top-N parole/emoji | Conteggio decrescente, poi parola crescente. Oggi l'ordine dipende dall'ordine di inserzione nella mappa e con il parallelismo cambierebbe. Cambia l'ordine visibile dei parimerito rispetto a oggi: e' voluto. | Decisa |
| D6 | Pre-aggregazione (Fase 3) | Proposta: file SQLite separato `stats_agg_<ns>.db`, non tabelle dentro `msgstore.db`. | Rimandata |
| D7 | Librerie di test | Niente Robolectric, niente MockK. Logica pura in classi senza dipendenze Android, testate in JVM. | Proposta |
| D8 | Test a livello repository | `AnalyticsRepository` ha un costruttore con molte dipendenze. Niente Hilt in androidTest: i test stanno a livello DAO e aggregatore. Alternativa: aggiungere `hilt-android-testing`. | Rimandata |

---

## 3. Fatti verificati e trappole

Verificati leggendo il codice. File sotto `app/src/main/java/com/whatstats/`.

### 3.1 Analisi lessicale

1. `data/db/dao/MessageDao.kt`, `buildLexicalDeltaStream` (~2525). Per ogni messaggio: `Calendar` + `String.format` per il mese, regex URL (`replaceAll`), regex di pulizia, `split`. Per ogni parola: regex di normalizzazione. Due update di mappa (globale e mensile) con boxing. Un solo thread.
2. Stessa tokenizzazione copiata in 5 punti: `getGlobalWordsAndEmojis` (~286), `getTopWords` (~694), `getWordsAndEmojis` (~829), `buildLexicalDeltaStream` (~2525), `buildWordFrequencyMap` (~2620).
3. `extractEmojis`, `stripSkinTone`, `isEmoji` sono `private` in `MessageDao`. `buildEmojiFrequencyMap` usa `android.util.Log`. Non testabili in JVM.
4. `getEmojiStatsBySender` (~908): carica TUTTI i testi della chat in `MutableMap<String, MutableList<String>>`. `getSlowChatStats` (`data/repository/AnalyticsRepository.kt` ~778) lo chiama oltre all'indice per-chat. Risultato: seconda scansione dei testi e rischio OutOfMemory.
5. `getSlowGlobalStats` (`AnalyticsRepository.kt` ~441-445): progress 5, 10, poi 80. Nessun aggiornamento durante la scansione. L'utente vede la barra ferma.
6. Il ciclo del cursore non e' sospendibile. Non c'e' nessun controllo di cancellazione dentro la scansione. Da verificare con test C-4.
7. Indice mensile: `CacheManager.loadMonthlyWordIndexFromDisk` (~953) carica tutto in mappe annidate. `buildOrUpdateGlobalWordIndex` (`AnalyticsRepository.kt` ~1708-1710) ne fa una copia con `toMutableMap()`. RAM misurata: ~128 MB per 1,4M righe (JVM desktop).
8. Ordinamento top-N: `sortedByDescending` sulla mappa. A parita' di conteggio vale l'ordine di inserzione (primo incontrato nella scansione).

### 3.2 Distribuzioni Dashboard e ChatDetail

1. Globali: `getGlobalHourlyDistribution`, `getGlobalWeeklyDistribution`, `getGlobalDayOfMonthDistribution`, `getGlobalMonthOfYearDistribution`, `getGlobalYearTrendDistribution` (`MessageDao.kt` ~211, ~341, ~379, ~417, ~495). Per chat: le versioni senza `Global` (~171-650) + `getMonthlyTrend` (~650). Ognuna scandisce `message` con `LEFT JOIN jid`, `JOIN chat`, `JOIN jid` e `strftime(..., datetime(ts / 1000, 'unixepoch', 'localtime'))`.
2. Chiamate in parallelo da `getQuickGlobalStats` (`AnalyticsRepository.kt` ~217-387) e `getQuickChatStats` (~539-716). Il pool WAL di Android limita il parallelismo reale (tipicamente 4 connessioni).
3. Cache solo per `ALL_TIME` (`AnalyticsRepository.kt` ~226, ~548, ~737). Ogni altro filtro ricalcola tutto.
4. Ordine del breakdown (`parse*Count`, `MessageDao.kt` ~2361-2460): `sortedByDescending { count }`, stabile. Base = ordine delle righe SQL = `GROUP BY bucket, jid`, quindi jid crescente in collation BINARY (byte UTF-8). La UI mostra i primi 3 (`ui/component/InteractiveChart.kt` ~317). L'ordine dei parimerito si vede.
5. Chiave mittente: `from_me = 1` -> `me@s.whatsapp.net`. `sender_jid_row_id > 0` -> `jid.raw_string` di quel mittente (NULL -> `"unknown"`). Altrimenti jid della chat.
6. `JOIN chat` e `JOIN jid` sono INNER: i messaggi orfani (chat o jid mancante) sono esclusi dalle distribuzioni. `getTotalMessageCount` non li esclude.
7. Filtro tipi: `message_type NOT IN (7, 15, 90)`.
8. `timestamp` non e' NOT NULL nello schema Root. Le distribuzioni NON filtrano `timestamp` 0 o NULL. `getActiveDates` filtra `timestamp > 0`.
9. Streak: `getActiveDates` usa giorni UTC (`timestamp / 86400000`). Le distribuzioni usano ora locale. NON unificare.
10. Ora locale: SQLite `'localtime'` = fuso corrente del device, con DST. Convenzione `dayOfWeek`: 0 = domenica, 6 = sabato. In Kotlin: `DayOfWeek.value % 7`.
11. Campi che dipendono dall'ora corrente (da escludere dai confronti golden): `milestonePredictionState`, `averageMessagesPerDay` in `ALL_TIME`.

### 3.3 Altre letture

1. `getMessageCountForChat` (~91) e `getMessageCountByChat` (~146) contano le parole in SQL: `LENGTH(text_data) - LENGTH(REPLACE(text_data, ' ', '')) + 1` su ogni testo. Un testo vuoto vale 1 parola. Costo misurato: ~1,1 s su 1M messaggi sintetici (secondario).
2. `getAverageMessageLength` e `getLongestMessage` (`ORDER BY LENGTH(text_data) DESC`) scandiscono i testi della chat.
3. Root DB: `WhatsAppDatabase.open` (`data/db/WhatsAppDatabase.kt` ~52, ~63) applica solo WAL e `createIndexes`. Nessun PRAGMA. Lite: PRAGMA sulla connessione primaria (`data/lite/LiteDatabase.kt` ~29-32). Un PRAGMA per-connessione non si propaga alle connessioni secondarie del pool. Da verificare su device.
4. Schema reale `message`: 23 colonne (`docs/DBs Structures/DB_Messages.md` ~446). Il DB sintetico dei benchmark ne ha 10. Il guadagno da indice coprente sul DB reale dovrebbe essere maggiore. Non misurato.
5. `jid_map.lid_row_id` e' PRIMARY KEY nello schema Root (`DB_Messages.md` ~364). I `LEFT JOIN jid_map` non sono un problema in Root.

### 3.4 Infrastruttura di test e ambiente

1. Dipendenze test: solo `junit:junit:4.13.2` e `kotlinx-coroutines-test:1.9.0`. Nessun `testOptions`: `android.util.Log` lancia eccezione nei test JVM.
2. Non esiste `app/src/androidTest`. Esistono `src/debug`, `src/main`, `src/test`.
3. Gli engine puri stanno in `domain/` con test in `app/src/test/java/com/whatstats/domain/...`. Stesso schema per il nuovo codice.
4. `.gitignore` esclude `*.db`. I DB sintetici si generano. Non si committano.
5. Sandbox cloud usato per scrivere questo piano: `ANDROID_HOME` assente. Il wrapper Gradle non scarica la distribuzione. I livelli A e C non erano eseguibili li'.

---

## 4. Baseline misurata

Preliminare. Da rifare su device in Fase 0.

Ambiente: SQLite 3.53.1, JVM desktop, 2 core, fuso Europe/Rome. DB sintetico da 1M messaggi, 200 chat, tabella da 10 colonne.

| Misura | Attuale | Alternativa | Note |
|---|---|---|---|
| 5 distribuzioni globali | 5,51 s (1,07-1,12 s ciascuna) | 1 passata su indice coprente: 0,11 s | Solo motore SQL. Esclusi cursor e aggregazione Kotlin. |
| Query oraria, isolamento | 3 JOIN + localtime: 1,07 s. Solo localtime: 0,85 s. Solo JOIN: 0,69 s. Nessuno dei due: 0,54 s | - | Il costo grosso e' la scansione ripetuta 5 volte. |
| Lessicale, 850K testi | 6,95 s | 1,49 s (4,7x). Con 2 thread 0,89 s (7,8x) | Tokenizer a mano, risultati identici. |
| Equivalenza tokenizer | - | 0 differenze su 134.898 code point x 6 contesti (809.388 stringhe) e 300.000 stringhe casuali | JVM desktop. |
| Parole con `REPLACE` | 1,21 s | 0,08 s senza | Su 1M messaggi. |
| Creazione 5 indici su `message` | 1,92 s | Indice coprente in piu': 0,31 s | Una tantum. |
| Indice mensile 1,42M righe | Scrittura 0,46 s. File 6,4 MB. Lettura 0,64 s. ~128 MB RAM | - | Rischio memoria su Android. |

Limiti: Android e' piu' lento in assoluto. I rapporti sono indicativi. Le 5 query nell'app girano in parallelo: il tempo reale sta sotto la somma. Gli script non sono ancora nel repo: nascono in Fase 0 sotto `tools/perf/`.

---

## 5. Piano di sviluppo

Ordine obbligatorio: Fase 0, poi 1, poi 2. La Fase 3 parte solo dopo approvazione di un design separato.

### Fase 0 - Harness e baseline

Solo aggiunte. Nessuna modifica di comportamento.

| Passo | Cosa |
|---|---|
| 0.1 | `tools/perf/gen_synthetic_db.py`: generatore di DB sintetici (spec in 6.4). |
| 0.2 | `tools/perf/legacy_sql_oracle.py`: esegue le query SQL attuali, copiate verbatim da `MessageDao`. Intestazione con hash del commit. Salva JSON. |
| 0.3 | `app/src/test/java/com/whatstats/domain/lexical/LegacyLexicalReference.kt`: copia congelata della tokenizzazione attuale (con `extractEmojis`, senza `android.util.Log`). Commento in testa: NON MODIFICARE. |
| 0.4 | `app/src/androidTest/java/com/whatstats/perf/`: test strumentali che misurano i metodi attuali su dataset M e L. DAO costruiti a mano con un `DatabaseProvider` finto (interfaccia di 3 metodi). |
| 0.5 | Helper `PerfTrace` (solo Debug): log `WhatStatPerf: <metodo> <dataset> <ms>`. |
| 0.6 | Golden: output delle API attuali sul dataset S, salvati in `app/src/androidTest/assets/golden/S/`. Esclusi i campi di 3.2.11. |
| 0.7 | RIMANDATO. Metodo per portare i DB sintetici sul device. Lo sviluppo lo fa una AI locale: si decide quando si arriva a questo passo, in base a cosa sa fare. Opzioni note: `adb push` + copia, o generazione on-device. Prima di decidere, l'esecutore si ferma e chiede. |
| 0.8 | Baseline su almeno un device. Scrivere i numeri nella sezione 8. |

Fatto quando: baseline in sezione 8 per S, M, L. Golden S salvati. Tutti i test dei livelli A e B passano sul codice attuale.

### Fase 1 - Lessicale e streaming

Nessun cambio di schema. Nessun cambio di cache (D2).

| Patch | Cosa | Test |
|---|---|---|
| 1.1 | Estrai `EmojiExtractor` (`domain/lexical/`, puro) da `MessageDao`. Comportamento identico. | A-1 |
| 1.2 | Crea `LexicalTokenizer` e `LexicalAccumulator` (`domain/lexical/`, puri). Sostituisci le 5 copie. Regole esatte sotto. | A-2, A-3, A-4, C-1, C-3 |
| 1.3 | Progress reale in `buildLexicalDeltaStream`: callback `onProgress`, righe lette / `getGlobalTextMessageCount` (o `getChatTextMessageCount`), mappato su 10-80. Aggiorna al massimo ogni 1% o 2000 righe. Controllo di cancellazione ogni ~2000 righe. Nessun indice parziale se cancellato. | C-4, C-5 |
| 1.4 | Parallelismo: partizioni per `_id` (da `MIN(_id)` e `MAX(_id)`), max 3 worker (D4), merge delle mappe. Sotto 50.000 righe resta sequenziale (soglia proposta, da misurare). | A-4, A-8, C-1, C-2 |
| 1.5 | `getEmojiStatsBySender` in streaming: accumulatore puro `SenderEmojiAccumulator`, nessuna lista di testi. Usa `LinkedHashMap` per mantenere l'ordine di inserzione. | A-5, C-1 |
| 1.6 | Opzionale: evitare la copia `toMutableMap()` dell'indice mensile (`AnalyticsRepository.kt` ~1708). Verificare che la cache in memoria resti coerente. | A-4, C-6 |
| 1.7 | Opzionale, bassa priorita': parole per messaggio fuori da `getMessageCountForChat`. Il valore deve restare identico: `numero di ' ' + 1` per ogni testo (anche vuoto), diviso per `from_me`. | C-1 |

Regole esatte del tokenizer (patch 1.2). Equivalgono al codice attuale:

1. URL: rimuovi con la regex attuale (invariata) SOLO se il testo contiene `/` o `www.` (senza distinguere maiuscole). Senza questi, nessuna alternativa della regex puo' corrispondere.
2. Minuscolo con la semantica di `String.lowercase()` (`Locale.ROOT`). Caso speciale: U+0130 diventa `i` + U+0307. U+0307 non e' ammesso: separa la parola.
3. Caratteri ammessi: `a-z` e le lettere accentate elencate in `WORD_CLEANER` (`MessageDao.kt` ~65). In pratica U+00E0-U+00FD escluso U+00F7. Tutto il resto separa le parole, spazi inclusi.
4. Parola = sequenza massimale di caratteri ammessi.
5. Scarta se lunghezza <= 2 (PRIMA della normalizzazione) o se e' in `STOP_WORDS` (confronto sulla parola PRIMA della normalizzazione).
6. Normalizzazione: ogni sequenza di 3 o piu' caratteri uguali diventa 2 caratteri.
7. Mese `YYYY-MM` nel fuso di default. Cache del mese per bucket da 15 minuti (`ts / 900000`): esatto per ogni fuso reale, perche' gli offset sono multipli di 15 minuti. Va provato in A-4 con fusi a 30 e 45 minuti.
8. Emoji: estrai solo se il testo contiene un carattere >= U+2600. Il codice attuale non puo' trovare emoji sotto quella soglia. Le sequenze keycap contengono U+FE0F: passano il controllo.
9. Contatori `IntArray(1)` nelle mappe. Una sola ricerca hash per parola.

Fatto quando: A-1..A-5 e A-8 passano. C-1 e C-3 passano su S. Tempo lessicale su L secondo 6.6.

### Fase 2 - Una passata per Dashboard e ChatDetail

| Patch | Cosa | Test |
|---|---|---|
| 2.1 | Due indici coprenti, in `WhatsAppDatabase.createIndexes` e `LiteDatabase.ensureIndexes`: `idx_message_cover_global ON message (timestamp, message_type, chat_row_id, from_me, sender_jid_row_id)` e `idx_message_cover_chat ON message (chat_row_id, timestamp, message_type, from_me, sender_jid_row_id)`. NON rimuovere indici esistenti in questa fase. | B-3, B-4 |
| 2.2 | `ActivityAggregator` (`domain/activity/`, puro, `java.time`). Input: righe `(timestamp, fromMe, senderJidRowId, chatRowId)` in ordine di timestamp. `ZoneId` iniettabile (default `systemDefault`). Conversione locale incrementale con `ZoneRules` (l'offset cambia solo alle transizioni). Output: le 5 distribuzioni + trend mensile per chat. | A-6, A-7 |
| 2.3 | Query unica globale e per chat che legge le righe dall'indice coprente. Nomi dei mittenti risolti DOPO la passata, con una mappa `id -> raw_string`. Nessun JOIN per riga. Regole di invarianza sotto. | B-2, C-1 |
| 2.4 | `getQuickGlobalStats` e `getQuickChatStats` usano l'aggregatore. Le vecchie funzioni DAO restano finche' C-1 non passa su S e M. | C-1, C-2 |
| 2.5 | Patch finale della fase, separata: rimuovi le vecchie funzioni DAO. Prima verifica che i golden S e M esistano e siano stati registrati dal codice vecchio. | tutti |

Invarianti di 2.3 (vedi 3.2):
- Filtro `message_type NOT IN (7, 15, 90)`.
- Esclusi i messaggi orfani (chat o jid mancante).
- Chiave mittente come in 3.2.5.
- Breakdown: conteggio decrescente. A parita': jid crescente per ORDINE DEI CODE POINT. Equivale ai byte UTF-8 della collation BINARY. NON usare `String.compareTo`: ordina per UTF-16 e differisce per i caratteri supplementari (es. emoji nei nomi Lite).
- Liste ordinate per bucket crescente.
- `timestamp` 0 e NULL trattati come oggi: 0 diventa 1970-01-01 in ora locale. Verificare cosa fa il codice attuale con NULL e replicarlo.
- Streak invariato (3.2.9).
- Cache: nessuna modifica. Resta solo `ALL_TIME`.

Fatto quando: A-6, A-7, B-2, B-3, C-1 passano. Tempo su L secondo 6.6.

### Fase 3 - Pre-aggregazione persistente

Solo scheda di design. NON implementare senza approvazione.

Idea: tabelle riassuntive in un file SQLite separato (D6). Dashboard e filtri leggono tabelle piccole: millisecondi, anche per filtri oggi non in cache.

Tabelle proposte:
- `agg_activity(bucket_utc_15m, chat_id, sender_key, cnt)`
- `agg_word(month, chat_id, sender_key, word, cnt)`
- `agg_emoji(month, chat_id, sender_key, emoji, cnt)`
- `agg_meta(last_processed_ts, text_count, db_signature, schema_version)`

Punti da risolvere nel design:
- Costruzione incrementale col delta `lastProcessedTimestamp` (gia' usato dall'indice gz).
- Lite: popolare durante l'import (`TextChatParser`, `LiteDbWriter`).
- Invalidazione negli stessi punti dell'attuale cache (README sezione 3): `clearCache`, `clearRootCache`, `clearLiteCache`, `notifyLiteImportDone`, `notifyLiteChatDeleted`, `notifyLiteRenameChat`, `fullReset`.
- Parole con filtro: oggi l'indice mensile e' approssimato (range >= 28 giorni) e sotto usa SQL esatto. Mantenere questa semantica.
- Migrazione dal formato `.gz`. `PRAGMA user_version`.
- Test: parita' con golden, invalidazione, migrazione, cancellazione a meta' costruzione.

---

## 6. Piano di test

Li esegue l'AI dopo ogni fase.

### 6.1 Principi

- Oracolo = comportamento del codice attuale. Non l'intuizione.
- Si confrontano mappe complete, non solo top-N.
- Caso limite non coperto = bug aperto.
- Nessun test dipende dall'ora corrente.

### 6.2 Oracolo

| Area | Oracolo |
|---|---|
| Tokenizzazione, emoji | `LegacyLexicalReference` (patch 0.3). Congelato. |
| Distribuzioni | Funzioni DAO attuali, tenute in vita fino alla patch 2.5. Poi i golden (6.4). |
| Ora locale | CSV prodotto da SQLite `'localtime'` (B-1). |
| Output a livello DAO | Golden S registrati in Fase 0. |

### 6.3 Livelli

| Livello | Cosa verifica | Requisiti | Comando | Dove gira |
|---|---|---|---|---|
| A | Logica pura in JVM | Android SDK + Gradle | `./gradlew :app:testDebugUnitTest` | Mac, CI, AI con SDK |
| B | Query SQL, piani, tempi | Python 3 | `python3 tools/perf/<script>.py` | Ovunque, anche senza SDK |
| C | Parita' e tempi su device | Device o emulatore con adb | `./gradlew :app:connectedDebugAndroidTest` | Dove c'e' adb |
| D | Controllo manuale UI | Device | Checklist 6.5 | Luigi |

### 6.4 Dataset sintetici

Generatore: `tools/perf/gen_synthetic_db.py --schema {lite,root} --messages N --seed S --out FILE`.

| Nome | Messaggi | Uso |
|---|---|---|
| S | 50.000 | Golden e parita' |
| M | 300.000 | Parita' e tempi intermedi |
| L | 1.000.000 | Tempi |

Seed fisso: 1. Stesso seed = stesso DB.

Schema:
- `root`: colonne come `docs/DBs Structures/DB_Messages.md` (23 colonne in `message`) + `chat`, `jid`, `jid_map` (`lid_row_id` PRIMARY KEY), `message_media`, `call_log`.
- `lite`: come `LiteDatabase.createSchema`.

Distribuzione:
- 200 chat. Pesi di Zipf con esponente 0,9. Poche chat enormi.
- 20% gruppi: ricevuti con `sender_jid_row_id` tra 2 e 30. 1-a-1: `sender_jid_row_id = 0`.
- `from_me` al 45%.
- 10 anni di timestamp, in ordine crescente con rumore piccolo.
- 85% testo (`message_type = 0`). Il resto: 1, 2, 3, 9, 20. Piu' tipi di sistema 7, 15, 90.
- Testi da 1 a 18 parole. 70% da 45 parole comuni, 30% da un vocabolario di 20.000. Presenti: maiuscole, accentate, ripetizioni (`daiii`, `aaaaaaah`), punteggiatura.
- 10% con emoji: skin tone, ZWJ, bandiere, keycap, emoji singole.
- 5% con URL `https://...`. 1% con `www.`. 1% con `10/12`.

Casi forzati (sempre presenti, anche in S):
- `timestamp = 0`. In schema `root` anche NULL.
- Timestamp futuro.
- Messaggi a cavallo delle transizioni DST Europe/Rome: 2024-03-31 01:59 e 03:01 locali; 2024-10-27 02:30 locale (ambiguo).
- Messaggi a cavallo di mezzanotte locale, fine mese, fine anno.
- Testo vuoto `''`.
- Chat vuota. Chat con un solo messaggio.
- Messaggi orfani (chat o jid mancante).
- Mittente `@lid`.
- Mittente con emoji nel nome (schema `lite`).
- Caratteri speciali: U+0130, U+212A, U+212B, spazi unicode.

Golden: output delle API attuali su S, serializzati con `toString()` dei data class. Ordinare prima mappe e set senza ordine. Escludere i campi di 3.2.11.

### 6.5 Catalogo test

Livello A (JVM, `app/src/test/java/com/whatstats/domain/...`):

| ID | Fase | Cosa | Oracolo |
|---|---|---|---|
| A-1 | 1 | `EmojiExtractor` identico al codice attuale su tutti i code point BMP, piano 1 e fuzz | `LegacyLexicalReference` |
| A-2 | 1 | `LexicalTokenizer`: ogni code point (BMP + piano 1 + campione dei piani alti) in 6 contesti. Almeno 800.000 stringhe. Mappe complete identiche. | `LegacyLexicalReference` |
| A-3 | 1 | Fuzz: 300.000 stringhe casuali, seed 42, pool di caratteri "cattivi" (maiuscole, U+0130, spazi unicode, ZWJ, keycap, URL, `www.`, ripetizioni, stop word) | `LegacyLexicalReference` |
| A-4 | 1 | Merge di k parti == passata unica. Mese dalla cache == mese da `Calendar` su confini di mese, anno e DST, in piu' `ZoneId` | `LegacyLexicalReference` |
| A-5 | 1 | `SenderEmojiAccumulator`: stesso output e stesso ordine del codice attuale. Passa 500.000 testi generati al volo e misura l'heap usato dopo GC (misura approssimata) | `getEmojiStatsBySender` attuale |
| A-6 | 2 | `ActivityAggregator` contro CSV `localtime` per Europe/Rome, America/New_York, Asia/Kolkata, Asia/Kathmandu, Australia/Lord_Howe, UTC. Include bordi DST e offset da 30 e 45 minuti | CSV da B-1 |
| A-7 | 2 | Semantica: chiave mittente, filtro tipi, orfani, `dayOfWeek` 0 = domenica, ordine del breakdown per code point, `timestamp` 0 | Funzioni DAO attuali |
| A-8 | 1 | Tie-break top-N (D5): conteggio decrescente, poi parola crescente. Risultato indipendente dal numero di worker | Test stesso |

Livello B (Python, `tools/perf/`):

| ID | Fase | Cosa |
|---|---|---|
| B-1 | 2 | Genera i CSV `localtime` con SQLite per la lista di fusi. Controllo incrociato con `zoneinfo` di Python. Salva in `app/src/test/resources/golden/`. |
| B-2 | 2 | La nuova SELECT a righe, aggregata con il riferimento Python, produce le stesse distribuzioni delle query attuali su S e M, per ogni fuso. Include i casi forzati. |
| B-3 | 2 | `EXPLAIN QUERY PLAN`: le nuove query usano `COVERING INDEX`. Le query invariate restano invariate. |
| B-4 | 2 | Tempi delle query attuali e nuove su M e L. Tabella baseline e dopo. |

Livello C (device, `app/src/androidTest/java/com/whatstats/perf/`):

| ID | Fase | Cosa |
|---|---|---|
| C-1 | 1, 2 | Parita': DAO e aggregatore contro i golden S, in schema `root` e `lite`. Anche su M contro le vecchie funzioni DAO. |
| C-2 | 1, 2 | Tempi su L: lessicale globale (ricostruzione completa), `getQuickGlobalStats` senza cache, `getQuickChatStats` sulla chat piu' grande. 3 esecuzioni. |
| C-3 | 1 | A-2 e A-3 sul device. Il minuscolo di Android usa ICU: puo' differire dalla JVM. |
| C-4 | 1 | Cancellazione durante la scansione: la scansione si ferma, nessun file indice parziale (il `.tmp` non viene rinominato). Riportare il tempo di arresto. |
| C-5 | 1 | Progress: monotono non decrescente, arriva a 100, almeno 20 aggiornamenti su L. |
| C-6 | 1 | Compatibilita' file indice: file `.gz` scritto dal codice vecchio letto dal nuovo, e viceversa. Delta merge corretto. |

Livello D (manuale, Luigi):
- Dashboard: 6 grafici, tooltip, dettaglio esteso. Valori uguali a prima.
- ChatDetail: nuvola parole, emoji, "Per Persona", sticker.
- Filtri: 30 giorni, 6 mesi, anno, intervallo personalizzato.
- Lite: importa una nuova chat. Dashboard si aggiorna.
- Lite: elimina chat. Rinomina chat.
- Analisi lessicale: barra di avanzamento si muove. Cancellazione funziona.
- Cambio fuso orario del device: la cache `ALL_TIME` resta con il vecchio fuso. Comportamento gia' presente, non e' una regressione.

### 6.6 Criteri di accettazione

Relativi (D3). Stesso device, stessa build. Build Release per i tempi. 3 esecuzioni: riportare la mediana, e la prima separata (cache fredda).

| Fase | Misura | Soglia |
|---|---|---|
| 1 | Lessicale su L, ricostruzione completa, 1 worker | <= baseline / 3 |
| 1 | Lessicale su L, 3 worker | <= baseline / 4 |
| 1 | Heap usato in A-5 (approssimato) | <= 64 MB per 500.000 testi |
| 2 | `getQuickGlobalStats` su L, senza cache | <= baseline / 3 |
| 2 | `getQuickChatStats` sulla chat piu' grande di L | <= baseline / 3 |
| 1, 2 | Apertura da cache | <= baseline + 10% |
| 1, 2 | Differenze nei test di parita' | 0 |

Riferimento desktop (sezione 4): lessicale 4,7x con 1 thread, 7,8x con 2. Distribuzioni: 52x sul solo motore SQL. Le soglie sopra sono volutamente piu' basse.

Se una soglia non e' raggiunta: NON abbassarla. Riporta i numeri e fermati.

### 6.7 Protocollo per l'AI

1. Prima di modificare: esegui i test sul codice attuale. Registra la baseline. Devono passare.
2. Dopo ogni patch: `ktlintFormat`, `ktlintCheck`, compilazione debug e release, livello A.
3. Dopo ogni fase: livello B. Livello C se c'e' adb.
4. Test fallito: correggi il CODICE. Mai oracolo, golden o test. Se pensi che il comportamento vecchio sia un bug: FERMATI e chiedi. E' una decisione di prodotto.
5. Livello non eseguibile (es. niente SDK, niente adb): scrivi `NON ESEGUITO`, il motivo, e il comando per chi puo' eseguirlo. Mai dedurre PASS.
6. Non lasciare DB, CSV temporanei o log nel repo.
7. Mai commit senza permesso esplicito.
8. Chiudi ogni fase con il report (template sotto) in sezione 8.

Template report:

| Test | Livello | Comando | Esito | Evidenza |
|---|---|---|---|---|
| A-2 | A | `./gradlew :app:testDebugUnitTest --tests '...'` | PASS, FAIL o NON ESEGUITO | Conteggi, differenze, motivo |

---

## 7. Fuori scope

- Scrittura dell'import Lite.
- Cambi alla semantica di qualunque statistica.
- Rimozione di indici esistenti (serve prima `EXPLAIN QUERY PLAN` sul DB reale).
- Radar Parole, Media Gallery, Log Chiamate, Highlights e Badge.
- Modifiche UI, salvo la barra di avanzamento della patch 1.3.

---

## 8. Registro risultati

Da riempire durante l'esecuzione.

Baseline per device:

| Device | Build | Dataset | Misura | Prima esecuzione | Mediana di 3 |
|---|---|---|---|---|---|
| - | - | - | - | - | - |

Report per fase: aggiungere una tabella (template in 6.7) per ogni fase chiusa.

---

## 9. Checklist di chiusura

- [ ] Fase 0: baseline registrata per S, M, L. Golden S salvati.
- [ ] Fase 0: tutti i test A e B passano sul codice attuale.
- [ ] Fase 1: A-1..A-5, A-8 passano.
- [ ] Fase 1: C-1, C-3, C-4, C-5, C-6 passano (oppure `NON ESEGUITO` con motivo e comando).
- [ ] Fase 1: soglie 6.6 raggiunte, con numeri nella sezione 8.
- [ ] Fase 2: A-6, A-7, B-1..B-4 passano.
- [ ] Fase 2: C-1 e C-2 passano. Soglie 6.6 raggiunte.
- [ ] Fase 2: vecchie funzioni DAO rimosse solo dopo C-1 su S e M.
- [ ] Decisioni D6 e D8 chiuse dal proprietario (D4 e D5 gia' decise).
- [ ] Passo 0.7 deciso e documentato.
- [ ] Livello D eseguito da Luigi.
- [ ] `ktlintCheck` e compilazione debug/release puliti.
- [ ] README sezione 3 (cache e prestazioni) aggiornata se cambia qualcosa di visibile.
- [ ] Nessun commit fatto senza autorizzazione esplicita.
