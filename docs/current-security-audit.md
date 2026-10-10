# Current security audit follow-up

Date: 2026-10-10 (Europe/Istanbul). Working branch:
`audit/current-main-20261009`, integrating `origin/main` source `4995bd0`.
Local integrated code revision: `f13b5f3`; visible brand Yakıno, canonical package
`com.batuhanduran.burada`, compatibility database `mahallem`.

## Conversation identity

New conversation writes bind the document ID to both participant UIDs using the
same length-prefixed encoding as the Android repository. An authenticated Eve
cannot reserve Alice and Bob's deterministic conversation document by substituting
Eve for either participant. Both membership and ID binding are checked in Rules;
the repository verifies the actual participants of an existing conversation.

Rules regression cases exercise three outsider payloads against Alice and Bob's
ID, arbitrary IDs, normal participant creation, reversed participant arrays and
UIDs containing colons. The named `mahallem` integration repeats the reservation
attack using real Auth emulator UIDs before the legitimate client creates its chat.
Every attack uses a correctly formed atomic quota transaction and asserts that
rejection leaves the attacker's allowance untouched.

The Android repository sorts participant UIDs for its deterministic ID. Rules
accept either encoded participant order; they bind identity but do not establish
global uniqueness of a pair across both possible document IDs. Legacy admin-seeded
conversation IDs remain in read, message and report fixtures to exercise existing
history. Client creation fixtures use encoded IDs, including block and quota tests.

## Quote authority and visibility

New quotes require a published provider and a published, pending request. Provider
name, title and rating must equal the provider document's authoritative values.
The client repository derives these values from that document inside its
transaction rather than trusting a caller's display snapshot.

Regression cases reject forged names, forged titles, inflated ratings and stale
zero ratings for an actually reviewed provider. A legitimate 4.25 rating succeeds.
Hidden and closed provider/request documents reject fresh quotes while restoring
published visibility permits a valid quote. The named database integration also
tests three display-field forgeries with real Auth UIDs before successful quoting.
Failed writes neither create the forged quote nor consume its quota.

## Validation evidence

- `node --check test/firebase/rules.test.mjs`: passed locally.
- `node --check test/firebase/auth.test.mjs`: passed locally.
- `git diff --check -- test/firebase/rules.test.mjs test/firebase/auth.test.mjs`:
  passed locally.
- Auth/Firestore emulator suite: **50/50 passed**, zero failures or skips, repeated
  after dependency changes (Node 24.19.0, JDK 21.0.12.1+1, `demo-mahallem`). Includes
  actual Auth emulator UIDs and named `mahallem` transactions. Latest branding
  merge changed no Rules, fixtures, server dependencies or Functions code.
- Backend tests: **20/20 passed**; all six server syntax checks passed.
- Python configuration fixtures: **60/60 passed**, Python 3.12.14 in UTF-8 mode
  with the JDK on PATH. These are fixtures, not live enforcement checks.
- Final Android on code `f13b5f3`: `assembleDebug`, `testDebugUnitTest`, `lintDebug`,
  `assembleDebugAndroidTest` **passed**, Gradle exit 0 in 3m55s. **115/115** tests,
  zero failures/errors/skips. Lint **0 errors / 43 warnings** (dependency versions,
  unused resources and configuration recommendations). No check suppressed.
  JDK21/Windows; debug and test APKs built. No adb device: instrumentation was
  compiled, not executed locally. Details/checksums: `verification-results.json`.
- Real local Auth/Firestore/Storage/Functions integration: **11/11 passed**, no
  failures/skips, 49.1s. Includes SMS REST UID-preserving link/unlink and trust revoke,
  private sanitized photos, block/access checks, lifecycle races, review totals,
  expiry and moderator revocation. Emulator fixtures do not deliver real SMS or
  validate production Play Integrity. Node24 locally, Functions production target22.

## Draft retention and reliable retries

Chat composers and provider quote drafts live in the UID-scoped ViewModel's
memory. Rotation or leaving and returning to a screen retains the same pending
state; a late callback updates that state instead of enabling a second send.
Failed sends preserve editable drafts. Quote forms stay open until acknowledgement,
disable duplicate actions, retain failure details and scroll on small screens.
Successful sends clear only the acknowledged draft. Sign-out clears private
drafts; they are not persisted to disk or Activity saved-state bundles.

Firebase Tasks can finish after the client's 30-second timeout. Chat retry attempts
reuse a UUID until acknowledgement. The transaction reads that message first:
matching sender and immutable payload return success without new writes, last-message
changes or quota charges; a different payload with the same ID fails. Acknowledged
intentional identical messages get a new UUID. Unit and Android SDK regressions
cover these cases; actual Android SDK execution awaits the final CI result.
Quote retries also reconcile the actual immutable server record after write
failure/timeout. Only matching UID, request, provider and price/arrival/notes with
pending/accepted status acknowledge that attempt; altered/rejected/withdrawn
records do not. Failed writes roll back their quota transaction. Rules were not
relaxed to permit missing private quote reads.

## Authentication and mobile account controls

Added email verification status, resend/check actions, per-UID 30-second local
cooldown and bounded requests. Account actions keep the signed-in marketplace
mounted and disable logout during the request. Firebase remains the server quota
authority. Registration asks for at least eight characters, matching the production
audit's minimum policy; legacy sign-in passwords are not rejected locally.
Profile creation and rollback waits are bounded. Names reject Unicode line and
paragraph separators. Account controls moved into a scrollable dialog accessible
from a compact header. Unsupported voice and call controls are disabled, and the
chat composer applies keyboard insets.

## Dependency and configuration hardening

Removed the generic Secrets Gradle plugin and unused AI/network SDKs, closing the
possibility of arbitrary `.env` entries being compiled into BuildConfig. Moshi
remains because the current Firebase document serialization uses it. Live Google
Services configuration is ignored, and signing/service-account files are excluded.
The real canonical Firebase app was listed and its valid config downloaded locally;
no downloaded config or private key is included in the changes.

Root npm audit decreased from 16 vulnerable package entries to 3 after pinned
`grpc-js`, `basic-ftp`, scoped `uuid` and scoped OpenTelemetry Core updates. The
three remaining entries are one unpatched advisory propagated through
`firebase-tools -> chokidar -> braces`:
[GHSA-vfj7-8cjw-p6xm](https://github.com/advisories/GHSA-vfj7-8cjw-p6xm).
It belongs to local emulator/CLI filesystem watchers; no app request path was
found. Forcing an incompatible watcher version was avoided. Root production audit
with `--omit=dev` and the Functions dependency audit each reported zero findings;
that is dependency evidence, not proof of a vulnerability-free application.
The revised tooling passed npm dependency resolution, valid local FTP parsing,
gaxios multipart HTTP and PubSub/OpenTelemetry propagation compatibility checks.

## Remaining release requirements

- No new production Rules/Functions deployment or enforcement claim. The read-only
  Functions listing failed; CLI login and app listing do not prove deploy access.
- Protected production signing key/passwords and Cloud WIF/production variables
  still required. Existing upload/Play certificates must be verified; no substitute
  signing key is generated as production evidence.
- No physical phone was attached to adb. Real SMS, Play Integrity rejection and
  foreground/background FCM require signed installed builds and actual devices.
- Payment collection/refunds remain unavailable without the selected iyzico
  merchant credentials, supported marketplace contract and verified server flow.
- Voice/calls, GPS/map/distance, nationwide geography and staffed support are
  product work, not implicitly completed by these security fixes. See the current
  comparison document for implemented versus missing flows.

These checks do not establish production Rules deployment, a signed Android
build, physical-device behavior or live Firebase enforcement. Final integrated
build and emulator results belong in `PROGRESS.md` with their actual environment
and source revision.
