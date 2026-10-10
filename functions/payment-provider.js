"use strict";
const crypto = require("node:crypto");
const net = require("node:net");
const { fail, id, decimal, decimalMinor, decimalSignature } = require("./payment-policy");
const INITIALIZE = "/payment/iyzipos/checkoutform/initialize/auth/ecom";
const RETRIEVE = "/payment/iyzipos/checkoutform/auth/ecom/detail";
const ORIGINS = Object.freeze({ sandbox: "https://sandbox-api.iyzipay.com", production: "https://api.iyzipay.com" });
function text(value, maximum = 128) {
  if (typeof value !== "string" || !value.trim() || value.length > maximum || /[\u0000-\u001f]/.test(value))
    fail("failed-precondition", "Ödeme için doğrulanmış fatura ve müşteri bilgileri gerekli.");
  return value;
}
function signature(values, secret) {
  return crypto.createHmac("sha256", secret).update(values.join(":"), "utf8").digest("hex");
}
function verifySignature(values, secret, actual) {
  if (values.some(value => value === null || value === undefined || !["string", "number"].includes(typeof value))
    || typeof actual !== "string" || !/^[a-f0-9]{64}$/i.test(actual)
    || !crypto.timingSafeEqual(Buffer.from(signature(values, secret), "hex"), Buffer.from(actual, "hex")))
    fail("data-loss", "Ödeme sağlayıcı imzası doğrulanamadı.");
}
function authorization(apiKey, secretKey, path, body, random) {
  const digest = crypto.createHmac("sha256", secretKey).update(random + path + body, "utf8").digest("hex");
  return "IYZWSv2 " + Buffer.from(`apiKey:${apiKey}&randomKey:${random}&signature:${digest}`, "utf8").toString("base64");
}
function hostedUrl(value, origin) {
  let url;
  try { url = new URL(value); } catch { fail("data-loss", "Ödeme sayfası doğrulanamadı."); }
  if (value.length > 4096 || url.origin !== origin || url.username || url.password || url.hash
    || !(url.pathname.startsWith("/checkoutform/") || url.pathname === "/payment/checkoutform/initialize/auth/ecom"))
    fail("data-loss", "Ödeme sayfası doğrulanamadı.");
  return url.href;
}
function createIyzicoProvider(config, { fetchImpl = globalThis.fetch } = {}) {
  const origin = ORIGINS[config?.environment];
  if (!origin || config.merchantEnabled !== true || typeof config.apiKey !== "string" || !config.apiKey.trim()
    || typeof config.secretKey !== "string" || !config.secretKey.trim())
    fail("failed-precondition", "Ödeme sağlayıcısı henüz etkinleştirilmedi.");
  let callback;
  try { callback = new URL(config.callbackUrl); } catch { fail("failed-precondition", "Ödeme geri dönüş adresi eksik."); }
  if (callback.protocol !== "https:" || callback.username || callback.password || callback.hash || callback.search)
    fail("failed-precondition", "Ödeme geri dönüş adresi güvenli değil.");

  async function post(path, payload) {
    const body = JSON.stringify(payload);
    const random = crypto.randomBytes(24).toString("hex");
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 15000);
    try {
      const response = await fetchImpl(origin + path, { method: "POST", redirect: "error", signal: controller.signal,
        headers: { "Content-Type": "application/json", "x-iyzi-rnd": random,
          Authorization: authorization(config.apiKey, config.secretKey, path, body, random) }, body });
      if (!response.ok) fail("unavailable", "Ödeme sağlayıcısına ulaşılamadı. Sonucu tekrar kontrol edin.");
      // Bound even chunked responses; provider diagnostics/card details are never logged or returned.
      const reader = response.body?.getReader();
      if (!reader) fail("unavailable", "Ödeme sağlayıcısından yanıt alınamadı.");
      const parts = []; let length = 0;
      for (;;) {
        const { done, value } = await reader.read();
        if (done) break;
        length += value.length;
        if (length > 1024 * 1024) { await reader.cancel(); fail("data-loss", "Ödeme sağlayıcısı yanıtı geçersiz."); }
        parts.push(Buffer.from(value));
      }
      const result = JSON.parse(Buffer.concat(parts).toString("utf8"));
      if (!result || Array.isArray(result) || typeof result !== "object") fail("data-loss", "Ödeme yanıtı geçersiz.");
      // A provider error does not prove a previous ambiguous attempt failed.
      if (result.status !== "success") fail("unavailable", "Ödeme sağlayıcısı işlemi doğrulayamadı.");
      return result;
    } catch (error) {
      if (error?.name === "PaymentError") throw error;
      fail("unavailable", "Ödeme sonucu henüz doğrulanamadı. Yeni ödeme başlatmadan durumu kontrol edin.");
    } finally { clearTimeout(timeout); }
  }
  return {
    async initialize({ paymentId, authority, buyer, billingAddress, subMerchantKey, subMerchantAmountMinor }) {
      id(paymentId);
      if (!authority || authority.currency !== "TRY") fail("failed-precondition", "Ödeme para birimi geçersiz.");
      const amount = decimal(authority.amountMinor);
      const payout = decimal(subMerchantAmountMinor);
      if (subMerchantAmountMinor > authority.amountMinor) fail("failed-precondition", "Hizmet veren ödeme payı geçersiz.");
      text(subMerchantKey, 256);
      if (buyer?.id !== authority.customerUid || !net.isIP(buyer?.ip || "")
        || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(buyer?.email || "")
        || !/^\+[1-9][0-9]{7,14}$/.test(buyer?.gsmNumber || "")
        || !/^[0-9]{11}$/.test(buyer?.identityNumber || ""))
        fail("failed-precondition", "Doğrulanmış ödeme müşteri bilgileri gerekli.");
      const safeBuyer = {};
      for (const field of ["id", "name", "surname", "email", "gsmNumber", "identityNumber", "registrationAddress", "ip", "city", "country"])
        safeBuyer[field] = text(buyer[field], field === "registrationAddress" ? 500 : 128);
      const address = {};
      for (const field of ["contactName", "city", "country", "address"])
        address[field] = text(billingAddress?.[field], field === "address" ? 500 : 128);
      const result = await post(INITIALIZE, { locale: "tr", conversationId: paymentId, price: amount,
        paidPrice: amount, currency: "TRY", basketId: authority.requestId, paymentGroup: "PRODUCT",
        callbackUrl: callback.href, enabledInstallments: [1], buyer: safeBuyer, billingAddress: address,
        basketItems: [{ id: authority.quoteId, name: "Hizmet bedeli", category1: "Hizmet", itemType: "VIRTUAL",
          price: amount, subMerchantKey, subMerchantPrice: payout }] });
      verifySignature([result.conversationId, result.token], config.secretKey, result.signature);
      if (result.conversationId !== paymentId || typeof result.token !== "string" || !/^[a-zA-Z0-9_-]{8,256}$/.test(result.token)
        || !Number.isSafeInteger(result.tokenExpireTime) || result.tokenExpireTime < 1 || result.tokenExpireTime > 86400)
        fail("data-loss", "Ödeme oturumu doğrulanamadı.");
      return { token: result.token, paymentPageUrl: hostedUrl(result.paymentPageUrl, origin), expiresInSeconds: result.tokenExpireTime };
    },
    async retrieve({ paymentId, token, authority }) {
      id(paymentId); text(token, 256);
      const result = await post(RETRIEVE, { locale: "tr", conversationId: paymentId, token });
      verifySignature([result.paymentStatus, result.paymentId, result.currency, result.basketId,
        result.conversationId, decimalSignature(result.paidPrice), decimalSignature(result.price), result.token], config.secretKey, result.signature);
      if (result.conversationId !== paymentId || result.token !== token || result.basketId !== authority.requestId
        || result.currency !== "TRY" || authority.currency !== "TRY"
        || decimalMinor(result.price) !== authority.amountMinor || decimalMinor(result.paidPrice) !== authority.amountMinor
        || typeof result.paymentId !== "string" || !/^[a-zA-Z0-9_-]{1,128}$/.test(result.paymentId))
        fail("data-loss", "Ödeme sonucu iş ve tutarla eşleşmiyor.");
      if (!["SUCCESS", "FAILURE"].includes(result.paymentStatus) || ![1, 0, -1].includes(result.fraudStatus))
        fail("data-loss", "Ödeme sonucu doğrulanamadı.");
      if (result.paymentStatus === "FAILURE" || result.fraudStatus === -1)
        return { status: "FAILED", providerPaymentId: result.paymentId };
      const items = result.itemTransactions;
      if (!Array.isArray(items) || items.length !== 1 || items[0].itemId !== authority.quoteId
        || decimalMinor(items[0].price) !== authority.amountMinor
        || typeof items[0].paymentTransactionId !== "string" || !/^[a-zA-Z0-9_-]{1,128}$/.test(items[0].paymentTransactionId))
        fail("data-loss", "Ödeme kalemi doğrulanamadı.");
      return { status: result.fraudStatus === 1 ? "PAID" : "REVIEW", providerPaymentId: result.paymentId,
        itemTransactionId: items[0].paymentTransactionId, amountMinor: authority.amountMinor, currency: "TRY" };
    }
  };
}
module.exports = { createIyzicoProvider, signature, verifySignature, authorization, hostedUrl, INITIALIZE, RETRIEVE };
