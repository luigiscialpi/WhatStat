# Linee guida: conservare lo stato durante la rotazione

Lo scopo di questa guida è evitare che una schermata perda lo stato necessario quando Android ricrea l'Activity dopo una modifica di configurazione, come la rotazione. Qui, per **migrazione**, si intende spostare ogni stato nel componente che ne può garantire la durata corretta.

## Scegliere dove conservare lo stato

La scelta dipende da quanto a lungo lo stato deve vivere e se può essere ricostruito:

| Tipo di stato | Dove conservarlo |
| --- | --- |
| Stato temporaneo della UI, come dialogo aperto o filtro selezionato | `rememberSaveable` in Compose |
| Stato della schermata o logica di dominio durante la rotazione | `ViewModel` |
| Dati minimi da ripristinare anche dopo la terminazione del processo | `SavedStateHandle` |
| Dati utente che devono restare disponibili nel tempo | Database, DataStore o file |

Un `ViewModel` sopravvive alla ricreazione dell'Activity, ma non alla terminazione del processo. `SavedStateHandle` e lo storage persistente coprono casi diversi: salvare solo i dati necessari e ricostruire quelli derivabili.

## Procedura di migrazione

Per migrare una schermata senza aggiungere persistenza inutile, procedere per passi:

1. **Inventariare lo stato.** Elencare dialoghi, selezioni, dati caricati e operazioni asincrone.
2. **Assegnare un proprietario.** Usare `rememberSaveable` per lo stato UI semplice; spostare nel `ViewModel` lo stato condiviso o legato alla logica della schermata.
3. **Salvare il minimo necessario.** Preferire identificativi, flag e parametri di ricerca a oggetti grandi o ricostruibili.
4. **Gestire i cambi di layout.** In Compose, sollevare lo stato sopra i rami che cambiano con l'orientamento. Usare `movableContentOf` solo quando lo stesso contenuto deve spostarsi tra rami mantenendo la propria composizione.
5. **Rivedere callback e operazioni asincrone.** Un risultato di un selettore o di un permesso può arrivare mentre la schermata ricreata sta ancora caricando i dati. Recuperare allora i dati dal proprietario corretto o ricostruirli; non produrre un risultato vuoto come valore di ripiego.
6. **Separare la UI dal lavoro durevole.** Se un'operazione deve continuare anche quando la schermata scompare, non affidarla a uno scope legato alla composizione.

In Compose, `remember` da solo non ripristina il valore dopo la ricreazione. Per un semplice flag della UI, usare `rememberSaveable`:

```kotlin
var dialogOpen by rememberSaveable { mutableStateOf(false) }
```

`rememberSaveable` è adatto a valori salvabili nello stato dell'Activity. Per oggetti personalizzati, salvare una rappresentazione compatta con un `Saver` oppure conservarne l'identificativo e ricostruire l'oggetto. Non salvare `Context`, `Activity`, `View`, coroutine o payload voluminosi.

## Verifiche

La rotazione e la terminazione del processo sono scenari distinti; testarli separatamente. Durante la rotazione, verificare almeno questi casi:

- schermata durante il caricamento e caricamento completato;
- dialogo aperto;
- selettore di file o richiesta di permesso in corso;
- ritorno dal selettore mentre i dati della schermata non sono ancora pronti;
- più rotazioni consecutive.

Controllare che la UI ripristini lo stato previsto e che callback o salvataggi non perdano dati, non producano output vuoti e non ripetano effetti collaterali. Per il ripristino dopo la terminazione del processo, verificare separatamente `SavedStateHandle` o lo storage persistente scelto.

**Regola:** non disabilitare la ricreazione dell'Activity con `android:configChanges` come scorciatoia. Gestire lo stato tramite i componenti Android che ne definiscono esplicitamente la durata.
