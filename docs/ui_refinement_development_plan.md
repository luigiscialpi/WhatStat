# Piano di rifinitura UI - WhatStat

## Stato

- Stato: IMPLEMENTATO, primo passaggio completato.
- Task o Jira: da associare.
- Branch proposto: `codex/ui-refinement`, non creato.
- Dispositivi: SM_A366B e Redmi Note 9 Pro, build Debug.
- Configurazione: Lite ZIP, tema chiaro.
- Schermate: Home, chat, dettaglio, Media, Radar, Highlights, badge.

## Obiettivo

Ridurre ridondanze visive. Chiarire gerarchia delle statistiche. Intervenire solo sulla UI Compose. Aggiornare risorse testuali, quando serve.

Nessuna migrazione dati. Nessun cambio a Room o SQLite. Nessun cambio a repository, query, cache, ViewModel, routing o semantica dei filtri. La posizione visuale del carosello cambia solo quando il chip scelto non e' visibile. Le animazioni continue restano fuori ambito.

## Esito del double check

La navigazione conferma alcuni problemi. Smentisce altre ipotesi. La tabella registra decisioni e riscontri.

| Area | Riscontro | Decisione proposta |
| --- | --- | --- |
| Home, schede statistiche | Griglia 2x2 ripetuta. Quattro card precedono il grafico. La griglia occupa spazio verticale. | Provare padding e spazi piu stretti. Conservare leggibilita di valori e label. |
| Home, badge Lite | Viola molto visibile. Usa `tertiaryContainer`, token condiviso. | Ridurre enfasi in `DashboardHeader`. Non cambiare il colore globale. |
| Navigazione inferiore | Lavanda nel tema chiaro. Nessun container esplicito nel componente. | Provare `surface` solo su `NavigationBar`. Conservare selezione verde e ombra. |
| Nuvola di parole | Colori legati all'indice. La dimensione indica gia la frequenza. | Proposta neutra ritirata su richiesta. Conservare lo stile originale. |
| Scheda attivita | La selezione si notava poco. Tutte le label erano forzate a 12 sp. | Usare 14 sp e peso 600 per la selezionata. Usare 12 sp e peso 400 per le altre. |
| Intervallo date | Il chiamante usa gia `isSmallText`. Le date scorrono nella riga. | Proposta su righe separate ritirata su richiesta. Conservare `AutoMarquee` originale. |
| Filtri Radar | Controlli leggibili. Tutti entrano nello schermo. La densita ipotizzata non si conferma. | Non ridisegnare i controlli. Controllare solo l'indizio di scorrimento. |
| Chip personalizzato | Chip in fondo al `LazyRow`. L'auto-scroll segue la selezione. Il bordo lo taglia talvolta. | Fade da 24 dp su ogni bordo con contenuto scorribile. |
| Skeleton distribuzione | Placeholder grafico da 180 dp. Mancano titolo e spazio. Skeleton circa 248 dp; card reale circa 196 dp. Grafico orario: 68 dp. | Riprodurre tab da 48 dp, titolo, spazio e grafico da 68 dp. |
| Transizione filtri Home | Nel video Samsung il carosello si riallinea anche se il chip e' gia visibile. Durante il refresh l'intero contenuto caricato sparisce per mostrare lo skeleton. Il grafico orario skeleton da 68 dp corrisponde a quello reale. | Conservare la posizione del carosello. Durante il refresh, sovrapporre skeleton alle card dati senza smontare la lista. |
| Media e Link | Chip, conteggi e colori distinguono categorie. La galleria mostra contenuti leggibili. | Conservare struttura e colori semantici. |
| Grafico mittenti | Rosso e blu distinguono partecipanti. | Conservare i colori attuali. |
| Elenco chat | Una chat lascia spazio libero. La riga resta chiara. Il campione e limitato. | Nessuna modifica ora. Ricontrollare con piu chat. |
| Highlights e badge | Schede riconoscibili. Contenuti leggibili. | Conservare layout e trattamento. |
| Nuvola emoji | Emoji rappresentano contenuti reali. | Conservare contenuti e stile. |

## Limiti dell'audit

Audit dal vivo: tema chiaro. Dati: una chat Lite. Tema scuro non controllato. Nomi lunghi non controllati. Elenco multi-chat non controllato.

Misure proposte: indicative. Provarle sul dispositivo. Verificare tutte le lingue. Le stringhe visibili restano in `strings.xml`.

## Piano di sviluppo

Procedere per passaggi piccoli. Confrontare ogni schermata prima e dopo.

### Passo 1 - Colori e gerarchia, attuato in parte

- Mantenere i colori originali della word cloud.
- Evidenziare tab selezionata con dimensione e peso.
- Attenuare tab inattive.
- Impostare `surface` solo su `NavigationBar`.
- Ridurre localmente enfasi del badge Lite.
- Aggiornare badge in tutte le 13 lingue.

### Passo 2 - Intervallo date, ripristinato

- Conservare layout originale su una riga.
- Conservare `AutoMarquee` originale.

### Passo 3 - Densita delle card, completato

- Provare padding da 16 dp a 12 dp.
- Provare icona da 24 dp a 18 dp.
- Provare spazio da 10 dp a 8 dp.
- Applicare misure coerenti agli skeleton.
- Verificare valori lunghi sul device.
- Allineare i due skeleton.
- Condividere padding e spaziature tra `StatCard` e skeleton.
- Misurare i placeholder testuali con un `Text` trasparente e lo stesso `TextStyle` della card.
- Distribuzione oraria: tab 48 dp, titolo 16 dp, gap 16 dp, grafico 68 dp.

### Passo 4 - Indizio carosello, completato

- Mostrare fade sui bordi scorrevoli.
- Conservare la posizione se il chip scelto e' gia visibile.
- Animare lo scroll solo quando il chip scelto e' fuori viewport.

### Passo 5 - Transizione filtri Home, completato

- In `TimeRangeFilterRow`, non riallineare il carosello quando il chip scelto e' gia visibile.
- Animare il carosello quando il chip scelto non e' visibile per intero.
- In `DashboardScreen`, mantenere montata la lista durante il refresh di un filtro.
- Sovrapporre skeleton a griglia statistiche e scheda attivita, senza cambiarne la geometria.
- Mostrare `DashboardSkeletonContent` completo al caricamento iniziale, quando `globalStats` e' `null`.
- Conservare la progress bar. Non aggiungere ritardi artificiali.

## File modificati

- `app/src/main/java/com/whatstats/ui/component/ActivityCard.kt`
- `app/src/main/java/com/whatstats/ui/component/TimeRangeFilterRow.kt`
- `app/src/main/java/com/whatstats/ui/component/StatCard.kt`
- `app/src/main/java/com/whatstats/ui/component/ShimmerUtils.kt`
- `app/src/main/java/com/whatstats/ui/navigation/BottomNavigationBar.kt`
- `app/src/main/java/com/whatstats/ui/screen/dashboard/DashboardHeader.kt`
- `app/src/main/java/com/whatstats/ui/screen/dashboard/DashboardScreen.kt`
- `app/src/main/java/com/whatstats/ui/screen/dashboard/DashboardSkeleton.kt`
- `app/src/main/java/com/whatstats/ui/screen/chatdetail/ChatDetailSkeleton.kt`
- `app/src/main/res/values*/strings.xml`, testo badge aggiornato

Non cambiare `Color.kt`. Il token terziario ha altri usi. Non cambiare il tema globale per questi ritocchi locali.

## Criteri di accettazione

- Word cloud con stile originale.
- Scheda attiva piu evidente.
- Badge Lite meno dominante.
- Barra inferiore coerente col tema.
- Intervallo date con `AutoMarquee` originale.
- Card piu compatte, se ancora leggibili.
- Skeleton coerenti con card caricate.
- Skeleton distribuzione allineato al grafico reale.
- Radar e Media senza regressioni.
- Highlights e badge senza regressioni.
- Selezione periodi invariata.
- Tutte le stringhe localizzate.

## Verifica eseguita

- `./gradlew ktlintCheck`: superato.
- Compilazione post-ripristino: superata.
- Build Debug installata su SM_A366B.
- Tab leggibili in screenshot Home.
- Fade verificato a sinistra e destra.
- Skeleton distribuzione misurato nel codice.
- Skeleton stat allineati alla tipografia.
- Padding e spaziature skeleton condivisi con `StatCard`.
- Screenshot Redmi: l'overlay comprimeva la seconda riga con altezze placeholder manuali.
- Placeholder testuali misurati con lo stesso `TextStyle` delle card; righe allineate nel frame dopo la correzione.
- Registrazione Samsung analizzata: scatti causati dal riallineamento del carosello e dal rimpiazzo completo durante il refresh.
- Refresh filtri: skeleton sovrapposto alle card dati; lista e posizione conservate.
- Chip visibile: posizione del carosello mantenuta. Chip fuori viewport: scroll animato.
- Word cloud e date ripristinate su richiesta.
- Durata del caricamento lasciata invariata. Nessun ritardo artificiale.
- Dati della chat invariati.
- `./gradlew ktlintCheck :app:compileDebugKotlin :app:compileReleaseKotlin`: superato.
- Build Debug aggiornata installata e avviata su SM_A366B.
- Build Debug aggiornata installata e avviata su Redmi Note 9 Pro.

## Verifiche residue

- Controllare il tema scuro.
- Controllare piu chat e nomi lunghi.
- Controllare le traduzioni su device.
- Controllare altri schermi e densita.

## Fuori ambito

- Fermare o ridurre animazioni continue.
- Cambiare dati, query, cache o logica di navigazione.
- Cambiare logica dei filtri.
- Ridisegnare Media, Radar o galleria.
- Ridisegnare Highlights o badge.
- Dedurre problemi da una chat sola.
