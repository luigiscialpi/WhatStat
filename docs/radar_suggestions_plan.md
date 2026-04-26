# Piano Implementazione: Suggerimenti di Ricerca Radar

Implementazione di suggerimenti testuali dinamici nella funzione Radar, basati sull'indice lessicale esistente.

## Obiettivi

1.  **Suggerimenti**: Mostrare una lista di parole suggerite mentre l'utente digita.
2.  **Soglie di attivazione**:
    *   **Ricerca**: Abilitata da **2 lettere** in poi.
    *   **Suggerimenti**: Abilitati da **3 lettere** in poi.
3.  **Performance**: Utilizzo dell'indice lessicale (v1.14) per evitare query pesanti su SQLite durante la digitazione.

---

## Modifiche Proposte

### 1. Data Layer (`AnalyticsRepository.kt`)

Aggiungere un metodo per estrarre suggerimenti dall'indice in memoria o su disco.

*   **Metodo**: `getWordSuggestions(query: String, chatId: Long?): List<String>`
*   **Logica**:
    1.  Carica `WordFrequencyIndexData` (globale o per chat).
    2.  Filtra le chiavi della mappa `wordFrequencies` che iniziano con la `query`.
    3.  Ordina per frequenza (conteggio) decrescente.
    4.  Ritorna le prime 10 parole.

### 2. UI State (`WordSearchViewModel.kt`)

Gestione dello stato dei suggerimenti.

*   **State**: `_suggestions = MutableStateFlow<List<String>>(emptyList())`.
*   **Logica**:
    *   In `onSearchQueryChange(query)`:
        *   Se `query.length >= 3`, avvia coroutine per recuperare suggerimenti.
        *   Altrimenti, svuota la lista dei suggerimenti.
    *   In `startSearch()`: Svuota i suggerimenti quando la ricerca ufficiale viene avviata.

### 3. UI Layer (`WordSearchScreen.kt`)

Visualizzazione dei suggerimenti tramite componenti Material 3.

*   **Componente**: `LazyRow` inserita tra la barra di ricerca e la sezione risultati.
*   **Elemento**: `SuggestionChip` per ogni parola suggerita.
*   **Interazione**: Al click su un chip:
    1.  Aggiorna il testo nella barra di ricerca.
    2.  Esegue `viewModel.startSearch()`.
    3.  Nasconde i suggerimenti.

---

## Piano di Verifica

### Test Manuali
1.  Digitare 2 caratteri: verificare che il pulsante di ricerca sia attivo ma non appaiano suggerimenti.
2.  Digitare 3 caratteri: verificare l'apparizione dei suggerimenti pertinenti.
3.  Cliccare su un suggerimento: verificare che il testo venga completato e la ricerca avviata automaticamente.
4.  Cancellare il testo: verificare che i suggerimenti spariscano sotto la soglia di 3 caratteri.

### Performance
*   Verificare che il caricamento dell'indice non causi lag nell'interfaccia (uso di `Dispatchers.IO`).
