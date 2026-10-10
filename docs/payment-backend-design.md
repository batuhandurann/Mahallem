# Hosted iyzico checkout integration

Implemented on 2026-10-10. This is a server adapter and transaction policy with
automated fixture tests. It is not evidence of a real sandbox or live payment.
Production collection must remain disabled until merchant onboarding, verified
submerchant onboarding, refund/settlement handling and protected credentials are
available and tested. Current job lifecycle rejects legacy funded escrow; this
integration must never set legacy `escrowFunded` or claim money is held in escrow.

## Boundaries and API contract

`payment-policy.js` accepts `checkoutCommand({ requestId })` only. Amount,
customer/provider identity, currency and payment result cannot come from the app.
`authoritativePayment({ requestId, listing, quote, actorUid })` consumes the
authoritative request and its `acceptedQuoteId` document, checks accepted status,
party bindings, active job status and absence of a prior legacy payment, then
produces `{requestId, quoteId, customerUid, providerUid, amountMinor, currency}`.
The caller must fetch the quote using the accepted ID, not a caller-selected ID.

Existing price strings use Turkish grouping/decimal rules; integer kuruş is
derived without floating point arithmetic. For example, `1.250,50 TL` is 125050
kuruş. Ambiguous US-style `1.25`, nonpositive, exponent and oversized prices fail.

`createIyzicoProvider(config, {fetchImpl?})` requires an explicit `sandbox` or
`production` environment, `apiKey`, `secretKey`, `merchantEnabled: true`, and a
server-controlled HTTPS `callbackUrl`. There is no production fallback. The app
must not receive any of these secrets. The orchestration layer independently
keeps production disabled until the full payment lifecycle is implemented.

- `initialize({paymentId, authority, buyer, billingAddress, subMerchantKey,
  subMerchantAmountMinor})` returns `{token, paymentPageUrl, expiresInSeconds}`.
  `paymentId` is an immutable server-generated attempt ID. Buyer billing and
  submerchant fields originate from validated private server records; callback
  URL, payout and amount are never taken from app input. No dummy identity number
  or address is generated. Only selected buyer/address fields reach iyzico.
- `retrieve({paymentId, token, authority})` returns a minimal verified result:
  `status` (`PAID`, `REVIEW`, `FAILED`), `providerPaymentId`, and, where applicable,
  `itemTransactionId`, `amountMinor`, `currency`. Callback POST values only request
  reconciliation; they cannot establish success. The adapter re-fetches the
  result over authenticated HTTPS, validates HMAC, then matches stored attempt,
  token, job, amount, currency and single quote item. Fraud review never means paid.
- `PaymentError` carries a callable-compatible `code` and sanitized Turkish
  message. Network errors, invalid replies and provider errors remain unresolved;
  they do not establish a safe-to-retry failed payment. Secrets, billing details,
  raw provider responses, card metadata and callback tokens must not be logged.

Only iyzico's exact environment-specific API origin and `/checkoutform/` path are
accepted as the hosted payment URL. HTTP redirects for API requests are rejected.
The adapter bounds replies to 1 MiB and requests to 15 seconds, uses cryptographic
request randomness, and compares response signatures in constant time. Android
opens the hosted URL externally; this app does not collect PAN, expiry or CVC.

## Durable orchestration

`payment-handler.js` implements `getPaymentAvailability`, `startHostedCheckout`,
`getHostedCheckoutStatus` and `iyzicoCheckoutCallback` with fresh Auth/deletion
checks, quotas and server-only `_paymentAttempts`, `_paymentTokens`,
`_paymentBuyers` and `_paymentMerchants` records. The buyer and submerchant
records must be verified by server-side onboarding; no client write is permitted.
No onboarding identity or address is invented. Production collection is always
disabled in this version; an explicit sandbox configuration is required even
when credentials exist. The operator must provide the authenticated sandbox
callback URL plus sandbox credentials through protected server configuration.

Reserve a single durable attempt per request in a Firestore transaction before
calling iyzico. A second concurrent call returns the same known checkout or
pending status. `conversationId` is a correlation identifier; iyzico documentation
does not establish initialize-call idempotency. Therefore an interrupted or
timed-out initialize must never automatically create another checkout. An attempt
without a known token requires provider reconciliation/support before retry.

Store immutable authority, provider environment, token and status in a server-only
collection. Deny all client reads/writes. Store an opaque callback-token hash index
if needed; revalidate its attempt binding. Read status through authenticated,
App-Check-protected callable code with fresh account/deletion checks and quotas.
Do not return private billing/submerchant data or raw payment tokens in status.

On reconciliation, use the stored immutable authority even if job status has
subsequently changed: a charge must remain discoverable after cancellation or
moderation. Apply `assertSameAttempt` before checkout reuse. Apply
`nextPaymentState` inside a transaction to reject changed provider payment IDs
and prevent out-of-order callbacks from downgrading `PAID`. Preserve an audit
record of exceptions; never create a second charge to recover a lost response.

Customer payment capture and service-provider settlement are distinct states.
The minimal adapter stores the item transaction ID required for later refund and
marketplace approval. It does not implement refund, transfer approval, legal
escrow, cancellation compensation or disputes. A future implementation needs
durable operation IDs, verified provider outcomes and reconciled bookkeeping for
those operations before enabling production collection.

## Validation

`node --test functions/test/payment.test.js`: 13/13 passed on local Node24.19.0
(Functions production runtime is Node22). Tests cover authority/price parsing,
caller amount rejection, immutable replay and monotonic paid state, required
configuration, exact request HMAC, marketplace payload, response signatures,
malicious hosted URL rejection, fraud review, wrong amount/token/item binding,
unknown network outcomes and missing submerchant/billing prerequisites. These
tests use local fake provider replies, never merchant credentials or actual cards.
Six additional Auth/Firestore/Functions emulator integrations verify concurrent
single initialization, durable unknown outcomes, paid reconciliation after job
cancellation, private-document denial, account revocation, disabled real callable
configuration and forged browser callbacks. Monetary reply signatures strip
fractional trailing zeroes as required by the official response-signature guide.

## Official sources inspected

- [Checkout Form initialize](https://docs.iyzico.com/en/payment-methods/checkoutform/cf-implementation/cf-initialize)
- [Response signatures and monetary trailing zeroes](https://docs.iyzico.com/en/advanced/response-signature-validation)
- [Checkout Form retrieve, fraud status and transaction item fields](https://docs.iyzico.com/en/payment-methods/checkoutform/cf-implementation/cf-retrieve)
- [Official Node SDK request authorization implementation](https://github.com/iyzico/iyzipay-node/blob/master/lib/utils.js)
- [Official initialize response signature sample](https://github.com/iyzico/iyzipay-php/blob/master/samples/initialize_checkout_form.php)
- [Official retrieve response signature sample](https://github.com/iyzico/iyzipay-php/blob/master/samples/retrieve_checkout_form_result.php)
- [Official Node marketplace and Checkout Form samples](https://github.com/iyzico/iyzipay-node/blob/master/samples/IyzipaySamples.js)

No provider API network request or deployment was performed during development.
