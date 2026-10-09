import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { initializeApp, deleteApp } from 'firebase/app';
import { getAuth, connectAuthEmulator, createUserWithEmailAndPassword } from 'firebase/auth';
import { getStorage, connectStorageEmulator, ref, getBytes, uploadBytes } from 'firebase/storage';

const backendRequire = createRequire(new URL('../../functions/package.json', import.meta.url));
const { initializeApp: adminApp, deleteApp: deleteAdminApp } = backendRequire('firebase-admin/app');
const { getFirestore, FieldValue } = backendRequire('firebase-admin/firestore');
const { getStorage: adminStorage } = backendRequire('firebase-admin/storage');
const { getAuth: adminAuth } = backendRequire('firebase-admin/auth');
const sharp = backendRequire('sharp');
let admin, db, photo, alice, bob, eve;
const apps = [];
const projectId = 'demo-mahallem';
const bucket = `${projectId}.appspot.com`;
const conversationId = `media-test-${Date.now()}`;
async function account(name) {
  const app = initializeApp({ projectId, apiKey: 'fake-emulator-key', appId: `1:123:android:${name}`, storageBucket: bucket }, `media-${name}-${Date.now()}`);
  apps.push(app);
  const auth = getAuth(app);
  connectAuthEmulator(auth, `http://${process.env.FIREBASE_AUTH_EMULATOR_HOST || '127.0.0.1:9099'}`, { disableWarnings: true });
  const user = (await createUserWithEmailAndPassword(auth, `${name}-${Date.now()}@example.com`, 'strong-Test-123!')).user;
  return { app, user, uid: user.uid, token: await user.getIdToken() };
}
async function call(name, user, data) {
  const response = await fetch(`http://127.0.0.1:5001/${projectId}/europe-west3/${name}`, {
    method: 'POST', headers: { 'content-type': 'application/json', ...(user ? { authorization: `Bearer ${user.token}` } : {}) },
    body: JSON.stringify({ data })
  });
  const body = await response.json();
  return { status: response.status, body, result: body.result || body.data };
}
before(async () => {
  assert.ok(process.env.FIRESTORE_EMULATOR_HOST && process.env.FIREBASE_AUTH_EMULATOR_HOST && process.env.FIREBASE_STORAGE_EMULATOR_HOST,
    'Only run with npm run test:media; never point tests at live Firebase');
  admin = adminApp({ projectId, storageBucket: bucket }, `media-admin-${Date.now()}`);
  db = getFirestore(admin, 'mahallem');
  [alice, bob, eve] = await Promise.all(['alice','bob','eve'].map(account));
  await db.doc(`conversations/${conversationId}`).set({ participantUids: [alice.uid, bob.uid], names: { [alice.uid]: 'Alice', [bob.uid]: 'Bob' },
    lastMessage: '', relatedItemTitle: 'Private photo', createdAt: FieldValue.serverTimestamp(), updatedAt: FieldValue.serverTimestamp() });
});
after(async () => {
  await Promise.all(apps.map(deleteApp));
  if (admin) await deleteAdminApp(admin);
});
test('trusted photo lifecycle: real Auth emulator, server sanitization, UID isolation, private Storage and blocks', async () => {
  const input = await sharp({ create: { width: 4, height: 4, channels: 3, background: { r: 1, g: 2, b: 3 } } }).png().toBuffer();
  const payload = { conversationId, base64: input.toString('base64') };
  assert.equal((await call('uploadConversationPhoto', null, payload)).status, 401);
  assert.equal((await call('uploadConversationPhoto', eve, payload)).status, 403);
  assert.equal((await call('uploadConversationPhoto', alice, { ...payload, base64: Buffer.from('<script>evil</script>').toString('base64') })).status, 400);
  const uploaded = await call('uploadConversationPhoto', alice, payload);
  assert.equal(uploaded.status, 200, JSON.stringify(uploaded.body));
  photo = uploaded.result;
  assert.ok(photo.mediaId && photo.storagePath.startsWith(`conversationMedia/${conversationId}/${alice.uid}/`));
  const doc = (await db.doc(`conversations/${conversationId}/media/${photo.mediaId}`).get()).data();
  assert.equal(doc.uploaderUid, alice.uid);
  assert.equal(doc.contentType, 'image/jpeg');
  const [metadata] = await adminStorage(admin).bucket().file(photo.storagePath).getMetadata();
  assert.equal(metadata.metadata?.firebaseStorageDownloadTokens, undefined);
  const downloaded = await call('readConversationPhoto', bob, { conversationId, mediaId: photo.mediaId });
  assert.equal(downloaded.status, 200, JSON.stringify(downloaded.body));
  const imageInfo = await sharp(Buffer.from(downloaded.result.base64, 'base64')).metadata();
  assert.equal(imageInfo.format, 'jpeg');
  assert.equal(imageInfo.exif, undefined);
  assert.equal((await call('readConversationPhoto', eve, { conversationId, mediaId: photo.mediaId })).status, 403);
  assert.equal((await call('readConversationPhoto', null, { conversationId, mediaId: photo.mediaId })).status, 401);
  // Revoke the original participant while retaining the same validated private image.
  // Old upload-time ACLs must not authorize Bob after the conversation is updated.
  await db.doc(`conversations/${conversationId}`).update({ participantUids: [alice.uid, eve.uid] });
  assert.equal((await call('readConversationPhoto', bob, { conversationId, mediaId: photo.mediaId })).status, 403);
  assert.equal((await call('readConversationPhoto', alice, { conversationId, mediaId: photo.mediaId })).status, 200);
  await db.doc(`conversations/${conversationId}`).update({ participantUids: [alice.uid, bob.uid] });

  // A cached signed-in token cannot bypass server-side account disablement.
  await adminAuth(admin).updateUser(bob.uid, { disabled: true });
  assert.equal((await call('readConversationPhoto', bob, { conversationId, mediaId: photo.mediaId })).status, 403);
  await adminAuth(admin).updateUser(bob.uid, { disabled: false });

  // Deletion-state revocation also fails closed without Storage rule lookups.
  await db.doc(`users/${bob.uid}`).set({ deletionStatus: 'REQUESTED' }, { merge: true });
  assert.equal((await call('readConversationPhoto', bob, { conversationId, mediaId: photo.mediaId })).status, 403);
  await db.doc(`users/${bob.uid}`).update({ deletionStatus: 'ACTIVE' });
  assert.equal((await call('readConversationPhoto', bob, { conversationId, mediaId: '../path' })).status, 400);
  for (const user of [alice, bob, eve]) {
    const storage = getStorage(user.app);
    connectStorageEmulator(storage, '127.0.0.1', 9199);
    await assert.rejects(getBytes(ref(storage, photo.storagePath)), /unauthorized/);
    await assert.rejects(uploadBytes(ref(storage, 'conversationMedia/evil.jpg'), input), /unauthorized/);
  }
  const publicResponse = await fetch(`http://127.0.0.1:9199/v0/b/${bucket}/o/${encodeURIComponent(photo.storagePath)}?alt=media`);
  assert.equal(publicResponse.status, 403);
  await db.doc(`users/${bob.uid}/blocks/${alice.uid}`).set({ blockedUid: alice.uid, createdAt: FieldValue.serverTimestamp() });
  assert.equal((await call('readConversationPhoto', alice, { conversationId, mediaId: photo.mediaId })).status, 403);
  assert.equal((await call('uploadConversationPhoto', alice, payload)).status, 403);
  await db.doc(`users/${bob.uid}/blocks/${alice.uid}`).delete();
  await db.doc(`users/${alice.uid}/blocks/${bob.uid}`).set({ blockedUid: bob.uid, createdAt: FieldValue.serverTimestamp() });
  assert.equal((await call('readConversationPhoto', bob, { conversationId, mediaId: photo.mediaId })).status, 403);
});
test('moderation queue requires server role; review atomically hides listing and audits, revoked role fails immediately', async () => {
  const listingId = `moderation-listing-${Date.now()}`;
  const reportId = `moderation-report-${Date.now()}`;
  await db.doc(`providers/${listingId}`).set({ ownerUid: bob.uid, data: { isReported: false } });
  await db.doc(`reports/${reportId}`).set({ reporterUid: eve.uid, targetType: 'listing', targetId: `provider:${listingId}`,
    targetUid: bob.uid, reason: 'fraud', details: 'Test report', status: 'pending', createdAt: FieldValue.serverTimestamp() });
  assert.equal((await call('getModerationQueue', alice, { moderator: true })).status, 403);
  assert.equal((await call('reviewReport', alice, { reportId, action: 'hide_listing', note: 'Verified by moderator' })).status, 403);
  await adminAuth(admin).setCustomUserClaims(alice.uid, { moderator: true });
  alice.token = await alice.user.getIdToken(true);
  const queue = await call('getModerationQueue', alice, {});
  assert.equal(queue.status, 200, JSON.stringify(queue.body));
  assert.ok(queue.result.reports.some(report => report.id === reportId));
  const review = await call('reviewReport', alice, { reportId, action: 'hide_listing', note: 'Verified by moderator' });
  assert.equal(review.status, 200, JSON.stringify(review.body));
  const listing = (await db.doc(`providers/${listingId}`).get()).data();
  assert.equal(listing.data.isReported, true);
  assert.equal(listing.moderationStatus, 'hidden');
  assert.equal(listing.visibility, 'hidden');
  const report = (await db.doc(`reports/${reportId}`).get()).data();
  assert.equal(report.status, 'reviewed');
  assert.equal(report.reviewedByUid, alice.uid);
  const audits = await db.collection('moderationAudit').where('reportId', '==', reportId).get();
  assert.equal(audits.size, 1);
  assert.equal((await call('reviewReport', alice, { reportId, action: 'dismiss', note: 'Duplicate review forbidden' })).status, 400);
  await adminAuth(admin).setCustomUserClaims(alice.uid, {});
  // Deliberately keep her old token: fresh Auth custom claims must still deny access.
  assert.equal((await call('getModerationQueue', alice, {})).status, 403);
});

test('real SMS emulator linking preserves UID; trust uses current Auth and private matching contact, revocation fails closed', async () => {
  const { reload, signInWithEmailAndPassword, signOut, unlink } = await import('firebase/auth');
  const owner = await account('phone-owner');
  const auth = getAuth(owner.app);
  const originalUid = owner.uid;
  const phoneNumber = '+905551234567';
  const host = process.env.FIREBASE_AUTH_EMULATOR_HOST;
  const sent = await fetch(`http://${host}/identitytoolkit.googleapis.com/v1/accounts:sendVerificationCode?key=fake-emulator-key`, {
    method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ phoneNumber })
  });
  assert.equal(sent.status, 200);
  const { sessionInfo } = await sent.json();
  const codes = await (await fetch(`http://${host}/emulator/v1/projects/${projectId}/verificationCodes`)).json();
  const code = codes.verificationCodes.find(entry => entry.sessionInfo === sessionInfo)?.code;
  assert.match(code, /^\d{6}$/);
  const wrong = code === '000000' ? '111111' : '000000';
  // The Node SDK deliberately stubs browser PhoneAuthProvider; use the same
  // documented link REST operation with the existing ID token, never phone sign-in.
  const linkPhone = async smsCode => fetch(`http://${host}/identitytoolkit.googleapis.com/v1/accounts:signInWithPhoneNumber?key=fake-emulator-key`, {
    method: 'POST', headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ idToken: owner.token, sessionInfo, code: smsCode })
  });
  const rejected = await linkPhone(wrong);
  assert.equal(rejected.status, 400);
  assert.match((await rejected.json()).error.message, /INVALID_CODE|INVALID_VERIFICATION_CODE/);
  assert.equal(auth.currentUser.uid, originalUid);
  const linked = await linkPhone(code);
  assert.equal(linked.status, 200);
  assert.equal((await linked.json()).localId, originalUid);
  await reload(owner.user);
  assert.equal(auth.currentUser.uid, originalUid);
  assert.equal(auth.currentUser.phoneNumber, phoneNumber);
  owner.token = await auth.currentUser.getIdToken(true);
  const listingId = `phone-listing-${Date.now()}`;
  await db.doc(`providers/${listingId}`).set({ ownerUid: originalUid, visibility: 'published',
    data: { phoneVerified: true, verifiedSafeBadge: true } });
  const contact = db.doc(`providerContacts/${listingId}`);
  await contact.set({ ownerUid: originalUid, phone: '0555 123 45 67' });
  const payload = { kind: 'providers', ids: [listingId] };
  const trust = async user => {
    const result = await call('getListingTrust', user, payload);
    assert.equal(result.status, 200, JSON.stringify(result.body));
    return result.result.listings;
  };
  assert.equal((await call('getListingTrust', null, payload)).status, 401);
  assert.equal((await trust(bob))[listingId].phoneVerified, true);
  assert.deepEqual(Object.keys((await trust(bob))[listingId]), ['phoneVerified']);
  await contact.update({ phone: '05551234568' });
  assert.equal((await trust(bob))[listingId].phoneVerified, false);
  await contact.update({ phone: phoneNumber, ownerUid: bob.uid });
  assert.equal((await trust(bob))[listingId].phoneVerified, false);
  await contact.update({ ownerUid: originalUid });
  await db.doc(`providers/${listingId}`).update({ visibility: 'hidden' });
  assert.equal((await trust(bob))[listingId], undefined);
  assert.equal((await trust(owner))[listingId].phoneVerified, true);
  await db.doc(`providers/${listingId}`).update({ visibility: 'published' });
  await adminAuth(admin).updateUser(originalUid, { disabled: true });
  assert.equal((await trust(bob))[listingId].phoneVerified, false);
  assert.equal((await call('getListingTrust', owner, payload)).status, 403);
  await adminAuth(admin).updateUser(originalUid, { disabled: false });
  await db.doc(`users/${originalUid}`).set({ deletionStatus: 'REQUESTED' });
  assert.equal((await trust(bob))[listingId].phoneVerified, false);
  await db.doc(`users/${originalUid}`).delete();
  // Unlink while retaining old caller tokens: the server never trusts their phone claim.
  await unlink(auth.currentUser, 'phone');
  assert.equal((await trust(bob))[listingId].phoneVerified, false);
  assert.equal((await call('getListingTrust', bob, { kind: 'users', ids: [originalUid] })).status, 400);
  assert.equal((await call('getListingTrust', bob, { kind: 'providers', ids: ['../private'] })).status, 400);
  assert.equal((await call('getListingTrust', bob, { kind: 'providers', ids: Array(51).fill(listingId) })).status, 400);
  // Re-login using the original email after linking/unlinking must retain all private UID data.
  const email = owner.user.email;
  await signOut(auth);
  const login = await signInWithEmailAndPassword(auth, email, 'strong-Test-123!');
  assert.equal(login.user.uid, originalUid);
});
