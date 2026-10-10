#!/usr/bin/env python3
"""Check production config and verify release artifacts against the supplied upload key.

Passwords stay in the environment; reports contain only public certificate fingerprints
and artifact checksums. No signing key or Firebase registration is created here.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import zipfile

PACKAGE = "com.batuhanduran.burada"


def validate_config(config):
    info = config.get("project_info", {})
    project_id = info.get("project_id", "")
    project_number = str(info.get("project_number", ""))
    if not re.fullmatch(r"[a-z][a-z0-9-]{4,28}[a-z0-9]", project_id) or project_id.startswith("demo-"):
        raise ValueError("Production requires a real Firebase project ID")
    if not project_number.isdigit():
        raise ValueError("Production requires a Firebase project number")
    clients = [c for c in config.get("client", []) if c.get("client_info", {}).get("android_client_info", {}).get("package_name") == PACKAGE]
    if len(clients) != 1:
        raise ValueError("Production config must contain exactly one canonical Android registration")
    client = clients[0]
    app_id = client.get("client_info", {}).get("mobilesdk_app_id", "")
    if not re.fullmatch(r"1:" + re.escape(project_number) + r":android:[a-fA-F0-9]+", app_id):
        raise ValueError("Firebase Android app ID must belong to the configured project number")
    if not any(re.fullmatch(r"AIza[0-9A-Za-z_-]{35}", entry.get("current_key", "")) for entry in client.get("api_key", [])):
        raise ValueError("Canonical Android registration is missing its Firebase API key")
    return {"package": PACKAGE, "firebase_project_id": project_id, "firebase_app_id": app_id}


def run(command):
    result = subprocess.run(command, stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=False)
    if result.returncode:
        # Tool output can contain config/key metadata; keep CI errors secret-independent.
        raise ValueError("Release verification command failed: " + Path(command[0]).name)
    return result.stdout


def jarsigner_command():
    binary = shutil.which("jarsigner")
    return [binary] if binary else ["java", "-m", "jdk.jartool/sun.security.tools.jarsigner.Main"]


def upload_certificate(keystore, alias):
    return run(["keytool", "-exportcert", "-keystore", str(keystore), "-storepass:env", "STORE_PASSWORD", "-alias", alias])


def verify_aab(path, keystore, alias):
    with zipfile.ZipFile(path) as archive:
        names = [name.upper() for name in archive.namelist()]
        if not any(re.fullmatch(r"META-INF/[^/]+\.SF", name) for name in names) or not any(re.fullmatch(r"META-INF/[^/]+\.(RSA|DSA|EC)", name) for name in names):
            raise ValueError("Release AAB has no signing metadata")
    # Trust the supplied Android upload certificate instead of requiring a public CA.
    # Alias restriction plus strict verification rejects wrong signers and unsigned entries.
    run(jarsigner_command() + ["-verify", "-strict", "-keystore", str(keystore), "-storepass:env", "STORE_PASSWORD", str(path), alias])


def verify_apk(path, apksigner, expected_sha256):
    output = run([str(apksigner), "verify", "--verbose", "--print-certs", str(path)]).decode("utf-8")
    signers = re.findall(r"Signer #[0-9]+ certificate SHA-256 digest: ([0-9a-fA-F]+)", output)
    if len(signers) != 1 or signers[0].lower() != expected_sha256:
        raise ValueError("Release APK signer does not match the supplied upload certificate")


def verified_checkout_provenance():
    """Bind release evidence to a clean checked-out commit, not just artifact hashes."""
    sha = run(["git", "rev-parse", "HEAD"]).decode("ascii").strip()
    if not re.fullmatch(r"[0-9a-f]{40}", sha):
        raise ValueError("Release source revision must be a full Git SHA")
    # Refuse a claimed source revision when tracked sources changed after checkout.
    # Ignored build outputs and protected (untracked) Firebase config are unaffected.
    run(["git", "diff", "--quiet", "HEAD", "--"])
    is_actions = os.environ.get("GITHUB_ACTIONS") == "true"
    expected = os.environ.get("GITHUB_SHA", "")
    if is_actions:
        if not re.fullmatch(r"[0-9a-f]{40}", expected) or expected != sha:
            raise ValueError("Release checkout does not match GitHub Actions source SHA")
    return {"source_commit_sha": sha, "github_actions_source_verified": is_actions}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--config", default="app/google-services.json")
    parser.add_argument("--validate-config-only", action="store_true")
    parser.add_argument("--aab", default="app/build/outputs/bundle/release/app-release.aab")
    parser.add_argument("--apk", default="app/build/outputs/apk/release/app-release.apk")
    parser.add_argument("--apksigner")
    parser.add_argument("--report", default="app/build/outputs/release-evidence.json")
    args = parser.parse_args()
    try:
        evidence = validate_config(json.loads(Path(args.config).read_text(encoding="utf-8")))
        if args.validate_config_only:
            print("PASS: canonical live Firebase Android registration")
            return 0
        keystore = Path(os.environ["KEYSTORE_PATH"])
        alias = os.environ.get("KEY_ALIAS", "upload")
        if not alias.strip() or not os.environ.get("STORE_PASSWORD"):
            raise ValueError("Upload key alias and store password are required")
        certificate = upload_certificate(keystore, alias)
        certificate_sha256 = hashlib.sha256(certificate).hexdigest()
        aab, apk = Path(args.aab), Path(args.apk)
        verify_aab(aab, keystore, alias)
        apksigner = args.apksigner or str(Path(os.environ["ANDROID_HOME"]) / "build-tools/36.0.0/apksigner")
        verify_apk(apk, apksigner, certificate_sha256)
        evidence.update(verified_checkout_provenance())
        evidence.update(upload_certificate_sha1=hashlib.sha1(certificate).hexdigest(), upload_certificate_sha256=certificate_sha256)
        evidence["artifacts"] = [{"file": path.name, "sha256": hashlib.sha256(path.read_bytes()).hexdigest()} for path in (aab, apk)]
        evidence["scope"] = "Upload-key signed APK/AAB; not Play App Signing or physical-device attestation"
        report = Path(args.report)
        report.parent.mkdir(parents=True, exist_ok=True)
        report.write_text(json.dumps(evidence, indent=2) + "\n", encoding="utf-8")
        print("PASS: signed APK and AAB match the protected upload certificate; checksums recorded")
        return 0
    except (OSError, ValueError, KeyError, TypeError, AttributeError, zipfile.BadZipFile):
        print("FAIL: release config/signature verification failed; check protected config, key alias and signing inputs", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
