# Product P1 — My Requests sector presentation (8 Oct 2026)

## User problem
MyRequestsScreen currently displays "Eğlence & Organizasyon" for every non-HOME_REPAIR request and renders event-only fields for cleaning, moving, tutoring and personal care.

## Scope
Branch: `agent/product-request-sector-summary-20261008`. This branch adds `RequestSectorLabel.kt` and `RequestSectorLabelTest.kt`. It does **not** yet wire the policy into `MyRequestsScreen.kt`: two connector updates were blocked.

## Acceptance criteria
1. Each of the six persisted sector codes shows its matching `SectorType.titleTr` label.
2. Unknown, blank or legacy codes show `Diğer Hizmet`, never a fabricated entertainment label.
3. Only HOME_REPAIR shows renovation-specific fields; only EVENT_ENTERTAINMENT shows event-specific fields.
4. Other sectors show their category, when known, without misleading renovation/event details.
5. Existing request status, quote actions and emergency badge are unchanged.
6. Android unit + instrumented UI checks pass on the actual integration commit.

## Evidence
- Kotlin 1.9 standalone compile/run of locally mirrored policy + sector enum: 21/21 smoke assertions passed.
- Seven JUnit test methods committed but **not executed** in Android Gradle.
- No emulator, Firebase or physical-device verification for this branch.
- No PR opened: PR creation blocked by connector safety checks.
- No merge or deployment; `main` unchanged.

## Next action
Wire `requestSectorLabel(request.sector)` into the card title and use `requestDetailsKind(request.sector)` for the details branch. Re-run Gradle unit/lint/debug, Android UI, and QA CI. Do not mark fixed until the rendered screen is verified.
