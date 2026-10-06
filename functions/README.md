# Mahallem backend functions

Node.js 22 Cloud Functions are used for trusted server-side operations. Firebase is configured here for Node.js 22.

Functions in this package:

- `createPaymentIntent`: authenticated checkout initialization; fails closed until PayTR Marketplace credentials/application are configured.
- `requestRefund`: admin-only refund workflow entry point.
- `paytrWebhook`: validates the PayTR callback hash and applies an idempotent payment state transition.
- `notifyNewMessage`: sends FCM notifications for new messages.

Secrets:

- `PAYTR_MERCHANT_KEY`
- `PAYTR_MERCHANT_SALT`

Never put these values in the Android app or Git history.
