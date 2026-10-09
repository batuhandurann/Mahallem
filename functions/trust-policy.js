"use strict";
// Shared exact normalization: Turkish mobile contacts only, no arbitrary text stripping.
function mobile(value) {
  if (typeof value !== "string" || !/^[+0-9 ()-]{1,40}$/.test(value)) return null;
  let number = value.replace(/[ ()-]/g, "");
  if (/^05\d{9}$/.test(number)) number = "+90" + number.slice(1);
  else if (/^5\d{9}$/.test(number)) number = "+90" + number;
  else if (/^905\d{9}$/.test(number)) number = "+" + number;
  return /^\+905\d{9}$/.test(number) ? number : null;
}
function phoneMatches(account, contact, profile) {
  return !!account && !account.disabled && typeof account.uid === "string" && account.uid.length > 0
    && profile?.uid === account.uid && !["REQUESTED", "PURGING"].includes(profile.deletionStatus)
    && account.providerData?.some(provider => provider.providerId === "phone") === true
    && mobile(account.phoneNumber) !== null && mobile(account.phoneNumber) === mobile(contact);
}
function listingTargets(data) {
  if (!data || !["providers", "requests"].includes(data.kind) || !Array.isArray(data.ids)
      || data.ids.length < 1 || data.ids.length > 50
      || data.ids.some(id => typeof id !== "string" || !/^[A-Za-z0-9_-]{1,128}$/.test(id)))
    throw new Error("Invalid listing targets");
  return { kind: data.kind, ids: [...new Set(data.ids)] };
}
module.exports = { mobile, phoneMatches, listingTargets };
