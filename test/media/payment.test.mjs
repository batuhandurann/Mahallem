import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { randomUUID } from 'node:crypto';
import { initializeApp, deleteApp } from 'firebase/app';
import { getAuth, connectAuthEmulator, createUserWithEmailAndPassword } from 'firebase/auth';
import { getFirestore as clientFirestore, connectFirestoreEmulator, doc, getDoc, setDoc } from 'firebase/firestore';
const backend = createRequire(new URL('../../functions/package.json', import.meta.url));
const { initializeApp: adminApp, deleteApp: deleteAdminApp } = backend('firebase-admin/app');
const { getFirestore, FieldValue } = backend('firebase-admin/firestore');
const { getAuth: adminAuth } = backend('firebase-admin/auth');
const { createPaymentHandlers } = backend('./payment-handler');
const projectId = 'demo-mahallem';
let admin, db, customer, provider, outsider; const apps = [];
async function account(name) {
  const app = initializeApp({ projectId, apiKey: 'fake-emulator-key', appId: `pay-${name}` }, `pay-${name}-${randomUUID()}`);
  apps.push(app); const auth = getAuth(app);
  connectAuthEmulator(auth, `http://${process.env.FIREBASE_AUTH_EMULATOR_HOST}`, { disableWarnings: true });
  const user = (await createUserWithEmailAndPassword(auth, `${name}-${randomUUID()}@example.com`, 'strong-Test-123!')).user;
  const client = clientFirestore(app, 'mahallem'); const [host, port] = process.env.FIRESTORE_EMULATOR_HOST.split(':');
  connectFirestoreEmulator(client, host, Number(port));
  return { uid: user.uid, token: await user.getIdToken(), client };
}
async function seed() {
  const id = `payment-${randomUUID()}`, quoteId = `quote-${randomUUID()}`;
  await db.doc(`requests/${id}`).set({ ownerUid: customer.uid, acceptedQuoteId: quoteId, acceptedProviderUid: provider.uid,
    data: { status: 'ACCEPTED', escrowStatus: 'NONE', escrowAmount: '' } });
  await db.doc(`quotes/${quoteId}`).set({ status: 'ACCEPTED', requestId: id, customerUid: customer.uid, providerUid: provider.uid,
    data: { price: '1.250,50 TL', escrowFunded: false } });
  await db.doc(`_paymentBuyers/${customer.uid}`).set({ verified: true, environment: 'sandbox', buyer: {}, billingAddress: {} });
  await db.doc(`_paymentMerchants/${provider.uid}`).set({ verified: true, environment: 'sandbox', subMerchantKey: 'fixture', payoutBasisPoints: 9500 });
  return id;
}
const req = (actor, id, extra = {}) => ({ auth: actor ? { uid: actor.uid } : null, data: { requestId: id, ...extra }, rawRequest: { ip: '127.0.0.1' } });
function handlers(service) {
  return createPaymentHandlers({ db, auth: adminAuth(admin), reserve: async () => {},
    configuration: () => ({ environment: 'sandbox' }), providerFactory: () => service });
}
before(async () => {
  assert.ok(process.env.FIRESTORE_EMULATOR_HOST && process.env.FIREBASE_AUTH_EMULATOR_HOST, 'Emulators required; never run against live Firebase');
  admin = adminApp({ projectId }, `payment-admin-${randomUUID()}`); db = getFirestore(admin, 'mahallem');
  [customer, provider, outsider] = await Promise.all(['payment-customer', 'payment-provider', 'payment-outsider'].map(account));
});
after(async () => { await Promise.all(apps.map(deleteApp)); if (admin) await deleteAdminApp(admin); });

test('concurrent starts initialize once; server-owned price and private payment records', async () => {
  const id = await seed(); let starts = 0;
  const service = { initialize: async payload => {
    starts++; assert.equal(payload.authority.amountMinor, 125050); assert.equal(payload.subMerchantAmountMinor, 118797);
    await new Promise(resolve => setTimeout(resolve, 20));
    return { token: randomUUID(), paymentPageUrl: 'https://sandbox-api.iyzipay.com/checkoutform/pay', expiresInSeconds: 1800 };
  } };
  const handler = handlers(service);
  const results = await Promise.all([handler.start(req(customer, id)), handler.start(req(customer, id))]);
  assert.equal(starts, 1); assert.ok(results.every(r => ['READY','INITIALIZING'].includes(r.status)));
  assert.equal((await handler.start(req(customer, id))).status, 'READY'); assert.equal(starts, 1);
  const record = (await db.doc(`_paymentAttempts/${id}`).get()).data();
  assert.equal(record.amountMinor, 125050); assert.equal(record.customerUid, customer.uid);
  assert.equal(results.find(r => r.status === 'READY').token, undefined);
  for (const actor of [customer, provider, outsider]) {
    await assert.rejects(getDoc(doc(actor.client, `_paymentAttempts/${id}`)), /permission-denied/);
    await assert.rejects(setDoc(doc(actor.client, `_paymentAttempts/${id}`), { status:'PAID' }), /permission-denied/);
    await assert.rejects(getDoc(doc(actor.client, `_paymentBuyers/${customer.uid}`)), /permission-denied/);
  }
});
test('unknown initialize outcome is durable and never creates a second charge', async () => {
  const id = await seed(); let starts = 0;
  const handler = handlers({ initialize: async () => { starts++; throw new Error('fixture network timeout'); } });
  await assert.rejects(handler.start(req(customer, id)));
  assert.equal((await handler.start(req(customer, id))).status, 'UNKNOWN'); assert.equal(starts, 1);
  assert.equal((await handler.status(req(customer, id))).status, 'UNKNOWN');
});
test('signed provider abstraction reconciles stored authority after cancellation and never downgrades paid', async () => {
  const id = await seed(); let retrieves = 0;
  const handler = handlers({ initialize: async () => ({ token: randomUUID(), paymentPageUrl: 'https://sandbox-api.iyzipay.com/checkoutform/pay', expiresInSeconds: 1800 }),
    retrieve: async payload => { retrieves++; assert.equal(payload.authority.amountMinor, 125050); return { status:'PAID', providerPaymentId:'fixture-payment-123', itemTransactionId:'fixture-item' }; } });
  await handler.start(req(customer, id));
  await db.doc(`requests/${id}`).update({ 'data.status':'CANCELLED' });
  assert.equal((await handler.status(req(customer, id))).status, 'PAID');
  assert.equal((await handler.status(req(customer, id))).status, 'PAID'); assert.equal(retrieves, 1);
  assert.equal((await db.doc(`requests/${id}`).get()).data().data.escrowStatus, 'NONE');
});
test('outsider, client amount and current account revocation denied before provider calls', async () => {
  const id = await seed(); let starts = 0; const handler = handlers({ initialize: async () => { starts++; throw Error(); } });
  await assert.rejects(handler.start(req(null, id)), e => e.code === 'unauthenticated');
  await assert.rejects(handler.start(req(outsider, id)), e => e.code === 'permission-denied');
  await assert.rejects(handler.start(req(customer, id, { amountMinor:1 })), e => e.code === 'invalid-argument');
  await assert.rejects(handler.status(req(outsider, id)), e => e.code === 'permission-denied');
  await adminAuth(admin).updateUser(customer.uid, { disabled:true });
  try { await assert.rejects(handler.start(req(customer, id)), e => e.code === 'permission-denied'); }
  finally { await adminAuth(admin).updateUser(customer.uid, { disabled:false }); }
  assert.equal(starts, 0);
});
test('real callable stays disabled without merchant config; client success/config cannot activate it', async () => {
  async function call(data, actor = customer) {
    const res = await fetch(`http://127.0.0.1:5001/${projectId}/europe-west3/getPaymentAvailability`, {
      method:'POST',headers:{'content-type':'application/json',...(actor?{authorization:`Bearer ${actor.token}`}:{})},body:JSON.stringify({data}) });
    return { code:res.status, body:await res.json() };
  }
  const result = await call({}); assert.equal(result.code, 200);
  assert.deepEqual(result.body.result || result.body.data, { available:false, environment:'disabled' });
  assert.equal((await call({ available:true, environment:'production' })).code, 400);
  assert.equal((await call({}, null)).code, 401);
  const handler = createPaymentHandlers({ db, auth:adminAuth(admin), reserve:async()=>{},configuration:()=>({environment:'production',merchantEnabled:true,apiKey:'fixture',secretKey:'fixture',callbackUrl:'https://example.test/callback'}) });
  assert.deepEqual(await handler.availability({auth:{uid:customer.uid},data:{}}), {available:false,environment:'disabled'});
});

test('forged browser callback cannot create or credit a payment', async () => {
  const res = await fetch(`http://127.0.0.1:5001/${projectId}/europe-west3/iyzicoCheckoutCallback`, {
    method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify({token:randomUUID(),status:'PAID',amountMinor:1}) });
  assert.equal(res.status, 400);
  assert.equal(res.headers.get('cache-control'), 'no-store');
});
