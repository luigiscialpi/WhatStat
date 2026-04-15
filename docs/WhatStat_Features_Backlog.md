# 🚀 WhatStat Reborn: Product Backlog & Features Plan

Sulla base del potente motore decrittografico di WhatStat Reborn e delle idee raccolte, abbiamo strutturato una nuova roadmap di funzionalità definitiva. Accanto ad ogni feature è specificata la reale fattibilità tecnica rispetto ai limiti architetturali del database SQLite nativo (`msgstore.db`) di WhatsApp.

---

## 1. 📅 Filtri Temporali (Global Time-Filtering) ✅ IMPLEMENTATO
Tutte le statistiche reagiscono istantaneamente a uno slider / filtro di periodo.
*   *Range Custom*: Cliccando su due date di un calendario l'utente crea una bolla temporale.
*   **Range disponibili**: Tutto, Oggi, Ieri, 7 giorni, 30 giorni, Anno, Personalizzato
*   **Default**: Tutto (`ALL_TIME`)
*   **Implementato in**: Dashboard, ChatDetail, WordSearch, CallsStats, MediaStats
*   **Margini UI**: Allineati a `horizontal = 16.dp, vertical = 4.dp` per i FilterChips e `modifier = Modifier.padding(horizontal = 16.dp)` per il DateRangePicker — coerente in tutte le schermate

---

## 2. 🔍 Word Search Analyzer (Radar Parole) ✅ IMPLEMENTATO
Un potente microscopio sulle abitudini testuali.
*   **Contatore occorrenze esatte**: conta quante volte la parola appare (NON quanti messaggi la contengono, NON sottostringhe)
*   **Split per mittente**: nelle chat 1-a-1 mostra "Tu" vs "Altra persona"; nei gruppi per ogni membro
*   **Uniformità con Parole più usate**: stesso algoritmo (split esatto su spazi, normalizzazione caratteri)
*   **Debounce**: 300ms dopo aver smesso di digitare, minimo 2 caratteri
*   **Filtro temporale**: rispetta il range selezionato
*   **Pre-filtro SQL**: `LIKE '%word%'` per velocizzare la query (solo i messaggi contenenti la parola vengono trasferiti in memoria)

### Algoritmo occorrenze esatte
```kotlin
text.lowercase()
    .replace(Regex("[^a-zàáâ...\\s]"), "")
    .split(Regex("\\s+"))
    .count { it == searchWord }
```

---

## 3. 📞 Log Chiamate ✅ IMPLEMENTATO
*   **Riepilogo totale**: card in testa con contatori (Totali, Audio, Video, Perse) + barre di progresso durata audio vs video
*   **Dettaglio per persona**: per ogni contatto (vista globale) o per "Tu" vs "Altra persona" (chat 1-a-1)
*   **Split durata**: `audioDurationSec` e `videoDurationSec` separati
*   **Formato durata**: `Xh Ym` o `Ym` secondo necessità
*   **Search bar client-side**: filtra contatti per nome in tempo reale (nascosta se non ci sono risultati)
*   **DB Table**: `call_log` (ha `from_me`, `video_call`, `duration`, `call_result`)

---

## 4. 📎 Media & Link ✅ IMPLEMENTATO
*   **Vista in chat 1-a-1**: mostra direttamente "Tu" vs "Altra persona" senza tap aggiuntivo
*   **Vista globale**: ranking per chat, tap per aprire il breakdown per mittente
*   **Card**: mostra prominentemente sia **media** (icona 📷, colore secondary) sia **link** (icona 🔗, colore primary) come cifre grandi
*   **Campo `mediaCount`**: conta i messaggi con allegato via `message_media` JOIN
*   **Search bar client-side**: filtra contatti per nome in tempo reale (nascosta se non ci sono risultati)
*   **DB Tables**: `message`, `message_media`, `jid`, `chat`

---

## 5. 💬 Parole più usate (TopWords) ✅ IMPLEMENTATO
*   **Rispetta il filtro temporale**: passa `startTime`/`endTime` alla query
*   **Default ALL_TIME**: il chip "Tutto" è selezionato di default
*   **Normalizzazione parole**: regex `(.)\\1{2,}` → `$1$1` (non rimuove doppie legittime)
*   **Stop words**: oltre 60 parole italiane e inglesi escluse
*   **Lunghezza minima**: > 2 caratteri
*   **Visualizzazione**: word cloud interattiva con tooltip (count)

---

## 6. 📊 Dashboard & Statistiche Chat ✅ IMPLEMENTATO
*   **Panoramica Globale**: Messaggi totali, chat attive, media/giorno, streak, media files, chat più attiva
*   **Heatmap Oraria**: distribuzione oraria dei messaggi
*   **Attività Settimanale**: distribuzione per giorno della settimana
*   **Dettaglio Chat**: statistiche complete per singola chat con tutti i filtri temporali
*   **Pie chart per-membro**: donut chart "Chi scrive di più" e "Chi inizia le conversazioni" con breakdown per singolo mittente
*   **Pie chart espandibile**: "Mostra tutti (N)" per gruppi con >10 membri, toggle per espandere/comprimere la legenda
*   **Risoluzione nomi contatti**: tutti i nomi risolti via `wa_contacts` (wa.db), non più numeri di telefono
*   **Bug fix**: `getGlobalStats()` ora riceve correttamente `customDateRange`

---

## 7. 🎨 UI/UX — Dettagli implementativi

### Margini e padding
- `statusBarsPadding()` su tutte le schermate principali e sui debug panel
- `Spacer(Modifier.height(48.dp))` in fondo a tutte le LazyColumn per evitare taglio dell'ultimo elemento
- DateRangePicker con `padding(horizontal = 16.dp)` in tutte le schermate

### Terminologia utente
- L'utente corrente è sempre denominato **"Tu"** (mai "Me" o "me")

### JID dell'utente corrente
- Placeholder `me@s.whatsapp.net` usato internamente
- Risolto a "Tu" in tutti i punti di display (DAO SQL + repository + UI)

---

## 🚫 COSA NON È POSSIBILE FARE
*   ❌ **Frequenza cambio foto profilo / Ultimo online**: WhatsApp non salva storicamente queste info nel DB locale.
*   ❌ **Tracking retroattivo Last Seen**: i dati sono fetchati a runtime dal server, non persistiti.

---

## 📱 DB Tables Utilizzate
*   `message` — messaggi, `text_data`, `timestamp`, `chat_row_id`, `sender_jid_row_id`, `from_me`, `message_type`
*   `chat` — info chat, `jid_row_id`, `subject`
*   `jid` — contatti e chat, `raw_string`, `user`
*   `call_log` — chiamate, `duration`, `video_call`, `call_result`, `jid_row_id`, `from_me`
*   `message_link` — link condivisi
*   `message_media` — media condivisi, `mime_type`, `file_size`

---

## 8. ⚡ Performance & Progressive Loading ✅ IMPLEMENTATO
*   **7 indici SQL** creati alla prima apertura del DB (copia locale, non il DB originale di WhatsApp)
*   **WAL mode** per letture concorrenti da thread multipli
*   **Query parallelizzate**: 14 query in `async {}`, await parallelo (non sequenziale)
*   **Progressive loading**: UI divisa in 2 fasi:
    - **Fase 1 (Quick, <1s)**: conteggi, date, distribuzioni orarie/settimanali/mensili, media stats
    - **Fase 2 (Slow, 3-10s)**: top words, sender breakdown (pie chart), conversation starters, tempo di risposta medio
*   **Pre-filtro SQL** per ricerca parole: `LIKE '%word%'` riduce il set prima del conteggio in-memory
*   **Indicatore visivo**: sezioni in attesa mostrano "Calcolo in corso…" con spinner

---

## 9. 🔎 Search Bar Client-Side ✅ IMPLEMENTATO
*   **Log Chiamate**: campo di ricerca per filtrare contatti per nome senza query DB aggiuntive
*   **Media & Link**: campo di ricerca per filtrare contatti per nome senza query DB aggiuntive
*   **Comportamento**: nascosta durante loading e quando non ci sono risultati, visibile solo con dati disponibili
*   **Filtro istantaneo**: `remember(data, query)` ricalcola la lista filtrata ad ogni keystroke