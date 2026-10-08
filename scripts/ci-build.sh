#!/usr/bin/env bash
# One-command, clean, unsigned CI build with NO production Firebase/Play secrets.
set -euo pipefail
cd "$(dirname "$0")/.."
test -x ./gradlew || { echo 'Missing executable Gradle wrapper' >&2; exit 1; }
python3 scripts/verify-gradle-wrapper.py
export MAPS_API_KEY="${MAPS_API_KEY:-CI_ONLY_NO_MAPS_API_KEY}"
./gradlew --no-daemon --stacktrace \
  -PallowMissingGoogleServices=true \
  -PciUnsignedRelease=true \
  clean :app:assembleDebug :app:testDebugUnitTest :app:lintDebug \
  :app:assembleRelease :app:bundleRelease
python3 scripts/build-evidence.py debug
python3 scripts/build-evidence.py release
echo 'CI-only debug APK and UNSIGNED release APK/AAB verified; NOT production signed.'
