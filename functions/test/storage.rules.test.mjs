import { readFileSync } from "node:fs";
import {
  initializeTestEnvironment,
  assertSucceeds,
  assertFails,
} from "@firebase/rules-unit-testing";
import { doc, setDoc } from "firebase/firestore";
import { ref, uploadBytes, getBytes } from "firebase/storage";

const rules = readFileSync(new URL("../../storage.rules", import.meta.url), "utf8");
const projectId = process.env.FIREBASE_PROJECT_ID || "mahallem-rules-test";
const bucket = projectId + ".appspot.com";

const env = await initializeTestEnvironment({
  projectId,
  storage: { rules },
});

async function run() {
  const alice = env.authenticatedContext("alice");
  const bob = env.authenticatedContext("bob");

  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), "users/alice"), {
      uid: "alice",
      role: "user",
      deletionStatus: "ACTIVE",
    });
    await setDoc(doc(ctx.firestore(), "users/bob"), {
      uid: "bob",
      role: "user",
      deletionStatus: "ACTIVE",
    });
  });

  const validImage = new Uint8Array([0x89, 0x50, 0x4e, 0x47]);

  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), "users/alice/uploadGrants/grant-user"), {
      ownerUid: "alice",
      kind: "USER",
      expiresAt: new Date(Date.now() + 10 * 60 * 1000),
    });
  });

  await assertSucceeds(
    uploadBytes(ref(alice.storage("gs://" + bucket), "users/alice/images/grant-user.jpg"), validImage, {
      contentType: "image/png",
    })
  );
  await assertFails(
    uploadBytes(ref(alice.storage("gs://" + bucket), "users/alice/images/grant-user.jpg"), validImage, {
      contentType: "image/png",
    })
  );

  await assertFails(
    uploadBytes(ref(bob.storage("gs://" + bucket), "users/alice/images/blocked.jpg"), validImage, {
      contentType: "image/png",
    })
  );
  await assertFails(
    uploadBytes(ref(alice.storage("gs://" + bucket), "users/alice/images/no-grant.jpg"), validImage, {
      contentType: "image/png",
    })
  );

  await assertFails(
    uploadBytes(ref(alice.storage("gs://" + bucket), "users/alice/images/file.jpg"), validImage, {
      contentType: "text/plain",
    })
  );
  await assertFails(
    uploadBytes(ref(alice.storage("gs://" + bucket), "users/alice/images/grant-user.png"), validImage, {
      contentType: "image/png",
    })
  );
  await assertFails(
    uploadBytes(ref(alice.storage("gs://" + bucket), "users/alice/images/grant.user.jpg"), validImage, {
      contentType: "image/png",
    })
  );

  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), "users/alice-deleting"), {
      uid: "alice-deleting",
      deletionStatus: "REQUESTED",
    });
  });
  const deleting = env.authenticatedContext("alice-deleting");
  await assertFails(
    uploadBytes(ref(deleting.storage("gs://" + bucket), "users/alice-deleting/images/blocked.jpg"), validImage, {
      contentType: "image/png",
    })
  );

  await assertFails(
    uploadBytes(ref(alice.storage("gs://" + bucket), "jobRequests/alice/request-1/photo.jpg"), validImage, {
      contentType: "image/png",
    })
  );

  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), "jobRequests/request-1"), {
      ownerId: "alice",
      status: "PENDING",
    });
    await setDoc(doc(ctx.firestore(), "users/alice/uploadGrants/grant-job"), {
      ownerUid: "alice",
      kind: "JOB_REQUEST",
      requestId: "request-1",
      expiresAt: new Date(Date.now() + 10 * 60 * 1000),
    });
  });

  await assertSucceeds(
    uploadBytes(ref(alice.storage("gs://" + bucket), "jobRequests/alice/request-1/images/grant-job.jpg"), validImage, {
      contentType: "image/png",
    })
  );

  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), "users/alice/uploadGrants/expired-user"), {
      ownerUid: "alice",
      kind: "USER",
      expiresAt: new Date(Date.now() - 60_000),
      validated: true,
    });
  });
  await assertFails(
    uploadBytes(ref(alice.storage("gs://" + bucket), "users/alice/images/expired-user.jpg"), validImage, {
      contentType: "image/png",
    })
  );

  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), "conversations/chat-1"), {
      participantIds: ["alice", "bob"],
    });
  });

  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), "users/alice/uploadGrants/grant-chat"), {
      ownerUid: "alice",
      kind: "CHAT",
      conversationId: "chat-1",
      participantIds: ["alice", "bob"],
      expiresAt: new Date(Date.now() + 10 * 60 * 1000),
    });
  });

  const chatPath = "chatAttachments/chat-1/alice/grant-chat.jpg";
  await assertSucceeds(
    uploadBytes(ref(alice.storage("gs://" + bucket), chatPath), validImage, {
      contentType: "image/png",
    })
  );

  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), "users/alice/uploadGrants/grant-chat"), {
      ownerUid: "alice",
      kind: "CHAT",
      conversationId: "chat-1",
      participantIds: ["alice", "bob"],
      expiresAt: new Date(Date.now() - 60_000),
      validated: true,
    });
  });

  await assertSucceeds(
    getBytes(ref(bob.storage("gs://" + bucket), chatPath), 64)
  );

  const charlie = env.authenticatedContext("charlie");
  await assertFails(
    getBytes(ref(charlie.storage("gs://" + bucket), chatPath), 64)
  );

  console.log("Storage rules tests passed.");
}

try {
  await run();
} finally {
  await env.cleanup();
}
