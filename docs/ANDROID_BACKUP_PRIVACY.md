# Android local-data backup policy

The app caches account-scoped marketplace information locally. A device backup or transfer can restore data into a context with a different signed-in account or a lost device. This is a privacy and account-isolation risk.

## Current branch

- AndroidManifest.xml sets `android:allowBackup="false"`.
- The pre-Android-12 backup XML explicitly excludes the app-private root.
- Local JVM regression tests check manifest settings, legacy exclusions, and Android 12+ cloud/device transfer exclusions.

## Android 12+ backup and transfer safeguards

The Android 12+ `data_extraction_rules.xml` excludes app-local `root`, `file`, `database`, `sharedpref`, and `external` domains under **both** `cloud-backup` and `device-transfer`. The manifest references this XML as well as the pre-Android-12 backup rules. JVM regression tests verify both transport sections, the exclusions, and the manifest references.

These source-code protections are **not evidence of actual Android restore / device-transfer behavior**. Before merge, validate backup and device migration on representative Android 11 and 12+ devices or emulators, including account switching, and record the outputs.

## UX and operations trade-offs

Disabling backup can mean that locally stored drafts, cached messages, and offline work are not restored after reinstall or device migration. Server-backed records must be recovered from the authenticated backend, and any unsynced work should be clearly marked before logout or uninstall. Avoid storing plaintext personal data in logs or analytics.

## Verification before merge

1. Confirm Android Quality / Security CI for the **latest PR head SHA**, including JVM tests, build, lint and UI tests.
2. Verify actual backup/restore and device transfer behavior on Android 11 and Android 12+ devices or emulators, including account switching.
3. Confirm no unexpected local-only customer data is required for recovery; warn users about unsynced drafts.
4. Obtain owner approval before integration; do not merge solely based on source-level regressions.
