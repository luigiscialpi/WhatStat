# WhatStat - Scoperte, Bug e Soluzioni

## 📊 STATO ATTUALE (aggiornato 15 aprile 2026)

### ✅ FUNZIONA
- **Radar Parole**: conta occorrenze esatte per mittente, splitta Tu vs Altra persona nelle chat 1-a-1. Pre-filtro SQL con `LIKE` per velocizzare la ricerca.
- **Media & Link nella chat**: mostra direttamente il breakdown per mittente senza dover toccare. Search bar client-side per filtrare contatti.
- **Media & Link globale**: mostra ranking per chat con media + link. Search bar client-side.
- **Log Chiamate**: riepilogo totale + split durata audio/video + dettaglio per persona. Search bar client-side per filtrare contatti.
- **Filtri temporali**: tutti i range (Tutto, Oggi, Ieri, 7g, 30g, Anno, Personalizzato) funzionano in tutte le schermate
- **Parole più usate**: rispettano il range di date selezionato, default ALL_TIME
- **Pie chart per-membro**: donut chart con breakdown per mittente ("Chi scrive di più" e "Chi inizia le conversazioni"), espandibile con "Mostra tutti" per gruppi > 10 membri
- **Risoluzione nomi contatti**: nomi risolti via `wa_contacts` (wa.db), non più numeri di telefono
- **Progressive loading**: le stat di base appaiono in <1s, le stat lente (top words, pie chart, starters) arrivano dopo con indicatore "Calcolo in corso…"
- **Performance DB**: 7 indici creati alla prima apertura, WAL mode per letture parallele, query parallelizzate con async/await

### ❌ PROBLEMI NOTI
- Nessun problema critico aperto al momento

---

## 🐛 BUG RISOLTI

### Bug 1 — `sender_jid_row_id = 0` nelle chat 1-a-1
**Scoperta chiave (verificata con DB Browser):**
```sql
SELECT m.from_me, m.sender_jid_row_id, j.raw_string, COUNT(*) AS cnt
FROM message m
LEFT JOIN jid j ON m.sender_jid_row_id = j._id
WHERE m.chat_row_id = 256 AND m.message_type = 0 AND m.text_data IS NOT NULL
  AND LOWER(m.text_data) LIKE '%tardi%'
GROUP BY m.from_me, m.sender_jid_row_id, j.raw_string;

-- Risultato:
-- from_me=0, sender_jid_row_id=0, sender_jid=NULL → 1908
-- from_me=1, sender_jid_row_id=0, sender_jid=NULL → 837
```

Nelle chat 1-a-1, WhatsApp mette `sender_jid_row_id = 0` per **tutti** i messaggi (sia inviati che ricevuti). Il `LEFT JOIN jid ON sender_jid_row_id = j._id` fallisce sempre. Bisogna usare `from_me` per distinguere, e per i messaggi ricevuti (`from_me=0`) recuperare il JID dalla tabella `chat`.

**Fix applicato in**: `searchWordFrequency`, `getLinkStatsBySender`, `getCallStats` (chat specifica)

**Pattern corretto:**
```sql
LEFT JOIN jid j ON m.sender_jid_row_id = j._id AND m.sender_jid_row_id > 0
LEFT JOIN chat c ON m.chat_row_id = c._id
LEFT JOIN jid chat_jid ON c.jid_row_id = chat_jid._id
-- Per i messaggi ricevuti in 1-a-1: il mittente è chat_jid
```

---

### Bug 2 — `LIKE '%parola%'` conta sottostringhe
**Problema**: `searchWordFrequency` usava `LIKE '%tardi%'` che catturava anche "ritardi", "ritardino" ecc., producendo un conteggio diverso da `getTopWords` (2755 vs 2718).

**Fix**: La query ora usa `LIKE` solo come pre-filtro SQL per ridurre il set di messaggi trasferiti, poi conta con split esatto Kotlin in-memory (stesso metodo di `countWords`/`getTopWords`).

---

### Bug 3 — Normalizzazione parole troppo aggressiva
**Problema**: La regex `(.)\\1+` → `$1` eliminava **tutte** le doppie, trasformando "bello" → "belo", "tutto" → "tuto".

**Fix**: Regex `(.)\\1{2,}` → `$1$1` — interviene solo su 3+ caratteri identici consecutivi (es. "belloooo" → "belloo"), preservando le doppie legittime.

---

### Bug 4 — `TimeRange.CUSTOM` come default
**Problema**: Entrambi i ViewModel (`ChatDetailViewModel`, `DashboardViewModel`) inizializzavano con `TimeRange.CUSTOM`, rendendo nessun chip visivamente selezionato e le query senza filtro temporale.

**Fix**: Default cambiato a `TimeRange.ALL_TIME`.

---

### Bug 5 — `DashboardViewModel.getGlobalStats()` ignorava `customDateRange`
**Problema**: La chiamata era `getGlobalStats(_selectedTimeRange.value)` — il secondo parametro `customDateRange` non veniva passato.

**Fix**: `getGlobalStats(_selectedTimeRange.value, _customDateRange.value)`.

---

### Bug 6 — Stringa "Me" invece di "Tu"
**Problema**: Varie query SQL e risoluzioni nome nel repository restituivano `"Me"` per l'utente corrente.

**Fix**: Sostituito con `"Tu"` ovunque — in `searchWordFrequency` (SQL), `getLinkStatsBySender` (SQL), `getCallStats` (SQL), e nel repository per il JID `me@s.whatsapp.net`.

---

## 🔍 RADAR PAROLE — Architettura finale

### Algoritmo `searchWordFrequency` (Kotlin in-memory)
1. SQL: carica tutti i messaggi con `(from_me, other_jid, other_name, text_data)`
2. Kotlin: per ogni messaggio, `text.lowercase().replace(non-alpha, "").split(" ").count { it == word }`
3. Accumula conteggio per mittente, ritorna top 50

**Uniformità con `getTopWords`**: stesso split esatto su spazi, stessa normalizzazione caratteri.

---

## 🗃️ STRUTTURA DATABASE — Fatti chiave

### Tabella `message`
- `from_me = 1` → messaggio inviato da te, `sender_jid_row_id = 0`
- `from_me = 0` → messaggio ricevuto
  - In **gruppi**: `sender_jid_row_id` punta al membro del gruppo
  - In **1-a-1**: `sender_jid_row_id = 0` sempre → usa il JID della chat via JOIN
- `message_type = 0` → messaggi di testo normali (esclude sistema/media)

### Tabella `call_log`
- Ha `from_me` (chi ha fatto la chiamata)
- `jid_row_id` → l'altra persona (non il mittente come nei messaggi)
- `video_call = 1` → videochiamata
- `call_result = 0` → chiamata persa
- Non ha `chat_row_id`, filtro per chat via subquery su `jid_row_id`

### JID types
| Formato | Contesto | Note |
|---------|----------|------|
| `123@s.whatsapp.net` | Contatti normali | Ha nome in `jid.user` |
| `123@lid` | Privacy mode / New devices | Stesso utente ma ID diverso — mappare via `wa_contacts` |
| `me@s.whatsapp.net` | Placeholder per l'utente corrente | Non esiste nel DB |
| `123@g.us` | Gruppo | `jid_row_id` punta alla chat di gruppo |

### Risoluzione @lid → @s.whatsapp.net
WhatsApp usa `@lid` e `@s.whatsapp.net` per lo stesso utente in contesti diversi. Il repository risolve via `contactDao.resolveLidToRealJid()` e aggrega i conteggi.

---

## 📋 DEBUG PANELS

### Radar Parole
Mostra: `searchQuery`, `chatId`, `DEBUG INFO` (risultati DAO diretti), risultati finali per JID.

### Media & Link
Mostra: `chatId`, `linkStats[]`, `linkStatsBySender[]`, `debugInfo` dalla risoluzione nomi.

---

## 🐛 BUG RISOLTI (sessione 2)

### Bug 7 — Numeri di telefono invece di nomi contatti
**Problema**: In tutta l'app venivano mostrati numeri di telefono (es. "393401234567") invece dei nomi dei contatti. Le query SQL usavano `jid.user` che contiene il numero, non il nome.

**Fix sistematico**:
- **ChatDao**: `getChatBasicInfo` usa `c.subject AS name`; `getChatList` usa `COALESCE(c.subject, j.raw_string)`; `getMostActiveChat` ritorna il JID per risoluzione successiva
- **MessageDao**: tutte le query per-sender usano `raw_string` invece di `user`
- **Repository**: risolve tutti i nomi via `contactDao.resolveContactName()` usando `wa_contacts` (wa.db)
- **ChatListViewModel**: enrichment esteso a tutte le chat non-gruppo e chat con "@" nel nome

### Bug 8 — Pie chart "Conversation Starters" parzialmente riempito con filtri temporali
**Problema**: Con filtri di data attivi, il donut chart di "Chi inizia le conversazioni" mostrava fette piccole su un cerchio grande perché `totalMessages` era calcolato da `getConversationStarters()` (senza filtro temporale) mentre le fette venivano da `getConversationStartersBySender()` (con filtro).

**Fix**: Il totale ora si calcola come `conversationStarterBreakdown.sumOf { it.count }`, coerente con i dati effettivi delle fette.

---

## ⚡ PERFORMANCE — Ottimizzazioni DB

### Problema
Chat detail con 400K+ messaggi impiegava ~60 secondi a caricarsi.

### Cause
1. **Zero indici** sul database SQLite copiato — tutte le query facevano full table scan
2. **15 query sequenziali** in `getChatStats()` — tempo totale = somma di tutti i tempi
3. **`getTopWords()`** caricava tutti i testi in memoria senza pre-filtro
4. **`searchWordFrequency()`** scansionava ogni messaggio del DB

### Fix applicati

#### 1. Indici SQL (WhatsAppDatabase.kt)
DB aperto in READWRITE (non più READONLY). 7 indici creati alla prima apertura:
| Indice | Colonne | Query beneficiate |
|--------|---------|-------------------|
| `idx_message_chat_type_ts` | `message(chat_row_id, message_type, timestamp)` | Tutte le query con filtro temporale |
| `idx_message_chat_type_from` | `message(chat_row_id, message_type, from_me)` | Sender breakdown, pie chart |
| `idx_message_sender_jid` | `message(sender_jid_row_id)` | JOIN per identificazione mittente |
| `idx_message_media_row` | `message_media(message_row_id)` | LEFT JOIN media stats |
| `idx_chat_jid` | `chat(jid_row_id)` | JOIN chat → jid |
| `idx_call_log_jid` | `call_log(jid_row_id)` | Query chiamate |
| `idx_message_chat_text` | `message(chat_row_id, message_type) WHERE text_data IS NOT NULL` | Ricerca parole, top words |

#### 2. WAL mode
`enableWriteAheadLogging()` abilita letture concorrenti da thread multipli.

#### 3. Query parallelizzate (AnalyticsRepository.kt)
14 query lanciate con `async {}` in un `coroutineScope`, attese con `.await()`. Tempo totale = tempo della query più lenta (non la somma).

#### 4. Progressive loading (ChatDetailViewModel.kt)
`getChatStats()` splittato in `getQuickChatStats()` (conteggi, distribuzioni, <1s) + `getSlowChatStats()` (top words, sender breakdown, starters, response time). La UI mostra le stat veloci immediatamente e le lente progressivamente con indicatore "Calcolo in corso…".

#### 5. Pre-filtro SQL per ricerca parole
`searchWordFrequency()` ora usa `WHERE text_data LIKE '%' || ? || '%'` per pre-filtrare i messaggi lato SQL. Solo i messaggi contenenti la parola vengono trasferiti in memoria per il conteggio esatto.

### Risultato
Da ~60s a ~5-10s per chat con 400K messaggi. La UI appare in <1s grazie al progressive loading.