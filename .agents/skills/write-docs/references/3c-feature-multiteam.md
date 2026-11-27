# Famiglia 3c — Design di feature: variante multi-team/complessa

Esempio osservato: integrazione Buoni SAVI (galleria, wallet, offline, analytics), più sviluppatori in parallelo.

## Cosa la caratterizza

Stessa spina dorsale della 3b (Rationale/decisioni → Implementazione per branch → checklist), ma scalata a una feature grande con **più persone che lavorano in parallelo** e quindi bisogno di coordinamento esplicito. Usare questa variante quando il documento deve anche fare da project plan, non solo da specifica tecnica.

## Elementi aggiuntivi rispetto a 3b

1. **Blocco link in testa**, prima ancora del titolo: Figma, tool esterni, documenti collegati (doc backend, gestione asset). Compare solo quando il progetto ha davvero più fonti esterne da coordinare.
2. **Indice profondo e integrale**, che rispecchia 1:1 tutta la gerarchia dei titoli — necessario quando il documento è lungo e con molti sviluppatori che devono trovare rapidamente "la propria" sezione.
3. **Attribuzione del proprietario accanto ai titoli di sezione**: `(Nome)` — usare quando più persone contribuiscono a parti diverse dello stesso documento/feature.
4. **Tag di stato sugli step**: `(FATTO)`, `(Da fare)`, `(CHI?)` — ibrido tra piano e tracker, utile quando gli step sono a carico di persone diverse e non basta la sola checklist `[x]/[ ]` interna a un singolo step.
5. **Sezione "Decisioni Architetturali"** separata da **"Decisioni implementative prese durante gli sviluppi"**: la prima raccoglie le scelte fatte *a priori* in fase di design (es. provider centralizzato, stato Redux dedicato, flag di feature), la seconda quelle emerse *durante* lo sviluppo (es. refactor di un componente legacy deciso in corsa). Tenerle sempre distinte, non fonderle.
6. **Diagramma ASCII di dipendenze tra step/persone**, per mostrare cosa è parallelizzabile e cosa è bloccante — utile solo quando c'è davvero concorrenza tra sviluppatori.
7. **Tabelle comparative estese** per specifiche di business (es. differenze tra due entità simili, matrice stato→comportamento UI) — stesso principio del trade-off di 3a/3b ma applicato a specifiche funzionali anziché a scelte tecniche.
8. **Snippet CORRETTO/ERRATO affiancati** nel codice di esempio — più prescrittivo di un semplice esempio, utile quando serve allineare più sviluppatori sullo stesso pattern.
9. **Appendice finale strutturata**: tabelle di riferimento (campi API, endpoint), linee guida di testing (spesso TDD), e talvolta un vero "pattern di sviluppo componenti" con struttura di cartelle standard — il documento si chiude anche come manuale di stile riutilizzabile per il team, non solo come piano per questa feature.

## Struttura complessiva

```
[link esterni: Figma, doc BE, tool]

Indice profondo (tutti i livelli di titolo)

Scopo
Decisioni Architetturali
Decisioni implementative prese durante gli sviluppi

Specifiche Funzionali e Business
  [tabelle comparative/matrici stato→UI]

Cosa bisogna aggiungere (Cosa)
  Schermate Nuove
  Schermate da modificare
  Componenti
    [uno per componente, con proprietà/stati/comportamento in tabella]

Piano di Implementazione (COME)
  [diagramma ASCII di dipendenze tra step/persone]
  Step 0.A - ... (Persona)
  Step 0.B - ... (Persona)
  Step 1.A / 1.B - ... (in parallelo, Persone diverse)
  Step N - ... (FATTO / Da fare / CHI?)
    - [ ] task
    - [x] task

Appendice
  Tabella campi API
  Endpoint API
  Linee guida testing
  Pattern di sviluppo componenti (struttura cartelle standard)
```

## Convenzioni di stile specifiche

- Non introdurre `(Nome)` o tag di stato se il documento ha un solo autore/esecutore — sono segnali di coordinamento multi-persona, altrimenti sono rumore (usare la 3b in quel caso).
- Il diagramma di dipendenze va inserito solo se c'è vero parallelismo da comunicare, non come decorazione.
- L'appendice con pattern riutilizzabili (struttura cartelle, convenzioni test) va scritta pensando che sopravviverà alla feature specifica — è materiale di riferimento per il team, va tenuto abbastanza generale da restare valido anche dopo.
