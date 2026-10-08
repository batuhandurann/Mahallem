#!/usr/bin/env bash
# Run on a CI Android emulator after connectedDebugAndroidTest; no production Firebase access.
set -euo pipefail
package="com.batuhanduran.burada"
activity="$package/.MainActivity"
adb wait-for-device
adb logcat -c

launch_and_require_pid() {
  adb shell am start -W -n "$activity" > /dev/null
  local pid
  pid="$(adb shell pidof "$package" | tr -d '\r' | awk '{print $1}')"
  if [[ -z "$pid" ]]; then
    echo "::error::Android process did not remain alive after launch"
    exit 1
  fi
  printf '%s' "$pid"
}

original_rotation="$(adb shell settings get system accelerometer_rotation | tr -d '\r')"
original_orientation="$(adb shell settings get system user_rotation | tr -d '\r')"
restore_display() {
  adb shell settings put system accelerometer_rotation "$original_rotation" || true
  adb shell settings put system user_rotation "$original_orientation" || true
}
trap restore_display EXIT

first_pid="$(launch_and_require_pid)"
echo "Started $package (PID $first_pid)"

# Background/foreground: the app process must remain reachable.
adb shell input keyevent KEYCODE_HOME
adb shell am start -W -n "$activity" > /dev/null
test -n "$(adb shell pidof "$package" | tr -d '\r')"

# Force a real orientation configuration change, not only ActivityScenario.recreate().
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 1
sleep 2
test -n "$(adb shell pidof "$package" | tr -d '\r')"
adb shell settings put system user_rotation 0
sleep 2
test -n "$(adb shell pidof "$package" | tr -d '\r')"

# A genuine OS force-stop and cold relaunch creates a fresh application process.
adb shell am force-stop "$package"
if [[ -n "$(adb shell pidof "$package" 2>/dev/null | tr -d '\r')" ]]; then
  echo "::error::Android process remained after force-stop"
  exit 1
fi
second_pid="$(launch_and_require_pid)"
if [[ "$first_pid" == "$second_pid" ]]; then
  echo "::error::Force-stop did not create a new application process"
  exit 1
fi
if adb logcat -d -s AndroidRuntime:E | grep -Fq "Process: $package,"; then
  echo "::error::AndroidRuntime crash reported for $package"
  adb logcat -d -s AndroidRuntime:E | tail -n 80
  exit 1
fi
echo "Passed: foreground, rotation, actual process death, cold relaunch, no observed app crash."
