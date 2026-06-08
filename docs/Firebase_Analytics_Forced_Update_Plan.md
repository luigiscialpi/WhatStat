# Firebase Analytics + Forced Update Plan

Data: 2026-06-08
Stato: draft operativo

## 1. Obiettivo

Aggiungere telemetria prodotto senza inviare contenuti chat, con questi output:

- capire versione app usata
- capire dove utenti cliccano
- capire quali schermate usano di piu
- capire tempo speso per schermata
- poter forzare update quando necessario

Vincolo non negoziabile:

- nessun contenuto chat inviato a Firebase
- nessun JID, nome contatto, numero telefono, testo messaggi

## 2. Scope tecnico

In scope:

- Firebase Analytics
- Firebase Remote Config (gating forced update)
- In-App Updates Google Play (flow IMMEDIATE)
- controllo remoto tracking (globale + selettivo)
- guardrail anti-overquota piano Firebase
- event taxonomy stabile
- privacy/policy/data safety alignment

Out of scope:

- A/B test iniziale
- BigQuery model avanzati
- funnel complessi multi-step

## 3. Stato attuale progetto

Punti utili gia presenti:

- consenso UMP gia integrato in AdsRepository
- voce privacy ads gia presente in drawer
- routing centralizzato in AppNavigation
- versioning app in app/build.gradle.kts

File chiave esistenti:

- app/src/main/java/com/whatstats/ads/AdsRepository.kt
- app/src/main/java/com/whatstats/MainActivity.kt
- app/src/main/java/com/whatstats/ui/navigation/AppNavigation.kt
- app/src/main/java/com/whatstats/ui/component/AppDrawer.kt
- app/build.gradle.kts
- README.md

## 4. Strategia raccomandata

Scelta migliore: approccio ibrido.

- Decisione remota: Remote Config decide min version supportata
- Enforcement UX: In-App Updates IMMEDIATE quando possibile
- Fallback: deep link Play Store + schermata bloccante se update manager non disponibile

Perche ibrido:

- cambi soglia update senza rilasciare apk
- user experience nativa Play su update obbligatorio
- fallback robusto per casi edge

## 5. Architettura analytics

Creare un layer unico, non chiamare Firebase direttamente da ogni screen.

Nuovi componenti:

- AnalyticsTracker interface
- FirebaseAnalyticsTracker implementazione
- AnalyticsEvent sealed model
- ScreenTimeTracker helper
- TrackingGate per consenso e kill-switch

Principi:

- naming eventi breve, stabile, versionato
- parametri minimi e anonimi
- max 25 parametri GA4 rispettato
- no PII sempre

## 5.1 Kill switch remoto tracking

Obiettivo:

- spegnere tracking subito senza rilascio app
- spegnere solo subset eventi se serve
- ridurre volume eventi in caso rischio quota

Remote Config keys consigliate:

- tracking_enabled_global (bool, default true)
- tracking_mode (string: full|minimal|off, default full)
- tracking_allowed_events_csv (string, default vuoto = usa default app)
- tracking_blocked_events_csv (string, default vuoto)
- tracking_sampling_percent (long 0-100, default 100)
- tracking_daily_event_cap_per_user (long, default 200)
- tracking_config_ttl_minutes (long, default 240)

Precedenza regole (ordine valutazione):

1. tracking_enabled_global=false -> blocca tutto
2. tracking_mode=off -> blocca tutto
3. tracking_daily_event_cap_per_user superato -> blocca evento
4. sampling non passato -> scarta evento
5. blocked list contiene evento -> blocca evento
6. allowed list non vuota e evento non presente -> blocca evento
7. altrimenti invia evento

Note implementative:

- parsing CSV robusto, trim + lowercase
- fallback safe a config locale se Remote Config non disponibile
- cache decisione gate in memoria per evitare overhead per evento
- log interno solo debug per capire perche evento bloccato

## 5.2 Guardrail anti-costo quota

Strategia consigliata:

- baseline: tracking_mode=full solo in fase iniziale validazione
- steady state: tracking_mode=minimal con soli eventi core
- sampling: 20-50% su eventi ad alto volume (es. cta_click)
- cap giornaliero per utente per limitare burst anomali

Set eventi core (minimal mode):

- app_opened
- screen_view_custom
- analysis_started
- analysis_completed

Eventi sacrificabili in emergenza:

- cta_click
- content_inspected
- screen_time

## 6. Event taxonomy v1

Eventi base consigliati:

- app_opened
  - params: app_version, version_code, build_type, mode_root_or_lite
- screen_view_custom
  - params: screen_name, graph_name, mode_root_or_lite
- cta_click
  - params: screen_name, cta_name, section
- tab_selected
  - params: tab_name, mode_root_or_lite
- analysis_started
  - params: flow_type (root|lite), source
- analysis_completed
  - params: flow_type, duration_ms_bucket
- content_inspected
  - params: feature (dashboard|chat|media|radar|calls)
- screen_time
  - params: screen_name, stay_ms_bucket

Bucketing raccomandato:

- duration_ms_bucket: <1s, 1-5s, 5-15s, 15-60s, >60s
- stay_ms_bucket: stessi bucket

## 7. Piano implementativo a fasi

### Fase 0 - Setup Firebase console

- crea progetto Firebase
- collega package release: com.whatstats
- collega package debug: com.whatstats.debug
- abilita Analytics
- abilita Remote Config
- recupera dal pannello Firebase i valori di configurazione app e inseriscili in local.properties

Deliverable:

- local.properties con chiavi Firebase compilate

Chiavi richieste da local.properties:

- FIREBASE_APP_ID
- FIREBASE_API_KEY
- FIREBASE_PROJECT_ID
- FIREBASE_GCM_SENDER_ID
- FIREBASE_STORAGE_BUCKET
- FIREBASE_DEBUG_APP_ID, opzionale
- FIREBASE_DEBUG_API_KEY, opzionale

### Fase 1 - Gradle wiring

- aggiungi plugin google services
- aggiungi Firebase BoM
- aggiungi dependency analytics-ktx
- aggiungi dependency remote-config-ktx
- aggiungi dependency play app update (core + ktx)
- bootstrap Firebase manuale, quindi google-services.json non e necessario

Deliverable:

- build.gradle.kts aggiornato
- app/build.gradle.kts aggiornato

### Fase 2 - Tracking layer

- crea package app/src/main/java/com/whatstats/telemetry
- aggiungi interface tracker
- aggiungi impl Firebase tracker
- aggiungi no-op tracker per test
- integra Hilt module
- aggiungi TrackingGateConfig + RemoteTrackingGate
- aggiungi contatore locale eventi/giorno (DataStore)

Deliverable:

- chiamate tracking centralizzate
- nessuna dipendenza Firebase nei composable
- kill switch remoto globale attivo
- kill switch selettivo per evento attivo

### Fase 3 - Hook navigazione e click

- traccia screen enter in AppNavigation
- traccia click primari in drawer e CTA principali
- traccia start/complete analisi root e lite

Deliverable:

- eventi visibili in DebugView

### Fase 4 - Screen time

- aggiungi helper start/stop timer per route
- invia evento screen_time su uscita schermo
- bucketizza durata lato app

Deliverable:

- metrica tempo per schermata stabile

### Fase 5 - Forced update

Remote Config keys iniziali:

- force_update_enabled (bool, default false)
- min_supported_version_code (long, default 1)
- min_recommended_version_code (long, default 1)
- force_update_message_it (string)
- force_update_message_en (string)
- tracking_enabled_global (bool, default true)
- tracking_mode (string: full|minimal|off)
- tracking_allowed_events_csv (string)
- tracking_blocked_events_csv (string)
- tracking_sampling_percent (long)
- tracking_daily_event_cap_per_user (long)

Flow runtime:

1. app launch
2. fetch/activate remote config
3. confronta BuildConfig.VERSION_CODE con min_supported_version_code
4. se versione sotto soglia e force_update_enabled true:
   - prova AppUpdateType.IMMEDIATE
   - se fallisce, mostra blocking dialog con bottone Play Store
5. blocca navigazione finche update non completato o app chiusa

Policy fail-safe:

- timeout fetch breve (es. 3-5s)
- usa cache remote config se offline
- non bloccare se nessun valore valido disponibile

Deliverable:

- UpdateGate unico invocato da MainActivity
- schermata/dialog blocco update

### Fase 6 - QA e rollout

- valida eventi in Firebase DebugView
- valida forced update con soglia alta in internal testing
- valida path release e debug separati
- rollout graduale su Play

Deliverable:

- checklist test firmata
- rollback plan pronto

## 8. Come forzare update in pratica

Procedura operativa consigliata:

1. pubblichi nuova versione su Play
2. aspetti disponibilita update
3. in Remote Config imposti:
   - force_update_enabled=true
   - min_supported_version_code=<nuovo minimo>
4. monitori crash e adozione
5. quando base utenti aggiornata, alzi ancora soglia se serve

Regola importante:

- mai attivare forced update prima che build target sia realmente disponibile a tutti i paesi/canali necessari

## 8.1 Come fermare tracking da remoto in pratica

Scenario A - stop totale immediato:

1. imposta tracking_enabled_global=false
2. publish Remote Config
3. verifica calo eventi in DebugView e dashboard standard

Scenario B - stop selettivo eventi rumorosi:

1. lascia tracking_enabled_global=true
2. imposta tracking_blocked_events_csv=cta_click,screen_time
3. opzionale: abbassa tracking_sampling_percent (es. 30)
4. publish Remote Config

Scenario C - modalita minima conservativa:

1. imposta tracking_mode=minimal
2. svuota allowed/blocked list
3. publish Remote Config

Regola operativa:

- prepara preset salvati in console: full, minimal, off
- usa rollout graduale anche su config critiche

## 9. Policy e Data Safety: dove aggiornare

## 9.1 Play Console

Percorsi principali:

- App content -> Privacy policy
  - aggiorna URL policy pubblica
  - policy deve citare Firebase Analytics, Remote Config, AdMob, UMP

- App content -> Data safety
  - aggiorna form su raccolta/condivisione dati
  - dichiara finalita analytics, app functionality, fraud/security se usate
  - dichiara eventuale raccolta device identifiers e app interactions

- App content -> Ads
  - conferma presenza advertising SDK

- Store presence -> Main store listing
  - verifica coerenza testo descrizione privacy

## 9.2 Documenti e app da aggiornare

Nel repo/app:

- README.md
  - allinea sezione privacy con uso Firebase
- docs policy interne
  - aggiungi sezione telemetria + forced update
- AppDrawer
  - aggiungi voce Privacy Policy (link web)
- eventuale schermata About/Legal
  - mostra versione policy e data ultimo update

## 10. Data safety: guida pratica compilazione

Per Firebase Analytics tipicamente va verificato almeno:

- App interactions
- Device or other IDs
- Diagnostics (se abiliti Crashlytics)

Per ogni categoria nel form:

- raccolta dati: si/no
- condivisione dati: si/no
- scopo uso dati
- dati cifrati in transito
- possibilita delete request (se applicabile)

Nota operativa:

- compilare Data Safety basandosi su comportamento reale SDK attivi nel build release pubblicato

## 11. Rischi e mitigazioni

Rischio: drift eventi nel tempo
Mitigazione: file taxonomy versionato + review PR obbligatoria

Rischio: blocco utenti per config errata
Mitigazione: default safe, guardrail, rollout graduale

Rischio: mismatch dichiarazioni Play
Mitigazione: checklist pre-release policy/data safety

Rischio: overquota eventi Firebase
Mitigazione: kill switch remoto + sampling + cap giornaliero + modalita minimal

## 12. Definition of Done

Done quando:

- eventi base arrivano in DebugView
- dashboard GA4 mostra screen e click principali
- screen_time disponibile per almeno 5 schermate core
- forced update testato internal track
- privacy policy pubblica aggiornata
- Data Safety Play allineata e approvata
- README allineato

## 13. Checklist rapida esecuzione

- [ ] Firebase project creato
- [ ] google-services.json release + debug inseriti
- [ ] plugin e dependency aggiunte
- [ ] tracking layer implementato con Hilt
- [ ] eventi core tracciati
- [ ] screen time tracciato
- [ ] UpdateGate implementato
- [ ] Remote Config keys create
- [ ] kill switch globale testato
- [ ] kill switch selettivo testato
- [ ] sampling testato
- [ ] cap giornaliero testato
- [ ] forced update testato
- [ ] policy aggiornata
- [ ] data safety aggiornata
- [ ] README aggiornato
