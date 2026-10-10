"use strict";
const test = require("node:test");
const assert = require("node:assert/strict");
const crypto = require("node:crypto");
const { checkoutCommand, quoteMinor, authoritativePayment, assertSameAttempt, nextPaymentState } = require("../payment-policy");
const { createIyzicoProvider, authorization, signature, INITIALIZE } = require("../payment-provider");
const authority = { requestId: "request", quoteId: "quote", customerUid: "customer", providerUid: "provider", amountMinor: 125050, currency: "TRY" };
const config = { environment: "sandbox", apiKey: "fixture-api", secretKey: "fixture-secret", merchantEnabled: true, callbackUrl: "https://example.com/callback" };
const buyer = { id: "customer", name: "Test", surname: "Buyer", email: "buyer@example.com", gsmNumber: "+905550000000",
  identityNumber: "11111111111", registrationAddress: "Fixture address", city: "Izmir", country: "Turkey", ip: "192.0.2.1" };
const billingAddress = { contactName: "Test Buyer", address: "Fixture address", city: "Izmir", country: "Turkey" };
const signed = (result, fields) => ({ ...result, signature: crypto.createHmac("sha256", config.secretKey)
  .update(fields.map(key => result[key]).join(":")).digest("hex") });
const initResult = () => signed({ status: "success", conversationId: "attempt", token: "test-token-123", tokenExpireTime: 1800,
  paymentPageUrl: "https://sandbox-api.iyzipay.com/checkoutform/pay/test-token-123" }, ["conversationId", "token"]);
const retrieveResult = (changes = {}) => signed({ status: "success", conversationId: "attempt", token: "test-token-123",
  paymentStatus: "SUCCESS", paymentId: "123", currency: "TRY", basketId: "request", price: 1250.5, paidPrice: 1250.5,
  fraudStatus: 1, itemTransactions: [{ itemId: "quote", price: 1250.5, paymentTransactionId: "456" }], ...changes },
  ["paymentStatus", "paymentId", "currency", "basketId", "conversationId", "paidPrice", "price", "token"]);
const adapter = (result, capture = () => {}) => createIyzicoProvider(config, { fetchImpl: async (url, options) => {
  capture(url, options); return new Response(JSON.stringify(result), { status: 200 });
} });
const initialize = provider => provider.initialize({ paymentId: "attempt", authority, buyer, billingAddress,
  subMerchantKey: "fixture-submerchant", subMerchantAmountMinor: 120000 });
const retrieve = provider => provider.retrieve({ paymentId: "attempt", token: "test-token-123", authority });
test("only the job ID is accepted; client price and success are rejected", () => {
  assert.deepEqual(checkoutCommand({ requestId: "request" }), { requestId: "request" });
  for (const value of [null, [], { requestId: "a/b" }, { requestId: "request", amountMinor: 1 }, { requestId: "request", paid: true }])
    assert.throws(() => checkoutCommand(value));
});
test("Turkish display prices become exact kuruş without float/truncation ambiguity", () => {
  assert.equal(quoteMinor("1.250,50 TL"), 125050); assert.equal(quoteMinor("0,01 ₺"), 1);
  assert.equal(quoteMinor("999.999.999,99"), 99999999999);
  for (const price of ["1.25", "1e3", "-1", "0", "1000000000", "1,001", "1.250,501", "NaN", 100])
    assert.throws(() => quoteMinor(price));
});
test("charge authority requires accepted quote, matching parties and eligible state", () => {
  const input = { requestId: "request", actorUid: "customer", listing: { ownerUid: "customer", acceptedProviderUid: "provider",
    acceptedQuoteId: "quote", data: { status: "ACCEPTED", escrowStatus: "NONE", escrowAmount: "" } },
  quote: { status: "ACCEPTED", requestId: "request", customerUid: "customer", providerUid: "provider", data: { price: "1.250,50 TL", escrowFunded: false } } };
  assert.deepEqual(authoritativePayment(input), authority);
  for (const bad of [{ ...input, actorUid: "provider" }, { ...input, quote: { ...input.quote, status: "PENDING" } },
    { ...input, listing: { ...input.listing, acceptedProviderUid: "other" } },
    { ...input, quote: { ...input.quote, data: { ...input.quote.data, escrowFunded: true } } },
    { ...input, listing: { ...input.listing, data: { ...input.listing.data, status: "CANCELLED" } } }])
    assert.throws(() => authoritativePayment(bad));
});
test("attempt replay binds immutable authority and cannot roll paid backwards", () => {
  assertSameAttempt(authority, authority);
  for (const key of Object.keys(authority)) assert.throws(() => assertSameAttempt({ ...authority, [key]: "changed" }, authority));
  const paid = { status: "PAID", providerPaymentId: "123" };
  assert.deepEqual(nextPaymentState(paid, { status: "REVIEW", providerPaymentId: "123" }), paid);
  assert.throws(() => nextPaymentState(paid, { status: "PAID", providerPaymentId: "456" }));
});
test("credentials, explicit environment, merchant activation and HTTPS callback are mandatory", () => {
  for (const changes of [{ apiKey: "" }, { secretKey: "" }, { environment: "other" }, { merchantEnabled: false },
    { callbackUrl: "http://example.com/callback" }, { callbackUrl: "https://user:secret@example.com" }])
    assert.throws(() => createIyzicoProvider({ ...config, ...changes }));
});
test("authorization signs exact transmitted JSON with unique request randomness", () => {
  const body = '{"locale":"tr"}';
  const expected = crypto.createHmac("sha256", "secret").update("random" + INITIALIZE + body).digest("hex");
  const result = authorization("api", "secret", INITIALIZE, body, "random");
  assert.equal(Buffer.from(result.slice(8), "base64").toString(), `apiKey:api&randomKey:random&signature:${expected}`);
});

test("provider monetary signature removes decimal trailing zeros exactly as documented", async () => {
  const result = retrieveResult({ price: "1250.500000", paidPrice: "1250.50" });
  result.signature = signature([result.paymentStatus, result.paymentId, result.currency, result.basketId,
    result.conversationId, "1250.5", "1250.5", result.token], config.secretKey);
  assert.equal((await retrieve(adapter(result))).status, "PAID");
});
test("initialize sends one authoritative marketplace item, no client card fields, follows no redirect", async () => {
  const result = await initialize(adapter(initResult(), (url, options) => {
    assert.equal(url, "https://sandbox-api.iyzipay.com" + INITIALIZE);
    assert.equal(options.redirect, "error"); assert.ok(options.signal);
    const payload = JSON.parse(options.body);
    assert.equal(payload.price, "1250.50"); assert.equal(payload.paidPrice, "1250.50");
    assert.equal(payload.basketItems[0].subMerchantPrice, "1200.00");
    assert.deepEqual(payload.enabledInstallments, [1]); assert.equal(payload.paymentCard, undefined);
    assert.equal(options.headers.Authorization, authorization(config.apiKey, config.secretKey, INITIALIZE, options.body, options.headers["x-iyzi-rnd"]));
  }));
  assert.deepEqual(result, { token: "test-token-123", paymentPageUrl: initResult().paymentPageUrl, expiresInSeconds: 1800 });
});
test("initialize rejects tampered signatures, wrong binding, malicious and wrong-environment hosted URLs", async () => {
  for (const result of [{ ...initResult(), signature: "0".repeat(64) }, { ...initResult(), conversationId: "other" },
    { ...initResult(), paymentPageUrl: "https://sandbox-api.iyzipay.com.attacker.example/checkoutform/pay" },
    { ...initResult(), paymentPageUrl: "https://api.iyzipay.com/checkoutform/pay" },
    { ...initResult(), paymentPageUrl: "https://sandbox-api.iyzipay.com/other" },
    { ...initResult(), tokenExpireTime: -1 }]) await assert.rejects(() => initialize(adapter(result)), { code: "data-loss" });
});
test("retrieve releases only minimal verified fields; fraud review is not paid", async () => {
  assert.deepEqual(await retrieve(adapter(retrieveResult())), { status: "PAID", providerPaymentId: "123", itemTransactionId: "456", amountMinor: 125050, currency: "TRY" });
  assert.equal((await retrieve(adapter(retrieveResult({ fraudStatus: 0 })))).status, "REVIEW");
  assert.equal((await retrieve(adapter(retrieveResult({ fraudStatus: -1 })))).status, "FAILED");
});
test("even signed retrieve cannot credit another amount, token, quote, currency or basket", async () => {
  for (const changes of [{ price: 1 }, { paidPrice: 1 }, { currency: "USD" }, { token: "different-token" },
    { basketId: "other" }, { conversationId: "other" }, { fraudStatus: 9 },
    { itemTransactions: [{ itemId: "other", price: 1250.5, paymentTransactionId: "456" }] }])
    await assert.rejects(() => retrieve(adapter(retrieveResult(changes))), { code: "data-loss" });
  await assert.rejects(() => retrieve(adapter({ ...retrieveResult(), signature: "0".repeat(64) })), { code: "data-loss" });
});
test("provider timeout/invalid JSON/error is unknown, never a successful or retry-safe failed charge", async () => {
  const provider = createIyzicoProvider(config, { fetchImpl: async () => { throw new Error("secret provider debug"); } });
  await assert.rejects(() => initialize(provider), error => error.code === "unavailable" && !error.message.includes("secret"));
  await assert.rejects(() => retrieve(adapter({ status: "failure", errorMessage: "private" })), { code: "unavailable" });
});
test("submerchant payout and private billing prerequisites are checked before networking", async () => {
  let calls = 0;
  const provider = adapter(initResult(), () => calls++);
  const base = { paymentId: "attempt", authority, buyer, billingAddress, subMerchantKey: "fixture", subMerchantAmountMinor: 120000 };
  for (const change of [{ subMerchantKey: "" }, { subMerchantAmountMinor: 125051 }, { buyer: { ...buyer, id: "attacker" } },
    { buyer: { ...buyer, ip: "arbitrary" } }, { billingAddress: {} }])
    await assert.rejects(() => provider.initialize({ ...base, ...change }), { code: "failed-precondition" });
  assert.equal(calls, 0);
});
