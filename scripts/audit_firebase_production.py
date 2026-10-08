#!/usr/bin/env python3
"""Validate downloaded app config and read-only Cloud API evidence; never output keys/tokens.

Cloud evidence is a JSON object containing the unmodified API responses described in
FIREBASE_SECURITY_RELEASE.md. Passing this audit is configuration evidence, not an
end-to-end attestation or proof that a physical device was tested.
"""
import argparse
import json
from pathlib import Path
import re
import sys

PACKAGE = "com.batuhanduran.burada"
SERVICES = {"firestore.googleapis.com", "firebasestorage.googleapis.com", "identitytoolkit.googleapis.com"}
REQUIRED_APIS = {"identitytoolkit.googleapis.com", "securetoken.googleapis.com", "firebaseappcheck.googleapis.com", "firestore.googleapis.com",
                 "firebaseinstallations.googleapis.com", "fcmregistrations.googleapis.com"}


def audit(config, cloud=None):
    errors = []
    info = config.get("project_info", {})
    project_id = info.get("project_id", "")
    project_number = str(info.get("project_number", ""))
    if not project_id or project_id.startswith("demo-"):
        errors.append("A real Firebase project is required")
    if not project_number.isdigit():
        errors.append("Firebase project_number is missing")
    clients = [c for c in config.get("client", []) if c.get("client_info", {}).get("android_client_info", {}).get("package_name") == PACKAGE]
    if not clients:
        errors.append("Firebase config does not register the canonical Android package")
    if cloud is None:
        errors.append("Cloud evidence missing: enforcement, Play Integrity and API restrictions are unverified")
        return errors
    app_ids = {c.get("client_info", {}).get("mobilesdk_app_id") for c in clients}
    integrity = cloud.get("playIntegrityConfig", {})
    expected_names = {f"projects/{project_number}/apps/{app_id}/playIntegrityConfig" for app_id in app_ids}
    if integrity.get("name") not in expected_names:
        errors.append("Play Integrity registration belongs to a different Firebase Android app")
    ttl = integrity.get("tokenTtl", "3600s")
    if not re.fullmatch(r"[0-9]+(?:\.[0-9]{1,9})?s", ttl) or not 1800 <= float(ttl[:-1]) <= 604800:
        errors.append("Play Integrity token TTL must be between 30 minutes and 7 days")
    if integrity.get("appIntegrity", {}).get("allowUnrecognizedVersion", False):
        errors.append("Play Store release policy requires recognized application versions")
    modes = {}
    for service in cloud.get("appCheckServices", {}).get("services", []):
        name = service.get("name", "")
        if name.startswith(f"projects/{project_number}/services/"):
            modes[name.rsplit("/", 1)[-1]] = service.get("enforcementMode")
    for service in sorted(SERVICES):
        if modes.get(service) != "ENFORCED":
            errors.append(f"App Check enforcement not verified for {service}")
    key = cloud.get("androidApiKey", {})
    if not key.get("name", "").startswith(f"projects/{project_number}/locations/global/keys/"):
        errors.append("Android API key evidence belongs to a different project")
    configured_keys = {entry.get("current_key") for client in clients for entry in client.get("api_key", [])}
    exported_key = cloud.get("androidApiKeyString", {}).get("keyString")
    if not exported_key or exported_key not in configured_keys:
        errors.append("Audited API key is not the key used by the Android Firebase config")
    restrictions = key.get("restrictions", {})
    allowed = restrictions.get("androidKeyRestrictions", {}).get("allowedApplications", [])
    if not allowed or any(a.get("packageName") != PACKAGE or not re.fullmatch(r"[A-Fa-f0-9]{40}|(?:[A-Fa-f0-9]{2}:){19}[A-Fa-f0-9]{2}", a.get("sha1Fingerprint", "")) for a in allowed):
        errors.append("Android API key requires only the canonical package and valid signing SHA-1 fingerprints")
    targets = restrictions.get("apiTargets", [])
    names = {target.get("service") for target in targets}
    if not REQUIRED_APIS.issubset(names):
        errors.append("Android API allowlist is missing one or more required Firebase APIs")
    if "*" in names or any(not target.get("service") for target in targets):
        errors.append("Android API allowlist contains an unrestricted target")
    # Review other used Firebase APIs (for example Storage) rather than silently removing them.
    return errors


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--config", default="app/google-services.json")
    parser.add_argument("--cloud-evidence", help="Private JSON containing App Check services, Play Integrity config and Android API key restriction responses")
    args = parser.parse_args()
    try:
        config = json.loads(Path(args.config).read_text())
        cloud = json.loads(Path(args.cloud_evidence).read_text()) if args.cloud_evidence else None
        errors = audit(config, cloud)
    except (OSError, ValueError, TypeError, AttributeError):
        print("FAIL: configuration/evidence could not be read as valid JSON", file=sys.stderr)
        return 1
    for error in errors:
        print(f"FAIL: {error}", file=sys.stderr)
    if errors:
        return 1
    print("PASS: app registration, App Check enforcement and Android API key restrictions match the supplied Cloud evidence")
    print("Physical-device Play Integrity, abuse quotas and backend deployment still require separate verification")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
