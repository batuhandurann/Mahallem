# Mahallem backend roadmap

## Architecture
- LOCAL: Room + Firebase Local Emulator Suite/Test Gateways. No real SMS or money.
- STAGING: separate Firebase project + Firebase phone-auth test numbers + payment sandbox.
- PRODUCTION: separate Firebase project + live providers + App Check/Play Integrity.
- Android never receives SMS-provider or payment-provider secrets.

## Research decision
Primary marketplace payment adapter: PayTR Marketplace Solution. Its official marketplace offering supports sub-merchant commission distribution, protected-payment flow, automated settlement, partial refunds and configurable commission rules.

## Firebase responsibilities
- Firebase Authentication: identity and session.
- Firestore: cloud application data.
- Firebase Security Rules: authorization and field-level validation.
- Cloud Functions: trusted payment, refund, webhook and notification workflows.
- Cloud Storage: images with MIME/size rules.
- FCM: push notifications.
- App Check / Play Integrity: reduce unauthorized backend access.
- Crashlytics: crash/ANR monitoring.
- Analytics: product usage measurement, enabled only after consent.

## Data model
- users/{uid}
- users/{uid}/devices/{token}
- providers/{providerId}
- jobRequests/{requestId}
- jobRequestPrivate/{requestId}
- quotes/{quoteId}
- conversations/{conversationId}
- messages/{messageId}
- payments/{paymentId}

Sensitive address/phone fields must not be placed in public request documents.

## Payment flow
Android -> authenticated + App Check callable -> Cloud Function -> PayTR Marketplace -> webhook -> backend state machine -> Firestore -> FCM.
Financial state changes are backend-controlled. The Android client cannot mark a payment as paid/released/refunded.

## Security baseline
- Firebase Auth required for non-local environments.
- App Check enforced on callable backend functions.
- Admin access uses backend-managed custom claims; client cannot assign itself admin.
- Firestore updates use field allowlists.
- Payment provider webhooks require HMAC verification and idempotent processing.
- Storage accepts images only and enforces a 10 MB upload limit.
- Device notification tokens are owner-scoped.
- Local test verification code 123456 is never accepted in staging/production.
- No payment card number, CVV or provider secret is stored in the Android app.

## Required external setup before live use
1. Create staging and production Firebase projects.
2. Register the Android package in each project.
3. Add matching google-services.json files per environment through a secret-safe workflow.
4. Enable Phone Authentication and configure fictional test numbers for staging.
5. Deploy Firestore and Storage rules.
6. Configure Play Integrity/App Check.
7. Apply to PayTR Marketplace Solution and obtain merchant credentials.
8. Store PayTR secrets in Cloud Functions Secret Manager.
9. Complete staging payment/webhook/refund tests.
10. Complete privacy/KVKK consent and store disclosures.
11. Run release QA before production.

Research references: Firebase Security Rules, Phone Authentication, App Check/Play Integrity, Cloud Functions, PayTR Marketplace and callback documentation.