import { test } from 'node:test';
import assert from 'node:assert/strict';
import { initializeApp, deleteApp } from 'firebase/app';
import { getAuth, connectAuthEmulator, createUserWithEmailAndPassword, signInWithEmailAndPassword, signOut, updateProfile, deleteUser, sendPasswordResetEmail } from 'firebase/auth';
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

import { readFileSync } from 'node:fs';
import { collection, getDocs, query, where, or, writeBatch, runTransaction } from 'firebase/firestore';
const fixture=JSON.parse(readFileSync(new URL('./fixtures.json',import.meta.url)));
test('named database with real Auth UIDs: request -> owned provider -> quote -> chat -> atomic acceptance', {timeout:60000}, async()=>{
  const suffix=Date.now();
  function client(label){
    const app=initializeApp({projectId:'demo-mahallem',apiKey:'fake-emulator-key',appId:'test'},`${label}-${suffix}`);
    const auth=getAuth(app);connectAuthEmulator(auth,'http://127.0.0.1:9099',{disableWarnings:true});
    const db=getFirestore(app,'mahallem');connectFirestoreEmulator(db,'127.0.0.1',8080);return {app,auth,db};
  }
  const a=client('customer'),b=client('provider'),e=client('outsider');
  const stamp=()=>({createdAt:serverTimestamp(),updatedAt:serverTimestamp()});
  try {
    await Promise.all([a,b,e].map((c,i)=>createUserWithEmailAndPassword(c.auth,`named-${suffix}-${i}@example.com`,'SecurePass123!')));
    const au=a.auth.currentUser.uid,bu=b.auth.currentUser.uid;
    const r=`request-${suffix}`,p=`provider-${suffix}`,q=`${r}_${bu}`,conv=`conv-${suffix}`;
    const batch=writeBatch(a.db);
    batch.set(doc(a.db,`requests/${r}`),{ownerUid:au,visibility:'published',acceptedQuoteId:'',acceptedProviderUid:'',data:{...fixture.request,id:r,ownerUid:au},...stamp()});
    batch.set(doc(a.db,`requestContacts/${r}`),{ownerUid:au,phone:'555',address:'Private street',updatedAt:serverTimestamp()});
    await batch.commit();
    await assert.rejects(getDoc(doc(b.db,`requestContacts/${r}`)),x=>x.code==='permission-denied');
    await setDoc(doc(b.db,`providers/${p}`),{ownerUid:bu,visibility:'published',data:{...fixture.provider,id:p,ownerUid:bu},...stamp()});
    await setDoc(doc(b.db,`quotes/${q}`),{providerUid:bu,customerUid:au,requestId:r,status:'PENDING',data:{...fixture.quote,id:q,requestId:r,providerId:p,providerUid:bu,customerUid:au},...stamp()});
    assert.equal((await getDocs(query(collection(a.db,'quotes'),or(where('customerUid','==',au),where('providerUid','==',au))))).docs.filter(x=>x.id===q).length,1);
    await assert.rejects(getDoc(doc(e.db,`quotes/${q}`)),x=>x.code==='permission-denied');
    await setDoc(doc(b.db,`conversations/${conv}`),{participantUids:[au,bu],names:{[au]:'Customer',[bu]:'Provider'},relatedItemTitle:'Boya',lastMessage:'',...stamp()});
    const msg=writeBatch(b.db);
    msg.set(doc(b.db,`conversations/${conv}/messages/m`),{senderUid:bu,data:{...fixture.message,id:'m',conversationId:conv,senderId:bu},...stamp()});
    msg.update(doc(b.db,`conversations/${conv}`),{lastMessage:'Merhaba',updatedAt:serverTimestamp()});
    await msg.commit();
    assert.equal((await getDoc(doc(a.db,`conversations/${conv}/messages/m`))).data().senderUid,bu);
    assert.equal((await getDocs(query(collection(a.db,'conversations'),where('participantUids','array-contains',au)))).docs.filter(x=>x.id===conv).length,1);
    await assert.rejects(getDoc(doc(e.db,`conversations/${conv}/messages/m`)),x=>x.code==='permission-denied');
    await runTransaction(a.db,async tx=>{
      const rr=doc(a.db,`requests/${r}`),qr=doc(a.db,`quotes/${q}`);
      assert.equal((await tx.get(rr)).data().acceptedQuoteId,'');
      assert.equal((await tx.get(qr)).data().status,'PENDING');
      tx.update(qr,{status:'ACCEPTED',updatedAt:serverTimestamp()});
      tx.update(rr,{'data.status':'ACCEPTED',acceptedQuoteId:q,acceptedProviderUid:bu,updatedAt:serverTimestamp()});
    });
    assert.equal((await getDoc(doc(b.db,`requestContacts/${r}`))).data().address,'Private street');
    await assert.rejects(getDoc(doc(e.db,`requestContacts/${r}`)),x=>x.code==='permission-denied');
  } finally {
    for(const c of [a,b,e]){if(c.auth.currentUser)await deleteUser(c.auth.currentUser);await terminate(c.db);await deleteApp(c.app);}
  }
});


test('real Auth emulator: duplicate account, invalid email, password reset and repeat login', async () => {
  const suffix = `${Date.now()}-${Math.random().toString(36).slice(2)}`;
  const app = initializeApp({projectId:'demo-mahallem',apiKey:'fake-emulator-key',appId:'test'},`auth-regression-${suffix}`);
  const auth = getAuth(app);
  connectAuthEmulator(auth,'http://127.0.0.1:9099',{disableWarnings:true});
  const email = `regression-${suffix}@example.com`;
  const password = 'SecurePass123!';
  try {
    await assert.rejects(createUserWithEmailAndPassword(auth,'not-an-email',password),error => error.code === 'auth/invalid-email');
    const first = await createUserWithEmailAndPassword(auth,email,password);
    assert.ok(first.user.uid);
    await assert.rejects(createUserWithEmailAndPassword(auth,email,password),error => error.code === 'auth/email-already-in-use');
    await sendPasswordResetEmail(auth,email);
    await signOut(auth);
    assert.equal(auth.currentUser,null);
    await assert.rejects(signInWithEmailAndPassword(auth,email,'WrongPassword123!'));
    const returning = await signInWithEmailAndPassword(auth,email,password);
    assert.equal(returning.user.uid,first.user.uid);
    await signOut(auth);
    assert.equal(auth.currentUser,null);
  } finally {
    if(auth.currentUser) await deleteUser(auth.currentUser);
    await deleteApp(app);
  }
});

test('Auth login fails closed during network failure without persisting a session', {timeout: 20000}, async () => {
  // Port 1 is intentionally unreachable. This is still a real Firebase Auth SDK call,
  // not a mocked success path; it must never fall back to a stored user.
  const app = initializeApp(
    {projectId: 'demo-mahallem', apiKey: 'fake-emulator-key', appId: 'test'},
    `offline-auth-${Date.now()}-${Math.random().toString(36).slice(2)}`
  );
  const auth = getAuth(app);
  connectAuthEmulator(auth, 'http://127.0.0.1:1', {disableWarnings: true});
  try {
    await assert.rejects(
      signInWithEmailAndPassword(auth, 'offline@example.com', 'SecurePass123!'),
      error => error.code === 'auth/network-request-failed'
    );
    assert.equal(auth.currentUser, null, 'Failed offline login must not restore any user');
  } finally {
    await deleteApp(app);
  }
});
