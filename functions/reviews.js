"use strict";
const crypto = require("node:crypto");
const WINDOW_MS = 30 * 86400000;
function identifier(value) {
  if (typeof value !== "string" || !/^[A-Za-z0-9_-]{1,200}$/.test(value)) throw new Error("Geçersiz iş kimliği.");
  return value;
}
function reviewInput(data) {
  if (!data || Object.keys(data).some(k => !["requestId", "rating", "comment"].includes(k))) throw new Error("Geçersiz değerlendirme alanı.");
  const requestId = identifier(data.requestId);
  if (!Number.isInteger(data.rating) || data.rating < 1 || data.rating > 5) throw new Error("1–5 arasında puan seçin.");
  if (typeof data.comment !== "string" || data.comment.length > 2000 || /[\u0000-\u0008\u000b\u000c\u000e-\u001f]/.test(data.comment)) throw new Error("Yorum en fazla 2000 karakter olmalı.");
  return { requestId, rating: data.rating, comment: data.comment.trim() };
}
function acceptedJob(job, quote, uid) {
  return job && quote && job.ownerUid === uid && quote.customerUid === uid
    && quote.requestId === job.data?.id && quote.status === "ACCEPTED"
    && job.acceptedProviderUid === quote.providerUid && quote.providerUid !== uid;
}
function reviewEligible(job, quote, uid, now) {
  const completed = job?.completedAt?.toMillis?.();
  return acceptedJob(job, quote, uid) && job.data.status === "COMPLETED"
    && job.data.escrowStatus !== "DISPUTED" && job.completedByUid === uid && Number.isFinite(completed)
    && completed <= now && now <= completed + WINDOW_MS;
}
function registerReviews({ db, onCall, options, HttpsError, getAuth, FieldValue, reserve, requireModerator }) {
  const fail = (code, message) => { throw new HttpsError(code, message); };
  const parse = (fn, value) => { try { return fn(value); } catch (e) { return fail("invalid-argument", e.message); } };
  async function active(request) {
    if (!request.auth) fail("unauthenticated", "Giriş yapın.");
    const uid = request.auth.uid;
    const account = await getAuth().getUser(uid).catch(e => { if (e.code === "auth/user-not-found") return null; throw e; });
    const user = (await db.doc(`users/${uid}`).get()).data();
    if (!account || account.disabled || ["REQUESTED", "PURGING"].includes(user?.deletionStatus)) fail("permission-denied", "Hesap etkin değil.");
    return uid;
  }
  const privateRef = id => db.doc(`jobReviews/${id}`);
  const publicId = id => crypto.createHash("sha256").update(id).digest("hex");
  const result = {};
  // Customer confirmation is authoritative; accepting a quote or funding escrow does not complete a job.
  result.confirmJobCompletion = onCall(options, async request => {
    const uid = await active(request);
    const id = parse(identifier, request.data?.requestId);
    await reserve(uid, "completion", 60, 3600);
    await db.runTransaction(async tx => {
      const ref = db.doc(`requests/${id}`), job = (await tx.get(ref)).data();
      if (!job || job.ownerUid !== uid) fail("permission-denied", "Bu işi yalnızca müşterisi tamamlayabilir.");
      const quote = job.acceptedQuoteId ? (await tx.get(db.doc(`quotes/${identifier(job.acceptedQuoteId)}`))).data() : null;
      if (!acceptedJob(job, quote, uid)) fail("failed-precondition", "Kabul edilmiş teklif bulunamadı.");
      if (job.data.status === "COMPLETED" && job.data.escrowStatus !== "DISPUTED" && job.completedByUid === uid && job.completedAt) return;
      if (job.data.status !== "ACCEPTED" || job.data.escrowStatus === "DISPUTED") fail("failed-precondition", "Bu iş tamamlanamaz.");
      tx.update(ref, { "data.status": "COMPLETED", completedAt: FieldValue.serverTimestamp(), completedByUid: uid, updatedAt: FieldValue.serverTimestamp() });
    });
    return { requestId: id, status: "COMPLETED" };
  });
  result.submitJobReview = onCall(options, async request => {
    const uid = await active(request), input = parse(reviewInput, request.data);
    await reserve(uid, "review", 30, 3600);
    return db.runTransaction(async tx => {
      const jobRef = db.doc(`requests/${input.requestId}`), job = (await tx.get(jobRef)).data();
      if (!job || job.ownerUid !== uid) fail("permission-denied", "Bu işi yalnızca müşterisi değerlendirebilir.");
      const quote = job.acceptedQuoteId ? (await tx.get(db.doc(`quotes/${identifier(job.acceptedQuoteId)}`))).data() : null;
      // Evaluate server time on every transaction retry; no client completion claim is accepted.
      if (!reviewEligible(job, quote, uid, Date.now())) fail("failed-precondition", "Yalnızca tamamlanan işler 30 gün içinde değerlendirilebilir.");
      const providerId = parse(identifier, quote.data?.providerId);
      const providerRef = db.doc(`providers/${providerId}`), ownRef = privateRef(input.requestId);
      const [providerSnap, existing] = await Promise.all([tx.get(providerRef), tx.get(ownRef)]);
      const provider = providerSnap.data();
      if (!provider || provider.ownerUid !== quote.providerUid) fail("failed-precondition", "Hizmet veren kaydı eşleşmiyor.");
      const reviewId = publicId(input.requestId);
      if (existing.exists) {
        const old = existing.data();
        if (old.customerUid === uid && old.rating === input.rating && old.comment === input.comment) return { reviewId, alreadySubmitted: true };
        fail("already-exists", "Bu iş için değerlendirme zaten gönderildi.");
      }
      const count = (provider.data.reviewCount || 0) + 1, sum = (provider.ratingSum || 0) + input.rating;
      const publicRef = providerRef.collection("reviews").doc(reviewId);
      tx.create(ownRef, { customerUid: uid, providerUid: quote.providerUid, providerId, reviewId, rating: input.rating, comment: input.comment, status: "published", createdAt: FieldValue.serverTimestamp() });
      // Public projection intentionally excludes customer UID, request ID, contact and address.
      tx.create(db.doc(`reviewLookup/${reviewId}`), { requestId: input.requestId, providerId });
      tx.create(publicRef, { rating: input.rating, comment: input.comment, verifiedJob: true, createdAt: FieldValue.serverTimestamp() });
      tx.update(providerRef, { "data.rating": sum / count, "data.reviewCount": count, ratingSum: sum, updatedAt: FieldValue.serverTimestamp() });
      tx.update(jobRef, { reviewId, updatedAt: FieldValue.serverTimestamp() });
      return { reviewId, alreadySubmitted: false };
    });
  });
  result.reportJobReview = onCall(options, async request => {
    const uid = await active(request), providerId = parse(identifier, request.data?.providerId), reviewId = parse(identifier, request.data?.reviewId);
    const reason = request.data?.reason;
    if (typeof reason !== "string" || reason.trim().length < 3 || reason.length > 1000) fail("invalid-argument", "3–1000 karakter gerekçe yazın.");
    await reserve(uid, "reviewReport", 10, 3600);
    const review = await db.doc(`providers/${providerId}/reviews/${reviewId}`).get();
    const lookup = (await db.doc(`reviewLookup/${reviewId}`).get()).data();
    if (!lookup || lookup.providerId !== providerId) fail("not-found", "Değerlendirme bulunamadı.");
    if (!review.exists) fail("not-found", "Değerlendirme bulunamadı.");
    const id = crypto.createHash("sha256").update(`${uid}:${providerId}:${reviewId}`).digest("hex");
    // One report per person/review; reporting never hides content automatically.
    await db.runTransaction(async tx => {
      const ref = db.doc(`reviewReports/${id}`);
      if ((await tx.get(ref)).exists) return;
      tx.create(ref, { reporterUid: uid, requestId: lookup.requestId, providerId, reviewId, reason: reason.trim(), status: "pending", updatedAt: FieldValue.serverTimestamp() });
    });
    return { reportId: id };
  });
  result.getReviewModerationQueue = onCall(options, async request => {
    const uid = await requireModerator(request);
    await reserve(uid, "reviewQueue", 120, 3600);
    const queue = await db.collection("reviewReports").where("status", "==", "pending").orderBy("updatedAt").limit(50).get();
    return { reports: queue.docs.map(doc => ({ reportId: doc.id, requestId: doc.data().requestId, providerId: doc.data().providerId,
      reviewId: doc.data().reviewId, reason: doc.data().reason })) };
  });
  result.hideJobReview = onCall(options, async request => {
    const moderatorUid = await requireModerator(request), id = parse(identifier, request.data?.requestId);
    const note = request.data?.note;
    if (typeof note !== "string" || note.trim().length < 3 || note.length > 2000) fail("invalid-argument", "İnceleme gerekçesi gerekli.");
    await reserve(moderatorUid, "reviewModeration", 120, 3600);
    await db.runTransaction(async tx => {
      const ref = privateRef(id), snap = await tx.get(ref), review = snap.data();
      if (!review) fail("not-found", "Değerlendirme bulunamadı.");
      const reportRef = request.data?.reportId ? db.doc(`reviewReports/${parse(identifier, request.data.reportId)}`) : null;
      const report = reportRef ? (await tx.get(reportRef)).data() : null;
      if (reportRef && report?.requestId !== id) fail("invalid-argument", "Şikayet bu değerlendirmeye ait değil.");
      if (review.status === "hidden") {
        if (reportRef) tx.update(reportRef, { status: "reviewed", updatedAt: FieldValue.serverTimestamp() });
        return;
      }
      const providerRef = db.doc(`providers/${review.providerId}`), provider = (await tx.get(providerRef)).data();
      if (!provider) fail("failed-precondition", "Hizmet veren bulunamadı.");
      const count = provider.data.reviewCount - 1, sum = provider.ratingSum - review.rating;
      if (count < 0 || sum < 0) fail("failed-precondition", "Puan toplamı tutarsız.");
      if (reportRef) tx.update(reportRef, { status: "reviewed", updatedAt: FieldValue.serverTimestamp() });
      tx.delete(providerRef.collection("reviews").doc(review.reviewId));
      tx.update(ref, { status: "hidden", moderatedByUid: moderatorUid, moderationNote: note.trim(), moderatedAt: FieldValue.serverTimestamp() });
      tx.update(providerRef, { "data.reviewCount": count, "data.rating": count ? sum / count : 0, ratingSum: sum, updatedAt: FieldValue.serverTimestamp() });
      tx.create(db.collection("moderationAudit").doc(), { requestId: id, reviewId: review.reviewId, moderatorUid, action: "hide_review", note: note.trim(), createdAt: FieldValue.serverTimestamp() });
    });
    return { status: "hidden" };
  });
  return result;
}
module.exports = { reviewInput, reviewEligible, acceptedJob, WINDOW_MS, registerReviews };
