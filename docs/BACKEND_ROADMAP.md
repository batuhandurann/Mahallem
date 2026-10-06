# Mahallem backend roadmap

## Environments
- LOCAL: Room + test gateways. No real SMS or money.
- STAGING: separate Firebase project + SMS/payment sandbox.
- PRODUCTION: separate Firebase project + live providers.

## Firebase
Authentication provides identity/session. Firestore stores cloud data. Security Rules enforce authorization. Trusted backend functions handle SMS, payment creation/verification and webhooks.

## Collections
- users/{uid}
- providers/{providerId}
- jobRequests/{requestId}
- quotes/{quoteId}
- conversations/{conversationId}
- messages/{messageId}
- payments/{paymentId}

Payment creation and payment status changes are backend-only.

## Required before production
1. Create staging and production Firebase projects.
2. Register the Android package in each project.
3. Add matching google-services.json files through the deployment workflow.
4. Enable authentication providers.
5. Deploy Firestore rules.
6. Implement trusted backend functions.
7. Select and configure an SMS provider.
8. Select and configure a Turkish payment provider sandbox.
9. Add App Check for production.
10. Run staging QA before release.

Provider credentials and payment secrets must never be stored in the Android app.
