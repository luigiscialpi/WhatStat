# Famiglia 3b — Design di feature: variante standard

Esempio osservato: modalità offline (barcode Bennet Club + coupon), legata a un ticket Jira, sviluppo a carico di una singola persona.

## Cosa la caratterizza

Rispetto a 3a, qui il documento **converge in un piano esecutivo**: non solo analisi ma anche implementazione tradotta in branch e checklist spuntabile. È ancorato a un ticket Jira specifico (link in apertura), non a un principio generale.

## Struttura tipica

1. **Link al ticket Jira** subito in apertura, prima ancora dell'indice.
2. **Indice** (elenco titoli delle sezioni).
3. **Descrizione**: obiettivo della feature, cosa cambia per l'utente finale. Se pertinente, tabella comparativa di scenari (es. dati esistenti vs. dati non esistenti).
4. **Analisi**: fattori da considerare, con sotto-sezioni per ciascuno (storage, sicurezza, rilevamento di stato, ecc.). Alternative tecniche presentate con trade-off, come in 3a.
5. **Stato attuale**: comportamento oggi, problemi noti, raccomandazioni di massima.
6. **DRAFT: Ipotesi di funzionamento**: qui, a differenza di 3a, è ammesso e atteso un registro più colloquiale con blocchi `DOMANDE:` che riportano dubbi aperti quasi "a voce alta" — segno che il documento è ancora in fase di validazione con il team prima di passare all'esecutivo. Presentare alternative (es. transizione manuale vs. automatica) con pro/contro.
7. **Idee di sviluppo**: flusso proposto, passo dopo passo, ancora in forma descrittiva/discorsiva (non branch).
8. **Funzionalità da finalizzare**: dettaglio per singola funzionalità, con sotto-sezioni tipo "Come possiamo svilupparla" (verbo al condizionale — è ancora una proposta) e "Considerazioni aggiuntive".
9. **Sviluppo → Rationale**: qui il registro cambia, si passa al concreto — outline tecnico dettagliato con riferimenti a righe di codice reali (`riga 108`), nomi di metodi/file esistenti, organizzato per componente/area tecnica.
10. **Sviluppo → Implementazione**: lo stesso contenuto del Rationale riorganizzato per **branch** (`feature/RQM-XXXX-nome-scopo`), ciascuno con la propria lista di step.
11. **Stessa implementazione ripetuta come checklist spuntabile** (`- [x]` / `- [ ]`), branch per branch — il piano narrativo diventa tracker di avanzamento eseguibile.
12. **Snippet di codice reale** quando utile a illustrare un pattern (es. un componente React con `...` per gli omissis), più concreto dello pseudocodice.
13. **Elenco file impattati** in chiusura: tabella o lista file-per-file (nuovo/esistente + cosa cambia) — un "diff plan" testuale per stimare l'impatto prima di aprire le PR.

## Convenzioni di stile specifiche

- Verbo al condizionale ("Come possiamo svilupparla", "potremmo") nelle sezioni di proposta/analisi; verbo all'infinito/imperativo diretto solo nella sezione Implementazione finale.
- I blocchi `DOMANDE:` sono legittimi solo nella sezione DRAFT — non vanno sparsi altrove nel documento una volta che si passa a Rationale/Implementazione.
- La ripetizione Rationale → Implementazione-per-branch → Checklist non è ridondanza involontaria: sono tre livelli di dettaglio crescente (outline tecnico → piano per branch → tracker eseguibile) e vanno mantenuti distinti.

## Schema minimo riutilizzabile

```markdown
Task relativo: [link Jira]
- Descrizione
- Analisi
- Stato attuale
- DRAFT: Ipotesi di funzionamento
- Idee di sviluppo
- Funzionalità da finalizzare
- Sviluppo
  - Rationale
  - Implementazione

Descrizione
[obiettivo feature]

Analisi
[fattori da considerare, con trade-off]

Stato attuale
[comportamento oggi + problemi noti]

DRAFT: Ipotesi di funzionamento
DOMANDE:
- [dubbio aperto]

Idee di sviluppo
1. ...

Funzionalità da finalizzare
### Funzionalità X
Come possiamo svilupparla
[proposta al condizionale]

Sviluppo
Rationale
1. [area tecnica]
   1. [dettaglio con riferimento a file/riga]

Implementazione
1. feature/RQM-XXXX-nome
   1. [step]
   2. [step]

[stessa lista come checklist]
- [ ] [step]
- [ ] [step]

Elenco file impattati:
- path/file.tsx: [cosa cambia]
```
