"use strict";
const test = require("node:test"), assert = require("node:assert/strict");
const { reviewInput, reviewEligible, WINDOW_MS } = require("../reviews");
const now = Date.now();
const job = { ownerUid: "alice", acceptedProviderUid: "bob", data: { id: "r", status: "COMPLETED" }, completedByUid: "alice", completedAt: { toMillis: () => now - 1000 } };
const quote = { requestId: "r", customerUid: "alice", providerUid: "bob", status: "ACCEPTED" };
test("strict review input rejects identity overrides, traversal, malformed scores and unbounded text", () => {
  assert.deepEqual(reviewInput({requestId:"r",rating:1,comment:"  honest  "}), {requestId:"r",rating:1,comment:"honest"});
  for (const rating of [0,6,3.5,"5",NaN,Infinity]) assert.throws(() => reviewInput({requestId:"r",rating,comment:""}));
  for (const extra of ["customerUid","providerUid","completedAt","verifiedJob"]) assert.throws(() => reviewInput({requestId:"r",rating:5,comment:"",[extra]:true}));
  assert.throws(() => reviewInput({requestId:"../r",rating:5,comment:""}));
  assert.throws(() => reviewInput({requestId:"r",rating:5,comment:"x".repeat(2001)}));
});
test("only the completed job customer with authoritative accepted quote and completion timestamp is eligible", () => {
  assert.equal(reviewEligible(job,quote,"alice",now),true);
  for (const uid of ["bob","eve",null]) assert.equal(reviewEligible(job,quote,uid,now),false);
  for (const status of ["PENDING","ACCEPTED","CANCELLED","DISPUTED"]) assert.equal(reviewEligible({...job,data:{...job.data,status}},quote,"alice",now),false);
  for (const patch of [{customerUid:"eve"},{providerUid:"alice"},{providerUid:"eve"},{status:"REJECTED"},{requestId:"other"}]) assert.equal(reviewEligible(job,{...quote,...patch},"alice",now),false);
  for (const patch of [{completedAt:null},{completedByUid:"bob"},{completedAt:{toMillis:()=>now+1}},{completedAt:{toMillis:()=>now-WINDOW_MS-1}}]) assert.equal(reviewEligible({...job,...patch},quote,"alice",now),false);
});
