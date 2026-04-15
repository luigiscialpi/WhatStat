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
*   **DB Table**: `call_log` (ha `from_me`, `video_call`, `duration`, `call_result`)

---

## 4. 📎 Media & Link ✅ IMPLEMENTATO
*   **Vista in chat 1-a-1**: mostra direttamente "Tu" vs "Altra persona" senza tap aggiuntivo
*   **Vista globale**: ranking per chat, tap per aprire il breakdown per mittente
*   **Card**: mostra prominentemente sia **media** (icona 📷, colore secondary) sia **link** (icona 🔗, colore primary) come cifre grandi
*   **Campo `mediaCount`**: conta i messaggi con allegato via `message_media` JOIN
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