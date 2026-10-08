import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { initializeTestEnvironment, assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { doc, getDoc, setDoc, updateDoc } from 'firebase/firestore';

// Independent P0 matrix: allow legitimate reads AND deny cross-account reads.
const env = await initializeTestEnvironment({
  projectId: process.env.FIREBASE_PROJECT_ID || 'mahallem-rules-test',
  firestore: { rules: readFileSync(new URL('../../firestore.rules', import.meta.url), 'utf8') },
});
const owner = env.authenticatedContext('qa-owner', { email_verified: true }).firestore();
const provider = env.authenticatedContext('qa-provider', { email_verified: true }).firestore();
const outsider = env.authenticatedContext('qa-outsider', { email_verified: true }).firestore();
const anonymous = env.unauthenticatedContext().firestore();
let assertions = 0;
const allow = async (promise) => {
  const snapshot = await assertSucceeds(promise);
  assert.equal(snapshot.exists(), true, 'Positive rule assertion must read a real document');
  assertions++;
};
const deny = async (promise) => { await assertFails(promise); assertions++; };

try {
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    for (const uid of ['qa-owner', 'qa-provider', 'qa-outsider']) {
      await setDoc(doc(db, 'users', uid), { uid, role: 'user', deletionStatus: 'ACTIVE' });
    }
    await setDoc(doc(db, 'jobRequests/qa-request'), { ownerId: 'qa-owner', status: 'PENDING' });
    await setDoc(doc(db, 'jobRequestPrivate/qa-request'), {
      ownerId: 'qa-owner', address: 'Private address', customerPhone: 'fixture-only',
    });
    await setDoc(doc(db, 'providers/qa-provider'), { ownerId: 'qa-provider' });
    await setDoc(doc(db, 'quotes/qa-quote'), {
      customerId: 'qa-owner', providerOwnerId: 'qa-provider', amountMinor: 10000,
    });
    await setDoc(doc(db, 'payments/qa-payment'), {
      customerId: 'qa-owner', providerId: 'qa-provider', amountMinor: 10000,
    });
    await setDoc(doc(db, 'conversations/qa-chat'), { participantIds: ['qa-owner', 'qa-provider'] });
    await setDoc(doc(db, 'messages/qa-message'), {
      conversationId: 'qa-chat', senderId: 'qa-owner', text: 'fixture-only',
    });
    await setDoc(doc(db, 'users/qa-owner/conversationState/qa-chat'), {
      conversationId: 'qa-chat', unreadCount: 1,
    });
  });

  for (const path of [
    'users/qa-owner', 'jobRequests/qa-request', 'jobRequestPrivate/qa-request',
    'quotes/qa-quote', 'payments/qa-payment', 'conversations/qa-chat',
    'messages/qa-message', 'users/qa-owner/conversationState/qa-chat',
  ]) {
    await allow(getDoc(doc(owner, path)));
    await deny(getDoc(doc(anonymous, path)));
  }
  for (const path of ['providers/qa-provider', 'quotes/qa-quote',
    'payments/qa-payment', 'conversations/qa-chat', 'messages/qa-message']) {
    await allow(getDoc(doc(provider, path)));
  }
  for (const path of ['users/qa-owner', 'jobRequests/qa-request',
    'jobRequestPrivate/qa-request', 'users/qa-owner/conversationState/qa-chat']) {
    await deny(getDoc(doc(provider, path)));
  }
  for (const path of [
    'users/qa-owner', 'jobRequests/qa-request', 'jobRequestPrivate/qa-request',
    'providers/qa-provider', 'quotes/qa-quote', 'payments/qa-payment',
    'conversations/qa-chat', 'messages/qa-message',
    'users/qa-owner/conversationState/qa-chat',
  ]) {
    await deny(getDoc(doc(outsider, path)));
  }
  await deny(getDoc(doc(owner, 'providers/qa-provider')));

  // Critical regression: removing a participant must revoke access to OLD
  // messages, not only block new messages.
  await env.withSecurityRulesDisabled(async (ctx) => {
    await updateDoc(doc(ctx.firestore(), 'conversations/qa-chat'), {
      participantIds: ['qa-owner'],
    });
  });
  await deny(getDoc(doc(provider, 'conversations/qa-chat')));
  await deny(getDoc(doc(provider, 'messages/qa-message')));
  await allow(getDoc(doc(owner, 'messages/qa-message')));

  // Deletion REQUESTED must revoke the owner's existing private collection
  // access. Own user profile is intentionally not assumed revoked.
  await env.withSecurityRulesDisabled(async (ctx) => {
    await updateDoc(doc(ctx.firestore(), 'users/qa-owner'), {
      deletionStatus: 'REQUESTED',
    });
  });
  for (const path of [
    'jobRequests/qa-request', 'jobRequestPrivate/qa-request',
    'quotes/qa-quote', 'payments/qa-payment', 'conversations/qa-chat',
    'messages/qa-message', 'users/qa-owner/conversationState/qa-chat',
  ]) {
    await deny(getDoc(doc(owner, path)));
  }
  console.log('P0 cross-account isolation assertions PASS: ' + assertions);
} finally {
  await env.cleanup();
}
