#!/usr/bin/env bash
set -euo pipefail

wait_for_boot() {
  timeout 180 adb wait-for-device
  local booted=""
  for _attempt in $(seq 1 30); do
    booted="$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
    if [[ "$booted" == "1" ]]; then
      return 0
    fi
    sleep 5
  done
  echo "Android emulator did not report sys.boot_completed=1" >&2
  return 1
}

run_tests() {
  ./gradlew :app:connectedDebugAndroidTest \
    --no-daemon \
    --stacktrace \
    --max-workers=2 \
    -PallowMissingGoogleServices=true
}

wait_for_boot

set +e
run_tests
result=$?
set -e

if [[ "$result" -eq 0 ]]; then
  exit 0
fi

# Retry only infrastructure failures where ADB lost the emulator.
if [[ "$(adb get-state 2>/dev/null || true)" == "device" ]]; then
  exit "$result"
fi

echo "ADB went offline; restarting ADB and retrying instrumentation once"
adb kill-server || true
adb start-server
wait_for_boot
run_tests
