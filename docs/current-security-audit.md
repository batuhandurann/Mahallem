# Current security audit follow-up

Date: 2026-10-10 (Europe/Istanbul). Working branch:
`audit/current-main-20261009`, integrating `origin/main` source `9eecf8d`.

## Conversation identity

New conversation writes bind the document ID to both participant UIDs using the
same length-prefixed encoding as the Android repository. An authenticated Eve
cannot reserve Alice and Bob's deterministic conversation document by substituting
Eve for either participant. Both membership and ID binding are checked in Rules;
the repository verifies the actual participants of an existing conversation.

Rules regression cases exercise three outsider payloads against Alice and Bob's
ID, arbitrary IDs, normal participant creation, reversed participant arrays and
UIDs containing colons. The named `mahallem` integration repeats the reservation
attack using real Auth emulator UIDs before the legitimate client creates its chat.
Every attack uses a correctly formed atomic quota transaction and asserts that
rejection leaves the attacker's allowance untouched.

The Android repository sorts participant UIDs for its deterministic ID. Rules
accept either encoded participant order; they bind identity but do not establish
global uniqueness of a pair across both possible document IDs. Legacy admin-seeded
conversation IDs remain in read, message and report fixtures to exercise existing
history. Client creation fixtures use encoded IDs, including block and quota tests.

## Quote authority and visibility

New quotes require a published provider and a published, pending request. Provider
name, title and rating must equal the provider document's authoritative values.
The client repository derives these values from that document inside its
transaction rather than trusting a caller's display snapshot.

Regression cases reject forged names, forged titles, inflated ratings and stale
zero ratings for an actually reviewed provider. A legitimate 4.25 rating succeeds.
Hidden and closed provider/request documents reject fresh quotes while restoring
published visibility permits a valid quote. The named database integration also
tests three display-field forgeries with real Auth UIDs before successful quoting.
Failed writes neither create the forged quote nor consume its quota.

## Validation evidence

- `node --check test/firebase/rules.test.mjs`: passed locally.
- `node --check test/firebase/auth.test.mjs`: passed locally.
- `git diff --check -- test/firebase/rules.test.mjs test/firebase/auth.test.mjs`:
  passed locally.
- Combined Auth/Firestore emulator execution is pending the coordinating agent's
  run against the final integrated source. No emulator suite was started in
  parallel by this audit task.

These checks do not establish production Rules deployment, a signed Android
build, physical-device behavior or live Firebase enforcement. Final integrated
build and emulator results belong in `PROGRESS.md` with their actual environment
and source revision.
