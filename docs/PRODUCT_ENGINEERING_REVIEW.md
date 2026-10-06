# Mahallem Product Engineering Review

This document records high-value, non-cosmetic product and engineering findings so future changes can be evaluated against measurable marketplace outcomes.

## Implemented in this QA branch

| Area | Change | Expected value | Main risk checked |
| --- | --- | --- | --- |
| Build reliability | Fixed the escrow checkout callback compilation errors in `MainActivity.kt`. | Restores Android CI/CodeQL buildability. | Uses the existing Activity context and Compose coroutine scope; no permission or lifecycle bypass added. |
| Discovery performance | Marketplace search is trimmed, capped at 80 characters, debounced for 250 ms, deduplicated, and separated from exact server-side discovery filters. | Fewer repeated client scans and fewer Firestore listener refreshes while typing. | Text search remains client-side over a bounded page; exact filters use backend queries. |
| Onboarding | New Firebase user profiles omit nullable `photoUrl`/`phoneNumber` fields instead of writing invalid null values against rules. | Fewer first-login/profile bootstrap failures. | Does not weaken profile schema or verified-contact checks. |
| Public discovery | Added read-only Firestore rules for backend-generated `publicProviders` and `publicJobRequests`. | Production discovery can read the intended mirror collections. | Clients cannot create/update/delete mirror data; reads are bounded to 50 and blocked for accounts in purge states. |
| Customer trust/privacy | Separated customer `My Requests` from the public marketplace request feed using an owner-scoped Firestore query. | Prevents discovery traffic from appearing as a customer's own history. | Rule requires `ownerId == auth.uid`; unscoped queries still fail. |
| Cloud data hygiene | Cloud-mode provider/request/quote writes no longer duplicate newly created records into the shared local Room cache. | Reduces cross-account stale data risk and state divergence. | Local demo behavior remains unchanged; production source of truth stays server-side. |
| Provider operations | Provider availability/open-for-offers and booked dates are now server-authoritative in cloud mode; public mirrors publish sanitized calendar state. | Prevents local-only availability claims and keeps discovery in sync. | Only provider owners can mutate state; updates are App Check + rate limited. |
| Messaging trust | Per-user conversation unread state and read markers are maintained server-side and merged into the cloud conversation list. | More reliable message notification and unread UX. | Read-state writes are callable-only; clients can read only their own state. |
| Notifications | Message notifications honor per-user message notification preference and suppress notifications already marked read. | Less unwanted notification noise. | Marketing preference remains separate; no marketing sender was enabled. |
| Payment safety | PayTR iFrame token generation, signed callback verification, idempotent payment creation, and authenticated payment-status retrieval are implemented server-side. | Moves card handling out of Mahallem and gives the app a verifiable payment state. | Merchant credentials stay in Secret Manager; marketplace seller transfer still requires PayTR Marketplace onboarding/configuration. |
| Moderation | Content reports are backend-created, rate limited, idempotent per target, and admin-readable only. | Makes abuse reporting durable and auditable. | Clients cannot write the reports collection directly. |


## Next high-value backlog

### P0 — production correctness
1. **Marketplace settlement**: standard PayTR checkout and signed payment callbacks are implemented, but seller payout still requires the PayTR Marketplace Solution approval/configuration and the documented next-day platform transfer flow.
2. **Staging verification**: run the full PayTR sandbox journey, webhook retry/idempotency tests, refund tests, and provider settlement tests before enabling live money.
3. **Customer-owned request source**: keep `jobRequests` owner-scoped for history; never reuse the public discovery collection for private dashboards.

### P1 — marketplace conversion and scale
1. **True pagination / infinite scroll** for provider/request discovery. Exact category/sector/district/urgency filters are now server-side, but each query is still bounded to 50.
2. **Account-scoped offline cache**: cloud-created private records are no longer duplicated into Room; keep cache ownership explicit for any future offline-first feature.
3. **Search/indexing metrics**: track search-to-detail, detail-to-chat, chat-to-quote, quote acceptance, completion and report-resolution funnels using consented/aggregated analytics.
4. **Notification deep links/categories**: add category-specific notification payloads and robust deep-link handling.

### P2 — trust, accessibility, localization
1. Add explicit verification provenance to badges (phone, MYK, safety-related checks) instead of trusting user-provided booleans.
2. Add Turkish-specific canonicalization for phone, TRY currency entry, district naming, and date/time inputs.
3. Audit TalkBack labels, touch targets, dynamic text scaling, contrast, loading/error/empty states.
4. Add moderation audit trails, reason codes, reviewer actions and retention rules.

## Engineering guardrails

Every new feature should answer four questions before merge:
- **Security/privacy**: can a different authenticated user read or mutate this data? Does it leak address, phone, exact location, payment, or chat content?
- **Complexity**: does the change add a new source of truth, background process, or state machine that can drift?
- **Performance/cost**: does it multiply Firestore reads/listeners, image downloads, FCM sends, or client-side scans?
- **Failure behavior**: what happens offline, after token expiry, after duplicate taps, on retries, or after account deletion starts?

Prefer server-authoritative state for money, identity, permissions, moderation, and marketplace ownership. Prefer local caching for read-mostly public discovery only when cache invalidation and account boundaries are explicit.
