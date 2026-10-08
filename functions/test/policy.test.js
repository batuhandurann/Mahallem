"use strict";
const test = require("node:test");
const assert = require("node:assert/strict");
const { recipientFor, pushPayload, photoBytes, segment, deadToken, recipientPushAllowed, MAX_PHOTO_BYTES } = require("../policy");
const conversation = { participantUids: ["batuhan", "ayse"] };
test("push is UID scoped: sends to the other participant; forged sender and blocks denied", () => {
  assert.equal(recipientFor(conversation, "batuhan", false), "ayse");
  assert.equal(recipientFor(conversation, "ayse", false), "batuhan");
  assert.equal(recipientFor(conversation, "attacker", false), null);
  assert.equal(recipientFor(conversation, "batuhan", true), null);
  for (const participantUids of [["batuhan"], ["batuhan", "batuhan"], ["batuhan", "ayse", "attacker"], ["batuhan", "bad/path"]])
    assert.throws(() => recipientFor({ participantUids }, "batuhan", false));
});
test("data-only notifications carry no text, location, image, or auto-display payload", () => {
  const payload = pushPayload("ayse", "c", "m", ["device"]);
  assert.equal(payload.notification, undefined);
  assert.deepEqual(payload.data, { type: "chat_message", recipientUid: "ayse", conversationId: "c", messageId: "m" });
  assert.ok(payload.android.ttl <= 3600000);
});
test("media accepts canonical base64 and bounds allocation before decoding", () => {
  assert.deepEqual(photoBytes(Buffer.from("photo").toString("base64")), Buffer.from("photo"));
  for (const invalid of [null, "", "http://internal/image", "%%==", "YWJj\n", "YWJj====", "a".repeat(Math.ceil(MAX_PHOTO_BYTES / 3) * 4 + 4)])
    assert.throws(() => photoBytes(invalid));
});
test("paths prevent traversal and malformed identities; only permanent token errors pruned", () => {
  for (const invalid of ["", "../x", ".", "..", "x/y", null, "x".repeat(1501)]) assert.throws(() => segment(invalid));
  assert.equal(segment("3:uid5:other"), "3:uid5:other");
  assert.equal(deadToken("messaging/registration-token-not-registered"), true);
  assert.equal(deadToken("messaging/internal-error"), false);
});

test("push revokes disabled, deleted and pending-deletion recipients", () => {
  assert.equal(recipientPushAllowed({ disabled: false }, undefined), true);
  assert.equal(recipientPushAllowed({ disabled: false }, { deletionStatus: "ACTIVE" }), true);
  assert.equal(recipientPushAllowed({ disabled: false }, { deletionStatus: "REQUESTED" }), false);
  assert.equal(recipientPushAllowed({ disabled: false }, { deletionStatus: "PURGING" }), false);
  assert.equal(recipientPushAllowed({ disabled: true }, { deletionStatus: "ACTIVE" }), false);
  assert.equal(recipientPushAllowed(null, { deletionStatus: "ACTIVE" }), false);
});
