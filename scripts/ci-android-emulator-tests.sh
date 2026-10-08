#!/usr/bin/env bash
set -euo pipefail
# Retry only for ADB device disconnects, never for genuine instrumentation failures.
timeout 180 adb wait-for-device
for attempt in $(seq 1 30); do
  if [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; then break; fi
  sleep 5
done
test "$(adb shell getprop sys.boot_completed | tr -d '\r')" = "1"
set +e
gradle :app:connectedDebugAndroidTest --no-daemon --stacktrace --max-workers=2 -PallowMissingGoogleServices=true
result=$?
set -e
if [ "$result" -ne 0 ]; then
  if [ "$(adb get-state 2>/dev/null)" != "device" ]; then
    echo "ADB went offline; restarting ADB and retrying instrumentation once"
    adb kill-server || true
    adb start-server
    timeout 120 adb wait-for-device
    test "$(adb shell getprop sys.boot_completed | tr -d '\r')" = "1"
    gradle :app:connectedDebugAndroidTest --no-daemon --stacktrace --max-workers=2 -PallowMissingGoogleServices=true
  else
    exit "$result"
  fi
fi
