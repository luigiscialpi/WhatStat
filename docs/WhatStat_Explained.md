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
14. [Struttura della navigazione](#14-struttura-della-navigazione)
15. [Roadmap](#15-roadmap)
16. [Modalità Lite (non-root)](#16-modalità-lite-non-root)

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

**Indice lessicale incrementale** — Le frequenze di parole ed emoji globali e per-chat vengono persistite su disco come file gzippati (`word_index_global.gz`, `word_index_chat_{id}.gz` in `filesDir`/`cacheDir`). Ogni file memorizza la mappa completa `parola → conteggio`, il timestamp dell'ultimo messaggio processato e il totale dei messaggi come sanity check. Quando il DB cambia (SHA-256 diverso), l'app esegue un **delta**: legge solo i messaggi con `timestamp > lastProcessedTimestamp`, unisce le frequenze e aggiorna il file. Il rebuild completo avviene solo al primo avvio o se il totale messaggi nel nuovo DB è inferiore a quello registrato nell'indice (cambio account/device).

I modelli che strutturano l'indice sono `WordFrequencyIndexData` e `MonthlyWordIndexData`. Gli helper principali in `AnalyticsRepository` sono `buildOrUpdateGlobalWordIndex`, `buildOrUpdateChatWordIndex` e `computeFromMonthlyIndex`. I metodi DAO coinvolti in `MessageDao` sono `getTextsWithTimestamps`, `buildWordFrequencyMap`, `buildEmojiFrequencyMap`, `getGlobalTextMessageCount` e `getChatTextMessageCount`. La stop-word list è definita in `MessageDao.STOP_WORDS` (companion object).

**Indice mensile** — Accanto all'indice globale viene mantenuto `word_index_monthly.gz` (in `filesDir`), che bucket-izza le frequenze per mese ("YYYY-MM"). Per query con filtri temporali che coprono ≥ 28 giorni e il cui `endTime` ricade entro 7 giorni dall'ultimo aggiornamento dell'indice, le parole/emoji vengono calcolate sommando i bucket mensili pertinenti in memoria — zero accessi a SQLite. Per range più brevi (es. Ultimi 7/30 giorni o range custom ravvicinati) continua ad essere usata la query SQL diretta.

**Cache Quick Stats per chat** — Contiene distribuzioni orarie, settimanali, mensili e annuali per ogni singola chat, oltre ai conteggi base. Indicizzata per `chatId`. Garantisce l'apertura istantanea del dettaglio di una chat.

**Cache Slow Stats per chat** — Contiene le analisi testuali per chat: parole più usate, emoji più usate, sticker più usati, breakdown per mittente, breakdown degli starter, tempo medio di risposta. Cachata per `chatId` dopo la prima analisi opt-in.

### Comportamento della cache per i filtri temporali

La cache viene salvata **solo per il filtro `ALL_TIME`**. Quando l'utente applica un filtro temporale diverso (Ultimi 7 giorni, Ultimi 6 mesi, custom, ecc.), le query vengono rieseguite direttamente sul database. Questo è accettabile perché le query filtrate sono veloci grazie agli indici SQL.

### Ottimizzazioni database

Sono presenti 7 indici SQL compositi sulle colonne più usate nelle query: `chat_row_id`, `message_type`, `timestamp`. Il database è aperto in WAL mode (Write-Ahead Logging) per supportare letture concorrenti. Le query pesanti sono parallelizzate con `async`/`await` (fino a 14 query in parallelo per il calcolo delle statistiche globali).

---

## 4. Filtri temporali

Tutte le schermate principali che espongono analisi temporali (Dashboard, Dettaglio chat, Log Chiamate, Media & Link, Radar Parole) supportano filtri temporali applicabili alle metriche. I filtri disponibili sono:

- **Tutto** — nessun filtro, tutti i messaggi
- **Ultimi 30 giorni**
- **Ultimi 6 mesi**
- **Ultimo anno**
- **Scegli** — apre un selettore di intervallo date inline (`DateRangePicker`) senza triggerare alcun reload dei dati; il caricamento avviene solo alla pressione del pulsante di conferma

Il `DateRangePicker` è un componente dedicato con un pulsante "Cerca" che diventa verde quando è attivo e rosso per annullare. I filtri rapidi e il date picker rimangono fissi in cima alla schermata mentre si scorre (sticky header).

Lo stato dei filtri viene mantenuto in memoria tramite `FilterStateHolder` (singleton Hilt, con campi `globalTimeRange/globalCustomRange` e `chatTimeRange/chatCustomRange`), così il range selezionato viene preservato quando si passa tra schermate globali e chat-specifiche. L'UI dei filtri è centralizzata nel componente `TimeRangeFilterRow`, con auto-scroll del carosello al chip selezionato per rendere sempre visibile il filtro attivo. Una progress bar top coerente è presente su tutte le schermate con altezza riservata per ridurre i micro-spostamenti del layout durante i caricamenti. Il chip "Scegli" gestisce uno stato locale `showDatePicker` separato dal `selectedRange`: premere il chip mostra/nasconde il picker senza propagare alcuna chiamata a `setTimeRange()` e senza triggerare query al database.

Il modello dati per i filtri è `TimeRange` (enum con valori: `ALL_TIME`, `LAST_30_DAYS`, `LAST_6_MONTHS`, `LAST_YEAR`, `CUSTOM`) e `DateRange` (con campi `startTime: Long?` e `endTime: Long?`, entrambi Unix timestamp in millisecondi).

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
- Media di messaggi al giorno — calcolata dividendo il totale di messaggi per il numero di giorni nell'intervallo selezionato, non per i giorni dal primo messaggio in assoluto
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

I grafici sono interattivi: toccando una barra appare un popup di selezione inline con il valore esatto; toccare fuori dall'area del grafico chiude la selezione tramite il meccanismo `dismissSignal`.

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
- Numero di telefono del contatto, dove disponibile, mostrato accanto al nome tramite il componente `ContactNameWithPhone`. Il nome supporta scrolling orizzontale automatico con animazione per nomi lunghi, evitando troncamenti.
- Numero totale di messaggi.
- Indicatore di posizione nel ranking.

---

## 7. Dettaglio chat

**Schermata:** `ChatDetailScreen` / `ChatDetailViewModel`  
**Modelli dati:** `ChatStats` (statistiche veloci), `SlowChatStats` (analisi testuali)

L'apertura è istantanea grazie alla `QuickChatStats` cache per chat. Le statistiche lente vengono caricate progressivamente in background.

### Intestazione

- Avatar del contatto cliccabile. Toccando l'avatar si apre una visualizzazione a schermo intero con overlay nero. Toccando di nuovo si chiude. L'anteprima a schermo intero supporta pinch-to-zoom, rotazione a 90°, doppio tap e swipe-to-dismiss tramite `ZoomableImageViewer`.
- Nome del contatto e numero di telefono (componente `ContactNameWithPhone`). Il nome supporta scrolling orizzontale automatico con animazione per nomi lunghi.
- Periodo analizzato: data del primo e dell'ultimo messaggio nel filtro selezionato, visualizzato tramite `DateRangeCard` con sfondo SurfaceVariant e scorrimento orizzontale.

L'intestazione viene caricata in anticipo (`HeaderInfo` state) con shimmer placeholder, fornendo feedback visivo immediato. Il nome e l'avatar sono recuperati in background in parallelo alle statistiche, migliorando la percezione delle prestazioni.

### Statistiche di base

- Messaggi totali, suddivisi in inviati e ricevuti, con percentuale.
- Rapporto messaggi inviati/totali (`sentRatio`, valore 0.0–1.0).
- Tempo medio di risposta in minuti/ore. Il calcolo considera solo i cambi di mittente consecutivi (es. da `from_me=1` a `from_me=0` e viceversa) e scarta i gap superiori a 24 ore (86.400.000 ms) per non inquinare la media con periodi di inattività. Query con `LAG()` window function in `calculateAverageResponseTime` di `AnalyticsRepository`.
- Lunghezza media dei messaggi in caratteri.
- Messaggio più lungo.
- Numero totale di media, di cui audio e video separati.

### Chi scrive di più (`SenderBreakdown`)

Donut chart (grafico a ciambella) che visualizza la proporzione di messaggi per partecipante. Il dato si basa su `getSenderBreakdown` in `MessageDao`, che raggruppa per mittente (`from_me` + `sender_jid_row_id`). Nei gruppi con più di 10 partecipanti viene mostrato un pulsante "Mostra tutti" per espandere la lista. La legenda include nome e numero di telefono di ogni partecipante. I mittenti sono mostrati nell'ordine naturale del database (volume di messaggi decrescente).

Nota: nella sezione **Analisi testi → Per Persona** i chip di selezione mittente applicano un ordinamento locale che porta sempre "Tu" (`me@s.whatsapp.net`) in prima posizione. Questo sort avviene esclusivamente al livello UI (`sendersForChips` in `ChatDetailScreen`) e non altera il dato grezzo `senderBreakdown`, preservando l'ordine per volume in tutte le altre sezioni.

### Chi inizia le conversazioni (`ConversationStarterBreakdown`)

Donut chart analogo che conta quante volte ogni partecipante ha inviato il primo messaggio dopo almeno 3 ore di silenzio (10.800.000 ms). La logica usa `LAG()` sulla colonna `timestamp` ordinata per tempo: se la differenza con il messaggio precedente è nulla (primo messaggio in assoluto) o supera 3 ore, si conta come "inizio conversazione". La spiegazione della definizione è mostrata in un `AlertDialog` modale accessibile tramite pulsante info. La breakdown è per mittente con nome e numero di telefono.

Nota tecnica rilevante: il totale della breakdown viene calcolato sommando i valori della breakdown stessa, non con una query separata, per evitare discrepanze con i filtri temporali.

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

### Cronologia gruppi (Group History Timeline)

Per le chat di gruppo è disponibile una timeline completa della cronologia del gruppo, accessibile tramite un pulsante nella schermata dettaglio. La timeline mostra in ordine cronologico tutti gli eventi rilevanti:

- **Azioni sui partecipanti:** join, leave, add, remove, promote, demote
- **Cambi nome gruppo**
- **Cambi descrizione gruppo** (nuovo)

Questi eventi sono estratti tramite query dedicate in `MessageDao` e consolidati in `AnalyticsRepository.getGroupHistory()`, che normalizza i timestamp e risolve i nomi/avatari dei partecipanti tramite batch JID resolution. La UI utilizza una `LazyColumn` a layout inverso per simulare la timeline chat, distinguendo visivamente tra eventi informativi (es. cambio descrizione) e azioni sui partecipanti tramite l'allineamento (`isLeftAligned`).

**Modelli dati:** La sealed class `GroupEvent` include i tipi `ParticipantAction`, `NameChange` e `DescriptionChange`. Il modello `GroupParticipantHistoryEntity` ha una colonna `textData` per tracciare le descrizioni. I costruttori di `GroupEventUiModel` richiedono i parametri `userJid`, `isLeftAligned` e `onNavigateToHistory`.

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
- **Zoom immagini** — Anteprima a schermo intero con `ZoomableImageViewer` che supporta pinch-to-zoom, rotazione a 90°, doppio tap per zoom toggle e swipe-to-dismiss.

### Cache

Le statistiche media hanno cache dedicata con chiavi `media_global_v3` e `media_chat_v3`. L'invalidazione avviene automaticamente al cambio del database (SHA-256).

### Ottimizzazioni prestazionali

**Indice gzipped** — Un indice persistente e gzippato dei file multimediali viene salvato su disco. All'apertura della galleria, l'indice viene caricato in parallelo con la query SQL, eliminando completamente la scansione sequenziale del filesystem. Se l'indice non esiste o è obsoleto (mismatch SHA-256 del DB), viene eseguita una scansione one-time del filesystem per ricostruirlo.

**Supporto Sent** — `RootFileFetcher` tenta il lookup nella directory `/Sent/` per i media inviati, risolvendo correttamente i percorsi per i file multimediali inviati dall'utente.

**Risultato** — Tempo di caricamento passato da secondi a millisecondi nelle riaperture consecutive.

---

## 9. Radar Parole

Funzione di ricerca che conta le occorrenze di una parola o frase in una chat specifica (o globale), con breakdown per mittente. Implementata in `searchWordFrequency` di `MessageDao`.

### Caratteristiche principali:

- **Suggerimenti intelligenti** — Mentre l'utente digita (da 3 caratteri in poi), appaiono suggerimenti basati sulle parole più frequenti nell'indice lessicale (globale o per-chat). Questo evita di dover digitare interamente parole lunghe o comuni.
- **Supporto sottostringhe** — La ricerca trova tutte le occorrenze della parola digitata, anche se fa parte di una parola più lunga (es. "casa" troverà anche "casale").
- **Icona informativa** — Una "i" nella barra di ricerca spiega chiaramente il funzionamento dei suggerimenti e la logica delle sottostringhe tramite un dialog dedicato.
- **Ricerca esplicita** — La ricerca effettiva (con conteggio per mittente) parte solo alla pressione del pulsante di ricerca o al click su un suggerimento.
- **Ottimizzazione performance** — Pre-filtro SQL con `LIKE '%parola%'` prima del conteggio esatto, che riduce il numero di righe da processare in memoria di un fattore 10–100x.
- **Breakdown per mittente** — Mostra il conteggio per ogni partecipante, distinguendo `from_me=1` (l'utente) dal JID dell'interlocutore. Il mapping al nome del contatto avviene tramite `wa.db`.
- **Anteprima Messaggi** — Toccando il conteggio messaggi di un mittente, si apre una `ModalBottomSheet` che mostra l'elenco completo dei messaggi contenenti la parola cercata.
    - **Ordinamento dinamico**: Toggle tra "Più recenti" (default) e "Meno recenti".
    - **Paginazione**: Caricamento fluido in blocchi da 50 elementi con pulsante "Carica altri".
    - **Keyword Highlighting**: La parola cercata viene evidenziata in grassetto con il colore primario del tema.
    - **Context Awareness**: Visualizzazione automatica del nome della chat per i risultati in modalità globale; layout compatto per la modalità chat-specifica.
- **Opzioni di ricerca avanzata** — Riga di FilterChip sotto la barra di ricerca:
    - **Aa (Case Sensitive)**: permette di distinguere tra maiuscole e minuscole nel conteggio e nell'highlighting.
    - **ab sottolineato (Parola Esatta)**: utilizza confini di parola Unicode `(?<!\p{L})parola(?!\p{L})` per trovare solo termini isolati, escludendo le sottostringhe.
    - **?**: filtra i risultati mostrando solo i messaggi che contengono un punto interrogativo (filtro in memoria).
    - **Multi (Toggle a 3 stati)**: cicla tra tre modalità di ricerca:
        - *OFF*: ricerca della frase esatta.
        - *E (AND)*: messaggi che contengono TUTTE le parole inserite.
        - *O (OR)*: messaggi che contengono ALMENO UNA delle parole inserite.
    - **Icona informativa**: spostata nella riga dei chip per decongestionare l'area di input.

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

## 14. Struttura della navigazione

L'app utilizza una `BottomNavigationBar` Material 3 a 5 tab: **Panoramica**, **Chat**, **Media**, **Radar**, **Chiamate**. Ogni tab mantiene il proprio back stack indipendente tramite `saveState`/`restoreState`.

Il `NavHost` è strutturato con grafi annidati: un grafo Setup e un grafo Main contenente i 5 sotto-grafi per tab. Le schermate Radar, Chiamate e Media sono accessibili sia come radici di tab globali (senza `chatId`) sia come destinazioni chat-specifiche (con `chatId` nel path, es. `word_search/{chatId}`) all'interno del grafo Chat. Quando aperte da una chat, le schermate mostrano un header contestuale con nome chat, avatar e pulsante back tramite il componente `SimpleScreenHeader`. Il nome/avatar della chat è caricato da `AnalyticsRepository.getChatContextInfo(chatId)` e esposto come `StateFlow` nei rispettivi ViewModel (`WordSearchViewModel`, `CallsStatsViewModel`, `MediaStatsViewModel`).

Il `ChatDetailScreen` espone 3 card azione (Radar, Chiamate, Media) disposte in una riga orizzontale 1×3.

---

## 15. Roadmap

### Pianificato (non ancora implementato)

- Confronto tra due periodi temporali
- Supporto WhatsApp Business
- Modalità "time capsule" (analisi di un anno fa)
- Supporto formato crypt15 con inserimento manuale della chiave a 64 hex
- Backup automatico delle statistiche (non dei messaggi)

---

> Se WhatsApp rilascia un aggiornamento che modifica il formato del database o i percorsi dei file, verificare i DAO in `data/db/dao/` e il `FileExtractor` in `data/root/`.

---

## 16. Modalità Lite (non-root)

La **Modalità Lite** è l'alternativa a WhatsApp root-access che permette di analizzare una singola chat senza bisogno di permessi root. Invece di leggere il database `msgstore.db`, l'utente importa uno **ZIP di esportazione WhatsApp** (generato da WhatsApp con "Esporta chat → Includi media").

Questa sezione descrive in dettaglio tutta l'architettura Lite, poiché è separata dalla pipeline Root Mode e deve essere compresa autonomamente.

---

### 16.1 — Panoramica del flusso dati

```
Utente tocca "Importa ZIP"
        ↓
LiteSetupScreen
        ↓
LiteSetupViewModel.onZipSelected(uri)
        ↓  (run 1: parsing preliminare con ownerName temporaneo)
LiteModeManager.importAndProcess(uri, "Guest")
        ↓
FileExtractor.extractFullChatFolder(zipUri)  →  cacheDir/lite_chat_extracted/
        ↓
TextChatParser.parse(txtFile, ownerName)  →  ParseResult(messages, groupEvents)
        ↓
LiteMediaManager.extractMediaFromFolder(folder, messages)  →  LiteMediaStats
        ↓
LiteModeManager.calculateGlobalStats(messages, mediaStats)  →  GlobalStats
        ↓
LiteCacheRepository.saveCache(LiteCacheData)  →  cacheDir/lite_chat_cache.json
        ↓  (risposta: identifiedSenders → UI mostra il picker "Chi sei tu?")
Utente sceglie il proprio nome dal picker
        ↓
LiteSetupViewModel.onSenderSelected(name)
        ↓  (run 2: re-processing con ownerName definitivo)
LiteModeManager.importAndProcess(uri, name)
        ↓  (pipeline identica a sopra, sovrascrive la cache)
analyticsRepository.setLiteMode(true)
analyticsRepository.notifyLiteImportDone()   ← invalida DataStore + bumpa liteCacheVersion
        ↓
Navigazione a NavGraphRoute.Main (DashboardScreen mostra ChatDetailScreen con chatId = -1L)
```

**Nota importante**: il processing viene eseguito due volte. La prima con `ownerName = "Guest"` serve solo per estrarre l'elenco dei mittenti e presentarli all'utente. La seconda, con il nome reale scelto, è quella definitiva e sovrascrive la cache con `isCurrentUser` calcolato correttamente.

---

### 16.2 — Struttura dello ZIP WhatsApp

WhatsApp esporta la chat come ZIP con questa struttura piatta:

```
Chat WhatsApp con Mario Rossi.zip
├── Chat WhatsApp con Mario Rossi.txt   ← export testuale
├── IMG-20240101-WA0001.jpg             ← media allegati
├── VID-20240215-WA0003.mp4
├── AUD-20240310-WA0007.opus
├── PTT-20240401-WA0002.opus            ← messaggi vocali
├── DOC-20240501-WA0001.pdf
└── ...
```

Il file `.txt` contiene una riga per ogni messaggio nel formato:
```
DD/MM/YY, HH:MM - NomeMittente: testo del messaggio
```
o, per messaggi con media:
```
28/07/24, 16:35 - Mario Rossi: IMG-20240728-WA0003.jpg (file allegato)
28/07/24, 16:35 - Mario Rossi: ‎<Media omesso>
```

Lo ZIP viene estratto da `FileExtractor.extractFullChatFolder()` in `cacheDir/lite_chat_extracted/` (cartella svuotata e ricreata a ogni importazione).

---

### 16.3 — Parsing del file di testo (`TextChatParser`)

**File**: `data/lite/TextChatParser.kt`

Il parser usa due regex principali:

| Regex | Scopo |
|-------|-------|
| `headerRegex` | Riga inizio messaggio: `DD/MM/YY, HH:MM - Mittente: body` |
| `systemEventRegex` | Evento di sistema senza due punti (es. "Mario ha lasciato il gruppo") |

**Rilevamento media** (`hasMedia`): un messaggio è considerato multimediale se il `body` contiene una delle seguenti stringhe (formato italiano):
- `"omesso"` / `"omessi"` (‎<Media omesso>)
- `"allegato"` (file allegato)
- `"<Media"`
- `"PTE-"` (messaggi vocali con alcune versioni WhatsApp)

**Punto critico**: il campo `content` è sempre popolato con `cleanBody` anche per i messaggi media. Questo è fondamentale per permettere a `LiteMediaManager` di recuperare il nome file (es. `IMG-20240728-WA0003.jpg`) dal testo del messaggio per associare il file fisico al mittente corretto.

**Modello `ParsedMessage`**:
```kotlin
@Serializable
data class ParsedMessage(
    val timestamp: LocalDateTime,
    val senderName: String,        // nome grezzo dall'export, es. "Mario Rossi"
    val content: String,           // testo (o nome file per i media)
    val isCurrentUser: Boolean,    // true se senderName == ownerName
    val isSystemMessage: Boolean,
    val linkCount: Int,
    val emojiCount: Int,
    val hasMedia: Boolean
)
```

Il `senderName` viene ripulito dal carattere Unicode LTR Mark (`\u200E`) che WhatsApp inserisce. Il campo `isCurrentUser` è calcolato confrontando `cleanSender == ownerName`.

**Righe multi-riga**: le righe che non corrispondono all'`headerRegex` vengono accodate al messaggio corrente tramite `appendToCurrent()`.

---

### 16.4 — Associazione media → mittente (`LiteMediaManager`)

**File**: `data/lite/LiteMediaManager.kt`

Questo componente scansiona tutti i file nella cartella estratta dallo ZIP e, per ciascuno, cerca il messaggio corrispondente nell'elenco dei messaggi parsati.

**Algoritmo di associazione**:
1. Per ogni `ParsedMessage`, `MEDIA_FILENAME_REGEX` viene applicata al campo `content` per trovare il nome del file media menzionato.
2. Viene costruita una mappa `fileNameToMsgInfo: Map<String, Pair<senderName?, timestamp>>` con due chiavi per ogni match: nome completo (con estensione) e nome senza estensione.
3. Quando si itera sui file fisici nella cartella estratta, il nome del file (con e senza estensione) viene cercato nella mappa.
4. Se trovato: `senderName` e `timestamp` provengono dal messaggio corrispondente.
5. Se non trovato: `senderName = null`, `timestamp = file.lastModified()` (fallback).

**`MEDIA_FILENAME_REGEX`** (definita in `LiteMetricsCalculator`):
```kotlin
Regex("(?:IMG|VID|AUD|PTT|DOC)-\\d{8}-WA\\d{4,}(?:\\.[a-z0-9]+)?", RegexOption.IGNORE_CASE)
```

**Classificazione tipo media**:

| Estensione file | Tipo |
|-----------------|------|
| `.jpg`, `.jpeg`, `.png`, `.gif`, `.webp` | `IMAGE` |
| `.mp4`, `.3gp`, `.mkv`, `.avi`, `.mov` | `VIDEO` |
| `.opus`, `.mp3`, `.aac`, `.m4a`, `.ogg` | `AUDIO` |
| altro | `DOCUMENT` |

**Nota sticker**: gli sticker esportati hanno estensione `.webp` e vengono classificati come `IMAGE`. In `GlobalStats.topStickers` vengono inseriti i file `.webp`, ma il conteggio per sticker è sempre 1 (non tiene conto delle ripetizioni tra import diversi).

**Modello `LiteMediaItem`**:
```kotlin
@Serializable
data class LiteMediaItem(
    val fileName: String,
    val relativePath: String,
    val type: String,          // "IMAGE", "VIDEO", "AUDIO", "DOCUMENT"
    val timestamp: Long,       // epoch millis dal messaggio (o da file.lastModified se non trovato)
    val senderName: String?    // null se non associabile a un messaggio
)
```

---

### 16.5 — Cache Lite (`LiteCacheRepository`)

**File**: `data/lite/LiteCacheRepository.kt`

La cache è un singolo file JSON in `context.cacheDir/lite_chat_cache.json`, serializzato con `kotlinx.serialization`.

**Modello `LiteCacheData`**:
```kotlin
@Serializable
data class LiteCacheData(
    val sourceFileName: String,       // nome chat (es. "Mario Rossi") senza prefisso WhatsApp
    val lastModified: Long,           // timestamp dell'importazione
    val totalLines: Int,              // numero di messaggi
    val ownerName: String,            // nome scelto dall'utente (es. "Luca") → visualizzato come "Tu"
    val globalStats: GlobalStats,     // statistiche aggregate calcolate all'import
    val messages: List<ParsedMessage>,// tutti i messaggi, necessari per i filtri temporali
    val mediaStats: LiteMediaStats?   // null se nessun media nello ZIP
)
```

**Operazioni**:
- `saveCache(data)` — serializza e scrive il file; sovrascrive senza controllo versione
- `loadCache()` — deserializza il file; ritorna `null` se non esiste o in caso di errore di parsing
- `clearCache()` — elimina il file fisico

---

### 16.6 — Coordinatore (`LiteModeManager`)

**File**: `data/lite/LiteModeManager.kt`

Singleton Hilt che orchestra l'intero flusso di importazione. Metodi principali:

| Metodo | Descrizione |
|--------|-------------|
| `importAndProcess(zipUri, ownerName)` | Pipeline completa: estrazione → parsing → media → stats → cache |
| `getCachedStats()` | Ritorna `GlobalStats` dalla cache (o `null`) |
| `getOwnerName()` | Ritorna `ownerName` dalla cache — usato per la mappatura "Tu" |
| `getCachedMessages()` | Ritorna la lista completa di `ParsedMessage` dalla cache |
| `getCachedMedia()` | Ritorna `LiteMediaStats` dalla cache |
| `clearCache()` | Delega a `LiteCacheRepository.clearCache()` |

Il calcolo delle `GlobalStats` avviene interamente in memoria in `calculateGlobalStats()`: distribuzione oraria, settimanale, mensile, annuale, top 50 parole (> 3 lettere, dopo pulizia LTR), top 50 emoji, sticker `.webp`.

---

### 16.7 — Integrazione in `AnalyticsRepository`

**File**: `data/repository/AnalyticsRepository.kt`

`AnalyticsRepository` è il punto di integrazione tra Root Mode e Lite Mode. Ogni metodo pubblico ha un branch `if (isLiteMode.value)` che reindirizza la logica alla cache Lite invece che al database.

**Flag di stato**:
```kotlin
private val _isLiteMode = MutableStateFlow(false)
val isLiteMode: StateFlow<Boolean>

private val _liteCacheVersion = MutableStateFlow(0L)
val liteCacheVersion: StateFlow<Long>   // bumped a ogni import, ascoltato dai ViewModel
```

**Helper centrale**:
```kotlin
private suspend fun filteredLiteMessages(
    startTime: Long?, endTime: Long?, jid: String? = null
): List<ParsedMessage>
```
Filtra i messaggi cached per intervallo temporale e, opzionalmente, per mittente. Il `jid` passato dalla Root Mode (es. `"me@s.whatsapp.net"`) non esiste in Lite Mode: la distinzione usa invece `msg.isCurrentUser` (per il mittente corrente) o `msg.senderName == jid` per gli altri.

**Metodi chiave con branch Lite**:

| Metodo | Comportamento Lite |
|--------|--------------------|
| `getQuickGlobalStats(chatId, startTime, endTime)` | Per intervalli non-ALL_TIME: ricalcola da `filteredLiteMessages` invece di ritornare la cache grezza |
| `getMediaFiles(chatId, jid, startTime, endTime)` | Filtra `LiteMediaItem` per `senderName == jid` (o `senderName == null` per il mittente corrente) |
| `getLinkStats(chatId, jid, startTime, endTime)` | Usa `filteredLiteMessages`; mappa `ownerName → "Tu"` |
| `getTopEmojis(chatId, startTime, endTime, limit)` | Usa `filteredLiteMessages` |
| `getEmojiStatsBySender(chatId, startTime, endTime)` | Usa `filteredLiteMessages`; mappa `ownerName → "Tu"` |
| `getWordSuggestions(chatId, prefix)` | Scansiona in-memory i messaggi cached cercando parole che iniziano con `prefix` |

**Mappatura `ownerName → "Tu"**: ogni metodo che restituisce breakdown per mittente deve confrontare il `senderName` con `liteModeManager.getOwnerName()` e sostituirlo con `"Tu"` prima di restituire i dati alla UI.

**Invalidazione cache**:
```kotlin
fun notifyLiteImportDone() {
    _liteCacheVersion.value = System.currentTimeMillis()
    CoroutineScope(Dispatchers.IO).launch { cacheManager.clearCache() }  // svuota DataStore
}

fun resetLiteMode() {
    _isLiteMode.value = false           // sincrono — SetupViewModel lo legge subito
    CoroutineScope(Dispatchers.IO).launch { liteModeManager.clearCache() }
}
```

---

### 16.8 — Invalidazione e ricarica dei ViewModel

Quando viene importato un nuovo ZIP, `notifyLiteImportDone()` bumpa `liteCacheVersion`. Ogni ViewModel che consuma dati Lite osserva questa `StateFlow` nel proprio `init` block e reagisce:

| ViewModel | Reazione al bump di `liteCacheVersion` |
|-----------|----------------------------------------|
| `ChatDetailViewModel` | Chiama `loadChatStats()` direttamente (skippa la prima emissione per evitare doppio carico) |
| `MediaStatsViewModel` | Resetta `initialized = false` e chiama subito `loadStats()` |
| `WordSearchViewModel` | Resetta `initialized = false` (la `LaunchedEffect` richiamerà `initialize()` alla prossima navigazione) |

---

### 16.9 — Navigazione in Lite Mode

**File**: `ui/navigation/AppNavigation.kt`, `ui/screen/dashboard/DashboardScreen.kt`

- **Dopo l'import**: `LiteSetupViewModel` imposta `isComplete = true` → la navigazione usa `popUpTo(0) { inclusive = true }` per svuotare tutto il back stack e portare a `NavGraphRoute.Main`.
- **Dashboard in Lite Mode**: `DashboardScreen` rileva `isLiteMode == true` e renderizza direttamente `ChatDetailScreen` con `chatId = -1L` (sentinella per Lite). Non mostra la lista chat.
- **Tasto back dal Dashboard**: `BackHandler` chiama `onNavigateBack()` → `mainViewModel.resetLiteMode()` (imposta `_isLiteMode = false` + cancella cache) → naviga a Setup con `popUpTo(0) { inclusive = true }`.
- **`SetupViewModel` all'avvio**: controlla `analyticsRepository.isLiteMode.value && liteModeManager.getCachedStats() != null`. Poiché `resetLiteMode()` ha già impostato `_isLiteMode = false` in modo sincrono, questa condizione è `false` e non scatta l'auto-navigazione al Main, evitando il loop.

---

### 16.10 — Limitazioni rispetto alla Root Mode

| Funzionalità | Root Mode | Lite Mode |
|--------------|-----------|-----------|
| Numero di chat analizzabili | Tutte | Solo 1 per import |
| Risoluzione nomi contatti | Sì (da `contacts.db`) | No (nome grezzo dell'export) |
| Query al database SQLite | Sì | No (tutto in-memory) |
| Filtro media per mittente | Esatto (jid) | Euristico (basato su filename-match) |
| Timestamp media | Esatto (db) | Derivato dal messaggio; fallback a `file.lastModified()` |
| Sticker (conteggio ripetizioni) | Sì | No (sempre 1 per item) |
| Chiamate | Sì | No (nessun dato nel `.txt`) |
| `isCurrentUser` per "me@s.whatsapp.net" | Sì | No — usa `ownerName` scelto dall'utente |
| Supporto emoji con skin tone | Sì | Sì (via `LiteMetricsCalculator.stripSkinTone()`) |
| Supporto lingue export | Italiano | Solo italiano (keyword `"omesso"`, `"allegato"`, ecc.) |

---

### 16.11 — File coinvolti (riepilogo)

| File | Ruolo |
|------|-------|
| `data/lite/TextChatParser.kt` | Parsing del file `.txt` WhatsApp |
| `data/lite/LiteMediaManager.kt` | Scansione media e associazione file → mittente |
| `data/lite/LiteMetricsCalculator.kt` | Helper per metriche (emoji, parole, `MEDIA_FILENAME_REGEX`) |
| `data/lite/LiteCacheRepository.kt` | Serializzazione JSON della cache su disco |
| `data/lite/LiteModeManager.kt` | Coordinatore del flusso di importazione |
| `data/root/FileExtractor.kt` | Estrazione ZIP (condiviso con Root) |
| `data/repository/AnalyticsRepository.kt` | Branch Lite in ogni metodo dati pubblico |
| `ui/screen/setup/LiteSetupScreen.kt` | UI onboarding Lite |
| `ui/screen/setup/LiteSetupViewModel.kt` | Logica onboarding (double-processing) |
| `ui/screen/dashboard/DashboardScreen.kt` | Renderizza `ChatDetailScreen` con `chatId = -1L` |
| `ui/navigation/AppNavigation.kt` | Navigazione post-import e back-to-setup |