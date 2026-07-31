# Firebase Operations Runbook (Analytics, Remote Config, Forced Update)

Data: 2026-06-09
Stato: operativo

## Indice

- [1. Obiettivo](#1-obiettivo)
- [2. Scope tecnico](#2-scope-tecnico)
- [3. Stato attuale progetto](#3-stato-attuale-progetto)
- [4. Architettura operativa](#4-architettura-operativa)
- [5. Telemetria: chiavi Remote Config attive](#5-telemetria-chiavi-remote-config-attive)
- [6. Precedenza regole telemetria](#6-precedenza-regole-telemetria)
- [7. Event taxonomy v1](#7-event-taxonomy-v1)
- [8. TTL Remote Config (importante)](#8-ttl-remote-config-importante)
- [9. Runbook kill switch tracking](#9-runbook-kill-switch-tracking)
- [10. Forced update runbook](#10-forced-update-runbook)
- [11. Remote warning runbook](#11-remote-warning-runbook)
- [12. Setup e rollout](#12-setup-e-rollout)
- [13. Privacy, policy, data safety](#13-privacy-policy-data-safety)
- [14. Rischi e mitigazioni](#14-rischi-e-mitigazioni)
- [15. Definition of Done](#15-definition-of-done)
- [16. Checklist rapida](#16-checklist-rapida)

## 1. Obiettivo

Aggiungere telemetria prodotto senza inviare contenuti chat, con controllo remoto forte:

- capire versione app usata
- capire dove utenti cliccano
- capire quali schermate usano di piu
- capire tempo speso per schermata
- poter forzare update quando necessario
- poter spegnere tracking globale in emergenza

Vincolo non negoziabile:

- nessun contenuto chat inviato a Firebase
- nessun JID, nome contatto, numero telefono, testo messaggi

## 2. Scope tecnico

In scope:

- Firebase Analytics
- Firebase Remote Config
- In-App Updates Google Play (flow IMMEDIATE)
- kill switch remoto tracking (globale + selettivo)
- guardrail anti-overquota piano Firebase
- event taxonomy stabile
- privacy/policy/data safety alignment

Out of scope:

- A/B test iniziale
- BigQuery model avanzati
- funnel complessi multi-step

## 3. Stato attuale progetto

Punti gia presenti:

- consenso UMP integrato in AdsRepository
- voce privacy ads in drawer
- routing centralizzato in AppNavigation
- versioning app in app/build.gradle.kts

File chiave:

- app/src/main/java/com/whatstats/MainActivity.kt
- app/src/main/java/com/whatstats/telemetry/TelemetryRepository.kt
- app/src/main/java/com/whatstats/telemetry/TelemetryRemoteConfig.kt
- app/src/main/java/com/whatstats/update/UpdateGate.kt
- app/src/main/java/com/whatstats/ui/navigation/AppNavigation.kt
- docs/firebase_remote_config_template.json

## 4. Architettura operativa

Scelta: approccio ibrido.

- decisione remota via Remote Config
- enforcement UX via In-App Update IMMEDIATE
- fallback Play Store quando update manager non disponibile

Vantaggi:

- cambi soglia update senza rilasciare APK
- UX nativa Play su update obbligatorio
- fallback robusto per edge case

## 5. Telemetria: chiavi Remote Config attive

Controlli telemetria con parametri singoli (scelta intenzionale per operativita console):

- tracking_enabled_global (bool)
- tracking_mode (string: full|minimal|off)
- tracking_allowed_events_csv (string)
- tracking_blocked_events_csv (string)
- tracking_sampling_percent (long 0-100)
- tracking_daily_event_cap_per_user (long)
- tracking_config_ttl_minutes (long)

Chiavi feature (JSON):

- force_update_config (JSON)
- remote_warning_config (JSON)

Nota storica utile:

- valutata migrazione a 3 JSON totali
- mantenuta telemetria a campi singoli per velocita operativa in console
- forced update e remote warning restano JSON per localizzazione e struttura annidata

## 6. Precedenza regole telemetria

Ordine valutazione evento:

1. tracking_enabled_global=false -> blocca tutto
2. tracking_mode=off -> blocca tutto
3. tracking_mode=minimal -> consenti solo eventi minimal
4. evento in blocked list -> blocca
5. allowed list non vuota e evento assente -> blocca
6. sampling non passato -> scarta
7. cap giornaliero superato -> blocca
8. altrimenti invia

Minimal mode (core):

- app_opened
- screen_view_custom
- analysis_started
- analysis_completed

## 7. Event taxonomy v1

Eventi app correnti/supportati:

- `app_opened`
- `screen_view_custom`
- `overlay_view`
- `cta_click`
- `screen_time`
- `analysis_started` (flow analysis)
- `analysis_completed` (flow analysis)
- `db_parse_error` (frizione/errore apertura SQLite)
- `db_parse_duration` (performance apertura e dimensione DB)
- `empty_state_shown` (nessun dato disponibile per chat/intervallo)
- `media_share` (condivisione singola/multipla immagini o media)

Parametri principali consigliati:

- `app_opened`: version_code, build_type, mode_root_or_lite
- `screen_view_custom`: ws_screen_name, ws_graph_name, ws_mode
- `overlay_view`: ws_overlay_name, ws_screen_name, ws_mode
- `cta_click`: screen_name, cta_name, section
- `screen_time`: screen_name, stay_ms_bucket
- `db_parse_error`: reason, error_message
- `db_parse_duration`: duration_ms, duration_bucket, is_lite, db_size_mb
- `empty_state_shown`: ws_screen_name, reason
- `media_share`: media_type

User Properties attive su GA4 / Firebase Analytics:

- `installation_id`: ID anonimo installazione
- `app_version`: Versione app (es. 1.2.0)
- `app_version_code`: Codice versione
- `build_type`: debug/release
- `mode`: root/lite
- `chats_count`: Numero totale chat rilevate nel DB
- `db_size_mb`: Dimensione in MB del database analizzato

Bucketing durata raccomandato:

- lt_1s, 1_5s, 5_15s, 15_60s, gt_60s

## 8. TTL Remote Config (importante)

Significato:

- TTL = intervallo minimo tra fetch RC in release
- debug usa sempre 0

Valori pratici:

- 10080 min = 7 giorni (default consigliato uso raro)
- 1440 min = 24h
- 60 min = emergenza controllata
- 15 min = emergenza critica

Regola operativa:

- uso medio basso (es. 10 min/settimana): tieni 7 giorni
- emergenza kill switch: abbassa TTL temporaneamente
- finita emergenza: rialza TTL

## 9. Runbook kill switch tracking

### Stop totale immediato

1. Firebase Console -> Remote Config
2. tracking_enabled_global = false
3. Publish
4. Attendi fetch app (dipende da TTL release)

Effetto:

- zero nuovi eventi inviati
- dati storici GA4 restano

### Stop selettivo eventi rumorosi

1. tracking_enabled_global = true
2. tracking_blocked_events_csv = cta_click,screen_time
3. opzionale tracking_sampling_percent = 30
4. Publish

### Modalita ridotta

1. tracking_mode = minimal
2. opzionale allowed/blocked list vuote
3. Publish

Rollback:

1. tracking_enabled_global = true
2. tracking_mode = full (o minimal)
3. Publish

## 10. Forced update runbook

Chiave JSON: force_update_config

Struttura:

- enabled (bool)
- min_supported_version_code (long)
- message (map locale -> testo)

Flow runtime:

1. fetch/activate RC
2. confronto BuildConfig.VERSION_CODE con min_supported_version_code
3. se sotto soglia e enabled=true:
   - prova IMMEDIATE update
   - fallback dialog + deep link Play Store
4. blocco UX fino a update/completamento fallback

Regola critica:

- mai attivare forced update prima che build target sia disponibile sui canali necessari

## 11. Remote warning runbook

Chiave JSON: remote_warning_config

Struttura:

- enabled
- show_once_per_version
- title/message/primary_button/secondary_button localizzati
- primary_action, secondary_action
- action_url

Azioni supportate:

- dismiss
- open_url
- open_play_store

## 12. Setup e rollout

### Setup iniziale console

1. crea progetto Firebase
2. collega app release/debug
3. abilita Analytics e Remote Config
4. importa docs/firebase_remote_config_template.json

### Rollout consigliato

1. valida in DebugView
2. testa forced update in internal testing
3. applica rollout graduale
4. monitora GA4 real-time + crash

## 13. Privacy, policy, data safety

Play Console:

- App content -> Privacy policy
- App content -> Data safety
- App content -> Ads

Allineamenti minimi:

- dichiarare Analytics/Remote Config/AdMob reali
- verificare cifratura in transito
- mantenere coerenza tra form e comportamento SDK release

## 14. Rischi e mitigazioni

- drift eventi: taxonomy versionata + review PR
- forced update errato: default safe + rollout graduale
- mismatch Play dichiarazioni: checklist pre-release
- overquota Firebase: kill switch + sampling + cap + minimal mode

## 15. Definition of Done

Done quando:

- eventi base visibili in DebugView
- dashboard GA4 mostra screen/click principali
- screen_time disponibile su schermate core
- forced update testato su internal track
- kill switch globale e selettivo testati
- TTL verificato su release/debug
- policy privacy + data safety allineate
- README allineato

## 16. Checklist rapida

- [ ] progetto Firebase pronto
- [ ] template RC importato
- [ ] tracking layer attivo
- [ ] eventi core tracciati
- [ ] screen_time tracciato
- [ ] forced update testato
- [ ] remote warning testato
- [ ] kill switch globale testato
- [ ] kill switch selettivo testato
- [ ] sampling/cap testati
- [ ] TTL configurato (default 10080)
- [ ] policy/data safety aggiornate
