# Famiglia 3a — Design di feature: variante leggera/esplicativa

Esempio osservato: gestione della modale cookie in WebView.

## Cosa la caratterizza

È la variante più "bassa" della famiglia design-di-feature: **analisi tecnica conclusa ma non ancora un piano esecutivo**. Registro dichiaratamente non tecnico — leggibile anche da chi non scrive codice. Nessuna sezione DRAFT/domande aperte (l'analisi è già chiusa), nessun branch, nessuna checklist: il documento si ferma prima della fase di implementazione pianificata.

Usare questa variante quando la richiesta è un'analisi/proposta da condividere e discutere, non ancora un piano di lavoro da eseguire.

## Struttura tipica

1. **Indice** semplice a inizio documento (elenco titoli, senza descrizioni — a differenza della famiglia Processo).
2. **Descrizione**: contesto del problema, in linguaggio accessibile anche a chi non è sviluppatore.
3. **Analisi**: elenco puntato dei fattori da considerare, poi approfonditi uno per uno in sotto-sezioni. Ogni sotto-sezione tecnica (es. scelta di una libreria/tecnologia) segue lo schema **Vantaggi / Limiti** esplicito, con link diretti a documentazione/pacchetti quando pertinente.
4. **Nota sull'AS IS**: come funziona oggi il sistema, cosa è già in campo (SDK, librerie, provider) — con una sotto-sezione dedicata *"Luoghi nel codice da consultare (per sviluppatori)"* che isola i riferimenti tecnici (file, funzioni) per chi vuole approfondire, separandoli dalla prosa generale.
5. **Linee di sviluppo**: proposta numerata di come implementare, a livello di flusso logico (non ancora branch/task).
6. **Nota tecnica**: eventuali implicazioni pratiche/wiring consigliato, per chi implementerà davvero.
7. **Test da effettuare**: scenari utente in linguaggio naturale ("Caso Accetta Tutti: premendo... l'app deve..."), non test tecnici/codice — coerente col taglio non tecnico dichiarato in apertura.

## Convenzioni di stile specifiche

- Dichiarare esplicitamente il taglio non tecnico se il documento è indirizzato anche a stakeholder non sviluppatori.
- Coppia Vantaggi/Limiti sempre esplicita quando si valuta una tecnologia o un approccio — mai una scelta presentata senza il suo trade-off.
- Riferimenti al codice esistente isolati in una sotto-sezione dedicata, non sparsi nel testo discorsivo.
- I test sono scenari d'uso in prosa (comportamento atteso), non specifiche di test automatizzati.

## Schema minimo riutilizzabile

```markdown
- Descrizione
- Analisi
- Nota sull'AS IS
- Linee di sviluppo
- Nota tecnica
- Test da effettuare

Descrizione
[contesto del problema, in linguaggio accessibile]

Analisi
- fattore 1
- fattore 2
### Fattore 1 (es. scelta tecnologica)
- Vantaggi: ...
- Limiti: ...

Nota sull'AS IS
[come funziona oggi]
Luoghi nel codice da consultare (per sviluppatori):
- file: ...

Linee di sviluppo
1. ...
2. ...

Nota tecnica
[wiring consigliato/implicazioni pratiche]

Test da effettuare
1. Caso X: [comportamento atteso in prosa]
```
