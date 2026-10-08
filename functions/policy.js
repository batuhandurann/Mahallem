"use strict";
const crypto = require("node:crypto");
const MAX_PHOTO_BYTES = 5 * 1024 * 1024;

function segment(value) {
  if (typeof value !== "string" || !value.length || value.length > 1500 || value.includes("/") || value === "." || value === "..")
    throw new Error("Invalid document identifier");
  return value;
}
function participants(conversation) {
  const ids = conversation?.participantUids;
  if (!Array.isArray(ids) || ids.length !== 2 || new Set(ids).size !== 2 ||
      ids.some(id => typeof id !== "string" || !id.length || id.length > 128 || id.includes("/")))
    throw new Error("Invalid conversation participants");
  return ids;
}
function recipientFor(conversation, senderUid, blocked) {
  const ids = participants(conversation);
  if (!ids.includes(senderUid) || blocked) return null;
  return ids.find(id => id !== senderUid);
}
function pushPayload(recipientUid, conversationId, messageId, tokens) {
  return {
    tokens,
    data: { type: "chat_message", recipientUid, conversationId, messageId },
    android: { priority: "high", ttl: 60 * 60 * 1000, collapseKey: crypto.createHash("sha256").update(conversationId).digest("hex") }
  };
}
function photoBytes(encoded) {
  if (typeof encoded !== "string" || !encoded.length || encoded.length > Math.ceil(MAX_PHOTO_BYTES / 3) * 4 ||
      encoded.length % 4 !== 0 || !/^[A-Za-z0-9+/]*={0,2}$/.test(encoded)) throw new Error("Invalid photo encoding");
  const bytes = Buffer.from(encoded, "base64");
  if (!bytes.length || bytes.length > MAX_PHOTO_BYTES || bytes.toString("base64") !== encoded) throw new Error("Invalid photo size");
  return bytes;
}
function deadToken(code) {
  return ["messaging/registration-token-not-registered", "messaging/invalid-registration-token"].includes(code);
}
function recipientPushAllowed(authUser, profile) {
  return Boolean(authUser && authUser.disabled !== true &&
    !["REQUESTED", "PURGING"].includes(profile?.deletionStatus));
}
module.exports = { MAX_PHOTO_BYTES, segment, participants, recipientFor, pushPayload, photoBytes, deadToken, recipientPushAllowed };
