# Security

## Reporting a vulnerability

Do not open a public issue for a suspected security vulnerability. Report it privately to the repository owner with reproduction steps, affected component, impact and any safe proof-of-concept.

## Security baseline
- No production SMS/payment secrets in the Android app or Git history.
- Firebase Auth is required outside LOCAL.
- App Check / Play Integrity is enabled outside LOCAL.
- Firestore and Storage rules are least-privilege and field-scoped.
- Payment state changes are backend-controlled.
- Payment callbacks require signature verification and idempotent processing.
- Device tokens are owner-scoped.
- CI runs unit/UI/backend tests plus static/security checks.