# Famiglia 1 — Documenti di processo

Esempi osservati: gestione repository/branching e release, preparazione e distribuzione build.

## Cosa li caratterizza

Descrivono una procedura aziendale **stabile e ripetibile**, non legata a una feature o un ticket specifico. Tono da SOP (Standard Operating Procedure): niente ambiguità, niente parti "aperte" — è la via ufficiale, punto.

## Struttura tipica

1. **Titolo + paragrafo di scopo**: perché il documento esiste, quali problemi risolve, quali benefici porta il processo descritto.
2. **Elenco introduttivo delle sezioni**, ciascuna con una riga di descrizione (non solo il titolo nudo). Verificare sempre che l'elenco copra *tutte* le sezioni effettivamente presenti nel documento — errore facile da fare quando il documento cresce.
3. **Sezioni numerate progressive** (`## 1.`, `## 2.`...) via via più operative:
   - prima la struttura concettuale (es. i branch permanenti e il loro ruolo),
   - poi il ciclo di vita/flusso (apertura → stabilizzazione → chiusura),
   - poi le eccezioni,
   - poi i casi particolari/edge case.
4. **Comandi eseguibili** in blocchi di codice (`bash`), sempre con commenti che spiegano cosa fa ogni comando, non solo il comando nudo.
5. **Sezione finale di benefici/sintesi** (opzionale ma frequente): elenco puntato di "cosa si guadagna" adottando il processo.
6. **Schemi di riferimento** in appendice: diagrammi ASCII dei flussi principali, utili come promemoria visivo rapido — separati dal corpo narrativo, non intrecciati col testo.

## Convenzioni di stile specifiche

- Regole vincolanti espresse con verbi al presente indicativo o infinito ("Il branch deve rimanere...", "Non vengono introdotte nuove funzionalità...").
- Placeholder di naming sempre con esempio concreto accanto: `release/x.x.x` → poi mostrare `release/4.6.0`.
- Le eccezioni al processo standard vanno segnalate chiaramente come tali (`### Eccezione:` o `### Caso particolare:`), mai mescolate silenziosamente nel flusso principale.
- Se manca ancora una parte del processo (caso non normato), dichiararlo esplicitamente invece di ignorarlo — es. "Da aggiungere il caso in cui...".
- Passaggi "step by step" (es. procedura di build) numerati con verbo all'infinito o imperativo diretto rivolto all'operatore ("Eseguire il comando...", "Aprire Xcode...") — qui, a differenza del resto del documento, è accettabile un tono più da tutorial operativo diretto perché chi legge sta eseguendo passo-passo in tempo reale.

## Schema minimo riutilizzabile

```markdown
# Titolo

Paragrafo di scopo (perché esiste questo documento, quali problemi risolve).

La documentazione è divisa nelle seguenti sezioni:
- **Sezione A**: descrizione breve
- **Sezione B**: descrizione breve
...

## 1. Sezione A
### 1.1 Sotto-argomento
...

## 2. Sezione B
...

## N. Benefici del modello
- ...

## Schemi di riferimento
### Diagramma flusso principale
```
schema ASCII
```
```
