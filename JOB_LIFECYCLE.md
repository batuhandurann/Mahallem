# Job completion and cancellation

Scope: service requests with an accepted quote, and cancellation before quote
selection. Existing request/quote acceptance and UID guards are preserved.
This does not implement payments, refunds, guarantees, reviews or a staffed
dispute service.

## Product policy

| Current state | Actor / action | Result |
|---|---|---|
| PENDING | Customer cancels with reason + explanation | CANCELLED |
| ACCEPTED | Selected provider starts | IN_PROGRESS |
| ACCEPTED / IN_PROGRESS | Selected provider submits completion with explanation | AWAITING_CONFIRMATION |
| AWAITING_CONFIRMATION | Customer confirms | COMPLETED |
| AWAITING_CONFIRMATION | Customer requests revision with explanation | IN_PROGRESS |
| ACCEPTED / IN_PROGRESS / AWAITING_CONFIRMATION | Either party requests cancellation with reason + explanation | CANCELLATION_REQUESTED |
| CANCELLATION_REQUESTED | Other party accepts | CANCELLED |
| CANCELLATION_REQUESTED | Other party declines with explanation | Previous active state |
| CANCELLATION_REQUESTED | Initiator withdraws | Previous active state |
| COMPLETED / CANCELLED | Any mutation | Denied |

Pending cancellation does not imply completed cancellation. There is no silent
auto-accept or auto-complete timeout. Real-world service delivery needs explicit
confirmation; payment-sensitive cancellation and support adjudication need a
separate production policy before introducing fees, refunds or timers.

Completion can be submitted without explicitly starting (e.g. a short repair),
but always needs the customer's confirmation. Accepting a quote remains a
historical decision even if the job is later cancelled. Other quotes remain in
history; request status disables further acceptance. Users create a new request
instead of reopening a terminal job.

## Authoritative state and privacy

- `manageJob` is an authenticated, App Check enforced callable in `europe-west3`.
  It checks current Admin Auth disablement and deletion-state revocation, and
  limits attempts to 60 per UID per hour.
- Identity and roles derive from the request's owner and selected provider,
  checked against the accepted quote. The caller supplies neither role nor UID.
- `jobs/{requestId}` stores private state, version, participants and latest
  explanation. It is initialized lazily on the first lifecycle action so
  existing accepted jobs work without a destructive migration.
- `jobs/{requestId}/events/{actionId}` is append-only via Admin SDK. Each event
  includes actor, role, action, prior/new state, reason, note, version, a command
  digest and a server timestamp. All client state/history writes are denied.
- Request status, private job state and event commit in one transaction. Version
  checks serialize competing actions; action ID + actor + command digest make
  same-command retries idempotent, including after later transitions.
- Terminal requests become `visibility=closed`. Only the owner and selected
  provider can read them. Discovery shows open pending requests only. Notes and
  cancellation reasons never appear on public request records.
- Accounts blocked in chat can still resolve their existing jobs; blocking is
  not a unilateral job cancellation or a reason to trap the other party.
- This path fails closed if escrow funds/amounts are recorded or quote funding
  is acknowledged. It does not transfer money or invent a refund.

## Android experience

- My Requests: each request opens its status/history, including pre-selection
  cancellation. Closed request controls cannot accept more offers.
- Provider dashboard: Jobs opens assigned requests with active/completed/
  cancelled filters; records remain available after discovery closure.
- Job detail: role-specific actions, explicit confirmation, required reason
  and 10–1000 character explanations for cancellation, completion, revision
  and cancellation refusal; last 50 history events, newest first.
- No optimistic terminal success. Pending writes disable repeats. A failed or
  timed-out command can be retried with the same action ID; the server returns
  the prior result instead of creating another event. Stale-version actions
  fail and require inspecting the updated state.
- Existing UID-scoped snapshot listeners clear stale/cached data. Event rows
  carry their request ID so previous detail state is not shown on another job.

## References considered

- Fiverr Resolution Center: mutual request/accept/decline/withdraw model:
  https://help.fiverr.com/hc/en-us/articles/27274045277713-Using-the-Resolution-Center
- Armut distinguishes winning the work and requesting feedback after service:
  https://info.armut.com/ilk-isini-kazanmaya-hazir-misin
- Upwork contract closure separates delivery, closure, funds and feedback:
  https://support.upwork.com/hc/en-us/articles/17974934396947--End-a-fixed-price-contract

These are design references, not copied contractual promises. Provider/client
confirmation, private history and mutual accepted-job cancellation are Yakıno's
chosen policy. Fiverr's digital-delivery deadlines and automatic resolutions
are not copied into local services.

## Verification and deployment

- Unit policy tests cover role/state matrix, revision, both cancellation actors,
  prior-state restoration, terminal denial, malformed input and stale versions.
- Real Auth/Firestore/Functions integration covers atomic history, private reads,
  forbidden direct writes, duplicate concurrency, competing transitions, open
  cancellation vs real Rules-governed quote acceptance, revocation and payments.
- Android SDK integration covers assigned work, replay, delivery/revision/
  redelivery/confirmation and participant access after closure. Compose tests
  cover confirmation, reason/note validation and busy/requester controls.
- Use `npm run test:backend`, `npm run test:rules`, `npm run test:media` and the
  existing Android Quality workflow with `demo-mahallem` only.
- Release must deploy both updated Firestore rules and the `trusted-backend`
  functions codebase (including `manageJob`), then verify on real devices with
  production App Check. Source/CI success is not production deployment evidence.
- Job-specific push notifications and support dispute adjudication are not
  included; parties see updates in realtime while viewing their job records.
