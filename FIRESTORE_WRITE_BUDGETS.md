# UID write budgets

The client writes these documents through Firestore transactions. Security Rules
enforce the same limits even when a caller bypasses the Android app.

| Creation operation | Per UID, per one-hour window |
| --- | ---: |
| Provider listings and job requests combined | 10 |
| Quotes | 60 |
| New conversations | 30 |
| Messages across all conversations | 240 |
| Reports | 10 |

The window starts at the first successful write, using Firestore server time.
Normal message bursts are allowed within the hourly allowance. Creating more
accounts is not prevented by a per-UID allowance; Auth protection, App Check,
usage monitoring and incident response remain necessary.

Each operation has one private document at
`users/{uid}/writeBudgets/{operation}`. It records `count`, `windowStartedAt`,
`updatedAt` and a `target` document reference. The target and the budget increment
must commit together. The target must be a new document of the operation's type
and belong to that UID. Every target's own schema, ownership and block checks
still apply. One increment cannot authorize multiple new documents. Another
account cannot read or modify the allowance.

Only an increment by one is permitted while the server-time window is open.
After an hour, Rules permit count one and a new server timestamp. Counter resets,
decrements, future windows, standalone counter changes and deletes are denied.
**Do not apply TTL to these documents:** deleting a counter would reset the
allowance. There are at most five counter documents for an account.

Android reads the counter inside the same transaction, checks its allowance and
creates the content. Transactions retry conflicts, preserving the shared limit.
Client time helps propose an expired-window reset; Rules independently validate
server time. A wrong device clock cannot authorize an early reset. Requests that
cannot be committed show an error; there is no local/demo success fallback.

Conversation previews require the same transaction to create a fresh message,
consume its allowance and use that message's text. A participant cannot churn
preview writes without a message. Contact documents remain part of the listing
transaction; message previews remain part of the message transaction. Failed
content validation rolls back the counter and all associated writes.

These limits cover costly content creation and conversation previews. Profile,
favorite, device registration, blocking, provider availability and quote status
changes retain their existing authorization rules. They are not presented as a
global read/write rate limiter. Trusted Admin SDK functions bypass Rules and keep
their separate upload, download, moderation-queue and push budgets.

Deploy the matching Android client and Rules together. Older clients that omit
the counter will fail closed after these Rules are deployed. The prepared
production workflow must still be run with authorized Cloud credentials; these
source changes do not prove live deployment or enforcement.

## Verification

`npm run test:rules` runs actual Auth/Firestore emulator tests, including direct
SDK bypass attempts, counter tampering, atomic rollback and concurrent writes at
the allowance boundary. Android instrumentation runs the actual Android SDK
against the demo emulators. Neither establishes production App Check attestation
or physical-device success.
