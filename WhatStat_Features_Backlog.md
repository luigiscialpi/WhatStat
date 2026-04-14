# 🚀 WhatStat Reborn: Product Backlog & Features Plan

Sulla base del potente motore decrittografico di WhatStat Reborn e delle idee raccolte, abbiamo strutturato una nuova roadmap di funzionalità definitiva. Accanto ad ogni feature è specificata la reale fattibilità tecnica rispetto ai limiti architetturali del database SQLite nativo (`msgstore.db`) di WhatsApp.

---

## 1. 📅 Filtri Temporali Universitari (Global Time-Filtering)
Tutte le statistiche dovranno reagire istantaneamente a uno slider / filtro di periodo.
*   *Range Custom*: Cliccando su due date di un calendario l'utente crea una bolla temporale. L'app elabora dinamicamente le metriche per visualizzare come l'abitudine cambia in quello spaccato temporale.

---

## 2. 🔍 Word Search Analyzer & Slang Tracker
Un potente microscopio sulle abitudini testuali.
*   **Contatore per Parola**: Ricerchiamo una particolare parola o set di parole.
*   **Podio dello Slang**: Classifica per vedere tra due utenti chi usa di più una certa imprecazione o intercalare in un dato periodo.
*   *Fattibilità Tecnica*: **ALTA**. Pienamente eseguibile tramite full-text search asincrono (Tabelle `message` -> colonna `text_data`).

---

## 3. 📞 Call & Video Call Statistics (Avanzato)
Con gli ultimi aggiornamenti di WhatsApp lo storico chiamate è ben consolidato nel DB locale.
*   **Durata Totale & Media**: Classifiche delle persone con cui hai speso più ore della tua vita al telefono.
*   **Chiamate perse vs risposte**: Ratio percentuale (le famose "red flags" se un compagno/a non risponde mai).
*   **Video vs Audio**: Quante chiamate puramente vocali rispetto a quelle con fotocamera accesa.
*   **Orari Preferiti**: Calcolo dell'ora del giorno in cui scattano le telefonate predefinite.
*   *Fattibilità Tecnica*: **ALTA**. Sfruttando le tabelle relazionali interne come `message_call` e analizzando i parametri `duration` e l'ID del tipo di sistema per dedurre squilli o chiamate completate.

---

## 4. 🧠 Sentiment Analysis & NLP (Innovazione Assoluta)
Analisi testuale elaborata che rende l'App rivoluzionaria.
*   **Sentimento per Chat nel Tempo**: Capire se l'umore generale di una coppia o amicizia è "positivo", "neutro" o "negativo" sfogliando i mesi.
*   **Rilevamento di Discussioni (Litigi)**: Indentificato matematicamente dai "picchi di messaggi brevi e ravvicinati" in frame temporali ristretti (es. botta e risposta di 2 parole nello spazio di loghi secondi).
*   *Fattibilità Tecnica*: **MEDIA/ALTA**. Fattibile delegando l'analisi a un leggerissimo modello Machine Learning locale (es. TensorFlow Lite o librerie NLP Kotlin come Apache OpenNLP). Ovviamente va avvisato l'utente che il processing dell'NLP di 20.000 messaggi potrebbe scaldare un pio' la CPU in esecuzione.

---

## 5. 📍 Geo Mapping & Contesto
*   **Mappa degli Spostamenti**: Una Google Maps interna dove compaiono dei "pin" che rappresentano tutte le volte in cui avete inviato/ricevuto un "luogo".
*   **Fuso Orario Storico**: Per le comunicazioni a lunga distanza.
*   *Fattibilità Tecnica*: **ALTA**. Mappando tutti i messaggi per cui la latitudine/longitudine non è nulla nelle tabelle derivate della chat (`message_location`).

---

## 7. 📎 Condivisione Link & Documenti
Statistiche per rivelare chi è la fonte primaria di articoli, meme, PDF e fatture.
*   **Media Sharing Podio**: Chi condivide più documenti PDF, Fogli o file compressi in una discussione lavorativa o universitaria.
*   **Re dei Link**: Statistiche su quante URL / pagine web diverse sono state droppate nella chat, in quali giorni.
*   *Fattibilità Tecnica*: **ALTA**. Il database espone due tabelle chiarissime per questo scopo limitativo: `message_media` (utilizzando le colonne native come `media_wa_type` per pescare documenti e file) e `message_link` (o un parsing testuale asincrono) per i link.


## 6. 👥 Gruppi & Dinamiche Espulse
*   **Fughe dal gruppo**: Capire chi ha abbandonato il gruppo o chi interagisce meno nel corso del tempo.
*   *Fattibilità Tecnica*: **ALTA**. WhatsApp logs eventi sistemici come "Utente X ha lasciato" con appositi codici `action_type`.

---

## 🚫 COSA NON È POSSIBILE FARE (I falsi miti del tracking locale)
Per chiarezza intellettuale e onestà, ci sono operazioni del tutto impossibili da eseguire esaminando il file `msgstore.db` archiviato offline:

*   ❌ **Frequenza cambio foto profilo / Ultimo online (Last Seen)**: Il database di WhatsApp archivia **storicamente solo i messaggi e il log chiamate**. Non salva alcuna cronologia del profilo! La foto e le coordinate di ultimo accesso sono fetchate temporaneamente a livello server in RAM. Noi tramite File System **non possiamo** costruire un tracking retroattivo del Last Seen né dei cambi della foto profilo o degli "Stati".
*   *Unica via illegale*: L'unica via sarebbe "agganciare" in background WhatsApp e raschiare i dati h24 tramite server, che non è uno standard per un'app offline-first come la nostra, e vìola immediatamente l'Android Store per stalking Policy. Restiamo sui soli dati estratti dai database!
