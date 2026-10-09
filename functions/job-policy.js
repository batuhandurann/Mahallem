"use strict";

const REASONS = ["CHANGE_OF_PLANS", "SCHEDULE", "SCOPE", "NO_SHOW", "QUALITY", "OTHER"];
const ACTIVE = ["ACCEPTED", "IN_PROGRESS", "AWAITING_CONFIRMATION"];
class JobPolicyError extends Error {
  constructor(code, message) { super(message); this.code = code; }
}
function fail(code, message) { throw new JobPolicyError(code, message); }
function jobCommand(input) {
  if (!input || typeof input !== "object" || Array.isArray(input)
    || Object.keys(input).some(k => !["requestId", "actionId", "action", "version", "note", "reasonCode"].includes(k)))
    fail("invalid-argument", "İşlem bilgileri geçersiz.");
  const { requestId, actionId, action, version } = input;
  if (typeof requestId !== "string" || !/^[^/]{1,128}$/.test(requestId)
    || typeof actionId !== "string" || !/^[a-zA-Z0-9-]{16,64}$/.test(actionId)
    || !Number.isSafeInteger(version) || version < 0 || version >= 2147483647
    || !["START", "SUBMIT_COMPLETION", "CONFIRM_COMPLETION", "REQUEST_REVISION", "CANCEL_OPEN",
      "REQUEST_CANCEL", "ACCEPT_CANCEL", "DECLINE_CANCEL", "WITHDRAW_CANCEL"].includes(action))
    fail("invalid-argument", "İşlem bilgileri geçersiz.");
  if (typeof (input.note ?? "") !== "string" || (input.note ?? "").length > 1000)
    fail("invalid-argument", "Açıklama en fazla 1000 karakter olabilir.");
  const note = (input.note ?? "").trim();
  const reasonCode = input.reasonCode ?? "";
  if (["SUBMIT_COMPLETION", "REQUEST_REVISION", "CANCEL_OPEN", "REQUEST_CANCEL", "DECLINE_CANCEL"].includes(action) && note.length < 10)
    fail("invalid-argument", "En az 10 karakterlik bir açıklama yazın.");
  if (["CANCEL_OPEN", "REQUEST_CANCEL"].includes(action) ? !REASONS.includes(reasonCode) : reasonCode !== "")
    fail("invalid-argument", "Geçerli bir iptal nedeni seçin.");
  return { requestId, actionId, action, version, note, reasonCode };
}

// Pure policy; caller identity and all state originate from authoritative records.
function nextJobState(state, actorUid, command) {
  const customer = actorUid === state.customerUid;
  const provider = actorUid === state.providerUid && state.providerUid !== "";
  if (!customer && !provider) fail("permission-denied", "Bu iş için yetkiniz yok.");
  if (command.version !== state.version) fail("aborted", "İşin durumu değişti. Güncel durumu kontrol edip yeniden deneyin.");
  const next = { ...state, version: state.version + 1, note: command.note, reasonCode: command.reasonCode };
  const need = condition => { if (!condition) fail("failed-precondition", "Bu işlem işin güncel durumunda yapılamaz."); };
  switch (command.action) {
    case "CANCEL_OPEN": need(customer && state.status === "PENDING" && !state.providerUid); next.status = "CANCELLED"; break;
    case "START": need(provider && state.status === "ACCEPTED"); next.status = "IN_PROGRESS"; break;
    case "SUBMIT_COMPLETION":
      need(provider && ["ACCEPTED", "IN_PROGRESS"].includes(state.status)); next.status = "AWAITING_CONFIRMATION"; break;
    case "CONFIRM_COMPLETION": need(customer && state.status === "AWAITING_CONFIRMATION"); next.status = "COMPLETED"; break;
    case "REQUEST_REVISION": need(customer && state.status === "AWAITING_CONFIRMATION"); next.status = "IN_PROGRESS"; break;
    case "REQUEST_CANCEL":
      need(ACTIVE.includes(state.status)); next.previousStatus = state.status;
      next.cancellationByUid = actorUid; next.status = "CANCELLATION_REQUESTED"; break;
    case "ACCEPT_CANCEL":
      need(state.status === "CANCELLATION_REQUESTED" && state.cancellationByUid !== actorUid);
      next.status = "CANCELLED"; break;
    case "DECLINE_CANCEL":
      need(state.status === "CANCELLATION_REQUESTED" && state.cancellationByUid !== actorUid && ACTIVE.includes(state.previousStatus));
      next.status = state.previousStatus; break;
    case "WITHDRAW_CANCEL":
      need(state.status === "CANCELLATION_REQUESTED" && state.cancellationByUid === actorUid && ACTIVE.includes(state.previousStatus));
      next.status = state.previousStatus; break;
  }
  if (next.status !== "CANCELLATION_REQUESTED") { next.previousStatus = ""; next.cancellationByUid = ""; }
  return next;
}
module.exports = { jobCommand, nextJobState, JobPolicyError };
