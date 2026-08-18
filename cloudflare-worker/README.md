# WhatStat Feedback Worker (Cloudflare Worker)

Gestisce le notifiche Telegram in arrivo dall'app WhatStat e consente allo sviluppatore di rispondere direttamente citando il messaggio su Telegram. Il Worker sincronizza le risposte su Firebase Firestore e invia immediatamente una **notifica push di sistema via Firebase Cloud Messaging (FCM HTTP v1)** al dispositivo dell'utente anche ad app chiusa.

---

## 1. Setup Locale & Deploy

```bash
cd cloudflare-worker
npm install

# Login su Cloudflare (se non gia' effettuato)
npx wrangler login

# Crea il KV Namespace per il rate limiting
npx wrangler kv:namespace create FEEDBACK_KV
```

Copia l'ID restituito dal comando sopra e incollalo in [wrangler.toml](file:///Users/lscialpi/Downloads/Altro/WhatStat/cloudflare-worker/wrangler.toml) nel campo `id`:
```toml
[[kv_namespaces]]
binding = "FEEDBACK_KV"
id = "<INCOLLA_KV_ID_QUI>"
```

Poi effettua il deploy:
```bash
npx wrangler deploy
```

---

## 2. Configurazione Secrets

Imposta i 3 secret richiesti:

```bash
# 1. Token del bot creato con @BotFather
npx wrangler secret put TELEGRAM_BOT_TOKEN

# 2. Tuo ID utente o chat ID su Telegram (puoi scoprirlo con @userinfobot)
npx wrangler secret put TELEGRAM_CHAT_ID

# 3. JSON Service Account di Firebase (per scrittura su Firestore via REST)
# Scarica la chiave privata JSON da: Firebase Console -> Project Settings -> Service Accounts -> Generate new private key
# Incolla l'intero JSON minificato/su una riga
npx wrangler secret put FIREBASE_SERVICE_ACCOUNT_KEY
```

---

## 3. Configurazione Webhook Telegram

Collega il webhook del tuo Bot Telegram all'URL del Worker:

```bash
curl "https://api.telegram.org/bot<TUO_TELEGRAM_BOT_TOKEN>/setWebhook?url=https://whatstat-feedback-worker.<tuo-subdomain>.workers.dev/webhook"
```

---

## 4. Aggiorna URL nel Client Android

Nel file [FeedbackRepository.kt](file:///Users/lscialpi/Downloads/Altro/WhatStat/app/src/main/java/com/whatstats/data/feedback/FeedbackRepository.kt), imposta `WORKER_NOTIFY_URL`:
```kotlin
const val WORKER_NOTIFY_URL = "https://whatstat-feedback-worker.<tuo-subdomain>.workers.dev/notify"
```
