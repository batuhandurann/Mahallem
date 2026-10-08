# Burada: complete the production and physical-device checks

The main integration is merged through PR #10. The existing unsigned CI APK/AAB
proves compilation. Distribution signing, live Firebase/Play settings and real
device behavior require the credentials and hardware below. Missing prerequisites
must never be recorded as successful execution.

## Signed APK and AAB

In the protected GitHub `production` environment, supply:

| Secret | Value |
| --- | --- |
| `GOOGLE_SERVICES_JSON_BASE64` | Downloaded configuration for the real Firebase registration of `com.batuhanduran.burada` |
| `ANDROID_KEYSTORE_BASE64` | Existing Android upload keystore encoded as base64 |
| `ANDROID_KEYSTORE_PASSWORD` | Existing store password |
| `ANDROID_KEY_PASSWORD` | Existing private-key password |
| `ANDROID_KEY_ALIAS` | Optional existing alias; default `upload` |

Run **Production Signed APK and AAB** on the reviewed main commit. It builds and verifies
signed APK/AAB artifacts and records public certificate/file checksums. It does
not upload the application to Play. Use the established upload key if the app
already exists; generating a substitute cannot prove the correct signing setup.

## USB phone: real Android SDK plus isolated Auth/Rules

Install JDK 21, Node 22+, Android SDK platform `android-36.1` / build-tools
`36.0.0`, and platform-tools. Enable USB debugging on the physical phone and
authorize the host. `adb devices` must show that phone as `device`.

```bash
python3 scripts/physical_android_smoke.py --preflight-only
python3 scripts/physical_android_smoke.py
```

For multiple devices, add `--serial YOUR_DEVICE_SERIAL`. `--adb PATH_TO_ADB` is
available when platform-tools is not on PATH. On Windows, run the repository
commands from a bash/WSL environment with the Android SDK/device accessible.

The second command installs test dependencies, starts only `demo-mahallem` Auth
and Firestore emulators, sets device-specific USB reverse mappings, compiles
the debug/test APKs, and runs the full Android instrumentation suite on that
phone. It then runs foreground/background, rotation and actual process-death
smoke checks. New reverse mappings are removed afterward; existing conflicting
mappings stop the run. The runner does not uninstall an existing application to
work around a signing mismatch: use a dedicated test phone/profile.

Evidence is saved to `build/evidence/physical-device.json`. Only a positive JUnit
test count plus successful lifecycle smoke produces `PASS`. Preflight alone
remains `NOT_RUN`. The serial is hashed in the evidence. These tests use real
Android SDKs on a phone with an isolated emulator backend; they do not verify
production Play Integrity or real FCM delivery.

## Production device attestation and push

After the registered, signed application and backend are deployed, install the
release through Play internal testing on physical devices. Verify fresh App Check
tokens and rejection without valid attestation; confirm API-restricted Auth,
Firestore and photo operations. Send a real message between two test accounts:
the recipient must receive push in background/foreground. Sign out and switch
accounts on the recipient device; messages for the old UID must not display.
Repeat with notification permission denied and with either direction blocked.

Record the tested commit/version, physical model/API, distribution channel,
results and relevant Cloud metrics without tokens, private messages or keys.
`FIREBASE_SECURITY_RELEASE.md` explains the Console settings and evidence audit.
`FIREBASE_PRODUCTION_DEPLOY.md` describes the protected WIF/ADC deployment and
live audit workflow, exact environment variables and required permissions.

The existing **Android Physical Test Lab Smoke** workflow tests the selected
Compose screen on a physical hosted device. It requires authorized staging Test
Lab credentials and billing. It is a UI smoke test and does not replace the USB
Auth/Rules test or the signed production attestation/push checks.
