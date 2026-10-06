import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import {
  initializeTestEnvironment,
  assertSucceeds,
  assertFails,
} from "@firebase/rules-unit-testing";
import { doc, setDoc, getDoc } from "firebase/firestore";

const rules = readFileSync(new URL("../../firestore.rules", import.meta.url), "utf8");

const projectId = process.env.FIREBASE_PROJECT_ID || "mahallem-rules-test";

const env = await initializeTestEnvironment({
  projectId,
  firestore: { rules },
});

async function run() {
  const alice = env.authenticatedContext("alice");
  const bob = env.authenticatedContext("bob");
  const admin = env.authenticatedContext("admin", { admin: true });

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

  await assertSucceeds(
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

  await assertSucceeds(
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
      serviceArea: "Karşıyaka",
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
    setDoc(doc(aliceDb, "conversations/conversation-1"), {
      participantIds: ["alice", "bob"],
      relatedItemId: "request-1",
      relatedItemTitle: "Temizlik",
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );

  await assertSucceeds(
    setDoc(doc(admin.firestore(), "conversations/conversation-1"), {
      participantIds: ["alice", "bob"],
      relatedItemId: "request-1",
      relatedItemTitle: "Temizlik",
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );

  await assertSucceeds(
    setDoc(doc(bobDb, "messages/message-1"), {
      conversationId: "conversation-1",
      senderId: "bob",
      text: "Merhaba",
      messageType: "TEXT",
      createdAt: new Date(),
    })
  );

  await assertFails(
    setDoc(doc(bobDb, "messages/message-2"), {
      conversationId: "conversation-unknown",
      senderId: "bob",
      text: "Yetkisiz",
      messageType: "TEXT",
      createdAt: new Date(),
    })
  );

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

  assert.equal((await getDoc(doc(aliceDb, "users/alice"))).exists(), true);
}

try {
  await run();
  console.log("Firestore rules tests passed.");
} finally {
  await env.cleanup();
}
