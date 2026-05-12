# Piano Maestro: ActivityCard Multi-Layer Breakdown

L'obiettivo è trasformare i grafici di attività da semplici indicatori di volume a strumenti di analisi profonda, permettendo all'utente di vedere "chi ha scritto cosa e quando" direttamente dal grafico.

---

## 1. Visione e Obiettivo UI/UX
Vogliamo passare da un tooltip statico a un'esperienza a due livelli:

### Livello 1: La Nuvoletta (Quick Insight)
Al tocco della barra, appare una nuvoletta "Premium" (Glassmorphism, angoli 16dp) che mostra:
- **Titolo**: L'unità temporale (es. **Lunedì** o **14:00 - 15:00**) in grassetto.
- **Totale**: Conteggio complessivo messaggi.
- **Top 3**: Tu + i 2 mittenti più attivi.
- **Espandi**: Un suggerimento visivo tipo `+ Altri 5 (Tocca per i dettagli)`.

### Livello 2: Il Dettaglio Completo (Deep Dive)
Toccando nuovamente la barra selezionata, si apre un **AlertDialog** elegante:
- **Lista Scrollabile**: Tutti i partecipanti della chat ordinati per attività.
- **Estetica**: Pallini colorati per i mittenti, allineamento perfetto tra nomi e statistiche.
- **Esempio Riga**: `● Marco: 150 messaggi (12%)`
- **Chiusura**: Pulsante "Chiudi" dedicato e dismiss al tocco esterno.

---

## 2. Guida Tecnica (Step-by-Step)

### FASE 1: Modifica Modelli Dati (`QueryModels.kt`)
**Percorso:** `app/src/main/java/com/whatstat/reborn/data/db/model/QueryModels.kt`
Aggiungere il campo per il breakdown a tutti i modelli di distribuzione (`HourlyCount`, `DayCount`, ecc.):
```kotlin
val senderBreakdown: List<SenderStat> = emptyList()
```

### FASE 2: Query SQL e DAO (`MessageDao.kt`)
Modificare le query di distribuzione per includere il raggruppamento per mittente:
```sql
SELECT temporal_unit, sender_jid_row_id, COUNT(*) as count
FROM messages ...
GROUP BY temporal_unit, sender_jid_row_id
```
Il DAO deve restituire una lista di righe grezze che verranno poi aggregate in Kotlin.

### FASE 3: Aggregazione nel Repository (`AnalyticsRepository.kt`)
Nella funzione `getQuickChatStats`:
1. Raccogliere i dati grezzi (tempo + mittente).
2. Risolvere i nomi tramite `ContactDao`.
3. Raggruppare per tempo e mappare il `senderBreakdown` ordinandolo per volume.

### FASE 4: Componenti UI (`InteractiveChart.kt` & New Dialog)
1.  **StatPoint**: Aggiornare la classe per trasportare la lista `senderBreakdown`.
2.  **ChartPointBubble**: Riscrivere il composable per gestire il layout multi-riga.
3.  **ActivityDetailDialog.kt**: Creare il nuovo file per l'AlertDialog con `LazyColumn`.

---

## 3. Checklist di Qualità
- [ ] Il tooltip è fluido e non copre il punto di contatto.
- [ ] L'AlertDialog gestisce correttamente centinaia di utenti senza lag (LazyColumn).
- [ ] Le percentuali sono calcolate sul totale della singola colonna.
- [ ] Il pulsante "Chiudi" è ben visibile.
- [ ] Se una chat ha un solo utente (oltre a me), non mostrare il footer "+ Altri".
