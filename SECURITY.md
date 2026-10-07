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

## Current red-team hardening

- Backend callable actions verify that the Firebase Auth user still exists, is not disabled, and has a verified email or phone number. Account deletion states remain blocked.
- Account purge removes reverse device-token ownership records, UID-scoped rate-limit records, and user/job/chat media represented by upload grants.
- Closed or anonymized job requests are removed from the public marketplace mirror.
- Public marketplace mirrors are read-only to authenticated, active users; clients cannot write them.
- Security-sensitive GitHub Actions are pinned to reviewed commit SHAs, and the Firebase CLI used by CI is pinned to an exact version.

## Residual risk / external configuration

- PayTR Marketplace checkout is intentionally fail-closed until merchant-approved marketplace configuration and sandbox webhook verification are completed. Do not treat a local/demo escrow state as a real payment.
- Firebase App Check / Play Integrity, Authentication providers, Storage bucket configuration, PayTR secrets, and production domains remain deployment-time controls and must be configured and tested in the target Firebase project.
- Storage upload validation is asynchronous: invalid objects can exist briefly before the finalization trigger deletes them, although client reads require a validated grant. Upload quotas limit abuse but do not eliminate storage-processing cost.
- Existing environments may contain legacy orphaned device-token ownership records or media created before this purge hardening; perform a one-time maintenance cleanup before production if such data exists.



## Marketplace trust controls added

- User blocks are backend-controlled and enforced in both directions for new conversations, messages, quotes and push notifications.
- Job completion requires confirmation from both the customer and accepted provider before the request becomes COMPLETED.
- Transaction-verified reviews can be created only by the request owner after a completed accepted job; clients cannot write review documents directly.
- Disputes freeze the job into DISPUTED and require an authenticated admin resolution path to resume, complete or cancel it.
- Public discovery publishes only open PENDING requests and approximate coordinates; accepted, disputed, completed and cancelled jobs are removed from the public mirror.
- Favorites are account-scoped backend records rather than shared/local-only production state.


- Client-side direct quote/request mutation is denied by Firestore Rules; state changes must pass through authenticated/App Check Cloud Functions.
- Public review documents contain only display-safe provider/rating/comment/verification fields. Internal transaction linkage is kept in admin-only review audit records.
