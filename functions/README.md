# Trusted push, private media, moderation

The Android app uses Firestore database `mahallem`. Storage Security Rules can
only look up documents in `(default)` ([Firebase documentation](https://firebase.google.com/docs/storage/security/rules-conditions)).
All direct Storage client access is therefore denied. Two authenticated callable
functions check the authoritative conversation participants and both directions
of user blocking on each upload/read. Objects have no public download URLs or
Firebase bearer download tokens. Existing buckets must have public IAM removed
and legacy download tokens revoked before deploying this model.

`uploadConversationPhoto` accepts a content-picker image at most 5 MB. Android
and the server independently decode it. The server limits input to 16 megapixels,
accepts JPEG/PNG/WebP, strips EXIF/GPS and stores a fresh JPEG. Metadata is written
by the server in `conversations/{id}/media/{mediaId}`. Authenticated reads return
bounded bytes; no arbitrary remote URL or client-selected storage path is fetched.
Upload budgets are 30 attempts per UID per day; reads 120 per UID per hour.

Message push is triggered by server-observed Firestore message creation, never
by a client-selected recipient/token. It sends data-only FCM with `recipientUid`;
Android compares it with the active UID on the main thread. Lock-screen text is
generic. The device token lives under `users/{uid}/devices/{sha256(token)}`;
logout clears notifications/removes registration and rotates the token. Stale
registrations expire from fanout after 30 days. Invalid tokens are deleted.
Transient FCM failures retry; completed delivery markers suppress later retries.
At-least-once event delivery can repeat a send before the marker is committed;
the notification ID is the message ID, so retries replace an existing alert.
FCM has no emulator: policy tests cover identity/payload selection, but live push
delivery requires a configured Firebase project and a Play Services device.

`getModerationQueue` and `reviewReport` require both a signed Auth token with
`moderator: true` custom claims and a fresh matching Admin Auth user record.
Client profile fields never grant privileges. Server administration assigns
claims; no self-service role creation endpoint is included. Review can dismiss a
report or hide a listing, verifies its recorded owner, and transactionally updates
the pending report and creates `moderationAudit` evidence. Moderation of messages,
user bans, appeals and a moderator UI require a separate operational workflow.

## Local verification

Use JDK 21+ and Node 22+, then run from the repository root:

```sh
npm ci
npm ci --prefix functions
npm run test:backend
npm run test:media
```

The local Functions runtime alone disables App Check enforcement when the
Firebase-set `FUNCTIONS_EMULATOR` flag is exactly `true`. Production always
enforces it. Local integration uses real Auth/Firestore/Functions/Storage
emulators, verifies outsider/anonymous denial, photo sanitization, blocked
participant denial, private object access, moderator authorization and revocation.
These tests never target a live project.

## Cloud activation still required

No deployment or console change has been performed by these code edits. Before
release, register the final Android package and signing fingerprints, configure
Play Integrity App Check and enable Firestore/Storage enforcement, provide the
actual bucket and database, and deploy rules, indexes and trusted functions to
that project. Functions use Node 22 and region `europe-west3`; the runtime service
account needs least-privilege database, storage, Firebase Auth read and FCM send
access. Android connects to the same Functions region and database. Configure
Firestore TTL on `_abuseBudgets.expiresAt` and `_pushDeliveries.expiresAt` so
budget/delivery bookkeeping expires. Storage objects need a data retention and
deletion policy; chat deletion is not currently implemented.

Enable server-side quota/usage monitoring and confirm deployment's report index
(`status`, `createdAt`). Run signed Android builds on an emulator and an actual
device: login each of two accounts, upload/read an image, switch accounts,
deny/grant notification permission, rotate tokens, block in either direction,
and verify real background/foreground FCM delivery and Play Integrity failures.
The Cloud Storage project IAM must never grant anonymous reads for private media.
