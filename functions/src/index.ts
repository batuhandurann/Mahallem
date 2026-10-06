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
    const amountMinor = Number(data.amountMinor ?? 0);

    if (!requestId || !quoteId || !Number.isInteger(amountMinor) || amountMinor <= 0) {
      throw new HttpsError("invalid-argument", "Geçersiz ödeme parametreleri.");
    }

    const paymentRef = db.collection("payments").doc();
    await paymentRef.set({
      id: paymentRef.id,
      requestId,
      quoteId,
      customerId: request.auth.uid,
      amountMinor,
      currency: "TRY",
      provider: "paytr-marketplace",
      status: "PENDING_PROVIDER",
      createdAt: FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp(),
    });

    // PayTR Marketplace credentials/application must be approved and configured
    // before a live checkout token can be created. We deliberately fail closed.
    if (!paytrKey.value() || !paytrSalt.value()) {
      throw new HttpsError(
        "failed-precondition",
        "Ödeme sağlayıcısı staging/production ortamında henüz yapılandırılmadı."
      );
    }

    throw new HttpsError(
      "unimplemented",
      "PayTR Marketplace checkout token generation is pending merchant integration."
    );
  }
);

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
