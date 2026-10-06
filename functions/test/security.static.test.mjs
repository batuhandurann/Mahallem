import assert from "node:assert/strict";
import { readFileSync } from "node:fs";

const source = readFileSync(new URL("../src/index.ts", import.meta.url), "utf8");
const rules = readFileSync(new URL("../../firestore.rules", import.meta.url), "utf8");
const storage = readFileSync(new URL("../../storage.rules", import.meta.url), "utf8");

assert.match(
  source,
  /setGlobalOptions\(\{[^}]*maxInstances:\s*20[^}]*concurrency:\s*40/s
);

const onCallCount = (source.match(/export const [A-Za-z0-9_]+ = onCall\(/g) || []).length;
const appCheckCount = (source.match(/enforceAppCheck:\s*true/g) || []).length;
assert.equal(onCallCount, 18, "Unexpected callable-function count; review App Check coverage.");
assert.equal(appCheckCount, onCallCount, "Every callable function must enforce App Check.");

assert.match(source, /hashDeviceToken/);
assert.match(source, /deviceTokenOwners/);
assert.match(source, /adminAuth\.getUser\(uid\)/);
assert.match(source, /authUser\.emailVerified/);
assert.match(source, /authUser\.phoneNumber/);
assert.match(source, /if \(!userSnap\.exists\)/);
assert.match(source, /bucket\.deleteFiles/);
assert.match(source, /users.*devices/);
assert.match(source, /String\(jobRequest\.status \?\? ""\) === "CLOSED"/);

assert.match(source, /function requireRecentAuthentication/);
assert.match(source, /Number\.isSafeInteger\(authTime\)/);
assert.match(source, /authTime <= 0/);
assert.match(source, /const noInstallment = "1"/);
assert.match(source, /okUrlParsed\.protocol !== "https:"/);
assert.match(source, /withinClockSkew/);
assert.match(source, /recentEnough/);

assert.match(source, /validateUploadedImage/);
assert.match(source, /participantIds: kind === "CHAT"/);
assert.match(source, /grant\.expiresAt/);
assert.match(source, /expiresMillis > Date\.now\(\)/);
assert.match(source, /validated: true/);

assert.match(source, /reportContent/);
assert.match(source, /updateProviderAvailability/);
assert.match(source, /markConversationRead/);
assert.match(source, /conversationState/);
assert.match(source, /notificationPreferences/);
assert.match(source, /messagesEnabled/);
assert.match(source, /messages !== false/);
assert.match(source, /function isValidIsoDate/);
assert.match(source, /isValidIsoDate\(dateIso\)/);
assert.match(source, /contentReports/);
assert.match(source, /where\("reporterUid", "==", uid\)/);
assert.match(source, /reporterUid: anonymizedId/);
assert.match(source, /report-day:/);

assert.match(source, /hourlyRateLimitRef/);
assert.match(source, /parseTryAmountMinor\(price\) !== amountMinor/);
assert.match(source, /tokenGenerationStartedAt/);
assert.match(source, /next\.size > 366/);
assert.match(source, /Takvimde en fazla 366 gün tutulabilir/);
assert.match(source, /status === "PURGING"/);
assert.match(source, /Account purge failed; account remains locked in PURGING for retry/);
assert.doesNotMatch(source, /Account purge failed; returning account to REQUESTED/);

assert.match(rules, /match \/publicProviders\/\{providerId\}/);
assert.match(rules, /match \/publicJobRequests\/\{requestId\}/);
assert.match(rules, /request\.query\.limit <= 50/);
assert.doesNotMatch(rules, /request\.query\.limit <= 100/);
assert.match(
  rules,
  /jobRequests\/\$\(request\.resource\.data\.requestId\)/
);

assert.match(storage, /validGrant/);
assert.match(storage, /data\.validated == true/);
assert.match(storage, /filenameGrantId\(fileName\)/);
assert.match(storage, /fileName\.matches/);
assert.match(storage, /data\.participantIds/);
assert.doesNotMatch(storage, /documents\/.*\/conversations\//);
assert.doesNotMatch(storage, /documents\/.*\/jobRequests\//);
assert.doesNotMatch(
  storage,
  /match \\/users\/\{uid\}\\/images\/\{grantId\}\.jpg/
);
assert.doesNotMatch(storage, /allow read: if signedIn\(\);/);
assert.match(storage, /Legacy provider-media namespace/);

assert.match(source, /body:\s*"Yeni bir mesajınız var\."\s*,/);

console.log("Security static regression tests passed.");
