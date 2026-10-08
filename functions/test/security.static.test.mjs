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
assert.equal(onCallCount, 27, "Unexpected callable-function count; review App Check coverage.");
assert.equal(appCheckCount, onCallCount, "Every callable function must enforce App Check.");

assert.match(source, /hashDeviceToken/);
assert.match(source, /deviceTokenOwners/);
assert.match(source, /adminAuth\.getUser\(uid\)/);
assert.match(source, /authUser\.emailVerified/);
assert.match(source, /authUser\.phoneNumber/);
assert.match(source, /if \(!userSnap\.exists\)/);
assert.match(source, /bucket\.deleteFiles/);
assert.match(source, /users.*devices/);
assert.match(source, /String\(jobRequest\.status \?\? ""\) !== "PENDING"/);

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
assert.match(source, /notifyNewQuote/);
assert.match(source, /offersEnabled/);
assert.match(source, /setUserBlock/);
assert.match(source, /setFavorite/);
assert.match(source, /blockedUsers/);
assert.match(source, /cancelJobRequest/);
assert.match(source, /confirmJobCompletion/);
assert.match(source, /createReview/);
assert.match(source, /verifiedTransaction:\s*true/);
assert.match(source, /reviewAudits/);
assert.match(source, /openDispute/);
assert.match(source, /resolveDispute/);
assert.match(source, /getPaymentForQuote/);
assert.match(source, /conversationState/);
assert.match(source, /notificationPreferences/);
assert.match(source, /messagesEnabled/);
assert.match(source, /recipientBlockSnap/);
assert.match(source, /senderBlockSnap/);
assert.match(source, /teklif alışverişi engellendi/);
assert.match(source, /messages !== false/);
assert.match(source, /function isValidIsoDate/);
assert.match(source, /isValidIsoDate\(dateIso\)/);
assert.match(source, /function isValidTime/);
assert.match(source, /isValidTime\(eventTime\)/);
assert.match(source, /contentReports/);
assert.match(source, /where\("reporterUid", "==", uid\)/);
assert.match(source, /String\(existing\.data\(\)\?\.status \?\? ""\) !== "PENDING"/);
assert.match(source, /reporterUid: anonymizedId/);
assert.match(source, /collection\("contentReports"\)\.where\("targetId", "==", uid\)/);
assert.match(source, /targetId: anonymizedId/);
assert.match(source, /collection\("reviewAudits"\)\.where\("providerOwnerId", "==", uid\)/);
assert.match(source, /providerOwnerId: anonymizedId/);
assert.match(source, /collection\("payments"\)\.where\("refundRequestedBy", "==", uid\)/);
assert.match(source, /refundRequestedBy: anonymizedId/);
assert.match(source, /collection\("disputes"\)\.where\("resolvedBy", "==", uid\)/);
assert.match(source, /resolvedBy: anonymizedId/);
assert.doesNotMatch(source, /uid: doc\.id/);
assert.doesNotMatch(source, /Image validation failed", \{ name, error \}/);
assert.match(source, /objectHash: createHash\("sha256"\)\.update\(name\)/);
assert.match(source, /report-day:/);

assert.match(source, /hourlyRateLimitRef/);
assert.match(source, /const consumePaymentAttempt = \(\) =>/);
assert.match(source, /consumePaymentAttempt\(\);/);
assert.match(source, /parseTryAmountMinor\(price\) !== amountMinor/);
assert.match(source, /tokenGenerationStartedAt/);
assert.match(source, /getPaymentStatus/);
assert.match(source, /const isProvider = payment\.providerId === request\.auth\.uid/);
assert.match(source, /const linkedRequestId = String\(payment\.requestId \?\? ""\)/);
assert.match(source, /tx\.get\(db\.collection\("jobRequests"\)\.doc\(linkedRequestId\)\)/);
assert.match(source, /String\(linkedRequestSnap\.data\(\)\?\.status \?\? ""\) === "DISPUTED"/);
assert.match(source, /next\.size > 366/);
assert.match(source, /Takvimde en fazla 366 gün tutulabilir/);
assert.match(source, /status === "PURGING"/);
assert.match(source, /Account purge failed; account remains locked in PURGING for retry/);
assert.doesNotMatch(source, /Account purge failed; returning account to REQUESTED/);

assert.match(
  rules,
  /match \/publicProviders\/\{providerId\} \{[\s\S]*?allow list: if accountActive\(\) && request\.query\.limit <= 50;/
);
assert.match(
  rules,
  /match \/publicJobRequests\/\{requestId\} \{[\s\S]*?allow list: if accountActive\(\) && request\.query\.limit <= 50;/
);
assert.match(rules, /match \/reviews\/\{reviewId\}/);
assert.match(rules, /match \/reviewAudits\/\{reviewId\}/);
assert.match(rules, /match \/disputes\/\{disputeId\}/);
assert.match(rules, /match \/blockedUsers\/\{blockedUid\}/);
assert.match(rules, /match \/favorites\/\{providerId\}/);
assert.match(rules, /\('admin' in request\.auth\.token\)/);
assert.match(rules, /'deletionStatus' in get\(/);

assert.match(storage, /\('admin' in request\.auth\.token\)/);
assert.match(storage, /'deletionStatus' in firestore\.get\(/);
assert.match(storage, /validGrant/);
assert.match(storage, /data\.validated == true/);
assert.match(storage, /filenameGrantId\(fileName\)/);
assert.match(storage, /fileName\.matches/);
assert.ok(storage.includes("fileName.replace('\\\\.jpg$', '')"));
assert.ok(storage.includes("fileName.matches('^[A-Za-z0-9_-]{1,80}\\\\.jpg$')"));
assert.ok(!storage.includes("fileName.replace('\\\\\\\\.jpg$', '')"));
assert.ok(!storage.includes("fileName.matches('^[A-Za-z0-9_-]{1,80}\\\\\\\\.jpg$')"));
assert.match(storage, /data\.participantIds/);
assert.doesNotMatch(storage, /documents\/.*\/conversations\//);
assert.doesNotMatch(storage, /documents\/.*\/jobRequests\//);
assert.doesNotMatch(
  storage,
  /match \/users\/\{uid\}\/images\/\{grantId\}\.jpg/
);
assert.doesNotMatch(storage, /allow read: if signedIn\(\);/);
assert.match(
  storage,
  /match \/providers\/\{providerId\}\/\{allPaths=\*\*\} \{[\s\S]*?allow read, write, delete: if isAdmin\(\);/
);

assert.match(source, /body:\s*"Yeni bir mesajınız var\."\s*,/);

console.log("Security static regression tests passed.");

assert.match(
  rules,
  /match \/quotes\/\{quoteId\} \{[\s\S]*?allow create, update, delete: if isAdmin\(\);/
);
assert.match(
  rules,
  /match \/jobRequests\/\{requestId\} \{[\s\S]*?allow create, update, delete: if isAdmin\(\);/
);
