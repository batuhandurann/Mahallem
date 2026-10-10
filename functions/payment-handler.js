"use strict";
const crypto = require("node:crypto");
const { FieldValue, Timestamp } = require("firebase-admin/firestore");
const { HttpsError } = require("firebase-functions/v2/https");
const { PaymentError, checkoutCommand, authoritativePayment, assertSameAttempt, nextPaymentState } = require("./payment-policy");
const { createIyzicoProvider } = require("./payment-provider");

// Real collection stays disabled until refund, settlement and dispute authority
// exists. Credentials alone must never turn an unfinished marketplace live.
function sandboxConfiguration(env = process.env) {
  return { environment: "sandbox", merchantEnabled: env.IYZICO_SANDBOX_ENABLED === "true",
    apiKey: env.IYZICO_SANDBOX_API_KEY, secretKey: env.IYZICO_SANDBOX_SECRET_KEY,
    callbackUrl: env.IYZICO_SANDBOX_CALLBACK_URL };
}
function createPaymentHandlers({ db, auth, reserve, configuration = sandboxConfiguration,
  providerFactory = createIyzicoProvider, now = Date.now }) {
  function configured() {
    const config = configuration();
    if (config?.environment !== "sandbox") return null;
    try { providerFactory(config); return config; } catch { return null; }
  }
  async function account(request) {
    if (!request.auth) throw new HttpsError("unauthenticated", "Giriş yapın.");
    const uid = request.auth.uid;
    const user = await auth.getUser(uid).catch(error => {
      if (error.code === "auth/user-not-found") return null;
      throw error;
    });
    const profile = (await db.doc(`users/${uid}`).get()).data();
    if (!user || user.disabled || ["REQUESTED", "PURGING"].includes(profile?.deletionStatus))
      throw new HttpsError("permission-denied", "Hesap etkin değil.");
    return uid;
  }
  function command(request) {
    try { return checkoutCommand(request.data); }
    catch (e) { if (e instanceof PaymentError) throw new HttpsError(e.code, e.message); throw e; }
  }
  function view(record) {
    if (!record) return { status: "NOT_STARTED", environment: configured() ? "sandbox" : "disabled" };
    const ready = record.status === "READY" && record.expiresAt?.toMillis() > now();
    return { status: record.status === "READY" && !ready ? "UNKNOWN" : record.status,
      environment: record.environment, ...(ready ? { paymentPageUrl: record.paymentPageUrl } : {}) };
  }
  async function owner(uid, requestId) {
    const listing = (await db.doc(`requests/${requestId}`).get()).data();
    if (!listing || listing.ownerUid !== uid) throw new HttpsError("permission-denied", "Bu iş için ödeme yetkiniz yok.");
  }
  async function reconcile(ref, record) {
    const config = configured();
    if (!config || config.environment !== record.environment)
      throw new HttpsError("failed-precondition", "Ödeme sağlayıcısı bağlantısı gerekli. Sonuç henüz doğrulanamadı.");
    const result = await providerFactory(config).retrieve({ paymentId: record.paymentId, token: record.token, authority: record });
    return db.runTransaction(async tx => {
      const current = (await tx.get(ref)).data();
      if (!current || current.paymentId !== record.paymentId || current.token !== record.token)
        throw new HttpsError("data-loss", "Ödeme kaydı değişti.");
      const next = nextPaymentState(current, result);
      tx.update(ref, { ...next, updatedAt: FieldValue.serverTimestamp() });
      return next;
    });
  }
  async function translate(action) {
    try { return await action(); }
    catch (e) { if (e instanceof PaymentError) throw new HttpsError(e.code, e.message); throw e; }
  }
  return {
    availability: async request => {
      const uid = await account(request);
      if (!request.data || typeof request.data !== "object" || Array.isArray(request.data) || Object.keys(request.data).length)
        throw new HttpsError("invalid-argument", "Ödeme yapılandırması istemciden gönderilemez.");
      await reserve(uid, "paymentAvailability", 120, 3600);
      const config = configured();
      return { available: !!config, environment: config ? "sandbox" : "disabled" };
    },
    start: request => translate(async () => {
      const uid = await account(request);
      const { requestId } = command(request);
      const config = configured();
      if (!config) throw new HttpsError("failed-precondition", "iyzico test hesabı henüz bağlı değil.");
      await reserve(uid, "paymentStart", 20, 3600);
      const ref = db.doc(`_paymentAttempts/${requestId}`);
      const paymentId = crypto.randomUUID();
      const reserved = await db.runTransaction(async tx => {
        const [listingDoc, existingDoc, profileDoc, buyerDoc] = await Promise.all([
          tx.get(db.doc(`requests/${requestId}`)), tx.get(ref), tx.get(db.doc(`users/${uid}`)), tx.get(db.doc(`_paymentBuyers/${uid}`))]);
        if (["REQUESTED", "PURGING"].includes(profileDoc.data()?.deletionStatus))
          throw new HttpsError("permission-denied", "Hesap etkin değil.");
        const listing = listingDoc.data();
        if (!listing || listing.ownerUid !== uid) throw new HttpsError("permission-denied", "Bu iş için ödeme yetkiniz yok.");
        const quote = listing.acceptedQuoteId ? (await tx.get(db.doc(`quotes/${listing.acceptedQuoteId}`))).data() : null;
        const authority = authoritativePayment({ requestId, listing, quote, actorUid: uid });
        if (existingDoc.exists) { assertSameAttempt(existingDoc.data(), authority); return { existing: existingDoc.data() }; }
        const merchant = (await tx.get(db.doc(`_paymentMerchants/${authority.providerUid}`))).data();
        const buyer = buyerDoc.data();
        if (buyer?.verified !== true || buyer.environment !== "sandbox" || merchant?.verified !== true || merchant.environment !== "sandbox"
          || typeof merchant.subMerchantKey !== "string" || !merchant.subMerchantKey
          || !Number.isSafeInteger(merchant.payoutBasisPoints) || merchant.payoutBasisPoints < 1 || merchant.payoutBasisPoints > 10000)
          throw new HttpsError("failed-precondition", "Doğrulanmış iyzico test müşteri ve hizmet veren kayıtları gerekli.");
        const record = { ...authority, paymentId, environment: "sandbox", status: "INITIALIZING",
          createdAt: FieldValue.serverTimestamp(), updatedAt: FieldValue.serverTimestamp() };
        tx.create(ref, record);
        // Private billing remains outside attempt/status documents.
        return { record, buyer, merchant };
      });
      if (reserved.existing) return view(reserved.existing);
      let initialized;
      try {
        await account(request);
        initialized = await providerFactory(config).initialize({ paymentId, authority: reserved.record,
          buyer: { ...reserved.buyer.buyer, id: uid, ip: request.rawRequest?.ip }, billingAddress: reserved.buyer.billingAddress,
          subMerchantKey: reserved.merchant.subMerchantKey,
          subMerchantAmountMinor: Math.floor(reserved.record.amountMinor * reserved.merchant.payoutBasisPoints / 10000) });
      } catch (error) {
        // UNKNOWN is durable even when the provider call might have succeeded.
        // Never delete this lock or reinitialize after an ambiguous outcome.
        await db.runTransaction(async tx => {
          const current = (await tx.get(ref)).data();
          if (current?.paymentId === paymentId && current.status === "INITIALIZING")
            tx.update(ref, { status: "UNKNOWN", updatedAt: FieldValue.serverTimestamp() });
        });
        throw error;
      }
      const record = await db.runTransaction(async tx => {
        const current = (await tx.get(ref)).data();
        if (!current || current.paymentId !== paymentId || current.status !== "INITIALIZING")
          throw new HttpsError("data-loss", "Ödeme kaydı değişti. Yeni ödeme başlatmayın.");
        const next = { ...current, ...initialized, status: "READY",
          expiresAt: Timestamp.fromMillis(now() + initialized.expiresInSeconds * 1000) };
        tx.update(ref, { ...next, updatedAt: FieldValue.serverTimestamp() });
        tx.create(db.doc(`_paymentTokens/${crypto.createHash("sha256").update(initialized.token).digest("hex")}`), { requestId, paymentId });
        return next;
      });
      await account(request);
      return view(record);
    }),
    status: request => translate(async () => {
      const uid = await account(request);
      const { requestId } = command(request);
      await reserve(uid, "paymentStatus", 120, 3600);
      await owner(uid, requestId);
      const ref = db.doc(`_paymentAttempts/${requestId}`);
      let record = (await ref.get()).data();
      if (record && record.customerUid !== uid) throw new HttpsError("permission-denied", "Ödeme erişimi reddedildi.");
      if (record?.token && !["PAID", "FAILED"].includes(record.status)) record = await reconcile(ref, record);
      await account(request);
      return view(record);
    }),
    callback: async (request, response) => {
      // Callback is an untrusted wake-up signal. A signed authenticated provider
      // retrieve is the only authority; no browser POST may set payment status.
      const token = request.body?.token;
      response.set("Cache-Control", "no-store").set("X-Content-Type-Options", "nosniff");
      if (request.method !== "POST" || typeof token !== "string" || !/^[a-zA-Z0-9_-]{8,256}$/.test(token)) {
        response.status(400).send("Geçersiz ödeme bildirimi."); return;
      }
      try {
        const index = (await db.doc(`_paymentTokens/${crypto.createHash("sha256").update(token).digest("hex")}`).get()).data();
        if (!index) { response.status(400).send("Ödeme bildirimi doğrulanamadı."); return; }
        const ref = db.doc(`_paymentAttempts/${index.requestId}`);
        const record = (await ref.get()).data();
        if (!record || record.paymentId !== index.paymentId || record.token !== token) throw new Error();
        if (!["PAID", "FAILED"].includes(record.status)) await reconcile(ref, record);
        response.set("Cache-Control", "no-store").set("Content-Type", "text/plain; charset=utf-8")
          .status(200).send("Uygulamaya dönüp ödeme sonucunu kontrol edin.");
      } catch {
        response.status(503).send("Sonuç henüz doğrulanamadı. Uygulamadan tekrar kontrol edin.");
      }
    }
  };
}
module.exports = { sandboxConfiguration, createPaymentHandlers };
