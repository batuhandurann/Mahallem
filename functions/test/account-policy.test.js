"use strict";
const test=require("node:test"),assert=require("node:assert/strict");
const {profilePatch,listingPatch,revision,recentLogin,terminalJob}=require("../account-policy");
test("profile edits bound text, identity and revision",()=>{
  assert.deepEqual(profilePatch({displayName:" Batuhan Duran ",bio:" developer ",revision:""}),{displayName:"Batuhan Duran",bio:"developer"});
  for(const input of [{displayName:" ",bio:"",revision:""},{displayName:"a".repeat(81),bio:"",revision:""},{displayName:"Valid",bio:"x".repeat(1001),revision:""},{displayName:"Valid",bio:"",revision:"",role:"admin"},{displayName:"Valid",bio:"",revision:"",uid:"other"},{displayName:"Valid",bio:""}]) assert.throws(()=>profilePatch(input));
});
test("listing edits cannot change ownership, agreement, geography or trust",()=>{
  assert.deepEqual(listingPatch("providers",{title:" Painter ",bio:"Details"}),{title:"Painter",bio:"Details"});
  for(const field of ["ownerUid","id","status","visibility","rating","phoneVerified","categoryId","provinceId","isReported"]) assert.throws(()=>listingPatch("providers",{[field]:"forged"}));
  assert.throws(()=>listingPatch("requests",{title:" "}));assert.throws(()=>listingPatch("invalid",{title:"Title"}));
});
test("edited schedules reject impossible dates and times",()=>{
  for(const date of ["2026-02-29","2026-04-31","2026-13-01","2026-1-01"]) assert.throws(()=>listingPatch("requests",{eventOrJobDate:date}));
  for(const time of ["24:00","12:60","9:00"]) assert.throws(()=>listingPatch("requests",{eventTime:time}));
  assert.equal(listingPatch("requests",{eventOrJobDate:"2028-02-29",eventTime:"23:59"}).eventTime,"23:59");
});
test("fresh auth_time is required and revision preserves nanoseconds",()=>{
  assert.equal(recentLogin({auth_time:100},399000),true);
  for(const token of [{},{auth_time:"100"},{auth_time:99},{auth_time:401}]) assert.equal(recentLogin(token,400000),false);
  assert.notEqual(revision({seconds:123,nanoseconds:1}),revision({seconds:123,nanoseconds:2}));
});
test("historical accepted offers can close only after terminal undisputed job",()=>{
  assert.equal(terminalJob({data:{status:"COMPLETED",escrowStatus:"NONE"}}),true);
  assert.equal(terminalJob({data:{status:"CANCELLED",escrowStatus:"NONE"}}),true);
  for(const data of [null,{data:{status:"ACCEPTED"}},{data:{status:"COMPLETED",escrowStatus:"DISPUTED"}},{data:{status:"COMPLETED",escrowStatus:"LOCKED"}}]) assert.equal(terminalJob(data),false);
});

test("single-line names, titles and displayed prices reject control characters",()=>{
  assert.throws(()=>profilePatch({displayName:"Bad\nName",bio:"",revision:""}));
  for(const field of ["name","title","hourlyOrBasePrice"]) assert.throws(()=>listingPatch("providers",{[field]:"Bad\nInput"}));
  assert.equal(listingPatch("providers",{bio:"Line one\nLine two"}).bio,"Line one\nLine two");
  assert.throws(()=>listingPatch("providers",JSON.parse('{"__proto__":"forged"}')));
});
