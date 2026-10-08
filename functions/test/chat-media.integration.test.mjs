import assert from "node:assert/strict";
import { before, after, beforeEach, test } from "node:test";
import { getAuth } from "firebase-admin/auth";
import { getFirestore, Timestamp } from "firebase-admin/firestore";
import { getStorage } from "firebase-admin/storage";
import { getApp, deleteApp } from "firebase-admin/app";

for (const key of ["FIREBASE_AUTH_EMULATOR_HOST", "FIRESTORE_EMULATOR_HOST", "FIREBASE_STORAGE_EMULATOR_HOST"]) {
  if (!process.env[key]) throw new Error(`${key} required; no live fallback.`);
}
const projectId = "demo-mahallem-rules-test";
process.env.GCLOUD_PROJECT = projectId;
process.env.FIREBASE_CONFIG = JSON.stringify({ projectId, storageBucket: `${projectId}.appspot.com` });
// Tests execute the compiled production callable handler, not a copied policy.
const { readChatAttachment, validateUploadedImage, purgeDeletedAccounts } = await import("../lib/index.js");
const db = getFirestore();
const auth = getAuth();
const bucket = getStorage().bucket();
const conversationId = "a".repeat(64);
const grantId = "media-grant";
const path = `chatAttachments/${conversationId}/media-alice/${grantId}.jpg`;
const privatePath = path.replace("chatAttachments/", "privateChatAttachments/");
const bytes = Buffer.from([0xff, 0xd8, 0xff, 0xe0, 0, 0, 0, 0]);
const conversation = db.doc(`conversations/${conversationId}`);
const grant = db.doc(`users/media-alice/uploadGrants/${grantId}`);
const read = (uid, mediaPath = path) => readChatAttachment.run({
  data: { path: mediaPath }, auth: uid ? { uid, token: { email_verified: true } } : undefined,
});
const denied = (promise, code = "permission-denied") => assert.rejects(promise, (e) => e.code === code);

before(async () => {
  for (const uid of ["media-alice", "media-bob", "media-charlie"]) {
    await auth.createUser({ uid, email: `${uid}@example.test`, emailVerified: true, password: "EmulatorOnly-Password123" });
  }
});

beforeEach(async () => {
  for (const uid of ["media-alice", "media-bob", "media-charlie"]) {
    await auth.getUser(uid).catch(async (error) => {
      if (error.code !== "auth/user-not-found") throw error;
      return auth.createUser({ uid, email: `${uid}@example.test`, emailVerified: true, password: "EmulatorOnly-Password123" });
    });
    await auth.updateUser(uid, { disabled: false, emailVerified: true });
    await db.doc(`users/${uid}`).set({ uid, deletionStatus: "ACTIVE" });
    await db.doc(`rateLimits/chat-media-read:${uid}`).delete();
  }
  await db.doc("users/media-alice/blockedUsers/media-bob").delete();
  await db.doc("users/media-bob/blockedUsers/media-alice").delete();
  await conversation.set({ participantIds: ["media-alice", "media-bob"] });
  await grant.set({ ownerUid: "media-alice", kind: "CHAT", conversationId,
    participantIds: ["media-alice", "media-bob"], validated: true, privateObjectPath: privatePath,
    expiresAt: Timestamp.fromMillis(Date.now() + 600_000) });
  await bucket.file(path).save(bytes, { resumable: false, contentType: "image/jpeg" });
  await bucket.file(privatePath).save(bytes, { resumable: false, contentType: "image/jpeg" });
});

test("active participants receive actual private bytes", async () => {
  assert.deepEqual(Buffer.from((await read("media-bob")).base64, "base64"), bytes);
  assert.deepEqual(Buffer.from((await read("media-alice")).base64, "base64"), bytes);
});
test("anonymous and unrelated users cannot read", async () => {
  await denied(read(undefined), "unauthenticated");
  await denied(read("media-charlie"));
});
test("membership removal overrides an unchanged validated grant", async () => {
  await read("media-bob");
  await conversation.update({ participantIds: ["media-alice"] });
  assert.equal((await grant.get()).data().validated, true);
  await denied(read("media-bob"));
});
test("deleted conversation and removed sender fail closed", async () => {
  await conversation.update({ participantIds: ["media-bob"] });
  await denied(read("media-bob"));
  await conversation.delete();
  await denied(read("media-alice"));
});
test("Auth disabled users cannot read with a stale token", async () => {
  await auth.updateUser("media-bob", { disabled: true });
  await denied(read("media-bob"), "failed-precondition");
});
test("pending deletion, purging and missing profiles fail closed", async () => {
  for (const deletionStatus of ["REQUESTED", "PURGING"]) {
    await db.doc("users/media-bob").update({ deletionStatus });
    await denied(read("media-bob"), "failed-precondition");
  }
  await db.doc("users/media-bob").delete();
  await denied(read("media-bob"), "failed-precondition");
});
test("blocks in either direction revoke reads", async () => {
  const block = db.doc("users/media-alice/blockedUsers/media-bob");
  await block.set({ blocked: true });
  await denied(read("media-bob"));
  await block.delete();
  await db.doc("users/media-bob/blockedUsers/media-alice").set({ blocked: true });
  await denied(read("media-bob"));
});
test("invalidated and cross-conversation grants cannot authorize bytes", async () => {
  await grant.update({ validated: false });
  await denied(read("media-bob"));
  await grant.update({ validated: true, conversationId: "b".repeat(64) });
  await denied(read("media-bob"));
});
test("traversal and external URLs are rejected", async () => {
  await denied(read("media-bob", "../" + path), "invalid-argument");
  await denied(read("media-bob", "https://example.test/media.jpg"), "invalid-argument");
});
test("read quota denies an authorized excessive request", async () => {
  await db.doc("rateLimits/chat-media-read:media-bob").set({ windowStartMs: Date.now(), count: 30 });
  await denied(read("media-bob"), "resource-exhausted");
});
test("production validator moves private bytes and invalidates an actual bearer URL", async () => {
  await bucket.file(path).setMetadata({ metadata: { firebaseStorageDownloadTokens: "obsolete-token" } });
  const tokenUrl = `http://${process.env.FIREBASE_STORAGE_EMULATOR_HOST}/v0/b/${bucket.name}/o/${encodeURIComponent(path)}?alt=media&token=obsolete-token`;
  assert.equal((await fetch(tokenUrl)).status, 200, "Fixture must prove the old token works before validation");
  await validateUploadedImage.run({ data: { bucket: bucket.name, name: path,
    size: String(bytes.length), contentType: "image/jpeg" } });
  assert.equal((await bucket.file(path).exists())[0], false, "Token-bearing source object must be physically deleted");
  const revokedResponse = await fetch(tokenUrl);
  assert.ok([403, 404].includes(revokedResponse.status), "Known bearer token must be denied; 403 can hide object existence");
  const [metadata] = await bucket.file(privatePath).getMetadata();
  assert.ok(!metadata.metadata?.firebaseStorageDownloadTokens);
  assert.equal((await grant.get()).data().validated, true);
  assert.deepEqual(Buffer.from((await read("media-bob")).base64, "base64"), bytes);
  await conversation.update({ participantIds: ["media-alice"] });
  await denied(read("media-bob"));
});

test("validator rejects an upload whose sender membership was revoked", async () => {
  await conversation.update({ participantIds: ["media-bob"] });
  await validateUploadedImage.run({ data: { bucket: bucket.name, name: path,
    size: String(bytes.length), contentType: "image/jpeg" } });
  assert.equal((await grant.get()).data().validated, false);
  assert.equal((await bucket.file(path).exists())[0], false);
  await denied(read("media-bob"));
});

test("production account purge deletes both upload and private media objects", async () => {
  await db.doc("users/media-alice").update({ deletionStatus: "REQUESTED", deletionDueAt: Timestamp.fromMillis(Date.now() - 60_000) });
  await purgeDeletedAccounts.run({});
  assert.equal((await bucket.file(path).exists())[0], false);
  assert.equal((await bucket.file(privatePath).exists())[0], false);
  assert.equal((await db.doc("users/media-alice").get()).exists, false);
  await assert.rejects(auth.getUser("media-alice"), (e) => e.code === "auth/user-not-found");
});

after(async () => {
  await bucket.file(path).delete({ ignoreNotFound: true });
  await bucket.file(privatePath).delete({ ignoreNotFound: true });
  for (const uid of ["media-alice", "media-bob", "media-charlie"]) {
    await auth.deleteUser(uid).catch((error) => { if (error.code !== "auth/user-not-found") throw error; });
  }
  await db.terminate();
  await deleteApp(getApp());
});
