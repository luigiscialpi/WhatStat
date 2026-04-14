# Analisi Statistiche e Funzionalità WhatStat Originale

Dall'ispezione dell'APK decompilato dell'app originale "WhatStat" (in particolare file di risorse come `strings.xml` e le query incapsulate in `com.androidappetizers.whatstat.database.Database`), è emerso che l'applicazione forniva una serie di insight dettagliati interrogando e decifrando il database nativo di WhatsApp (`msgstore.db`). 

Di seguito un raggruppamento strutturato delle metriche proposte originariamente all'utente:

## 1. Metriche Globali (Totals Overview)
WhatStat offriva un quadro d'insieme dell'utilizzo di WhatsApp attraverso un'aggregazione dei totali di tutti i contatti, con distinzione su base temporale ("Oggi", "Ieri", "Ultima settimana", "Ultimo mese", "Ultimo anno", "In assoluto").
- **Messaggi Inviati vs Ricevuti:** Conteggio incrociato in entrata e uscita (`txtSentAndReceived`).
- **Messaggi Privati vs Messaggi di Gruppo:** Suddivisione tra conversazioni one-to-one e chat di gruppo.

## 2. Metriche per Singolo Contatto
Per ogni chat privata o amico, l'utente poteva visualizzare:
- **Conversation starter (Chi inizia la conversazione):** Una metrica interessante (`txtStartConversationcount`) che stabiliva matematicamente quale dei due utenti era solito avviare o sbloccare una conversazione dopo un periodo di pausa. Il codice sorgente definisce 4 ore (`ConversationMaxReplyTime`) e 12 ore (`ConversationMaxNoReplyTime`) come soglie oltre le quali un nuovo messaggio viene considerato come "nuova conversazione". 
- **Top 5 Contatti / Amici:** Classifica degli utenti con la mole aggregata maggiore di messaggi scambiati (`txtTop5Contact`).
- **Time to catch up:** ("Tempo di rimettersi in contatto!") - L'app rilevava lo stato temporale di inattività e agiva da reminder (`txtCatchUpDate`): *You haven't talked since %s. What happened? Catch up!*

## 3. Distribuzione Temporale (History & Heatmaps)
Le distribuzioni di volume crudo potevano essere analizzate secondo diversi raggruppamenti:
- **Most active time of the day:** (`txtMessagecountPerHourCumulative`) Calcolo a torta/barre della percentuale di messaggi raggruppata per fasce orarie della giornata.
- **Most active day of the week:** (`txtMessagecountPerWeekdayCumulative`) Aggregazione del numero di messaggi suddivisi per ogni singolo giorno della settimana (e.g. per capire se sei più attivo il venerdì o la domenica).
- **Andamento storico:** (`txtGroupByDay`, `txtGroupByWeek`, `txtGroupByMonth`) Funzione granulare per tracciare picchi passati di conversazione selezionando giorno, settimana o mese.

## 4. Dinamiche dei Gruppi
- **Member Activity (Classifica membri):** Classifica interna dell'attività tra i membri dei vari gruppi di cui l'utente fa parte (`txtGroupsmembers`). In SQL, il codice estraeva le occorrenze relazionando `remote_resource` (il mittente reale) e `key_remote_jid` (l'identificativo della chat di gruppo). Da qui poteva estrarre chi contribuisse al gruppo in maggior o minor percentuale numerica.

## Dettagli Tecnici dal Sorgente
- Le interrogazioni al database cifrato avvenivano tramite massiccio impiego di query annidate via `SQLiteDatabase.rawQuery`. Molto utilizzata era la clausola `GROUP BY` per aggregare e calcolare le percentuali d'uso totali: 
  Esempio: `SELECT key_remote_jid, key_from_me, remote_resource, count(*) ...`
- Per ricavare dinamicamente il nome utente e l'avatar, l'APK interrogava le API `ContactsContract.PhoneLookup` usando parser della libreria Google i18n (`PhoneNumberUtil`) in modo da normalizzare i contatti memorizzati in rubrica.
