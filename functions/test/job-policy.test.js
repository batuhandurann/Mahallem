"use strict";
const { test } = require("node:test");
const assert = require("node:assert/strict");
const { jobCommand, nextJobState } = require("../job-policy");
const base = { customerUid: "alice", providerUid: "bob", status: "ACCEPTED", version: 0, previousStatus: "", cancellationByUid: "" };
const command = (action, version = 0, extras = {}) => jobCommand({ requestId: "request", actionId: "12345678-1234-1234-1234-123456789000",
  action, version, note: ["SUBMIT_COMPLETION", "REQUEST_REVISION", "DECLINE_CANCEL", "REQUEST_CANCEL", "CANCEL_OPEN"].includes(action) ? "Açıklanan iş gerekçesi" : "",
  reasonCode: ["REQUEST_CANCEL", "CANCEL_OPEN"].includes(action) ? "OTHER" : "", ...extras });
test("completion is provider submission followed by customer confirmation; no unilateral close", () => {
  assert.throws(() => nextJobState(base, "alice", command("SUBMIT_COMPLETION")));
  assert.throws(() => nextJobState(base, "bob", command("CONFIRM_COMPLETION")));
  const submitted = nextJobState(base, "bob", command("SUBMIT_COMPLETION"));
  assert.equal(submitted.status, "AWAITING_CONFIRMATION");
  assert.throws(() => nextJobState(submitted, "bob", command("CONFIRM_COMPLETION", 1)));
  assert.equal(nextJobState(submitted, "alice", command("CONFIRM_COMPLETION", 1)).status, "COMPLETED");
});
test("revision returns to progress and requires a fresh provider submission", () => {
  let state = nextJobState(base, "bob", command("START"));
  state = nextJobState(state, "bob", command("SUBMIT_COMPLETION", 1));
  state = nextJobState(state, "alice", command("REQUEST_REVISION", 2));
  assert.equal(state.status, "IN_PROGRESS");
  assert.throws(() => nextJobState(state, "alice", command("CONFIRM_COMPLETION", 3)));
  state = nextJobState(state, "bob", command("SUBMIT_COMPLETION", 3));
  assert.equal(nextJobState(state, "alice", command("CONFIRM_COMPLETION", 4)).version, 5);
});
for (const status of ["ACCEPTED", "IN_PROGRESS", "AWAITING_CONFIRMATION"]) {
  for (const actor of ["alice", "bob"]) test(`mutual cancellation from ${status}, initiated by ${actor}`, () => {
    const peer = actor === "alice" ? "bob" : "alice";
    const waiting = nextJobState({ ...base, status }, actor, command("REQUEST_CANCEL"));
    assert.equal(waiting.previousStatus, status);
    assert.throws(() => nextJobState(waiting, actor, command("ACCEPT_CANCEL", 1)));
    assert.throws(() => nextJobState(waiting, peer, command("WITHDRAW_CANCEL", 1)));
    assert.equal(nextJobState(waiting, peer, command("ACCEPT_CANCEL", 1)).status, "CANCELLED");
    const declined = nextJobState(waiting, peer, command("DECLINE_CANCEL", 1));
    assert.equal(declined.status, status); assert.equal(declined.cancellationByUid, "");
    assert.equal(nextJobState(waiting, actor, command("WITHDRAW_CANCEL", 1)).status, status);
  });
}
test("only owner can close an unaccepted request; accepted job cannot bypass mutual cancellation", () => {
  const open = { ...base, status: "PENDING", providerUid: "" };
  assert.equal(nextJobState(open, "alice", command("CANCEL_OPEN")).status, "CANCELLED");
  assert.throws(() => nextJobState(open, "bob", command("CANCEL_OPEN")));
  assert.throws(() => nextJobState(base, "alice", command("CANCEL_OPEN")));
});
test("terminal states, stale version and outsider are denied across all actions", () => {
  const actions = ["START", "SUBMIT_COMPLETION", "CONFIRM_COMPLETION", "REQUEST_REVISION", "CANCEL_OPEN", "REQUEST_CANCEL", "ACCEPT_CANCEL", "DECLINE_CANCEL", "WITHDRAW_CANCEL"];
  for (const action of actions) {
    for (const status of ["COMPLETED", "CANCELLED"]) for (const uid of ["alice", "bob"])
      assert.throws(() => nextJobState({ ...base, status }, uid, command(action)));
    assert.throws(() => nextJobState(base, "eve", command(action)), e => e.code === "permission-denied");
    assert.throws(() => nextJobState(base, "bob", command(action, 1)), e => e.code === "aborted");
  }
});
test("strict command schema rejects forged roles, bad reasons, paths, versions and short notes", () => {
  for (const extras of [{ customerUid: "eve" }, { requestId: "a/b" }, { actionId: "../bad" }, { version: -1 }, { version: 0.5 },
    { version: 2147483647 }, { note: "short" }, { note: "x".repeat(1001) }, { reasonCode: "FAKE" }])
    assert.throws(() => command("REQUEST_CANCEL", 0, extras));
  assert.throws(() => command("START", 0, { reasonCode: "OTHER" }));
});
