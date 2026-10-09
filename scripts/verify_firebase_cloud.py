#!/usr/bin/env python3
"""Read live Firebase release evidence using explicit ADC; print no credentials.

Only the redacted result is written. Raw API responses (which can contain API keys,
Auth test phone numbers or SMTP secrets) remain in memory and are never artifacts.
"""
import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
from urllib.error import HTTPError, URLError
from urllib.parse import quote, urlencode, urlsplit
from urllib.request import HTTPRedirectHandler, Request, build_opener

from audit_firebase_production import PACKAGE, audit

HOSTS = {"firebase.googleapis.com", "firebaseappcheck.googleapis.com", "apikeys.googleapis.com",
         "identitytoolkit.googleapis.com", "firestore.googleapis.com", "storage.googleapis.com",
         "firebaserules.googleapis.com", "cloudfunctions.googleapis.com"}
FUNCTIONS = {"getModerationQueue", "reviewReport", "uploadConversationPhoto",
             "readConversationPhoto", "notifyConversationMessage", "getListingTrust"}


class VerificationError(Exception):
    """Messages contain only static labels, never a Cloud response or token."""


def require(condition, message):
    if not condition:
        raise VerificationError(message)


def fingerprint(value, byte_count):
    """Accept only the documented hex/colon certificate formats; never echo input."""
    require(isinstance(value, str) and re.fullmatch(
        rf"[A-Fa-f0-9]{{{byte_count * 2}}}|(?:[A-Fa-f0-9]{{2}}:){{{byte_count - 1}}}[A-Fa-f0-9]{{2}}",
        value) is not None, "Explicit distribution signing fingerprint is missing/malformed")
    return value.replace(":", "").upper()


def validate_inputs(config, firebase, project_id, key_id):
    require(isinstance(project_id, str) and re.fullmatch(r"[a-z][a-z0-9-]{4,28}[a-z0-9]", project_id)
            and not project_id.startswith("demo-"), "Explicit real project ID required")
    info = config.get("project_info", {})
    require(info.get("project_id") == project_id, "Firebase Android config project mismatch")
    number = str(info.get("project_number", ""))
    require(number.isdigit(), "Firebase project number required")
    require(isinstance(key_id, str) and re.fullmatch(r"[A-Za-z0-9_-]{1,128}", key_id), "Explicit API key resource ID required")
    clients = [c for c in config.get("client", []) if c.get("client_info", {}).get("android_client_info", {}).get("package_name") == PACKAGE]
    require(len(clients) == 1, "Exactly one canonical Android app registration required")
    app_id = clients[0].get("client_info", {}).get("mobilesdk_app_id", "")
    require(re.fullmatch(r"1:" + number + r":android:[a-fA-F0-9]+", app_id) is not None, "Android app ID/project number mismatch")
    bucket = info.get("storage_bucket", "")
    require(isinstance(bucket, str) and re.fullmatch(r"[a-z0-9][a-z0-9._-]{1,220}[a-z0-9]", bucket), "Explicit Storage bucket required")
    fs = firebase.get("firestore", {})
    require(isinstance(fs, dict) and fs.get("database") == "mahallem" and fs.get("location") == "europe-west3", "Deployment must target named mahallem database in europe-west3")
    require(fs.get("rules") == "firestore.rules" and fs.get("indexes") == "firestore.indexes.json", "Unexpected Firestore deployment files")
    require(firebase.get("storage", {}).get("rules") == "storage.rules", "Unexpected Storage deployment file")
    require(firebase.get("functions") == [{"source": "functions", "codebase": "trusted-backend", "runtime": "nodejs22"}], "Unexpected trusted Functions deployment target")
    return number, app_id, bucket


def validate_adc(path, project_id):
    require(bool(path), "GOOGLE_APPLICATION_CREDENTIALS must explicitly select authorized ADC")
    try:
        cred = json.loads(Path(path).read_text())
    except (OSError, ValueError):
        raise VerificationError("Explicit ADC file is missing or invalid") from None
    if cred.get("type") == "service_account":
        require(cred.get("project_id") == project_id, "ADC service account belongs to a different project")
        email = cred.get("client_email", "")
    elif cred.get("type") == "external_account":
        # Only service-account impersonation in this project; no ambient user login.
        url = cred.get("service_account_impersonation_url", "")
        prefix = "https://iamcredentials.googleapis.com/v1/projects/-/serviceAccounts/"
        require(url.startswith(prefix) and url.endswith(":generateAccessToken"), "WIF ADC must impersonate an explicit project service account")
        email = url[len(prefix):-len(":generateAccessToken")]
    else:
        raise VerificationError("Use explicit service-account or WIF ADC; ambient user credentials are refused")
    require(re.fullmatch(r"[a-z0-9-]+@" + re.escape(project_id) + r"\.iam\.gserviceaccount\.com", email) is not None,
            "ADC principal does not belong to the requested project")


def access_token():
    try:
        result = subprocess.run(["gcloud", "auth", "application-default", "print-access-token", "--quiet"],
                                check=True, capture_output=True, text=True, timeout=90)
    except (OSError, subprocess.SubprocessError):
        raise VerificationError("Could not obtain access token from explicit ADC") from None
    token = result.stdout.strip()
    require(bool(token) and not any(c.isspace() for c in token), "ADC returned no valid access token")
    return token


class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        raise VerificationError("Cloud API redirect refused")


class CloudReader:
    def __init__(self, token):
        self.token = token
        self.opener = build_opener(NoRedirect())

    def get(self, url):
        parsed = urlsplit(url)
        require(parsed.scheme == "https" and parsed.hostname in HOSTS and parsed.port is None
                and parsed.username is None and parsed.password is None and not parsed.fragment,
                "Untrusted Cloud API endpoint refused")
        req = Request(url, headers={"Authorization": "Bearer " + self.token, "Accept": "application/json"})
        try:
            with self.opener.open(req, timeout=45) as response:
                data = response.read(8 * 1024 * 1024 + 1)
            require(len(data) <= 8 * 1024 * 1024, "Cloud API response exceeds evidence limit")
            value = json.loads(data)
            require(isinstance(value, dict), "Cloud API response must be a JSON object")
            return value
        except HTTPError as error:
            raise VerificationError(f"Cloud API request failed (HTTP {error.code}); check IAM/API enablement") from None
        except (URLError, TimeoutError, ValueError):
            raise VerificationError("Cloud API evidence request failed") from None


def list_all(reader, url, field):
    values, seen, token = [], set(), ""
    for _ in range(100):
        page = reader.get(url + (("&" if "?" in url else "?") + urlencode({"pageToken": token}) if token else ""))
        items = page.get(field, [])
        require(isinstance(items, list), "Cloud API list response malformed")
        values.extend(items)
        token = page.get("nextPageToken", "")
        if not token:
            return {field: values}
        require(isinstance(token, str) and token not in seen, "Cloud API pagination cycle refused")
        seen.add(token)
    raise VerificationError("Cloud API pagination limit exceeded")


def collect(reader, config, project_id, number, app_id, bucket, key_id, signing_sha1, signing_sha256):
    expected_sha1 = fingerprint(signing_sha1, 20)
    expected_sha256 = fingerprint(signing_sha256, 32)
    project = reader.get(f"https://firebase.googleapis.com/v1beta1/projects/{project_id}")
    require(project.get("projectId") == project_id and str(project.get("projectNumber")) == number, "Live Firebase project identity mismatch")
    app = reader.get(f"https://firebase.googleapis.com/v1beta1/projects/{project_id}/androidApps/{app_id}")
    require(app.get("appId") == app_id and app.get("packageName") == PACKAGE and app.get("state") == "ACTIVE", "Live canonical Android registration is missing/inactive")
    sha = list_all(reader, f"https://firebase.googleapis.com/v1beta1/projects/{project_id}/androidApps/{app_id}/sha", "certificates")
    registered_sha1 = {fingerprint(c.get("shaHash"), 20) for c in sha["certificates"] if c.get("certType") == "SHA_1"}
    registered_sha256 = {fingerprint(c.get("shaHash"), 32) for c in sha["certificates"] if c.get("certType") == "SHA_256"}
    require(expected_sha256 in registered_sha256, "Intended distribution signing SHA-256 registration is missing")
    require(expected_sha1 in registered_sha1, "Intended distribution signing SHA-1 registration is missing")
    admin = reader.get(f"https://firebase.googleapis.com/v1beta1/projects/{project_id}/adminSdkConfig")
    require(admin.get("projectId") == project_id and admin.get("storageBucket") == bucket, "Android and trusted backend default Storage buckets mismatch")
    db = reader.get(f"https://firestore.googleapis.com/v1/projects/{project_id}/databases/mahallem")
    require(db.get("name") == f"projects/{project_id}/databases/mahallem" and db.get("locationId") == "europe-west3"
            and db.get("type") == "FIRESTORE_NATIVE", "Live named Firestore database identity/location/mode mismatch")
    evidence = {
        "appCheckServices": list_all(reader, f"https://firebaseappcheck.googleapis.com/v1/projects/{number}/services", "services"),
        "playIntegrityConfig": reader.get(f"https://firebaseappcheck.googleapis.com/v1/projects/{number}/apps/{app_id}/playIntegrityConfig"),
        "androidApiKey": reader.get(f"https://apikeys.googleapis.com/v2/projects/{number}/locations/global/keys/{key_id}"),
        "androidApiKeyString": reader.get(f"https://apikeys.googleapis.com/v2/projects/{number}/locations/global/keys/{key_id}/keyString"),
    }
    errors = audit(config, evidence)
    require(not errors, "; ".join(errors))
    allowed_apps = evidence["androidApiKey"]["restrictions"]["androidKeyRestrictions"]["allowedApplications"]
    allowed_sha1 = {fingerprint(a.get("sha1Fingerprint"), 20) for a in allowed_apps}
    require(expected_sha1 in allowed_sha1 and allowed_sha1.issubset(registered_sha1),
            "Android API restrictions do not match registered/intended signing SHA-1 certificates")
    auth = reader.get(f"https://identitytoolkit.googleapis.com/admin/v2/projects/{project_id}/config")
    require(auth.get("name") == f"projects/{project_id}/config", "Auth configuration project mismatch")
    require(auth.get("emailPrivacyConfig", {}).get("enableImprovedEmailPrivacy") is True, "Auth email enumeration protection is not enabled")
    policy = auth.get("passwordPolicyConfig", {})
    versions = policy.get("passwordPolicyVersions", [])
    require(policy.get("passwordPolicyEnforcementState") == "ENFORCE" and len(versions) == 1
            and versions[0].get("customStrengthOptions", {}).get("minPasswordLength", 0) >= 8,
            "Auth must enforce a password policy of at least 8 characters")
    require(auth.get("signIn", {}).get("email", {}).get("enabled") is True, "Email Authentication is not enabled")
    phone = auth.get("signIn", {}).get("phoneNumber", {})
    require(phone.get("enabled") is True, "Phone Authentication must be enabled for SMS linking")
    test_numbers = phone.get("testPhoneNumbers", {})
    require(isinstance(test_numbers, dict) and not test_numbers, "Production Phone Authentication must not contain test numbers/codes")
    sms = auth.get("smsRegionConfig", {})
    require(isinstance(sms, dict) and "allowByDefault" not in sms
            and sms.get("allowlistOnly", {}).get("allowedRegions") == ["TR"],
            "SMS region policy must allowlist only TR")
    bucket_info = reader.get(f"https://storage.googleapis.com/storage/v1/b/{quote(bucket, safe='')}")
    require(bucket_info.get("name") == bucket and str(bucket_info.get("projectNumber")) == number, "Storage bucket belongs to a different project")
    require(bucket_info.get("iamConfiguration", {}).get("publicAccessPrevention") == "enforced", "Private media bucket must enforce public access prevention")
    iam = reader.get(f"https://storage.googleapis.com/storage/v1/b/{quote(bucket, safe='')}/iam")
    require(not any(member in {"allUsers", "allAuthenticatedUsers"} for binding in iam.get("bindings", []) for member in binding.get("members", [])), "Private media bucket has a public IAM binding")


def verify_rules(reader, project_id, release_name, file_path):
    release = reader.get(f"https://firebaserules.googleapis.com/v1/projects/{project_id}/releases/{release_name}")
    name = release.get("rulesetName", "")
    require(re.fullmatch(r"projects/" + re.escape(project_id) + r"/rulesets/[A-Za-z0-9_-]+", name) is not None, "Deployed Rules release belongs to a different project")
    ruleset = reader.get("https://firebaserules.googleapis.com/v1/" + name)
    files = ruleset.get("source", {}).get("files", [])
    expected = Path(file_path).read_text()
    require(len(files) == 1 and files[0].get("content") == expected, "Deployed Rules content differs from this commit")
    return hashlib.sha256(expected.encode()).hexdigest()


def verify_deployed(reader, project_id, bucket, root):
    digests = {
        "firestoreRulesSha256": verify_rules(reader, project_id, "cloud.firestore/mahallem", root / "firestore.rules"),
        "storageRulesSha256": verify_rules(reader, project_id, "firebase.storage/" + bucket, root / "storage.rules"),
    }
    url = f"https://firestore.googleapis.com/v1/projects/{project_id}/databases/mahallem/collectionGroups/reports/indexes"
    indexes = list_all(reader, url, "indexes")["indexes"]
    require(any(i.get("state") == "READY" and i.get("queryScope") == "COLLECTION" and
                [(f.get("fieldPath"), f.get("order")) for f in i.get("fields", []) if f.get("fieldPath") != "__name__"] ==
                [("status", "ASCENDING"), ("createdAt", "ASCENDING")] for i in indexes), "Moderation reports index is not READY")
    for group in ("_abuseBudgets", "_pushDeliveries"):
        field = reader.get(f"https://firestore.googleapis.com/v1/projects/{project_id}/databases/mahallem/collectionGroups/{group}/fields/expiresAt")
        require(field.get("ttlConfig", {}).get("state") == "ACTIVE", "Abuse/delivery cleanup TTL policy is not ACTIVE")
    for function in sorted(FUNCTIONS):
        live = reader.get(f"https://cloudfunctions.googleapis.com/v2/projects/{project_id}/locations/europe-west3/functions/{function}")
        require(live.get("name") == f"projects/{project_id}/locations/europe-west3/functions/{function}" and live.get("state") == "ACTIVE"
                and live.get("buildConfig", {}).get("runtime") == "nodejs22", "Trusted function is missing/inactive or has incorrect runtime")
        service = live.get("serviceConfig", {})
        require(0 < service.get("maxInstanceCount", 0) <= 5, "Trusted function must have a bounded maximum instance count")
        variables = service.get("environmentVariables", {})
        require(variables.get("FUNCTIONS_EMULATOR") != "true" and variables.get("FIRESTORE_DATABASE_ID", "mahallem") == "mahallem",
                "Production function has an unsafe emulator/database setting")
        # Secret-backed overrides are not observable through this read-only API.
        # Do not let an unknown secret turn off App Check or redirect trusted data.
        guard_keys = {"FUNCTIONS_EMULATOR", "FIRESTORE_DATABASE_ID", "FIREBASE_AUTH_EMULATOR_HOST",
                      "FIRESTORE_EMULATOR_HOST", "FIREBASE_STORAGE_EMULATOR_HOST"}
        require(not any(variables.get(key) for key in guard_keys if key.endswith("_EMULATOR_HOST")),
                "Production function points to an emulator service")
        require(not any(secret.get("key") in guard_keys for secret in service.get("secretEnvironmentVariables", [])),
                "Production function has an unobservable secret emulator/database override")
        if function == "notifyConversationMessage":
            require(live.get("eventTrigger", {}).get("eventFilters", {}).get("database") == "mahallem", "Push trigger targets the wrong Firestore database")
    return digests


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--project", required=True)
    parser.add_argument("--key-id", required=True)
    parser.add_argument("--signing-sha1", required=True, help="Intended distribution certificate SHA-1 (Play app signing, not upload key)")
    parser.add_argument("--signing-sha256", required=True, help="Intended distribution certificate SHA-256 (Play app signing, not upload key)")
    parser.add_argument("--config", default="app/google-services.json")
    parser.add_argument("--firebase-config", default="firebase.json")
    parser.add_argument("--report", required=True, help="Redacted result; never raw Cloud evidence")
    parser.add_argument("--deployed", action="store_true", help="Also verify active functions/index/TTL and exact deployed Rules")
    args = parser.parse_args()
    report = {"status": "FAIL", "observedAtUtc": datetime.now(timezone.utc).isoformat(), "verifierMutatesCloud": False,
              "physicalDeviceVerified": False, "pushDeliveryVerified": False, "playConsoleLinkVerified": False,
              "phoneSmsDeliveryVerified": False, "callableAppCheckRejectionVerified": False}
    code = 1
    try:
        config = json.loads(Path(args.config).read_text())
        firebase = json.loads(Path(args.firebase_config).read_text())
        number, app_id, bucket = validate_inputs(config, firebase, args.project, args.key_id)
        fingerprint(args.signing_sha1, 20)
        fingerprint(args.signing_sha256, 32)
        validate_adc(os.environ.get("GOOGLE_APPLICATION_CREDENTIALS"), args.project)
        reader = CloudReader(access_token())
        collect(reader, config, args.project, number, app_id, bucket, args.key_id, args.signing_sha1, args.signing_sha256)
        digests = verify_deployed(reader, args.project, bucket, Path(args.firebase_config).resolve().parent) if args.deployed else {}
        report.update({"status": "PASS", "projectId": args.project, "androidPackage": PACKAGE, "databaseId": "mahallem",
                       "appCheckApiAuthBucketVerified": True, "deploymentVerified": args.deployed, **digests})
        code = 0
    except VerificationError as error:
        report["reason"] = str(error)
    except (OSError, ValueError, TypeError, AttributeError):
        report["reason"] = "Configuration or Cloud evidence is malformed/unreadable"
    path = Path(args.report)
    path.parent.mkdir(parents=True, exist_ok=True)
    # Output has only the fixed allowlisted fields above, no token/key/API payload.
    path.write_text(json.dumps(report, indent=2) + "\n")
    print("PASS: live Firebase configuration verified" if code == 0 else "FAIL: " + report["reason"])
    return code


if __name__ == "__main__":
    raise SystemExit(main())
