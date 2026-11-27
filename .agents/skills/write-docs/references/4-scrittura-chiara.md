# Scrittura chiara — rilettura finale (tutte le famiglie)

Questo reference si applica **dopo** aver scritto o riorganizzato il documento, come ultima rilettura. Ha due scopi: togliere ambiguità e togliere i segni tipici di un testo generato da IA.

Nasce dai principi di ASD-STE100, lo standard di inglese tecnico semplificato. STE esiste solo per l'inglese: qui si tengono i principi (un termine, un significato; una frase, una struttura; una cosa per volta) e si usano regole fatte per l'italiano. Non dichiarare mai un documento "conforme a STE".

## Regola madre

Cambiare le parole, non la sostanza. Non togliere fatti, comandi, path, nomi di branch, numeri di ticket, avvertenze o motivi. Conservare la forza di ogni affermazione: "dovrebbe" non diventa "deve", "di solito" non diventa "sempre".

Se un punto è ambiguo e il contesto non lo risolve, non indovinare. Segnalarlo nel documento con `Da approfondire` (convenzione del team) oppure, se serve una risposta dell'utente, con una riga alla fine del messaggio.

## Rigoroso o leggero: dipende dalla sezione

Le regole di lunghezza non valgono ovunque. Questa skill vuole prosa discorsiva nelle parti descrittive, e frasi di venti parole la spezzetterebbero.

| Modalità | Sezioni | Che cosa si applica |
|---|---|---|
| **Rigorosa** | `Regole`, `FASE` e step di `Implementazione`, comandi e procedure step-by-step, checklist, avvertenze, `Test da effettuare` | Tutte le regole sotto, comprese le soglie di lunghezza |
| **Leggera** | Scopo, Descrizione, Analisi, Stato attuale, DRAFT e `DOMANDE:`, Idee di sviluppo, Funzionalità da finalizzare, Rationale narrativo, Nota sull'AS IS | Solo termini coerenti, ambiguità, pulizia anti-IA. Nessun limite di parole e nessuno spezzettamento forzato |

Nelle sezioni leggere una frase lunga va bene se si legge senza fatica. Accorciarla solo quando contiene due idee separate o un pronome ambiguo.

## Regole per le sezioni rigorose

- **Lunghezza.** Massimo 20 parole per frase nelle procedure e 25 nelle descrizioni. Sono soglie di lavoro: se una frase sotto soglia si legge male, accorciarla comunque.
- **Un'istruzione per frase.** Due azioni diventano due frasi o due passi numerati.
- **Condizione prima dell'azione.** "Se il file manca, crearlo." Non: "Creare il file, se manca."
- **Soggetto esplicito, voce attiva.** "Il servizio invia la richiesta", non "La richiesta viene inviata".
- **Una sola subordinata.** Dopo la prima, aprire una nuova frase.
- **Obbligo, permesso, possibilità** con parole diverse e costanti: "deve" (obbligo), "non deve" (divieto), "può" (permesso), "è possibile che" (possibilità), "non serve" (non necessario).
- **Avvertenze** prima del passo a cui si riferiscono, con una frase che dice che cosa succede se si sbaglia.

## Regole per tutte le sezioni

**Un nome per ogni cosa.** Scegliere un termine per componente, servizio o concetto e usarlo sempre. Se è "worker" a inizio documento, non diventa "processo" o "servizio" più avanti. Controllare anche che l'indice usi gli stessi nomi dei titoli.

**Ogni concetto una volta sola.** Spiegare un concetto nel punto in cui serve e altrove rimandare ("come descritto in…"). Se due paragrafi dicono la stessa cosa con parole diverse, tenerne uno e trasformare l'altro in un rimando. Il caso tipico nei testi generati da IA è lo stesso concetto riformulato nello Scopo, nella Descrizione e nell'ultima sezione.

Restano ammesse le ripetizioni **strutturali** previste dalla famiglia: i tre livelli di 3b (Rationale, Implementazione per branch, Checklist), l'indice, l'elenco introduttivo delle sezioni nella famiglia Processo, il blocco di benefici finale. Non sono ridondanza, e non vanno fusi. Prima di togliere una ripetizione, controllare che non sia una di queste.

**Pronomi (lo, la, li, ne, ci, gli).** Se il pronome può riferirsi a due cose, ripetere il nome. È l'ambiguità più frequente nei documenti tecnici in italiano.

**Gerundio.** Spesso nasconde se indica causa, tempo o modo. "Premendo il pulsante, il sistema si riavvia" può significare "quando" o "perché". Scrivere il legame: "Quando si preme il pulsante, il sistema si riavvia."

**"Si" passivante senza soggetto.** "Si configura il modulo" non dice chi. Nelle istruzioni usare l'infinito del team ("Configurare il modulo"). Nelle descrizioni usare un soggetto: "Il sistema configura il modulo".

**Verbi precisi al posto di verbi vuoti.** Evitare "effettuare", "procedere a", "dare luogo a" e "gestire" quando nascondono un verbo più preciso: "effettuare una verifica" diventa "verificare", "gestire l'errore" diventa il verbo vero (registrare, mostrare, riprovare).

**Burocratese.** "al fine di" → "per"; "qualora", "nel caso in cui" → "se"; "ove" → "se" o "dove"; "altresì" → "anche"; "pertanto" → "quindi"; "mediante", "tramite" → "con"; "utilizzare" → "usare"; "a livello di" → spesso si toglie.

**Doppie negazioni** → affermazione diretta.

**Termini tecnici e anglicismi** (branch, commit, hook, endpoint) restano com'è consuetudine del team. Non tradurli e non italianizzare gli identificatori.

## Forma e registro (convenzioni del team, non cambiare)

- Istruzioni all'**infinito**, mai all'imperativo in "tu". Tenere la stessa forma in tutto il documento.
- **Condizionale** solo nelle sezioni di proposta e analisi (3b: "Come possiamo svilupparla", "potremmo"). Non usarlo per attenuare un fatto già deciso.
- **Prima persona plurale** solo dove la skill la ammette (proposta e DRAFT di 3b/3c). Altrove, registro impersonale.
- **Passato remoto e futuro epistemico** ("sarà il file corrotto"): evitarli. Descrizioni al presente, eventi passati al passato prossimo.

## Pulizia dello stile IA

Cinque passaggi. Ognuno toglie solo ciò che è decorativo e non porta fatti.

1. **Emoji e simboli decorativi** nei titoli, negli elenchi e nelle frasi di apertura o chiusura. Restano solo i simboli che il team usa come convenzione (per esempio `- [x]` e `- [ ]` nelle checklist).
2. **Formule vuote.** "È importante sottolineare che…", "Va detto che…", "In questo documento vedremo…", "Scopriamo insieme…", "Nel panorama attuale…", e conclusioni che ripetono l'introduzione. Eccezione: "Lo scopo di questo documento è…" resta, perché è la formula del team. Deve però dichiarare uno scopo concreto, non uno generico come "migliorare la qualità".
3. **Parole gonfie.** "Viaggio", "panorama", "cuore", "pilastro", "sbloccare il potenziale", "sfruttare", "potenziare", "fondamentale", "cruciale", "robusto", "potente", "olistico", "all'avanguardia". Restano "robusto" o "scalabile" solo se descrivono una proprietà vera e misurabile.
4. **Strutture ripetitive.** Antitesi di prammatica ("Non è solo X, ma anche Y"), terzine forzate quando servono due esempi, trattini lunghi (—) al posto di punto o virgola, connettivi a ogni frase ("Inoltre", "Tuttavia", "Pertanto"), titoli che annunciano ("Perché è importante?").
5. **Enfasi.** Il grassetto resta dove la skill lo prevede: termini chiave alla prima occorrenza e nomi di file. Togliere quello sparso per dare enfasi. Togliere intensificatori ("estremamente", "davvero") e cortesie rivolte al lettore.

Un elenco o una tabella richiesti dalla struttura della famiglia non sono "stile IA": è il formato del team.

## Quando l'utente chiede un testo "umano"

A volte l'utente chiede qualcosa come: *scrivi un testo umano, che non sembri generato da un'AI, con tono conversazionale, caldo e naturale, frasi brevi, linguaggio semplice.* Interpretarlo così:

- **Sì a:** frasi brevi, parole semplici, testo naturale e diretto, niente stile IA (passaggi 1-5 sopra).
- **Non a:** tono caldo o colloquiale in tutto il documento. Il registro delle famiglie (impersonale, da SOP o da analisi) resta quello del team. Il "calore" produce proprio i tic che si vogliono togliere: saluti, empatia di circostanza, esclamazioni, domande retoriche, "Ecco…".
- **Eccezione:** nel DRAFT e nei blocchi `DOMANDE:` di 3b e 3c il registro colloquiale è già ammesso. Lì un tono più parlato va bene, ma senza cortesie.
- Se l'utente chiede esplicitamente un tono colloquiale per **tutto** il documento, seguire la richiesta e tenere le regole di chiarezza e la pulizia anti-IA. Dirlo in una riga: il documento si discosta dal registro del team.

## Esempi

**Sezione rigorosa (`Regole`, infinito, forza conservata)**

Prima:
> Qualora un file raccolga più responsabilità, è opportuno procedere alla sua suddivisione in moduli distinti, tenendo presente che la logica di tracking dovrebbe essere mantenuta separata da quella di presentazione.

Dopo:
> Se un file raccoglie più responsabilità, è opportuno suddividerlo in moduli distinti. La logica di tracking dovrebbe restare separata dalla logica di presentazione.

"È opportuno" e "dovrebbe" restano: sono raccomandazioni, non obblighi.

**Sezione leggera (Descrizione, prosa discorsiva)**

Prima:
> L'applicazione, che in assenza di connessione deve comunque consentire all'utente di mostrare il barcode, effettua il salvataggio dei dati necessari all'avvio, i quali vengono poi recuperati dallo storage locale qualora la rete non sia disponibile, permettendo così un'esperienza sostanzialmente fluida.

Dopo:
> Senza connessione, l'app deve comunque mostrare il barcode all'utente. Per questo salva in anticipo i dati necessari all'avvio e, se la rete non è disponibile, li legge dallo storage locale.

Il paragrafo resta discorsivo. Tolto solo ciò che non porta contenuto ("un'esperienza sostanzialmente fluida") e risolto il pronome (`li` = i dati).

## Lista di controllo

- Ogni cosa ha un solo nome, e l'indice usa gli stessi nomi dei titoli.
- Nessun concetto è spiegato due volte con parole diverse. Le uniche ripetizioni sono quelle strutturali della famiglia.
- Nelle sezioni rigorose nessuna frase supera la soglia senza motivo e nessuna contiene due istruzioni.
- Nessun pronome può riferirsi a due cose e nessun gerundio nasconde causa, tempo o modo.
- Nessun fatto, comando, path, branch, ticket o avvertenza è sparito.
- La forza di ogni affermazione è uguale all'originale.
- Istruzioni sempre all'infinito, prima persona plurale solo dove ammessa.
- Nessuna emoji, nessuna formula vuota, nessun grassetto sparso che la struttura non richieda.
- Struttura, indice, `Nota:`, `TO DO`, `DRAFT`, `DOMANDE:` e snippet sono quelli della famiglia, intatti.
