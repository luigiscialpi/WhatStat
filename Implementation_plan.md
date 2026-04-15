# Piano di Implementazione: WhatStat Reborn Expansion

L'obiettivo è trasformare WhatStat Reborn in un centro di analisi avanzato, recuperando il feeling dell'app originale e aggiungendo potenza di calcolo moderna (Sentiment, Word Analysis, Call Logs).

## User Review Required

> [!IMPORTANT]
> Ho previsto l'aggiunta di una barra di filtri temporali "Sticky" (che resta in alto allo scorrimento) nella Dashboard. Confermi che questo non intralci troppo la visualizzazione dei grafici?

> [!TIP]
> Per l'analisi dei link e dei documenti, utilizzeremo le tabelle native di WhatsApp per mantenere l'app fulminea anche con anni di storico.

## Proposed Changes

---

### 1. Data Layer & Repository (Il Motore)

Per abilitare i filtri temporali ovunque, dobbiamo centralizzare la logica di calcolo del tempo.

#### [MODIFY] [QueryModels.kt](file:///Users/lscialpi/Downloads/Altro/WhatStat-Reborn/app/src/main/java/com/whatstat/reborn/data/db/model/QueryModels.kt)
- Aggiunta dell'enum `TimeRange` (Oggi, Ieri, Ultimi 7 giorni, Mese, Anno, Tutto).
- Creazione di classi DTO per le nuove statistiche: `CallStat`, `LinkStat`, `WordSearchResult`.

#### [MODIFY] [MessageDao.kt](file:///Users/lscialpi/Downloads/Altro/WhatStat-Reborn/app/src/main/java/com/whatstat/reborn/data/db/dao/MessageDao.kt)
- Refactoring di tutte le funzioni di conteggio (es. `getTotalMessageCount`) affinché accettino parametri `startTime` e `endTime` opzionali.
- Inserimento di query specifiche per:
    - Ricerca parole con `LIKE` e raggruppamento per utente.
    - Estrazione media e link tramite `media_wa_type`.
    - Estrazione log chiamate (audio vs video) e durata.

#### [MODIFY] [AnalyticsRepository.kt](file:///Users/lscialpi/Downloads/Altro/WhatStat-Reborn/app/src/main/java/com/whatstat/reborn/data/repository/AnalyticsRepository.kt)
- Aggiornamento della logica per convertire l'enum `TimeRange` in millisecondi epoch.

---

### 2. UI Layer: Dashboard & Navigazione

La Dashboard cambierà volto per diventare un vero hub di navigazione.

#### [MODIFY] [DashboardScreen.kt](file:///Users/lscialpi/Downloads/Altro/WhatStat-Reborn/app/src/main/java/com/whatstat/reborn/ui/screen/dashboard/DashboardScreen.kt)
- **Nuova Sezione Filtri**: Inserimento di una `LazyRow` di `FilterChip` Material 3 in cima allo schermo.
- **Action Grid**: Sostituzione del tasto "Analizza chat" con una `LazyVerticalGrid` a 2 colonne contenente card premium per le nuove sezioni:
    - 🔍 **Radar Parole** (Cerca frequenza e chi le dice).
    - 📞 **Log Chiamate** (Audio/Video, perse e durata).
    - 📎 **Media & Link** (Statistiche su allegati e URL).
    - 💬 **Analisi Chat** (La lista contatti esistente).

---

### 3. Nuove Schermate di Analisi

#### [NEW] `WordSearchScreen.kt`
- Barra di ricerca persistente.
- Grafico a barre orizzontale dei top "mittenti" della parola cercata.
- Cronologia dei picchi d'uso (Heatmap temporale della parola).

#### [NEW] `CallsStatsScreen.kt`
- Indicatori circolari per Chiamate vs Videochiamate.
- Statistiche sulla durata media e totale.
- Lista delle chiamate perse raggruppate per contatto.

#### [NEW] `MediaStatsScreen.kt`
- Suddivisione dei file per tipo (Documenti, Link, Foto, Video).
- Ranking dei contatti che inviano più "spam" o link esterni.

## Open Questions

> [!WARNING]
> Alcune statistiche avanzate (come il Sentiment Analysis o il calcolo millimetrico della durata chiamate nei gruppi) potrebbero richiedere un tempo di calcolo di qualche secondo la prima volta che vengono generate. Vuoi che aggiunga una barra di progresso specifica o bastano gli attuali indicatori ciclici?

## Verification Plan

### Automated Tests
- Non previsti nuovi unit test in questa fase, ci affideremo alla validazione manuale dei dati estratti.

### Manual Verification
1. Verificare che cambiando il filtro "Oggi" i contatori della dashboard si azzerino correttamente (se non ci sono messaggi oggi).
2. Provare la ricerca di una parola comune (es. "ciao") e verificare che la classifica mittenti sia coerente.
3. Testare la navigazione fluida tra i nuovi hub della Dashboard.

---

## ✅ Implementato

Tutte le feature sopra descritte sono state implementate:

### Data Layer
- `TimeRange` enum con: Oggi, Ieri, 7 giorni, 30 giorni, Anno, Personalizzato
- DTO: `CallStat`, `LinkStat`, `WordSearchResult`, `DayCount`, `HourlyCount`
- `DateRange` per filtri custom

### Repository
- TimeRange → epoch milliseconds conversion
- Filtri temporali su tutte le query

### UI - Dashboard
- Filter chips sticky
- Heatmap oraria interattiva (tooltip al tocco)
- Attività settimanale interattiva (tooltip al tocco)
- Action grid con navigazione

### UI - Chat Detail
- Filtri temporali sticky
- Heatmap oraria interattiva
- Attività settimanale interattiva
- Top parole interattive (tooltip al tocco)
- Navigazione a Radar Parole / Calls / Media con chatId

### UI - Word Search
- Ricerca con debounce 300ms
- Filtri temporali
- Filtro per chat
- Conteggio occorrenze (non messaggi)
- Tooltip per utente

### UI - Calls Stats
- Statistiche chiamate (totale, video, perse, durata)
- Filtri temporali
- Filtro per chat (via jid)

### UI - Media Stats
- Statistiche link per utente
- Filtri temporali
- Filtro per chat (via message_link.chat_row_id)
