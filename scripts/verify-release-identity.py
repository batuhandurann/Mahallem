"""Fail closed on unapproved Firebase/package/upload certificate identity."""
import hashlib
import json
import os
from pathlib import Path
import re
import ssl
import subprocess
import sys
import zipfile


def approved_value(env, name):
    value = env.get(name, "").strip()
    if not value:
        raise ValueError(f"Missing protected approval: {name}")
    return value


def validate_config(config, application_id, project_id, approved_app):
    if project_id.startswith("demo-") or approved_app.endswith(".staging"):
        raise ValueError("Production approvals cannot target demo/staging")
    if application_id != approved_app:
        raise ValueError("Build applicationId differs from approved production applicationId")
    if config.get("project_info", {}).get("project_id") != project_id:
        raise ValueError("Firebase project differs from approved production project")
    clients = config.get("client", [])
    if not any(c.get("client_info", {}).get("android_client_info", {}).get("package_name") == approved_app for c in clients):
        raise ValueError("Firebase config has no approved Android client")


def normalized_fingerprint(value):
    value = value.replace(":", "").upper()
    if not re.fullmatch(r"[0-9A-F]{64}", value):
        raise ValueError("Approved upload certificate must be a SHA-256 fingerprint")
    return value


def certificate_fingerprint(output):
    match = re.search(r"-----BEGIN CERTIFICATE-----.*?-----END CERTIFICATE-----", output, re.S)
    if not match:
        raise ValueError("No signing certificate found")
    return hashlib.sha256(ssl.PEM_cert_to_DER_cert(match.group())).hexdigest().upper()


def verify_signed_archive(aab, keystore, alias, expected_fingerprint, env):
    expected_fingerprint = normalized_fingerprint(expected_fingerprint)
    if not env.get("STORE_PASSWORD"):
        raise ValueError("STORE_PASSWORD is required for protected keystore verification")
    # Passwords are referenced through the environment, never argv or output.
    exported = subprocess.check_output([
        "keytool", "-exportcert", "-rfc", "-keystore", str(keystore),
        "-alias", alias, "-storepass:env", "STORE_PASSWORD",
    ], env=env, text=True, stderr=subprocess.PIPE)
    if certificate_fingerprint(exported) != expected_fingerprint:
        raise ValueError("Upload keystore certificate differs from Play-approved SHA-256")
    with zipfile.ZipFile(aab) as archive:
        blocks = [n for n in archive.namelist() if re.fullmatch(r"META-INF/[^/]+\.(RSA|DSA|EC)", n, re.I)]
        if len(blocks) != 1:
            raise ValueError("AAB must have exactly one signing block")
    signed_cert = subprocess.check_output([
        "keytool", "-printcert", "-rfc", "-jarfile", str(aab),
    ], env=env, text=True, stderr=subprocess.PIPE)
    if certificate_fingerprint(signed_cert) != expected_fingerprint:
        raise ValueError("AAB signer differs from Play-approved upload certificate")
    # Trust the approved upload key so valid self-signed upload certificates do
    # not fail solely because they are not issued by a public certificate CA.
    subprocess.run([
        "jarsigner", "-verify", "-strict", "-keystore", str(keystore),
        "-storepass:env", "STORE_PASSWORD", str(aab),
    ], env=env, check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    return expected_fingerprint


def main():
    if len(sys.argv) != 2 or sys.argv[1] not in {"config", "signed"}:
        raise ValueError("Usage: verify-release-identity.py {config|signed}")
    root = Path(__file__).resolve().parents[1]
    env = dict(os.environ)
    project = approved_value(env, "APPROVED_FIREBASE_PROJECT_ID")
    app_id = approved_value(env, "APPROVED_ANDROID_APPLICATION_ID")
    fingerprint = normalized_fingerprint(approved_value(env, "APPROVED_UPLOAD_CERT_SHA256"))
    gradle = (root / "app/build.gradle.kts").read_text()
    match = re.search(r'applicationId\s*=\s*"([^"]+)"', gradle)
    if not match:
        raise ValueError("Cannot determine production build applicationId")
    config = json.loads((root / "app/google-services.json").read_text())
    validate_config(config, match.group(1), project, app_id)
    if sys.argv[1] == "signed":
        aab = root / "app/build/outputs/bundle/release/app-release.aab"
        verify_signed_archive(aab, Path(approved_value(env, "KEYSTORE_PATH")),
                              approved_value(env, "KEY_ALIAS"), fingerprint, env)
        report = {
            "firebase_project_id": project, "application_id": app_id,
            "upload_certificate_sha256": fingerprint,
            "aab_sha256": hashlib.sha256(aab.read_bytes()).hexdigest(),
            "source_commit": subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=root, text=True).strip(),
            "workflow_sha": env.get("GITHUB_SHA"),
            "version_code_input": env.get("ANDROID_VERSION_CODE"),
            "version_name_input": env.get("ANDROID_VERSION_NAME"),
            "play_console_verified": False,
        }
        output = root / "app/build/reports/release-identity.json"
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(json.dumps(report, indent=2) + "\n")
    print("Approved production identity checks passed (Play Console state remains a separate verification).")


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError, subprocess.SubprocessError, zipfile.BadZipFile) as error:
        # Do not print captured keytool output or protected configuration.
        sys.exit(f"Release identity verification failed: {type(error).__name__}: "
                 + (str(error) if isinstance(error, ValueError) else "protected operation failed"))
