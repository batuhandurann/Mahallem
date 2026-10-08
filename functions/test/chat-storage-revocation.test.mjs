import { readFileSync } from "node:fs";
import { test } from "node:test";
import { initializeTestEnvironment, assertSucceeds, assertFails } from "@firebase/rules-unit-testing";
import { doc, setDoc } from "firebase/firestore";
import { ref, uploadBytes, getBytes } from "firebase/storage";

// Regression for stale upload-grant membership. This test is expected to FAIL
// against current Storage Rules: grant.participantIds is a snapshot that does
// not reflect removal from the live conversation.
test("revoked conversation participant must not download existing chat media", async () => {
  const projectId = process.env.FIREBASE_PROJECT_ID || "mahallem-chat-revocation-test";
  const rules = readFileSync(new URL("../../storage.rules", import.meta.url), "utf8");
  const env = await initializeTestEnvironment({ projectId, storage: { rules } });
  try {
    const alice = env.authenticatedContext("alice");
    const bob = env.authenticatedContext("bob");
    const path = "chatAttachments/chat-1/alice/grant-chat.jpg";
    const image = new Uint8Array([0x89, 0x50, 0x4e, 0x47]);
    await env.withSecurityRulesDisabled(async (ctx) => {
      const db = ctx.firestore();
      await setDoc(doc(db, "users/alice"), { uid: "alice", deletionStatus: "ACTIVE" });
      await setDoc(doc(db, "users/bob"), { uid: "bob", deletionStatus: "ACTIVE" });
      await setDoc(doc(db, "conversations/chat-1"), { participantIds: ["alice", "bob"] });
      await setDoc(doc(db, "users/alice/uploadGrants/grant-chat"), {
        ownerUid: "alice",
        kind: "CHAT",
        conversationId: "chat-1",
        participantIds: ["alice", "bob"],
        expiresAt: new Date(Date.now() + 600_000),
      });
    });
    const aliceFile = ref(alice.storage("gs://" + projectId + ".appspot.com"), path);
    const bobFile = ref(bob.storage("gs://" + projectId + ".appspot.com"), path);
    await assertSucceeds(uploadBytes(aliceFile, image, { contentType: "image/png" }));
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), "users/alice/uploadGrants/grant-chat"), {
        validated: true,
      }, { merge: true });
    });
    await assertSucceeds(getBytes(bobFile, 64));
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), "conversations/chat-1"), {
        participantIds: ["alice", "charlie"],
      });
    });
    // Security invariant: Bob's old grant must not outlive his membership.
    await assertFails(getBytes(bobFile, 64));
  } finally {
    await env.cleanup();
  }
});
