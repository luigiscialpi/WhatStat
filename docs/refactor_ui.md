# Refactor della navigazione — Brief per il dev

## Contesto

Ho analizzato la struttura UX/UI dell'app con una chat esterna (senza accesso alla codebase). Di seguito trovi le criticità identificate e la proposta di riprogettazione da implementare.

---

## Struttura attuale

L'app ha attualmente queste schermate principali:

| Schermata | Come si raggiunge |
|---|---|
| **Dashboard (Panoramica)** | Entry point dell'app |
| **Lista Chat (Analisi Chat)** | Pulsante in fondo alla Dashboard |
| **Dettaglio Chat** | Tap su una chat nella Lista Chat |
| **Media & Link** | Pulsante in fondo alla Dashboard |
| **Radar Parole** | Pulsante in fondo alla Dashboard |
| **Log Chiamate** | Pulsante in fondo alla Dashboard |

### Problemi identificati

1. **I 4 pulsanti in fondo alla Dashboard sembrano tab ma non lo sono.** Visivamente equivalenti, non comunicano la profondità di contenuto disponibile e si perdono.

2. **Il back dalla schermata Dettaglio Chat è sbagliato.** In fondo al Dettaglio Chat c'è un pulsante "Analisi Chat" per tornare alla Lista Chat — è contro-intuitivo perché sembra un'azione in avanti, non un ritorno indietro.

3. **Nessun indicatore di posizione.** L'utente non sa a che livello della gerarchia si trova.

4. **Media & Link, Radar Parole e Log Chiamate sono di fatto invisibili** — l'utente medio non li scopre facilmente.

---

## Proposta di riprogettazione

### Pattern: Bottom Navigation Bar permanente

Sostituire i 4 pulsanti con una **tab bar fissa** presente in ogni schermata di primo livello, con 5 voci:

```
[ Panoramica ]  [ Chat ]  [ Media ]  [ Radar ]  [ Chiamate ]
```

Ogni tab è una sezione autonoma con navigazione interna indipendente.

---

### Modifiche schermata per schermata

#### 1. Panoramica (Dashboard)
- **Rimuovere** i 4 pulsanti in fondo (sostituiti dalla tab bar)
- La schermata rimane invariata nel contenuto

#### 2. Chat (ex "Analisi Chat")
- Diventa la **seconda tab** della bottom bar
- La Lista Chat è la schermata root di questa tab
- Tap su una chat → push di Dettaglio Chat nello stack di navigazione
- **Rimuovere** il pulsante "Analisi Chat" in fondo al Dettaglio Chat
- Il ritorno avviene con la **freccia ← standard nell'header** (back nativo della piattaforma)

#### 3. Media & Link
- Diventa la **terza tab** della bottom bar — sezione autonoma di primo livello
- Valutare di esporre "Media" e "Link" come **due sotto-tab orizzontali** in cima alla schermata (anziché come bottoni per riga), così il contenuto è visibile subito senza tap aggiuntivi

#### 4. Radar Parole
- Diventa la **quarta tab** — nessun'altra modifica al contenuto

#### 5. Log Chiamate
- Diventa la **quinta tab** — nessun'altra modifica al contenuto

---

### Navigazione: stack per tab

Ogni tab ha il proprio stack di navigazione indipendente. Esempio per la tab Chat:

```
Tab "Chat"
└── Lista Chat        ← root dello stack
    └── Dettaglio Chat  ← push nello stack, back con ←
```

Passare da una tab all'altra non distrugge lo stack delle altre tab (comportamento standard).

---

### Nice to have: scopribilità al primo avvio

Per guidare l'utente alla prima apertura, considerare un **tooltip o badge** sulle tab meno ovvie (es. Radar, Media) che sparisce dopo il primo accesso. Bassa priorità, ma utile dato che il target non è ancora definito.

---

## Riepilogo delle modifiche richieste

| Cosa | Azione |
|---|---|
| 4 pulsanti in fondo alla Dashboard | Rimuovere |
| Pulsante "Analisi Chat" in fondo al Dettaglio Chat | Rimuovere |
| Bottom Navigation Bar con 5 tab | Aggiungere (globale) |
| Stack di navigazione per tab Chat | Implementare |
| Sotto-tab Media/Link nella schermata Media & Link | Valutare |