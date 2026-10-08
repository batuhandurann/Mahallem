import assert from "node:assert/strict";
import { initializeApp, deleteApp } from "firebase/app";
import { getAuth, connectAuthEmulator, createUserWithEmailAndPassword,
  signInWithEmailAndPassword, signOut } from "firebase/auth";

if (!process.env.FIREBASE_AUTH_EMULATOR_HOST) throw new Error("Auth emulator is required; no live fallback.");
const app = initializeApp({ projectId: "demo-mahallem-rules-test", apiKey: "demo-key" }, "auth-negative-tests");
const auth = getAuth(app);
connectAuthEmulator(auth, `http://${process.env.FIREBASE_AUTH_EMULATOR_HOST}`, { disableWarnings: true });
const email = `auth-${Date.now()}@example.test`;
const password = "EmulatorOnly-LongPassword-2026";
try {
  const created = await createUserWithEmailAndPassword(auth, email, password);
  const uid = created.user.uid;
  await assert.rejects(createUserWithEmailAndPassword(auth, email, password),
    (e) => e.code === "auth/email-already-in-use");
  await signOut(auth);
  assert.equal(auth.currentUser, null);
  await assert.rejects(signInWithEmailAndPassword(auth, email, "wrong-password"),
    (e) => ["auth/wrong-password", "auth/invalid-credential"].includes(e.code));
  assert.equal(auth.currentUser, null);
  const login = await signInWithEmailAndPassword(auth, email, password);
  assert.equal(login.user.uid, uid);
  await signOut(auth);
  assert.equal(auth.currentUser, null);
  console.log("Auth emulator: registration, duplicate email, wrong password, logout and re-login PASS.");
} finally {
  await deleteApp(app);
}
