import { createHash } from "node:crypto";
import { initializeApp } from "firebase-admin/app";
import { getFirestore, FieldValue } from "firebase-admin/firestore";
import { getMessaging } from "firebase-admin/messaging";
import { onDocumentCreated } from "firebase-functions/v2/firestore";
import { onCall, onRequest, HttpsError } from "firebase-functions/v2/https";
import { defineSecret } from "firebase-functions/params";
import { logger } from "firebase-functions";
import {
  verifyPaytrCallback,
} from "./payments/paytr";

initializeApp();

const db = getFirestore();
const paytrKey = defineSecret("PAYTR_MERCHANT_KEY");
const paytrSalt = defineSecret("PAYTR_MERCHANT_SALT");

export const createPaymentIntent = onCall(
  { region: "europe-west1", enforceAppCheck: true, secrets: [paytrKey, paytrSalt] },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Kimlik doğrulaması gerekli.");
    }

    const data = request.data as Record<string, unknown>;
    const requestId = String(data.requestId ?? "");
    const quoteId = String(data.quoteId ?? "");
    const idempotencyKey = String(data.idempotencyKey ?? "");

    if (!requestId || !quoteId || !/^[A-Za-z0-9._:-]{16,128}$/.test(idempotencyKey)) {
      throw new HttpsError("invalid-argument", "Geçersiz ödeme parametreleri.");
    }

    const quoteSnap = await db.collection("quotes").doc(quoteId).get();
    if (!quoteSnap.exists) {
      throw new HttpsError("not-found", "Teklif bulunamadı.");
    }
    const quote = quoteSnap.data()!;
    if (String(quote.requestId ?? "") !== requestId) {
      throw new HttpsError("failed-precondition", "Teklif ve talep eşleşmiyor.");
    }
    if (String(quote.customerId ?? "") !== request.auth.uid) {
      throw new HttpsError("permission-denied", "Bu ödeme yalnızca talep sahibine aittir.");
    }
    if (String(quote.status ?? "") !== "ACCEPTED") {
      throw new HttpsError("failed-precondition", "Ödeme için teklif kabul edilmiş olmalı.");
    }

    const amountMinor = Number(quote.amountMinor ?? 0);
    if (!Number.isSafeInteger(amountMinor) || amountMinor <= 0) {
      throw new HttpsError("failed-precondition", "Teklifin güvenilir ödeme tutarı bulunmuyor.");
    }

    const idemHash = createHash("sha256")
      .update(request.auth.uid + ":" + idempotencyKey)
      .digest("hex");
    const paymentRef = db.collection("payments").doc(idemHash);
    const existing = await paymentRef.get();
    if (existing.exists) {
      return existing.data();
    }

    const payment = {
      id: paymentRef.id, requestId, quoteId,
      customerId: request.auth.uid,
      providerId: String(quote.providerOwnerId ?? ""),
      amountMinor, currency: "TRY",
      provider: "paytr-marketplace",
      status: "CREATED",
      idempotencyKeyHash: idemHash,
      createdAt: FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp(),
    };
    await paymentRef.create(payment);

    if (!paytrKey.value() || !paytrSalt.value()) {
      throw new HttpsError(
        "failed-precondition",
        "Ödeme sağlayıcısı staging/production ortamında henüz yapılandırılmadı."
      );
    }

    // PayTR Marketplace checkout generation requires the merchant-approved
    // marketplace account configuration. Fail closed until it is configured.
    throw new HttpsError(
      "unimplemented",
      "PayTR Marketplace checkout token generation is pending merchant configuration."
    );
  }
);

export const sendMessage = onCall(
  { region: "europe-west1", enforceAppCheck: true },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Kimlik doğrulaması gerekli.");
    }

    const data = request.data as Record<string, unknown>;
    const conversationId = String(data.conversationId ?? "");
    const text = String(data.text ?? "");
    const messageType = String(data.messageType ?? "TEXT");
    const attachmentUrl = data.attachmentUrl == null ? null : String(data.attachmentUrl);

    if (!conversationId) {
      throw new HttpsError("invalid-argument", "conversationId gerekli.");
    }
    if (!["TEXT", "OFFER", "VOICE", "IMAGE"].includes(messageType)) {
      throw new HttpsError("invalid-argument", "Geçersiz mesaj tipi.");
    }
    if (text.length > 2000) {
      throw new HttpsError("invalid-argument", "Mesaj en fazla 2000 karakter olabilir.");
    }
    if (!text.trim() && !attachmentUrl) {
      throw new HttpsError("invalid-argument", "Mesaj içeriği boş olamaz.");
    }
    if (attachmentUrl && attachmentUrl.length > 2048) {
      throw new HttpsError("invalid-argument", "Ek bağlantısı çok uzun.");
    }
    if (attachmentUrl
      && !attachmentUrl.startsWith("gs://")
      && !attachmentUrl.startsWith("https://firebasestorage.googleapis.com/")) {
      throw new HttpsError("invalid-argument", "Geçersiz medya bağlantısı.");
    }

    const conversationRef = db.collection("conversations").doc(conversationId);
    const rateLimitRef = db.collection("rateLimits").doc(
      "message:" + request.auth.uid
    );

    const messageRef = db.collection("messages").doc();
    const now = Date.now();

    await db.runTransaction(async (tx) => {
      const conversationSnap = await tx.get(conversationRef);
      const rateLimitSnap = await tx.get(rateLimitRef);

      if (!conversationSnap.exists) {
        throw new HttpsError("not-found", "Sohbet bulunamadı.");
      }

      const participants = conversationSnap.data()?.participantIds;
      if (!Array.isArray(participants) || !participants.includes(request.auth!.uid)) {
        throw new HttpsError("permission-denied", "Bu sohbete mesaj gönderemezsiniz.");
      }

      const rate = rateLimitSnap.exists ? rateLimitSnap.data()! : {};
      const windowStart = Number(rate.windowStartMs ?? 0);
      const count = Number(rate.count ?? 0);
      const windowMs = 60_000;

      const activeWindow = Number.isSafeInteger(windowStart)
        && now - windowStart < windowMs;

      if (activeWindow && count >= 30) {
        throw new HttpsError("resource-exhausted", "Çok fazla mesaj gönderildi. Lütfen biraz sonra tekrar deneyin.");
      }

      tx.set(rateLimitRef, {
        windowStartMs: activeWindow ? windowStart : now,
        count: activeWindow ? count + 1 : 1,
        updatedAt: FieldValue.serverTimestamp(),
      }, { merge: true });

      tx.create(messageRef, {
        conversationId,
        senderId: request.auth!.uid,
        text,
        attachmentUrl,
        messageType,
        createdAt: FieldValue.serverTimestamp(),
      });

      tx.update(conversationRef, {
        lastMessageAt: FieldValue.serverTimestamp(),
        lastMessagePreview: messageType === "IMAGE" ? "📷 Fotoğraf" : text.slice(0, 200),
        updatedAt: FieldValue.serverTimestamp(),
      });
    });

    return { messageId: messageRef.id };
  }
);

export const startConversation = onCall(
  { region: "europe-west1", enforceAppCheck: true },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Kimlik doğrulaması gerekli.");
    }

    const data = request.data as Record<string, unknown>;
    const targetId = String(data.targetId ?? "");
    const relatedItemId = String(data.relatedItemId ?? "");
    const relatedItemTitle = String(data.relatedItemTitle ?? "");
    const isRequestConversation = targetId === "request-owner";

    if (isRequestConversation && !relatedItemId) {
      throw new HttpsError("invalid-argument", "Talep sohbeti için requestId gerekli.");
    }
    if (!isRequestConversation && !targetId) {
      throw new HttpsError("invalid-argument", "Geçerli bir sohbet hedefi gerekli.");
    }

    let participantUid = "";

    if (isRequestConversation) {
      const requestSnap = await db.collection("jobRequests").doc(relatedItemId).get();
      if (!requestSnap.exists) {
        throw new HttpsError("not-found", "Talep bulunamadı.");
      }

      participantUid = String(requestSnap.data()?.ownerId ?? "");
      if (!participantUid || participantUid === request.auth.uid) {
        throw new HttpsError("permission-denied", "Talep sahibiyle geçerli bir sohbet oluşturulamadı.");
      }

      const status = String(requestSnap.data()?.status ?? "");
      if (!["PENDING", "QUOTED", "ACCEPTED"].includes(status)) {
        throw new HttpsError("failed-precondition", "Bu talep artık sohbet başlatılabilir durumda değil.");
      }

      const providerQuery = await db.collection("providers")
        .where("ownerId", "==", request.auth.uid)
        .limit(1)
        .get();

      if (providerQuery.empty) {
        throw new HttpsError("permission-denied", "Bu talep için sohbet başlatma yetkiniz yok.");
      }
    } else {
      const providerSnap = await db.collection("providers").doc(targetId).get();
      if (!providerSnap.exists) {
        throw new HttpsError("not-found", "Hizmet sağlayıcı bulunamadı.");
      }

      participantUid = String(providerSnap.data()?.ownerId ?? "");
      if (!participantUid || participantUid === request.auth.uid) {
        throw new HttpsError("failed-precondition", "Geçerli bir hizmet sağlayıcı bulunamadı.");
      }

      if (relatedItemId !== targetId) {
        throw new HttpsError("invalid-argument", "Hizmet sağlayıcı sohbet bağlantısı geçersiz.");
      }
    }

    const participantIds = [request.auth.uid, participantUid].sort();
    const conversationId = createHash("sha256")
      .update(participantIds.join(":") + ":" + relatedItemId)
      .digest("hex");

    const ref = db.collection("conversations").doc(conversationId);
    const rateLimitRef = db.collection("rateLimits").doc("conversation:" + request.auth.uid);
    const now = Date.now();

    await db.runTransaction(async (tx) => {
      const existing = await tx.get(ref);
      if (existing.exists) return;

      const rateLimitSnap = await tx.get(rateLimitRef);
      const rate = rateLimitSnap.exists ? rateLimitSnap.data()! : {};
      const windowStart = Number(rate.windowStartMs ?? 0);
      const count = Number(rate.count ?? 0);
      const activeWindow = Number.isSafeInteger(windowStart) && now - windowStart < 60_000;

      if (activeWindow && count >= 10) {
        throw new HttpsError("resource-exhausted", "Çok fazla yeni sohbet başlatıldı. Lütfen biraz sonra tekrar deneyin.");
      }

      tx.set(rateLimitRef, {
        windowStartMs: activeWindow ? windowStart : now,
        count: activeWindow ? count + 1 : 1,
        updatedAt: FieldValue.serverTimestamp(),
      }, { merge: true });

      tx.create(ref, {
        participantIds,
        relatedItemId,
        relatedItemTitle,
        createdAt: FieldValue.serverTimestamp(),
        updatedAt: FieldValue.serverTimestamp(),
      });
    });

    return { conversationId };
  }
);

export const sendMessage = onCall(
  { region: "europe-west1", enforceAppCheck: true },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Kimlik doğrulaması gerekli.");
    }

    const data = request.data as Record<string, unknown>;
    const conversationId = String(data.conversationId ?? "");
    const text = String(data.text ?? "");
    const messageType = String(data.messageType ?? "TEXT");
    const attachmentUrl = data.attachmentUrl == null ? null : String(data.attachmentUrl);

    if (!conversationId) {
      throw new HttpsError("invalid-argument", "conversationId gerekli.");
    }
    if (!["TEXT", "OFFER", "VOICE", "IMAGE"].includes(messageType)) {
      throw new HttpsError("invalid-argument", "Geçersiz mesaj tipi.");
    }
    if (text.length > 2000) {
      throw new HttpsError("invalid-argument", "Mesaj en fazla 2000 karakter olabilir.");
    }
    if (!text.trim() && !attachmentUrl) {
      throw new HttpsError("invalid-argument", "Mesaj içeriği boş olamaz.");
    }
    if (attachmentUrl && attachmentUrl.length > 2048) {
      throw new HttpsError("invalid-argument", "Ek bağlantısı çok uzun.");
    }

    const conversationRef = db.collection("conversations").doc(conversationId);
    const rateLimitRef = db.collection("rateLimits").doc(
      "message:" + request.auth.uid
    );

    const messageRef = db.collection("messages").doc();
    const now = Date.now();

    await db.runTransaction(async (tx) => {
      const [conversationSnap, rateLimitSnap] = await Promise.all([
        tx.get(conversationRef),
        tx.get(rateLimitRef),
      ]);

      if (!conversationSnap.exists) {
        throw new HttpsError("not-found", "Sohbet bulunamadı.");
      }

      const participants = conversationSnap.data()?.participantIds;
      if (!Array.isArray(participants) || !participants.includes(request.auth!.uid)) {
        throw new HttpsError("permission-denied", "Bu sohbete mesaj gönderemezsiniz.");
      }

      const rate = rateLimitSnap.exists ? rateLimitSnap.data()! : {};
      const windowStart = Number(rate.windowStartMs ?? 0);
      const count = Number(rate.count ?? 0);
      const windowMs = 60_000;

      const activeWindow = Number.isSafeInteger(windowStart)
        && now - windowStart < windowMs;

      if (activeWindow && count >= 30) {
        throw new HttpsError("resource-exhausted", "Çok fazla mesaj gönderildi. Lütfen biraz sonra tekrar deneyin.");
      }

      tx.set(rateLimitRef, {
        windowStartMs: activeWindow ? windowStart : now,
        count: activeWindow ? count + 1 : 1,
        updatedAt: FieldValue.serverTimestamp(),
      }, { merge: true });

      tx.create(messageRef, {
        conversationId,
        senderId: request.auth!.uid,
        text,
        attachmentUrl,
        messageType,
        createdAt: FieldValue.serverTimestamp(),
      });

      tx.update(conversationRef, {
        lastMessageAt: FieldValue.serverTimestamp(),
        lastMessagePreview: messageType === "IMAGE" ? "📷 Fotoğraf" : text.slice(0, 200),
        updatedAt: FieldValue.serverTimestamp(),
      });
    });

    return { messageId: messageRef.id };
  }
);

export const startConversation = onCall(
  { region: "europe-west1", enforceAppCheck: true },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Kimlik doğrulaması gerekli.");
    }

    const data = request.data as Record<string, unknown>;
    const targetId = String(data.targetId ?? "");
    const relatedItemId = String(data.relatedItemId ?? "");
    const relatedItemTitle = String(data.relatedItemTitle ?? "");

    if (!targetId || !relatedItemId && targetId === request.auth.uid) {
      throw new HttpsError("invalid-argument", "Geçerli bir sohbet hedefi gerekli.");
    }

    let participantUid = "";
    const providerSnap = await db.collection("providers").doc(targetId).get();

    if (providerSnap.exists) {
      participantUid = String(providerSnap.data()?.ownerId ?? "");
      if (!participantUid || participantUid === request.auth.uid) {
        throw new HttpsError("failed-precondition", "Geçerli bir hizmet sağlayıcı bulunamadı.");
      }
      if (relatedItemId && relatedItemId !== targetId) {
        throw new HttpsError("invalid-argument", "Hizmet sağlayıcı sohbet bağlantısı geçersiz.");
      }
    } else {
      if (!relatedItemId) {
        throw new HttpsError("permission-denied", "Doğrudan kullanıcılar arası sohbet desteklenmiyor.");
      }

      const requestSnap = await db.collection("jobRequests").doc(relatedItemId).get();
      if (!requestSnap.exists || requestSnap.data()?.ownerId !== targetId) {
        throw new HttpsError("permission-denied", "Talep ve sohbet katılımcısı eşleşmiyor.");
      }

      const status = String(requestSnap.data()?.status ?? "");
      if (!["PENDING", "QUOTED", "ACCEPTED"].includes(status)) {
        throw new HttpsError("failed-precondition", "Bu talep artık sohbet başlatılabilir durumda değil.");
      }

      const providerQuery = await db.collection("providers")
        .where("ownerId", "==", request.auth.uid)
        .limit(1)
        .get();

      if (providerQuery.empty) {
        throw new HttpsError("permission-denied", "Bu talep için sohbet başlatma yetkiniz yok.");
      }

      participantUid = targetId;
    }

    if (!participantUid || participantUid === request.auth.uid) {
      throw new HttpsError("failed-precondition", "Geçerli bir sohbet katılımcısı bulunamadı.");
    }

    const participantIds = [request.auth.uid, participantUid].sort();
    const conversationId = createHash("sha256")
      .update(participantIds.join(":") + ":" + relatedItemId)
      .digest("hex");

    const ref = db.collection("conversations").doc(conversationId);
    await db.runTransaction(async (tx) => {
      const existing = await tx.get(ref);
      if (existing.exists) return;

      tx.create(ref, {
        participantIds,
        relatedItemId,
        relatedItemTitle,
        createdAt: FieldValue.serverTimestamp(),
        updatedAt: FieldValue.serverTimestamp(),
      });
    });

    return { conversationId };
  }
);

export const sendMessage = onCall(
  { region: "europe-west1", enforceAppCheck: true },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Kimlik doğrulaması gerekli.");
    }

    const data = request.data as Record<string, unknown>;
    const conversationId = String(data.conversationId ?? "");
    const text = String(data.text ?? "");
    const messageType = String(data.messageType ?? "TEXT");
    const attachmentUrl = data.attachmentUrl == null ? null : String(data.attachmentUrl);

    if (!conversationId) {
      throw new HttpsError("invalid-argument", "conversationId gerekli.");
    }
    if (!["TEXT", "OFFER", "VOICE", "IMAGE"].includes(messageType)) {
      throw new HttpsError("invalid-argument", "Geçersiz mesaj tipi.");
    }
    if (text.length > 2000) {
      throw new HttpsError("invalid-argument", "Mesaj en fazla 2000 karakter olabilir.");
    }
    if (!text.trim() && !attachmentUrl) {
      throw new HttpsError("invalid-argument", "Mesaj içeriği boş olamaz.");
    }
    if (attachmentUrl && attachmentUrl.length > 2048) {
      throw new HttpsError("invalid-argument", "Ek bağlantısı çok uzun.");
    }

    const conversationRef = db.collection("conversations").doc(conversationId);
    const rateLimitRef = db.collection("rateLimits").doc(
      "message:" + request.auth.uid
    );

    const messageRef = db.collection("messages").doc();
    const now = Date.now();

    await db.runTransaction(async (tx) => {
      const [conversationSnap, rateLimitSnap] = await Promise.all([
        tx.get(conversationRef),
        tx.get(rateLimitRef),
      ]);

      if (!conversationSnap.exists) {
        throw new HttpsError("not-found", "Sohbet bulunamadı.");
      }

      const participants = conversationSnap.data()?.participantIds;
      if (!Array.isArray(participants) || !participants.includes(request.auth!.uid)) {
        throw new HttpsError("permission-denied", "Bu sohbete mesaj gönderemezsiniz.");
      }

      const rate = rateLimitSnap.exists ? rateLimitSnap.data()! : {};
      const windowStart = Number(rate.windowStartMs ?? 0);
      const count = Number(rate.count ?? 0);
      const windowMs = 60_000;

      const activeWindow = Number.isSafeInteger(windowStart)
        && now - windowStart < windowMs;

      if (activeWindow && count >= 30) {
        throw new HttpsError("resource-exhausted", "Çok fazla mesaj gönderildi. Lütfen biraz sonra tekrar deneyin.");
      }

      tx.set(rateLimitRef, {
        windowStartMs: activeWindow ? windowStart : now,
        count: activeWindow ? count + 1 : 1,
        updatedAt: FieldValue.serverTimestamp(),
      }, { merge: true });

      tx.create(messageRef, {
        conversationId,
        senderId: request.auth!.uid,
        text,
        attachmentUrl,
        messageType,
        createdAt: FieldValue.serverTimestamp(),
      });

      tx.update(conversationRef, {
        lastMessageAt: FieldValue.serverTimestamp(),
        lastMessagePreview: messageType === "IMAGE" ? "📷 Fotoğraf" : text.slice(0, 200),
        updatedAt: FieldValue.serverTimestamp(),
      });
    });

    return { messageId: messageRef.id };
  }
);

export const startConversation = onCall(
  { region: "europe-west1", enforceAppCheck: true },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Kimlik doğrulaması gerekli.");
    }

    const data = request.data as Record<string, unknown>;
    const targetId = String(data.targetId ?? "");
    const relatedItemId = String(data.relatedItemId ?? "");
    const relatedItemTitle = String(data.relatedItemTitle ?? "");

    if (!targetId || targetId === request.auth.uid) {
      throw new HttpsError("invalid-argument", "Geçerli bir sohbet hedefi gerekli.");
    }

    let participantUid = targetId;
    const userSnap = await db.collection("users").doc(targetId).get();
    if (!userSnap.exists) {
      const providerSnap = await db.collection("providers").doc(targetId).get();
      if (!providerSnap.exists) {
        throw new HttpsError("not-found", "Sohbet hedefi bulunamadı.");
      }
      participantUid = String(providerSnap.data()?.ownerId ?? "");
    }

    if (!participantUid || participantUid === request.auth.uid) {
      throw new HttpsError("failed-precondition", "Geçerli bir sohbet katılımcısı bulunamadı.");
    }

    const existing = await db.collection("conversations")
      .where("participantIds", "array-contains", request.auth.uid)
      .get();

    const match = existing.docs.find((doc) => {
      const ids = doc.data().participantIds;
      return Array.isArray(ids)
        && ids.length === 2
        && ids.includes(participantUid)
        && (!relatedItemId || String(doc.data().relatedItemId ?? "") === relatedItemId);
    });

    if (match) {
      return { conversationId: match.id };
    }

    const ref = db.collection("conversations").doc();
    await ref.create({
      participantIds: [request.auth.uid, participantUid],
      relatedItemId,
      relatedItemTitle,
      createdAt: FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp(),
    });

    return { conversationId: ref.id };
  }
);

export const acceptQuote = onCall({ region: "europe-west1", enforceAppCheck: true }, async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Kimlik doğrulaması gerekli.");
  const quoteId = String((request.data as Record<string, unknown>).quoteId ?? "");
  if (!quoteId) throw new HttpsError("invalid-argument", "quoteId gerekli.");
  const quoteRef = db.collection("quotes").doc(quoteId);
  let acceptedRequestId = "";

  await db.runTransaction(async (tx) => {
    const quoteSnap = await tx.get(quoteRef);
    if (!quoteSnap.exists) throw new HttpsError("not-found", "Teklif bulunamadı.");
    const quote = quoteSnap.data()!;
    if (quote.customerId !== request.auth!.uid) {
      throw new HttpsError("permission-denied", "Bu teklifi yalnızca talep sahibi kabul edebilir.");
    }
    if (quote.status !== "PENDING") {
      throw new HttpsError("failed-precondition", "Teklif artık beklemede değil.");
    }

    acceptedRequestId = String(quote.requestId ?? "");
    if (!acceptedRequestId) {
      throw new HttpsError("failed-precondition", "Teklif talep bağlantısı eksik.");
    }

    const requestRef = db.collection("jobRequests").doc(acceptedRequestId);
    const requestSnap = await tx.get(requestRef);
    if (!requestSnap.exists || requestSnap.data()?.ownerId !== request.auth!.uid) {
      throw new HttpsError("permission-denied", "Talep doğrulanamadı.");
    }
    if (requestSnap.data()?.status !== "PENDING") {
      throw new HttpsError("failed-precondition", "Talep artık beklemede değil.");
    }

    tx.update(quoteRef, { status: "ACCEPTED", updatedAt: FieldValue.serverTimestamp() });
    tx.update(requestRef, { status: "ACCEPTED", updatedAt: FieldValue.serverTimestamp() });
  });

  const alternatives = await db.collection("quotes").where("requestId", "==", acceptedRequestId).get();
  const batch = db.batch();
  for (const doc of alternatives.docs) {
    if (doc.id !== quoteId && doc.data().status === "PENDING") {
      batch.update(doc.ref, { status: "REJECTED", updatedAt: FieldValue.serverTimestamp() });
    }
  }
  if (!alternatives.empty) await batch.commit();

  return { accepted: true, quoteId };
});

export const rejectQuote = onCall({ region: "europe-west1", enforceAppCheck: true }, async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Kimlik doğrulaması gerekli.");
  const quoteId = String((request.data as Record<string, unknown>).quoteId ?? "");
  if (!quoteId) throw new HttpsError("invalid-argument", "quoteId gerekli.");
  const quoteRef = db.collection("quotes").doc(quoteId);

  await db.runTransaction(async (tx) => {
    const quoteSnap = await tx.get(quoteRef);
    if (!quoteSnap.exists) throw new HttpsError("not-found", "Teklif bulunamadı.");
    const quote = quoteSnap.data()!;
    if (quote.customerId !== request.auth!.uid) {
      throw new HttpsError("permission-denied", "Bu işlemi yalnızca talep sahibi reddedebilir.");
    }
    if (quote.status !== "PENDING") {
      throw new HttpsError("failed-precondition", "Teklif artık beklemede değil.");
    }
    tx.update(quoteRef, { status: "REJECTED", updatedAt: FieldValue.serverTimestamp() });
  });

  return { rejected: true, quoteId };
});

export const releaseEscrowPayment = onCall({ region: "europe-west1", enforceAppCheck: true }, async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Kimlik doğrulaması gerekli.");
  const paymentId = String((request.data as Record<string, unknown>).paymentId ?? "");
  if (!paymentId) throw new HttpsError("invalid-argument", "paymentId gerekli.");
  const paymentRef = db.collection("payments").doc(paymentId);

  await db.runTransaction(async (tx) => {
    const snap = await tx.get(paymentRef);
    if (!snap.exists) throw new HttpsError("not-found", "Ödeme bulunamadı.");
    const payment = snap.data()!;
    if (payment.customerId !== request.auth!.uid) {
      throw new HttpsError("permission-denied", "Bu ödemeyi yalnızca müşteri serbest bırakabilir.");
    }
    if (!["PAID", "HELD"].includes(String(payment.status ?? ""))) {
      throw new HttpsError("failed-precondition", "Ödeme serbest bırakılabilir durumda değil.");
    }
    tx.update(paymentRef, { status: "RELEASE_REQUESTED", updatedAt: FieldValue.serverTimestamp() });
  });

  return { accepted: true, paymentId, status: "RELEASE_REQUESTED" };
});

export const requestRefund = onCall(
  { region: "europe-west1", enforceAppCheck: true },
  async (request) => {
    if (!request.auth) throw new HttpsError("unauthenticated", "Kimlik doğrulaması gerekli.");
    const paymentId = String((request.data as Record<string, unknown>).paymentId ?? "");
    if (!paymentId) throw new HttpsError("invalid-argument", "paymentId gerekli.");
    const paymentRef = db.collection("payments").doc(paymentId);

    await db.runTransaction(async (tx) => {
      const snap = await tx.get(paymentRef);
      if (!snap.exists) throw new HttpsError("not-found", "Ödeme bulunamadı.");
      const payment = snap.data()!;
      const isAdmin = request.auth!.token.admin === true;
      if (!isAdmin && payment.customerId !== request.auth!.uid) {
        throw new HttpsError("permission-denied", "Bu ödeme için iade talebi açamazsınız.");
      }
      if (!["PAID", "HELD", "RELEASE_REQUESTED"].includes(String(payment.status ?? ""))) {
        throw new HttpsError("failed-precondition", "Ödeme iade için uygun durumda değil.");
      }
      tx.update(paymentRef, {
        status: "REFUND_REQUESTED",
        refundRequestedBy: request.auth!.uid,
        updatedAt: FieldValue.serverTimestamp(),
      });
    });

    return { accepted: true, paymentId, status: "REFUND_REQUESTED" };
  }
);

export const paytrWebhook = onRequest(
  { region: "europe-west1", secrets: [paytrKey, paytrSalt] },
  async (req, res) => {
    if (req.method !== "POST") {
      res.status(405).send("");
      return;
    }

    const merchantOid = String(req.body?.merchant_oid ?? "");
    const status = String(req.body?.status ?? "");
    const totalAmount = String(req.body?.total_amount ?? "");
    const receivedHash = String(req.body?.hash ?? "");

    if (!merchantOid || !["success", "failed"].includes(status) || !/^\d+$/.test(totalAmount)) {
      res.status(400).send("");
      return;
    }

    const valid = verifyPaytrCallback(
      paytrKey.value(),
      merchantOid,
      paytrSalt.value(),
      status,
      totalAmount,
      receivedHash
    );

    if (!valid) {
      logger.warn("Rejected PayTR webhook with invalid signature", { merchantOid });
      res.status(400).send("");
      return;
    }

    const receivedTotalMinor = Number(totalAmount);
    if (!Number.isSafeInteger(receivedTotalMinor) || receivedTotalMinor <= 0) {
      res.status(400).send("");
      return;
    }

    const paymentQuery = await db
      .collection("payments")
      .where("providerOrderId", "==", merchantOid)
      .limit(1)
      .get();

    if (!paymentQuery.empty) {
      const paymentRef = paymentQuery.docs[0].ref;

      await db.runTransaction(async (tx) => {
        const paymentSnap = await tx.get(paymentRef);
        if (!paymentSnap.exists) return;

        const payment = paymentSnap.data()!;
        if (payment.webhookProcessedAt) return;

        const expectedMinor = Number(payment.amountMinor ?? 0);
        if (!Number.isSafeInteger(expectedMinor) || expectedMinor <= 0) {
          logger.error("PayTR webhook ignored: invalid stored payment amount", { merchantOid });
          return;
        }

        if (receivedTotalMinor < expectedMinor) {
          logger.error("PayTR webhook ignored: callback amount below order amount", {
            merchantOid,
            expectedMinor,
            receivedTotalMinor,
          });
          return;
        }

        const currentStatus = String(payment.status ?? "");
        const terminalNonSuccess = ["PAID", "HELD", "RELEASE_REQUESTED", "REFUND_REQUESTED"];
        if (status !== "success" && terminalNonSuccess.includes(currentStatus)) {
          return;
        }

        tx.set(
          paymentRef,
          {
            status: status === "success" ? "PAID" : "FAILED",
            providerStatus: status,
            providerTotalMinor: receivedTotalMinor,
            webhookProcessedAt: FieldValue.serverTimestamp(),
            updatedAt: FieldValue.serverTimestamp(),
          },
          { merge: true }
        );
      });
    }

    res.status(200).send("OK");
  }
);

export const notifyNewMessage = onDocumentCreated(
  { document: "messages/{messageId}", region: "europe-west1" },
  async (event) => {
    const message = event.data?.data();
    const conversationId = String(message.conversationId ?? "");
    const senderId = String(message.senderId ?? "");
    if (!conversationId || !senderId) return;

    const conversationSnap = await db.collection("conversations").doc(conversationId).get();
    if (!conversationSnap.exists) return;

    const participantIds = conversationSnap.data()?.participantIds;
    if (!Array.isArray(participantIds)) return;

    const recipientIds = participantIds.filter((id: unknown) =>
      typeof id === "string" && id !== senderId
    ) as string[];

    if (recipientIds.length === 0) return;

    const tokenDocs = await Promise.all(
      recipientIds.map((uid) => db.collection("users").doc(uid).collection("devices").get())
    );

    const tokens = tokenDocs.flatMap((snap) =>
      snap.docs.map((doc) => doc.id)
    );

    if (tokens.length === 0) return;

    const payload = {
      notification: {
        title: "Mahallem'den yeni mesaj",
        body: String(message.text ?? "Yeni bir mesajınız var."),
      },
      data: {
        conversationId: String(message.conversationId ?? ""),
      },
    };

    for (let i = 0; i < tokens.length; i += 500) {
      const batchTokens = tokens.slice(i, i + 500);
      const response = await getMessaging().sendEachForMulticast({
        tokens: batchTokens,
        ...payload,
      });

      const invalidTokenDocs = response.responses
        .map((result, index) => ({
          result,
          token: batchTokens[index],
        }))
        .filter(({ result }) =>
          result.error?.code === "messaging/registration-token-not-registered"
          || result.error?.code === "messaging/invalid-registration-token"
        )
        .map(({ token }) =>
          tokenDocs
            .flatMap((snap) => snap.docs)
            .find((doc) => doc.id === token)
        )
        .filter(Boolean);

      await Promise.all(invalidTokenDocs.map((doc) => doc!.ref.delete()));
    }
  }
);
