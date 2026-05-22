# Piano di fattibilita per AdMob e versione Pro in WhatStat

Data: 2026-05-22

## 1. Contesto del progetto

WhatStat e un'app Android nativa scritta in Kotlin e Jetpack Compose. L'app legge e analizza database WhatsApp direttamente sul dispositivo, senza inviare i contenuti delle chat a server esterni.

L'app ha due modalita principali:

- Root mode: legge il database WhatsApp live da dispositivo rootato.
- Lite mode: importa chat esportate in formato ZIP da WhatsApp.

Le schermate principali sono gestite con Navigation Compose e una bottom navigation. Le tab principali sono:

- Dashboard
- Chat
- Media
- Radar Parole
- Chiamate, solo in Root mode

Oggi WhatStat e progettata come app completamente offline. Questo e confermato dal codice:

- `app/src/main/AndroidManifest.xml` rimuove il permesso `android.permission.INTERNET`.
- `app/src/main/res/xml/network_security_config.xml` blocca il traffico HTTPS rimuovendo i trust anchor.
- Il README dichiara che nessun dato viene inviato a server esterni.

L'introduzione di AdMob cambia questa promessa, perche AdMob richiede rete, SDK Google Mobile Ads e consenso privacy.

## 2. Obiettivo

L'obiettivo e monetizzare WhatStat mantenendo una sola app sul Play Store.

La strategia decisa e:

- mantenere una sola app
- mostrare pubblicita agli utenti Free
- aggiungere uno sblocco Pro acquistabile dentro l'app
- permettere agli utenti Pro di rimuovere tutte le pubblicita
- rimuovere anche lo spazio UI occupato dalle pubblicita per gli utenti Pro
- mantenere per gli utenti Pro lo stesso layout attuale, cioe quello esistente prima dell'introduzione degli ads

La voce per acquistare o gestire Pro andra inserita dentro `AppDrawer`, il drawer laterale dell'app.

## 3. Decisioni finali

Le decisioni di prodotto e implementazione sono queste:

1. Non creare due applicazioni separate.
2. Non creare flavor separati Free/Pro.
3. Usare una sola app con acquisto in-app non consumabile.
4. Usare Google Play Billing per sbloccare Pro.
5. Usare AdMob per la versione Free.
6. Usare UMP SDK per il consenso privacy in app.
7. Inserire un banner adaptive sopra la `BottomNavigationBar`.
8. Inserire native ads in alcune schermate scrollabili.
9. Usare interstitial full-screen solo raramente, dopo operazioni lunghe completate.
10. Non mostrare video o full-screen ads durante operazioni sul database.
11. Non concatenare piu ads full-screen.
12. Non usare rewarded ads nel primo MVP.

## 4. Impatto privacy e comunicazione

Con una sola app, il package installato conterra sempre:

- Google Mobile Ads SDK
- Google UMP SDK
- Google Play Billing Library
- permesso `android.permission.INTERNET`
- configurazione di rete compatibile con HTTPS

Anche se un utente compra Pro e non vede piu pubblicita, l'app non puo piu essere descritta come app "100% offline" in senso assoluto, perche il package contiene SDK che usano rete.

La comunicazione consigliata e:

- "Le analisi WhatsApp restano locali sul dispositivo."
- "I contenuti delle chat non vengono inviati a server esterni."
- "La versione Free usa AdMob per mostrare annunci."
- "La versione Pro rimuove gli annunci e non carica spazi pubblicitari."

Frasi da evitare dopo questa modifica:

- "App 100% offline."
- "Nessuna rete."
- "Nessun SDK di terze parti."
- "Nessun dato esce mai dal dispositivo."

Una formulazione corretta dovrebbe distinguere fra contenuti WhatsApp e SDK pubblicitari. L'obiettivo e poter dire che i dati analizzati da WhatStat restano locali, non che l'app non faccia mai traffico di rete.

## 5. Consenso privacy

Un semplice link a una pagina web non basta per gestire il consenso AdMob.

Serve implementare il flusso UMP dentro l'app. UMP sta per User Messaging Platform ed e l'SDK Google usato per raccogliere e gestire il consenso privacy richiesto per gli annunci.

Il flusso corretto e:

1. A ogni avvio chiamare `requestConsentInfoUpdate()`.
2. Se necessario, chiamare `loadAndShowConsentFormIfRequired()`.
3. Caricare ads solo se `canRequestAds()` restituisce `true`.
4. Mostrare un entry point per le privacy options se `privacyOptionsRequirementStatus == REQUIRED`.
5. Da AppDrawer, usare `showPrivacyOptionsForm()` per permettere all'utente di cambiare scelta.

La pagina web serve comunque, ma non sostituisce UMP. Deve essere usata per:

- privacy policy pubblica
- dettagli su dati, SDK e finalita
- link da Play Store
- link da AppDrawer

## 6. Fonti ufficiali

Fonti usate per questo piano:

- Google Mobile Ads setup Android: https://developers.google.com/admob/android/quick-start
- Google Mobile Ads SDK Android: https://developers.google.com/admob/android/sdk
- UMP SDK Android: https://developers.google.com/admob/android/privacy
- Banner consigliati: https://support.google.com/admob/answer/6275335
- Banner sconsigliati: https://support.google.com/admob/answer/6275345
- Interstitial consigliati: https://support.google.com/admob/answer/6201350
- Interstitial vietati/sconsigliati: https://support.google.com/admob/answer/6201362
- Rewarded ads policy: https://support.google.com/admob/answer/7313578
- Google Play Billing integration: https://developer.android.com/google/play/billing/integrate
- Google Play Billing release notes: https://developer.android.com/google/play/billing/release-notes

Versioni ufficiali aggiornate al 2026-05-22:

- `com.google.android.gms:play-services-ads:25.2.0`
- `com.google.android.ump:user-messaging-platform:4.0.0`
- `com.android.billingclient:billing-ktx:9.0.0`

## 7. Compatibilita tecnica

WhatStat e compatibile con Google Mobile Ads dal punto di vista di SDK Android:

- minSdk attuale: 26
- compileSdk attuale: 35
- Google Mobile Ads richiede minSdk 23 o superiore
- Google Mobile Ads richiede compileSdk 35 o superiore

Sono gia presenti:

- repository `google()`
- repository `mavenCentral()`
- Kotlin
- Jetpack Compose
- Hilt
- DataStore

Le modifiche principali non riguardano Room, DAO, query SQL o repository statistiche. Gli ads devono restare separati dal data layer dell'app.

## 8. Dipendenze Gradle

Nel file:

`app/build.gradle.kts`

aggiungere:

```kotlin
implementation("com.google.android.gms:play-services-ads:25.2.0")
implementation("com.google.android.ump:user-messaging-platform:4.0.0")
implementation("com.android.billingclient:billing-ktx:9.0.0")
```

In debug usare sempre test ad units e test app ID.

Gli ID reali non dovrebbero essere hardcoded direttamente nel repository. Meglio leggerli da `local.properties`, Gradle properties o secrets di CI.

## 9. Manifest e rete

Nel file:

`app/src/main/AndroidManifest.xml`

oggi e presente una rimozione esplicita del permesso internet:

```xml
<uses-permission
    android:name="android.permission.INTERNET"
    tools:node="remove" />
```

Per usare AdMob, questa riga va rimossa o modificata.

Servira aggiungere anche il meta-data AdMob:

```xml
<meta-data
    android:name="com.google.android.gms.ads.APPLICATION_ID"
    android:value="${ADMOB_APP_ID}" />
```

Nel file:

`app/src/main/res/xml/network_security_config.xml`

oggi il traffico HTTPS e di fatto bloccato perche non sono definiti trust anchor. Per AdMob serve permettere HTTPS normale, mantenendo comunque bloccato il cleartext.

La regola da mantenere e:

- HTTPS consentito
- HTTP cleartext non consentito
- nessuna apertura generica a traffico non sicuro

## 10. Architettura proposta

La logica ads e billing deve essere isolata in package dedicati. Non deve entrare dentro DAO, repository analytics o query database.

### Package ads

Creare:

`app/src/main/java/com/whatstat/ads/`

Classi consigliate:

- `AdsConfig`
- `AdsConsentManager`
- `MobileAdsInitializer`
- `AdStateRepository`
- `InterstitialGate`
- `NativeAdCache`
- `AdPlacementPolicy`

Responsabilita:

- gestire consenso UMP
- sapere se si possono richiedere ads
- inizializzare Mobile Ads
- esporre stato ads alla UI
- gestire cooldown interstitial
- evitare carichi pubblicitari se Pro e attivo

Stato esposto:

```kotlin
val adsEnabled: StateFlow<Boolean>
val isPro: StateFlow<Boolean>
val canRequestAds: StateFlow<Boolean>
val showPrivacyOptions: StateFlow<Boolean>
```

Regola principale:

```kotlin
adsEnabled = !isPro && canRequestAds
```

Se `isPro == true`, l'app non deve caricare nuove pubblicita e deve distruggere eventuali native ads in cache.

### Package billing

Creare:

`app/src/main/java/com/whatstat/billing/`

Classi consigliate:

- `BillingRepository`
- `ProEntitlementStore`
- `BillingUiState`
- `ProPurchaseViewModel`

Responsabilita:

- connettersi a Google Play Billing
- leggere product details
- lanciare purchase flow
- gestire restore
- interrogare acquisti esistenti
- riconoscere lo stato Pro
- fare acknowledge dell'acquisto

### DataStore Pro

Creare uno store dedicato:

`ProEntitlementStore`

Chiavi suggerite:

- `is_pro_cached`
- `last_purchase_check_ms`
- `purchase_token_hash`

La cache locale serve solo per UI veloce. La sorgente autorevole deve restare Google Play Billing.

Regole:

- se acquisto e `PURCHASED`, Pro attiva
- se acquisto e `PENDING`, Pro non ancora attiva
- se acquisto viene revocato o rimborsato, Pro viene disattivata al prossimo sync
- acquisto non consumabile va acknowledged

Per MVP e accettabile una verifica client-only. In futuro, un backend renderebbe piu robusta la verifica contro manomissioni.

## 11. UI Pro in AppDrawer

File:

`app/src/main/java/com/whatstat/ui/component/AppDrawer.kt`

Aggiungere voci:

- "Passa a Pro" se l'utente e Free
- "Pro attiva" se l'utente ha gia acquistato
- "Ripristina acquisto"
- "Privacy annunci" se UMP richiede privacy options

La schermata o dialog Pro deve mostrare:

- prezzo letto da Google Play Billing
- beneficio principale: nessuna pubblicita
- beneficio secondario: layout pulito come oggi
- beneficio etico: supporto allo sviluppo dell'app
- bottone acquisto
- bottone ripristino
- messaggio per acquisto pending o fallito

Quando l'acquisto Pro e attivo:

- il banner sopra bottom bar sparisce
- gli ads native spariscono
- gli interstitial non vengono mostrati
- gli slot pubblicitari non occupano spazio
- il layout torna identico a quello attuale

## 12. Componenti Compose per ads

Creare:

`app/src/main/java/com/whatstat/ui/component/ads/`

Componenti consigliati:

- `AdSlot`
- `BottomBannerAd`
- `NativeAdCard`
- `AdSpacerPolicy`

Il wrapper centrale deve avere una regola semplice:

```kotlin
if (!adsEnabled) return
```

Questo garantisce che utente Pro non veda ads e non veda spazio vuoto.

Per i native ads usare `AndroidView` con `NativeAdView`, gestendo correttamente `destroy()` quando il composable viene rimosso.

## 13. Placement ads decisi

### 13.1 Banner sopra bottom navigation

Questo e il placement fisso scelto per avere revenue stabile.

File:

`app/src/main/java/com/whatstat/ui/screen/home/AppScaffold.kt`

Formato:

- adaptive banner

Posizione:

- sopra `BottomNavigationBar`
- dentro `Scaffold.bottomBar`
- separato dalla navigation con bordo o divider non cliccabile
- altezza fissa solo quando ad e visibile

Schema:

```kotlin
bottomBar = {
    Column {
        if (showBannerAd) {
            BottomBannerAd()
            HorizontalDivider()
        }
        BottomNavigationBar(...)
    }
}
```

Mostrare quando:

- utente Free
- consenso valido
- bottom bar visibile
- route dentro Root mode o Lite mode

Nascondere quando:

- utente Pro
- Setup screen
- import Lite
- operazioni critiche
- viewer media fullscreen
- dialog o bottom sheet coprirebbero il banner

Rischio:

Google sconsiglia banner troppo vicini a elementi interattivi. La bottom navigation e un elemento interattivo, quindi serve separazione visiva e spazio fisso.

Mitigazione:

- divider non cliccabile
- nessun overlay
- nessuna animazione
- contenuto mai sotto il banner
- monitorare invalid clicks in AdMob

### 13.2 Native ad in Dashboard

File:

`app/src/main/java/com/whatstat/ui/screen/dashboard/DashboardScreen.kt`

Formato:

- native ad card

Posizione:

- dopo la grid delle `StatCard`
- prima di `ActivityRecapCard`

Motivo:

L'utente ha gia visto il valore principale della schermata. Il placement e dentro contenuto scrollabile e non vicino a bottoni critici.

### 13.3 Native ad in ChatList

File:

`app/src/main/java/com/whatstat/ui/screen/chatlist/ChatListScreen.kt`

Formato:

- native item dentro lista

Posizione:

- dopo la sesta chat
- poi ogni dodici chat
- solo se la lista contiene almeno dieci chat

Regole:

- label "Annuncio" visibile
- altezza stabile
- non vicino search bar
- non vicino FAB Lite
- non alterare click sulle chat

### 13.4 Native ad in ChatDetail

File:

`app/src/main/java/com/whatstat/ui/screen/chatdetail/ChatDetailScreen.kt`

Formato:

- native ad card

Posizione:

- dopo le prime card statistiche
- prima delle sezioni lente o opt-in

Regole:

- massimo un ad per detail
- niente ads vicino header o avatar
- niente ads tra filtro temporale e grafici
- niente ads dentro chip mittenti o sezioni interattive

### 13.5 Native ad in Media e Link

File:

`app/src/main/java/com/whatstat/ui/screen/media/MediaStatsScreen.kt`

Formato:

- native ad card fra risultati link

Posizione:

- dopo la terza `LinkStatCard`

Non mostrare ads:

- dentro griglia immagini
- dentro video/audio viewer
- dentro gallery fullscreen

### 13.6 Native ad in Radar Parole

File:

`app/src/main/java/com/whatstat/ui/screen/wordsearch/WordSearchScreen.kt`

Formato:

- native ad dopo risultati

Posizione:

- dopo cinque risultati
- solo dopo una ricerca avvenuta
- solo se ci sono almeno dieci risultati

Non mostrare ads:

- mentre utente digita
- sotto search field
- fra suggerimenti
- prima che esistano risultati

### 13.7 Native ad in Chiamate

File:

`app/src/main/java/com/whatstat/ui/screen/calls/CallsStatsScreen.kt`

Formato:

- native ad singola

Posizione:

- dopo riepilogo alto livello
- prima lista dettagliata

Nota:

Questa tab esiste solo in Root mode.

## 14. Interstitial full-screen

Gli interstitial sono ads full-screen. Possono essere video, ma la durata e il formato esatto dipendono dal creativo servito da AdMob. L'app non deve basarsi sull'idea che siano sempre video da 15 secondi.

Decisione:

- usare interstitial in modo raro
- mostrarli solo dopo operazioni lunghe completate
- non mostrarli durante operazioni DB

Trigger ammessi:

- import Lite completato
- analisi Root completata
- analisi lenta completata
- refresh molto pesante completato

Trigger vietati:

- durante progress
- durante lavoro sul database
- avvio app
- uscita app
- back button
- cambio tab
- apertura ChatDetail
- apertura media viewer
- ogni singola azione utente
- subito dopo un altro interstitial

Gate consigliato:

- massimo un interstitial ogni 10-15 minuti
- mai prima che l'utente abbia visto valore nell'app
- mai se utente Pro
- mai se consenso mancante
- mai se errore
- mai se import fallisce
- pre-load anticipato
- se ad non e pronta, continuare navigazione senza aspettare

Motivo:

Google consiglia gli interstitial solo in pause naturali. Un'operazione DB in corso non e una pausa naturale: l'utente sta aspettando un risultato.

## 15. Ads durante operazioni DB

Decisione esplicita:

- non mostrare full-screen ads durante operazioni DB
- non mostrare video durante progress
- non concatenare video

Motivi:

- il progress e parte importante dell'esperienza
- l'utente deve poter capire se import o analisi stanno procedendo
- l'operazione potrebbe finire prima dell'ad
- l'ad potrebbe comparire in ritardo, nel momento sbagliato
- rischio UX alto
- rischio policy piu alto

Alternativa accettabile in futuro:

- mostrare una schermata "Analisi completata" con native ad o banner
- poi mostrare bottone "Continua"

Ma nel primo MVP e meglio non monetizzare il progress.

## 16. Rewarded ads

I rewarded ads sono ads che l'utente sceglie di guardare per ricevere una ricompensa. Nel primo MVP non vanno implementati.

Possibili usi futuri:

- export extra
- analisi extra non essenziali
- funzioni premium temporanee

Regole obbligatorie se verranno usati:

- opt-in esplicito
- opzione "no" visibile
- reward spiegata prima
- niente blocco dell'uso normale se l'utente rifiuta
- niente reward monetarie dirette

Non usare rewarded ads per:

- import ZIP
- analisi base
- accesso ai dati gia importati
- funzioni privacy o sicurezza

## 17. Regole dati e targeting

Non inviare mai ad AdMob informazioni derivate dalle chat.

Non usare come targeting:

- testo messaggi
- parole cercate
- nomi chat
- JID
- numeri telefono
- domini condivisi
- path media
- statistiche calcolate
- modalita Root/Lite

Richiesta ads consigliata:

```kotlin
AdRequest.Builder().build()
```

Senza custom targeting.

## 18. File impattati

File da modificare probabilmente:

- `app/build.gradle.kts`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/res/xml/network_security_config.xml`
- `app/src/main/java/com/whatstat/MainActivity.kt`
- `app/src/main/java/com/whatstat/ui/component/AppDrawer.kt`
- `app/src/main/java/com/whatstat/ui/screen/home/AppScaffold.kt`
- `app/src/main/java/com/whatstat/ui/screen/dashboard/DashboardScreen.kt`
- `app/src/main/java/com/whatstat/ui/screen/chatlist/ChatListScreen.kt`
- `app/src/main/java/com/whatstat/ui/screen/chatdetail/ChatDetailScreen.kt`
- `app/src/main/java/com/whatstat/ui/screen/media/MediaStatsScreen.kt`
- `app/src/main/java/com/whatstat/ui/screen/wordsearch/WordSearchScreen.kt`
- `app/src/main/java/com/whatstat/ui/screen/calls/CallsStatsScreen.kt`
- `app/src/main/res/values/strings.xml`
- localizzazioni in `values-it`, `values-de`, `values-es`, ecc.

Nuovi file probabili:

- `app/src/main/java/com/whatstat/ads/AdsConfig.kt`
- `app/src/main/java/com/whatstat/ads/AdsConsentManager.kt`
- `app/src/main/java/com/whatstat/ads/MobileAdsInitializer.kt`
- `app/src/main/java/com/whatstat/ads/AdStateRepository.kt`
- `app/src/main/java/com/whatstat/ads/InterstitialGate.kt`
- `app/src/main/java/com/whatstat/ads/NativeAdCache.kt`
- `app/src/main/java/com/whatstat/billing/BillingRepository.kt`
- `app/src/main/java/com/whatstat/billing/ProEntitlementStore.kt`
- `app/src/main/java/com/whatstat/billing/ProPurchaseViewModel.kt`
- `app/src/main/java/com/whatstat/ui/component/ads/AdSlot.kt`
- `app/src/main/java/com/whatstat/ui/component/ads/BottomBannerAd.kt`
- `app/src/main/java/com/whatstat/ui/component/ads/NativeAdCard.kt`

## 19. Piano implementazione

### Fase 1: Pro e Billing

Implementare prima lo stato Pro, anche senza ads visibili.

Passi:

1. Aggiungere dipendenza Billing.
2. Creare `BillingRepository`.
3. Creare `ProEntitlementStore`.
4. Aggiungere voce "Passa a Pro" in `AppDrawer`.
5. Creare paywall.
6. Implementare acquisto `whatstat_pro`.
7. Implementare restore purchase.
8. Implementare acknowledge acquisto.
9. Esporre `isPro` come `StateFlow<Boolean>`.

Output:

- utente puo comprare Pro
- app riconosce Pro
- nessuna pubblicita ancora implementata

### Fase 2: UMP e AdMob base

Passi:

1. Aggiungere dipendenze Ads e UMP.
2. Aggiornare manifest.
3. Aggiornare network security config.
4. Creare `AdsConsentManager`.
5. Chiamare UMP a ogni avvio.
6. Aggiungere privacy options in `AppDrawer`.
7. Inizializzare Mobile Ads solo dopo consenso.
8. Non inizializzare ads se utente Pro.

Output:

- consenso funziona
- app non carica ads senza consenso valido
- Pro bypassa ads

### Fase 3: Banner sopra bottom bar

Passi:

1. Creare `BottomBannerAd`.
2. Inserirlo in `AppScaffold`.
3. Aggiungere separatore non cliccabile.
4. Nasconderlo su Pro.
5. Nasconderlo su Setup e import.
6. Testare con bottom navigation, drawer e modal.

Output:

- banner stabile sopra bottom bar
- zero spazio banner su Pro

### Fase 4: Native ads

Passi:

1. Creare `NativeAdCard`.
2. Creare `NativeAdCache`.
3. Inserire native ad in Dashboard.
4. Inserire native ad in ChatList.
5. Inserire native ad in ChatDetail.
6. Inserire native ad in Media, Radar e Calls se si vuole estendere MVP.

Output:

- ads dentro contenuto scrollabile
- niente overlay
- niente spazio su Pro

### Fase 5: Interstitial

Passi:

1. Creare `InterstitialGate`.
2. Implementare cooldown 10-15 minuti.
3. Precaricare interstitial.
4. Trigger dopo import Lite completato.
5. Trigger dopo analisi Root completata.
6. Se ad non e pronta, navigare comunque.

Output:

- full-screen raro
- mai durante DB/progress
- mai su navigazione frequente

### Fase 6: Documentazione e release

Passi:

1. Aggiornare README.
2. Aggiornare privacy policy.
3. Aggiornare Play Store Data Safety.
4. Testare UMP per EEA/UK/Svizzera.
5. Testare acquisti con license testers.
6. Usare test ad unit in debug.
7. Usare ID reali solo in release.

Output:

- release pronta per Play Store review

## 20. Test manuali

### Test Pro

- acquisto Pro riuscito
- restore Pro riuscito
- acquisto pending non abilita Pro
- rimborso o revoca disabilita Pro dopo sync
- Pro nasconde banner
- Pro rimuove spazio banner
- Pro rimuove native ads
- Pro blocca interstitial
- layout Pro uguale al layout attuale

### Test Free

- consenso richiesto dove necessario
- ads non partono prima di `canRequestAds()`
- privacy options visibile se richiesta
- banner sopra bottom bar non copre navigation
- banner ha spazio fisso
- banner sparisce su Setup
- banner sparisce su import
- native ads non rompono scroll
- ChatList search non rompe posizioni ads
- FAB Lite non finisce vicino ad ads
- drawer e bottom sheet non coprono banner in modo problematico

### Test interstitial

- non appare durante progress
- appare solo dopo completamento
- cooldown rispettato
- non appare su back
- non appare su cambio tab
- non appare se non pronto
- non appare se utente Pro

### Comandi

```bash
./gradlew ktlintCheck
./gradlew :app:compileDebugKotlin
```

## 21. Rischi

### Rischio fiducia utenti

Alto.

Motivo:

WhatStat analizza dati WhatsApp sensibili. Aggiungere pubblicita e rete potrebbe ridurre fiducia se comunicato male.

Mitigazione:

- spiegare chiaramente che i contenuti chat restano locali
- spiegare che AdMob serve solo annunci nella versione Free
- offrire Pro senza ads
- non usare dati WhatsApp per targeting

### Rischio policy banner

Medio.

Motivo:

Il banner sopra bottom navigation e vicino a controlli interattivi.

Mitigazione:

- separatore visivo
- spazio fisso
- nessun overlay
- monitoraggio invalid clicks

### Rischio policy interstitial

Medio-alto.

Motivo:

Gli interstitial diventano problematici se appaiono troppo spesso o in momenti inattesi.

Mitigazione:

- cooldown 10-15 minuti
- solo dopo operazioni lunghe completate
- mai durante progress
- mai catene
- mai su back/tab

### Rischio acquisti

Medio.

Motivo:

Billing richiede gestione corretta di pending, restore, acknowledge e revoche.

Mitigazione:

- usare Billing Library aggiornata
- query purchases su resume
- acknowledge solo acquisti `PURCHASED`
- messaggi chiari per pending e restore

## 22. Raccomandazione finale

Procedere con una sola app.

MVP consigliato:

1. Implementare Pro in `AppDrawer`.
2. Implementare UMP correttamente.
3. Aggiungere banner adaptive sopra `BottomNavigationBar`.
4. Aggiungere native ads in Dashboard, ChatList e ChatDetail.
5. Aggiungere interstitial raro solo dopo operazioni lunghe completate.

Non implementare nel MVP:

- video durante operazioni DB
- full-screen durante progress
- catene di video
- rewarded ads
- ads in Setup
- ads in gallery fullscreen

Obiettivo finale:

- la versione Free monetizza con ads
- la versione Pro torna graficamente identica alla app attuale
- i contenuti WhatsApp restano locali
- il rischio policy resta gestibile
