import { createHash } from "node:crypto";
import { initializeApp } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { getFirestore, FieldValue, Timestamp as FirestoreTimestamp } from "firebase-admin/firestore";
import { getMessaging } from "firebase-admin/messaging";
import { onDocumentCreated } from "firebase-functions/v2/firestore";
import { onCall, onRequest, HttpsError } from "firebase-functions/v2/https";
import { defineSecret } from "firebase-functions/params";
import { logger } from "firebase-functions";
import { onSchedule } from "firebase-functions/v2/scheduler";
import {
  verifyPaytrCallback,
} from "./payments/paytr";

initializeApp();

const db = getFirestore();
const adminAuth = getAuth();
const paytrKey = defineSecret("PAYTR_MERCHANT_KEY");
const paytrSalt = defineSecret("PAYTR_MERCHANT_SALT");

async function assertAccountActive(uid: string) {
  const userSnap = await db.collection("users").doc(uid).get();
  if (userSnap.exists && ["REQUESTED", "PURGING"].includes(String(userSnap.data()?.deletionStatus ?? ""))) {
    throw new HttpsError("failed-precondition", "Hesap silme sürecinde olduğu için bu işlem kullanılamaz.");
  }
}

export const createPaymentIntent = onCall(
  { region: "europe-west1", enforceAppCheck: true, secrets: [paytrKey, paytrSalt] },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Kimlik doğrulaması gerekli.");
    }

    await assertAccountActive(request.auth.uid);

    const authTime = Number(request.auth.token.auth_time ?? 0);
    if (!Number.isFinite(authTime) || Date.now() - authTime * 1000 > 15 * 60 * 1000) {
      throw new HttpsError("failed-precondition", "Ödeme işlemi için yakın zamanda yeniden doğrulama gerekli.");
    }

    const data = request.data as Record<string, unknown>;
    const requestId = String(data.requestId ?? "");
    const quoteId = String(data.quoteId ?? "");
    const idempotencyKey = String(data.idempotencyKey ?? "");
    const currency = String(data.currency ?? "TRY").toUpperCase();

    if (!requestId || !quoteId || currency !== "TRY" || !/^[A-Za-z0-9._:-]{16,128}$/.test(idempotencyKey)) {
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

    const requestDoc = await db.collection("jobRequests").doc(requestId).get();
    if (!requestDoc.exists || requestDoc.data()?.ownerId !== request.auth.uid || requestDoc.data()?.status !== "ACCEPTED") {
      throw new HttpsError("failed-precondition", "Talep ödeme için uygun durumda değil.");
    }

    const idemHash = createHash("sha256")
      .update(request.auth.uid + ":" + idempotencyKey)
      .digest("hex");
    const rateLimitRef = db.collection("rateLimits").doc("payment-intent:" + request.auth.uid);
    const paymentRef = db.collection("payments").doc(idemHash);

    const existing = await paymentRef.get();
    if (existing.exists) {
      const existingPayment = existing.data()!;
      if (
        String(existingPayment.requestId ?? "") !== requestId ||
        String(existingPayment.quoteId ?? "") !== quoteId ||
        Number(existingPayment.amountMinor ?? 0) !== amountMinor ||
        String(existingPayment.customerId ?? "") !== request.auth.uid
      ) {
        throw new HttpsError(
          "already-exists",
          "Idempotency anahtarı farklı bir ödeme işlemi için kullanılmış."
        );
      }
      return existingPayment;
    }

    await db.runTransaction(async (tx) => {
      const rateLimitSnap = await tx.get(rateLimitRef);
      const rate = rateLimitSnap.exists ? rateLimitSnap.data()! : {};
      const windowStart = Number(rate.windowStartMs ?? 0);
      const count = Number(rate.count ?? 0);
      const now = Date.now();
      const activeWindow = Number.isSafeInteger(windowStart) && now - windowStart < 60_000;

      if (activeWindow && count >= 5) {
        throw new HttpsError("resource-exhausted", "Çok fazla ödeme denemesi. Lütfen biraz sonra tekrar deneyin.");
      }

      tx.set(rateLimitRef, {
        windowStartMs: activeWindow ? windowStart : now,
        count: activeWindow ? count + 1 : 1,
        updatedAt: FieldValue.serverTimestamp(),
      }, { merge: true });
    });
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
    await assertAccountActive(request.auth.uid);

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
    if (attachmentUrl && attachmentUrl.length > 512) {
      throw new HttpsError("invalid-argument", "Ek dosya yolu çok uzun.");
    }
    if (messageType === "IMAGE" && !attachmentUrl) {
      throw new HttpsError("invalid-argument", "Fotoğraf mesajı için ek dosya gerekli.");
    }
    if (attachmentUrl) {
      const allowedPrefixes = [
        "chatAttachments/" + conversationId + "/" + request.auth.uid + "/",
      ];
      if (!allowedPrefixes.some((prefix) => attachmentUrl.startsWith(prefix))) {
        throw new HttpsError("permission-denied", "Bu medya dosyasına mesajda erişim yetkiniz yok.");
      }
      if (attachmentUrl.includes("..") || attachmentUrl.includes("?") || attachmentUrl.includes("#")) {
        throw new HttpsError("invalid-argument", "Geçersiz medya yolu.");
      }
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
    await assertAccountActive(request.auth.uid);

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

      const provider = providerQuery.docs[0].data();
      if (provider.isOpenForOffers !== true) {
        throw new HttpsError("permission-denied", "Hizmet sağlayıcınız yeni taleplere kapalı.");
      }

      await assertAccountActive(participantUid);
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

export const createQuote = onCall(
  { region: "europe-west1", enforceAppCheck: true },
  async (request) => {
    if (!request.auth) throw new HttpsError("unauthenticated", "Kimlik doğrulaması gerekli.");
    await assertAccountActive(request.auth.uid);

    const data = request.data as Record<string, unknown>;
    const quoteId = String(data.quoteId ?? "");
    const requestId = String(data.requestId ?? "");
    const providerId = String(data.providerId ?? "");
    const price = String(data.price ?? "");
    const durationOrArrival = String(data.durationOrArrival ?? "");
    const notes = String(data.notes ?? "");
    const amountMinor = Number(data.amountMinor ?? 0);

    if (!/^[A-Za-z0-9_-]{1,80}$/.test(quoteId) ||
        !/^[A-Za-z0-9_-]{1,80}$/.test(requestId) ||
        !providerId ||
        !Number.isSafeInteger(amountMinor) || amountMinor <= 0 ||
        price.length > 200 || durationOrArrival.length > 200 || notes.length > 1000) {
      throw new HttpsError("invalid-argument", "Geçersiz teklif verisi.");
    }

    const providerRef = db.collection("providers").doc(providerId);
    const requestRef = db.collection("jobRequests").doc(requestId);
    const quoteRef = db.collection("quotes").doc(quoteId);
    const rateLimitRef = db.collection("rateLimits").doc("quote:" + request.auth.uid);

    await db.runTransaction(async (tx) => {
      const [providerSnap, requestSnap, quoteSnap, rateSnap] = await Promise.all([
        tx.get(providerRef),
        tx.get(requestRef),
        tx.get(quoteRef),
        tx.get(rateLimitRef),
      ]);

      if (!providerSnap.exists || providerSnap.data()?.ownerId !== request.auth!.uid) {
        throw new HttpsError("permission-denied", "Bu hizmet sağlayıcı adına teklif veremezsiniz.");
      }
      if (providerSnap.data()?.isOpenForOffers !== true) {
        throw new HttpsError("failed-precondition", "Hizmet sağlayıcı yeni tekliflere kapalı.");
      }
      if (!requestSnap.exists || requestSnap.data()?.status !== "PENDING") {
        throw new HttpsError("failed-precondition", "Talep artık yeni teklif kabul etmiyor.");
      }

      const customerId = String(requestSnap.data()?.ownerId ?? "");
      if (!customerId || customerId === request.auth.uid) {
        throw new HttpsError("permission-denied", "Geçerli bir müşteri talebi bulunamadı.");
      }

      if (quoteSnap.exists) {
        throw new HttpsError("already-exists", "Bu teklif kimliği zaten kullanılmış.");
      }

      const rate = rateSnap.exists ? rateSnap.data()! : {};
      const windowStart = Number(rate.windowStartMs ?? 0);
      const count = Number(rate.count ?? 0);
      const now = Date.now();
      const activeWindow = Number.isSafeInteger(windowStart) && now - windowStart < 60_000;
      if (activeWindow && count >= 20) {
        throw new HttpsError("resource-exhausted", "Çok fazla teklif gönderildi. Lütfen biraz sonra tekrar deneyin.");
      }

      tx.set(rateLimitRef, {
        windowStartMs: activeWindow ? windowStart : now,
        count: activeWindow ? count + 1 : 1,
        updatedAt: FieldValue.serverTimestamp(),
      }, { merge: true });

      tx.create(quoteRef, {
        providerId,
        providerOwnerId: request.auth!.uid,
        customerId,
        requestId,
        price,
        amountMinor,
        durationOrArrival,
        notes,
        status: "PENDING",
        createdAt: FieldValue.serverTimestamp(),
        updatedAt: FieldValue.serverTimestamp(),
      });
    });

    return { created: true, quoteId };
  }
);

export const acceptQuote = onCall({ region: "europe-west1", enforceAppCheck: true }, async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Kimlik doğrulaması gerekli.");
    await assertAccountActive(request.auth.uid);
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
  const pendingAlternatives = alternatives.docs.filter(
    (doc) => doc.id !== quoteId && doc.data().status === "PENDING"
  );
  await commitInChunks(pendingAlternatives, (batch, doc) => {
    batch.update(doc.ref, {
      status: "REJECTED",
      updatedAt: FieldValue.serverTimestamp(),
    });
  });

  return { accepted: true, quoteId };
});

export const rejectQuote = onCall({ region: "europe-west1", enforceAppCheck: true }, async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Kimlik doğrulaması gerekli.");
    await assertAccountActive(request.auth.uid);
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
    await assertAccountActive(request.auth.uid);
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
    await assertAccountActive(request.auth.uid);
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


const ACCOUNT_DELETION_DELAY_MS = 30 * 24 * 60 * 60 * 1000;

function requireRecentAuthentication(authTimeSeconds: unknown) {
  const authTime = Number(authTimeSeconds ?? 0);
  if (!Number.isFinite(authTime) || Date.now() - authTime * 1000 > 15 * 60 * 1000) {
    throw new HttpsError("failed-precondition", "Bu güvenlik işlemi için yakın zamanda yeniden doğrulama gerekli.");
  }
}

function deletedAccountId(uid: string): string {
  return "deleted:" + createHash("sha256").update(uid).digest("hex").slice(0, 24);
}

async function commitInChunks<T>(
  items: T[],
  apply: (batch: FirebaseFirestore.WriteBatch, item: T) => void,
  chunkSize = 450
): Promise<void> {
  for (let i = 0; i < items.length; i += chunkSize) {
    const batch = db.batch();
    for (const item of items.slice(i, i + chunkSize)) {
      apply(batch, item);
    }
    await batch.commit();
  }
}

async function anonymizeAccount(uid: string): Promise<void> {
  const anonymizedId = deletedAccountId(uid);

  const providerQuery = await db.collection("providers")
    .where("ownerId", "==", uid)
    .get();
  await commitInChunks(providerQuery.docs, (batch, doc) => batch.delete(doc.ref));

  const requestQuery = await db.collection("jobRequests")
    .where("ownerId", "==", uid)
    .get();
  for (const requestDoc of requestQuery.docs) {
    const requestId = requestDoc.id;
    await db.collection("jobRequests").doc(requestId).set({
      ownerId: anonymizedId,
      status: "CLOSED",
      accountDeletedAt: FieldValue.serverTimestamp(),
    }, { merge: true });

    await db.collection("jobRequestPrivate").doc(requestId).delete().catch(() => undefined);
  }

  const customerQuotes = await db.collection("quotes")
    .where("customerId", "==", uid)
    .get();
  const providerQuotes = await db.collection("quotes")
    .where("providerOwnerId", "==", uid)
    .get();
  const quoteDocs = Array.from(
    new Map([...customerQuotes.docs, ...providerQuotes.docs].map((doc) => [doc.id, doc])).values()
  );
  await commitInChunks(quoteDocs, (batch, doc) => {
    const data = doc.data();
    batch.set(doc.ref, {
      ...(data.customerId === uid ? { customerId: anonymizedId } : {}),
      ...(data.providerOwnerId === uid ? { providerOwnerId: anonymizedId } : {}),
      accountDeletedAt: FieldValue.serverTimestamp(),
    }, { merge: true });
  });

  const customerPayments = await db.collection("payments")
    .where("customerId", "==", uid)
    .get();
  const providerPayments = await db.collection("payments")
    .where("providerId", "==", uid)
    .get();
  const paymentDocs = Array.from(
    new Map([...customerPayments.docs, ...providerPayments.docs].map((doc) => [doc.id, doc])).values()
  );
  await commitInChunks(paymentDocs, (batch, doc) => {
    const data = doc.data();
    batch.set(doc.ref, {
      ...(data.customerId === uid ? { customerId: anonymizedId } : {}),
      ...(data.providerId === uid ? { providerId: anonymizedId } : {}),
      accountDeletedAt: FieldValue.serverTimestamp(),
    }, { merge: true });
  });

  const conversations = await db.collection("conversations")
    .where("participantIds", "array-contains", uid)
    .get();
  for (const conversation of conversations.docs) {
    const participantIds = (conversation.data().participantIds as unknown[])
      .map((id) => id === uid ? anonymizedId : id);

    await conversation.ref.set({
      participantIds,
      accountDeletedAt: FieldValue.serverTimestamp(),
    }, { merge: true });

    const messages = await db.collection("messages")
      .where("conversationId", "==", conversation.id)
      .get();
    const ownedMessages = messages.docs.filter((message) => message.data().senderId === uid);
    await commitInChunks(ownedMessages, (batch, message) => {
      batch.set(message.ref, {
        senderId: anonymizedId,
        accountDeletedAt: FieldValue.serverTimestamp(),
      }, { merge: true });
    });
  }

  await db.recursiveDelete(db.collection("users").doc(uid));
  await adminAuth.deleteUser(uid).catch((error: { code?: string }) => {
    if (error.code !== "auth/user-not-found") throw error;
  });
}

export const requestAccountDeletion = onCall(
  { region: "europe-west1", enforceAppCheck: true },
  async (request) => {
    if (!request.auth) throw new HttpsError("unauthenticated", "Kimlik doğrulaması gerekli.");
    requireRecentAuthentication(request.auth.token.auth_time);

    const ref = db.collection("users").doc(request.auth.uid);
    let alreadyRequested = false;

    await db.runTransaction(async (tx) => {
      const snap = await tx.get(ref);
      const status = snap.exists ? String(snap.data()?.deletionStatus ?? "ACTIVE") : "ACTIVE";

      if (status === "REQUESTED") {
        alreadyRequested = true;
        return;
      }
      if (status === "PURGING") {
        throw new HttpsError("failed-precondition", "Hesap silme işlemi devam ediyor.");
      }
      if (status !== "ACTIVE") {
        throw new HttpsError("failed-precondition", "Hesap silme durumu geçersiz.");
      }

      tx.set(ref, {
        deletionStatus: "REQUESTED",
        deletionRequestedAt: FieldValue.serverTimestamp(),
        deletionDueAt: new Date(Date.now() + ACCOUNT_DELETION_DELAY_MS),
        updatedAt: FieldValue.serverTimestamp(),
      }, { merge: true });
    });

    return { requested: true, alreadyRequested, dueInDays: 30 };
  }
);

export const cancelAccountDeletion = onCall(
  { region: "europe-west1", enforceAppCheck: true },
  async (request) => {
    if (!request.auth) throw new HttpsError("unauthenticated", "Kimlik doğrulaması gerekli.");
    requireRecentAuthentication(request.auth.token.auth_time);

    const ref = db.collection("users").doc(request.auth.uid);
    const snap = await ref.get();
    if (!snap.exists || snap.data()?.deletionStatus !== "REQUESTED") {
      return { canceled: true, alreadyActive: true };
    }

    const dueAt = snap.data()?.deletionDueAt as FirestoreTimestamp | Date | undefined;
    const dueMillis = dueAt instanceof Date
      ? dueAt.getTime()
      : dueAt && "toMillis" in dueAt
        ? dueAt.toMillis()
        : Number.POSITIVE_INFINITY;

    if (dueMillis <= Date.now()) {
      throw new HttpsError(
        "failed-precondition",
        "Hesap silme süresi dolduğu için silme işlemi artık iptal edilemez."
      );
    }

    await ref.set({
      deletionStatus: "ACTIVE",
      deletionCanceledAt: FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp(),
    }, { merge: true });

    return { canceled: true };
  }
);

export const purgeDeletedAccounts = onSchedule(
  { schedule: "every day 03:15", region: "europe-west1", timeZone: "Europe/Istanbul" },
  async () => {
    const now = Date.now();
    const snapshot = await db.collection("users")
      .where("deletionStatus", "==", "REQUESTED")
      .limit(20)
      .get();

    for (const doc of snapshot.docs) {
      const ref = doc.ref;
      let shouldPurge = false;

      await db.runTransaction(async (tx) => {
        const fresh = await tx.get(ref);
        if (!fresh.exists || fresh.data()?.deletionStatus !== "REQUESTED") return;

        const dueAt = fresh.data()?.deletionDueAt as FirestoreTimestamp | Date | undefined;
        const dueMillis = dueAt instanceof Date
          ? dueAt.getTime()
          : dueAt && "toMillis" in dueAt
            ? dueAt.toMillis()
            : Number.POSITIVE_INFINITY;

        if (dueMillis <= now) {
          tx.update(ref, {
            deletionStatus: "PURGING",
            purgeStartedAt: FieldValue.serverTimestamp(),
            updatedAt: FieldValue.serverTimestamp(),
          });
          shouldPurge = true;
        }
      });

      if (!shouldPurge) continue;

      try {
        await anonymizeAccount(doc.id);
      } catch (error) {
        logger.error("Account purge failed; returning account to REQUESTED", {
          uid: doc.id,
          error,
        });
        await ref.set({
          deletionStatus: "REQUESTED",
          purgeStartedAt: FieldValue.delete(),
          updatedAt: FieldValue.serverTimestamp(),
        }, { merge: true });
      }
    }
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

        if (receivedTotalMinor !== expectedMinor) {
          logger.error("PayTR webhook ignored: callback amount does not match order amount", {
            merchantOid,
            expectedMinor,
            receivedTotalMinor,
          });
          return;
        }

        const currentStatus = String(payment.status ?? "");
        const callbackEligibleStatuses = ["CREATED", "PENDING"];
        if (!callbackEligibleStatuses.includes(currentStatus)) {
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