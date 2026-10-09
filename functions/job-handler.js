"use strict";
const crypto = require("node:crypto");
const { FieldValue } = require("firebase-admin/firestore");
const { HttpsError } = require("firebase-functions/v2/https");
const { jobCommand, nextJobState, JobPolicyError } = require("./job-policy");

function createJobHandler({ db, auth, reserve }) {
  return async request => {
    if (!request.auth) throw new HttpsError("unauthenticated", "Giriş yapın.");
    let command;
    try { command = jobCommand(request.data); }
    catch (error) { if (error instanceof JobPolicyError) throw new HttpsError(error.code, error.message); throw error; }
    const uid = request.auth.uid;
    const user = await auth.getUser(uid).catch(error => {
      if (error.code === "auth/user-not-found") return null;
      throw error;
    });
    if (!user || user.disabled) throw new HttpsError("permission-denied", "Hesap etkin değil.");
    await reserve(uid, "jobLifecycle", 60, 3600);
    const hash = crypto.createHash("sha256").update(JSON.stringify(command)).digest("hex");
    const requestRef = db.doc(`requests/${command.requestId}`);
    const jobRef = db.doc(`jobs/${command.requestId}`);
    const eventRef = jobRef.collection("events").doc(command.actionId);
    try {
      return await db.runTransaction(async tx => {
        const [profileDoc, requestDoc, jobDoc, eventDoc] = await Promise.all([
          tx.get(db.doc(`users/${uid}`)), tx.get(requestRef), tx.get(jobRef), tx.get(eventRef)
        ]);
        if (["REQUESTED", "PURGING"].includes(profileDoc.data()?.deletionStatus))
          throw new HttpsError("permission-denied", "Hesap silinme sürecinde.");
        const listing = requestDoc.data();
        if (!listing) throw new HttpsError("not-found", "İş bulunamadı.");
        const customerUid = listing.ownerUid;
        const providerUid = listing.acceptedProviderUid || "";
        if (uid !== customerUid && (!providerUid || uid !== providerUid))
          throw new HttpsError("permission-denied", "Bu iş için yetkiniz yok.");
        // Replay succeeds even after later transitions, but never for a different command/actor.
        if (eventDoc.exists) {
          const event = eventDoc.data();
          if (event.actorUid !== uid || event.commandHash !== hash)
            throw new HttpsError("already-exists", "İşlem kimliği başka bir işlemde kullanılmış.");
          return { status: event.toStatus, version: event.version, replayed: true };
        }
        if (listing.data?.escrowStatus !== "NONE" || listing.data?.escrowAmount !== "")
          throw new HttpsError("failed-precondition", "Ödeme bulunan iş için ödeme çözüm süreci gerekli.");
        if (providerUid) {
          if (!listing.acceptedQuoteId) throw new HttpsError("failed-precondition", "Kabul edilen teklif bulunamadı.");
          const quote = (await tx.get(db.doc(`quotes/${listing.acceptedQuoteId}`))).data();
          if (!quote || quote.status !== "ACCEPTED" || quote.requestId !== command.requestId
            || quote.customerUid !== customerUid || quote.providerUid !== providerUid || quote.data?.escrowFunded !== false)
            throw new HttpsError("failed-precondition", "İş ve teklif kayıtları eşleşmiyor.");
        } else if (listing.acceptedQuoteId) throw new HttpsError("failed-precondition", "İş kaydı tutarsız.");
        const state = jobDoc.exists ? jobDoc.data() : { customerUid, providerUid,
          status: listing.data?.status, version: 0, previousStatus: "", cancellationByUid: "" };
        if (state.customerUid !== customerUid || state.providerUid !== providerUid || state.status !== listing.data?.status)
          throw new HttpsError("failed-precondition", "İş kayıtları tutarsız. Destek ile iletişime geçin.");
        const next = nextJobState(state, uid, command);
        const stamp = FieldValue.serverTimestamp();
        tx.set(jobRef, { ...next, requestId: command.requestId, participantUids: providerUid ? [customerUid, providerUid] : [customerUid],
          createdAt: state.createdAt || stamp, updatedAt: stamp });
        const changes = { "data.status": next.status, updatedAt: stamp };
        if (["COMPLETED", "CANCELLED"].includes(next.status)) changes.visibility = "closed";
        tx.update(requestRef, changes);
        tx.create(eventRef, { action: command.action, actorUid: uid, actorRole: uid === customerUid ? "CUSTOMER" : "PROVIDER",
          fromStatus: state.status, toStatus: next.status, note: command.note, reasonCode: command.reasonCode,
          version: next.version, commandHash: hash, createdAt: stamp });
        return { status: next.status, version: next.version, replayed: false };
      });
    } catch (error) {
      if (error instanceof JobPolicyError) throw new HttpsError(error.code, error.message);
      throw error;
    }
  };
}
module.exports = { createJobHandler };
