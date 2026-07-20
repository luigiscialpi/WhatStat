# Ads Revenue Roadmap

Riferimento: `docs/admob-knowledge-base.md` (best practice ufficiali + spunti community).

Stato dei 4 interventi scelti a seguito dell'audit AdMob. Aggiornato progressivamente man mano
che ogni punto viene completato.

## 1. Fix placeholder NativeAdCard su fallimento caricamento

- Stato: DONE
- Problema: se `onAdFailedToLoad` scattava, il placeholder grigio 120dp restava visibile a
  tempo indeterminato, occupando spazio inutile in UI.
- Fix: introdotta macchina a stati (`LOADING` / `LOADED` / `FAILED`) in
  [NativeAdCard.kt](../app/src/main/java/com/whatstats/ui/component/ads/NativeAdCard.kt). Un
  singolo retry automatico dopo un breve delay; se fallisce anche il retry, il composable non
  occupa più spazio (nessun placeholder permanente).

## 2. TTL sugli ad precaricati (interstitial e rewarded)

- Stato: DONE
- Problema: `AdsRepository` teneva in memoria `interstitialAd` / `rewardedInterstitialAd` senza
  scadenza. Le policy Google raccomandano di non riusare ad caricati da piu' di un'ora.
  Rischio: mostrare ad "stale" con eCPM piu' basso o invalidi.
- Fix: aggiunto timestamp di caricamento e controllo scadenza (1h) in
  [AdsRepository.kt](../app/src/main/java/com/whatstats/ads/AdsRepository.kt). Ad scaduti
  vengono scartati e ricaricati sia nei metodi di preload sia prima dello show.

## 3. Aggiornamento Google Mobile Ads SDK (Legacy)

- Stato: DONE
- Da: `com.google.android.gms:play-services-ads:24.7.0`
- A: `com.google.android.gms:play-services-ads:24.8.0`
- Note: la serie 24.9.0 e tutta la 25.x richiedono metadata Kotlin >= 2.2.0, incompatibili con
  la toolchain Kotlin 2.0.21 usata dal progetto (errore ksp: "Module was compiled with an
  incompatible version of Kotlin"). 24.8.0 e' la versione piu' recente verificata compatibile.
  Aggiornare oltre richiede prima l'upgrade del plugin Kotlin (fuori scope di questo intervento).

## 4. Rewarded ad per disattivare temporaneamente tutta la pubblicita'

- Stato: DONE
- Idea: analogo agli unlock rewarded gia' esistenti (tabs avanzati, prediction), ma con effetto
  globale: nasconde banner e native ad in tutta l'app per una finestra di tempo (2 ore),
  guardando un rewarded ad.
- UI: nuova voce nel drawer laterale ([AppDrawer.kt](../app/src/main/java/com/whatstats/ui/component/AppDrawer.kt)),
  vicino a "Opzioni privacy". Etichetta dinamica: invito a sbloccare se non attivo, countdown
  ore rimanenti se attivo.
- Persistenza: nuovo campo TTL in
  [RewardedUnlockStore.kt](../app/src/main/java/com/whatstats/ads/RewardedUnlockStore.kt),
  stesso pattern di `tabsUnlockUntilMs` / `predictionUnlockUntilMs`.
- Propagazione: `AdsUiState.areAdsEnabled` tiene conto del nuovo unlock, quindi banner e native
  ad si nascondono automaticamente ovunque nell'app (nessuna modifica necessaria nei singoli
  screen). L'interstitial viene bloccato esplicitamente in
  `tryShowInterstitialAfterLongOperation`.

## 5. Migrazione dei 3 unlock rewarded da Rewarded Interstitial a Rewarded classico

- Stato: DONE (codice) / richiede azione manuale utente su AdMob console
- Problema: tutti e 3 gli unlock rewarded (`tabs`, `prediction`, `ads-free`) usavano
  `RewardedInterstitialAd`, mostrato su richiesta esplicita dell'utente (click su un bottone/voce).
  Secondo `docs/admob-knowledge-base.md`, questo e' esattamente lo use case per cui Google
  raccomanda il formato Rewarded classico (opt-in esplicito), che ha eCPM full-screen piu' alto;
  Rewarded Interstitial e' pensato per punti di transizione naturali con mostraggio automatico,
  ed e' supportato da meno reti in mediation.
- Fix: [AdsRepository.kt](../app/src/main/java/com/whatstats/ads/AdsRepository.kt) ora usa
  `com.google.android.gms.ads.rewarded.RewardedAd` / `RewardedAd.load(...)` per tutti e 3 gli
  unlock, tramite `BuildConfig.ADMOB_REWARDED_UNIT_ID` (nuovo campo in
  [app/build.gradle.kts](../app/build.gradle.kts)). `ADMOB_REWARDED_INTERSTITIAL_UNIT_ID` resta
  presente in `BuildConfig` per compatibilita' ma non e' piu' usato.
- **Azione richiesta all'utente**: creare un ad unit "Rewarded" (non-interstitial) nella console
  AdMob e impostarne l'ID reale in `ADMOB_REWARDED_UNIT_ID` su `local.properties`/CI. Finche' non
  viene fatto, il default e' il demo ID Google (`ca-app-pub-3940256099942544/5224354917`), quindi
  in build reale (non test) il rewarded non caricherebbe annunci veri.

## Verifica finale

- Stato: DONE
- `./gradlew ktlintCheck`
- `./gradlew :app:compileDebugKotlin :app:compileReleaseKotlin`
