#!/usr/bin/env python3
"""Read-only local release prerequisites. Never claims live service verification.

Does not build, install, sign, send SMS, send push, or write to Firebase.
Only presence/status information is emitted; credentials and device IDs are omitted.
"""
import argparse
import json
import os
from pathlib import Path
import subprocess

from physical_android_smoke import adb_program, choose_device
from verify_signed_release import validate_config

ROOT = Path(__file__).resolve().parents[1]


def signing_inputs(environment):
    names = ("KEYSTORE_PATH", "STORE_PASSWORD", "KEY_PASSWORD", "KEY_ALIAS")
    checks = {name: bool(environment.get(name, "").strip()) for name in names}
    try:
        checks["KEYSTORE_FILE"] = bool(checks["KEYSTORE_PATH"] and Path(environment["KEYSTORE_PATH"]).is_file())
    except OSError:
        checks["KEYSTORE_FILE"] = False
    return checks


def inspect(config, adb=None):
    checks = signing_inputs(os.environ)
    try:
        validate_config(json.loads(config.read_text(encoding="utf-8")))
        config_status = "PRESENT_VALID_STRUCTURE"
    except (OSError, ValueError, TypeError, AttributeError):
        config_status = "MISSING_OR_INVALID"
    device_status = "ADB_UNAVAILABLE"
    binary = adb_program(adb)
    if binary:
        try:
            result = subprocess.run([binary, "devices"], capture_output=True, text=True, check=True, timeout=15)
            serial = choose_device(result.stdout, os.environ.get("ANDROID_SERIAL"))
            def prop(name):
                return subprocess.run([binary, "-s", serial, "shell", "getprop", name],
                                      capture_output=True, text=True, check=True, timeout=15).stdout.strip()
            virtual = prop("ro.kernel.qemu") == "1" or prop("ro.boot.qemu") == "1"
            device_status = "UNSUPPORTED_OR_VIRTUAL" if virtual or int(prop("ro.build.version.sdk")) < 24 else "AUTHORIZED_PHYSICAL_DEVICE"
        except ValueError:
            device_status = "NO_UNAMBIGUOUS_SUPPORTED_PHYSICAL_DEVICE"
        except (OSError, subprocess.SubprocessError):
            device_status = "ADB_CHECK_FAILED"
    ready = config_status == "PRESENT_VALID_STRUCTURE" and all(checks.values()) and device_status == "AUTHORIZED_PHYSICAL_DEVICE"
    return {"status": "LOCAL_INPUTS_PRESENT" if ready else "BLOCKED",
            "firebaseConfig": config_status, "signingInputPresence": checks, "physicalDevice": device_status,
            "verification": {name: "NOT_VERIFIED" for name in
                             ("signingCredentials", "signedArtifacts", "cloudPermissions", "appCheckEnforcement", "smsDelivery", "pushDelivery", "payments")}}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--config", type=Path, default=ROOT / "app/google-services.json")
    parser.add_argument("--adb")
    args = parser.parse_args()
    report = inspect(args.config, args.adb)
    print(json.dumps(report, indent=2))
    return 0 if report["status"] == "LOCAL_INPUTS_PRESENT" else 2


if __name__ == "__main__":
    raise SystemExit(main())
