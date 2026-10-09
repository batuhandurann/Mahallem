# Android local-data backup policy

The app caches account-scoped marketplace information locally. A device backup or transfer can restore data into a context with a different signed-in account or a lost device. This is a privacy and account-isolation risk.

## Current branch

- AndroidManifest.xml sets `android:allowBackup="false"`.
- The pre-Android-12 backup XML explicitly excludes the app-private root.
- A local JVM regression test checks both settings.

## Important remaining gap (do not merge yet)

The Android 12+ `data_extraction_rules.xml` currently contains only the template rules. Explicit exclusions are still needed in **both** `cloud-backup` and `device-transfer`, for example `<exclude domain="root" path="." />` in each. The connector rejected the file update, so this branch is incomplete. In particular, `allowBackup="false"` alone is not a sufficient guarantee against device-to-device transfer on every supported device.

## UX and operations trade-offs

Disabling backup can mean that locally stored drafts, cached messages, and offline work are not restored after reinstall or device migration. Server-backed records must be recovered from the authenticated backend, and any unsynced work should be clearly marked before logout or uninstall. Avoid storing plaintext personal data in logs or analytics.

## Verification before merge

1. Add the Android 12+ exclusions and a regression test that parses both transport sections.
2. Run debug and unsigned CI release builds, JVM tests and instrumentation smoke tests.
3. Verify restore/transfer behavior on Android 11 and Android 12+ devices or emulators, including account switch.
4. Confirm no unexpected local-only customer data is required for recovery.
