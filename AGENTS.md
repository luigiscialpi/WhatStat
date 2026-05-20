# Agent Rules — WhatStat

## Stack
Android nativo, Kotlin, Jetpack Compose, SQLite (Room + query raw), Hilt, DataStore, Coroutines/Flow, Coil, kotlinx.serialization, Jetpack Navigation Compose.

## Architettura
- Pattern MVVM: Screen → ViewModel → Repository → DAO
- Routing Root vs Lite trasparente via `ActiveDatabaseProvider` — niente `if (isLite)` nei DAO
- Cache: `CacheManager` con DataStore Preferences, namespace separati `stats_cache_root` / `stats_cache_lite`
- Navigazione: 3 livelli di grafi annidati in `AppNavigation.kt`

## Regole Inderogabili
- Leggi `README.md` (indice) prima di qualsiasi task. Approfondisci solo le sezioni necessarie.
- Applica Divide et Impera. Mai blocchi monolitici.
- Mai auto-committare senza permesso esplicito. Commit in inglese.
- No emoji. ASCII only.
- Niente query pesanti sul main thread. Usa `StateFlow` e `CacheManager`.
- Rispetta sempre `ktlint` (`./gradlew ktlintCheck`).

## Quirk Critici del DB
- `sender_jid_row_id = 0` nelle chat 1-a-1. Niente JOIN dirette. Usa `from_me` + JOIN su `chat` → `jid`.
- L'utente corrente è `me@s.whatsapp.net`, mappato a "Tu" in `AnalyticsRepository`.
- Tutti i timestamp sono UTC. Conversione locale con `datetime(..., 'localtime')` in SQLite.
- Chat 1-a-1 vs gruppo: `jid.contains("@g.us")`.

## Stile Comunicazione
1. Caveman compression: elimina articoli, congiunzioni, avverbi inutili
2. Max 2-5 parole per frase. Un concetto per frase.
3. Forma attiva. "calcola valore" non "il valore viene calcolato"
4. Verbi d'azione: fai, crea, correggi, controlla, esegui, aggiungi
5. In dubbio: mantieni più informazioni anziché meno

## Non Comprimere Mai
- Blocchi di codice (sintassi e nomi variabili intatti)
- Messaggi di errore (verbatim)
- Percorsi file e URL (verbatim)
- Valori tecnici: nomi DAO, chiavi DataStore, route di navigazione, soglie numeriche