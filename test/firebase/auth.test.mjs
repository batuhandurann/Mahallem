import { test } from 'node:test';
import assert from 'node:assert/strict';
import { initializeApp, deleteApp } from 'firebase/app';
import { getAuth, connectAuthEmulator, createUserWithEmailAndPassword, signInWithEmailAndPassword, signOut, updateProfile, deleteUser } from 'firebase/auth';
import { getFirestore, connectFirestoreEmulator, doc, setDoc, getDoc, serverTimestamp, terminate } from 'firebase/firestore';
test('real Auth emulator: register/login/logout, invalid password, token UID enforces private profile rules',async()=>{
  const app=initializeApp({projectId:'demo-mahallem',apiKey:'fake-emulator-key',appId:'test'},`auth-${Date.now()}`);
  const auth=getAuth(app);connectAuthEmulator(auth,'http://127.0.0.1:9099',{disableWarnings:true});
  const db=getFirestore(app,'mahallem');connectFirestoreEmulator(db,'127.0.0.1',8080);
  const email=`auth-${Date.now()}@example.com`,password='SecurePass123!';
  try {
    const {user}=await createUserWithEmailAndPassword(auth,email,password);
    assert.ok(user.uid);
    await updateProfile(user,{displayName:'Android Test'});
    await setDoc(doc(db,`users/${user.uid}`),{uid:user.uid,displayName:'Android Test',email,createdAt:serverTimestamp(),updatedAt:serverTimestamp()});
    await assert.rejects(setDoc(doc(db,'users/someone-else'),{uid:'someone-else',displayName:'Forged',email,createdAt:serverTimestamp(),updatedAt:serverTimestamp()}),e=>e.code==='permission-denied');
    await signOut(auth);assert.equal(auth.currentUser,null);
    await assert.rejects(signInWithEmailAndPassword(auth,email,'wrong-password'));
    await assert.rejects(getDoc(doc(db,`users/${user.uid}`)),e=>e.code==='permission-denied');
    const result=await signInWithEmailAndPassword(auth,email,password);
    assert.equal(result.user.uid,user.uid);
    assert.equal((await getDoc(doc(db,`users/${user.uid}`))).data().uid,user.uid);
    await deleteUser(result.user);
  } finally {await terminate(db);await deleteApp(app);}
});
