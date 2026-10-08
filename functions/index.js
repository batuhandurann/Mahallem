"use strict";
const { initializeApp } = require("firebase-admin/app");
const { getFirestore, FieldValue, Timestamp } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");
const { getAuth } = require("firebase-admin/auth");
const { getStorage } = require("firebase-admin/storage");
const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const { onCall, HttpsError } = require("firebase-functions/v2/https");
const crypto = require("node:crypto");
const sharp = require("sharp");
const { MAX_PHOTO_BYTES, segment, participants, recipientFor, pushPayload, photoBytes, deadToken } = require("./policy");

initializeApp();
const DATABASE = process.env.FIRESTORE_DATABASE_ID || "mahallem";
const db = getFirestore(DATABASE);
// FUNCTIONS_EMULATOR is set by the Firebase runtime, never accepted from a client.
const callableOptions = { region: "europe-west3", enforceAppCheck: process.env.FUNCTIONS_EMULATOR !== "true", memory: "512MiB", timeoutSeconds: 60,
  maxInstances: 5, concurrency: 2 };

async function requireModerator(request) {
  if (!request.auth) throw new HttpsError("unauthenticated", "Giriş yapın.");
  if (request.auth.token.moderator !== true) throw new HttpsError("permission-denied", "Moderatör yetkisi gerekli.");
  // A revoked moderator must not retain access until an old ID token expires.
  const user = await getAuth().getUser(request.auth.uid);
  if (user.disabled || user.customClaims?.moderator !== true)
    throw new HttpsError("permission-denied", "Moderatör yetkisi gerekli.");
  return request.auth.uid;
}

exports.getModerationQueue = onCall(callableOptions, async request => {
  const uid = await requireModerator(request);
  await reserve(uid, "moderationQueue", 120, 3600);
  const queue = await db.collection("reports").where("status", "==", "pending").orderBy("createdAt").limit(50).get();
  return { reports: queue.docs.map(doc => ({ id: doc.id, ...doc.data(), createdAt: doc.data().createdAt?.toMillis() || 0 })) };
});

exports.reviewReport = onCall(callableOptions, async request => {
  const moderatorUid = await requireModerator(request);
  let reportId;
  try { reportId = segment(request.data?.reportId); } catch { throw new HttpsError("invalid-argument", "Geçersiz rapor."); }
  const action = request.data?.action;
  const note = request.data?.note;
  if (!["dismiss", "hide_listing"].includes(action) || typeof note !== "string" || note.trim().length < 3 || note.length > 2000)
    throw new HttpsError("invalid-argument", "İşlem ve 3–2000 karakter inceleme notu gerekli.");
  const ref = db.doc(`reports/${reportId}`);
  await db.runTransaction(async tx => {
    const snapshot = await tx.get(ref);
    if (!snapshot.exists) throw new HttpsError("not-found", "Rapor bulunamadı.");
    const report = snapshot.data();
    if (report.status !== "pending") throw new HttpsError("failed-precondition", "Rapor zaten incelenmiş.");
    if (action === "hide_listing") {
      const match = /^(provider|request):([^/]+)$/.exec(report.targetId || "");
      if (report.targetType !== "listing" || !match) throw new HttpsError("invalid-argument", "Bu işlem yalnızca ilanlar içindir.");
      const listingRef = db.doc(`${match[1] === "provider" ? "providers" : "requests"}/${match[2]}`);
      const listing = await tx.get(listingRef);
      if (!listing.exists || listing.data().ownerUid !== report.targetUid)
        throw new HttpsError("failed-precondition", "İlan sahibi raporla eşleşmiyor.");
      tx.update(listingRef, { "data.isReported": true, visibility: "hidden", moderationStatus: "hidden", updatedAt: FieldValue.serverTimestamp() });
    }
    tx.update(ref, { status: "reviewed", action, reviewNote: note.trim(), reviewedByUid: moderatorUid, reviewedAt: FieldValue.serverTimestamp() });
    tx.create(db.collection("moderationAudit").doc(), { reportId, moderatorUid, action, targetType: report.targetType,
      targetId: report.targetId, targetUid: report.targetUid, note: note.trim(), createdAt: FieldValue.serverTimestamp() });
  });
  return { reportId, status: "reviewed" };
});

async function blocked(a, b) {
  const docs = await db.getAll(db.doc(`users/${a}/blocks/${b}`), db.doc(`users/${b}/blocks/${a}`));
  return docs.some(doc => doc.exists);
}

async function authorizeConversation(request) {
  if (!request.auth) throw new HttpsError("unauthenticated", "Giriş yapın.");
  let conversationId;
  try { conversationId = segment(request.data?.conversationId); } catch { throw new HttpsError("invalid-argument", "Geçersiz sohbet."); }
  const snapshot = await db.doc(`conversations/${conversationId}`).get();
  let ids;
  try { ids = participants(snapshot.data()); } catch { throw new HttpsError("permission-denied", "Sohbete erişilemiyor."); }
  const uid = request.auth.uid;
  if (!ids.includes(uid) || await blocked(ids[0], ids[1])) throw new HttpsError("permission-denied", "Sohbete erişilemiyor.");
  return { conversationId, uid };
}

// Per-UID atomic budget; rejected malformed payloads never reach Storage. Reserving
// before upload is conservative: a failed upload uses the attempt quota too.
async function reserve(uid, operation, limit, seconds) {
  const now = Date.now();
  const window = Math.floor(now / (seconds * 1000));
  const ref = db.doc(`_abuseBudgets/${uid}_${operation}_${window}`);
  await db.runTransaction(async tx => {
    const snap = await tx.get(ref);
    const count = snap.data()?.count || 0;
    if (count >= limit) throw new HttpsError("resource-exhausted", "İşlem sınırına ulaşıldı. Daha sonra deneyin.");
    tx.set(ref, { count: count + 1, expiresAt: Timestamp.fromMillis(now + seconds * 2000) });
  });
}

exports.uploadConversationPhoto = onCall(callableOptions, async request => {
  const { conversationId, uid } = await authorizeConversation(request);
  let input;
  try { input = photoBytes(request.data?.base64); } catch { throw new HttpsError("invalid-argument", "En fazla 5 MB fotoğraf yükleyin."); }
  await reserve(uid, "photoUpload", 30, 86400);
  let image;
  try {
    // This validation is authoritative; a forged MIME type or client bypass cannot
    // upload scripts/SVG/executable bytes. EXIF/GPS stripped by fresh JPEG encoding.
    const decoder = sharp(input, { limitInputPixels: 16_000_000, animated: false, failOn: "warning" });
    const metadata = await decoder.metadata();
    if (!["jpeg", "png", "webp"].includes(metadata.format) || metadata.pages > 1) throw new Error("Unsupported image");
    image = await decoder.rotate().resize({ width: 2048, height: 2048, fit: "inside", withoutEnlargement: true })
      .jpeg({ quality: 85 }).toBuffer();
    if (image.length > MAX_PHOTO_BYTES) throw new Error("Oversized photo");
  } catch { throw new HttpsError("invalid-argument", "Fotoğraf biçimi desteklenmiyor."); }
  // Re-check ACL after decoding and immediately before writing.
  await authorizeConversation(request);
  const mediaId = crypto.randomUUID();
  const storagePath = `conversationMedia/${conversationId}/${uid}/${mediaId}.jpg`;
  const file = getStorage().bucket().file(storagePath);
  await file.save(image, { resumable: false, metadata: { contentType: "image/jpeg", cacheControl: "private, no-store",
    metadata: { uploaderUid: uid, conversationId, mediaId } } });
  try {
    await db.doc(`conversations/${conversationId}/media/${mediaId}`).create({ uploaderUid: uid, storagePath,
      contentType: "image/jpeg", sizeBytes: image.length, createdAt: FieldValue.serverTimestamp() });
  } catch (error) { await file.delete().catch(() => {}); throw error; }
  return { mediaId, storagePath };
});

exports.readConversationPhoto = onCall(callableOptions, async request => {
  const { conversationId, uid } = await authorizeConversation(request);
  let mediaId;
  try { mediaId = segment(request.data?.mediaId); } catch { throw new HttpsError("invalid-argument", "Geçersiz fotoğraf."); }
  await reserve(uid, "photoRead", 120, 3600);
  const media = (await db.doc(`conversations/${conversationId}/media/${mediaId}`).get()).data();
  if (!media || media.sizeBytes > MAX_PHOTO_BYTES || media.contentType !== "image/jpeg" ||
      media.storagePath !== `conversationMedia/${conversationId}/${media.uploaderUid}/${mediaId}.jpg`)
    throw new HttpsError("not-found", "Fotoğraf bulunamadı.");
  const file = getStorage().bucket().file(media.storagePath);
  const [metadata] = await file.getMetadata();
  if (Number(metadata.size) > MAX_PHOTO_BYTES) throw new HttpsError("failed-precondition", "Fotoğraf sınırı aşıldı.");
  const [image] = await file.download();
  await authorizeConversation(request);
  return { base64: image.toString("base64"), contentType: "image/jpeg" };
});

exports.notifyConversationMessage = onDocumentCreated({
  document: "conversations/{conversationId}/messages/{messageId}", database: DATABASE,
  region: "europe-west3", retry: true, maxInstances: 5
}, async event => {
  const message = event.data?.data();
  if (!message) return;
  const { conversationId, messageId } = event.params;
  const snapshot = await db.doc(`conversations/${conversationId}`).get();
  let ids;
  try { ids = participants(snapshot.data()); } catch { return; }
  const recipientUid = recipientFor(snapshot.data(), message.senderUid, await blocked(ids[0], ids[1]));
  if (!recipientUid) return;
  // Deduplicate completed triggers. Firestore event delivery itself is at least once;
  // client uses messageId as notification ID, replacing retries instead of leaking duplicates.
  const deliveryId = crypto.createHash("sha256").update(`${conversationId}/${messageId}`).digest("hex");
  const deliveryRef = db.doc(`_pushDeliveries/${deliveryId}`);
  if ((await deliveryRef.get()).data()?.sent === true) return;
  const devices = await db.collection(`users/${recipientUid}/devices`).orderBy("updatedAt", "desc").limit(20).get();
  const valid = devices.docs.filter(doc => typeof doc.data().token === "string" && doc.data().token.length > 0 &&
    doc.data().token.length <= 4096 && doc.data().platform === "android" &&
    doc.data().updatedAt?.toMillis() > Date.now() - 30 * 86400 * 1000);
  if (!valid.length) return;
  if (await blocked(ids[0], ids[1])) return;
  try { await reserve(message.senderUid, "messagePush", 240, 3600); }
  catch (error) {
    if (error.code === "resource-exhausted") return;
    throw error;
  }
  const result = await getMessaging().sendEachForMulticast(pushPayload(recipientUid, conversationId, messageId,
    valid.map(doc => doc.data().token)));
  await Promise.all(result.responses.map((response, i) => deadToken(response.error?.code) ? valid[i].ref.delete() : Promise.resolve()));
  if (result.responses.some(response => !response.success && !deadToken(response.error?.code)))
    throw new Error("Retry transient push delivery failure");
  await deliveryRef.set({ sent: true, updatedAt: FieldValue.serverTimestamp(),
    expiresAt: Timestamp.fromMillis(Date.now() + 7 * 86400 * 1000) });
});
