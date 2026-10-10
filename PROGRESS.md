# Progress — Yakıno

Updated: 2026-10-10 (Europe/Istanbul). Read `AGENTS.md` and git status before work;
fetch current remote state because other sessions may advance the repository.

## Current security and mobile flow audit (2026-10-10)

- Preserved main `4995bd0` including Yakıno branding, phone trust, job lifecycle,
  verified reviews, sector/empty-state fixes and chat ordering. Audit code is
  committed as `f13b5f36b6c03e9598b892e4fd948318be245842` (following `3972132`).
- Closed outsider conversation-ID reservation/impersonation, verifying both
  participant UIDs in Rules and existing-chat repository reads. Quotes require
  published provider/pending request and authoritative name/title/rating snapshots.
- UID-scoped in-memory chat/quote drafts survive rotation/navigation and failed
  sends; completion waits for remote acknowledgement. Stable chat UUIDs reconcile
  uncertain retries without extra writes/quota; matching server quotes reconcile
  duplicate/late acknowledgement without claiming altered/rejected quotes sent.
- Added accessible compact account controls, verification resend/check/cooldown,
  bounded profile save/rollback, eight-character registration guidance and Unicode
  single-line name checks. Disabled unavailable call/voice controls; chat handles
  keyboard insets and quote/account dialogs scroll. Private drafts clear on logout.
- Removed arbitrary `.env` BuildConfig injection via the generic Secrets plugin
  and unused SDKs. Downloaded the existing canonical Firebase Android config
  locally; removed the stale tracked config. Protected config/keystore rules remain.
- Local final Gradle on audit source `f13b5f3`, JDK21/Windows: debug APK, AndroidTest
  APK compilation, **115/115** JVM/Robolectric/Compose tests, **0 lint errors / 43
  dependency/resource warnings**, exit 0. No tests skipped. Earlier locale/semantic
  assertion failures were repaired; no lint/test suppression used.
- Auth/Firestore **50/50** (repeated after tooling pins), backend **20/20**, config
  fixtures **60/60**, real local Auth/Firestore/Storage/Functions integrations
  **11/11**, all passed. Node24.19.0 locally versus production Functions Node22.
  No prior-version result is substituted for final Android validation. See
  `docs/verification-results.json` and `docs/current-security-audit.md`.
- npm tooling audit fell **16 -> 3 affected packages**, representing one unpatched
  braces watcher advisory. Root production `--omit=dev` and Functions audits each
  found zero dependency advisories. This does not prove a vulnerability-free app.
- adb has no physical device. SMS REST trust fixtures are not real SMS delivery.
  Signed production APK/AAB, Play Integrity/enforced App Check, live Rules/Functions
  deployment, real SMS and FCM delivery remain unverified. Functions listing failed;
  successful app config retrieval does not prove deployment authority.
- Payment requires the chosen iyzico merchant credentials/contract and integration.
  Voice/calls, GPS/map/distance, nationwide coverage, booking authority and staffed
  support remain product work; see `docs/competitor-comparison.md`.
- Next concrete task: run exact-head required Android/Security CI on the audit PR,
  merge only after success, then provide protected signing/Cloud/merchant access and
  physical devices for the documented production audit. Never bypass protections
  or use substitute keys as production evidence.

## Report reconciliation and Yakıno branding (2026-10-10)

- Based on main `9eecf8db2eeaabb39f04e281fa389d7a3c4cfe71`.
  Main Android Quality `37993663156` and Security `37993663150` passed.
  The same source also passed Android `38019052398`: 9/9 jobs and 31 Android
  tests, zero failures/skips, with real Android SDK plus isolated Firebase emulators.
- PR #53 is merged. #54 and legacy #1 are closed without merge. The #55 trust
  regression is already in main with semantic scrolling and visible assertions.
  #48/#49/#51 are closed; their canonical hardening is in main.
- Ported remaining #42 sector summaries, #43 selected-tab empty state and #52
  conversation ordering onto current main. Preserved non-payment quote acceptance,
  separate payment information, job lifecycle, reviews and all UID/Rules guards.
  Sector UI uses targeted semantic scrolling; existing payment tests run together.
- Applied #68's Yakıno app label, Auth/home copy and private notification resource,
  plus accessible logo label and Gradle project display name. Installed application
  ID and backend database identifiers remain compatibility identifiers; they are
  not public branding and changing them requires registration/data migration.
- This integration's exact-source Android/Security results are pending; prior
  main success does not verify these edits. `git diff --check` passed locally.
  Signed production AAB, live staging/App Check and real physical-device proof
  remain unavailable until their protected access is verified. No substitute
  key, fixture configuration or unsigned artifact is production evidence.

## Verified customer reviews (2026-10-10)

- Integrated current main including PR #75's `manageJob` lifecycle and PR #74's
  report authority. Removed the review draft's separate completion callable/CTA:
  provider submission followed by customer confirmation is the only close path.
- Review eligibility verifies request, accepted quote/provider, private job state
  and the authoritative terminal CONFIRM_COMPLETION event (actor, role, version,
  prior status and server timestamp). Completed flags alone cannot unlock reviews.
- Added 1–5 rating and optional bounded comment within 30 days, unique per job,
  retry idempotency, transactional aggregates, App Check, current-account checks
  and quotas. Job detail shows review only to the customer and hides expired CTA.
- Public reviews omit UID/request ID/contact. Added provider review list/load-more,
  reporting and current-moderator-only queue/hide/audit. Reports never auto-hide
  negative feedback; hidden tombstones prevent reposting and atomically reduce
  rating totals. Moderator queue is callable-only, including revocation checks.
- Initial PR #76 source `5e82b77`: Android Quality `37957795533` passed debug/unit/
  lint, clean unsigned release and Android instrumentation. Backend/Rules passed,
  but one callable fixture failed: its CANCELLED/PENDING status was accidentally
  passed as the fixture name. Corrected that fixture and rebuilt tests around the
  real provider-submit/customer-confirm lifecycle. Initial run is not evidence
  for the subsequent integration. New Android SDK review read-back added.
- Current local backend 20/20 and configuration/device fixtures 60/60 passed.
  Current Auth/Firestore 46/46 and focused review HTTP integration 3/3 passed,
  exercising real Auth, private lifecycle/confirmation events and transactions.
  Provider summary remains before reviews, and unrated providers show "Yeni"
  instead of an apparent zero-star score; actual averages render one decimal.
  Exact-head Android/security CI results are recorded in the validation update.
  Native Functions emulator workers cannot bind Unix sockets locally (EPERM);
  a TCP harness exercises the actual onCall HTTP wrappers with Auth/Firestore
  emulators without replacing authorization or transaction code.
- Local Android compilation failed resolving Foojay before compilation; use exact
  head CI. No signed production build, real device or live deployment is claimed.
  See `VERIFIED_REVIEWS.md`; deploy all review callables/Rules/index together and
  enforce their presence with the protected production audit before release.


## Job completion and cancellation (2026-10-09)

- Based on canonical main `e287fe08b4044dbc37f9c2219ab350b984107694` (merged
  phone isolation PR #71). Reviewed Armut, Fiverr and Upwork official material;
  chosen local-service policy is documented in `JOB_LIFECYCLE.md`.
- Integrated newly merged main `8042ff209c3be84c44de74a8b99fe63c39a9cba9`
  (PR #74) without reverting its report authority or release/device checks.
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
  Updated production audit requires `manageJob`; merged configuration/device
  fixture suite 59/59 passed (not a live Cloud verification).
  Local Gradle cannot download its distribution (network unreachable), so this
  is not Android build evidence. Local emulator execution requires JDK 21;
  this environment has JDK 17 and no Android SDK. New Rules, real callable,
  Android SDK and Compose tests are committed for real GitHub CI execution.
- CI source `630f238a`: debug build/unit/lint, clean unsigned release, backend
  18/18, Auth/Rules 45/45, trusted integration 8/8 and both CodeQL languages /
  secret scan passed. Android ran 27 tests: 26 passed, one failed because the
  Functions SDK's IID context rejected `fake-emulator-key` syntax before sending
  the job callable. Emulator-only options now use an explicitly nonfunctional
  39-character SDK-compatible placeholder; production options are unchanged.
- Corrected an incomplete GitHub source-tree transfer after the emulator key
  fix: restored all 188 tracked files, verified against local Git tree
  `a0fd55486fe38fa41e879587ef912a2d6becc66b`, source commit `e98732b`.
  Fresh Android/security CI is required for the restored source; the earlier
  partial-tree commit is not build evidence.
- PR #75 records the current CI outcome; merge only after required checks pass.
  Production needs protected
  Rules + trusted-backend deployment and real-device App Check verification.
  Payments/refunds, job-specific push, staffed disputes and reviews are separate
  work; no automatic completion/cancellation timeout or financial claims added.

## Report authority and production/device compatibility (2026-10-09)

- Started from main `e287fe08b4044dbc37f9c2219ab350b984107694` (PR #71).
- Report creation now requires a canonical same-database content reference,
  existing published listing owner or private conversation membership and actual
  message sender. Forged/nonexistent/private outsider targets fail atomically
  without consuming the report allowance. Blocked participants can report history.
  USER paths bind the UID but cannot prove Firebase Auth existence; no new user
  sanction is introduced. Client/Rules rollout requirements: `REPORT_TARGETS.md`.
- The moderation queue returns `targetRefPath` strings/null for old reports,
  avoiding raw Admin SDK reference serialization. Added Rules, Kotlin, real
  Android SDK and callable integration regression coverage.
- Production audit now requires Phone enabled with TR-only SMS allowlist, no
  production test phone numbers, explicitly intended Play signing SHA-1/SHA-256,
  matching API restrictions and all six functions including `getListingTrust`.
  Unknown secret emulator/database overrides fail closed. New protected variables
  and observed-vs-unverified evidence are documented; this is not a live audit.
- USB runner installs Functions dependencies, starts Auth/Firestore/Functions,
  reverses port 5001 and refuses Android execution without the callable emulator.
  Regression fixtures cover startup, conflicts and cleanup; they are not phones.
- Local Auth/Firestore Rules: 44/44 passed, backend tests 7/7 plus syntax passed,
  Python configuration/device suite 58/58 passed. First local media run stopped
  before tests because the Firestore Eventarc registration request failed.
  Android build/emulator and callable tests must pass current branch CI before
  merge. The physical preflight observed no attached phone and returned NOT_RUN.
- Access still required: canonical production Firebase config, signing secrets,
  protected WIF/Cloud variables including intended Play certificate fingerprints,
  and a physical phone/authorized Test Lab. SMS delivery, callable App Check
  rejection, live deployment and actual FCM delivery remain unverified.

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
