"use strict";
const { test } = require("node:test");
const assert = require("node:assert/strict");
const { mobile, phoneMatches, listingTargets } = require("../trust-policy");
const verified = { phoneNumber: "+905551234567", disabled: false, providerData: [{ providerId: "phone" }] };
test("badge needs actual active Auth phone provider and exact private contact", () => {
  assert.equal(phoneMatches(verified, "0555 123 45 67"), true);
  for (const input of ["05551234568", "", "05xx xxx xx xx", "call 05551234567", null])
    assert.equal(!!phoneMatches(verified, input), false);
  for (const account of [null, { ...verified, disabled: true }, { ...verified, providerData: [] }, { ...verified, providerData: undefined }, { ...verified, phoneNumber: null }])
    assert.equal(!!phoneMatches(account, "05551234567"), false);
  assert.equal(phoneMatches(verified, "05551234567", { deletionStatus: "REQUESTED" }), false);
  assert.equal(mobile("+90 (555) 123-45-67"), "+905551234567");
});
test("bounded listing lookup rejects paths, invalid kinds and unbounded requests", () => {
  assert.deepEqual(listingTargets({ kind: "providers", ids: ["a", "a"] }), { kind: "providers", ids: ["a"] });
  for (const data of [null, {kind:"users",ids:["a"]}, {kind:"providers",ids:[]},
    {kind:"requests",ids:["../users"]}, {kind:"providers",ids:Array(51).fill("a")}])
    assert.throws(() => listingTargets(data));
});
