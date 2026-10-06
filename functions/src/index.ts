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

export const acceptQuote = onCall({ region: "europe-west1", enforceAppCheck: true }, async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Kimlik doğrulaması gerekli.");
  const quoteId = String((request.data as Record<string, unknown>).quoteId ?? "");
  if (!quoteId) throw new HttpsError("invalid-argument", "quoteId gerekli.");
  const quoteRef = db.collection("quotes").doc(quoteId);
  const quoteSnap = await quoteRef.get();
  if (!quoteSnap.exists) throw new HttpsError("not-found", "Teklif bulunamadı.");
  const quote = quoteSnap.data()!;
  if (quote.customerId !== request.auth.uid) throw new HttpsError("permission-denied", "Bu teklifi yalnızca talep sahibi kabul edebilir.");
  if (quote.status !== "PENDING") throw new HttpsError("failed-precondition", "Teklif artık beklemede değil.");
  const requestRef = db.collection("jobRequests").doc(String(quote.requestId));
  const requestSnap = await requestRef.get();
  if (!requestSnap.exists || requestSnap.data()?.ownerId !== request.auth.uid) throw new HttpsError("permission-denied", "Talep doğrulanamadı.");

  const batch = db.batch();
  batch.update(quoteRef, { status: "ACCEPTED", updatedAt: FieldValue.serverTimestamp() });
  batch.update(requestRef, { status: "ACCEPTED", updatedAt: FieldValue.serverTimestamp() });
  const alternatives = await db.collection("quotes").where("requestId", "==", String(quote.requestId)).get();
  for (const doc of alternatives.docs) {
    if (doc.id !== quoteId && doc.data().status === "PENDING") {
      batch.update(doc.ref, { status: "REJECTED", updatedAt: FieldValue.serverTimestamp() });
    }
  }
  await batch.commit();
  return { accepted: true, quoteId };
});

export const rejectQuote = onCall({ region: "europe-west1", enforceAppCheck: true }, async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Kimlik doğrulaması gerekli.");
  const quoteId = String((request.data as Record<string, unknown>).quoteId ?? "");
  const quoteRef = db.collection("quotes").doc(quoteId);
  const quoteSnap = await quoteRef.get();
  if (!quoteSnap.exists) throw new HttpsError("not-found", "Teklif bulunamadı.");
  const quote = quoteSnap.data()!;
  if (quote.customerId !== request.auth.uid) throw new HttpsError("permission-denied", "Bu işlemi yalnızca talep sahibi yapabilir.");
  if (quote.status !== "PENDING") throw new HttpsError("failed-precondition", "Teklif artık beklemede değil.");
  await quoteRef.update({ status: "REJECTED", updatedAt: FieldValue.serverTimestamp() });
  return { rejected: true, quoteId };
});

export const releaseEscrowPayment = onCall({ region: "europe-west1", enforceAppCheck: true }, async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Kimlik doğrulaması gerekli.");
  const paymentId = String((request.data as Record<string, unknown>).paymentId ?? "");
  if (!paymentId) throw new HttpsError("invalid-argument", "paymentId gerekli.");
  const paymentRef = db.collection("payments").doc(paymentId);
  const snap = await paymentRef.get();
  if (!snap.exists) throw new HttpsError("not-found", "Ödeme bulunamadı.");
  const payment = snap.data()!;
  if (payment.customerId !== request.auth.uid) throw new HttpsError("permission-denied", "Bu ödemeyi yalnızca müşteri serbest bırakabilir.");
  if (!["PAID", "HELD"].includes(String(payment.status ?? ""))) throw new HttpsError("failed-precondition", "Ödeme serbest bırakılabilir durumda değil.");

  // The final provider payout call is intentionally fail-closed until the approved
  // marketplace merchant integration is configured. Firestore never pretends payout happened.
  await paymentRef.update({ status: "RELEASE_REQUESTED", updatedAt: FieldValue.serverTimestamp() });
  return { accepted: true, paymentId, status: "RELEASE_REQUESTED" };
});
export const requestRefund = onCall(
  { region: "europe-west1", enforceAppCheck: true },
  async (request) => {
    if (!request.auth?.token.admin) {
      throw new HttpsError("permission-denied", "İade işlemi yetkili sunucu işlemi.");
    }

    const paymentId = String((request.data as Record<string, unknown>).paymentId ?? "");
    if (!paymentId) {
      throw new HttpsError("invalid-argument", "paymentId gerekli.");
    }

    await db.collection("payments").doc(paymentId).set(
      {
        status: "REFUND_REQUESTED",
        updatedAt: FieldValue.serverTimestamp(),
      },
      { merge: true }
    );
    return { accepted: true, paymentId };
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

    const paymentQuery = await db
      .collection("payments")
      .where("providerOrderId", "==", merchantOid)
      .limit(1)
      .get();

    if (!paymentQuery.empty) {
      const payment = paymentQuery.docs[0];
      if (payment.data().webhookProcessedAt) {
        res.status(200).send("OK");
        return;
      }

      await payment.ref.set(
        {
          status: status === "success" ? "PAID" : "FAILED",
          providerStatus: status,
          providerTotalMinor: totalAmount,
          webhookProcessedAt: FieldValue.serverTimestamp(),
          updatedAt: FieldValue.serverTimestamp(),
        },
        { merge: true }
      );
    }

    res.status(200).send("OK");
  }
);

export const notifyNewMessage = onDocumentCreated(
  { document: "messages/{messageId}", region: "europe-west1" },
  async (event) => {
    const message = event.data?.data();
    if (!message?.participantIds || !Array.isArray(message.participantIds)) return;

    const recipientIds = message.participantIds.filter((id: unknown) =>
      typeof id === "string" && id !== message.senderId
    ) as string[];

    if (recipientIds.length === 0) return;

    const tokenDocs = await Promise.all(
      recipientIds.map((uid) => db.collection("users").doc(uid).collection("devices").get())
    );

    const tokens = tokenDocs.flatMap((snap) =>
      snap.docs.map((doc) => doc.id)
    );

    if (tokens.length === 0) return;

    await getMessaging().sendEachForMulticast({
      tokens,
      notification: {
        title: "Mahallem'den yeni mesaj",
        body: String(message.text ?? "Yeni bir mesajınız var."),
      },
      data: {
        conversationId: String(message.conversationId ?? ""),
      },
    });
  }
);
