import assert from "node:assert/strict";
import { test } from "node:test";
import { chatGrantRecipient, canIssueChatGrant } from "../lib/security/chatGrantPolicy.js";

test("chat media grant requires two distinct valid participants and caller membership", () => {
  assert.equal(chatGrantRecipient(["alice", "bob"], "alice"), "bob");
  assert.equal(chatGrantRecipient(["alice", "bob"], "bob"), "alice");
  assert.equal(chatGrantRecipient(["alice", "bob"], "charlie"), null);
  assert.equal(chatGrantRecipient(["alice", "alice"], "alice"), null);
  assert.equal(chatGrantRecipient(["alice"], "alice"), null);
  assert.equal(chatGrantRecipient(["alice", "bad/id"], "alice"), null);
  assert.equal(chatGrantRecipient(null, "alice"), null);
});

test("chat media grant policy denies a block from either direction", () => {
  assert.equal(canIssueChatGrant(["alice", "bob"], "alice", false, false), true);
  assert.equal(canIssueChatGrant(["alice", "bob"], "alice", true, false), false);
  assert.equal(canIssueChatGrant(["alice", "bob"], "alice", false, true), false);
  assert.equal(canIssueChatGrant(["alice", "bob"], "alice", true, true), false);
  assert.equal(canIssueChatGrant(["alice", "bob"], "charlie", false, false), false);
});
