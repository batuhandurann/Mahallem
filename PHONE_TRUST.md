# SMS phone verification and listing trust

The authenticated account header opens **Telefon doğrulaması**. The user consents
before SMS dispatch, enters a Turkish mobile number, receives Firebase's OTP and
links the credential to their existing email account. The UID remains unchanged.
Another account's phone is rejected; the app never signs in with the phone
credential or silently merges accounts. Changing an already linked phone and
account recovery are separate, not implemented workflows.

OTP IDs/codes remain in the UID-scoped ViewModel memory, survive Activity rotation
and are discarded on session disposal. A code is accepted by the UI for five
minutes; dispatch has a 60-second cooldown. Old callbacks are ignored on account
change. SDK calls can finish against the originally captured FirebaseUser after
cancellation; they cannot be treated as success for a different current UID.

`getListingTrust` runs in `europe-west3`, with production App Check enforcement.
It takes only `kind` (`providers`/`requests`) and 1–50 listing IDs. It checks the
current caller, rate limits reads, checks listing visibility, loads the current
owner from Admin Auth and compares the linked phone provider's phone with the
private owner-bound listing contact. Missing/mismatched contacts, disabled or
deleting users, deleted accounts, private listings and unavailable evidence do
not receive a badge. The result contains booleans only; phone/contact/profile
values are not returned. Client trust fields are never evidence.

The listing streams reset badges before each fetch, fail closed on network/error
and refresh every 60 seconds. This is periodic revocation, not immediate push
revocation. A listing/contact change can remain represented by the last successful
response until that refresh. Professional certificates and safety checks are
always false until a reviewed, authorized evidence workflow is implemented. SMS
verification itself does not prove identity, qualifications or safety.

## Verification

- `npm run test:backend`: bounded input/normalization/exact Auth-contact match.
- `npm run test:media`: actual Auth emulator send/code/link/wrong-code/unlink,
  stable UID, callable visibility/contacts/disablement/deletion/stale tokens.
- `./gradlew -PfirebaseEmulators=true :app:connectedDebugAndroidTest` with
  demo Auth/Firestore/Functions running: actual Android SMS callbacks, wrong/correct
  OTP, rotation, relogin, account-switch guards plus existing isolation tests.
- Android Quality CI installs Functions dependencies and starts all three
  emulators. Never use production phone numbers/OTP in these tests.

## Production inputs still required

Register the canonical Android package `com.batuhanduran.burada` with the real
Firebase project and protect its configuration/signing inputs. Enable the Phone
provider in Firebase Console, configure allowed SMS regions and billing/quotas,
register the signing SHA-256 and SHA-1 fingerprints for the intended distribution,
and verify real-device Play Integrity/reCAPTCHA. Keep test bypasses out of live
builds. Deploy the trusted backend, with the correct named `mahallem` database
and App Check configuration, using the protected production workflow/principal.
Then prove a real SMS link, stable UID, matching and mismatching listing badges,
logout/relogin, two-account private-data isolation and revocation on a real phone.

The protected production audit requires Phone enabled, a TR-only SMS allowlist
(`smsRegionConfig.allowlistOnly.allowedRegions: ["TR"]`) and no configured test
phone numbers/codes (`signIn.phoneNumber.testPhoneNumbers`). Set public protected
environment variables `FIREBASE_ANDROID_SIGNING_SHA1` and
`FIREBASE_ANDROID_SIGNING_SHA256` from the **same Play application-signing
certificate**, not its upload key. Both must be registered on the canonical app;
API key restrictions must include the intended SHA-1 and every allowed SHA-1
must be registered there. The post-deploy check includes active Node 22
`getListingTrust` with bounded instances and the named database. This read-only
audit does not deliver an SMS or prove callable App Check token rejection;
those evidence flags remain false until separate real-device verification.

Emulator tests do not prove SMS delivery, live provider configuration, attestation,
backend deployment or Play signing. See `PRODUCTION_EXECUTION.md` and
`FIREBASE_PRODUCTION_DEPLOY.md` for the existing protected release workflow.
