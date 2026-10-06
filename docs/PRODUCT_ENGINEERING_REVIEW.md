# Mahallem Product Engineering Review

This document records high-value, non-cosmetic product and engineering findings so future changes can be evaluated against measurable marketplace outcomes.

## Implemented in this QA branch

| Area | Change | Expected value | Main risk checked |
| --- | --- | --- | --- |
| Build reliability | Fixed the escrow checkout callback compilation errors in `MainActivity.kt`. | Restores Android CI/CodeQL buildability. | Uses the existing Activity context and Compose coroutine scope; no permission or lifecycle bypass added. |
| Discovery performance | Marketplace search is trimmed, capped at 80 characters, debounced for 250 ms, and deduplicated. | Fewer repeated Room/client-side filter passes while typing; bounded input. | Small 250 ms response delay is intentional; max length prevents pathological input without hiding normal queries. |
| Onboarding | New Firebase user profiles omit nullable `photoUrl`/`phoneNumber` fields instead of writing invalid null values against rules. | Fewer first-login/profile bootstrap failures. | Does not weaken profile schema or verified-contact checks. |
| Public discovery | Added read-only Firestore rules for backend-generated `publicProviders` and `publicJobRequests`. | Production discovery can read the intended mirror collections. | Clients cannot create/update/delete mirror data; reads are bounded to 50 and blocked for accounts in purge states. |
| Customer trust/privacy | Separated customer `My Requests` from the public marketplace request feed using an owner-scoped Firestore query. | Prevents discovery traffic from appearing as a customer's own history. | Rule requires `ownerId == auth.uid`; unscoped queries still fail. |
| Cloud data hygiene | Cloud-mode provider/request/quote writes no longer duplicate the newly created records into the shared local Room cache. | Reduces cross-account stale data risk on shared devices and avoids divergent local/cloud state. | Local demo behavior remains unchanged; production source of truth stays server-side. |

## Next high-value backlog

### P0 — production correctness
1. **Real payment completion path**: PayTR checkout creation, webhook verification, payment state transitions, release/refund/dispute lifecycle, and idempotency must be verified in staging before any “Güvenli Havuz” copy is treated as a real-money guarantee.
2. **Customer-owned request source**: keep `jobRequests` owner-scoped for history; never reuse the public discovery collection for private dashboards.
3. **Server-side provider availability and moderation**: provider open/closed state, booked dates, listing reports, and content moderation need callable/server-authorized mutations in cloud mode. Local-only toggles must not imply production persistence.

### P1 — marketplace conversion and scale
1. **Server-side filtering + pagination** for provider/request discovery. The current client reads a bounded first page and filters locally, which can hide relevant results as inventory grows.
2. **Unread message state** with per-user read markers and server-maintained unread counts. The current cloud conversation mapper does not derive real unread counts.
3. **Account-scoped offline cache**: introduce authenticated-user scoping or cache invalidation on sign-out before retaining private cloud data locally.
4. **Notification preferences**: honor user consent/preferences server-side before sending FCM; add notification categories and deep-link handling.
5. **Search/indexing metrics**: track search-to-detail, detail-to-chat, chat-to-quote, quote acceptance, and request completion funnels using consented/aggregated analytics.

### P2 — trust, accessibility, localization
1. Add explicit verification provenance to badges (phone, MYK, safety-related checks) instead of trusting user-provided booleans.
2. Add Turkish-specific form validation and canonicalization for phone, TRY currency entry, district naming, and date/time inputs.
3. Audit TalkBack labels, touch targets, dynamic text scaling, contrast, and loading/error/empty states.
4. Add moderation reason codes, reporter rate limits, audit trails, and an admin review queue.

## Engineering guardrails

Every new feature should answer four questions before merge:
- **Security/privacy**: can a different authenticated user read or mutate this data? Does it leak address, phone, exact location, payment, or chat content?
- **Complexity**: does the change add a new source of truth, background process, or state machine that can drift?
- **Performance/cost**: does it multiply Firestore reads/listeners, image downloads, FCM sends, or client-side scans?
- **Failure behavior**: what happens offline, after token expiry, after duplicate taps, on retries, or after account deletion starts?

Prefer server-authoritative state for money, identity, permissions, moderation, and marketplace ownership. Prefer local caching for read-mostly public discovery only when cache invalidation and account boundaries are explicit.
