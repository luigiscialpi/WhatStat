# Implementazione "Storia del Gruppo"

L'obiettivo di questa funzionalità è fornire all'utente una visualizzazione cronologica (timeline) degli eventi storici di un gruppo WhatsApp, tra cui ingressi, uscite, rimozioni, promozioni ad amministratore e cambi di nome del gruppo.

## User Review Required

> [!IMPORTANT]
> **Visibilità del Pulsante:** La "Storia del gruppo" ha senso solo per le chat di gruppo. Il piano prevede di mostrare la griglia 2x2 solo per i gruppi, mantenendo l'attuale disposizione 1x3 per le chat singole (o nascondendo il 4° pulsante). Sei d'accordo con questa logica o preferisci mostrare il pulsante "Storico" disabilitato per le chat singole?
> 
> **Icona del Pulsante:** Propongo di usare `Icons.Outlined.History` per il pulsante "Storico". 

## Proposed Changes

---
### 1. Data Layer (Database & Domain Models)

Dobbiamo estrarre gli eventi da due fonti principali e unificarli in una timeline.

#### [NEW] `GroupEvent.kt` (in `domain/model/`)
Creazione di una `sealed class` per rappresentare i diversi tipi di evento:
- `GroupEvent.ParticipantAction(timestamp, userJid, actionType, adminJid?)` (Join, Leave, Add, Remove, Promote, Demote)
- `GroupEvent.NameChange(timestamp, oldName, newName, userJid)`

#### [MODIFY] `GroupEvent.kt`
- Aggiornamento di `GroupEventUiModel` per includere i campi `userName: String` e `isMe: Boolean`, necessari per la nuova UI a nuvolette.

#### [MODIFY] `MessageDao.kt` (o creazione `GroupDao.kt`)
Aggiunta di due query principali:
1. **Query Storico Partecipanti:** Lettura dalla tabella `group_participants_history` filtrata per `gjid` (Group JID). Restituisce `timestamp`, `jid`, e `action`.
2. **Query Storico Nomi:** Lettura da `message` unita a `message_system_value_change`. Il `message_type` o l'`action_type` indicano un cambio nome, e `old_data` contiene il vecchio nome. Il testo del messaggio (se disponibile) o lo stato corrente daranno il nuovo nome.

#### [MODIFY] `AnalyticsRepository.kt`
Aggiunta del metodo `getGroupHistory(chatId: Long)`:
- Recupera il JID del gruppo dal `chatId`.
- Esegue in parallelo le query DAO per partecipanti e nomi.
- Unisce i risultati, li ordina per `timestamp` decrescente (dal più recente al più vecchio).
- Risolve i JID dei partecipanti tramite `wa.db` per ottenere il `displayName` e il path dell'avatar.

---
### 2. UI / Presentation Layer

Creazione della nuova schermata e del suo ViewModel.

#### [NEW] `GroupHistoryViewModel.kt`
Gestirà lo stato della schermata:
- Caricamento in corso (`isLoading`)
- Lista degli eventi formattata per la UI (`List<GroupEventUiModel>`)
- Dati del gruppo per l'header (Nome e Avatar)

#### [MODIFY] `GroupHistoryScreen.kt`
- **Header:** Utilizza `SimpleScreenHeader` (come le altre schermate secondarie).
- **Contenuto:** Una `LazyColumn` con `reverseLayout = true`. Questo inverte lo scorrimento, facendo apparire gli eventi più recenti in basso, simulando l'esperienza di apertura di una vera chat di WhatsApp.
- **Componente ChatBubbleItem:** Sostituisce il vecchio layout a linea del tempo (TimelineItem).
  - **Cambi Nome (Stile Ricevuti):** Allineati a sinistra. Presentano l'avatar, la "punta" (angolo a 0.dp) della nuvoletta in alto a sinistra, e il nome del contatto scritto in cima all'interno della nuvoletta.
  - **Ingressi/Uscite/Azioni Partecipanti (Stile Inviati):** Allineati a destra. Nuvoletta verde con la "punta" in alto a destra, ideali per differenziare a colpo d'occhio le modifiche dei membri da quelle testuali/info.
  - **Raggruppamento (Consecutivi):** Algoritmo per raggruppare visivamente eventi consecutivi dello stesso tipo eseguiti dallo stesso utente. Solo il "primo" evento del blocco mostrerà l'avatar, il nome e la punta; i successivi (i più recenti sotto di esso) avranno i bordi smussati e nessun avatar, ricalcando l'impaginazione nativa di WhatsApp.

---
### 3. Navigazione e Integrazione

Integrazione della nuova schermata nell'app esistente.

#### [MODIFY] `AppNavigation.kt`
- Aggiunta della rotta `Screen.GroupHistory` (es. `group_history/{chatId}`).
- Aggiunta del `composable` nel grafo di navigazione principale (`Chats` graph).

#### [MODIFY] `ChatDetailScreen.kt`
Riorganizzazione dell'Action Row alla riga 719:
- Sostituzione della `Row(1x3)` con una `Column` contenente due `Row` (griglia 2x2).
- Riga 1: "Radar Parole", "Log Chiamate".
- Riga 2: "Media & Link", "Storico".
- Aggiunta di logica `if (isGroup)`: se la chat è 1-a-1, manterremo un layout adatto (es. il 4° posto vuoto o ritorno alla 1x3).

## Verification Plan

### Manual Verification
1. Aprire il dettaglio di una chat 1-a-1 e verificare che la UI sia coerente (niente pulsante storico o pulsante nascosto correttamente).
2. Aprire il dettaglio di un gruppo.
3. Cliccare su "Storico".
4. Verificare che l'header della nuova schermata mostri il nome e l'avatar del gruppo.
5. Scorrere la timeline e verificare che ci siano eventi di ingresso/uscita (se disponibili per quel gruppo).
6. Verificare che le date siano formattate correttamente e i nomi dei contatti siano risolti.
