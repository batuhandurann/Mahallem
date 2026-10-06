import { strict as assert } from "node:assert";
import { test } from "node:test";
import { createHmac } from "node:crypto";
import { createPaytrCallbackHash, verifyPaytrCallback } from "../lib/payments/paytr.js";

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