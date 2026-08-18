export interface Env {
  FEEDBACK_KV?: KVNamespace;
  TELEGRAM_BOT_TOKEN?: string;
  TELEGRAM_CHAT_ID?: string;
  FIREBASE_SERVICE_ACCOUNT_KEY?: string;
}

interface NotifyPayload {
  feedbackId: string;
  type: string;
  message: string;
  userId: string;
  appVersion: string;
  deviceModel: string;
  androidVersion: number;
  mode: string;
  isDebug?: boolean;
}

export default {
  async fetch(request: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
    const url = new URL(request.url);

    if (request.method === "OPTIONS") {
      return new Response(null, {
        status: 204,
        headers: {
          "Access-Control-Allow-Origin": "*",
          "Access-Control-Allow-Methods": "POST, OPTIONS",
          "Access-Control-Allow-Headers": "Content-Type",
        },
      });
    }

    if (request.method === "POST" && url.pathname === "/notify") {
      return handleNotify(request, env);
    }

    if (request.method === "POST" && url.pathname === "/webhook") {
      return handleTelegramWebhook(request, env);
    }

    return new Response(JSON.stringify({ status: "WhatStat Feedback Worker running" }), {
      status: 200,
      headers: { "Content-Type": "application/json" },
    });
  },
};

async function handleNotify(request: Request, env: Env): Promise<Response> {
  try {
    const payload = (await request.json()) as NotifyPayload;
    const isDebug = payload.isDebug === true;

    if (!payload.feedbackId || !payload.message || !payload.userId) {
      return new Response(JSON.stringify({ error: "Missing required fields" }), {
        status: 400,
        headers: { "Content-Type": "application/json" },
      });
    }

    if (payload.message.length > 2000) {
      return new Response(JSON.stringify({ error: "Message too long (max 2000 chars)" }), {
        status: 400,
        headers: { "Content-Type": "application/json" },
      });
    }

    const clientIp = request.headers.get("CF-Connecting-IP") || "unknown";

    // Rate limiting se KV e' configurato (solo per release)
    if (env.FEEDBACK_KV && !isDebug) {
      const userKey = `rate:release:user:${payload.userId}`;
      const ipKey = `rate:release:ip:${clientIp}`;

      const [userBlocked, ipBlocked] = await Promise.all([
        env.FEEDBACK_KV.get(userKey),
        env.FEEDBACK_KV.get(ipKey),
      ]);

      if (userBlocked) {
        return new Response(
          JSON.stringify({ error: "Rate limit: max 1 request every 60 minutes per user" }),
          { status: 429, headers: { "Content-Type": "application/json" } }
        );
      }

      if (ipBlocked) {
        return new Response(
          JSON.stringify({ error: "Rate limit: max 1 request every 30 minutes per IP" }),
          { status: 429, headers: { "Content-Type": "application/json" } }
        );
      }

      // Imposta TTL: 3600s (60 min) per user, 1800s (30 min) per IP
      await Promise.all([
        env.FEEDBACK_KV.put(userKey, "1", { expirationTtl: 3600 }),
        env.FEEDBACK_KV.put(ipKey, "1", { expirationTtl: 1800 }),
      ]);
    }

    // Invia messaggio Telegram
    if (env.TELEGRAM_BOT_TOKEN && env.TELEGRAM_CHAT_ID) {
      const typeBadge =
        payload.type === "BUG" ? "🚨 <b>BUG</b>" : payload.type === "FEATURE" ? "💡 <b>PROPOSTA</b>" : "📩 <b>ALTRO</b>";
      const debugBadge = isDebug ? " 🛠 <code>[DEBUG]</code>" : "";

      const text = `${typeBadge}${debugBadge} <code>#${escapeHtml(payload.feedbackId)}</code>\n` +
        `👤 <b>Utente</b>: <code>${escapeHtml(payload.userId)}</code>\n` +
        `📱 <b>Device</b>: ${escapeHtml(payload.deviceModel)} (Android ${payload.androidVersion})\n` +
        `📦 <b>Versione</b>: ${escapeHtml(payload.appVersion)} | <b>Mode</b>: ${escapeHtml(payload.mode)}\n` +
        `━━━━━━━━━━━━━━━━━━\n` +
        `${escapeHtml(payload.message)}`;

      const tgRes = await fetch(
        `https://api.telegram.org/bot${env.TELEGRAM_BOT_TOKEN}/sendMessage`,
        {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            chat_id: env.TELEGRAM_CHAT_ID,
            text: text,
            parse_mode: "HTML",
          }),
        }
      );

      const tgData = (await tgRes.json()) as { ok: boolean; result?: { message_id: number } };
      const messageId = tgData.result?.message_id;

      // Aggiorna Firestore con telegramMessageId se service account presente
      if (messageId && env.FIREBASE_SERVICE_ACCOUNT_KEY) {
        await updateFirestoreDoc(env.FIREBASE_SERVICE_ACCOUNT_KEY, payload.feedbackId, {
          telegramMessageId: { integerValue: String(messageId) },
        });
      }

      return new Response(JSON.stringify({ ok: true, messageId }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      });
    }

    return new Response(JSON.stringify({ ok: true, note: "Telegram not configured yet" }), {
      status: 200,
      headers: { "Content-Type": "application/json" },
    });
  } catch (err: any) {
    return new Response(JSON.stringify({ error: err.message || "Internal server error" }), {
      status: 500,
      headers: { "Content-Type": "application/json" },
    });
  }
}

async function handleTelegramWebhook(request: Request, env: Env): Promise<Response> {
  try {
    const update = (await request.json()) as any;
    const msg = update.message;

    if (!msg || !msg.reply_to_message || !msg.text) {
      return new Response("OK", { status: 200 });
    }

    const originalText = msg.reply_to_message.text || msg.reply_to_message.caption || "";
    
    // Cerca pattern: #fb_123_456, fb_123_456, o fb con cifre
    let feedbackId: string | null = null;
    const match = originalText.match(/#?`?(fb_[0-9a-zA-Z_]+)`?/) ||
                  originalText.match(/#?`?(fb\d+_\d+)`?/) ||
                  originalText.match(/#?`?(fb_?\d+)`?/);

    if (match) {
      feedbackId = match[1];
      if (!feedbackId.startsWith("fb_")) {
        feedbackId = "fb_" + feedbackId.replace(/^fb/, "");
      }
    }

    if (!feedbackId) {
      if (env.TELEGRAM_BOT_TOKEN) {
        await sendTelegramMessage(
          env.TELEGRAM_BOT_TOKEN,
          msg.chat.id,
          `⚠️ Non sono riuscito a trovare l'ID del feedback (#fb_...) nel messaggio citato.\nTesto ricevuto:\n"${originalText.substring(0, 150)}"`,
          msg.message_id
        );
      }
      return new Response("OK", { status: 200 });
    }
    const replyText = msg.text.trim();
    const now = Date.now();

    if (!env.FIREBASE_SERVICE_ACCOUNT_KEY) {
      if (env.TELEGRAM_BOT_TOKEN) {
        await sendTelegramMessage(
          env.TELEGRAM_BOT_TOKEN,
          msg.chat.id,
          "⚠️ Secret `FIREBASE_SERVICE_ACCOUNT_KEY` mancante nel Worker. Impostalo con `wrangler secret put FIREBASE_SERVICE_ACCOUNT_KEY`.",
          msg.message_id
        );
      }
      return new Response("OK", { status: 200 });
    }

    try {
      await updateFirestoreDoc(env.FIREBASE_SERVICE_ACCOUNT_KEY, feedbackId, {
        replyMessage: { stringValue: replyText },
        repliedAt: { integerValue: String(now) },
        status: { stringValue: "REPLIED" },
        readByDevice: { booleanValue: false },
      });

      if (env.TELEGRAM_BOT_TOKEN) {
        await sendTelegramMessage(
          env.TELEGRAM_BOT_TOKEN,
          msg.chat.id,
          `✅ Risposta registrata per \`#${feedbackId}\`!`,
          msg.message_id
        );
      }

      // Invia notifica push istantanea tramite Firebase Cloud Messaging (FCM v1)
      try {
        const doc = await getFirestoreDoc(env.FIREBASE_SERVICE_ACCOUNT_KEY, feedbackId);
        const fcmToken = doc?.fields?.fcmToken?.stringValue;
        if (fcmToken) {
          await sendFcmPush(env.FIREBASE_SERVICE_ACCOUNT_KEY, fcmToken, feedbackId);
        }
      } catch (fcmErr: any) {
        console.error("FCM send error:", fcmErr?.message || fcmErr);
      }
    } catch (fsErr: any) {
      if (env.TELEGRAM_BOT_TOKEN) {
        await sendTelegramMessage(
          env.TELEGRAM_BOT_TOKEN,
          msg.chat.id,
          `❌ Errore aggiornamento Firestore per \`#${feedbackId}\`: ${fsErr.message}`,
          msg.message_id
        );
      }
    }

    return new Response("OK", { status: 200 });
  } catch (err: any) {
    return new Response(`Webhook error: ${err.message}`, { status: 500 });
  }
}

async function sendTelegramMessage(
  token: string,
  chatId: number | string,
  text: string,
  replyToMessageId?: number
): Promise<void> {
  await fetch(`https://api.telegram.org/bot${token}/sendMessage`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      chat_id: chatId,
      text: text,
      parse_mode: "HTML",
      ...(replyToMessageId ? { reply_to_message_id: replyToMessageId } : {}),
    }),
  });
}

async function getFirestoreDoc(
  serviceAccountJson: string,
  docId: string
): Promise<any> {
  const creds = JSON.parse(serviceAccountJson);
  const token = await getGoogleAuthToken(creds);
  const projectId = creds.project_id;
  const firestoreUrl = `https://firestore.googleapis.com/v1/projects/${projectId}/databases/(default)/documents/feedbacks/${docId}`;

  const res = await fetch(firestoreUrl, {
    method: "GET",
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
    },
  });

  if (!res.ok) {
    return null;
  }
  return await res.json();
}

async function sendFcmPush(
  serviceAccountJson: string,
  fcmToken: string,
  feedbackId: string
): Promise<void> {
  const creds = JSON.parse(serviceAccountJson);
  const token = await getGoogleAuthToken(creds);
  const projectId = creds.project_id;
  const fcmUrl = `https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`;

  const payload = {
    message: {
      token: fcmToken,
      notification: {
        title: "Risposta dallo sviluppatore",
        body: "Lo sviluppatore ha risposto al tuo feedback. Tocca per vedere.",
      },
      data: {
        extra_open_feedback: "true",
        feedback_id: feedbackId,
      },
      android: {
        priority: "HIGH",
        notification: {
          channel_id: "feedback_reply_channel",
          notification_priority: "PRIORITY_HIGH",
          default_sound: true,
          default_vibrate_timings: true,
        },
      },
    },
  };

  const res = await fetch(fcmUrl, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify(payload),
  });

  if (!res.ok) {
    const errorText = await res.text();
    console.error(`FCM HTTP ${res.status}: ${errorText}`);
  }
}

async function updateFirestoreDoc(
  serviceAccountJson: string,
  docId: string,
  fields: Record<string, any>
): Promise<void> {
  const creds = JSON.parse(serviceAccountJson);
  const token = await getGoogleAuthToken(creds);
  const projectId = creds.project_id;

  const maskParams = Object.keys(fields)
    .map((k) => `updateMask.fieldPaths=${encodeURIComponent(k)}`)
    .join("&");

  const firestoreUrl = `https://firestore.googleapis.com/v1/projects/${projectId}/databases/(default)/documents/feedbacks/${docId}?${maskParams}`;

  const res = await fetch(firestoreUrl, {
    method: "PATCH",
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ fields }),
  });

  if (!res.ok) {
    const errorText = await res.text();
    throw new Error(`Firestore REST API HTTP ${res.status}: ${errorText}`);
  }
}

async function getGoogleAuthToken(creds: any): Promise<string> {
  const iat = Math.floor(Date.now() / 1000);
  const exp = iat + 3600;

  const header = { alg: "RS256", typ: "JWT" };
  const claimSet = {
    iss: creds.client_email,
    scope: "https://www.googleapis.com/auth/datastore https://www.googleapis.com/auth/firebase.messaging",
    aud: "https://oauth2.googleapis.com/token",
    exp: exp,
    iat: iat,
  };

  const encHeader = base64UrlEncode(JSON.stringify(header));
  const encClaims = base64UrlEncode(JSON.stringify(claimSet));
  const unsignedToken = `${encHeader}.${encClaims}`;

  const signature = await signRsa(unsignedToken, creds.private_key);
  const signedJwt = `${unsignedToken}.${signature}`;

  const res = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: `grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=${signedJwt}`,
  });

  const data = (await res.json()) as { access_token: string };
  return data.access_token;
}

async function signRsa(data: string, pemKey: string): Promise<string> {
  const pemHeader = "-----BEGIN PRIVATE KEY-----";
  const pemFooter = "-----END PRIVATE KEY-----";
  const pemContents = pemKey
    .replace(pemHeader, "")
    .replace(pemFooter, "")
    .replace(/\s/g, "");

  const binaryDer = Uint8Array.from(atob(pemContents), (c) => c.charCodeAt(0));

  const cryptoKey = await crypto.subtle.importKey(
    "pkcs8",
    binaryDer.buffer,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"]
  );

  const encoder = new TextEncoder();
  const signature = await crypto.subtle.sign(
    "RSASSA-PKCS1-v1_5",
    cryptoKey,
    encoder.encode(data)
  );

  return base64UrlEncodeArray(new Uint8Array(signature));
}

function base64UrlEncode(str: string): string {
  return btoa(str).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function base64UrlEncodeArray(arr: Uint8Array): string {
  let binary = "";
  for (let i = 0; i < arr.byteLength; i++) {
    binary += String.fromCharCode(arr[i]);
  }
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function escapeHtml(text: string): string {
  return text
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;");
}
