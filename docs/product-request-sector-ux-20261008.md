# Product P1 — My Requests sector presentation (8 Oct 2026)

## User problem
MyRequestsScreen currently displays "Eğlence & Organizasyon" for every non-HOME_REPAIR request and renders event-only fields for cleaning, moving, tutoring and personal care.

## Scope
Branch: `agent/product-request-sector-summary-20261008`. This branch adds `RequestSectorLabel.kt` and `RequestSectorLabelTest.kt`, wires the policy into `MyRequestsScreen.kt`, and includes Compose UI regressions in `MyRequestsSectorScreenTest.kt`. The code is committed on an isolated product branch, not merged.

## Acceptance criteria
1. Each of the six persisted sector codes shows its matching `SectorType.titleTr` label.
2. Unknown, blank or legacy codes show `Diğer Hizmet`, never a fabricated entertainment label.
3. Only HOME_REPAIR shows renovation-specific fields; only EVENT_ENTERTAINMENT shows event-specific fields.
4. Other sectors show their category, when known, without misleading renovation/event details.
5. Existing request status, quote actions and emergency badge are unchanged.
6. Android unit + instrumented UI checks pass on the actual integration commit.

## Evidence
- Kotlin 1.9 standalone compile/run of locally mirrored policy + sector enum: 21/21 smoke assertions passed.
- Seven JUnit methods and four Compose Android UI methods committed, **not yet proven passing** in Android Gradle or emulator at time of this update.
- No emulator, Firebase or physical-device verification for this branch.
- A draft PR targets `qa/mahallem-test-suite`; check its latest CI/checks before integration.
- No merge or deployment; `main` unchanged.

## Next action
Run Gradle unit/lint/debug and instrumented Compose UI, fix any failures, and verify latest PR SHA in QA CI. Do not mark release-ready until rendered screen and release P0 gates are verified.
