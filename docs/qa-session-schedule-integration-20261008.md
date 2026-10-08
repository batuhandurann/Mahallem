# QA-CI P0 integration verification — 2026-10-08

Integration PR: #44. Coordination: Issue #11. Base: `qa/mahallem-test-suite`.

## Test matrix (do not infer PASS from a sibling PR)

- [ ] JDK 21 clean debug APK + Android unit tests + lint (run `gradle clean testDebugUnitTest lintDebug assembleDebug --no-daemon --stacktrace -PallowMissingGoogleServices=true`).
- [ ] Android emulator instrumentation (CreateJobRequestScreen initial urgent, toggle planned, blank title, valid future scheduled request; session swap).
- [ ] Unsigned release APK / AAB with artifact evidence and SHA.
- [ ] Firebase Auth, Firestore, Storage rules negative tests and Cloud Functions backend tests.
- [ ] Security CodeQL Java/Kotlin, JavaScript and secret scan.
- [ ] Two UIDs: logout clear all local tables, relaunch + same UID and account switch isolation, offline token-unregister timeout, login serialized after logout and data-clean failure.
- [ ] Calendar boundaries and leap day, invalid day/time, date-and-time defaults from the same instant, emergency schedule translation at submit time.
- [ ] Real Android physical-device smoke tests (not possible from this build environment).
- [ ] Approved Firebase staging integration, signed production upload cert + appId + AAB validation and Play Console owner checks (not performed).

## Implementation choices

- The integration uses PR #39's serialized `SessionOperations` and `AccountViewModelStore` rather than also introducing PR #37's alternate session store.
- PR #37's required title field is retained (blank titles cannot be silently auto-generated).
- Existing QA's atomic `RequestDateTimeDefaults.at()` and calendar/boundary tests are retained.
- Normal logout retains a shared Room instance but clears all rows on IO; a distinct context-aware destructive cleanup path explicitly deletes the persisted DB. The existing singleton tests are adapted to those separate contracts.
- No source code was pushed into the PR #37/#39 branches; they remain subject to review.
- Main has not been changed, no secrets/deploy/merge or production signing.

Do not approve integration until CI and security jobs for the final HEAD are green.
