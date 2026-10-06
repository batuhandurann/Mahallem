import { strict as assert } from "node:assert";
import { test } from "node:test";
import { createHmac } from "node:crypto";
import { createPaytrCallbackHash, verifyPaytrCallback, createPaytrIframeToken } from "../lib/payments/paytr.js";

test("PayTR callback hash verifies valid payload", () => {
  const key = "key";
  const salt = "salt";
  const oid = "ORDER-123";
  const status = "success";
  const total = "10000";
  const expected = createHmac("sha256", key).update(oid + salt + status + total).digest("base64");
  assert.equal(createPaytrCallbackHash(key, oid, salt, status, total), expected);
  assert.equal(verifyPaytrCallback(key, oid, salt, status, total, expected), true);
  assert.equal(verifyPaytrCallback(key, oid, salt, status, "999", expected), false);
});
test("PayTR iframe token signing matches the documented HMAC input", () => {
  const merchantId = "123456";
  const userIp = "203.0.113.10";
  const merchantOid = "MHL202610061234";
  const email = "user@example.com";
  const amountMinor = "159900";
  const basket = Buffer.from(JSON.stringify([["Hizmet", "1599.00", 1]])).toString("base64");
  const noInstallment = "0";
  const maxInstallment = "0";
  const currency = "TL";
  const testMode = "1";
  const salt = "salt";
  const key = "key";

  const expected = createHmac("sha256", key)
    .update(
      merchantId
      + userIp
      + merchantOid
      + email
      + amountMinor
      + basket
      + noInstallment
      + maxInstallment
      + currency
      + testMode
      + salt
    )
    .digest("base64");

  assert.equal(
    createPaytrIframeToken(
      merchantId,
      userIp,
      merchantOid,
      email,
      amountMinor,
      basket,
      noInstallment,
      maxInstallment,
      currency,
      testMode,
      salt,
      key
    ),
    expected
  );
});
