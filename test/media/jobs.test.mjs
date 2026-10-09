import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { randomUUID } from 'node:crypto';
import { initializeApp, deleteApp } from 'firebase/app';
import { getAuth, connectAuthEmulator, createUserWithEmailAndPassword } from 'firebase/auth';
import { getFirestore as clientFirestore, connectFirestoreEmulator, doc, getDoc, getDocs, collection,
  query, where, updateDoc, setDoc, deleteDoc, writeBatch, serverTimestamp } from 'firebase/firestore';
const requireBackend = createRequire(new URL('../../functions/package.json', import.meta.url));
const { initializeApp: adminApp, deleteApp: deleteAdminApp } = requireBackend('firebase-admin/app');
const { getFirestore, FieldValue } = requireBackend('firebase-admin/firestore');
const { getAuth: adminAuth } = requireBackend('firebase-admin/auth');
const fixtures = JSON.parse(readFileSync(new URL('../firebase/fixtures.json', import.meta.url)));
const apps = []; let admin, db, customer, provider, outsider;
const projectId = 'demo-mahallem';
async function account(name) {
  const app = initializeApp({ projectId, apiKey: 'fake-emulator-key', appId: `job-${name}` }, `jobs-${name}-${randomUUID()}`);
  apps.push(app);
  const auth = getAuth(app);
  connectAuthEmulator(auth, `http://${process.env.FIREBASE_AUTH_EMULATOR_HOST}`, { disableWarnings: true });
  const user = (await createUserWithEmailAndPassword(auth, `${name}-${randomUUID()}@example.com`, 'strong-Test-123!')).user;
  const client = clientFirestore(app, 'mahallem');
  const [host, port] = process.env.FIRESTORE_EMULATOR_HOST.split(':');
  connectFirestoreEmulator(client, host, Number(port));
  return { uid: user.uid, token: await user.getIdToken(), client };
}
async function call(actor, command) {
  const response = await fetch(`http://127.0.0.1:5001/${projectId}/europe-west3/manageJob`, {
    method: 'POST', headers: { 'content-type': 'application/json', ...(actor ? { authorization: `Bearer ${actor.token}` } : {}) },
    body: JSON.stringify({ data: command })
  });
  const body = await response.json();
  return { code: response.status, body, result: body.result || body.data };
}
function command(requestId, action, version, overrides = {}) {
  return { requestId, actionId: randomUUID(), action, version,
    note: ['SUBMIT_COMPLETION', 'REQUEST_REVISION', 'REQUEST_CANCEL', 'CANCEL_OPEN', 'DECLINE_CANCEL'].includes(action) ? 'Gerçek test işinin ayrıntılı açıklaması' : '',
    reasonCode: ['REQUEST_CANCEL', 'CANCEL_OPEN'].includes(action) ? 'OTHER' : '', ...overrides };
}
async function seed(accepted = true) {
  const id = `job-${randomUUID()}`, quoteId = `quote-${randomUUID()}`;
  const stamp = { createdAt: FieldValue.serverTimestamp(), updatedAt: FieldValue.serverTimestamp() };
  await db.doc(`requests/${id}`).set({ ownerUid: customer.uid, visibility: 'published',
    acceptedQuoteId: accepted ? quoteId : '', acceptedProviderUid: accepted ? provider.uid : '', ...stamp,
    data: { ...fixtures.request, id, ownerUid: customer.uid, status: accepted ? 'ACCEPTED' : 'PENDING', escrowStatus: 'NONE', escrowAmount: '' } });
  await db.doc(`quotes/${quoteId}`).set({ providerUid: provider.uid, customerUid: customer.uid, requestId: id,
    status: accepted ? 'ACCEPTED' : 'PENDING', ...stamp, data: { ...fixtures.quote, id: quoteId, requestId: id,
      providerUid: provider.uid, customerUid: customer.uid, escrowFunded: false } });
  return { id, quoteId };
}
async function acceptedBatch(id, quoteId) {
  const batch = writeBatch(customer.client);
  batch.update(doc(customer.client, `requests/${id}`), { 'data.status': 'ACCEPTED', acceptedQuoteId: quoteId,
    acceptedProviderUid: provider.uid, updatedAt: serverTimestamp() });
  batch.update(doc(customer.client, `quotes/${quoteId}`), { status: 'ACCEPTED', updatedAt: serverTimestamp() });
  return batch.commit();
}
before(async () => {
  assert.ok(process.env.FIRESTORE_EMULATOR_HOST && process.env.FIREBASE_AUTH_EMULATOR_HOST, 'Emulators required; never run against live Firebase');
  admin = adminApp({ projectId }, `jobs-admin-${randomUUID()}`); db = getFirestore(admin, 'mahallem');
  [customer, provider, outsider] = await Promise.all(['job-customer', 'job-provider', 'job-outsider'].map(account));
});
after(async () => { await Promise.all(apps.map(deleteApp)); if (admin) await deleteAdminApp(admin); });

test('real callable completion/revision; private events, server-only writes, terminal closure and participant history', async () => {
  const { id } = await seed();
  assert.equal((await call(null, command(id, 'START', 0))).code, 401);
  assert.equal((await call(outsider, command(id, 'START', 0))).code, 403);
  assert.equal((await call(customer, command(id, 'SUBMIT_COMPLETION', 0))).code, 400);
  for (const [actor, action, version, expected] of [[provider,'START',0,'IN_PROGRESS'], [provider,'SUBMIT_COMPLETION',1,'AWAITING_CONFIRMATION'],
    [customer,'REQUEST_REVISION',2,'IN_PROGRESS'], [provider,'SUBMIT_COMPLETION',3,'AWAITING_CONFIRMATION'], [customer,'CONFIRM_COMPLETION',4,'COMPLETED']]) {
    const result = await call(actor, command(id, action, version));
    assert.equal(result.code, 200, JSON.stringify(result.body)); assert.equal(result.result.status, expected);
  }
  const state = (await db.doc(`jobs/${id}`).get()).data();
  assert.equal(state.version, 5); assert.equal(state.status, 'COMPLETED');
  const request = (await db.doc(`requests/${id}`).get()).data();
  assert.equal(request.visibility, 'closed'); assert.equal(request.data.escrowStatus, 'NONE');
  assert.equal((await db.collection(`jobs/${id}/events`).get()).size, 5);
  for (const actor of [customer, provider]) {
    assert.equal((await getDoc(doc(actor.client, `jobs/${id}`))).data().status, 'COMPLETED');
    assert.equal((await getDocs(collection(actor.client, `jobs/${id}/events`))).size, 5);
    assert.equal((await getDoc(doc(actor.client, `requests/${id}`))).data().visibility, 'closed');
    await assert.rejects(updateDoc(doc(actor.client, `jobs/${id}`), { status: 'ACCEPTED' }), /permission-denied/);
    await assert.rejects(updateDoc(doc(actor.client, `requests/${id}`), { 'data.status': 'ACCEPTED', updatedAt: serverTimestamp() }), /permission-denied/);
    await assert.rejects(setDoc(doc(actor.client, `jobs/${id}/events/forged`), { action: 'CONFIRM_COMPLETION' }), /permission-denied/);
  }
  for (const path of [`jobs/${id}`, `requests/${id}`]) await assert.rejects(getDoc(doc(outsider.client, path)), /permission-denied/);
  await assert.rejects(getDocs(collection(outsider.client, `jobs/${id}/events`)), /permission-denied/);
  assert.equal((await getDocs(query(collection(provider.client, 'requests'), where('acceptedProviderUid', '==', provider.uid)))).empty, false);
  assert.equal((await getDocs(query(collection(outsider.client, 'jobs'), where('participantUids', 'array-contains', outsider.uid)))).size, 0);
  assert.equal((await call(customer, command(id, 'REQUEST_CANCEL', 5))).code, 400);
});

test('both cancellation parties, reason privacy, decline/withdraw restore previous state and no self approval', async () => {
  const { id } = await seed();
  assert.equal((await call(provider, command(id, 'SUBMIT_COMPLETION', 0))).code, 200);
  assert.equal((await call(customer, command(id, 'REQUEST_CANCEL', 1))).code, 200);
  assert.equal((await call(customer, command(id, 'ACCEPT_CANCEL', 2))).code, 400);
  assert.equal((await call(provider, command(id, 'DECLINE_CANCEL', 2))).result.status, 'AWAITING_CONFIRMATION');
  assert.equal((await call(provider, command(id, 'REQUEST_CANCEL', 3))).code, 200);
  assert.equal((await call(provider, command(id, 'WITHDRAW_CANCEL', 4))).result.status, 'AWAITING_CONFIRMATION');
  assert.equal((await call(provider, command(id, 'REQUEST_CANCEL', 5))).code, 200);
  assert.equal((await call(customer, command(id, 'ACCEPT_CANCEL', 6))).result.status, 'CANCELLED');
  assert.equal((await db.doc(`requests/${id}`).get()).data().visibility, 'closed');
  assert.equal((await db.doc(`requests/${id}`).get()).data().reasonCode, undefined);
  await assert.rejects(deleteDoc(doc(customer.client, `jobs/${id}`)), /permission-denied/);
});

test('parallel duplicate retries produce one event; stale competing writes cannot both succeed', async () => {
  const { id } = await seed();
  const start = command(id, 'START', 0);
  const results = await Promise.all([call(provider, start), call(provider, start)]);
  assert.ok(results.every(r => r.code === 200), JSON.stringify(results));
  assert.equal((await db.collection(`jobs/${id}/events`).get()).size, 1);
  assert.equal((await call(provider, { ...start, note: 'Changed command payload' })).code, 409);
  const competing = await Promise.all([call(provider, command(id, 'SUBMIT_COMPLETION', 1)), call(customer, command(id, 'REQUEST_CANCEL', 1))]);
  assert.equal(competing.filter(r => r.code === 200).length, 1, JSON.stringify(competing));
  assert.equal(competing.filter(r => r.code === 409).length, 1, JSON.stringify(competing));
  assert.equal((await db.collection(`jobs/${id}/events`).get()).size, 2);
  assert.equal((await call(provider, start)).result.replayed, true);
});

test('open request cancellation races safely against real Rules-governed quote acceptance', async () => {
  const { id, quoteId } = await seed(false);
  const results = await Promise.allSettled([call(customer, command(id, 'CANCEL_OPEN', 0)), acceptedBatch(id, quoteId)]);
  const listing = (await db.doc(`requests/${id}`).get()).data();
  const quote = (await db.doc(`quotes/${quoteId}`).get()).data();
  if (listing.data.status === 'CANCELLED') {
    assert.equal(listing.acceptedQuoteId, ''); assert.equal(quote.status, 'PENDING');
    assert.equal(results[0].value.code, 200); assert.equal(results[1].status, 'rejected');
    await assert.rejects(acceptedBatch(id, quoteId), /permission-denied/);
  } else {
    assert.equal(listing.data.status, 'ACCEPTED'); assert.equal(quote.status, 'ACCEPTED');
    assert.equal(results[1].status, 'fulfilled'); assert.equal(results[0].value.code, 400);
  }
});

test('stale tokens, deleting accounts, forged authority, mismatched quotes and future payments fail closed', async () => {
  const { id, quoteId } = await seed();
  assert.equal((await call(provider, command(id, 'START', 0, { customerUid: provider.uid }))).code, 400);
  await adminAuth(admin).updateUser(provider.uid, { disabled: true });
  assert.equal((await call(provider, command(id, 'START', 0))).code, 403);
  await adminAuth(admin).updateUser(provider.uid, { disabled: false });
  await db.doc(`users/${provider.uid}`).set({ deletionStatus: 'REQUESTED' });
  assert.equal((await call(provider, command(id, 'START', 0))).code, 403);
  await db.doc(`users/${provider.uid}`).delete();
  await db.doc(`quotes/${quoteId}`).update({ providerUid: outsider.uid });
  assert.equal((await call(provider, command(id, 'START', 0))).code, 400);
  await db.doc(`quotes/${quoteId}`).update({ providerUid: provider.uid, 'data.escrowFunded': true });
  assert.equal((await call(provider, command(id, 'START', 0))).code, 400);
  await db.doc(`quotes/${quoteId}`).update({ 'data.escrowFunded': false });
  await db.doc(`requests/${id}`).update({ 'data.escrowStatus': 'LOCKED' });
  assert.equal((await call(provider, command(id, 'START', 0))).code, 400);
  assert.equal((await db.doc(`jobs/${id}`).get()).exists, false);
});
