import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import {
  initializeTestEnvironment,
  assertSucceeds,
  assertFails,
} from "@firebase/rules-unit-testing";
import { doc, setDoc, getDoc, getDocs, collection, query, limit, deleteDoc, updateDoc, whereEqualTo } from "firebase/firestore";

const rules = readFileSync(new URL("../../firestore.rules", import.meta.url), "utf8");

const projectId = process.env.FIREBASE_PROJECT_ID || "mahallem-rules-test";

const env = await initializeTestEnvironment({
  projectId,
  firestore: { rules },
});

async function run() {
  const alice = env.authenticatedContext("alice", { email_verified: true });
  const bob = env.authenticatedContext("bob", { email_verified: true });
  const anonymousDb = env.unauthenticatedContext().firestore();

  const aliceDb = alice.firestore();
  const bobDb = bob.firestore();

  await assertSucceeds(
    setDoc(doc(aliceDb, "users/alice"), {
      uid: "alice",
      displayName: "Alice",
      role: "user",
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );

  await assertFails(
    setDoc(doc(aliceDb, "users/alice"), {
      uid: "alice",
      displayName: "Alice",
      role: "admin",
      updatedAt: new Date(),
    })
  );

  await assertFails(
    setDoc(doc(aliceDb, "users/alice-extra"), {
      uid: "alice",
      displayName: "Alice",
      role: "user",
      isSuperuser: true,
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );

  await assertFails(
    setDoc(doc(aliceDb, "users/alice-fake-phone"), {
      uid: "alice",
      displayName: "Alice",
      role: "user",
      phoneNumber: "+905551112233",
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );

  await assertFails(
    setDoc(doc(aliceDb, "jobRequests/request-1"), {
      ownerId: "alice",
      title: "Temizlik",
      sector: "CLEANING",
      categoryId: "cleaning",
      district: "Karşıyaka",
      urgencyMode: "NORMAL",
      status: "PENDING",
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );

  await assertFails(
    setDoc(doc(bobDb, "jobRequests/request-2"), {
      ownerId: "alice",
      title: "Yetkisiz talep",
      sector: "CLEANING",
      categoryId: "cleaning",
      district: "Karşıyaka",
      urgencyMode: "NORMAL",
      status: "PENDING",
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );

  await assertFails(
    setDoc(doc(aliceDb, "jobRequests/request-3"), {
      ownerId: "alice",
      title: "Başlangıç durumu yanlış",
      sector: "CLEANING",
      categoryId: "cleaning",
      district: "Karşıyaka",
      urgencyMode: "NORMAL",
      status: "ACCEPTED",
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );

  await assertFails(
    setDoc(doc(aliceDb, "providers/provider-1"), {
      ownerId: "alice",
      displayName: "Alice Hizmet",
      title: "Temizlik",
      bio: "Test",
      sector: "CLEANING",
      categoryId: "cleaning",
      district: "Karşıyaka",
      city: "İzmir",
      experienceYears: 3,
      serviceArea: { latitude: 38.46, longitude: 27.11 },
      isOpenForOffers: true,
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );

  await assertFails(
    setDoc(doc(bobDb, "providers/provider-2"), {
      ownerId: "alice",
      displayName: "Sahte sağlayıcı",
      title: "Test",
      sector: "CLEANING",
      categoryId: "cleaning",
      district: "Karşıyaka",
      city: "İzmir",
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );

  await assertFails(
    setDoc(doc(aliceDb, "conversations/conversation-client-write"), {
      participantIds: ["alice", "bob"],
      relatedItemId: "request-1",
      relatedItemTitle: "Temizlik",
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );

  await assertFails(
    setDoc(doc(aliceDb, "quotes/client-create"), {
      providerId: "provider-1",
      providerOwnerId: "alice",
      customerId: "alice",
      requestId: "request-1",
      price: "100 TL",
      amountMinor: 10000,
      durationOrArrival: "1 gün",
      notes: "istemci doğrudan yazmamalı",
      status: "PENDING",
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );

  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), "conversations/conversation-1"), {
      participantIds: ["alice", "bob"],
      relatedItemId: "request-1",
      relatedItemTitle: "Temizlik",
      createdAt: new Date(),
      updatedAt: new Date(),
    });
  });

  await assertFails(
    setDoc(doc(bobDb, "messages/message-client-write"), {
      conversationId: "conversation-1",
      senderId: "bob",
      text: "Merhaba",
      messageType: "TEXT",
      createdAt: new Date(),
    })
  );

  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), "messages/message-1"), {
      conversationId: "conversation-1",
      senderId: "bob",
      text: "Merhaba",
      messageType: "TEXT",
      createdAt: new Date(),
    });
  });

  assert.equal((await getDoc(doc(bobDb, "messages/message-1"))).exists(), true);

  await assertFails(
    setDoc(doc(bobDb, "messages/message-3"), {
      conversationId: "conversation-1",
      senderId: "bob",
      text: "Alan kaçırma",
      messageType: "TEXT",
      createdAt: new Date(),
      participantIds: ["alice", "bob"],
    })
  );

  await assertFails(
    setDoc(doc(aliceDb, "jobRequestPrivate/private-1"), {
      ownerId: "alice",
      address: "Karşıyaka",
      customerName: "Alice",
      customerPhone: "+905551112233",
      phoneVerified: false,
      updatedAt: new Date(),
    })
  );

  await assertFails(
    setDoc(doc(aliceDb, "jobRequestPrivate/private-2"), {
      ownerId: "alice",
      address: "Karşıyaka",
      customerName: "Alice",
      customerPhone: "+905551112233",
      phoneVerified: true,
      updatedAt: new Date(),
    })
  );

    await assertFails(
    setDoc(doc(aliceDb, "jobRequestPrivate/private-1"), {
      ownerId: "alice",
      address: "İzinsiz alan",
      secretRole: "admin",
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );

  await assertFails(
    updateDoc(doc(aliceDb, "jobRequestPrivate/private-1"), {
      secretRole: "admin",
    })
  );

  await assertFails(
    updateDoc(doc(aliceDb, "conversations/conversation-1"), {
      lastMessagePreview: "client spoof",
    })
  );

  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), "users/alice-deleting"), {
      uid: "alice-deleting",
      role: "user",
      deletionStatus: "REQUESTED",
    });
  });

  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), "users/alice-purging"), {
      uid: "alice-purging",
      role: "user",
      deletionStatus: "PURGING",
    });
  });
  const purgingUser = env.authenticatedContext("alice-purging", { email_verified: true });
  const purgingDb = purgingUser.firestore();
  await assertFails(
    setDoc(doc(purgingDb, "jobRequests/purging-request"), {
      ownerId: "alice-purging",
      title: "Bloklanmalı",
      sector: "CLEANING",
      categoryId: "cleaning",
      district: "Karşıyaka",
      urgencyMode: "NORMAL",
      status: "PENDING",
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );

  const deletingUser = env.authenticatedContext("alice-deleting", { email_verified: true });
  const deletingDb = deletingUser.firestore();

  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, "providers/provider-visible"), {
      ownerId: "bob",
      displayName: "Bob Provider",
    });
    await setDoc(doc(db, "publicProviders/provider-visible"), {
      displayName: "Bob Provider",
      title: "Temizlik",
      district: "Karşıyaka",
      city: "İzmir",
    });
    await setDoc(doc(db, "publicJobRequests/request-visible"), {
      title: "Visible request",
      district: "Karşıyaka",
      status: "PENDING",
    });
    await setDoc(doc(db, "jobRequests/request-visible"), {
      ownerId: "bob",
      status: "PENDING",
      title: "Visible request",
    });
    await setDoc(doc(db, "conversations/conversation-visible"), {
      participantIds: ["alice-deleting", "bob"],
    });
    await setDoc(doc(db, "messages/message-visible"), {
      conversationId: "conversation-visible",
      senderId: "bob",
      text: "hello",
    });
    await setDoc(doc(db, "quotes/quote-visible"), {
      customerId: "alice-deleting",
      providerOwnerId: "bob",
      requestId: "request-visible",
      status: "PENDING",
      amountMinor: 10000,
    });
    await setDoc(doc(db, "payments/payment-visible"), {
      customerId: "alice-deleting",
      providerId: "bob",
      status: "PAID",
      amountMinor: 10000,
    });
  });

  await assertFails(getDoc(doc(deletingDb, "providers/provider-visible")));
  await assertFails(getDoc(doc(deletingDb, "jobRequests/request-visible")));
  await assertFails(getDoc(doc(deletingDb, "conversations/conversation-visible")));
  await assertFails(getDoc(doc(deletingDb, "messages/message-visible")));
  await assertFails(getDoc(doc(deletingDb, "quotes/quote-visible")));
  await assertFails(getDoc(doc(deletingDb, "payments/payment-visible")));

  await assertFails(getDocs(query(collection(aliceDb, "providers"), limit(50))));
  await assertSucceeds(getDocs(query(collection(aliceDb, "jobRequests"), whereEqualTo("ownerId", "alice"), limit(50))));
  await assertFails(getDocs(query(collection(aliceDb, "jobRequests"), limit(50))));
  await assertSucceeds(getDocs(query(collection(aliceDb, "publicProviders"), limit(50))));
  await assertSucceeds(getDocs(query(collection(aliceDb, "publicJobRequests"), limit(50))));
  await assertFails(getDocs(query(collection(anonymousDb, "publicProviders"), limit(50))));
  await assertFails(getDocs(query(collection(anonymousDb, "publicJobRequests"), limit(50))));

  await assertFails(
    setDoc(doc(aliceDb, "publicProviders/client-write"), {
      displayName: "İstemci yazmamalı",
      title: "Temizlik"
    })
  );
  await assertFails(
    setDoc(doc(aliceDb, "publicJobRequests/client-write"), {
      title: "İstemci yazmamalı",
      status: "PENDING"
    })
  );

  await assertFails(getDoc(doc(aliceDb, "users/alice/devices/device-1")));
  await assertFails(
    setDoc(doc(aliceDb, "contentReports/client-write"), {
      reporterUid: "alice",
      targetType: "PROVIDER",
      targetId: "provider-visible",
      reason: "İstemci doğrudan rapor yazmamalı",
      status: "OPEN",
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );

  await assertFails(
    setDoc(doc(aliceDb, "quotes/client-created-quote"), {
      providerId: "provider-1",
      providerOwnerId: "alice",
      customerId: "bob",
      requestId: "request-1",
      price: "100",
      amountMinor: 10000,
      status: "PENDING",
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );

  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), "users/alice-purging"), {
      uid: "alice-purging",
      role: "user",
      deletionStatus: "PURGING",
    });
  });
  const purgingDb = env.authenticatedContext("alice-purging", { email_verified: true }).firestore();
  await assertFails(getDoc(doc(purgingDb, "providers/provider-visible")));
  await assertFails(getDocs(query(collection(purgingDb, "publicProviders"), limit(50))));
  await assertFails(getDocs(query(collection(purgingDb, "publicJobRequests"), limit(50))));
  await assertFails(
    setDoc(doc(purgingDb, "jobRequests/purging-request"), {
      ownerId: "alice-purging",
      title: "Bloklanmalı",
      sector: "CLEANING",
      categoryId: "cleaning",
      district: "Karşıyaka",
      urgencyMode: "NORMAL",
      status: "PENDING",
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );

  await assertFails(
    setDoc(doc(deletingDb, "jobRequests/deleting-request"), {
      ownerId: "alice-deleting",
      title: "Bloklanmalı",
      sector: "CLEANING",
      categoryId: "cleaning",
      district: "Karşıyaka",
      urgencyMode: "NORMAL",
      status: "PENDING",
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );

  await assertFails(
    updateDoc(doc(deletingDb, "users/alice-deleting"), {
      displayName: "Silinme sürecinde değişiklik",
      updatedAt: new Date(),
    })
  );

  await assertFails(
    setDoc(doc(deletingDb, "users/alice-deleting/devices/device-1"), {
      platform: "android",
      updatedAt: new Date(),
    })
  );

  await assertFails(deleteDoc(doc(aliceDb, "providers/provider-1")));

  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), "jobRequests/accepted-request"), {
      ownerId: "alice",
      status: "ACCEPTED",
    });
  });

  await assertFails(deleteDoc(doc(aliceDb, "jobRequests/accepted-request")));

  assert.equal((await getDoc(doc(aliceDb, "users/alice"))).exists(), true);
}

try {
  await run();
  console.log("Firestore rules tests passed.");
} finally {
  await env.cleanup();
}
