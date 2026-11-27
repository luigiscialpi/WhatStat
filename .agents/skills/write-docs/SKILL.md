---
name: write-docs
description: Scrive o riorganizza documentazione tecnica interna in italiano nello stile usato dal team (Bennet e progetti collegati) — documenti di processo (git flow, release, build), standard/linee guida di codice (refactor, convenzioni), o design di feature (analisi tecnica per una ticket Jira). Usa questa skill ogni volta che l'utente chiede di scrivere, riscrivere, migrare su Confluence, rendere "più discorsiva" o "meno copia-incolla" una documentazione tecnica, di descrivere un piano di sviluppo/refactor, o di documentare l'analisi/implementazione di una feature con branch, step o checklist. Applicala anche quando l'utente allega un documento esistente come "esempio di stile" o "template" da seguire per uno nuovo, e quando chiede un testo più chiaro, più semplice o meno "da IA" per un documento tecnico del team.
---

# Bennet Tech Docs

Skill per scrivere documentazione tecnica interna coerente con lo stile osservato nei documenti del team (processi di repository/release, standard di refactor, design di feature). Copre sia la scrittura ex-novo sia la riorganizzazione/migrazione di documenti esistenti (es. verso Confluence).

## Step 1 — Riconoscere la famiglia del documento

Prima di scrivere, capire a quale delle quattro famiglie appartiene (chiedere all'utente se non è chiaro dal contesto — una sola domanda, con le opzioni sotto):

| Famiglia | Quando si usa | Segnali tipici nella richiesta |
|---|---|---|
| **1. Processo** | Regole operative stabili e ripetibili (git flow, release, build/deploy, branching) | "come gestiamo i branch", "processo di rilascio", "guida alla build" |
| **2. Standard di codice** | Linee guida normative da applicare ripetutamente sulla codebase (refactor, convenzioni architetturali) | "linee guida di refactor", "come strutturare i componenti", "standard per..." |
| **3a. Design di feature — leggero** | Analisi/proposta tecnica non ancora implementata, taglio divulgativo | "analisi di fattibilità", "documento non tecnico", niente branch/checklist previsti |
| **3b. Design di feature — standard** | Analisi + piano di implementazione per una feature legata a un ticket, un solo sviluppatore | link a ticket Jira, richiesta di "rationale e implementazione", branch singoli |
| **3c. Design di feature — multi-team** | Come 3b ma su feature grande, più sviluppatori in parallelo, richiede coordinamento | più nomi di persone, step numerati con dipendenze, esigenza di tracciare "chi fa cosa" |

Leggere il reference corrispondente in `references/` **prima di scrivere**, non affidarsi solo a questa tabella:

- `references/1-processo.md`
- `references/2-standard-codice.md`
- `references/3a-feature-leggero.md`
- `references/3b-feature-standard.md`
- `references/3c-feature-multiteam.md`

Ogni reference contiene: struttura tipica delle sezioni, convenzioni di stile specifiche della famiglia, ed esempi minimi.

## Step 2 — Convenzioni trasversali (valgono per tutte e 4 le famiglie)

- **Lingua**: italiano, salvo termini tecnici standard (branch, commit, hook, endpoint...) che restano in inglese.
- **Apertura con lo scopo**: iniziare quasi sempre con un paragrafo che dichiara *perché* il documento esiste, prima del *cosa*. Formula tipica: "Lo scopo di questo documento è...", "L'obiettivo è...".
- **Registro impersonale**, mai in prima persona singolare. La prima persona plurale ("possiamo", "potremmo") è ammessa solo nelle sezioni di proposta e analisi e nel DRAFT della famiglia 3b e 3c; in tutte le altre sezioni e famiglie resta vietata. Le istruzioni operative usano l'**infinito** ("Individuare...", "Creare...", "Validare...") o il presente indicativo descrittivo, non l'imperativo alla seconda persona ("Devi fare...").
- **Contesto prima della lista**: ogni bullet/numerata è preceduta da almeno una frase che spiega perché quella lista esiste. Mai un elenco "nudo" senza introduzione.
- **`Nota:`** in linea o come blocco a parte per precisazioni/eccezioni che non meritano un intero paragrafo ma vanno isolate visivamente.
- **Esempi concreti** introdotti con "Esempio:" quando un concetto è astratto — meglio ancorare a un caso reale del progetto (nome file, nome variabile) che a un caso generico.
- **Percorsi di file reali**, citati per esteso (`src/app/common/...`), quando il documento riguarda codice esistente — mai genericizzati.
- **Grassetto** per entità/termini chiave (nomi di file, concetti cardine); `code` per nomi tecnici, variabili, comandi, path; corsivo usato con parsimonia.
- **Naming convention esplicite** per branch/commit quando pertinente (`RQM-XXXX/...`, `feature/nome-scopo`), sempre con almeno un esempio concreto accanto alla regola astratta.
- **Sezioni "vive"**: se qualcosa non è ancora deciso o manca, dichiararlo esplicitamente (`TO DO`, `DRAFT`, `Da approfondire`) invece di ometterlo o fingere completezza. Non nascondere le parti incomplete.
- **Cross-reference interni** invece di ripetere un concetto già spiegato altrove nel documento ("come descritto in...", "secondo la regola di...").
- **Un nome per ogni cosa**: lo stesso componente, servizio o concetto si chiama sempre allo stesso modo in tutto il documento. Variare i sinonimi per eleganza fa pensare a oggetti diversi. Dettagli e altri controlli in `references/4-scrittura-chiara.md` (vedi Step 4).

## Step 3 — Riorganizzare un documento esistente (es. migrazione su Confluence)

Quando l'utente fornisce un documento già scritto (spesso con paragrafi frammentati, interruzioni di riga "alla Word", liste senza contesto) e chiede di renderlo "più discorsivo" o pronto per una piattaforma come Confluence:

1. Identificare la famiglia (Step 1) e leggere il reference pertinente.
2. Riorganizzare in sezioni numerate coerenti con la famiglia (vedi reference).
3. Anteporre un paragrafo introduttivo discorsivo a ogni lista tecnica esistente nel documento originale, se manca.
4. Mantenere intatti dati tecnici, comandi, path, schemi — riformattare solo la prosa attorno, non i contenuti tecnici.
5. Consolidare un indice iniziale che rispecchi *tutte* le sezioni effettivamente presenti — ricontrollarlo a fine scrittura, è un errore facile da fare.
6. Se il documento originale ha un elenco introduttivo di sezioni con descrizione (pattern della famiglia Processo), verificare che sia completo prima di consegnare.

## Step 4 — Rilettura con le regole di scrittura chiara

A documento scritto o riorganizzato, rileggerlo una volta con `references/4-scrittura-chiara.md` prima di consegnarlo. Il reference fa tre cose:

- controlla ambiguità e coerenza dei termini (pronomi con doppio referente, gerundi poco chiari, "si" senza soggetto);
- toglie le ripetizioni accidentali (lo stesso concetto spiegato due volte con parole diverse) e lascia quelle strutturali della famiglia, come i tre livelli di 3b;
- toglie i segni tipici di un testo generato da IA (emoji, formule vuote, grassetti sparsi, antitesi di prammatica);
- indica in quali sezioni applicare le regole in modo **rigoroso** (Regole, step di Implementazione, FASE, comandi, checklist) e in quali in modo **leggero** (Descrizione, Analisi, DRAFT, Rationale narrativo), per non spezzettare la prosa discorsiva che questa skill vuole ottenere.

Le convenzioni di questa skill (registro, infinito per le istruzioni, grassetto sui termini chiave, struttura delle famiglie) vincono sempre sulle regole del reference. Il reference non cambia la struttura e non tocca i contenuti tecnici.

## Step 5 — Formato di output

- Markdown (`.md`) è il formato di default e funziona bene se poi va incollato in Confluence (il paste di Markdown viene spesso riconosciuto e convertito automaticamente).
- Se l'utente chiede esplicitamente HTML "pronto per Confluence", ricordare che il copia-incolla di codice HTML grezzo può non essere affidabile in tutti gli editor Confluence — il Markdown è generalmente la scelta più sicura, ma procedere comunque se richiesto esplicitamente.
- Creare sempre il file con `create_file` in `/mnt/user-data/outputs/` e presentarlo con `present_files` — non limitarsi a mostrare il contenuto in chat per documenti lunghi (>~50 righe).
