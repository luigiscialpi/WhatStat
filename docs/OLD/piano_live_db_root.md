# Piano di Sviluppo: Root Live DB (Bypass crypt14)

## Problema

WhatsApp utilizza SQLite in modalita **WAL** (Write-Ahead Logging). I messaggi recenti
vengono scritti nel file `msgstore.db-wal` e consolidati nel file principale `msgstore.db`
solo periodicamente (checkpoint). Quando WhatsApp genera il backup locale `.crypt14`,
spesso **non esegue un checkpoint completo** del WAL prima di cifrare. Il risultato e'
che il file `.crypt14` contiene una "fotografia" del database incompleta: mancano gli
ultimi giorni di messaggi ancora presenti solo nel WAL.

Questo problema e' strutturale e non risolvibile lato WhatStat con l'approccio attuale
(decifra il `.crypt14`). L'unica soluzione e' **bypassare completamente il backup cifrato**
e copiare direttamente il database live dalla directory protetta di WhatsApp.

### Perche' abbiamo il vantaggio

WhatStat ha gia' i permessi Root. La directory `/data/data/com.whatsapp/databases/`
e' accessibile via `SuFile`/`SuFileInputStream`. I file che ci interessano sono:

| File | Ruolo |
|------|-------|
| `msgstore.db` | Database principale messaggi |
| `msgstore.db-wal` | Write-Ahead Log (messaggi recenti) |
| `msgstore.db-shm` | Shared Memory Map (indice WAL, necessario per leggere il WAL) |
| `wa.db` | Database contatti |
| `wa.db-wal` | WAL del database contatti |
| `wa.db-shm` | SHM del database contatti |

> **IMPORTANTE**: Copiare `msgstore.db` senza `msgstore.db-wal` e `msgstore.db-shm` produrrebbe
> esattamente lo stesso problema di prima: dati incompleti. Tutti e tre i file
> devono essere copiati atomicamente.

---

## Architettura della Soluzione

### Principio: "Live-first, crypt14-fallback"

1. **Tentativo primario**: Copia diretta dei file live da `/data/data/com.whatsapp/databases/`.
2. **Fallback**: Se la copia live fallisce (permessi, file non trovati, DB corrotto),
   si ricade nel vecchio flusso: estrazione `crypt14` + key + decifrazione.
3. **Modalita' Lite**: Completamente intatta. Nessun file, flusso o cache Lite viene toccato.

### Flusso di Setup Root — NUOVO

```
Step 1: Verifica Root               (invariato)
Step 2: Estrazione Live DB          (NUOVO -- sostituisce estrazione crypt14)
        |
        +---> Successo? ---> Step 3
        |
        +---> Fallimento? ---> Fallback: estrai crypt14 + key
                                          |
                                          +---> Step 2b: Decifra crypt14
                                          +---> Step 3
Step 3: Apertura database           (invariato)
Step 4: Lettura contatti            (invariato)
```

### Step di UI nella SetupScreen

Per non confondere l'utente, la barra di progresso deve adattarsi:

- **Flusso Live**: Root -> Estrazione -> ~~Decifratura~~ -> Contatti (3 step, la decifratura viene saltata e marcata SUCCESS)
- **Flusso Fallback**: Root -> Estrazione -> Decifratura -> Contatti (4 step, come oggi)

---

## Fase 1: FileExtractor — Nuovo metodo `extractLiveDb()`

### File: `data/root/FileExtractor.kt`

Aggiungere un nuovo metodo pubblico `extractLiveDb()` che restituisce `Result<LiveExtractedFiles>`.

### Nuova data class

```kotlin
data class LiveExtractedFiles(
    val msgstoreDb: File,
    val waDbFile: File,
)
```

### Logica di `extractLiveDb()`

```
1. Trova la directory databases di WhatsApp:
   - Iterare su /data/user/*/com.whatsapp/databases/
   - Prendere il primo userId dove msgstore.db esiste
   (stessa strategia gia' usata da findWaDbFile())

2. Copiare i file in quest'ordine PRECISO:
   a. msgstore.db-wal  -> filesDir/msgstore.db-wal
   b. msgstore.db-shm  -> filesDir/msgstore.db-shm
   c. msgstore.db      -> filesDir/msgstore.db
   d. wa.db-wal        -> filesDir/wa.db-wal   (se esiste)
   e. wa.db-shm        -> filesDir/wa.db-shm   (se esiste)
   f. wa.db            -> filesDir/wa.db

3. Estrarre gli avatar (riuso di extractAvatarDir())

4. Restituire LiveExtractedFiles con i percorsi locali
```

> **ATTENZIONE — Ordine di copia critico**: Il WAL deve essere copiato PRIMA del file principale.
> Se copiamo prima `msgstore.db` e WhatsApp nel frattempo esegue un checkpoint,
> il WAL che copiamo dopo potrebbe essere incoerente col DB principale.
> Copiando WAL per primo, nel peggiore dei casi abbiamo transazioni extra
> nel WAL che SQLite ignorera' perche' non ancora committate (safe).

> **IMPORTANTE**: Usare lo stesso pattern gia' presente in `copyWithSuFile()`.
> Non usare `Shell.cmd("cp ...")` perche' non gestisce bene i permessi SELinux
> su alcuni dispositivi.

### Gestione errori in `extractLiveDb()`

- Se `msgstore.db` non esiste nel path trovato -> `Result.failure(IOException("..."))`
- Se la copia di `msgstore.db-wal` fallisce -> loggare warning ma continuare
  (il WAL potrebbe non esistere se WhatsApp ha appena fatto un checkpoint completo)
- Se la copia di `msgstore.db` fallisce -> `Result.failure`
- Se `wa.db` non esiste -> continuare (wa.db e' gia' opzionale nel sistema attuale)

### Calcolo signature per rilevamento cambiamenti

Il metodo `calculateSourceCrypt14Hash()` va affiancato con un
`calculateSourceDbSignature()` generico che:

1. Prima prova a leggere la signature dal DB live (`/data/data/.../databases/msgstore.db`)
2. Se non accessibile, ricade sulla signature del crypt14 (come oggi)

La signature sara' sempre `"${file.length()}_${file.lastModified()}"` sul file sorgente
(non sulla copia locale).

### Snippet di riferimento: `calculateSourceDbSignature()`

```kotlin
// In FileExtractor.kt
fun calculateSourceDbSignature(): String? {
    return try {
        // Prima: prova il DB live (percorso diretto)
        val dataUser = SuFile("/data/user")
        dataUser.listFiles()?.forEach { userDir ->
            val dbDir = SuFile(userDir, "com.whatsapp/databases")
            val msgstoreDb = SuFile(dbDir, "msgstore.db")
            if (msgstoreDb.exists()) {
                // Includiamo anche WAL size per catturare cambiamenti recenti
                val walFile = SuFile(dbDir, "msgstore.db-wal")
                val walSize = if (walFile.exists()) walFile.length() else 0L
                return "${msgstoreDb.length()}_${msgstoreDb.lastModified()}_${walSize}"
            }
        }
        // Fallback: crypt14 (per chi usa ancora il vecchio flusso)
        val cryptPath = findCryptFile()
        val f = SuFile(cryptPath)
        "${f.length()}_${f.lastModified()}"
    } catch (e: Exception) {
        android.util.Log.w("WhatStat", "calculateSourceDbSignature failed: ${e.message}")
        null
    }
}
```

> **IMPORTANTE**: La signature include **anche la dimensione del WAL**. Questo e' cruciale:
> se l'utente riceve nuovi messaggi, `msgstore.db` potrebbe non cambiare
> (non c'e' stato un checkpoint), ma `msgstore.db-wal` crescera'. Senza
> questa componente, il rilevamento fallirebbe esattamente come prima.

### Pulizia in `cleanup()`

Aggiornare `cleanup()` per eliminare anche i file WAL e SHM:

```kotlin
listOf(
    KEY_DEST, CRYPT14_DEST, WA_DB_DEST, "msgstore.db",
    "msgstore.db-wal", "msgstore.db-shm",
    "wa.db-wal", "wa.db-shm",
).forEach { name ->
    File(workDir, name).delete()
}
```

---

## Fase 2: WhatsAppRepository — Orchestrazione del nuovo flusso

### File: `data/repository/WhatsAppRepository.kt`

### Modifiche a `performSetup()`

Il metodo `performSetup()` deve tentare prima il flusso Live, poi il fallback.

```
// Pseudocodice

Step 1: Root check (invariato)

Step 2: Tentativo Live DB
    val liveResult = fileExtractor.extractLiveDb()
    if (liveResult.isSuccess) {
        // Salta Step 3 (decifratura)
        // Segna decifratura come SUCCESS nella UI
        // Vai direttamente a Step 4
        val liveFiles = liveResult.getOrThrow()
        outputDb = liveFiles.msgstoreDb
        waDbFile = liveFiles.waDbFile
    } else {
        // FALLBACK: vecchio flusso crypt14
        Step 2b: fileExtractor.extractAll()   // estrae key + crypt14 + wa.db
        Step 3:  decryptor.decrypt(...)        // decifra
        outputDb = File(filesDir, "msgstore.db")
        waDbFile = File(filesDir, "wa.db")
    }

Step 4: database.open(outputDb, waDbFile)   // invariato
        invalidazione cache (invariato)
```

### Dettaglio sulla decifratura skippata

Quando il flusso Live ha successo, la UI deve mostrare:
- Root: SUCCESS
- Estrazione: SUCCESS
- Decifratura: SUCCESS (saltata, non serve)
- Contatti: LOADING -> SUCCESS

Per ottenere questo, emettere gli stati in sequenza rapida:

```kotlin
_setupState.update {
    it.copy(
        extractionStatus = GlobalStepStatus.SUCCESS,
        decryptionStatus = GlobalStepStatus.SUCCESS, // skip, gia' in chiaro
        contactsStatus = GlobalStepStatus.LOADING,
    )
}
```

### Modifiche a `tryRestoreSession()`

La logica di `tryRestoreSession()` deve aggiornare il calcolo della signature
per usare il nuovo `calculateSourceDbSignature()` che controlla prima il DB live.

Tutto il resto del metodo (confronto hash, flag `hasNewDbAvailable`) resta invariato.

### Nessuna modifica a `performManualSetup()`

Il setup manuale (l'utente fornisce i file a mano) non e' coinvolto in questa modifica.
Resta identico.

---

## Fase 3: WhatsAppDatabase — Gestione WAL nella copia locale

### File: `data/db/WhatsAppDatabase.kt`

### Il problema

Quando apriamo `msgstore.db` con `SQLiteDatabase.openDatabase()` in modalita'
`OPEN_READWRITE`, SQLite cerca automaticamente `msgstore.db-wal` e `msgstore.db-shm`
nella stessa directory. Se i file sono presenti, SQLite li integra automaticamente
nel database durante l'apertura.

**Questo e' esattamente il comportamento che vogliamo**. SQLite fara' il "replay"
delle transazioni dal WAL, rendendo i messaggi recenti visibili nelle query.

### Cosa puo' andare storto

1. **WAL corrotto o parziale**: SQLite e' robusto. Se una transazione nel WAL e'
   incompleta, viene semplicemente ignorata (rollback implicito). Nessun crash.

2. **SHM mancante**: Se copiamo WAL ma non SHM, SQLite ricostruisce SHM da zero.
   Leggero rallentamento alla prima apertura, ma funziona.

3. **WAL mancante**: Se il WAL non esiste sul dispositivo (raro, post-checkpoint),
   SQLite apre solo il file principale. Perfettamente funzionante.

### Azione richiesta

**Nessuna modifica al codice di `WhatsAppDatabase.kt`**. Il comportamento attuale e'
gia' corretto:
- Apre in `OPEN_READWRITE` (necessario per creare indici)
- Abilita WAL mode con `enableWriteAheadLogging()`
- SQLite gestisce il replay del WAL in autonomia

> **NOTA**: Dopo che SQLite ha fatto il replay del WAL, al primo checkpoint (automatico o alla
> chiusura), il WAL viene consolidato nel file principale. Alle riaperture successive
> il file `msgstore.db` nella nostra `filesDir` contera' gia' tutti i messaggi.

---

## Fase 4: Protezione della Modalita' Lite

### Regola fondamentale

**Nessun file, flusso, cache o UI della modalita' Lite deve essere modificato.**

### Checklist di verifica

- [ ] `extractLiveDb()` scrive SOLO in `filesDir` con nomi `msgstore.db`, `wa.db` e relativi WAL/SHM.
      Nessuno di questi nomi collide con file Lite (`lite_media/`, `lite_chats.db`).
- [ ] `clearRootCache()` (gia' implementato) cancella solo il DataStore `stats_cache_root`.
      Non tocca `stats_cache_lite`.
- [ ] `cleanup()` nel repository cancella solo file Root (`msgstore.db`, `wa.db`, `key`, `crypt14`, WAL, SHM, avatars).
      Non tocca `lite_chats.db` o `lite_media/`.
- [ ] `isLiteMode` non viene mai modificato durante il flusso Root. Lo switch avviene solo
      in `SetupViewModel.startSetup()` / `activateLiteMode()`.
- [ ] Gli indici lessicali Root (`root_word_index_*.gz`) e Lite (`lite_word_index_*.gz`)
      hanno prefissi diversi. L'invalidazione Root non tocca quelli Lite.

---

## Fase 5: Invalidazione Cache e Signature

### Logica attuale (da aggiornare)

Oggi la signature e' calcolata sul file `crypt14` nella sdcard. Con il nuovo flusso,
la signature deve essere calcolata sul file `msgstore.db` sorgente live.

### Impatto sulla cache

Il cambio di formato della signature (da `"size_lastmod"` a `"size_lastmod_walsize"`)
causera' un'invalidazione automatica della cache alla prima esecuzione con la nuova
versione dell'app. Questo e' il comportamento desiderato: forza il ricalcolo delle
statistiche con i dati completi (inclusi i messaggi che prima mancavano dal WAL).

---

## Fase 6: Gestione Errori e Casi Limite

### Caso 1: WhatsApp in uso durante la copia

WhatsApp potrebbe star scrivendo nel WAL mentre noi lo copiamo.

**Mitigazione**: SQLite e' progettato per gestire questo. Un WAL parzialmente scritto
(transazione non completa) viene ignorato da SQLite al replay. Nel peggiore dei casi
perdiamo l'ultimo messaggio (in arrivo durante la copia), ma al prossimo aggiornamento
sara' incluso.

### Caso 2: WhatsApp non installato

`findLiveDbDirectory()` non trovera' `/data/data/com.whatsapp/databases/`.
Il fallback al crypt14 fallira' anch'esso. L'errore mostrato e' `Crypt14NotFound`.

**Azione**: Nessuna modifica. Il messaggio di errore attuale e' gia' appropriato.

### Caso 3: Profili multipli (XSpace, Dual App, Work Profile)

La strategia attuale itera su `/data/user/*/com.whatsapp/databases/`.
`extractLiveDb()` deve usare la stessa iterazione.

**Azione**: Riusare `findWaDbFile()` come riferimento per l'iterazione userId.

### Caso 4: Permessi SELinux restrittivi

Alcuni dispositivi con kernel custom hanno policy SELinux che bloccano `SuFileInputStream`
anche con root.

**Mitigazione**: Se `extractLiveDb()` fallisce con IOException, il fallback al crypt14
(che passa per `/sdcard/`, accessibile anche con SELinux restrittivo) si attiva
automaticamente.

### Caso 5: Database principale corrotto (header non SQLite)

Dopo la copia, `WhatsAppDatabase.open()` tenta di aprire il file.
Se l'header non e' valido, `SQLiteDatabase.openDatabase()` lancia
`SQLiteDatabaseCorruptException`.

**Mitigazione**: Il `catch (e: Exception)` in `performSetup()` lo gestisce gia',
mostrando `DatabaseError`. Nessuna modifica necessaria.

### Caso 6: WAL troppo grande (>1 GB)

Se WhatsApp non ha fatto checkpoint da molto, il WAL potrebbe essere enorme.

**Mitigazione**: La copia con `SuFileInputStream.copyTo()` e' streaming (buffer 8KB).
Non carica l'intero file in memoria. Su device con storage limitato, se lo spazio
non basta, `FileOutputStream` lancera' IOException -> fallback al crypt14.

### Caso 7: file msgstore.db bloccato da WhatsApp

Improbabile: WhatsApp usa file locking cooperativo (fcntl), non esclusivo.
`SuFileInputStream` bypassa i lock perche' opera come root.

---

## Ordine di Implementazione

### Step 1 — `FileExtractor.kt`

1. Aggiungere `data class LiveExtractedFiles`
2. Implementare `extractLiveDb()`
3. Implementare `calculateSourceDbSignature()` (affianca `calculateSourceCrypt14Hash()`)
4. Aggiornare `cleanup()` con i nuovi file (WAL, SHM)

### Step 2 — `WhatsAppRepository.kt`

1. Aggiornare `performSetup()` con try-live-then-fallback
2. Aggiornare `tryRestoreSession()` per usare `calculateSourceDbSignature()`
3. Aggiornare il calcolo hash in `performSetup()` e `performManualSetup()`

### Step 3 — Verifica

1. Verificare che `WhatsAppDatabase.open()` non necessiti modifiche
2. Verificare che la modalita' Lite non sia impattata (checklist Fase 4)
3. Verificare che `cleanup()` pulisca tutti i nuovi file

### Step 4 — Test manuale

1. Con WhatsApp installato e root: verificare che i messaggi recenti appaiano
2. Senza WhatsApp (o senza root): verificare che il fallback funzioni
3. Dopo aggiornamento: verificare che la cache venga invalidata e ricalcolata
4. In modalita' Lite: verificare che nulla sia cambiato

---

## File Coinvolti (Riepilogo)

| File | Tipo modifica |
|------|--------------|
| `data/root/FileExtractor.kt` | Nuovo metodo + nuova data class + aggiornamento cleanup |
| `data/repository/WhatsAppRepository.kt` | Logica try-fallback in performSetup + nuova signature |
| `data/db/WhatsAppDatabase.kt` | Nessuna modifica necessaria |
| `ui/screen/setup/SetupViewModel.kt` | Nessuna modifica necessaria |
| `ui/screen/setup/SetupScreen.kt` | Nessuna modifica necessaria |
| `data/cache/CacheManager.kt` | Nessuna modifica necessaria |
| Tutto il codice Lite | **NON TOCCARE** |

---

## Note Finali

- Il vecchio sistema (crypt14 + key + decifrazione) **resta nel codice** come fallback.
  Non va rimosso. Se il flusso Live fallisce, l'utente ottiene comunque i dati
  (anche se con il ritardo del WAL). Meglio dati vecchi di 5 giorni che nessun dato.

- La `Crypt14Decryptor` non viene modificata ne' rimossa. Resta disponibile
  per il fallback e per il setup manuale.

- Il setup manuale (`performManualSetup()`) non e' coinvolto. L'utente che
  fornisce file a mano sta gia' usando un DB decifrato esternamente.
