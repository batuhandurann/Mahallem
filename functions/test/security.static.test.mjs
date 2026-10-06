import assert from "node:assert/strict";
import { readFileSync } from "node:fs";

const source = readFileSync(new URL("../src/index.ts", import.meta.url), "utf8");
const rules = readFileSync(new URL("../../firestore.rules", import.meta.url), "utf8");
const storage = readFileSync(new URL("../../storage.rules", import.meta.url), "utf8");

assert.match(source, /setGlobalOptions\(\{[^}]*maxInstances:\s*20[^}]*concurrency:\s*40/s);
assert.equal((source.match(/export const [A-Za-z0-9_]+ = onCall\(/g) || []).length, 12);
assert.equal((source.match(/enforceAppCheck:\s*true/g) || []).length, 12);

assert.match(source, /hashDeviceToken/);
assert.match(source, /hourlyRateLimitRef/);
assert.match(source, /parseTryAmountMinor\(price\) !== amountMinor/);
assert.match(source, /status === "PURGING"/);
assert.match(source, /Account purge failed; account remains locked in PURGING for retry/);
assert.doesNotMatch(source, /Account purge failed; returning account to REQUESTED/);

assert.match(rules, /match \/publicProviders\/\{providerId\}/);
assert.match(rules, /match \/publicJobRequests\/\{requestId\}/);
assert.match(rules, /allow list: if false;/);

assert.match(storage, /safeImagePath/);
assert.match(storage, /\\\\.jpg/);

console.log("Security static regression tests passed.");
