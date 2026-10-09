# Yakıno: verified customer reviews

## Rules and product choices

A quote acceptance is an agreement, not proof that work is finished. The existing
`manageJob` lifecycle remains the only completion authority: provider submits,
customer confirms. Reviews verify the private completed job and its terminal
CONFIRM_COMPLETION event (customer identity, role, prior state, matching version
and server timestamp), plus the accepted quote/provider and request. Legacy
completed jobs with this authoritative event remain eligible; a standalone
COMPLETED flag cannot unlock reviews. No extra completion callable or bypass is
introduced, and no escrow payment is released by a review.

Only that customer can call `submitJobReview` after server-confirmed COMPLETED,
within 30 days. Pending, accepted, cancelled, disputed, unrelated jobs, disabled
accounts and pending account deletion fail closed. One 1–5 integer score per job;
optional plain-text comment of up to 2000 characters. No default five-star score,
reward for praise or provider control over customer feedback. Identical retries
return the existing result; changed submissions cannot overwrite the original.
The transaction binds the accepted quote to the actual provider listing and
atomically commits the private record, public projection, request marker and
rating total/count. Concurrent jobs cannot lose increments. Provider availability
editing remains permitted after their first real review.

## Android flow and privacy

Taleplerim → İş durumu → provider submission/customer confirmation → review dialog.
Drafts survive server errors while the dialog is open, submission disables repeated
taps and only confirmed persisted reviews close the dialog. Drafts are in memory
and do not survive process death. UID-bound repository guards and server-only
snapshots preserve existing account-switch behavior. Server time, not the phone's
clock, determines the 30-day cutoff; the job detail hides the review CTA after its terminal confirmation
expires. The server independently verifies the deadline against its own clock.

Provider details display newest reviews first, load 20 at a time, and permit loading
more. Increasing a live query limit rereads its prefix; this is not cursor pagination.
Public projections include score, comment, date and verifiedJob, but no customer UID,
request ID, phone, address or customer display name. A hashed public review ID is
linked by an Admin-only `reviewLookup` record. User comments can contain voluntarily
entered personal data; UI warns against it, and reports support manual moderation.

## Moderation and production

`reportJobReview` accepts bounded reasons and one report per person/review, with an
hourly quota. Reports cannot automatically hide negative feedback.
`getReviewModerationQueue` returns at most 50 pending reports to a currently
verified moderator. `hideJobReview({requestId, note, reportId?})` requires a current
Admin Auth moderator claim, records an audit trail, removes the public projection
and decrements rating sum/count atomically and idempotently. The private tombstone
prevents the customer from reposting a hidden review. If reportId is supplied, it
must reference the same job and is marked reviewed. A dedicated moderator UI,
restoring hidden content and provider replies are not included.

Client review/completion/aggregate writes are denied by Rules. Callables enforce
App Check outside the Firebase emulator. Hourly quotas apply to review, report and moderation operations. Existing ratings must remain server-owned; any
future migration of preexisting nonzero scores must reconstruct `ratingSum` from
verified records instead of trusting legacy client claims. This repository's
existing create rules required zero initial rating/count.

Deploy the Functions, Rules and new reviewReports index together using the existing
protected production workflow before shipping the Android feature. No production
Firebase credentials were available in this session; no live deployment is claimed.
Do not infer a signed production build or physical-phone success from emulator CI.

## References reviewed 2026-10-09

- Armut: https://armut.com/ and https://armut.com/bireysel-danisman
  Real customer reviews are a service-selection signal. Its public descriptions
  do not establish the exact internal transaction or authorization implementation.
- Fiverr: https://help.fiverr.com/hc/en-us/articles/360049982353-Leaving-and-managing-reviews-on-Fiverr
  Completed-order feedback and bounded review windows; Fiverr also has cancellation
  exceptions. Yakıno intentionally follows the user's stricter completed-only rule.
- Taskrabbit: https://support.taskrabbit.com/hc/en-us/articles/46260428700059-How-Do-I-Leave-a-Review
  Ratings focus on task performance rather than platform complaints.

The 30-day window, immutable submission and customer-only public review are Yakıno
product choices, not claims that every referenced marketplace uses the same rules.
