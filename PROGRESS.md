# Progress — Burada / Mahallem

Updated: 2026-10-09 (Europe/Istanbul). Read `AGENTS.md` and git status before work;
fetch current remote state because other sessions may advance the repository.

## Job completion and cancellation (2026-10-09)

- Based on canonical main `e287fe08b4044dbc37f9c2219ab350b984107694` (merged
  phone isolation PR #71). Reviewed Armut, Fiverr and Upwork official material;
  chosen local-service policy is documented in `JOB_LIFECYCLE.md`.
- Added provider start/completion submission, customer confirmation/revision,
  owner cancellation before selection and mutual accepted-job cancellation
  with accept/decline/withdraw and restoration of the prior active state.
- `manageJob` derives roles from authoritative request/quote records, enforces
  App Check/current account revocation, UID quotas, exact versions, idempotent
  command retries and atomic request/private state/append-only event updates.
  Terminal jobs close discovery but preserve participant access. Private notes
  and reasons never enter public listings. Direct client state/history writes
  and payment-bearing lifecycle bypasses are denied.
- Integrated My Requests -> Job Detail and Provider Dashboard -> My Jobs,
  filters, role-specific controls, confirmation, reason/explanation validation,
  busy/error/retry states and private timeline. Removed misleading acceptance
  guarantee/receipt claims from the changed quote card.
- Local backend policy/security unit tests 18/18 passed and syntax checks passed.
  Local Gradle cannot download its distribution (network unreachable), so this
  is not Android build evidence. Local emulator execution requires JDK 21;
  this environment has JDK 17 and no Android SDK. New Rules, real callable,
  Android SDK and Compose tests are committed for real GitHub CI execution.
- Next: verify CI on this branch before merging; production needs protected
  Rules + trusted-backend deployment and real-device App Check verification.
  Payments/refunds, job-specific push, staffed disputes and reviews are separate
  work; no automatic completion/cancellation timeout or financial claims added.

## Phone verification and authoritative listing trust (2026-10-09)

- Based on canonical main `f1db9637f52f80947a5a3c20ea9b3d890860fcdd`.
- Added UID-scoped Android Phone Auth SMS linking (no account sign-in/switch),
  number/code validation, resend cooldown, callback generation guards, failure
  handling, retry and in-memory-only OTP state across rotation.
- Added App Check enforced `getListingTrust`: reads current Admin Auth, private
  contact and account deletion status; checks exact normalized Turkish mobile
  number and linked phone provider, listing visibility/ownership and disabled
  accounts. Responses contain booleans only. Client truth claims are ignored;
  certificate/safety badges remain false without a reviewed evidence workflow.
- Feeds, own listings and details use the server response, reset trust on error,
  offline/account change and refresh every 60 seconds. This bounds evidence age;
  immediate push revocation is not implemented. Missing/unavailable deployment
  means no verified badge, not an invented success.
- New tests: Android real SMS callbacks, wrong OTP, correct link, UID preservation,
  rotation and old-session isolation; JS actual Auth emulator link/unlink, private
  contact mismatch, hidden listings, disabled/deleting accounts and stale tokens.
  Android CI now runs Functions alongside Auth/Firestore for instrumentation.
- Local backend tests: 7/7 passed; backend syntax and modified JS syntax passed.
  Existing Python configuration suite: 45/45 passed (fixture verification only).
  Local Gradle stopped before compilation because services.gradle.org was
  unreachable. Local Firebase tests stopped before startup: only JDK 17 is
  installed, CLI requires JDK 21. Android SDK/ADB/emulator are absent locally.
  These failures are NOT successful device/Rules/SMS execution evidence.
- First branch CI `37949884643`, source `e30b8ead`: debug/unit/lint and clean
  unsigned release passed. Existing Rules passed; new Node phone test revealed
  the Node SDK PhoneAuthProvider stub. Test now uses documented REST linking.
  Android test exposed a logout/recomposition race constructing a phone model
  from a null currentUser; the model now receives the immutable session UID and
  fails closed until that UID is current, avoiding this constructor crash.
- Second CI `37950712712`, source `9827ab58`: Rules 37/37, trusted integration
  3/3 including actual SMS/trust/revocation, backend 7/7, debug/unit/lint and
  unsigned release passed. Android exposed a second account-switch race: an old
  read stream started after logout and threw from requireAccount. Read streams
  now finish empty when stale; write checks remain strict. Marketplace models
  also receive immutable session UIDs rather than constructor-time currentUser.
  The Android regression now directly asserts stale read streams end empty.
- Actual local Auth-only emulator smoke passed wrong OTP, valid link, preserved
  UID, unlink and email re-login. This does not cover Android or Firestore trust.
- Next: verify this branch's full CI including the added tests. Production Phone
  provider, SHA-256/SHA-1 registration, SMS region policy/billing, App Check and
  callable deployment require protected production access and real-device proof.

## Direct content write abuse follow-up (2026-10-08)

- Started from current main `40e095f6c3f66592d780614f8870608548b4cd54`.
  PR #38 merged as `85e86771394334372f91d8a3e26bcc50e8c960b8`; its main Android
  and Security runs `37780454406` / `37780454359` passed. PR #45 also merged;
  current main Android / Security runs `37818019646` / `37818019716` passed.
- Added server-enforced one-hour creation allowances per UID: combined provider
  and request listings 10, quotes 60, new conversations 30, messages across all
  conversations 240 and reports 10. Counter and content commit in one transaction;
  a document reference binds one increment to one fresh target. Server timestamps
  prevent early resets, and counter deletion is denied. Do not enable counter TTL.
- Android transactions preserve listing/contact and message/preview atomicity,
  account guards and urgent schedule validation. Existing conversation reads do
  not consume a creation allowance. Quota exhaustion has a Turkish user message.
- Added 13 adversarial Rules cases, updated real-UID named-database integration,
  three Android unit tests and one actual Android SDK instrumentation regression.
  Local `npm run test:rules` passed 37/37 using demo Auth/Firestore emulators,
  including the real-UID named `mahallem` integration; zero failures or skips.
  Validate the latest commit's CI before claiming Android build/device success.
  Local Gradle could not resolve the configured Foojay artifact and stopped before
  compilation. The physical-device check still observes no attached phone.
- Scope and release coordination are documented in `FIRESTORE_WRITE_BUDGETS.md`.
  Other authorized metadata/status writes and global read traffic are not covered
  by these content creation allowances. This is not live deployment evidence.

## Urgent listing follow-up (2026-10-08)

- Continued from PR #37: its legacy QA run #719 passed unit/lint, emulator,
  backend/Rules and debug + unsigned release APK/AAB. That branch predates the
  current Firebase-only architecture and must not be merged into canonical main.
- Based the new `fix/canonical-urgent-schedule-20261008` branch on main `85e8677`.
  Main already removed Room, uses UID-bound repositories and clears session
  ViewModels on logout/account switch; those working guards are preserved.
- Added explicit required request title, strict calendar/time checks and actual
  submission-time YYYY-MM-DD/HH:mm for urgent requests. Validation also runs in
  the repository and Firestore Rules. Calendar leap-year rules reject impossible
  dates; the existing minimum two-character title requirement is preserved.
- Added Kotlin and Compose regressions, direct Firestore invalid-input tests and
  actual Android SDK urgent-request server read-back in the existing account
  isolation integration test.
- [PR #45](https://github.com/batuhandurann/Mahallem/pull/45), application source
  `df8417a23f52815e1dd0cbac1f54f6c2aede3977`, passed Android Quality
  [#136](https://github.com/batuhandurann/Mahallem/actions/runs/37816260461)
  and Security [#594](https://github.com/batuhandurann/Mahallem/actions/runs/37816260502).
  Verified debug APK, unit/lint, clean unsigned release APK/AAB, 14 Android
  instrumentation tests (zero failures/skips), account isolation and actual
  urgent-request server read-back. ADB foreground/rotation/process-death/cold
  relaunch smoke passed. Backend/Auth/Rules/media and all required status aliases
  passed; both CodeQL languages and secret scan passed. Artifacts were uploaded.
- Local `npm run test:rules`: 24/24 passed using demo-mahallem Auth/Firestore
  emulators; `git diff --check` passed. PR #37 was closed as superseded by #45.
  This evidence is for the stated application source, before this record-only
  documentation update; do not reuse it for later application changes.
- Real phone and production Firebase/config/attestation remain unavailable.
  Next: provide protected production inputs and a real phone/Test Lab to verify
  the signed build and live account/notification/attestation flows. Emulator
  success does not establish production deployment or physical-device evidence.

## Completed and verified

- PR #10 merged into `main`, merge commit `ae70278c4424fb45a1fad4b3bc5e7a90bbca7cce`.
- Both main workflows passed: Android Quality run `37777012961`, Security run
  `37777012995`. Actual debug APK, clean unsigned release APK/AAB, unit tests,
  lint, 12 Android instrumentation tests and lifecycle smoke passed. Auth/Rules
  23 tests, private photo/moderation 2 tests and backend 4 tests passed.
- CodeQL Java/Kotlin + JavaScript and secret scan passed. Main protection has
  eight required checks and no bypass actors. Required aliases depend on the
  actual build/emulator/backend jobs, so they cannot pass on skipped failures.
- Firestore/UID integration, account isolation, public published discovery,
  private chat/photo/report/block flows, Burada package identity and five-area
  neighborhood pilot are in main. This is not nationwide geography or payments.
- Production follow-up: signed APK/AAB signature validation, configurable upload
  alias, main-only protected signing, temporary-secret cleanup; authorized live
  Firebase audit/deploy; USB physical Auth/Rules/lifecycle runner. The combined
  Python suite passed 32 tests, including real fixture JAR signing checks and
  rejection of API restrictions that omit FCM registration/Installations.
  Fixtures do not prove production signing or live Firebase configuration.
- PR #38 validates this production follow-up. A warm Gradle cache caused CodeQL
  to extract no Java/Kotlin sources; its build command now forces recompilation
  with build/configuration caches disabled. Verify the current PR checks rather
  than treating an older workflow's success as evidence for the latest commit.
- Physical-device preflight observed zero attached phones and returned `NOT_RUN`.
  Local Firebase preflight rejected the old Android registration before Cloud
  access. No credentials or deployment were invented.

## Still blocked on real access

- Register/download `com.batuhanduran.burada` Firebase Android configuration.
- Protected production upload keystore/passwords and Firebase configuration for
  the signed build; Cloud WIF/deploy principal and production variables for audit
  and deployment. No such credentials are available in this execution environment.
- Real phone/authorized Test Lab access. Verify signed Play internal-testing
  attestation, Console enforcement/API restrictions and genuine FCM delivery,
  including blocked users and account switching. Prepared workflows are not
  successful production executions.
- Abuse controls for other write paths and global reads, production monitoring,
  nationwide data, voice and payments remain outside the completed integrations.

## Next concrete task

Preserve normal main protection and verify the current content quota follow-up's
actual checks/merge state. Supply the protected inputs described in
`PRODUCTION_EXECUTION.md` / `FIREBASE_PRODUCTION_DEPLOY.md`, run the signed build,
phone tests and authorized live audit/deployment, and record their real result
IDs here. Do not repeat existing successful integration work.
