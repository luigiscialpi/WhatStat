# Famiglia 2 — Standard di codice / linee guida

Esempio osservato: documento di refactor architetturale (standardizzazione, modularizzazione, DRY, separazione tracking/core/UI).

## Cosa li caratterizza

Sono **normativi**: definiscono regole da applicare ripetutamente su tutta la codebase, non su un singolo intervento. A differenza dei documenti di processo (famiglia 1), qui l'oggetto non è "come si rilascia" ma "come deve essere strutturato il codice". A differenza del design di feature (famiglia 3), non partono da un ticket/ambito specifico ma da un principio generale da applicare ovunque serva.

## Struttura tipica — framework PERCHÉ / COME / QUANDO / DOVE

Pattern distintivo di questa famiglia: le macro-sezioni sono etichettate esplicitamente con la domanda a cui rispondono, invece di un indice concettuale generico.

1. **Scopo/descrizione (PERCHÈ)**: criticità individuate + obiettivo del refactor/standard.
2. **Linee guida operative (COME/COSA)**: i principi generali (es. Standardizzazione, Modularizzazione, DRY), ciascuno spiegato con: definizione → regola → esempio concreto → eventuale doc esterna di riferimento.
3. **Implementazione**: le linee guida tradotte in **fasi sequenziali numerate** (FASE 0, FASE 1, FASE 2...), tipiche di un runbook eseguibile passo-passo. Ogni fase ha un obiettivo dichiarato in apertura.
4. **Sezione "Regole"** separata dalla parte descrittiva: mini-contratti vincolanti, elencati come lista secca, senza il preambolo narrativo che invece introduce le altre sezioni.
5. **Criteri di analisi (QUANDO)**: come decidere se/quanto applicare le linee guida a un caso specifico (es. dimensione del file, complessità dei flussi).
6. **Applicazione sulla codebase (DOVE)**: casi reali della codebase a cui applicare i principi, con **percorsi di file citati per esteso** e una breve diagnosi per ciascuno (perché è un problema, come intervenire).
7. **TO DO aperto**: elenco esplicito di aree ancora da analizzare/normare — segnala che il documento è "vivo", non definitivo.
8. **Implementazione finale per branch**: ogni fase del refactor mappata su una naming convention di branch (`RQM-XXX/standardize/tracking`, `RQM-XXX/modularization/dry-<ambito>`).

## Convenzioni di stile specifiche

- Ogni fase operativa dichiara il proprio obiettivo in una riga prima dei passi numerati.
- Le regole ("Regole") usano frasi brevi, dichiarative, senza narrazione — sono un contratto, non una spiegazione.
- Gli esempi di codice (se presenti) mostrano **corretto/errato affiancati**, non solo il pattern giusto isolato.
- Quando si descrive un file/flusso problematico reale, la diagnosi segue sempre questo schema: descrizione del problema → causa/motivo per cui è fragile → proposta di intervento numerata.
- Naming di branch sempre esplicito e con placeholder riconoscibile (`RQM-XXX/...`).

## Schema minimo riutilizzabile

```markdown
# Titolo

Scopo/descrizione (PERCHÈ)
[paragrafo di contesto: criticità osservate + obiettivo]

Linee guida operative (COME/COSA)
## Principio A
Definizione, regola, esempio.

## Principio B
...

Implementazione
FASE 0: [nome fase]
Obiettivo: ...
1. ...
2. ...

FASE 1: ...

Regole
1. ...
2. ...

Criteri di analisi (QUANDO)
[cosa determina se/quanto intervenire]

Applicazione sulla codebase (DOVE)
### File/flusso X
[diagnosi + proposta]

TO DO
- ...

Implementazione [per branch]
- nome-branch (RQM-XXX/...)
  [cosa include]
```
