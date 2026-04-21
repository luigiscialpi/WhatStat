# WhatStat Reborn — Documentazione funzionale

> App Android nativa che legge, decifra e analizza il database di WhatsApp direttamente sul dispositivo, senza mai inviare dati a server esterni. Tutto avviene in locale.

---

## Indice

1. [Requisiti e compatibilità](#1-requisiti-e-compatibilità)
2. [Setup e primo avvio](#2-setup-e-primo-avvio)
3. [Sistema di cache e prestazioni](#3-sistema-di-cache-e-prestazioni)
4. [Filtri temporali](#4-filtri-temporali)
5. [Dashboard — Panoramica globale](#5-dashboard--panoramica-globale)
6. [Le tue chat — Lista e ranking](#6-le-tue-chat--lista-e-ranking)
 7. [Dettaglio chat](#7-dettaglio-chat)
 8. [Media Gallery](#8-media-gallery)
 9. [Radar Parole](#9-radar-parole)
10. [Privacy e sicurezza](#10-privacy-e-sicurezza)
11. [Gestione degli errori](#11-gestione-degli-errori)
12. [Struttura dei dati sorgente](#12-struttura-dei-dati-sorgente)
13. [Comportamenti noti e quirk del database](#13-comportamenti-noti-e-quirk-del-database)
14. [Roadmap](#14-roadmap)

---

## 1. Requisiti e compatibilità

Il funzionamento dell'app richiede:

- Dispositivo Android rootato, tipicamente con Magisk. I test su emulatore con root sono sconsigliati per dati reali.
- Android 8.0 (API 26) o superiore. La versione target è Android 14 (API 34).
- WhatsApp installato con backup end-to-end **disattivato**. Il formato supportato è `crypt14`. Il formato `crypt15` (backup E2E attivo) non è ancora supportato, ma è in roadmap con inserimento manuale della chiave a 64 caratteri esadecimali.

Il formato del backup si riconosce dall'estensione del file: `msgstore.db.crypt14` indica il formato supportato. Se il file è `msgstore.db.crypt15`, il backup E2E è attivo e l'app non può procedere.

---

## 2. Setup e primo avvio

### Schermata di setup (`SetupScreen` / `SetupViewModel`)

Al primo avvio l'app esegue in sequenza quattro operazioni, ognuna con un proprio indicatore di stato visibile nella schermata di setup:

**1. Verifica accesso root** — L'app controlla che la shell root sia disponibile e accessibile tramite Magisk. Se il root non è disponibile o i permessi sono stati negati, il processo si interrompe con un messaggio esplicativo.

**2. Estrazione file protetti** — Con i permessi root, l'app copia tre file dalla posizione di WhatsApp nella directory privata dell'app (inaccessibile ad altre applicazioni):
- La chiave di cifratura: `/data/data/com.whatsapp/files/key`
- Il database cifrato: `/sdcard/WhatsApp/Databases/msgstore.db.crypt14`
- Il database contatti: `/data/data/com.whatsapp/databases/wa.db`

L'estrazione degli avatar avviene separatamente: l'app itera su tutti i profili in `/data/user/` per supportare configurazioni con profili multipli (es. XSpace), copia i file `.j` dalla directory `Avatars/` di WhatsApp e li rinomina in `.jpg`.

**3. Decifratura database** — Il file crypt14 viene decifrato in locale. Il processo legge la chiave AES-256 dal file `key` (deserializzazione Java Object, byte 99–131), estrae l'IV dall'header protobuf del file cifrato, decifra con AES-GCM e decomprime con zlib. Il risultato è un file SQLite valido (`msgstore.db`). L'operazione fallisce se la chiave non corrisponde al database (errore GCM authentication tag).

**4. Apertura database contatti** — Il file `wa.db` viene aperto in sola lettura per la risoluzione dei nomi dei contatti. I nomi sono ricavati dalla tabella `wa_contacts` del file `wa.db`, non da `jid.cached_name` in `msgstore.db` (spesso vuoto nelle versioni moderne di WhatsApp).

### Avvii successivi e rilevamento aggiornamenti

Ad ogni avvio l'app calcola l'hash SHA-256 del file `msgstore.db.crypt14` e lo confronta con quello dell'ultima sessione. Se l'hash non è cambiato, i dati vengono caricati dalla cache in modo istantaneo. Se l'hash è cambiato (WhatsApp ha aggiunto messaggi), la cache viene invalidata completamente e il processo di estrazione/decifratura riparte da capo.

---

## 3. Sistema di cache e prestazioni

La cache è il meccanismo centrale per garantire aperture veloci. Usa `kotlinx.serialization` per serializzare tutti i modelli di dati, incluse distribuzioni, liste di parole, emoji e breakdown per mittente.

### Tipi di cache

**Cache Quick Stats globali** — Contiene `GlobalStats` con tutte le distribuzioni (oraria, settimanale, mensile, annuale). Viene popolata dopo la prima analisi completa e viene mostrata istantaneamente alle aperture successive, senza alcun calcolo.

**Cache Slow Stats globali** — Contiene le parole più usate e le emoji più usate a livello globale. Queste analisi sono più lente perché richiedono di scorrere i testi di tutti i messaggi. Vengono cachate dopo la prima analisi manuale (opt-in) e mostrate automaticamente alle riaperture.

**Cache Quick Stats per chat** — Contiene distribuzioni orarie, settimanali, mensili e annuali per ogni singola chat, oltre ai conteggi base. Indicizzata per `chatId`. Garantisce l'apertura istantanea del dettaglio di una chat.

**Cache Slow Stats per chat** — Contiene le analisi testuali per chat: parole più usate, emoji più usate, sticker più usati, breakdown per mittente, breakdown degli starter, tempo medio di risposta. Cachata per `chatId` dopo la prima analisi opt-in.

### Comportamento della cache per i filtri temporali

La cache viene salvata **solo per il filtro `ALL_TIME`**. Quando l'utente applica un filtro temporale diverso (Oggi, Ultimi 7 giorni, ecc.), le query vengono rieseguite direttamente sul database. Questo è accettabile perché le query filtrate sono veloci grazie agli indici SQL.

### Ottimizzazioni database

Sono presenti 7 indici SQL compositi sulle colonne più usate nelle query: `chat_row_id`, `message_type`, `timestamp`. Il database è aperto in WAL mode (Write-Ahead Logging) per supportare letture concorrenti. Le query pesanti sono parallelizzate con `async`/`await` (fino a 14 query in parallelo per il calcolo delle statistiche globali).

---

## 4. Filtri temporali

Tutte le schermate principali (Dashboard e Dettaglio chat) supportano filtri temporali applicabili a tutte le metriche. I filtri disponibili sono:

- **Tutto** — nessun filtro, tutti i messaggi
- **Oggi** — messaggi del giorno corrente
- **Ieri** — messaggi del giorno precedente
- **Ultimi 7 giorni**
- **Ultimi 30 giorni**
- **Quest'anno**
- **Personalizzato** — selettore di intervallo date con `DateRangePicker`

Il `DateRangePicker` è un componente dedicato con un pulsante "Cerca" che diventa verde quando è attivo e rosso per annullare. I filtri rapidi e il date picker rimangono fissi in cima alla schermata mentre si scorre (sticky header).

Il modello dati per i filtri è `TimeRange` (enum con valori: `ALL_TIME`, `TODAY`, `YESTERDAY`, `LAST_7_DAYS`, `LAST_30_DAYS`, `LAST_YEAR`, `CUSTOM`) e `DateRange` (con campi `startTime: Long?` e `endTime: Long?`, entrambi Unix timestamp in millisecondi).

---

## 5. Dashboard — Panoramica globale

**Schermata:** `DashboardScreen` / `DashboardViewModel`  
**Modello dati:** `GlobalStats`

### Statistiche di riepilogo (card in cima)

Le card mostrano i valori principali per il periodo selezionato:

- Numero totale di messaggi
- Numero totale di chat
- Numero totale di contatti
- Numero totale di file multimediali scambiati
- Media di messaggi al giorno — calcolata dividendo il totale di messaggi per il numero di giorni nell'intervallo selezionato, non per i giorni dal primo messaggio in assoluto (fix introdotto in v1.7)
- Nome della chat più attiva (risolto tramite `wa.db`)
- Ora del giorno in cui si scrive di più (0–23)
- Giorno della settimana in cui si scrive di più (0 = domenica, 6 = sabato)
- Streak più lunga: numero massimo di giorni consecutivi con almeno un messaggio, calcolato recuperando le date distinte con messaggi e contando le sequenze di giorni consecutivi (`calculateLongestStreak` in `AnalyticsRepository`)

### Grafici di attività (componente `InteractiveBarChart`)

Quattro schede selezionabili, ciascuna con un grafico a barre con scrolling orizzontale:

- **Oraria** — 24 barre, una per ogni ora del giorno (0–23)
- **Settimanale** — 7 barre, una per ogni giorno della settimana
- **Mensile** — barre per giorno del mese (1–31)
- **Annuale** — una barra per ogni anno con dati

I grafici sono interattivi: toccando una barra appare un tooltip con il valore esatto.

### Analisi testi — Sezione opt-in

Le analisi testuali non partono automaticamente perché richiedono più tempo. L'utente vede un pulsante "Avvia analisi" con un'icona informativa che spiega la durata stimata. Una volta avviata, l'analisi procede in background con un indicatore di avanzamento (`LoadingSection`). I risultati vengono cachati e mostrati automaticamente alle aperture successive senza richiedere di riavviare l'analisi.

È possibile scegliere quanti elementi visualizzare (10, 20, 30 o 50) tramite il componente `SelectableLimitChip`. Il pool interno è sempre di 50 elementi pre-caricati; il selettore filtra la visualizzazione senza nuove query.

**Parole più usate** — Componente `WordCloudView`. Le parole sono mostrate con dimensione proporzionale alla frequenza. Le stop word italiane e comuni sono escluse con filtro in memoria dopo la query SQL. Toccando una parola appare un tooltip con il conteggio esatto.

**Emoji più usate** — Componente `InteractiveEmojiCloud`. Analoga alla word cloud. Il parser supporta emoji con skin tone modifier, sequenze ZWJ (emoji composte), bandiere e keycap. Toccando un'emoji si apre `EmojiStickerPreviewModal`, un modal in stile light con backdrop che mostra l'emoji in grande e il conteggio.

**Sticker più usati** — Componente `InteractiveStickerCloud`. Mostra le miniature degli sticker con conteggio utilizzi. I dati vengono letti dalla tabella `message_media`. Toccando uno sticker si apre lo stesso modal di anteprima.

---

## 6. Le tue chat — Lista e ranking

**Schermata:** `ChatListScreen` / `ChatListViewModel`

Elenco di tutte le conversazioni ordinate per numero totale di messaggi in ordine decrescente. Per ogni chat viene mostrato:

- Avatar circolare del contatto o del gruppo. Gli avatar vengono estratti da `/data/user/{userId}/com.whatsapp/files/Avatars/` e caricati come immagini JPEG tramite la libreria Coil. Se non è disponibile un avatar, viene mostrata un'icona Person (per chat 1-a-1) o Group (per gruppi).
- L'utente corrente viene identificato con il JID speciale `me@s.whatsapp.net`, il cui avatar è estratto come `me.jpg` dalla stessa directory.
- Nome del contatto risolto tramite `wa.db` (non il numero di telefono grezzo da `jid.raw_string`).
- Numero di telefono del contatto, dove disponibile, mostrato accanto al nome tramite il componente `ContactNameWithPhone`.
- Numero totale di messaggi.
- Indicatore di posizione nel ranking.

---

## 7. Dettaglio chat

**Schermata:** `ChatDetailScreen` / `ChatDetailViewModel`  
**Modelli dati:** `ChatStats` (statistiche veloci), `SlowChatStats` (analisi testuali)

L'apertura è istantanea grazie alla `QuickChatStats` cache per chat. Le statistiche lente vengono caricate progressivamente in background.

### Intestazione

- Avatar del contatto cliccabile. Toccando l'avatar si apre una visualizzazione a schermo intero con overlay nero. Toccando di nuovo si chiude.
- Nome del contatto e numero di telefono (componente `ContactNameWithPhone`).
- Periodo analizzato: data del primo e dell'ultimo messaggio nel filtro selezionato.

**v1.8.1:** L'intestazione viene caricata in anticipo (`HeaderInfo` state) con shimmer placeholder, fornendo feedback visivo immediato. Il nome e l'avatar sono recuperati in background in parallelo alle statistiche, migliorando la percezione delle prestazioni.

### Statistiche di base

- Messaggi totali, suddivisi in inviati e ricevuti, con percentuale.
- Rapporto messaggi inviati/totali (`sentRatio`, valore 0.0–1.0).
- Tempo medio di risposta in minuti/ore. Il calcolo considera solo i cambi di mittente consecutivi (es. da `from_me=1` a `from_me=0` e viceversa) e scarta i gap superiori a 24 ore (86.400.000 ms) per non inquinare la media con periodi di inattività. Query con `LAG()` window function in `calculateAverageResponseTime` di `AnalyticsRepository`.
- Lunghezza media dei messaggi in caratteri.
- Messaggio più lungo.
- Numero totale di media, di cui audio e video separati.

### Chi scrive di più (`SenderBreakdown`)

Donut chart (grafico a ciambella) che visualizza la proporzione di messaggi per partecipante. Il dato si basa su `getSenderBreakdown` in `MessageDao`, che raggruppa per mittente (`from_me` + `sender_jid_row_id`). Nei gruppi con più di 10 partecipanti viene mostrato un pulsante "Mostra tutti" per espandere la lista. La legenda include nome e numero di telefono di ogni partecipante.

### Chi inizia le conversazioni (`ConversationStarterBreakdown`)

Donut chart analogo che conta quante volte ogni partecipante ha inviato il primo messaggio dopo almeno 3 ore di silenzio (10.800.000 ms). La logica usa `LAG()` sulla colonna `timestamp` ordinata per tempo: se la differenza con il messaggio precedente è nulla (primo messaggio in assoluto) o supera 3 ore, si conta come "inizio conversazione". Include un tooltip informativo che spiega la definizione di "iniziare una conversazione". La breakdown è per mittente con nome e numero di telefono.

Nota tecnica rilevante: il totale della breakdown viene calcolato sommando i valori della breakdown stessa, non con una query separata, per evitare discrepanze con i filtri temporali (fix in v1.2).

### Grafici di attività

Stesse quattro schede della Dashboard (oraria, settimanale, mensile, annuale), filtrate sulla chat corrente. Implementate con `InteractiveBarChart` con scrolling orizzontale.

### Evoluzione mensile

Grafico a barre con l'andamento mese per mese del numero di messaggi. Dati da `getMonthlyTrend` in `MessageDao`, che raggruppa per `strftime('%Y-%m', ...)` sul timestamp.

### Analisi testi per chat (opt-in)

Stessa logica della Dashboard, ma filtrata per chat. Include in aggiunta il filtro per mittente (switcher "Tutti" / "Per Persona") su emoji e sticker.

**Parole più usate** — `WordCloudView` con selettore limite e filtro per mittente.  
**Emoji più usate** — `InteractiveEmojiCloud` con switcher aggregato/per-persona.  
**Sticker più usati** — `InteractiveStickerCloud` con miniature da `message_media`.

### Log Chiamate

Riepilogo delle chiamate per la chat selezionata. I dati vengono letti dalla tabella `call_log` di `msgstore.db`. Mostra:

- Totale chiamate audio, video e perse.
- Durata totale e durata media.
- Card di dettaglio per ogni voce con avatar circolare e nome del contatto.
- Barra di ricerca lato client per filtrare le voci. La barra viene nascosta automaticamente se non ci sono risultati.

### Media & Link

Statistiche sui contenuti multimediali e sui link condivisi nella chat. Dati letti da `message_media` (per media) e da `message` con filtro `text_data LIKE 'http%'` e dalla tabella `message_link` (per i link). Mostra:

- Conteggio media per tipo MIME (immagini, video, audio, documenti), con dimensione totale.
- Link condivisi raggruppati per dominio, con conteggio e data dell'ultima condivisione per ogni URL. Il tracking della data di ultima condivisione è implementato in `getSharedUrlsWithTimestamps` che ritorna coppie `(testo, timestamp)`.
- Per ogni dominio: breakdown per mittente con avatar e nome.
- Bottom sheet espandibile con dettaglio domini per singolo contatto, con avatar nell'intestazione.
- Barra di ricerca lato client. La barra viene nascosta se non ci sono risultati.

---

## 8. Media Gallery

**Schermata:** `MediaStatsScreen` / `MediaStatsViewModel`

Galleria multimediale per esplorare foto e video scambiati nelle chat, con accesso root per file protetti e supporto Scoped Storage.

### Funzionalità principali

- **Lista media** — Mostra tutti i file foto e video dalle tabelle `message_media` e `addon_message_media`, filtrando per MIME types image/* e video/* ed escludendo tipi di messaggio di sistema (20, 7).
- **Accesso root** — Quando il percorso normale del file non è accessibile (Scoped Storage), utilizza `RootFileFetcher` con `SuFileInputStream` per leggere i file protetti da directory root come `/data/user/*/com.whatsapp/files/Avatars/`.
- **Risoluzione percorsi** — Tenta prima il percorso standard, poi fallback a percorsi root comuni: `/sdcard/Android/media/com.whatsapp/WhatsApp` e `/sdcard/WhatsApp`.
- **Ordinamento** — Toggle tra ordine crescente e decrescente per data del media.
- **Lazy loading** — Caricamento a blocchi di 100 elementi alla volta con `LazyVerticalGrid`.
- **Anteprime video** — Supporto anteprime video tramite dipendenza `coil-video`.
- **Tracking** — Barra di progresso e stato di caricamento visibili durante il fetch.

### Cache

Le statistiche media hanno cache dedicata:
- `media_global_v1` → `media_global_v2` (v1.8) → `media_global_v3` (v1.9)
- `media_chat_v1` → `media_chat_v2` (v1.8) → `media_chat_v3` (v1.9)

L'invalidazione avviene automaticamente al cambio del database (SHA-256).

### Ottimizzazioni prestazionali (v1.9)

**Indice gzipped** — Un indice persistente e gzippato dei file multimediali viene salvato su disco. All'apertura della galleria, l'indice viene caricato in parallelo con la query SQL, eliminando completamente la scansione sequenziale del filesystem. Se l'indice non esiste o è obsoleto (mismatch SHA-256 del DB), viene eseguita una scansione one-time del filesystem per ricostruirlo.

**Supporto Sent** — `RootFileFetcher` tenta il lookup nella directory `/Sent/` per i media inviati, risolvendo correttamente i percorsi per i file multimediali inviati dall'utente.

**Risultato** — Tempo di caricamento passato da secondi a millisecondi nelle riaperture consecutive.

---

## 9. Radar Parole

Funzione di ricerca che conta le occorrenze di una parola o frase in una chat specifica, con breakdown per mittente. Implementata in `searchWordFrequency` di `MessageDao`.

Caratteristiche principali:

- Supporta ricerche multifrase (es. "ci vediamo domani"), non solo singole parole.
- La ricerca parte solo alla pressione esplicita del pulsante di ricerca, non in automatico durante la digitazione.
- Ottimizzazione: pre-filtro SQL con `LIKE '%parola%'` prima del conteggio esatto, che riduce il numero di righe da processare in memoria di un fattore 10–100x.
- La breakdown mostra il conteggio per ogni mittente, distinguendo `from_me=1` (l'utente) dal JID dell'interlocutore. Il mapping al nome del contatto avviene tramite `wa.db`.

---

## 10. Privacy e sicurezza

- Nessuna connessione di rete. Il traffico è bloccato a livello di configurazione Android (`network_security_config.xml` con `cleartextTrafficPermitted="false"` e nessuna trust anchor).
- Tutti i file estratti (chiave, database cifrato, database decifrato, avatar) sono salvati in `context.filesDir`, la directory privata dell'app non accessibile ad altre app senza root.
- La chiave di cifratura AES-256 non viene mai inclusa nei log di sistema.
- I contenuti dei messaggi, i nomi e i numeri di telefono non vengono loggati in produzione.
- I file temporanei vengono eliminati con sovrascrittura a zero prima della cancellazione (`secureDelete`), per evitare recupero da storage.

---

## 11. Gestione degli errori

Gli errori sono modellati con la sealed class `AppError` in `domain/model/AppError.kt`. I casi gestiti:

- **`RootNotAvailable`** — Root non presente o permessi negati. L'app mostra istruzioni per verificare Magisk.
- **`KeyFileNotFound`** — Il file `/data/data/com.whatsapp/files/key` non esiste. Può accadere se il percorso è cambiato con un aggiornamento di WhatsApp.
- **`Crypt14NotFound`** — Il file `msgstore.db.crypt14` non è presente. Può indicare che il backup locale è disabilitato.
- **`Crypt15Detected`** — Rilevato un file `msgstore.db.crypt15` (backup E2E attivo). L'app mostra un messaggio che spiega come disattivare il backup E2E in WhatsApp.
- **`DecryptionFailed(reason)`** — La decifratura AES-GCM è fallita. Causa più comune: la chiave non corrisponde al database (es. WhatsApp reinstallata). Soluzione suggerita: forzare un nuovo backup da WhatsApp.
- **`DatabaseError(message)`** — Il file SQLite è corrotto o ha uno schema non riconosciuto. Può accadere con versioni molto recenti di WhatsApp che modificano lo schema del database.
- **`UnknownError(throwable)`** — Errore imprevisto, con stacktrace.

Tutti gli errori vengono mostrati nella schermata di Setup come card con sfondo `errorContainer` (Material 3) e testo descrittivo.

---

## 12. Struttura dei dati sorgente

### Database `msgstore.db` (WhatsApp 2.23+)

Le tabelle principali usate dall'app:

**`message`** — tabella principale dei messaggi. Campi rilevanti:
- `_id`: chiave primaria
- `chat_row_id`: FK verso `chat._id`
- `from_me`: 0 = messaggio ricevuto, 1 = messaggio inviato
- `sender_jid_row_id`: FK verso `jid._id` del mittente. Attenzione: nelle chat 1-a-1 questo campo è sempre 0 (vedi sezione 12).
- `timestamp`: Unix timestamp in millisecondi (UTC)
- `message_type`: 0 = testo, 1 = foto, 2 = audio, 3 = video, altri valori per sticker, documenti, ecc.
- `text_data`: contenuto testuale del messaggio (null per messaggi non testuali)
- `starred`: 1 se il messaggio ha la stella

**`chat`** — una riga per ogni conversazione.
- `jid_row_id`: FK verso `jid._id` dell'interlocutore o del gruppo
- `subject`: nome del gruppo (null per chat 1-a-1)

**`jid`** — identificatori WhatsApp.
- `raw_string`: JID completo, es. `393391234567@s.whatsapp.net` o `123456789@g.us` per i gruppi
- `server`: `s.whatsapp.net` per contatti, `g.us` per gruppi
- `cached_name`: nome in cache (spesso vuoto nelle versioni moderne)

**`message_media`** — allegati multimediali.
- `message_row_id`: FK verso `message._id`
- `mime_type`: tipo MIME (es. `image/jpeg`, `audio/ogg`, `video/mp4`)
- `file_size`: dimensione in byte
- `media_duration`: durata in millisecondi (per audio e video)

**`message_link`** — link estratti dai messaggi.

**`call_log`** — registro delle chiamate.

**`message_add_on`** — reazioni e modifiche (WhatsApp 2022+).
- `add_on_type`: 1 = reazione, 2 = modifica, 3 = visualizzazione unica

### Database `wa.db`

**`wa_contacts`** — rubrica contatti WhatsApp.
- `jid`: JID del contatto
- `display_name`: nome come appare in WhatsApp
- `number`: numero normalizzato

---

## 13. Comportamenti noti e quirk del database

### `sender_jid_row_id = 0` nelle chat 1-a-1

Nelle chat dirette (non di gruppo), WhatsApp imposta `sender_jid_row_id = 0` per **tutti** i messaggi, sia inviati che ricevuti. Un `JOIN jid ON sender_jid_row_id = j._id` non restituisce mai il mittente in questo contesto.

Il pattern corretto prevede:
- Per i messaggi di gruppo: `JOIN jid ON sender_jid_row_id = j._id` funziona normalmente.
- Per le chat 1-a-1: usare `from_me` per distinguere i messaggi inviati, e ricavare il JID dell'interlocutore facendo JOIN su `chat` e poi su `jid` tramite `chat.jid_row_id`.

Questo pattern è implementato in `MessageDao` nei metodi `searchWordFrequency`, `getLinkStatsBySender` e `getCallStats`. In `AnalyticsRepository` il JID speciale `me@s.whatsapp.net` viene mappato alla stringa "Tu" in tutti i contesti di risoluzione nomi.

### Identificazione chat 1-a-1 vs gruppo

Un JID che contiene `@g.us` è un gruppo; un JID con `@s.whatsapp.net` è una chat 1-a-1. Il campo `ChatStats.isGroup` è calcolato come `jid.contains("@g.us")`.

### Timestamp in UTC

Tutti i timestamp nel database sono in UTC. Le query che calcolano distribuzioni orarie e per giorno della settimana applicano la conversione a ora locale con `datetime(timestamp / 1000, 'unixepoch', 'localtime')` in SQLite.

### Schema versioni recenti

WhatsApp 2.23+ ha separato i dati in più tabelle rispetto allo schema monolitico delle versioni precedenti. Se l'app restituisce `DatabaseError` con un messaggio su colonne o tabelle non trovate, è probabile che WhatsApp abbia rilasciato un aggiornamento che modifica lo schema. In quel caso occorre verificare le query nei DAO e aggiornare i nomi di colonna o tabella.

---

## 14. Roadmap

### Rilasciato (v1.0 — v1.9)

**v1.0 — MVP:** Accesso root, estrazione file, decifratura crypt14, Dashboard globale e lista chat, statistiche base per chat (messaggi, orari, giorni).

**v1.1 — Analytics avanzati:** Filtri temporali su tutte le schermate, word cloud interattiva con tooltip, Radar Parole con occorrenze per mittente, Log Chiamate (audio, video, perse, durata), Media & Link con link raggruppati per dominio, tempo medio di risposta, grafici di evoluzione mensile.

**v1.2 — Donut chart, nomi contatti, performance:** Donut chart "Chi scrive di più" e "Chi inizia le conversazioni" con breakdown per mittente, espansione per gruppi con più di 10 membri, risoluzione nomi contatti tramite `wa.db`, 7 indici SQL compositi, WAL mode, 14 query parallelizzate, progressive loading.

**v1.3 — UX migliorata:** Distribuzione giornaliera/mensile/annuale, schede (tabs) per le metriche di attività, `InteractiveBarChart` con scrolling orizzontale, filtri temporali per tutte le query, estrazione domini per link, progress tracking con `LoadingSection`, icona launcher adattiva.

**v1.4 — Cache intelligente:** Rilevamento cambio database tramite SHA-256, cache completa con `kotlinx.serialization`, cache Quick e Slow Stats globali e per singola chat, invalidazione automatica al cambio del database, analisi lessicale opt-in con pulsante, sticker più usati.

**v1.5 — Emoji & Word Analytics:** Emoji Cloud con dimensione dinamica e filtri temporali, modalità aggregata e per-persona per emoji, selettore limite (3, 5, 10, 20, 30, 50), pool interno di 50 elementi pre-caricati, query unificate per parole ed emoji, lazy loading con progress indicator.

**v1.6 — Avatar:** Estrazione avatar da `/data/user/*/com.whatsapp/files/Avatars/`, supporto profili multipli (XSpace), avatar in lista chat, dettaglio chat, log chiamate e media & link, zoom avatar a schermo intero con overlay, mapping utente corrente tramite `me@s.whatsapp.net` / `me.jpg`.

**v1.7 — Statistiche avanzate e UX:** Sticker cloud interattivo con miniature e modal di anteprima, numero di telefono accanto al nome in tutte le schermate, ricerca multifrase nel Radar Parole, ricerca manuale su pressione pulsante (non automatica), `DateRangePicker` con pulsante "Cerca" con stati verde/rosso, correzione "Media/giorno" per usare l'intervallo selezionato, sticky header con filtri temporali nella Dashboard, skeleton loading su tutte le schermate principali (`ShimmerUtils`), modal light per emoji e sticker, tracking data ultima condivisione per ogni URL.

**v1.8 — Media Gallery:** Galleria multimediale con accesso root a file protetti, risoluzione percorsi Scoped Storage (`/sdcard/Android/media/com.whatsapp/WhatsApp`, `/sdcard/WhatsApp`), lettura file tramite `SuFileInputStream` quando l'accesso diretto fallisce, griglia lazy con caricamento blocchi da 100, ordinamento per data (ascendente/discesa), cache keys aggiornate (`global_stats_v2→v3`, `chat_quick_v2→v3`, `media_global_v1→v2`, `media_chat_v1→v2`), supporto anteprime video via `coil-video`.

**v1.8.1 — Chat Detail UX:** Caricamento anticipato dell'header (`HeaderInfo` state) con shimmer placeholder, query asincrona per nome contatto e avatar eseguita in parallelo alle statistiche, header visibile immediatamente quando disponibile, migliorata percezione delle prestazioni.

**v1.9 — Media Index Cache:** Introdotto indice gzipped persistente su disco dei file multimediali, caricamento in parallelo con query database per eliminare scansione sequenziale, fallback a scansione filesystem one-time solo se indice mancante o stale, supporto variante `/Sent/` in `RootFileFetcher` per media inviati, tempo di caricamento galleria ridotto da secondi a millisecondi nelle riaperture.

### Pianificato (non ancora implementato)

- Confronto tra due periodi temporali
- Supporto WhatsApp Business
- Modalità "time capsule" (analisi di un anno fa)
- Supporto formato crypt15 con inserimento manuale della chiave a 64 hex
- Backup automatico delle statistiche (non dei messaggi)

---

> Documento aggiornato alla versione **1.9**. Se WhatsApp rilascia un aggiornamento che modifica il formato del database o i percorsi dei file, verificare i DAO in `data/db/dao/` e il `FileExtractor` in `data/root/`.