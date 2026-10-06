# Mahallem production backend setup

## 1. Firebase project

Create a Firebase project for Mahallem and register the Android application with:

- Package: `com.aistudio.mahallemde.kxqrvz`

Download separate Firebase configuration files for each environment. The Google Services Gradle plugin supports build-type-specific configuration files.

- Staging: `app/src/staging/google-services.json`
- Production: `app/src/release/google-services.json`
- Local/debug: no Firebase network should be required; the debug build routes Firebase SDKs to the local emulators when a Firebase config is present.

Do not commit service-account JSON files or server secrets. `google-services.json` contains project identifiers rather than server secrets, but this repository intentionally ignores these files so environment configuration cannot be mixed accidentally.

## 2. Authentication

Enable Firebase Authentication in each environment.

- LOCAL/debug: the app does not require a remote Firebase Auth session and routes Firebase SDKs to the local emulators.
- STAGING: enable Phone authentication and configure Firebase fictional/test phone numbers; never use a real personal phone number in automated tests.
- PRODUCTION: enable Phone authentication for real users and configure the final reCAPTCHA/App Check/Play Integrity policy.

The Android app uses Firebase Phone Auth directly. The legacy `ProductionSmsVerificationGateway` is only an abstraction boundary for a future external SMS provider and must not be treated as an active production SMS implementation.

The non-production demo verification code `123456` is retained only for local/demo flows and must never be accepted by Firebase production authentication.

## 3. Firestore

Create these initial collections:

- `users/{uid}`
- `providers/{providerId}`
- `jobRequests/{requestId}`
- `quotes/{quoteId}`
- `conversations/{conversationId}`
- `messages/{messageId}`
- `payments/{paymentId}`

Use Firebase Security Rules so users can only read/write records they are authorized to access.

## 4. SMS provider

The Android app must not contain SMS provider API keys.

Implement `ProductionSmsVerificationGateway` behind a server-side endpoint or Firebase Cloud Function. The provider should be selected before implementation (for example, a Turkish SMS provider).

Required production flow:

Android -> HTTPS backend -> SMS provider -> verification result

## 5. Payments

Do not put merchant/payment secrets in the Android application.

Implement the production `PaymentGateway` on the backend using the chosen Turkish payment provider's sandbox first.

Required flow:

Android -> backend -> payment provider sandbox -> webhook -> backend -> Firestore

The Android app should only receive a payment intent/checkout result.

Never store raw card numbers, CVV, or payment secrets in Mahallem.

## 5.1 PayTR iframe checkout configuration

The Android app never receives PayTR merchant secrets and never collects raw card data. The backend creates the PayTR iframe token and returns only a hosted checkout URL.

Configure these Cloud Functions secrets separately for staging and production:

- `PAYTR_MERCHANT_ID`
- `PAYTR_MERCHANT_KEY`
- `PAYTR_MERCHANT_SALT`
- `PAYTR_OK_URL`
- `PAYTR_FAIL_URL`
- `PAYTR_TEST_MODE` (`1` in sandbox/test, `0` in live)

The checkout flow is:

Android -> authenticated/App Check callable -> PayTR token endpoint -> PayTR hosted checkout -> signed PayTR webhook -> Firestore payment state.

The project currently implements the standard PayTR iframe token flow. A true marketplace seller-settlement/escrow model additionally requires the PayTR Marketplace Solution onboarding/approval and merchant-specific sub-merchant configuration. Do not represent the standard tokenized checkout as completed seller payout/escrow until that approval and staging verification are complete.

## 6. Environments

Use separate Firebase projects for staging and production. Firebase's Google Services Gradle plugin supports build-type-specific `google-services.json` files, so Mahallem uses:

- LOCAL/debug: no remote Firebase dependency required for the main demo flow; SDKs point to emulators when Firebase is initialized.
- STAGING: `app/src/staging/google-services.json`
- PRODUCTION: `app/src/release/google-services.json`

STAGING and PRODUCTION must never share the same Firebase project.

Payment and SMS integrations follow the same separation:
- STAGING: sandbox/test credentials only.
- PRODUCTION: live credentials only, injected as deployment secrets.

Account deletion uses a 30-day grace period. The user can cancel during the grace period. The scheduled cleanup anonymizes marketplace/audit references, removes private request/profile data, removes the Firebase Auth account, and preserves financial records needed for accounting/audit retention.

Production secrets must be supplied through deployment secrets, not Git.

## 7. Current status

The repository now contains Firebase Auth/Firestore dependencies and integration boundaries for Auth, Firestore, SMS and payments.

A Firebase project and payment/SMS provider credentials are still required before live connections can be enabled.
