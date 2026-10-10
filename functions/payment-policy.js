"use strict";

class PaymentError extends Error {
  constructor(code, message) { super(message); this.name = "PaymentError"; this.code = code; }
}
function fail(code, message) { throw new PaymentError(code, message); }
function id(value) {
  if (typeof value !== "string" || !/^[^/\u0000-\u001f]{1,128}$/.test(value))
    fail("invalid-argument", "Ödeme kaydı geçersiz.");
  return value;
}
function checkoutCommand(input) {
  if (!input || typeof input !== "object" || Array.isArray(input)
    || Object.keys(input).length !== 1 || !Object.hasOwn(input, "requestId"))
    fail("invalid-argument", "Yalnızca iş kimliği gönderilebilir.");
  return { requestId: id(input.requestId) };
}
// Mirror the existing Turkish quote format exactly. Never use parseFloat on a
// display price: "1.250,50 TL" is 125050 kuruş, not 1.25 lira.
function quoteMinor(value) {
  if (typeof value !== "string" || value.length > 32
    || !/^(?:0|[1-9][0-9]{0,8}|[1-9][0-9]{0,2}(?:\.[0-9]{3}){1,2})(?:,[0-9]{1,2})?(?:\s*(?:TL|₺))?$/i.test(value.trim()))
    fail("failed-precondition", "Kabul edilen teklif tutarı geçersiz.");
  const [whole, fraction = ""] = value.trim().replace(/\s*(?:TL|₺)$/i, "").replaceAll(".", "").split(",");
  const minor = Number(whole) * 100 + Number(fraction.padEnd(2, "0"));
  if (!Number.isSafeInteger(minor) || minor < 1 || minor > 99999999999)
    fail("failed-precondition", "Kabul edilen teklif tutarı geçersiz.");
  return minor;
}
function decimalMinor(value) {
  const text = decimalSignature(value);
  if (typeof text !== "string" || !/^(0|[1-9][0-9]{0,8})(?:\.[0-9]{1,2})?$/.test(text))
    fail("data-loss", "Ödeme yanıtındaki tutar doğrulanamadı.");
  const [whole, fraction = ""] = text.split(".");
  return Number(whole) * 100 + Number(fraction.padEnd(2, "0"));
}
// iyzico signs monetary response fields after removing fractional trailing zeroes.
// Keep this textual: no binary floating-point rounding of provider money.
function decimalSignature(value) {
  const text = typeof value === "number" ? String(value) : value;
  if (typeof text !== "string" || !/^(0|[1-9][0-9]{0,8})(?:\.[0-9]{1,12})?$/.test(text))
    fail("data-loss", "Ödeme yanıtındaki tutar doğrulanamadı.");
  return text.includes(".") ? text.replace(/0+$/, "").replace(/\.$/, "") : text;
}
function decimal(minor) {
  if (!Number.isSafeInteger(minor) || minor < 1 || minor > 99999999999)
    fail("failed-precondition", "Ödeme tutarı geçersiz.");
  return `${Math.floor(minor / 100)}.${String(minor % 100).padStart(2, "0")}`;
}
function authoritativePayment({ requestId, listing, quote, actorUid }) {
  id(requestId); id(actorUid);
  if (!listing || listing.ownerUid !== actorUid) fail("permission-denied", "Bu iş için ödeme yetkiniz yok.");
  if (!quote || quote.status !== "ACCEPTED" || quote.requestId !== requestId
    || quote.customerUid !== actorUid || quote.providerUid !== listing.acceptedProviderUid
    || quote.providerUid === actorUid || !listing.acceptedQuoteId
    || !["ACCEPTED", "IN_PROGRESS", "AWAITING_CONFIRMATION"].includes(listing.data?.status)
    || listing.data?.escrowStatus !== "NONE" || listing.data?.escrowAmount !== ""
    || quote.data?.escrowFunded !== false)
    fail("failed-precondition", "İş ve kabul edilen teklif ödeme için uygun değil.");
  return { requestId, quoteId: id(listing.acceptedQuoteId), customerUid: actorUid,
    providerUid: id(quote.providerUid), amountMinor: quoteMinor(quote.data?.price), currency: "TRY" };
}
function assertSameAttempt(record, authority) {
  if (!["requestId", "quoteId", "customerUid", "providerUid", "amountMinor", "currency"]
    .every(key => record[key] === authority[key]))
    fail("failed-precondition", "Ödeme denemesi güncel işle eşleşmiyor. Destek ile iletişime geçin.");
}
// A verified paid result is monotonic. A different transaction ID is a conflict,
// not a new charge. The caller must apply this inside a Firestore transaction.
function nextPaymentState(current, verified) {
  if (!["PAID", "REVIEW", "FAILED"].includes(verified?.status))
    fail("data-loss", "Ödeme sonucu doğrulanamadı.");
  if (current.providerPaymentId && current.providerPaymentId !== verified.providerPaymentId)
    fail("data-loss", "Ödeme işlem kimliği değişti.");
  if (current.status === "PAID") return current;
  return { ...current, ...verified };
}
module.exports = { PaymentError, fail, id, checkoutCommand, quoteMinor, decimalMinor, decimalSignature, decimal,
  authoritativePayment, assertSameAttempt, nextPaymentState };
