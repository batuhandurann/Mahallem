# Production readiness gates

This repository treats a green pull-request CI as necessary but not sufficient for production.

## Automated gates

- `Mahallem QA`: unit tests, lint, debug APK, Compose emulator tests, backend tests, Firebase rules emulator tests, unsigned release APK/AAB.
- `Mahallem Security`: Gitleaks plus CodeQL for JavaScript and Kotlin.
- `Firebase Staging Deploy`: manual staging deploy of Firestore rules/indexes, Storage rules and Cloud Functions.
- `Android Physical Device Smoke`: manual Firebase Test Lab run that selects an actual PHYSICAL Android device.
- `Production Signed AAB`: manual signed AAB verification using protected GitHub environment secrets.

## Required protected secrets

Staging environment:
- `FIREBASE_SERVICE_ACCOUNT_STAGING`
- `FIREBASE_TEST_LAB_SERVICE_ACCOUNT`

Production environment:
- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`
- `GOOGLE_SERVICES_JSON_BASE64`
- `MAPS_API_KEY_PRODUCTION`

## GitHub administration gate

Before merging to `main`, repository administration must require pull requests and successful `Mahallem QA` and `Mahallem Security` checks. Direct pushes to `main` must be disabled. The current ChatGPT GitHub integration can edit code but cannot modify repository administration/rulesets.

## Store release gate

A Play Console release is not approved until the signed AAB above passes internal testing, Data Safety/privacy disclosures are complete, account deletion is reachable in-app, App Check enforcement is enabled for production backends, and the release has crash/ANR monitoring.
