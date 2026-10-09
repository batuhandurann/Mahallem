import {test,before,after} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {randomUUID} from 'node:crypto';
import {createRequire} from 'node:module';
import {initializeApp,deleteApp} from 'firebase/app';
import {getAuth,connectAuthEmulator,createUserWithEmailAndPassword} from 'firebase/auth';
const require=createRequire(new URL('../../functions/package.json',import.meta.url));
const {initializeApp:adminApp,deleteApp:deleteAdminApp}=require('firebase-admin/app');
const {getFirestore,FieldValue}=require('firebase-admin/firestore');
const {getAuth:adminAuth}=require('firebase-admin/auth');
const {getStorage}=require('firebase-admin/storage');
const f=JSON.parse(readFileSync(new URL('../firebase/fixtures.json',import.meta.url)));
const projectId='demo-mahallem';let admin,db,owner,other;const apps=[];
async function account(label) {
  const app=initializeApp({projectId,apiKey:'fake-emulator-key'},`account-${label}-${Date.now()}`);apps.push(app);
  const auth=getAuth(app);connectAuthEmulator(auth,`http://${process.env.FIREBASE_AUTH_EMULATOR_HOST}`,{disableWarnings:true});
  const user=(await createUserWithEmailAndPassword(auth,`${label}-${Date.now()}@example.com`,'Strong-password123!')).user;
  return {uid:user.uid,token:await user.getIdToken()};
}
async function call(name,user,data) {
  const r=await fetch(`http://127.0.0.1:5001/${projectId}/europe-west3/${name}`,{method:'POST',headers:{'content-type':'application/json',...(user?{authorization:`Bearer ${user.token}`}:{})},body:JSON.stringify({data})});
  const body=await r.json();return {status:r.status,body,result:body.result || body.data};
}
function listing(kind,id,uid,patch={}) {return {ownerUid:uid,visibility:'published',createdAt:FieldValue.serverTimestamp(),updatedAt:FieldValue.serverTimestamp(),...(kind==='requests'?{acceptedQuoteId:'',acceptedProviderUid:''}:{}),data:{...(kind==='requests'?f.request:f.provider),id,ownerUid:uid,...patch}};}
before(async()=>{
  assert.ok(process.env.FIRESTORE_EMULATOR_HOST && process.env.FIREBASE_AUTH_EMULATOR_HOST && process.env.FIREBASE_STORAGE_EMULATOR_HOST,'Emulators required');
  admin=adminApp({projectId,storageBucket:`${projectId}.appspot.com`},`account-admin-${Date.now()}`);db=getFirestore(admin,'mahallem');
  [owner,other]=await Promise.all(['owner','other'].map(account));
});
after(async()=>{await Promise.all(apps.map(deleteApp));if(admin)await deleteAdminApp(admin);});
test('real callable profile/listing ownership, conflict, scope and moderation boundaries',async()=>{
  assert.equal((await call('getAccountProfile',null,{})).status,401);
  const profile=(await call('getAccountProfile',owner,{})).result;
  const update={displayName:'Batuhan Duran',bio:'Software developer',revision:profile.revision};
  const saved=await call('updateAccountProfile',owner,update);assert.equal(saved.status,200,JSON.stringify(saved.body));
  assert.equal((await db.doc(`users/${owner.uid}`).get()).data().displayName,'Batuhan Duran');
  assert.equal((await adminAuth(admin).getUser(owner.uid)).displayName,'Batuhan Duran');
  assert.equal((await call('updateAccountProfile',owner,update)).status,409);
  assert.equal((await call('updateAccountProfile',owner,{...update,revision:saved.result.revision,role:'admin'})).status,400);
  const id=`managed-${owner.uid}`,ref=db.doc(`requests/${id}`);await ref.set(listing('requests',id,owner.uid,{status:'PENDING'}));
  assert.equal((await call('getListingManagement',other,{kind:'requests',id})).status,403);
  const opened=await call('getListingManagement',owner,{kind:'requests',id});assert.equal(opened.status,200,JSON.stringify(opened.body));
  const input={kind:'requests',id,revision:opened.result.revision,action:'edit',fields:{title:'Updated job title'}};
  assert.equal((await call('manageListing',other,input)).status,403);
  assert.equal((await call('manageListing',owner,{...input,fields:{ownerUid:other.uid}})).status,400);
  assert.equal((await call('manageListing',owner,input)).status,200);
  assert.equal((await ref.get()).data().data.title,'Updated job title');
  assert.equal((await call('manageListing',owner,input)).status,409);
  await db.doc(`quotes/${id}`).set({requestId:id,customerUid:owner.uid,providerUid:other.uid,status:'PENDING'});
  const quoted=(await call('getListingManagement',owner,{kind:'requests',id})).result;assert.equal(quoted.editable,false);assert.equal(quoted.removable,true);
  assert.equal((await call('manageListing',owner,{...input,revision:quoted.revision})).status,400);
  await ref.update({'data.status':'ACCEPTED',acceptedQuoteId:id,acceptedProviderUid:other.uid});
  const agreed=(await call('getListingManagement',owner,{kind:'requests',id})).result;assert.equal(agreed.removable,false);
  assert.equal((await call('requestAccountDeletion',owner,{confirmation:'HESABIMI SİL'})).status,400);
  assert.equal((await call('manageListing',owner,{...input,revision:agreed.revision,action:'remove'})).status,400);
  await ref.update({'data.status':'PENDING',acceptedQuoteId:'',acceptedProviderUid:''});
  const removable=(await call('getListingManagement',owner,{kind:'requests',id})).result;
  assert.equal((await call('manageListing',owner,{...input,revision:removable.revision,action:'remove'})).status,200);
  assert.equal((await ref.get()).data().visibility,'archived');
  assert.equal((await call('manageJob',owner,{requestId:id,actionId:randomUUID(),action:'CANCEL_OPEN',version:0,note:'Archived request must remain unchanged',reasonCode:'OTHER'})).status,400);
  const hiddenId=`hidden-${owner.uid}`;await db.doc(`providers/${hiddenId}`).set({...listing('providers',hiddenId,owner.uid),visibility:'hidden',moderationStatus:'hidden'});
  const hidden=(await call('getListingManagement',owner,{kind:'providers',id:hiddenId})).result;assert.equal(hidden.editable,false);
  assert.equal((await call('manageListing',owner,{kind:'providers',id:hiddenId,revision:hidden.revision,action:'edit',fields:{title:'Republish'}})).status,400);
});
test('actual deletion trigger purges Auth/private data/own media while preserving other account',async()=>{
  const leaving=await account('leaving'),id=`delete-${leaving.uid}`;
  await db.doc(`users/${leaving.uid}`).set({uid:leaving.uid,displayName:'Leaving',email:'private@example.com'});
  await db.doc(`users/${leaving.uid}/devices/device`).set({token:'sensitive-token'});
  await db.doc(`users/${leaving.uid}/favorites/p`).set({});
  const cancelledId=`cancel-${leaving.uid}`;
  await db.doc(`requests/${cancelledId}`).set(listing('requests',cancelledId,leaving.uid,{status:'PENDING',escrowStatus:'NONE',escrowAmount:''}));
  const cancelled=await call('manageJob',leaving,{requestId:cancelledId,actionId:randomUUID(),action:'CANCEL_OPEN',version:0,note:'Private cancellation explanation',reasonCode:'OTHER'});
  assert.equal(cancelled.status,200,JSON.stringify(cancelled.body));
  await db.doc(`providers/${id}`).set(listing('providers',id,leaving.uid));
  await db.doc(`providerContacts/${id}`).set({ownerUid:leaving.uid,phone:'+905551234567'});
  const convo=db.doc(`conversations/${id}`);
  await convo.set({participantUids:[leaving.uid,other.uid],names:{[leaving.uid]:'Leaving',[other.uid]:'Other'},lastMessage:'Private text',relatedItemTitle:'Private title'});
  await convo.collection('messages').doc('mine').set({senderUid:leaving.uid,data:{text:'Private'}});
  await convo.collection('messages').doc('theirs').set({senderUid:other.uid,data:{text:'Keep'}});
  const path=`conversationMedia/${id}/${leaving.uid}/media.jpg`;
  await getStorage(admin).bucket().file(path).save(Buffer.from('private-photo'));
  await convo.collection('media').doc('media').set({uploaderUid:leaving.uid,storagePath:path});
  const otherPath=`conversationMedia/${id}/${other.uid}/retained.jpg`;
  await getStorage(admin).bucket().file(otherPath).save(Buffer.from('keep-other-media'),{metadata:{contentType:'image/jpeg'}});
  await convo.collection('media').doc('retained').set({uploaderUid:other.uid,storagePath:otherPath});
  assert.equal((await call('requestAccountDeletion',leaving,{confirmation:'WRONG'})).status,400);
  const started=await call('requestAccountDeletion',leaving,{confirmation:'HESABIMI SİL'});assert.equal(started.status,200,JSON.stringify(started.body));
  assert.equal(started.result.status,'REQUESTED');assert.equal((await call('getAccountProfile',leaving,{})).status,403);
  const deadline=Date.now()+90_000;
  while((await db.doc(`_accountDeletions/${leaving.uid}`).get()).data()?.status!=='COMPLETED' && Date.now()<deadline)await new Promise(r=>setTimeout(r,500));
  assert.equal((await db.doc(`_accountDeletions/${leaving.uid}`).get()).data().status,'COMPLETED');
  for(const p of [`users/${leaving.uid}`,`users/${leaving.uid}/devices/device`,`providers/${id}`,`providerContacts/${id}`,`conversations/${id}/messages/mine`,`conversations/${id}/media/media`])assert.equal((await db.doc(p).get()).exists,false,p);
  assert.equal((await convo.collection('messages').doc('theirs').get()).data().data.text,'Keep');
  assert.equal((await convo.get()).data().names[leaving.uid],'Silinmiş hesap');
  const retainedJob=(await db.doc(`jobs/${cancelledId}`).get()).data();
  assert.equal(retainedJob.status,'CANCELLED');assert.equal(retainedJob.note,'');assert.equal(retainedJob.privacyRedacted,true);
  const retainedEvent=(await db.collection(`jobs/${cancelledId}/events`).get()).docs[0].data();
  assert.equal(retainedEvent.note,'');assert.equal(retainedEvent.privacyRedacted,true);
  assert.equal((await getStorage(admin).bucket().file(path).exists())[0],false);
  assert.equal((await getStorage(admin).bucket().file(otherPath).exists())[0],true);
  assert.equal((await call('readConversationPhoto',other,{conversationId:id,mediaId:'retained'})).status,200);
  assert.equal((await call('uploadConversationPhoto',other,{conversationId:id,base64:'aGVsbG8='})).status,403);
  assert.equal((await call('readConversationPhoto',leaving,{conversationId:id,mediaId:'retained'})).status,403);
  assert.equal((await call('getListingTrust',leaving,{kind:'providers',ids:[id]})).status,403);
  await assert.rejects(adminAuth(admin).getUser(leaving.uid),e=>e.code==='auth/user-not-found');
  assert.ok(await adminAuth(admin).getUser(other.uid));
  // Simulate a trusted upload finishing after the durable purge is already complete.
  const late=`conversationMedia/${id}/${leaving.uid}/late.jpg`;
  await getStorage(admin).bucket().file(late).save(Buffer.from('late-upload'),{metadata:{metadata:{uploaderUid:leaving.uid}}});
  const lateDeadline=Date.now()+30_000;
  while((await getStorage(admin).bucket().file(late).exists())[0] && Date.now()<lateDeadline) await new Promise(r=>setTimeout(r,500));
  assert.equal((await getStorage(admin).bucket().file(late).exists())[0],false);

});
test('actual job completion unlocks provider deletion and redacts only its shared history notes',async()=>{
  const leaving=await account('completed-provider'),id=`terminal-${leaving.uid}`,quoteId=`terminal-quote-${leaving.uid}`;
  await db.doc(`requests/${id}`).set({...listing('requests',id,other.uid,{status:'ACCEPTED',escrowStatus:'NONE',escrowAmount:''}),acceptedQuoteId:quoteId,acceptedProviderUid:leaving.uid});
  await db.doc(`quotes/${quoteId}`).set({providerUid:leaving.uid,customerUid:other.uid,requestId:id,status:'ACCEPTED',data:{...f.quote,escrowFunded:false}});
  assert.equal((await call('requestAccountDeletion',leaving,{confirmation:'HESABIMI SİL'})).status,400);
  const commands=[
    [leaving,'SUBMIT_COMPLETION',0,'Provider private completion details'],
    [other,'REQUEST_REVISION',1,'Counterpart details remain available'],
    [leaving,'SUBMIT_COMPLETION',2,'Provider second private completion note'],
    [other,'CONFIRM_COMPLETION',3,'']
  ];
  for(const [actor,action,version,note] of commands) {
    const result=await call('manageJob',actor,{requestId:id,actionId:randomUUID(),action,version,note,reasonCode:''});
    assert.equal(result.status,200,JSON.stringify(result.body));
  }
  assert.equal((await db.doc(`jobs/${id}`).get()).data().status,'COMPLETED');
  assert.equal((await call('requestAccountDeletion',leaving,{confirmation:'HESABIMI SİL'})).status,200);
  const deadline=Date.now()+90_000;
  while((await db.doc(`_accountDeletions/${leaving.uid}`).get()).data()?.status!=='COMPLETED' && Date.now()<deadline)await new Promise(r=>setTimeout(r,500));
  assert.equal((await db.doc(`_accountDeletions/${leaving.uid}`).get()).data().status,'COMPLETED');
  const events=await db.collection(`jobs/${id}/events`).get();
  assert.equal(events.size,4);
  for(const event of events.docs.filter(d=>d.data().actorUid===leaving.uid)) {
    assert.equal(event.data().note,'');assert.equal(event.data().privacyRedacted,true);
  }
  assert.equal(events.docs.find(d=>d.data().version===2).data().note,'Counterpart details remain available');
  assert.equal((await db.doc(`requests/${id}`).get()).exists,true);
  assert.equal((await db.doc(`quotes/${quoteId}`).get()).exists,false);
  assert.equal((await call('manageJob',leaving,{requestId:id,actionId:randomUUID(),action:'START',version:4,note:'',reasonCode:''})).status,403);
});
