# WhatStat - Scoperte, Bug e Soluzioni

## 📊 STATO ATTUALE (aggiornato 15 aprile 2026)

### ✅ FUNZIONA
- **Radar Parole**: conta occorrenze esatte per mittente, splitta Tu vs Altra persona nelle chat 1-a-1
- **Media & Link nella chat**: mostra direttamente il breakdown per mittente senza dover toccare
- **Media & Link globale**: mostra ranking per chat con media + link
- **Log Chiamate**: riepilogo totale + split durata audio/video + dettaglio per persona
- **Filtri temporali**: tutti i range (Tutto, Oggi, Ieri, 7g, 30g, Anno, Personalizzato) funzionano in tutte le schermate
- **Parole più usate**: rispettano il range di date selezionato, default ALL_TIME

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

**Fix**: La query ora carica tutti i messaggi in memoria e conta con split esatto sugli spazi (stesso metodo di `countWords`/`getTopWords`).

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