"use strict";
const {onCall,HttpsError}=require("firebase-functions/v2/https");
const {onDocumentCreated}=require("firebase-functions/v2/firestore");
const {onObjectFinalized}=require("firebase-functions/v2/storage");
const {FieldValue,FieldPath}=require("firebase-admin/firestore");
const {getAuth}=require("firebase-admin/auth");
const {getStorage}=require("firebase-admin/storage");
const {segment}=require("./policy");
const {fields,profilePatch,listingPatch,revision,recentLogin,terminalJob}=require("./account-policy");
module.exports=function install(db,database,options,reserve) {
  const jobs=db.collection("_accountDeletions");
  const fail=(code,message)=>{throw new HttpsError(code,message);};
  async function account(request) {
    if(!request.auth) fail("unauthenticated","Giriş yapın.");
    const uid=request.auth.uid;
    const user=await getAuth().getUser(uid).catch(e=>{if(e.code==="auth/user-not-found") return null;throw e;});
    const [job,profile]=await db.getAll(jobs.doc(uid),db.doc(`users/${uid}`));
    if(!user || user.disabled || job.exists || ["REQUESTED","PURGING"].includes(profile.data()?.deletionStatus)) fail("permission-denied","Hesap etkin değil.");
    return {uid,user,profile};
  }
  async function activeTransaction(tx,uid) {
    if((await tx.get(jobs.doc(uid))).exists) fail("permission-denied","Hesap silinme sürecinde.");
  }
  function target(input) {
    try {if(!["providers","requests"].includes(input?.kind)) throw Error();return {kind:input.kind,id:segment(input.id)};}
    catch {fail("invalid-argument","Geçersiz ilan.");}
  }
  const exports={};
  exports.getAccountProfile=onCall(options,async request=>{
    const {uid,profile,user}=await account(request);
    await reserve(uid,"accountRead",120,3600);
    return {displayName:profile.data()?.displayName || user.displayName || "",bio:profile.data()?.bio || "",email:user.email || "",revision:revision(profile.data()?.updatedAt)};
  });
  exports.updateAccountProfile=onCall(options,async request=>{
    const {uid,user}=await account(request);
    let patch;try {patch=profilePatch(request.data);} catch {fail("invalid-argument","Ad 2–80, hakkımda en fazla 1000 karakter olmalı.");}
    await reserve(uid,"profileEdit",30,3600);
    await db.runTransaction(async tx=>{
      await activeTransaction(tx,uid);
      const ref=db.doc(`users/${uid}`),old=await tx.get(ref);
      if(revision(old.data()?.updatedAt)!==request.data.revision) fail("aborted","Profil değişti. Yeniden yükleyip güncel bilgileri kontrol edin.");
      tx.set(ref,{...patch,uid,email:user.email,createdAt:old.data()?.createdAt || FieldValue.serverTimestamp(),updatedAt:FieldValue.serverTimestamp()},{merge:true});
    });
    // Firestore is canonical; an old Auth snapshot must never overwrite profile edits.
    await getAuth().updateUser(uid,{displayName:patch.displayName});
    const current=(await db.doc(`users/${uid}`).get()).data();
    return {displayName:current?.displayName || patch.displayName,bio:current?.bio || "",revision:revision(current?.updatedAt)};
  });
  exports.getListingManagement=onCall(options,async request=>{
    const {uid}=await account(request),{kind,id}=target(request.data);
    await reserve(uid,"accountRead",120,3600);
    return db.runTransaction(async tx=>{
      await activeTransaction(tx,uid);
      const snap=await tx.get(db.doc(`${kind}/${id}`));
      if(!snap.exists || snap.data().ownerUid!==uid) fail("permission-denied","İlan size ait değil.");
      const d=snap.data();
      const quotes=kind==="requests"?await tx.get(db.collection("quotes").where("requestId","==",id).limit(1)):null;
      const locked=kind==="requests" && (d.data.status!=="PENDING" || d.acceptedQuoteId!=="" || !quotes.empty);
      const available=d.visibility==="published" && !d.moderationStatus && !d.data.isReported;
      return {kind,id,revision:revision(d.updatedAt),visibility:d.visibility,editable:available && !locked,
        removable:available && (kind==="providers" || (d.data.status==="PENDING" && d.acceptedQuoteId==="")),
        fields:Object.fromEntries(Object.keys(fields[kind]).map(k=>[k,d.data[k] || ""]))};
    });
  });
  exports.manageListing=onCall(options,async request=>{
    const {uid}=await account(request),{kind,id}=target(request.data),input=request.data;
    if(!["edit","remove"].includes(input.action) || typeof input.revision!=="string" || !input.revision) fail("invalid-argument","İlanı yeniden açın.");
    let patch;try {patch=input.action==="edit"?listingPatch(kind,input.fields):{};} catch {fail("invalid-argument","İlan bilgilerini kontrol edin.");}
    await reserve(uid,"listingEdit",60,3600);
    await db.runTransaction(async tx=>{
      await activeTransaction(tx,uid);
      const ref=db.doc(`${kind}/${id}`),snap=await tx.get(ref);
      if(!snap.exists || snap.data().ownerUid!==uid) fail("permission-denied","İlan size ait değil.");
      const d=snap.data();
      if(revision(d.updatedAt)!==input.revision) fail("aborted","İlan değişti. Yeniden açarak güncel bilgileri kontrol edin.");
      if(d.visibility!=="published" || d.moderationStatus || d.data.isReported) fail("failed-precondition","Bu ilan düzenlenemez veya yeniden yayımlanamaz.");
      if(kind==="requests") {
        if(d.data.status!=="PENDING" || d.acceptedQuoteId!=="") fail("failed-precondition","Anlaşılmış iş düzenlenemez veya kaldırılamaz. İş yönetimini kullanın.");
        const quotes=await tx.get(db.collection("quotes").where("requestId","==",id).limit(1));
        if(input.action==="edit" && !quotes.empty) fail("failed-precondition","Teklif alınan talebin kapsamı değiştirilemez. Talebi kaldırıp yenisini oluşturabilirsiniz.");
      }
      const update={updatedAt:FieldValue.serverTimestamp()};
      if(input.action==="remove") Object.assign(update,{visibility:"archived",removedAt:FieldValue.serverTimestamp()});
      else for(const [k,v] of Object.entries(patch)) update[`data.${k}`]=v;
      tx.update(ref,update);
    });
    return {status:input.action==="remove"?"archived":"saved"};
  });
  exports.requestAccountDeletion=onCall(options,async request=>{
    if(!request.auth) fail("unauthenticated","Giriş yapın.");
    if(!recentLogin(request.auth.token)) fail("unauthenticated","Hesabı silmek için yeniden giriş yapın.");
    if(request.data?.confirmation!=="HESABIMI SİL") fail("invalid-argument","Silme onayı gerekli.");
    const uid=request.auth.uid;
    if((await jobs.doc(uid).get()).exists) return {status:"REQUESTED"};
    await account(request);
    await reserve(uid,"accountDeletion",10,3600);
    await db.runTransaction(async tx=>{
      if((await tx.get(jobs.doc(uid))).exists) return;
      const owned=await tx.get(db.collection("requests").where("ownerUid","==",uid));
      const offered=await tx.get(db.collection("quotes").where("providerUid","==",uid));
      const active=owned.docs.some(d=>!terminalJob(d.data()) && (d.data().acceptedQuoteId || d.data().data?.status!=="PENDING" || ["LOCKED","DISPUTED"].includes(d.data().data?.escrowStatus)));
      // ACCEPTED quote status remains historical after job completion: consult its job.
      const accepted=offered.docs.filter(d=>d.data().status==="ACCEPTED");
      const related=await Promise.all(accepted.map(d=>tx.get(db.doc(`requests/${segment(d.data().requestId)}`))));
      if(active || related.some((d,i)=>!d.exists || !terminalJob(d.data()) || d.data().acceptedProviderUid!==uid || d.data().acceptedQuoteId!==accepted[i].id || d.data().ownerUid!==accepted[i].data().customerUid)) fail("failed-precondition","Devam eden işinizi tamamlayın veya iş yönetiminden iptal edin; ardından hesap silmeyi tekrar deneyin.");
      tx.create(jobs.doc(uid),{status:"REQUESTED",requestedAt:FieldValue.serverTimestamp()});
      tx.set(db.doc(`users/${uid}`),{deletionStatus:"REQUESTED"},{merge:true});
    });
    return {status:"REQUESTED"};
  });
  // Retryable late-upload cleanup closes the crash window between object save
  // and Firestore media registration, including when the main purge completed.
  exports.purgeDeletedAccountPhoto=onObjectFinalized({region:options.region,retry:true,
    timeoutSeconds:60,memory:"256MiB",maxInstances:5,concurrency:2},async event=>{
    const object=event.data,path=object.name || "";
    const match=/^conversationMedia\/([^/]+)\/([^/]+)\/([^/]+)\.jpg$/.exec(path);
    if(!match || object.metadata?.uploaderUid!==match[2]) return;
    const uid=segment(match[2]);
    if((await jobs.doc(uid).get()).exists)
      await getStorage().bucket(object.bucket).file(path).delete({ignoreNotFound:true});
  });
  exports.purgeDeletedAccount=onDocumentCreated({region:options.region,database,document:"_accountDeletions/{uid}",retry:true,
    timeoutSeconds:540,memory:"512MiB",maxInstances:5,concurrency:1},async event=>{
    const uid=segment(event.params.uid),jobRef=jobs.doc(uid);
    if((await jobRef.get()).data()?.status==="COMPLETED") return;
    await getAuth().updateUser(uid,{disabled:true}).catch(e=>{if(e.code!=="auth/user-not-found") throw e;});
    await jobRef.update({status:"PURGING"});
    async function erase(query) {
      while(true) {const page=await query.limit(100).get();if(page.empty) break;for(const doc of page.docs) await db.recursiveDelete(doc.ref);}
    }
    for(const kind of ["providers","requests"]) {
      await erase(db.collection(kind).where("ownerUid","==",uid));
      await erase(db.collection(kind==="providers"?"providerContacts":"requestContacts").where("ownerUid","==",uid));
    }
    for(const field of ["providerUid","customerUid"]) await erase(db.collection("quotes").where(field,"==",uid));
    // Persist progress so a timeout/retry resumes instead of repeatedly revisiting
    // the same shared conversations on accounts with long histories.
    let cursor=(await jobRef.get()).data()?.conversationCursor || "";
    while(true) {
      let query=db.collection("conversations").where("participantUids","array-contains",uid).orderBy(FieldPath.documentId()).limit(100);
      if(cursor) query=query.startAfter(cursor);
      const conversations=await query.get();
      if(conversations.empty) break;
      for(const convo of conversations.docs) {
        await erase(convo.ref.collection("messages").where("senderUid","==",uid));
        await getStorage().bucket().deleteFiles({prefix:`conversationMedia/${convo.id}/${uid}/`});
        await erase(convo.ref.collection("media").where("uploaderUid","==",uid));
        await convo.ref.update(new FieldPath("names",uid),"Silinmiş hesap","lastMessage","","relatedItemTitle","","updatedAt",FieldValue.serverTimestamp());
        cursor=convo.id;
        await jobRef.update({conversationCursor:cursor,lastProgressAt:FieldValue.serverTimestamp()});
      }
    }
    // Terminal shared job evidence stays available to the other participant.
    // Remove the leaving actor's free text while retaining minimal audit metadata.
    let jobCursor=(await jobRef.get()).data()?.jobCursor || "";
    while(true) {
      let query=db.collection("jobs").where("participantUids","array-contains",uid).orderBy(FieldPath.documentId()).limit(100);
      if(jobCursor) query=query.startAfter(jobCursor);
      const page=await query.get();
      if(page.empty) break;
      for(const job of page.docs) {
        const progress=(await jobRef.get()).data();
        let eventCursor=progress.jobEventJob===job.id ? progress.jobEventCursor || "" : "";
        while(true) {
          let eventsQuery=job.ref.collection("events").where("actorUid","==",uid).orderBy(FieldPath.documentId()).limit(100);
          if(eventCursor) eventsQuery=eventsQuery.startAfter(eventCursor);
          const events=await eventsQuery.get();
          if(events.empty) break;
          const batch=db.batch();
          for(const event of events.docs) {
            batch.update(event.ref,{note:"",privacyRedacted:true});
            if(event.data().version===job.data().version) batch.update(job.ref,{note:"",privacyRedacted:true});
            eventCursor=event.id;
          }
          batch.update(jobRef,{jobEventJob:job.id,jobEventCursor:eventCursor,lastProgressAt:FieldValue.serverTimestamp()});
          await batch.commit();
        }
        jobCursor=job.id;
        await jobRef.update({jobCursor,jobEventJob:FieldValue.delete(),jobEventCursor:FieldValue.delete(),lastProgressAt:FieldValue.serverTimestamp()});
      }
    }
    await db.recursiveDelete(db.doc(`users/${uid}`));
    await getAuth().deleteUser(uid).catch(e=>{if(e.code!=="auth/user-not-found") throw e;});
    await jobRef.update({status:"COMPLETED",completedAt:FieldValue.serverTimestamp()});
  });
  return exports;
};
