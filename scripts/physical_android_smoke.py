#!/usr/bin/env python3
"""Run Android Auth/Rules and lifecycle tests on one attached physical phone.

Backend data stays in demo-mahallem emulators. This does not prove production
Play Integrity or FCM delivery. Missing devices yield NOT_RUN, never PASS.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shlex
import shutil
import socket
import subprocess
import sys
from datetime import datetime, timezone

ROOT = Path(__file__).resolve().parents[1]
EMULATOR_PORTS = (8080, 9099, 5001)


def require_demo_emulators():
    if os.environ.get("GCLOUD_PROJECT") != "demo-mahallem" or not all(os.environ.get(name) for name in
            ("FIREBASE_AUTH_EMULATOR_HOST", "FIRESTORE_EMULATOR_HOST")):
        raise ValueError("Refusing tests without the isolated demo Firebase emulator environment")
    # Phone/trust instrumentation now calls the local callable backend as well.
    # The CLI does not export a standard Functions host variable for this SDK.
    try:
        with socket.create_connection(("127.0.0.1", 5001), timeout=3):
            pass
    except OSError:
        raise ValueError("Functions emulator on localhost:5001 is required for phone/trust tests") from None


def choose_device(output, requested=None):
    rows = [line.split() for line in output.splitlines()]
    devices = {row[0]: row[1] for row in rows if len(row) >= 2 and row[1] in {"device", "unauthorized", "offline"}}
    if requested:
        if devices.get(requested) != "device":
            raise ValueError("Selected phone is missing, offline or has not authorized USB debugging")
        candidates = [requested]
    else:
        candidates = [serial for serial, state in devices.items() if state == "device" and not serial.startswith("emulator-")]
    if len(candidates) != 1:
        raise ValueError("Connect and authorize one physical phone, or select it with --serial")
    if candidates[0].startswith("emulator-"):
        raise ValueError("An Android emulator cannot be reported as a physical phone")
    return candidates[0]


def successful_test_count(output):
    if any(marker in output for marker in ("FAILURES!!!", "INSTRUMENTATION_FAILED", "INSTRUMENTATION_ABORTED", "Process crashed.")):
        raise ValueError("Android instrumentation failed or aborted")
    match = re.search(r"(?:^|\n)OK \((\d+) tests?\)", output)
    if not match or int(match.group(1)) < 1:
        raise ValueError("No successful Android test result was produced")
    return int(match.group(1))


def execute(args, **kwargs):
    return subprocess.run(args, cwd=ROOT, check=True, text=True, **kwargs)


def adb_program(explicit):
    if explicit:
        return explicit
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    candidate = Path(sdk) / "platform-tools" / ("adb.exe" if os.name == "nt" else "adb") if sdk else None
    return str(candidate) if candidate and candidate.is_file() else shutil.which("adb")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", default=os.environ.get("ANDROID_SERIAL"))
    parser.add_argument("--adb")
    parser.add_argument("--preflight-only", action="store_true")
    parser.add_argument("--output", default="build/evidence/physical-device.json")
    parser.add_argument("--child", action="store_true", help=argparse.SUPPRESS)
    args = parser.parse_args()
    report = {"status": "NOT_RUN", "backend": "demo-mahallem emulators", "productionAttestation": "NOT_TESTED",
              "productionPush": "NOT_TESTED", "timeUtc": datetime.now(timezone.utc).isoformat()}
    output = Path(args.output)
    if not output.is_absolute():
        output = ROOT / output
    try:
        adb = adb_program(args.adb)
        if not adb:
            raise ValueError("Android SDK platform-tools/adb is required")
        serial = choose_device(execute([adb, "devices"], capture_output=True).stdout, args.serial)
        def device(*command):
            return execute([adb, "-s", serial, *command], capture_output=True).stdout.strip()
        if device("shell", "getprop", "ro.kernel.qemu") == "1" or device("shell", "getprop", "ro.boot.qemu") == "1":
            raise ValueError("The selected device is virtual; physical-device evidence is required")
        api = int(device("shell", "getprop", "ro.build.version.sdk"))
        if api < 24:
            raise ValueError("The app requires Android API 24 or newer")
        report.update(deviceIdHash=hashlib.sha256(serial.encode()).hexdigest(), androidApi=api,
                      model=device("shell", "getprop", "ro.product.model"))
        if args.preflight_only:
            report["reason"] = "Physical phone preflight passed; no application tests were run"
            return 0
        if not args.child:
            execute(["npm", "ci"])
            execute(["npm", "ci", "--prefix", "functions"])
            child = [sys.executable, str(Path(__file__).resolve()), "--child", "--adb", adb,
                     "--serial", serial, "--output", str(output)]
            # Firebase sets emulator-host variables only within this subprocess.
            output.unlink(missing_ok=True)
            result = subprocess.run(["npx", "--no-install", "firebase", "emulators:exec", "--project", "demo-mahallem",
                                     "--only", "auth,firestore,functions", shlex.join(child)], cwd=ROOT)
            if not output.exists():
                report.update(status="FAILED", reason="Emulator runner failed before Android tests started")
            else:
                report = json.loads(output.read_text())
            if result.returncode != 0 or report.get("status") != "PASS":
                report.update(status="FAILED", reason=report.get("reason", "Physical Android test runner failed"))
                return 1
            return 0
        require_demo_emulators()
        # Restrict every adb operation, including the existing lifecycle script, to this phone.
        os.environ["ANDROID_SERIAL"] = serial
        os.environ["PATH"] = str(Path(adb).resolve().parent) + os.pathsep + os.environ.get("PATH", "")
        mappings = device("reverse", "--list").splitlines()
        added = []
        try:
            for port in EMULATOR_PORTS:
                local = f"tcp:{port}"
                existing = [line.split() for line in mappings if local in line.split()[1:2]]
                if existing and any(row[-1] != local for row in existing):
                    raise ValueError("Existing adb reverse mapping conflicts with an emulator port")
                if not existing:
                    device("reverse", local, local)
                    added.append(local)
            report["status"] = "FAILED"
            execute(["./gradlew", "--no-daemon", "-PfirebaseEmulators=true", "-PemulatorHost=127.0.0.1",
                     ":app:assembleDebug", ":app:assembleDebugAndroidTest"])
            for apk in ("app/build/outputs/apk/debug/app-debug.apk", "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"):
                device("install", "-r", str(ROOT / apk))
            result = device("shell", "am", "instrument", "-w", "-r",
                            "com.batuhanduran.burada.test/androidx.test.runner.AndroidJUnitRunner")
            report["instrumentationTestsPassed"] = successful_test_count(result)
            execute(["bash", "scripts/adb-process-death-smoke.sh"])
            report.update(status="PASS", lifecycleSmoke="PASS")
            return 0
        finally:
            for local in added:
                subprocess.run([adb, "-s", serial, "reverse", "--remove", local], capture_output=True, text=True)
    except (ValueError, OSError, subprocess.CalledProcessError, json.JSONDecodeError) as error:
        # Do not print subprocess command lines or private signing/cloud credentials.
        report["reason"] = str(error) if isinstance(error, ValueError) else "A required device/build command failed"
        return 2 if report["status"] == "NOT_RUN" else 1
    finally:
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(json.dumps(report, indent=2) + "\n")
        print(json.dumps(report))


if __name__ == "__main__":
    sys.exit(main())
