import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { before, beforeEach, after, test } from 'node:test';
import { initializeTestEnvironment, assertSucceeds, assertFails } from '@firebase/rules-unit-testing';
import { doc, getDoc, getDocs, setDoc, updateDoc, collection, query, where, or, serverTimestamp, writeBatch, deleteDoc, runTransaction, Timestamp } from 'firebase/firestore';
const f = JSON.parse(readFileSync(new URL('./fixtures.json', import.meta.url)));
let env;
const identities = new WeakMap();
const db = uid => {
  const database = (uid ? env.authenticatedContext(uid, { email: `${uid}@example.com` }) : env.unauthenticatedContext()).firestore();
  if (uid) { identities.set(database, uid); if (database._delegate) identities.set(database._delegate, uid); }
  return database;
};
const operationFor = path => ({providers:'listing', requests:'listing', quotes:'quote', conversations: path.split('/').length === 4 ? 'message' : 'conversation', reports:'report'}[path.split('/')[0]]);
// Exercise the exact client transaction contract for every valid and invalid create.
// Invalid payload tests still carry a valid budget, so their original guards remain tested.
async function budgetedSet(ref, data, extraWrites) {
  const uid = identities.get(ref.firestore);
  const operation = operationFor(ref.path);
  if (!uid || !operation || (ref.path.startsWith('conversations/') && ref.path.split('/').length !== 2 && !ref.path.includes('/messages/'))) return setDoc(ref, data);
  return runTransaction(ref.firestore, async transaction => {
    const budgetRef = doc(ref.firestore, `users/${uid}/writeBudgets/${operation}`);
    const existing = await transaction.get(budgetRef);
    const previous = existing.data();
    const reset = !existing.exists() || Date.now() - previous.windowStartedAt.toMillis() >= 3_600_000;
    transaction.set(budgetRef, {count:reset ? 1 : previous.count + 1,
      windowStartedAt:reset ? serverTimestamp() : previous.windowStartedAt,
      updatedAt:serverTimestamp(), target:ref});
    transaction.set(ref, data);
    extraWrites?.(transaction);
  });
}

const stamp = () => ({createdAt: serverTimestamp(), updatedAt: serverTimestamp()});
const provider = (overrides = {}) => ({ ownerUid: 'bob', visibility: 'published', data: {...f.provider, ...overrides}, ...stamp() });
const request = (overrides = {}) => ({ ownerUid: 'alice', visibility: 'published', acceptedQuoteId: '', acceptedProviderUid: '', data: {...f.request, ...overrides}, ...stamp() });
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
    await Promise.all([budgetedSet(doc(d,'providers/p'),provider()),budgetedSet(doc(d,'requests/r'),request()),budgetedSet(doc(d,'quotes/q'),quote()),budgetedSet(doc(d,'conversations/c'),conversation())]);
  });
});
after(async () => env?.cleanup());
test('urgent request uses real date/time; malformed calendars, times and blank titles denied', async () => {
  const d = db('alice');
  await assertSucceeds(budgetedSet(doc(d,'requests/urgent'),request({id:'urgent',urgencyMode:'EMERGENCY'})));
  for (const [field, values] of Object.entries({
    title: ['', '   ', '\t\n', 'x'.repeat(121)],
    eventOrJobDate: ['', 'Hemen / Bugün', '2026-02-29', '2026-02-31', '2026-04-31', '2026-13-01', '2026-00-10', '2026-10-00', '2026-1-01', '1900-02-29'],
    eventTime: ['', 'En geç 1 saat içinde', '24:00', '25:70', '12:60', '9:00', '12:00:00'],
  })) {
    for (const value of values) {
      await assertFails(budgetedSet(doc(d,'requests/invalid'),request({id:'invalid',[field]:value})));
    }
  }
  for (const [id, date, time] of [['leap','2028-02-29','00:00'],['century','2000-02-29','23:59']]) {
    await assertSucceeds(budgetedSet(doc(d,`requests/${id}`),request({id,eventOrJobDate:date,eventTime:time})));
  }
});
test('private profile own create/read; strangers, anonymous, roles and UID mutation denied', async () => {
  const d=db('alice'); const ref=doc(d,'users/alice');
  const data={uid:'alice',displayName:'Alice',email:'alice@example.com',...stamp()};
  await assertSucceeds(setDoc(ref,data));
  await assertSucceeds(getDoc(ref));
  await assertFails(getDoc(doc(db('bob'),'users/alice')));
  await assertFails(getDoc(doc(db(null),'users/alice')));
  await assertFails(updateDoc(ref,{uid:'bob',updatedAt:serverTimestamp()}));
  await assertFails(updateDoc(ref,{role:'admin',updatedAt:serverTimestamp()}));
  await assertFails(updateDoc(ref,{isAdmin:true,updatedAt:serverTimestamp()}));
  await assertFails(updateDoc(ref,{createdAt:new Date(0),updatedAt:serverTimestamp()}));
  await assertFails(getDoc(doc(db('alice'),'unknownSystem/secret')));
  await assertFails(updateDoc(ref,{email:'forged@example.com',updatedAt:serverTimestamp()}));
  await assertSucceeds(updateDoc(ref,{displayName:'Alice Updated',updatedAt:serverTimestamp()}));
});
test('authenticated public feeds; unauthenticated denied',async()=>{
  await assertSucceeds(getDocs(query(collection(db('alice'),'providers'),where('visibility','==','published'))));
  await assertSucceeds(getDocs(query(collection(db('alice'),'requests'),where('visibility','==','published'))));
  await assertFails(getDocs(collection(db(null),'requests')));
});
test('request create UID derived; contacts private and no address/phone in feed',async()=>{
  const d=db('alice');
  await assertSucceeds(budgetedSet(doc(d,'requests/new'),request({id:'new'}), transaction =>
    transaction.set(doc(d,'requestContacts/new'),{ownerUid:'alice',phone:'05551234567',address:'Özel adres',updatedAt:serverTimestamp()})));
  await assertFails(getDoc(doc(db('bob'),'requestContacts/new')));
  await assertSucceeds(getDoc(doc(d,'requestContacts/new')));
  await assertFails(budgetedSet(doc(db('bob'),'requests/x'),request({id:'x'})));
  await assertFails(budgetedSet(doc(d,'requests/unsafe'),request({id:'unsafe',address:'Secret'})));
  await assertFails(budgetedSet(doc(d,'requests/unsafe'),request({id:'unsafe',customerPhone:'555'})));
  await assertFails(budgetedSet(doc(d,'requests/unsafe'),request({id:'unsafe',admin:true})));
});
test('providers cannot forge ownership, rating or safety badges',async()=>{
  await assertSucceeds(budgetedSet(doc(db('bob'),'providers/new'),provider({id:'new'})));
  await assertFails(budgetedSet(doc(db('alice'),'providers/new2'),provider({id:'new2'})));
  for(const change of [{rating:5},{reviewCount:99},{verifiedSafeBadge:true},{phoneVerified:true},{phone:'555'}])
    await assertFails(budgetedSet(doc(db('bob'),'providers/unsafe'),provider({id:'unsafe',...change})));
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
  await assertSucceeds(budgetedSet(doc(db('bob'),'quotes/new'),quote({data:{...f.quote,id:'new'}})));
  await assertFails(budgetedSet(doc(db('eve'),'quotes/forged'),quote({data:{...f.quote,id:'forged'}})));
  await assertFails(budgetedSet(doc(db('bob'),'quotes/forged'),quote({customerUid:'eve',data:{...f.quote,id:'forged',customerUid:'eve'}})));
  await assertFails(budgetedSet(doc(db('bob'),'quotes/forged'),quote({data:{...f.quote,id:'forged',providerId:'missing'}})));
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
  await assertSucceeds(budgetedSet(doc(db('bob'),'quotes/q2'),quote({data:{...f.quote,id:'q2'}})));
  await assertSucceeds(accept(db('alice')));
  await assertFails(accept(db('alice'),'q2'));
  await assertFails(updateDoc(doc(db('alice'),'quotes/q'),{status:'REJECTED',updatedAt:serverTimestamp()}));
});
test('race between accepts commits at most one winner',async()=>{
  await assertSucceeds(budgetedSet(doc(db('bob'),'quotes/q2'),quote({data:{...f.quote,id:'q2'}})));
  const outcomes=await Promise.allSettled([accept(db('alice')),accept(db('alice'),'q2')]);
  if(outcomes.filter(r=>r.status==='fulfilled').length!==1) throw new Error('Expected exactly one accepted quote');
});
test('accepted provider may read contacts; stranger may not',async()=>{
  await env.withSecurityRulesDisabled(c=>budgetedSet(doc(c.firestore(),'requestContacts/r'),{ownerUid:'alice',phone:'555',address:'Private',updatedAt:serverTimestamp()}));
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
test('provider can withdraw pending offer only; customer/outsider denied, withdrawal final',async()=>{
  const ref = doc(db('bob'),'quotes/q');
  await assertFails(updateDoc(doc(db('alice'),'quotes/q'),{status:'WITHDRAWN',updatedAt:serverTimestamp()}));
  await assertFails(updateDoc(doc(db('eve'),'quotes/q'),{status:'WITHDRAWN',updatedAt:serverTimestamp()}));
  await assertFails(updateDoc(ref,{status:'REJECTED',updatedAt:serverTimestamp()}));
  await assertFails(updateDoc(ref,{'data.price':'0',updatedAt:serverTimestamp()}));
  await assertSucceeds(updateDoc(ref,{status:'WITHDRAWN',updatedAt:serverTimestamp()}));
  await assertFails(accept(db('alice')));
  await assertFails(updateDoc(ref,{status:'PENDING',updatedAt:serverTimestamp()}));
  await assertFails(updateDoc(ref,{status:'REJECTED',updatedAt:serverTimestamp()}));
});
test('conversations list scoped, outsider denied, participants immutable',async()=>{
  await assertSucceeds(getDoc(doc(db('alice'),'conversations/c')));
  await assertSucceeds(getDocs(query(collection(db('bob'),'conversations'),where('participantUids','array-contains','bob'))));
  await assertFails(getDoc(doc(db('eve'),'conversations/c')));
  await assertFails(getDocs(collection(db('alice'),'conversations')));
  await assertFails(updateDoc(doc(db('alice'),'conversations/c'),{participantUids:['alice','eve'],updatedAt:serverTimestamp()}));
  await assertSucceeds(budgetedSet(doc(db('alice'),'conversations/new'),conversation()));
  await assertFails(budgetedSet(doc(db('eve'),'conversations/forged'),conversation()));
});
test('message sender bound to auth; outsider, spoofing, edits, deletes and oversized text denied',async()=>{
  await assertSucceeds(budgetedSet(doc(db('alice'),'conversations/c/messages/m'),message()));
  await assertSucceeds(getDoc(doc(db('bob'),'conversations/c/messages/m')));
  await assertFails(getDoc(doc(db('eve'),'conversations/c/messages/m')));
  await assertFails(budgetedSet(doc(db('eve'),'conversations/c/messages/x'),message({id:'x'})));
  await assertFails(budgetedSet(doc(db('bob'),'conversations/c/messages/x'),message({id:'x'})));
  await assertFails(budgetedSet(doc(db('alice'),'conversations/c/messages/x'),message({id:'x',senderId:'bob'})));
  await assertFails(budgetedSet(doc(db('alice'),'conversations/c/messages/x'),message({id:'x',text:'x'.repeat(4001)})));
  await assertFails(updateDoc(doc(db('alice'),'conversations/c/messages/m'),{'data.text':'edited'}));
  await assertFails(deleteDoc(doc(db('alice'),'conversations/c/messages/m')));
});
test('favorites isolated; unknown collections and payment writes denied',async()=>{
  await assertSucceeds(budgetedSet(doc(db('alice'),'users/alice/favorites/p'),{createdAt:serverTimestamp()}));
  await assertFails(getDocs(collection(db('bob'),'users/alice/favorites')));
  await assertFails(budgetedSet(doc(db('alice'),'payments/fake'),{paid:true}));
  await assertFails(budgetedSet(doc(db('alice'),'random/x'),{}));
});

test('blocks bidirectional for new messages/conversations/offers; own block records private',async()=>{
  await assertSucceeds(budgetedSet(doc(db('alice'),'users/alice/blocks/bob'),{blockedUid:'bob',createdAt:serverTimestamp()}));
  await assertFails(getDoc(doc(db('bob'),'users/alice/blocks/bob')));
  await assertFails(budgetedSet(doc(db('alice'),'users/alice/blocks/alice'),{blockedUid:'alice',createdAt:serverTimestamp()}));
  await assertFails(budgetedSet(doc(db('alice'),'conversations/new'),conversation()));
  await assertFails(budgetedSet(doc(db('bob'),'conversations/new'),conversation()));
  await assertFails(budgetedSet(doc(db('alice'),'conversations/c/messages/x'),message({id:'x'})));
  await assertFails(budgetedSet(doc(db('bob'),'quotes/x'),quote({data:{...f.quote,id:'x'}})));
  await assertSucceeds(getDoc(doc(db('bob'),'conversations/c'))); // readable history as evidence
  await assertSucceeds(deleteDoc(doc(db('alice'),'users/alice/blocks/bob')));
  await assertSucceeds(budgetedSet(doc(db('alice'),'conversations/c/messages/x'),message({id:'x'})));
});
test('device tokens and structured reports owner scoped; moderator fields cannot be client forged',async()=>{
  const id='a'.repeat(64);
  await assertSucceeds(budgetedSet(doc(db('alice'),`users/alice/devices/${id}`),{token:'device-token-for-testing-123',platform:'android',updatedAt:serverTimestamp()}));
  await assertFails(getDoc(doc(db('bob'),`users/alice/devices/${id}`)));
  await assertFails(budgetedSet(doc(db('alice'),'users/bob/devices/'+id),{token:'device-token-for-testing-123',platform:'android',updatedAt:serverTimestamp()}));
  const report=reportPayload(db('alice'));
  await assertSucceeds(budgetedSet(doc(db('alice'),'reports/r'),report));
  await assertSucceeds(getDoc(doc(db('alice'),'reports/r')));
  await assertFails(getDoc(doc(db('bob'),'reports/r')));
  await assertFails(updateDoc(doc(db('alice'),'reports/r'),{status:'reviewed'}));
  await assertFails(budgetedSet(doc(db('alice'),'reports/x'),{...report,status:'reviewed'}));
  await assertFails(budgetedSet(doc(db('alice'),'moderationAudit/fake'),report));
});
test('geo catalog identity valid, precise coordinates and unknown neighborhoods denied',async()=>{
  await assertFails(budgetedSet(doc(db('alice'),'requests/bad'),request({id:'bad',neighborhoodId:'unknown'})));
  await assertFails(budgetedSet(doc(db('alice'),'requests/bad'),request({id:'bad',latitude:38.4,longitude:27.1})));
  await assertFails(budgetedSet(doc(db('alice'),'requests/bad'),request({id:'bad',publicGeoHash:'exact12345'})));
  await assertSucceeds(budgetedSet(doc(db('alice'),'requests/good'),request({id:'good',publicGeoHash:'swb97'})));
});
test('hidden listing omitted from public query and outsider direct read; owner can read own',async()=>{
  await env.withSecurityRulesDisabled(c=>updateDoc(doc(c.firestore(),'providers/p'),{visibility:'hidden','data.isReported':true}));
  await assertFails(getDoc(doc(db('alice'),'providers/p')));
  await assertSucceeds(getDoc(doc(db('bob'),'providers/p')));
  const feed=await assertSucceeds(getDocs(query(collection(db('alice'),'providers'),where('visibility','==','published'))));
  if(feed.docs.some(x=>x.id==='p'))throw new Error('Hidden listing leaked');
});
test('only server can write photo metadata; photo attachment requires own authorized uploaded media',async()=>{
  await assertFails(budgetedSet(doc(db('alice'),'conversations/c/media/photo'),{uploaderUid:'alice'}));
  await assertFails(budgetedSet(doc(db('alice'),'conversations/c/messages/p'),message({id:'p',hasPhotoAttachment:true,photoMediaId:'missing'})));
  await env.withSecurityRulesDisabled(c=>budgetedSet(doc(c.firestore(),'conversations/c/media/photo'),{uploaderUid:'alice',storagePath:'private',contentType:'image/jpeg',sizeBytes:3,createdAt:serverTimestamp()}));
  await assertSucceeds(budgetedSet(doc(db('alice'),'conversations/c/messages/p'),message({id:'p',hasPhotoAttachment:true,photoMediaId:'photo'})));
  await assertFails(getDoc(doc(db('eve'),'conversations/c/media/photo')));
});

const budgets = {listing:10, quote:60, conversation:30, message:240, report:10};
const reportPayload = (d = db('alice'), overrides = {}) => ({reporterUid:'alice',targetType:'listing',targetId:'provider:p',targetUid:'bob',
  targetRef:doc(d,'providers/p'),conversationId:'',reason:'fraud',details:'Kontrol edin',status:'pending',createdAt:serverTimestamp(),...overrides});
const creations = (d, suffix) => [
  {operation:'listing', uid:'bob', ref:doc(d,`providers/${suffix}`), payload:provider({id:suffix})},
  {operation:'quote', uid:'bob', ref:doc(d,`quotes/${suffix}`), payload:quote({data:{...f.quote,id:suffix}})},
  {operation:'conversation', uid:'alice', ref:doc(d,`conversations/${suffix}`), payload:conversation()},
  {operation:'message', uid:'alice', ref:doc(d,`conversations/c/messages/${suffix}`), payload:message({id:suffix})},
  {operation:'report', uid:'alice', ref:doc(d,`reports/${suffix}`), payload:reportPayload(d)},
];
test('listing reports require an existing published target and its authoritative owner', async () => {
  const d=db('alice');
  await assertSucceeds(budgetedSet(doc(d,'reports/listing'),reportPayload(d)));
  const legacy = reportPayload(d);
  delete legacy.targetRef;
  await assertFails(budgetedSet(doc(d,'reports/missing-reference'),legacy));
  for (const override of [
    {targetUid:'eve'}, {targetId:'request:p'}, {targetRef:doc(d,'requests/r')},
    {targetId:'provider:missing',targetRef:doc(d,'providers/missing')},
    {targetRef:'providers/p'}, {conversationId:'c'}, {targetRef:doc(d,'providers/p/private/nested')}
  ]) await assertFails(budgetedSet(doc(d,'reports/forged-listing'),reportPayload(d,override)));
  await env.withSecurityRulesDisabled(c=>updateDoc(doc(c.firestore(),'providers/p'),{visibility:'hidden'}));
  await assertFails(budgetedSet(doc(d,'reports/hidden'),reportPayload(d)));
  assert.equal((await getDoc(doc(d,'users/alice/writeBudgets/report'))).data().count,1);
});
test('request listing reports bind the request owner and distinguish listing collections', async () => {
  const d=db('bob');
  const payload=reportPayload(d,{reporterUid:'bob',targetId:'request:r',targetUid:'alice',targetRef:doc(d,'requests/r')});
  await assertSucceeds(budgetedSet(doc(d,'reports/request'),payload));
  await assertFails(budgetedSet(doc(d,'reports/wrong-collection'),{...payload,targetId:'provider:r'}));
});
test('conversation reports require both reporter and claimed target to be existing participants', async () => {
  const d=db('alice');
  const payload=reportPayload(d,{targetType:'conversation',targetId:'c',targetRef:doc(d,'conversations/c')});
  await assertSucceeds(budgetedSet(doc(d,'reports/chat'),payload));
  await assertFails(budgetedSet(doc(d,'reports/wrong-user'),{...payload,targetUid:'eve'}));
  await assertFails(budgetedSet(doc(d,'reports/wrong-ref'),{...payload,targetRef:doc(d,'conversations/other')}));
  await assertFails(budgetedSet(doc(d,'reports/missing-chat'),{...payload,targetId:'other',targetRef:doc(d,'conversations/other')}));
  const eve=db('eve');
  await assertFails(budgetedSet(doc(eve,'reports/outsider-chat'),{...payload,reporterUid:'eve',targetRef:doc(eve,'conversations/c')}));
  assert.equal((await getDoc(doc(eve,'users/eve/writeBudgets/report'))).exists(),false);
});
test('message reports require its actual sender, participant visibility and exact parent reference', async () => {
  await env.withSecurityRulesDisabled(c=>setDoc(doc(c.firestore(),'conversations/c/messages/m'),message({id:'m'})));
  const d=db('bob');
  const payload=reportPayload(d,{reporterUid:'bob',targetType:'message',targetId:'m',targetUid:'alice',
    targetRef:doc(d,'conversations/c/messages/m'),conversationId:'c'});
  await assertSucceeds(budgetedSet(doc(d,'reports/message'),payload));
  for (const override of [{targetUid:'eve'},{conversationId:''},{conversationId:'other'},{targetId:'missing'},
    {targetRef:doc(d,'conversations/c')},{targetRef:doc(d,'conversations/c/messages/m/extra/ref')}])
    await assertFails(budgetedSet(doc(d,'reports/forged-message'),{...payload,...override}));
  const eve=db('eve');
  await assertFails(budgetedSet(doc(eve,'reports/outsider-message'),{...payload,reporterUid:'eve',targetRef:doc(eve,'conversations/c/messages/m')}));
  assert.equal((await getDoc(doc(eve,'users/eve/writeBudgets/report'))).exists(),false);
  assert.equal((await getDoc(doc(d,'users/bob/writeBudgets/report'))).data().count,1);
  const alice=db('alice');
  await assertFails(budgetedSet(doc(alice,'reports/blame-other-sender'),{...payload,reporterUid:'alice',targetUid:'bob',targetRef:doc(alice,'conversations/c/messages/m')}));
  await assertFails(budgetedSet(doc(d,'reports/missing-message'),{...payload,targetId:'missing',targetRef:doc(d,'conversations/c/messages/missing')}));
});
test('blocked chat participants can still report existing evidence including colon-containing IDs', async () => {
  const id='alice:bob:provider:p';
  await env.withSecurityRulesDisabled(async c=>{
    const admin=c.firestore();
    await setDoc(doc(admin,`conversations/${id}`),conversation());
    await setDoc(doc(admin,`conversations/${id}/messages/m:1`),message({id:'m:1',conversationId:id}));
  });
  const alice=db('alice'), bob=db('bob');
  await setDoc(doc(alice,'users/alice/blocks/bob'),{blockedUid:'bob',createdAt:serverTimestamp()});
  await assertSucceeds(budgetedSet(doc(alice,'reports/blocked-chat'),reportPayload(alice,{targetType:'conversation',targetId:id,targetRef:doc(alice,`conversations/${id}`)})));
  await assertSucceeds(budgetedSet(doc(bob,'reports/blocked-message'),reportPayload(bob,{reporterUid:'bob',targetType:'message',
    targetId:'m:1',targetUid:'alice',targetRef:doc(bob,`conversations/${id}/messages/m:1`),conversationId:id})));
});
test('user reports bind a canonical UID reference without requiring a private profile document', async () => {
  const d=db('alice');
  const payload=reportPayload(d,{targetType:'user',targetId:'bob',targetUid:'bob',targetRef:doc(d,'users/bob')});
  await assertSucceeds(budgetedSet(doc(d,'reports/user'),payload)); // Auth existence is a server moderation responsibility.
  for (const override of [{targetId:'eve'},{targetRef:doc(d,'users/eve')},{targetUid:'bob/other'},{targetId:'bob/other'},
    {targetRef:doc(d,'providerContacts/bob')}]) await assertFails(budgetedSet(doc(d,'reports/forged-user'),{...payload,...override}));
});
test('reports cannot refer to a target created in the same atomic batch', async () => {
  const d=db('alice');
  const payload=reportPayload(d,{targetType:'conversation',targetId:'fresh',targetRef:doc(d,'conversations/fresh')});
  await assertFails(budgetedSet(doc(d,'reports/new-evidence'),payload, tx=>{
    tx.set(doc(d,'conversations/fresh'),conversation());
    tx.set(doc(d,'users/alice/writeBudgets/conversation'),{count:1,windowStartedAt:serverTimestamp(),updatedAt:serverTimestamp(),target:doc(d,'conversations/fresh')});
  }));
  assert.equal((await getDoc(doc(d,'users/alice/writeBudgets/report'))).exists(),false);
  assert.equal((await getDoc(doc(d,'users/alice/writeBudgets/conversation'))).exists(),false);
});
async function seedBudget(uid, operation, count, ageMs = 0) {
  await env.withSecurityRulesDisabled(c => {
    const d = c.firestore();
    const time = Timestamp.fromMillis(Date.now() - ageMs);
    return setDoc(doc(d,`users/${uid}/writeBudgets/${operation}`), {count,windowStartedAt:time,updatedAt:time,target:doc(d,'providers/p')});
  });
}
async function inspectAsAdmin(path) {
  let snapshot;
  await env.withSecurityRulesDisabled(async c => { snapshot = await getDoc(doc(c.firestore(),path)); });
  return snapshot;
}
function manualCreate(d, uid, operation, target, payload, overrides = {}, extraWrites) {
  const batch = writeBatch(d);
  batch.set(doc(d,`users/${uid}/writeBudgets/${operation}`), {count:1,windowStartedAt:serverTimestamp(),updatedAt:serverTimestamp(),target,...overrides});
  batch.set(target,payload);
  extraWrites?.(batch);
  return batch.commit();
}
test('every charged write path denies a direct SDK create without its atomic budget', async () => {
  for (const operation of Object.keys(budgets)) {
    const uid = ['listing','quote'].includes(operation) ? 'bob' : 'alice';
    const create = creations(db(uid),`unbudgeted-${operation}`).find(item => item.operation === operation);
    await assertFails(setDoc(create.ref,create.payload));
  }
});
test('all five hourly quotas permit the final unit and reject the next valid create', async () => {
  for (const [operation,limit] of Object.entries(budgets)) {
    const uid = ['listing','quote'].includes(operation) ? 'bob' : 'alice';
    await seedBudget(uid,operation,limit-1);
    const d = db(uid);
    const last = creations(d,`last-${operation}`).find(item => item.operation === operation);
    const excess = creations(d,`excess-${operation}`).find(item => item.operation === operation);
    await assertSucceeds(budgetedSet(last.ref,last.payload));
    await assertFails(budgetedSet(excess.ref,excess.payload));
    assert.equal((await getDoc(doc(d,`users/${uid}/writeBudgets/${operation}`))).data().count,limit);
  }
});
test('provider and request listing creation share a single UID quota', async () => {
  const d = db('bob');
  await seedBudget('bob','listing',9);
  const ownedRequest = {...request({id:'combined',ownerUid:'bob'}),ownerUid:'bob'};
  await assertSucceeds(budgetedSet(doc(d,'requests/combined'),ownedRequest));
  await assertFails(budgetedSet(doc(d,'providers/over-shared'),provider({id:'over-shared'})));
  assert.equal((await getDoc(doc(d,'users/bob/writeBudgets/listing'))).data().count,10);
});
test('expired full quota resets only with fresh server timestamps and count one', async () => {
  const d = db('bob');
  await seedBudget('bob','listing',10,3_601_000);
  await assertFails(manualCreate(d,'bob','listing',doc(d,'providers/invalid-reset'),provider({id:'invalid-reset'}),{count:2}));
  await assertSucceeds(budgetedSet(doc(d,'providers/reset'),provider({id:'reset'})));
  const result = (await getDoc(doc(d,'users/bob/writeBudgets/listing'))).data();
  assert.equal(result.count,1);
  assert.ok(Math.abs(Date.now() - result.windowStartedAt.toMillis()) < 10_000);
  assert.equal(result.windowStartedAt.toMillis(),result.updatedAt.toMillis());
  assert.equal(result.target.path,'providers/reset');
});
test('client future/backdated timestamps, altered windows and decrements cannot reset a quota', async () => {
  const d = db('bob');
  const ref = doc(d,'providers/bad-window');
  for (const timestamp of [Timestamp.fromMillis(Date.now()+3_600_000),Timestamp.fromMillis(Date.now()-3_600_000)]) {
    await assertFails(manualCreate(d,'bob','listing',ref,provider({id:'bad-window'}),{windowStartedAt:timestamp}));
    await assertFails(manualCreate(d,'bob','listing',ref,provider({id:'bad-window'}),{updatedAt:timestamp}));
  }
  await seedBudget('bob','listing',5);
  await assertFails(manualCreate(d,'bob','listing',ref,provider({id:'bad-window'}),{count:4}));
  await assertFails(manualCreate(d,'bob','listing',ref,provider({id:'bad-window'}),{count:6})); // fresh window during active hour
  assert.equal((await getDoc(doc(d,'users/bob/writeBudgets/listing'))).data().count,5);
});
test('budget schema rejects strings, wrong operations, extra fields and wrong target binding', async () => {
  const d = db('bob');
  const target = doc(d,'providers/schema');
  const payload = provider({id:'schema'});
  for (const override of [{target:'providers/schema'},{count:1.5},{count:0},{count:2},{extra:true},{target:doc(d,'providers/other')}]) {
    await assertFails(manualCreate(d,'bob','listing',target,payload,override));
  }
  await assertFails(manualCreate(d,'bob','message',target,payload));
  await assertFails(manualCreate(d,'bob','unknown',target,payload));
});
test('preconsumption, replay and deletion cannot manufacture or erase write credits', async () => {
  const d = db('bob');
  const budget = doc(d,'users/bob/writeBudgets/listing');
  const target = doc(d,'providers/replay');
  await assertFails(setDoc(budget,{count:1,windowStartedAt:serverTimestamp(),updatedAt:serverTimestamp(),target}));
  await assertSucceeds(budgetedSet(target,provider({id:'replay'})));
  await assertFails(deleteDoc(budget));
  await assertFails(budgetedSet(target,provider({id:'replay'})));
  const existing = (await getDoc(budget)).data();
  await assertFails(setDoc(budget,{...existing,count:2,updatedAt:serverTimestamp()}));
  assert.equal((await getDoc(budget)).data().count,1);
});
test('budgets are private and cannot be consumed through another UID', async () => {
  const bob = db('bob');
  await assertSucceeds(budgetedSet(doc(bob,'providers/private-budget'),provider({id:'private-budget'})));
  const alice = db('alice');
  await assertFails(getDoc(doc(alice,'users/bob/writeBudgets/listing')));
  const target = doc(alice,'requests/stolen-budget');
  await assertFails(manualCreate(alice,'bob','listing',target,request({id:'stolen-budget'})));
  await assertFails(deleteDoc(doc(alice,'users/bob/writeBudgets/listing')));
});
test('one budget consumption cannot authorize multiple creates in one batch', async () => {
  const d = db('bob');
  const first = doc(d,'providers/batch-first');
  await assertFails(manualCreate(d,'bob','listing',first,provider({id:'batch-first'}),{},batch =>
    batch.set(doc(d,'providers/batch-second'),provider({id:'batch-second'}))));
  assert.equal((await inspectAsAdmin(first.path)).exists(),false);
  assert.equal((await getDoc(doc(d,'users/bob/writeBudgets/listing'))).exists(),false);
});
test('message quota is global per sender across conversations', async () => {
  await env.withSecurityRulesDisabled(c => setDoc(doc(c.firestore(),'conversations/c2'),conversation()));
  await seedBudget('alice','message',239);
  const d = db('alice');
  await assertSucceeds(budgetedSet(doc(d,'conversations/c/messages/global-last'),message({id:'global-last'})));
  await assertFails(budgetedSet(doc(d,'conversations/c2/messages/global-over'),message({id:'global-over',conversationId:'c2'})));
  assert.equal((await getDoc(doc(d,'users/alice/writeBudgets/message'))).data().count,240);
});
test('concurrent transactions at quota boundary commit exactly one target', async () => {
  await seedBudget('bob','listing',9);
  const d = db('bob');
  const outcomes = await Promise.allSettled(['race-a','race-b'].map(id => budgetedSet(doc(d,`providers/${id}`),provider({id}))));
  assert.equal(outcomes.filter(result => result.status === 'fulfilled').length,1);
  assert.equal((await getDoc(doc(d,'users/bob/writeBudgets/listing'))).data().count,10);
  const results = await Promise.all(['race-a','race-b'].map(id => inspectAsAdmin(`providers/${id}`)));
  assert.equal(results.filter(snapshot => snapshot.exists()).length,1);
});
test('a failed target validation rolls back its atomic budget increment', async () => {
  const d = db('alice');
  await seedBudget('alice','listing',5);
  await assertFails(budgetedSet(doc(d,'requests/invalid-budget-target'),request({id:'invalid-budget-target',title:''})));
  assert.equal((await getDoc(doc(d,'users/alice/writeBudgets/listing'))).data().count,5);
  assert.equal((await inspectAsAdmin('requests/invalid-budget-target')).exists(),false);
});
test('conversation preview requires a fresh charged message with matching text in the same transaction', async () => {
  const d = db('alice');
  const chat = doc(d,'conversations/c');
  await assertFails(updateDoc(chat,{lastMessage:'unbudgeted preview',updatedAt:serverTimestamp()}));
  const target = doc(d,'conversations/c/messages/preview');
  const payload = message({id:'preview',text:'Fresh preview'});
  await assertSucceeds(budgetedSet(target,payload,transaction => transaction.update(chat,{lastMessage:payload.data.text,updatedAt:serverTimestamp()})));
  await assertFails(updateDoc(chat,{lastMessage:payload.data.text,updatedAt:serverTimestamp()}));
  await assertFails(budgetedSet(doc(d,'conversations/c/messages/mismatch'),message({id:'mismatch',text:'Actual message'}),transaction =>
    transaction.update(chat,{lastMessage:'Forged preview',updatedAt:serverTimestamp()})));
  assert.equal((await getDoc(doc(d,'users/alice/writeBudgets/message'))).data().count,1);
  assert.equal((await getDoc(chat)).data().lastMessage,'Fresh preview');
});

test('deletion tombstone revokes cached-token access after profile removal; client cannot reset it', async () => {
  await env.withSecurityRulesDisabled(async c => { await setDoc(doc(c.firestore(),'_accountDeletions/alice'),{status:'REQUESTED'}); });
  const a=db('alice');
  await assertFails(getDoc(doc(a,'requests/r')));
  await assertFails(budgetedSet(doc(a,'requests/new'),request({id:'new'})));
  await assertFails(setDoc(doc(a,'users/alice'),{uid:'alice',displayName:'Alice',email:'alice@example.com',...stamp()}));
  await assertFails(deleteDoc(doc(a,'_accountDeletions/alice')));
  await assertFails(getDoc(doc(a,'_accountDeletions/alice')));
  await assertFails(budgetedSet(doc(db('bob'),'quotes/new'),quote({data:{...f.quote,id:'new'}})));
  await assertFails(budgetedSet(doc(db('bob'),'conversations/c/messages/new'),{senderUid:'bob',data:{...f.message,id:'new',senderId:'bob'},...stamp()}));
  await env.withSecurityRulesDisabled(async c => { await updateDoc(doc(c.firestore(),'_accountDeletions/alice'),{status:'COMPLETED'}); });
  await assertFails(getDoc(doc(a,'providers/p')));
});
test('archived listings reject new offers, acceptance and direct republishing', async () => {
  await env.withSecurityRulesDisabled(async c => {
    await updateDoc(doc(c.firestore(),'requests/r'),{visibility:'archived'});
    await updateDoc(doc(c.firestore(),'providers/p'),{visibility:'archived'});
  });
  await assertFails(budgetedSet(doc(db('bob'),'quotes/new'),quote({data:{...f.quote,id:'new'}})));
  await assertFails(updateDoc(doc(db('bob'),'providers/p'),{'data.isOpenForOffers':false,updatedAt:serverTimestamp()}));
  await assertFails(updateDoc(doc(db('bob'),'providers/p'),{visibility:'published',updatedAt:serverTimestamp()}));
  const a=db('alice'),batch=writeBatch(a);
  batch.update(doc(a,'quotes/q'),{status:'ACCEPTED',updatedAt:serverTimestamp()});
  batch.update(doc(a,'requests/r'),{'data.status':'ACCEPTED',acceptedQuoteId:'q',acceptedProviderUid:'bob',updatedAt:serverTimestamp()});
  await assertFails(batch.commit());
});
test('acceptance cannot race against deleting provider; direct scope edits remain denied',async () => {
  await env.withSecurityRulesDisabled(async c => { await setDoc(doc(c.firestore(),'_accountDeletions/bob'),{status:'REQUESTED'}); });
  const a=db('alice'),batch=writeBatch(a);
  batch.update(doc(a,'quotes/q'),{status:'ACCEPTED',updatedAt:serverTimestamp()});
  batch.update(doc(a,'requests/r'),{'data.status':'ACCEPTED',acceptedQuoteId:'q',acceptedProviderUid:'bob',updatedAt:serverTimestamp()});
  await assertFails(batch.commit());
  await assertFails(updateDoc(doc(a,'requests/r'),{'data.title':'Changed scope',updatedAt:serverTimestamp()}));
});
