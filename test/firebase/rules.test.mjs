import { readFileSync } from 'node:fs';
import { before, beforeEach, after, test } from 'node:test';
import { initializeTestEnvironment, assertSucceeds, assertFails } from '@firebase/rules-unit-testing';
import { doc, getDoc, getDocs, setDoc, updateDoc, collection, query, where, or, serverTimestamp, writeBatch, deleteDoc } from 'firebase/firestore';
const f = JSON.parse(readFileSync(new URL('./fixtures.json', import.meta.url)));
let env;
const db = uid => (uid ? env.authenticatedContext(uid, { email: `${uid}@example.com` }) : env.unauthenticatedContext()).firestore();
const stamp = () => ({createdAt: serverTimestamp(), updatedAt: serverTimestamp()});
const provider = (overrides = {}) => ({ ownerUid: 'bob', data: {...f.provider, ...overrides}, ...stamp() });
const request = (overrides = {}) => ({ ownerUid: 'alice', acceptedQuoteId: '', acceptedProviderUid: '', data: {...f.request, ...overrides}, ...stamp() });
const quote = (overrides = {}) => ({providerUid: 'bob', customerUid: 'alice', requestId: 'r', status: 'PENDING', data: {...f.quote}, ...stamp(), ...overrides});
const conversation = () => ({participantUids: ['alice','bob'], names: {alice:'Alice',bob:'Bob'}, relatedItemTitle:'Boya', lastMessage:'', ...stamp()});
const message = (overrides = {}) => ({senderUid:'alice', data:{...f.message, ...overrides}, ...stamp()});
before(async () => {
  env = await initializeTestEnvironment({projectId:'demo-mahallem', firestore:{host:'127.0.0.1',port:8080,rules:readFileSync('firestore.rules','utf8')}});
});
beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async c => {
    const d = c.firestore();
    await Promise.all([setDoc(doc(d,'providers/p'),provider()),setDoc(doc(d,'requests/r'),request()),setDoc(doc(d,'quotes/q'),quote()),setDoc(doc(d,'conversations/c'),conversation())]);
  });
});
after(async () => env?.cleanup());
test('private profile own create/read; strangers, anonymous, roles and UID mutation denied', async () => {
  const d=db('alice'); const ref=doc(d,'users/alice');
  const data={uid:'alice',displayName:'Alice',email:'alice@example.com',...stamp()};
  await assertSucceeds(setDoc(ref,data));
  await assertSucceeds(getDoc(ref));
  await assertFails(getDoc(doc(db('bob'),'users/alice')));
  await assertFails(getDoc(doc(db(null),'users/alice')));
  await assertFails(updateDoc(ref,{uid:'bob',updatedAt:serverTimestamp()}));
  await assertFails(updateDoc(ref,{role:'admin',updatedAt:serverTimestamp()}));
  await assertFails(updateDoc(ref,{email:'forged@example.com',updatedAt:serverTimestamp()}));
  await assertSucceeds(updateDoc(ref,{displayName:'Alice Updated',updatedAt:serverTimestamp()}));
});
test('authenticated public feeds; unauthenticated denied',async()=>{
  await assertSucceeds(getDocs(collection(db('alice'),'providers')));
  await assertSucceeds(getDocs(collection(db('alice'),'requests')));
  await assertFails(getDocs(collection(db(null),'requests')));
});
test('request create UID derived; contacts private and no address/phone in feed',async()=>{
  const d=db('alice'); const batch=writeBatch(d);
  batch.set(doc(d,'requests/new'),request({id:'new'}));
  batch.set(doc(d,'requestContacts/new'),{ownerUid:'alice',phone:'05551234567',address:'Özel adres',updatedAt:serverTimestamp()});
  await assertSucceeds(batch.commit());
  await assertFails(getDoc(doc(db('bob'),'requestContacts/new')));
  await assertSucceeds(getDoc(doc(d,'requestContacts/new')));
  await assertFails(setDoc(doc(db('bob'),'requests/x'),request({id:'x'})));
  await assertFails(setDoc(doc(d,'requests/unsafe'),request({id:'unsafe',address:'Secret'})));
  await assertFails(setDoc(doc(d,'requests/unsafe'),request({id:'unsafe',customerPhone:'555'})));
  await assertFails(setDoc(doc(d,'requests/unsafe'),request({id:'unsafe',admin:true})));
});
test('providers cannot forge ownership, rating or safety badges',async()=>{
  await assertSucceeds(setDoc(doc(db('bob'),'providers/new'),provider({id:'new'})));
  await assertFails(setDoc(doc(db('alice'),'providers/new2'),provider({id:'new2'})));
  for(const change of [{rating:5},{reviewCount:99},{verifiedSafeBadge:true},{phoneVerified:true},{phone:'555'}])
    await assertFails(setDoc(doc(db('bob'),'providers/unsafe'),provider({id:'unsafe',...change})));
  await assertSucceeds(updateDoc(doc(db('bob'),'providers/p'),{'data.isOpenForOffers':false,updatedAt:serverTimestamp()}));
  await assertFails(updateDoc(doc(db('alice'),'providers/p'),{'data.isOpenForOffers':false,updatedAt:serverTimestamp()}));
});
test('quotes visible only to parties; production OR query allowed, unscoped query denied',async()=>{
  await assertSucceeds(getDoc(doc(db('alice'),'quotes/q')));
  await assertSucceeds(getDoc(doc(db('bob'),'quotes/q')));
  await assertFails(getDoc(doc(db('eve'),'quotes/q')));
  await assertSucceeds(getDocs(query(collection(db('alice'),'quotes'),or(where('customerUid','==','alice'),where('providerUid','==','alice')))));
  await assertFails(getDocs(collection(db('alice'),'quotes')));
});
test('quote requires owned provider, correct customer and open request',async()=>{
  await assertSucceeds(setDoc(doc(db('bob'),'quotes/new'),quote({data:{...f.quote,id:'new'}})));
  await assertFails(setDoc(doc(db('eve'),'quotes/forged'),quote({data:{...f.quote,id:'forged'}})));
  await assertFails(setDoc(doc(db('bob'),'quotes/forged'),quote({customerUid:'eve',data:{...f.quote,id:'forged',customerUid:'eve'}})));
  await assertFails(setDoc(doc(db('bob'),'quotes/forged'),quote({data:{...f.quote,id:'forged',providerId:'missing'}})));
  await assertFails(updateDoc(doc(db('bob'),'quotes/q'),{status:'ACCEPTED',updatedAt:serverTimestamp()}));
});
async function accept(d,q='q'){
  const b=writeBatch(d);b.update(doc(d,`quotes/${q}`),{status:'ACCEPTED',updatedAt:serverTimestamp()});
  b.update(doc(d,'requests/r'),{'data.status':'ACCEPTED',acceptedQuoteId:q,acceptedProviderUid:'bob',updatedAt:serverTimestamp()});
  return b.commit();
}
test('acceptance atomic, customer only, only one winner, rejected offer final',async()=>{
  await assertFails(updateDoc(doc(db('alice'),'quotes/q'),{status:'ACCEPTED',updatedAt:serverTimestamp()}));
  await assertFails(accept(db('bob')));
  await assertSucceeds(setDoc(doc(db('bob'),'quotes/q2'),quote({data:{...f.quote,id:'q2'}})));
  await assertSucceeds(accept(db('alice')));
  await assertFails(accept(db('alice'),'q2'));
  await assertFails(updateDoc(doc(db('alice'),'quotes/q'),{status:'REJECTED',updatedAt:serverTimestamp()}));
});
test('race between accepts commits at most one winner',async()=>{
  await assertSucceeds(setDoc(doc(db('bob'),'quotes/q2'),quote({data:{...f.quote,id:'q2'}})));
  const outcomes=await Promise.allSettled([accept(db('alice')),accept(db('alice'),'q2')]);
  if(outcomes.filter(r=>r.status==='fulfilled').length!==1) throw new Error('Expected exactly one accepted quote');
});
test('accepted provider may read contacts; stranger may not',async()=>{
  await env.withSecurityRulesDisabled(c=>setDoc(doc(c.firestore(),'requestContacts/r'),{ownerUid:'alice',phone:'555',address:'Private',updatedAt:serverTimestamp()}));
  await assertFails(getDoc(doc(db('bob'),'requestContacts/r')));
  await assertSucceeds(accept(db('alice')));
  await assertSucceeds(getDoc(doc(db('bob'),'requestContacts/r')));
  await assertFails(getDoc(doc(db('eve'),'requestContacts/r')));
});
test('reject by customer only; cannot mutate quote price or escrow',async()=>{
  await assertFails(updateDoc(doc(db('bob'),'quotes/q'),{status:'REJECTED',updatedAt:serverTimestamp()}));
  await assertFails(updateDoc(doc(db('alice'),'quotes/q'),{'data.price':'1',updatedAt:serverTimestamp()}));
  await assertFails(updateDoc(doc(db('alice'),'quotes/q'),{'data.escrowFunded':true,updatedAt:serverTimestamp()}));
  await assertSucceeds(updateDoc(doc(db('alice'),'quotes/q'),{status:'REJECTED',updatedAt:serverTimestamp()}));
  await assertFails(accept(db('alice')));
});
test('conversations list scoped, outsider denied, participants immutable',async()=>{
  await assertSucceeds(getDoc(doc(db('alice'),'conversations/c')));
  await assertSucceeds(getDocs(query(collection(db('bob'),'conversations'),where('participantUids','array-contains','bob'))));
  await assertFails(getDoc(doc(db('eve'),'conversations/c')));
  await assertFails(getDocs(collection(db('alice'),'conversations')));
  await assertFails(updateDoc(doc(db('alice'),'conversations/c'),{participantUids:['alice','eve'],updatedAt:serverTimestamp()}));
  await assertSucceeds(setDoc(doc(db('alice'),'conversations/new'),conversation()));
  await assertFails(setDoc(doc(db('eve'),'conversations/forged'),conversation()));
});
test('message sender bound to auth; outsider, spoofing, edits, deletes and oversized text denied',async()=>{
  await assertSucceeds(setDoc(doc(db('alice'),'conversations/c/messages/m'),message()));
  await assertSucceeds(getDoc(doc(db('bob'),'conversations/c/messages/m')));
  await assertFails(getDoc(doc(db('eve'),'conversations/c/messages/m')));
  await assertFails(setDoc(doc(db('eve'),'conversations/c/messages/x'),message({id:'x'})));
  await assertFails(setDoc(doc(db('bob'),'conversations/c/messages/x'),message({id:'x'})));
  await assertFails(setDoc(doc(db('alice'),'conversations/c/messages/x'),message({id:'x',senderId:'bob'})));
  await assertFails(setDoc(doc(db('alice'),'conversations/c/messages/x'),message({id:'x',text:'x'.repeat(4001)})));
  await assertFails(updateDoc(doc(db('alice'),'conversations/c/messages/m'),{'data.text':'edited'}));
  await assertFails(deleteDoc(doc(db('alice'),'conversations/c/messages/m')));
});
test('favorites isolated; unknown collections and payment writes denied',async()=>{
  await assertSucceeds(setDoc(doc(db('alice'),'users/alice/favorites/p'),{createdAt:serverTimestamp()}));
  await assertFails(getDocs(collection(db('bob'),'users/alice/favorites')));
  await assertFails(setDoc(doc(db('alice'),'payments/fake'),{paid:true}));
  await assertFails(setDoc(doc(db('alice'),'random/x'),{}));
});
