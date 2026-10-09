# Canonical Android PR guard

This repository has two divergent Android implementations. The production canonical implementation is `com.batuhanduran.burada` on `main`. The historical `qa/mahallem-test-suite` branch uses `com.example` and must never be merged wholesale into `main`.

## Review gate

Before approving a PR targeting `main`:

1. Confirm the PR head was based on the current canonical `main` and compare the full diff.
2. Confirm `app/build.gradle.kts` retains namespace and applicationId `com.batuhanduran.burada`.
3. Confirm the manifest Application/Activity entry points remain canonical and no duplicate `com.example` app stack is introduced.
4. Check Firestore/Storage rules, Firebase database identity and migrations against canonical code; never cherry-pick rules blindly.
5. Require current-head Android build, unit/lint/instrumentation, backend/rules, CodeQL and secret checks; an older branch's green CI does not transfer.
6. Require human review; do not auto-merge. Signed production AAB and physical-device validation are separate release gates.

Legacy QA work must be ported as isolated canonical changes with regression tests and independent main-targeted draft PRs. PR #1 was closed without merge on 2026-10-09. Track architectural blockers in issues #11 and #26.
