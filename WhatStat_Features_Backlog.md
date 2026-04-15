# 🚀 WhatStat Reborn: Product Backlog & Features Plan

Sulla base del potente motore decrittografico di WhatStat Reborn e delle idee raccolte, abbiamo strutturato una nuova roadmap di funzionalità definitiva. Accanto ad ogni feature è specificata la reale fattibilità tecnica rispetto ai limiti architetturali del database SQLite nativo (`msgstore.db`) di WhatsApp.

---

## 1. 📅 Filtri Temporali Universitari (Global Time-Filtering) ✅ IMPLEMENTATO
Tutte le statistiche reagiscono istantaneamente a uno slider / filtro di periodo.
*   *Range Custom*: Cliccando su due date di un calendario l'utente crea una bolla temporale. L'app elabora dinamicamente le metriche per visualizzare come l'abitudine cambia in quello spaccato temporale.
*   **Range disponibili**: Oggi, Ieri, 7 giorni, 30 giorni, Anno, Personalizzato
*   **Implementato in**: Dashboard, ChatDetail, WordSearch, CallsStats, MediaStats

---

## 2. 🔍 Word Search Analyzer & Slang Tracker ✅ IMPLEMENTATO
Un potente microscopio sulle abitudini testuali.
*   **Contatore per Parola**: Ricerchiamo una particolare parola o set di parole.
*   **Podio dello Slang**: Classifica per vedere tra due utenti chi usa di più una certa imprecazione o intercalare in un dato periodo.
*   **Filtro per Chat**: Possibile filtrare le ricerche per una chat specifica
*   **Conteggio OCCORRENZE**: Conta quante volte la parola appare (NON quanti messaggi la contengono)
*   **Debounce**: La ricerca parte 300ms dopo aver smesso di digitare

---

## 3. 📞 Call & Video Call Statistics ✅ IMPLEMENTATO
Con gli ultimi aggiornamenti di WhatsApp lo storico chiamate è ben consolidato nel DB locale.
*   **Durata Totale & Media**: Classifiche delle persone con cui hai speso più ore della tua vita al telefono.
*   **Chiamate perse vs risposte**: Ratio percentuale (le famose "red flags" se un compagno/a non risponde mai).
*   **Video vs Audio**: Quante chiamate puramente vocali rispetto a quelle con fotocamera accesa.
*   **Filtro per Chat**: Parzialmente implementato via jid (per 1:1 calls)
*   **DB Table**: `call_log`

---

## 4. 📎 Condivisione Link & Documenti ✅ IMPLEMENTATO
Statistiche per rivelare chi è la fonte primaria di articoli, meme, PDF e fatture.
*   **Media Sharing Podio**: Chi condivide più documenti PDF, Fogli o file compressi in una discussione lavorativa o universitaria.
*   **Re dei Link**: Statistiche su quante URL / pagine web diverse sono state droppate nella chat, in quali giorni.
*   **Filtro per Chat**: Implementato via `message_link.chat_row_id`
*   **DB Tables**: `message_link`, `message_media`

---

## 5. 📊 Dashboard & Statistiche Chat ✅ IMPLEMENTATO
*   **Panoramica Globale**: Messaggi totali, chat attive, media/giorno, streak, media files, chat più attiva
*   **Heatmap Oraria**: "Quando sei più attivo" - distribuzione oraria dei messaggi
*   **Attività Settimanale**: Distribuzione per giorno della settimana
*   **Dettaglio Chat**: Statistiche complete per singola chat
*   **Top Parole**: Word cloud interattiva con tooltip

---

## 🚫 COSA NON È POSSIBILE FARE (I falsi miti del tracking locale)
Per chiarezza intellettuale e onestà, ci sono operazioni del tutto impossibili da eseguire esaminando il file `msgstore.db` archiviato offline:

*   ❌ **Frequenza cambio foto profilo / Ultimo online (Last Seen)**: Il database di WhatsApp archivia **storicamente solo i messaggi e il log chiamate**. Non salva alcuna cronologia del profilo! La foto e le coordinate di ultimo accesso sono fetchate temporaneamente a livello server in RAM. Noi tramite File System **non possiamo** costruire un tracking retroattivo del Last Seen né dei cambi della foto profilo o degli "Stati".
*   *Unica via illegale*: L'unica via sarebbe "agganciare" in background WhatsApp e raschiare i dati h24 tramite server, che non è uno standard per un'app offline-first come la nostra, e vìola immediatamente l'Android Store per stalking Policy. Restiamo sui soli dati estratti dai database!

---

## 📱 DB Tables Utilizzate
*   `message` - Messaggi, text_data, timestamp, chat_row_id, sender_jid_row_id
*   `chat` - Info chat, jid_row_id, subject
*   `jid` - Contatti, raw_string, user
*   `call_log` - Chiamate, duration, video_call, call_result, jid_row_id
*   `message_link` - Link condivisi, chat_row_id, message_row_id
*   `message_media` - Media condivisi, chat_row_id, media_wa_type