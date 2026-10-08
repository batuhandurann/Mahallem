# Progress — Burada / Mahallem

Updated: 2026-10-08 (Europe/Istanbul). Read `AGENTS.md` and git status before work;
fetch current remote state because other sessions may advance the repository.

## Logout/calendar follow-up (2026-10-08)

- PR #45 merged normally into main `40e095f6c3f66592d780614f8870608548b4cd54`;
  its final Android Quality #138 and Security #596 required checks passed.
- Follow-up on that main: logout's push cleanup exception previously escaped the
  UI coroutine despite its `finally` signing out. Added bounded best-effort
  cleanup; cancellation still propagates and the original UID guard protects a
  replacement account. Session data continues to clear before remote cleanup.
- Calendar validation now uses UTC for date-only parsing. Device zones with
  skipped days/midnights must not reject otherwise valid Gregorian dates that
  Firestore accepts. Submission-time date/time still use the device's local zone.
- Added unit regressions for successful/failed/hanging/cancelled cleanup,
  replacement-account protection, and Pacific/Apia + America/Sao_Paulo dates.
- Local Android tests are NOT verified: wrapper download is network-blocked and
  offline Gradle lacks the foojay resolver plugin. Required GitHub CI must prove
  this new application source before merge; previous test results do not apply.
- Physical-device and production Firebase checks remain unavailable. Next:
  complete the follow-up PR checks normally, then use protected real inputs for
  the existing production/physical-device plan.

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
- Full write-path abuse controls, production load/monitoring, nationwide data,
  voice and payments remain outside the completed feature integrations.

## Next concrete task

Preserve normal main protection and check PR #38's actual merge/check state.
Supply the protected inputs described in
`PRODUCTION_EXECUTION.md` / `FIREBASE_PRODUCTION_DEPLOY.md`, run the signed build,
phone tests and authorized live audit/deployment, and record their real result
IDs here. Do not repeat existing successful integration work.
