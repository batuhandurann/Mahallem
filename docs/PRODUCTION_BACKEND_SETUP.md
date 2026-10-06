# Mahallem production backend setup

## 1. Firebase project

Create a Firebase project for Mahallem and register the Android application with:

- Package: `com.aistudio.mahallemde.kxqrvz`

Download the project's `google-services.json` and place it at:

`app/google-services.json`

Do not commit production credentials or service-account JSON files.

## 2. Authentication

Enable Firebase Authentication providers from the Firebase Console.

Recommended first phase:
- Email/password
- Phone authentication

For phone-auth testing, configure Firebase's **fictional/test phone numbers** in the Firebase Console. Do not use a real phone number or real SMS during automated tests.

The existing non-production demo verification contract remains `123456`. It must never be accepted by production authentication.

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

## 6. Environments

- LOCAL: Room + test gateways; no real SMS/payment.
- STAGING: Firebase staging project + SMS/payment sandbox.
- PRODUCTION: separate Firebase project + live providers.

Production secrets must be supplied through deployment secrets, not Git.

## 7. Current status

The repository now contains Firebase Auth/Firestore dependencies and integration boundaries for Auth, Firestore, SMS and payments.

A Firebase project and payment/SMS provider credentials are still required before live connections can be enabled.
