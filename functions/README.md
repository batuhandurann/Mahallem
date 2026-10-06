# Mahallem backend functions

Node.js 22 Cloud Functions are used for trusted server-side operations. Firebase currently supports Node.js 20 and 22 for Cloud Functions. citeturn985246search0

Functions in this package:

- `createPaymentIntent`: authenticated checkout initialization; fails closed until PayTR Marketplace credentials/application are configured.
- `requestRefund`: admin-only refund workflow entry point.
- `paytrWebhook`: validates the PayTR callback hash and applies an idempotent payment state transition.
- `notifyNewMessage`: sends FCM notifications for new messages.

Secrets:

- `PAYTR_MERCHANT_KEY`
- `PAYTR_MERCHANT_SALT`

Never put these values in the Android app or Git history.
