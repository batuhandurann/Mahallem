import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { initializeApp, deleteApp } from 'firebase/app';
import { getAuth, connectAuthEmulator, createUserWithEmailAndPassword } from 'firebase/auth';
const require = createRequire(new URL('../../functions/package.json', import.meta.url));
const { initializeApp: adminApp, deleteApp: deleteAdminApp } = require('firebase-admin/app');
const { getFirestore, Timestamp } = require('firebase-admin/firestore');
const { getAuth: adminAuth } = require('firebase-admin/auth');
let admin, db, alice, bob, eve;
const apps = [], projectId = 'demo-mahallem', prefix = `reviews-${Date.now()}`;
async function account(name) {
  const app = initializeApp({projectId,apiKey:'emulator-key',appId:`1:123:android:${name}`},`${prefix}-${name}`); apps.push(app);
  const auth = getAuth(app); connectAuthEmulator(auth,`http://${process.env.FIREBASE_AUTH_EMULATOR_HOST}`,{disableWarnings:true});
  const user = (await createUserWithEmailAndPassword(auth,`${prefix}-${name}@example.com`,'Test-password-123!')).user;
  return {uid:user.uid,token:await user.getIdToken()};
}
async function call(name,user,data) {
  const response = await fetch(`http://127.0.0.1:5001/${projectId}/europe-west3/${name}`,{method:'POST',headers:{'content-type':'application/json',...(user?{authorization:`Bearer ${user.token}`}:{})},body:JSON.stringify({data})});
  return {status:response.status,body:await response.json()};
}
async function fixture(suffix,status='ACCEPTED',resetProvider=true) {
  const requestId=`${prefix}-${suffix}`,quoteId=`${requestId}-quote`,providerId=`${prefix}-provider`;
  if(resetProvider) await db.doc(`providers/${providerId}`).set({ownerUid:bob.uid,visibility:'published',data:{id:providerId,rating:0,reviewCount:0},ratingSum:0});
  await db.doc(`requests/${requestId}`).set({ownerUid:alice.uid,acceptedQuoteId:quoteId,acceptedProviderUid:bob.uid,data:{id:requestId,status,escrowStatus:'NONE'}});
  await db.doc(`quotes/${quoteId}`).set({requestId,customerUid:alice.uid,providerUid:bob.uid,status:'ACCEPTED',data:{providerId}});
  return {requestId,providerId};
}
before(async()=>{
  assert.ok(process.env.FIRESTORE_EMULATOR_HOST && process.env.FIREBASE_AUTH_EMULATOR_HOST,'Emulators required; never use live Firebase');
  admin=adminApp({projectId},`${prefix}-admin`); db=getFirestore(admin,'mahallem');
  [alice,bob,eve]=await Promise.all(['alice','bob','eve'].map(account));
});
after(async()=>{ await Promise.all(apps.map(deleteApp)); if(admin) await deleteAdminApp(admin); });
test('real callable lifecycle: customer-only completion/review, concurrent duplicate idempotency, privacy, moderation and aggregate rollback',async()=>{
  const {requestId,providerId}=await fixture('main'), input={requestId,rating:2,comment:'İşçilik geliştirilebilir.'};
  assert.equal((await call('submitJobReview',null,input)).status,401);
  for(const user of [bob,eve]) {
    assert.equal((await call('confirmJobCompletion',user,{requestId})).status,403);
    assert.equal((await call('submitJobReview',user,input)).status,403);
  }
  assert.equal((await call('submitJobReview',alice,input)).status,400);
  assert.equal((await call('confirmJobCompletion',alice,{requestId})).status,200);
  assert.equal((await call('confirmJobCompletion',alice,{requestId})).status,200);
  const duplicate=await Promise.all(Array.from({length:4},()=>call('submitJobReview',alice,input)));
  for(const response of duplicate) assert.equal(response.status,200,JSON.stringify(response.body));
  const provider=(await db.doc(`providers/${providerId}`).get()).data();
  assert.equal(provider.data.reviewCount,1);assert.equal(provider.data.rating,2);assert.equal(provider.ratingSum,2);
  const privateReview=(await db.doc(`jobReviews/${requestId}`).get()).data();
  const publicRef=db.doc(`providers/${providerId}/reviews/${privateReview.reviewId}`);
  const publicReview=(await publicRef.get()).data();
  assert.deepEqual(Object.keys(publicReview).sort(),['comment','createdAt','rating','verifiedJob']);
  assert.equal((await call('submitJobReview',alice,{...input,rating:5})).status,409);
  assert.equal((await call('hideJobReview',bob,{requestId,note:'Kaldır'})).status,403);
  assert.equal((await call('reportJobReview',bob,{providerId,reviewId:privateReview.reviewId,reason:'Kişisel bilgi içeriyor'})).status,200);
  assert.ok((await publicRef.get()).exists,'reports must not automatically remove bad reviews');
  await adminAuth(admin).setCustomUserClaims(eve.uid,{moderator:true});
  // Refresh token through the actual Auth emulator client.
  eve.token=await getAuth(apps.find(a=>a.name===`${prefix}-eve`)).currentUser.getIdToken(true);
  assert.equal((await call('hideJobReview',eve,{requestId,note:'Politika ihlali doğrulandı'})).status,200);
  assert.equal((await call('hideJobReview',eve,{requestId,note:'Tekrar'})).status,200);
  assert.equal((await publicRef.get()).exists,false);
  const hidden=(await db.doc(`providers/${providerId}`).get()).data();
  assert.equal(hidden.data.reviewCount,0);assert.equal(hidden.data.rating,0);
  assert.equal((await call('submitJobReview',alice,{...input,rating:5})).status,409);
});
test('concurrent reviews for distinct jobs update one aggregate without lost increments',async()=>{
  const jobs=[];
  for(let i=0;i<3;i++) {
    const job=await fixture(`parallel-${i}`,'ACCEPTED',i===0);jobs.push(job);
    assert.equal((await call('confirmJobCompletion',alice,{requestId:job.requestId})).status,200);
  }
  const responses=await Promise.all(jobs.map((job,i)=>call('submitJobReview',alice,{requestId:job.requestId,rating:1+2*i,comment:''})));
  for(const r of responses) assert.equal(r.status,200,JSON.stringify(r.body));
  const provider=(await db.doc(`providers/${jobs[0].providerId}`).get()).data();
  assert.equal(provider.data.reviewCount,3);assert.equal(provider.ratingSum,9);assert.equal(provider.data.rating,3);
});
test('cancelled, disputed, expired, forged completions, invalid input and disabled/deleting accounts fail closed',async()=>{
  for(const status of ['CANCELLED','DISPUTED','PENDING']) {
    const {requestId}=await fixture(status);
    assert.equal((await call('confirmJobCompletion',alice,{requestId})).status,400);
    assert.equal((await call('submitJobReview',alice,{requestId,rating:5,comment:''})).status,400);
  }
  const {requestId}=await fixture('expired','COMPLETED');
  await db.doc(`requests/${requestId}`).update({completedByUid:alice.uid,completedAt:Timestamp.fromMillis(Date.now()-31*86400000)});
  assert.equal((await call('submitJobReview',alice,{requestId,rating:5,comment:''})).status,400);
  assert.equal((await call('submitJobReview',alice,{requestId,rating:5,comment:'',customerUid:bob.uid})).status,400);
  await adminAuth(admin).updateUser(alice.uid,{disabled:true});
  assert.equal((await call('submitJobReview',alice,{requestId,rating:5,comment:''})).status,403);
  await adminAuth(admin).updateUser(alice.uid,{disabled:false});
  await db.doc(`users/${alice.uid}`).set({deletionStatus:'REQUESTED'});
  assert.equal((await call('confirmJobCompletion',alice,{requestId})).status,403);
});
