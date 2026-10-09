"use strict";
const fields = {
  providers: { name:[2,80], title:[2,120], bio:[0,2000], hourlyOrBasePrice:[1,80] },
  requests: { title:[2,120], renovationNotes:[0,2000], extraServicesRequested:[0,2000], budgetEstimate:[0,80], eventOrJobDate:[10,10], eventTime:[5,5] }
};
function text(value,min,max) {
  if(typeof value!=="string" || value.trim().length<min || value.length>max || /[\u0000-\u0008\u000b\u000c\u000e-\u001f]/.test(value)) throw Error("Geçersiz metin.");
  return value.trim();
}
function profilePatch(input) {
  if(!input || Object.keys(input).some(k=>!["displayName","bio","revision"].includes(k)) || typeof input.revision!=="string") throw Error("Geçersiz profil alanı.");
  const displayName=text(input.displayName,2,80);
  if(/[\u0000-\u001f\u007f-\u009f]/.test(displayName)) throw Error("Ad tek satır olmalı.");
  return {displayName,bio:text(input.bio,0,1000)};
}
function listingPatch(kind,input) {
  if(!fields[kind] || !input || !Object.keys(input).length || Object.keys(input).some(k=>!Object.hasOwn(fields[kind],k))) throw Error("Geçersiz ilan alanı.");
  const patch=Object.fromEntries(Object.entries(input).map(([k,v])=>[k,text(v,...fields[kind][k])]));
  for(const key of ["title","name","hourlyOrBasePrice","budgetEstimate"])
    if(patch[key] && /[\u0000-\u001f\u007f-\u009f]/.test(patch[key])) throw Error("Bu alan tek satır olmalı.");
  if(patch.eventOrJobDate) {
    const value=patch.eventOrJobDate,date=new Date(`${value}T00:00:00.000Z`);
    if(!/^\d{4}-\d{2}-\d{2}$/.test(value) || !Number.isFinite(date.getTime()) || date.toISOString().slice(0,10)!==value) throw Error("Geçersiz tarih.");
  }
  if(patch.eventTime && !/^([01]\d|2[0-3]):[0-5]\d$/.test(patch.eventTime)) throw Error("Geçersiz saat.");
  return patch;
}
function revision(stamp) {return stamp?`${stamp.seconds}:${stamp.nanoseconds}`:"";}
function recentLogin(token,now=Date.now()) {return Number.isInteger(token?.auth_time) && now/1000-token.auth_time>=0 && now/1000-token.auth_time<=300;}
function terminalJob(data) {return ["COMPLETED","CANCELLED"].includes(data?.data?.status) && !["LOCKED","DISPUTED"].includes(data?.data?.escrowStatus);}
module.exports={fields,text,profilePatch,listingPatch,revision,recentLogin,terminalJob};
