# Google Play Data Safety engineering draft

This file is an engineering inventory, not a legal submission. The Play Console declaration must be reviewed against the final production build, third-party SDK disclosures, privacy policy, and actual backend configuration before submission.

## Data observed in the current codebase

| Play data area | Current app behavior | Typical purpose | User choice / notes |
| --- | --- | --- | --- |
| Personal info: name | User profile/provider listing may contain a display name. | Account management, marketplace functionality | Required only where the user chooses to publish/profile themselves. |
| Personal info: phone number | Firebase Phone Auth and profile verification use the user's phone number. | Authentication, fraud/security, account management | Authentication data; Firebase processes the credential. |
| Personal info: email address | Email may exist in Firebase Auth and is sent to the payment backend/provider when payment requires it. | Authentication/account management, payment processing | Verify final Auth methods and PayTR processing terms before Play submission. |
| Approximate & precise location | Android requests coarse/fine location; map/job/provider flows can use coordinates. | App functionality, nearby discovery | Permission-gated. Declare both if production can transmit/store both. |
| User content | Job requests, provider listings, reviews, reports, chat text and optional images are sent off-device. | App functionality, moderation, customer support/security | User-generated. |
| Photos | StorageRepository normalizes and uploads user/chat/job images after a server-issued grant. | App functionality | Optional user action. |
| Messages | Conversation/message data is stored in Firestore and notifications can be sent to participants. | App functionality | Participant-scoped by security rules. |
| App/device identifiers | Firebase Cloud Messaging token is stored per authenticated account/device. | Notifications, security/device management | Removed on normal logout and account purge. |
| Financial/payment info | The app/backend handles payment amount, order/payment identifiers and payment state; payment checkout is delegated to PayTR. | Payment processing, fraud prevention | The app must not claim it collects card numbers unless the final payment integration actually exposes them to this app/backend. |
| App activity / diagnostics | Firebase Analytics is disabled by default and enabled only after UID-scoped analytics consent; Crashlytics is enabled outside local mode. | Analytics, diagnostics, security/reliability | Verify Firebase SDK Data Safety guidance for the exact production configuration. |

## Security and deletion facts to verify in Play Console

- Cleartext traffic is disabled.
- Firebase callable endpoints enforce App Check where applicable.
- Firestore/Storage rules are emulator-tested in CI.
- In-app account deletion exists under **Hesap ve Gizlilik**.
- Deletion enters a 30-day requested state, can be canceled before purge starts, then backend purge removes/anonymizes associated data and deletes Firebase Auth.
- A separate public HTTPS account-deletion web resource is still required before production release.
- A public HTTPS privacy policy is still required.
- Data retained after deletion for legitimate payment, dispute, fraud/security or regulatory reasons must be described accurately in the final privacy policy. Current code anonymizes retained transactional records rather than retaining the raw UID.

## Third-party SDK/service review required before submission

Review the final production configurations and current provider disclosures for:
- Firebase Authentication
- Cloud Firestore
- Cloud Functions
- Cloud Storage
- Firebase Cloud Messaging
- Firebase Crashlytics
- Firebase Analytics
- Firebase App Check / Play Integrity
- Google Maps / Play Services Location
- PayTR
- any AI/Firebase AI feature that is actually reachable in the production build

Do not mark the Play form complete solely from this document.
