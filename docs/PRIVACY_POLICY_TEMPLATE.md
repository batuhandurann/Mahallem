# Privacy Policy publication template

> **BLOCKED FOR PRODUCTION UNTIL ALL `[REQUIRED: ...]` FIELDS ARE REPLACED AND LEGAL REVIEW IS COMPLETE.**

## Privacy Policy for [REQUIRED: FINAL APP NAME]

Effective date: [REQUIRED: DATE]

Data controller/developer: [REQUIRED: LEGAL ENTITY OR DEVELOPER NAME]  
Contact: [REQUIRED: PRIVACY CONTACT EMAIL / CONTACT METHOD]  
Address/jurisdiction, if required: [REQUIRED: COMPANY/JURISDICTION DETAILS]

### Data we process

The production app may process account identifiers and verified contact information, profile/listing information, service requests and offers, messages and reviews, optional uploaded images, location when the user grants permission, device push-notification tokens, payment/order metadata, security/abuse records, and diagnostic data. The exact list must match the final production build and Google Play Data Safety declaration.

### Why we process it

Purposes include authentication and account management, providing the local marketplace and messaging features, nearby discovery, notifications, payment and dispute workflows, user support/moderation, abuse/fraud prevention, service reliability, and—only where the user consents—product analytics/marketing preferences.

### Processors and third parties

[REQUIRED: LIST FINAL PROCESSORS, COUNTRIES/TRANSFER BASIS IF APPLICABLE, AND PURPOSES.]  
Engineering currently uses Firebase/Google services and PayTR; verify the final enabled SDKs and contracts before publishing this policy.

### Retention and deletion

Users can request account deletion in the app from **Hesap ve Gizlilik**. The backend currently uses a 30-day requested-deletion window before purge. During purge, directly identifying account references are deleted or anonymized; Firebase Auth and user-scoped storage/profile data are removed. Any transactional/security records that must legally remain must be documented here with their retention reason and duration.

External deletion request page: [REQUIRED: PUBLIC HTTPS ACCOUNT DELETION URL]

### Analytics and marketing choices

Analytics collection is disabled by default and enabled only after the active user gives account-scoped analytics consent. Marketing preference is separate and optional. Explain any final advertising/marketing SDKs here; none should be implied by this template.

### Security

The app uses encrypted network transports, Firebase security rules, App Check/Play Integrity for protected backend calls, rate limits, authenticated ownership/participant checks, and CI security scanning. No policy can guarantee absolute security.

### User rights / contact

[REQUIRED: INSERT RIGHTS AND REQUEST PROCEDURE APPLICABLE TO YOUR LEGAL JURISDICTION, INCLUDING KVKK/GDPR WHERE APPLICABLE.]

This template is not legal advice and must be reviewed against the final product, business entity and applicable law.
