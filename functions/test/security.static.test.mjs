import assert from "node:assert/strict";
import { readFileSync } from "node:fs";

const source = readFileSync(new URL("../src/index.ts", import.meta.url), "utf8");
const rules = readFileSync(new URL("../../firestore.rules", import.meta.url), "utf8");
const storage = readFileSync(new URL("../../storage.rules", import.meta.url), "utf8");

assert.match(source, /setGlobalOptions\(\{[^}]*maxInstances:\s*20[^}]*concurrency:\s*40/s);
assert.equal((source.match(/export const [A-Za-z0-9_]+ = onCall\(/g) || []).length, 15);
assert.equal((source.match(/enforceAppCheck:\s*true/g) || []).length, 15);

assert.match(source, /hashDeviceToken/);
assert.match(source, /deviceTokenOwners/);
assert.match(source, /adminAuth\.getUser\(uid\)/);
assert.match(source, /bucket\.deleteFiles/);
assert.match(source, /users.*devices/);
assert.match(source, /String\(jobRequest\.status \?\? ""\) === "CLOSED"/);
assert.match(source, /requireRecentAuthentication\(request.auth.token.auth_time\)/);
assert.match(source, /validateUploadedImage/);
assert.match(source, /validated: true/);
assert.match(source, /issueImageUploadGrant/);
assert.match(source, /saveProviderListing/);
assert.match(source, /saveJobRequest/);
assert.match(source, /hourlyRateLimitRef/);
assert.match(source, /parseTryAmountMinor\(price\) !== amountMinor/);
assert.match(source, /status === "PURGING"/);
assert.match(source, /Account purge failed; account remains locked in PURGING for retry/);
assert.doesNotMatch(source, /Account purge failed; returning account to REQUESTED/);

assert.match(rules, /match \/publicProviders\/\{providerId\}/);
assert.match(rules, /match \/publicJobRequests\/\{requestId\}/);
assert.match(rules, /allow list: if false;/);

assert.match(storage, /validGrant/);
assert.match(storage, /data.validated == true/);
assert.match(storage, /\.jpg/);

console.log("Security static regression tests passed.");

assert.match(source, /body:\s*"Yeni bir mesajınız var\."\s*,/);
