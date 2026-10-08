# Staging Firestore index deployment (guarded)

This workflow is **manual only**. It deploys **indexes only**, not rules, functions, Auth, App Check, or production infrastructure.

## Required GitHub repository setup

In Settings → Environments, create `staging` and require reviewer approval. Set its variables:
- `FIREBASE_STAGING_PROJECT_ID`: **dedicated staging** Firebase project ID
- `FIREBASE_PRODUCTION_PROJECT_ID`: real production Firebase project ID (must differ)

Set the `staging` environment secret `FIREBASE_STAGING_SERVICE_ACCOUNT_JSON` to a service-account JSON authorized for the staging project's Firestore index management. Never commit or paste credential contents into a PR, log or chat.

Before invoking the workflow verify the staging project has a named Firestore database **mahallem** configured for the environment. Project selection is explicit, and the workflow refuses blank/identical staging and production project IDs. Confirm `firebase.json` remains a **named-database** configuration and that the deployed indexes match actual queries.

## Execution and evidence

Merge this PR to the appropriate protected development/default branch after reviews. Trigger **Actions → Deploy Firestore Indexes (Staging Only) → Run workflow** with authorized staging reviewer approval.

In the workflow log, verify `firebase deploy --only firestore:indexes --project <staging-project>` succeeds, followed by a list of indexes from `firebase firestore:indexes --database mahallem`. Save the workflow URL, database ID and redacted output in issue #11. Then run a staging Android test with 55 conversations and 205 messages to confirm newest-first query behavior and no missing index errors. Do not mark the staging gate complete merely because this workflow file exists.

## Release prerequisites still separate

Signed release AAB requires a real Android app registration `com.batuhanduran.burada`, matching `google-services.json`, upload keystore under secure storage, app signing certificates, production Maps API configuration and actual release/device/Play checks. GitHub repo access does not grant Google Cloud or Play Console rights.
