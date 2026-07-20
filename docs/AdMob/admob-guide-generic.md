# AdMob — Guida di riferimento rapido (generica, multi-progetto)

> Raccolta di conoscenze su Google AdMob/Google Mobile Ads SDK, utile per
> future decisioni di monetizzazione in qualsiasi progetto Android. Fonti:
> documentazione ufficiale Google ("Best practices to unlock more revenue
> with AdMob Mediation") e esperienza pratica condivisa dalla community
> ([post Reddit r/admob](https://www.reddit.com/r/admob/comments/1ojk0vn/my_tips_to_improve_your_revenue_from_ads/), autore AD-LB, ott 2025).
> Versione generica, senza riferimenti a un progetto specifico — copia di
> partenza da adattare per ogni nuova app.

---

## 1. SDK: versioni e changelog

- Google Mobile Ads SDK (Android), note di rilascio:
  https://developers.google.com/admob/android/rel-notes
- User Messaging Platform (UMP) SDK, note di rilascio:
  https://developers.google.com/admob/android/privacy/release-notes
- **Importante**: il "Mobile Ads SDK (Legacy)" è in modalità di manutenzione.
  Google raccomanda la migrazione al nuovo **GMA Next-Gen SDK**:
  - Guida migrazione: https://developers.google.com/admob/android/next-gen/migration
  - Quick start Next-Gen: https://developers.google.com/admob/android/next-gen/quick-start
- Controllare periodicamente le release notes sopra per capire se serve
  aggiornare o migrare al Next-Gen SDK.

## 2. Bidding vs waterfall vs mediation

- **Bidding** = tutte le reti competono in tempo reale per ogni richiesta,
  a parità di condizioni. Sempre preferibile quando disponibile: più
  veloce, più equo, eCPM migliore.
- **Waterfall** = le reti vengono interrogate in ordine di priorità/floor
  price fissi. Da usare solo per le reti che non offrono ancora bidding
  (es. Chartboost, Yandex, Kidoz al momento del post Reddit — verificare
  sempre lo stato aggiornato con ogni network).
- Guida ufficiale mediation: https://developers.google.com/admob/android/mediation
- Scelta reti pubblicitarie: https://developers.google.com/admob/android/choose-networks
- Bidding ad sources: https://support.google.com/admob/answer/11555701
- Bidding FAQ: https://support.google.com/admob/answer/9360574
- Troubleshoot bidding: https://developers.google.com/admob/android/troubleshoot-bidding
- Mediation groups (concetto + esempio): https://support.google.com/admob/answer/13411971
- Ranking reti secondo esperienza community (ott 2025, ordine indicativo,
  cambia nel tempo): Meta, Liftoff/Vungle, Unity, InMobi, Mintegral,
  Chartboost, Pangle, InMobi Exchange, Equativ, OneTag Exchange, OpenX.
  Le reti con SDK dedicato tendono a performare meglio di quelle senza.
  Attenzione: AppLovin e IronSource segnalati come a rischio ban account
  senza preavviso in alcuni casi (fonte aneddotica, non verificata
  ufficialmente).
- App per bambini: solo reti autorizzate, vedi
  https://support.google.com/googleplay/android-developer/answer/12955712
- Regola pratica: anche con mediation multi-rete attiva, Google/AdMob
  resta tipicamente la fetta più grande di revenue (~90% secondo
  l'esperienza condivisa nel post Reddit) — non aspettarsi miracoli dalle
  reti aggiuntive, ma nemmeno trascurarle.

## 3. Formati ads: quando usarli

| Formato | Note | Link |
|---|---|---|
| Banner adattivo | Facile, eCPM più basso. Considerare MREC (300x250) come alternativa/test, spesso eCPM migliore | https://support.google.com/admob/answer/3245199 |
| Collapsible banner | Banner che si espande poi si riduce, ottimo per home/feed | https://support.google.com/admob/answer/14160679 |
| Native ads | ~10x più lavoro di un banner ma spesso eCPM/revenue migliore. Gestire aspect ratio MediaView, RTL, posizione icona AdChoices | https://support.google.com/admob/answer/6239795 |
| Interstitial | eCPM full-screen più basso tra i formati full-screen | https://developers.google.com/admob/android/interstitial |
| Rewarded | eCPM full-screen più alto, ma regole rigide su quando mostrarlo (serve valore reale per l'utente); e' il formato consigliato quando l'utente clicca esplicitamente un bottone per richiedere la ricompensa | https://developers.google.com/admob/android/rewarded |
| Rewarded interstitial | Via di mezzo, pensato per punti di transizione naturali con mostraggio automatico (non opt-in esplicito); meno reti lo supportano in mediation | https://developers.google.com/admob/android/rewarded-interstitial |
| App open ads | Mostrato al ritorno in app — impatto UX molto discusso, alcuni sviluppatori lo evitano deliberatamente | https://developers.google.com/admob/android/app-open |
| Full-screen native | Meno diffuso, prestazioni intermedie secondo community | https://developers.google.com/admob/android/native/full-screen |

- High engagement ads (rewarded/interstitial): https://support.google.com/admob/answer/15525707
- Regola dimensione: banner/native più grandi tendono a rendere di più,
  ma non superare ~50% dello spazio visibile per non violare le policy
  sui contenuti: https://support.google.com/publisherpolicies/answer/11169917
- Per full-screen ads "generici" (non legati a reward specifico), valutare
  una catena di fallback nell'ordine: rewarded → rewarded-interstitial →
  interstitial (dal più remunerativo al meno). Ogni step nella catena
  rallenta il caricamento, quindi va bilanciato con l'esperienza utente.

## 4. Setup ad unit

- Naming convention univoca per placement (non solo per formato): permette
  A/B testing e reporting granulare per posizione. Usare
  [Firebase Remote Config](https://firebase.google.com/docs/remote-config)
  per gli ID così da poter cambiare/testare senza nuova release —
  tutorial: https://firebase.google.com/docs/tutorials/test-ad-format-adoption
- Se si usa mediation con più reti su banner, controllare quali size
  supporta ciascuna rete (non tutte supportano adaptive banner):
  https://developers.google.com/admob/android/banner/fixed-size
- Testare in tempo reale con Ad Inspector:
  https://support.google.com/admob/answer/10159602

## 5. Ottimizzare esperienza e revenue

- **Init non bloccante**: non aspettare il callback di
  `MobileAds.initialize()` prima della prima richiesta ads — alcune reti
  in mediation possono metterci molto. Usare un timeout ragionevole (es.
  4s) o non aspettare affatto.
  API: https://developers.google.com/admob/android/reference/com/google/android/gms/ads/MobileAds#initialize(android.content.Context,com.google.android.gms.ads.initialization.OnInitializationCompleteListener)
- **Prefetch**: precaricare sempre il prossimo ad subito dopo aver
  mostrato/fallito quello corrente.
- **Frequency capping**: limitare quanti ads full-screen per sessione/ora.
  Guida: https://support.google.com/admob/answer/6244508 — pilotabile
  via Remote Config: https://firebase.google.com/docs/tutorials/optimize-ad-frequency/solution-overview
- **Banner refresh rate**: ottimizzare la cadenza di refresh (Google
  raccomanda minimo 30s; se il refresh è gestito via codice, disabilitare
  l'auto-refresh nelle impostazioni dell'ad unit sul sito AdMob per
  evitare doppio refresh). Guida: https://support.google.com/admob/answer/3245199
- **Expired ads**: un ad caricato ma non mostrato entro un certo tempo va
  scartato e ricaricato (soglie tipiche: 1h per interstitial/rewarded/
  rewarded-interstitial, 4h per app open). Mostrare un ad "scaduto" causa
  show falliti o impression non pagate.
- **Show rate**: https://support.google.com/admob/answer/3436475
- **Timeout richieste HTTP**: `setHttpTimeoutMillis` (default 60s),
  personalizzabile se si notano richieste troppo lente:
  https://developers.google.com/android/reference/com/google/android/gms/ads/AbstractAdRequestBuilder#public-t-sethttptimeoutmillis-int-httptimeoutmillis
- **Preservare ad precaricati**: su back button, considerare
  `moveTaskToBack` invece di chiudere/ricreare l'Activity, per non perdere
  gli ad già caricati in memoria:
  https://developer.android.com/reference/android/app/Activity#moveTaskToBack(boolean)
- **Fallback quando non c'è nessun ad disponibile**: decidere sempre cosa
  mostrare al posto di uno spazio vuoto (nascondere il contenitore,
  ritentare, mostrare un link ad altre proprie app, o un banner statico
  senza bisogno di rete tipo GameZop https://www.gamezop.com/en).
- Content vs ads: assicurarsi che ogni schermata abbia più contenuto reale
  che pubblicità: https://support.google.com/publisherpolicies/answer/11169917

## 6. Reportistica e A/B testing

- Panoramica report: https://support.google.com/admob/answer/2751663
- Ads activity report: https://support.google.com/admob/answer/10979428
- Cohort report: https://support.google.com/admob/answer/9736628
- User activity report: https://support.google.com/admob/answer/10200963
- eCPM trends: https://support.google.com/admob/answer/14659812
- Report personalizzati: https://support.google.com/admob/answer/6157315
- A/B testing mediation: https://support.google.com/admob/answer/9572326
- Impression-level ad revenue (utile per analisi/BI esterne):
  https://developers.google.com/admob/android/impression-level-ad-revenue
- Prima di aggiungere mediation multi-rete, valutare A/B testing per
  capire il reale contributo di ciascuna rete rispetto a Google da solo.

## 7. Privacy e consenso (GDPR/CCPA)

- Configurare UMP SDK: https://developers.google.com/admob/android/privacy
- Per ogni rete di mediation aggiunta, registrarla anche tra i "custom ad
  partners" nel messaggio di consenso GDPR:
  https://admob.google.com/v2/privacymessaging/gdpr/settings
  (verificare che "Automatically include common ad partners" sia attivo)
- `app-ads.txt`: necessario anche solo per AdMob, gestito da
  https://admob.google.com/v2/apps/appadstxt — file da hostare sul
  dominio/sito del developer, non nell'app Android stessa.

## 8. Policy e rischi account

- Policy pubblicitarie generali: https://support.google.com/publisherpolicies/answer/10400453
- Violazioni comuni: https://www.gstatic.com/growthlab/api/SYBJE5RsN8DO4glD1wIsxBAe58qhKWs20xBWculK.pdf
- Ad content rating (bilancia reach vs revenue, restringere abbassa
  impression/revenue in modo significativo secondo dati citati nel post
  Reddit): https://support.google.com/admob/answer/7562142
- Controllare periodicamente la lista app sul proprio account AdMob per
  individuare app non proprie erroneamente associate:
  https://admob.google.com/v2/apps/list
- Non cliccare mai i propri ads reali (nemmeno per errore/test) — rischio
  di flag per invalid activity. Usare sempre device di test:
  https://developers.google.com/admob/android/next-gen/test-ads
- Server-side verification per rewarded ads (richiede un backend proprio):
  https://developers.google.com/admob/android/ssv — valutare se applicabile
  in base all'architettura del progetto (richiede un backend dedicato).

## 9. Waterfall e ottimizzazione avanzata (solo se si adotta mediation)

- Strategia waterfall: https://support.google.com/admob/answer/13404328
- Mediation stack: https://support.google.com/admob/answer/3508141
- Ad Network Optimization (ANO), riordino automatico waterfall:
  https://support.google.com/admob/answer/7374110
- Creare mediation group differenziati per paese solo se si osservano
  reali differenze di performance (non applicare "a scatola chiusa" —
  l'autore del post Reddit non ha notato benefici usando bidding puro).

## 10. Checklist rapida da rileggere prima di toccare il codice ads

1. Sto aggiungendo un nuovo placement? Serve un ad unit ID dedicato (non
   riusare quello di un altro placement) — vedi sezione 4.
2. Sto cambiando un valore di cooldown/frequenza? Valutare se renderlo
   configurabile da Remote Config invece che hardcoded — sezione 5.
3. Sto toccando il repository/manager degli ads? Verificare che gli ad
   precaricati abbiano (o non abbiano) un controllo di scadenza — sezione 5.
4. Sto aggiungendo un nuovo formato (collapsible, app open, MREC)?
   Controllare prima la policy sui contenuti (rapporto content/ads) —
   sezione 3.
5. Sto aggiungendo una rete di mediation? Registrarla anche nel consenso
   GDPR custom ad partners — sezione 7.
6. Sto implementando un rewarded ad? Se l'utente clicca esplicitamente un
   bottone per richiedere la ricompensa (opt-in), preferire il formato
   Rewarded classico invece di Rewarded Interstitial — sezione 3.
7. Prima di rilasciare, controllare le release notes SDK (sezione 1) per
   eventuali breaking change o deprecazioni.

---

## Fonti

- Google, "Best practices to unlock more revenue with AdMob Mediation"
- AD-LB, "My tips to improve your revenue from ads", r/admob, ott 2025:
  https://www.reddit.com/r/admob/comments/1ojk0vn/my_tips_to_improve_your_revenue_from_ads/
- AD-LB, "My experience with the various ad-networks", r/admob:
  https://www.reddit.com/r/admob/comments/1oy8gjd/my_experience_with_the_various_adnetworks_for/
- Google Mobile Ads SDK — release notes: https://developers.google.com/admob/android/rel-notes
- UMP SDK — release notes: https://developers.google.com/admob/android/privacy/release-notes
