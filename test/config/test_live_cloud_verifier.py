import copy
import importlib.util
import json
import os
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch
from urllib.error import HTTPError

ROOT = Path(__file__).parents[2]
sys.path.insert(0, str(ROOT / "scripts"))
spec = importlib.util.spec_from_file_location("live_cloud", ROOT / "scripts/verify_firebase_cloud.py")
cloud = importlib.util.module_from_spec(spec)
spec.loader.exec_module(cloud)


class FakeReader:
    def __init__(self, responses):
        self.responses = responses
        self.urls = []

    def get(self, url):
        self.urls.append(url)
        return copy.deepcopy(self.responses[url])


class LiveCloudTests(unittest.TestCase):
    def setUp(self):
        self.project = "burada-production"
        self.number = "123456"
        self.app_id = "1:123456:android:abcd"
        self.bucket = "burada-production.firebasestorage.app"
        self.config = {"project_info": {"project_id": self.project, "project_number": self.number, "storage_bucket": self.bucket},
                       "client": [{"client_info": {"mobilesdk_app_id": self.app_id, "android_client_info": {"package_name": cloud.PACKAGE}},
                                   "api_key": [{"current_key": "secret-test-key"}]}]}
        self.firebase = json.loads((ROOT / "firebase.json").read_text())
        self.routes = {
            f"https://firebase.googleapis.com/v1beta1/projects/{self.project}": {"projectId": self.project, "projectNumber": self.number},
            f"https://firebase.googleapis.com/v1beta1/projects/{self.project}/androidApps/{self.app_id}": {"appId": self.app_id, "packageName": cloud.PACKAGE, "state": "ACTIVE"},
            f"https://firebase.googleapis.com/v1beta1/projects/{self.project}/androidApps/{self.app_id}/sha": {"certificates": [{"certType": "SHA_256", "shaHash": "AB" * 32}]},
            f"https://firebase.googleapis.com/v1beta1/projects/{self.project}/adminSdkConfig": {"projectId": self.project, "storageBucket": self.bucket},
            f"https://firestore.googleapis.com/v1/projects/{self.project}/databases/mahallem": {"name": f"projects/{self.project}/databases/mahallem", "locationId": "europe-west3", "type": "FIRESTORE_NATIVE"},
            f"https://firebaseappcheck.googleapis.com/v1/projects/{self.number}/services": {"services": [{"name": f"projects/{self.number}/services/{s}", "enforcementMode": "ENFORCED"} for s in cloud.audit.__globals__["SERVICES"]]},
            f"https://firebaseappcheck.googleapis.com/v1/projects/{self.number}/apps/{self.app_id}/playIntegrityConfig": {"name": f"projects/{self.number}/apps/{self.app_id}/playIntegrityConfig"},
            f"https://apikeys.googleapis.com/v2/projects/{self.number}/locations/global/keys/key-id": {"name": f"projects/{self.number}/locations/global/keys/key-id", "restrictions": {"androidKeyRestrictions": {"allowedApplications": [{"packageName": cloud.PACKAGE, "sha1Fingerprint": "AB" * 20}]}, "apiTargets": [{"service": s} for s in cloud.audit.__globals__["REQUIRED_APIS"]]}},
            f"https://apikeys.googleapis.com/v2/projects/{self.number}/locations/global/keys/key-id/keyString": {"keyString": "secret-test-key"},
            f"https://identitytoolkit.googleapis.com/admin/v2/projects/{self.project}/config": {"name": f"projects/{self.project}/config", "emailPrivacyConfig": {"enableImprovedEmailPrivacy": True}, "passwordPolicyConfig": {"passwordPolicyEnforcementState": "ENFORCE", "passwordPolicyVersions": [{"customStrengthOptions": {"minPasswordLength": 8}}]}, "signIn": {"email": {"enabled": True}, "phoneNumber": {"enabled": False, "testPhoneNumbers": {"private-number": "secret-test-code"}}}, "notification": {"sendEmail": {"smtp": {"password": "secret-smtp-password"}}}},
            f"https://storage.googleapis.com/storage/v1/b/{self.bucket}": {"name": self.bucket, "projectNumber": self.number, "iamConfiguration": {"publicAccessPrevention": "enforced"}},
            f"https://storage.googleapis.com/storage/v1/b/{self.bucket}/iam": {"bindings": [{"members": ["serviceAccount:runtime@burada-production.iam.gserviceaccount.com"]}]},
        }

    def collect(self):
        cloud.collect(FakeReader(self.routes), self.config, self.project, self.number, self.app_id, self.bucket, "key-id")

    def test_all_live_guards_pass_matching_fixtures(self):
        self.assertEqual((self.number, self.app_id, self.bucket), cloud.validate_inputs(self.config, self.firebase, self.project, "key-id"))
        self.collect()

    def test_default_database_and_cross_project_config_refused(self):
        self.firebase["firestore"]["database"] = "(default)"
        with self.assertRaises(cloud.VerificationError):
            cloud.validate_inputs(self.config, self.firebase, self.project, "key-id")
        with self.assertRaises(cloud.VerificationError):
            cloud.validate_inputs(self.config, self.firebase, "another-project", "key-id")

    def test_disabled_enumeration_or_password_policy_refused(self):
        auth = self.routes[f"https://identitytoolkit.googleapis.com/admin/v2/projects/{self.project}/config"]
        auth["emailPrivacyConfig"]["enableImprovedEmailPrivacy"] = False
        with self.assertRaisesRegex(cloud.VerificationError, "enumeration"):
            self.collect()
        auth["emailPrivacyConfig"]["enableImprovedEmailPrivacy"] = True
        auth["passwordPolicyConfig"]["passwordPolicyEnforcementState"] = "OFF"
        with self.assertRaisesRegex(cloud.VerificationError, "password"):
            self.collect()

    def test_unused_sms_and_public_bucket_refused(self):
        auth = self.routes[f"https://identitytoolkit.googleapis.com/admin/v2/projects/{self.project}/config"]
        auth["signIn"]["phoneNumber"]["enabled"] = True
        with self.assertRaisesRegex(cloud.VerificationError, "SMS"):
            self.collect()
        auth["signIn"]["phoneNumber"]["enabled"] = False
        self.routes[f"https://storage.googleapis.com/storage/v1/b/{self.bucket}/iam"]["bindings"][0]["members"].append("allUsers")
        with self.assertRaisesRegex(cloud.VerificationError, "public IAM"):
            self.collect()

    def test_public_access_prevention_and_signing_certificate_required(self):
        self.routes[f"https://storage.googleapis.com/storage/v1/b/{self.bucket}"]["iamConfiguration"]["publicAccessPrevention"] = "inherited"
        with self.assertRaisesRegex(cloud.VerificationError, "prevention"):
            self.collect()
        self.routes[f"https://storage.googleapis.com/storage/v1/b/{self.bucket}"]["iamConfiguration"]["publicAccessPrevention"] = "enforced"
        self.routes[f"https://firebase.googleapis.com/v1beta1/projects/{self.project}/androidApps/{self.app_id}/sha"]["certificates"] = []
        with self.assertRaisesRegex(cloud.VerificationError, "SHA-256"):
            self.collect()

    def test_cloud_bucket_and_database_identity_must_match(self):
        self.routes[f"https://storage.googleapis.com/storage/v1/b/{self.bucket}"]["projectNumber"] = "999"
        with self.assertRaisesRegex(cloud.VerificationError, "different project"):
            self.collect()

    def test_adc_requires_explicit_same_project_principal(self):
        with self.assertRaises(cloud.VerificationError):
            cloud.validate_adc(None, self.project)
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "adc.json"
            for value, accepted in [
                ({"type": "authorized_user"}, False),
                ({"type": "service_account", "project_id": "wrong-project", "client_email": "deployer@wrong-project.iam.gserviceaccount.com"}, False),
                ({"type": "service_account", "project_id": self.project, "client_email": "deployer@burada-production.iam.gserviceaccount.com"}, True),
                ({"type": "external_account", "service_account_impersonation_url": "https://iamcredentials.googleapis.com/v1/projects/-/serviceAccounts/deployer@burada-production.iam.gserviceaccount.com:generateAccessToken"}, True),
            ]:
                path.write_text(json.dumps(value))
                if accepted:
                    cloud.validate_adc(str(path), self.project)
                else:
                    with self.assertRaises(cloud.VerificationError):
                        cloud.validate_adc(str(path), self.project)

    def test_pagination_collects_every_page_and_rejects_cycles(self):
        reader = FakeReader({"https://example/page": {"items": [1], "nextPageToken": "x"}, "https://example/page?pageToken=x": {"items": [2]}})
        self.assertEqual({"items": [1, 2]}, cloud.list_all(reader, "https://example/page", "items"))
        reader.responses["https://example/page?pageToken=x"]["nextPageToken"] = "x"
        with self.assertRaises(cloud.VerificationError):
            cloud.list_all(reader, "https://example/page", "items")

    def test_wrong_hosts_redirects_and_cloud_error_body_never_expose_token(self):
        reader = cloud.CloudReader("secret-access-token")
        for url in ("https://evil.example/", "http://firebase.googleapis.com/", "https://firebase.googleapis.com@evil.example/"):
            with self.assertRaises(cloud.VerificationError):
                reader.get(url)
        with self.assertRaises(cloud.VerificationError):
            cloud.NoRedirect().redirect_request(None, None, 302, "", {}, "https://evil.example/")
        with patch.object(reader.opener, "open", side_effect=HTTPError("https://firebase.googleapis.com/", 403, "secret-access-token", {}, None)):
            with self.assertRaises(cloud.VerificationError) as error:
                reader.get("https://firebase.googleapis.com/")
            self.assertNotIn("secret-access-token", str(error.exception))

    def test_deployed_rules_compare_exact_content_and_project(self):
        content = (ROOT / "firestore.rules").read_text()
        release_url = f"https://firebaserules.googleapis.com/v1/projects/{self.project}/releases/cloud.firestore/mahallem"
        name = f"projects/{self.project}/rulesets/abc"
        reader = FakeReader({release_url: {"rulesetName": name}, "https://firebaserules.googleapis.com/v1/" + name: {"source": {"files": [{"content": content}]}}})
        self.assertEqual(64, len(cloud.verify_rules(reader, self.project, "cloud.firestore/mahallem", ROOT / "firestore.rules")))
        reader.responses["https://firebaserules.googleapis.com/v1/" + name]["source"]["files"][0]["content"] = "allow read,write: if true;"
        with self.assertRaisesRegex(cloud.VerificationError, "differs"):
            cloud.verify_rules(reader, self.project, "cloud.firestore/mahallem", ROOT / "firestore.rules")

    def deployed_reader(self):
        routes = {}
        for release, path, identifier in (("cloud.firestore/mahallem", "firestore.rules", "firestore"),
                                          ("firebase.storage/" + self.bucket, "storage.rules", "storage")):
            name = f"projects/{self.project}/rulesets/{identifier}"
            routes[f"https://firebaserules.googleapis.com/v1/projects/{self.project}/releases/{release}"] = {"rulesetName": name}
            routes["https://firebaserules.googleapis.com/v1/" + name] = {"source": {"files": [{"content": (ROOT / path).read_text()}]}}
        prefix = f"https://firestore.googleapis.com/v1/projects/{self.project}/databases/mahallem/collectionGroups/"
        routes[prefix + "reports/indexes"] = {"indexes": [{"state": "READY", "queryScope": "COLLECTION", "fields": [
            {"fieldPath": "status", "order": "ASCENDING"}, {"fieldPath": "createdAt", "order": "ASCENDING"}, {"fieldPath": "__name__", "order": "ASCENDING"}]}]}
        for group in ("_abuseBudgets", "_pushDeliveries"):
            routes[prefix + group + "/fields/expiresAt"] = {"ttlConfig": {"state": "ACTIVE"}}
        for function in cloud.FUNCTIONS:
            name = f"projects/{self.project}/locations/europe-west3/functions/{function}"
            routes["https://cloudfunctions.googleapis.com/v2/" + name] = {"name": name, "state": "ACTIVE", "buildConfig": {"runtime": "nodejs22"}, "serviceConfig": {"maxInstanceCount": 5}, "eventTrigger": {"eventFilters": {"database": "mahallem"}}}
        return FakeReader(routes)

    def test_active_deployment_passes_but_unready_index_or_ttl_fails(self):
        reader = self.deployed_reader()
        self.assertEqual(2, len(cloud.verify_deployed(reader, self.project, self.bucket, ROOT)))
        key = f"https://firestore.googleapis.com/v1/projects/{self.project}/databases/mahallem/collectionGroups/reports/indexes"
        reader.responses[key]["indexes"][0]["state"] = "CREATING"
        with self.assertRaisesRegex(cloud.VerificationError, "READY"):
            cloud.verify_deployed(reader, self.project, self.bucket, ROOT)
        reader.responses[key]["indexes"][0]["state"] = "READY"
        key = f"https://firestore.googleapis.com/v1/projects/{self.project}/databases/mahallem/collectionGroups/_abuseBudgets/fields/expiresAt"
        reader.responses[key]["ttlConfig"]["state"] = "CREATING"
        with self.assertRaisesRegex(cloud.VerificationError, "TTL"):
            cloud.verify_deployed(reader, self.project, self.bucket, ROOT)

    def test_unsafe_function_runtime_configuration_and_wrong_push_database_fail(self):
        for field, value, message in (("FUNCTIONS_EMULATOR", "true", "unsafe"), ("FIRESTORE_DATABASE_ID", "(default)", "unsafe")):
            reader = self.deployed_reader()
            key = f"https://cloudfunctions.googleapis.com/v2/projects/{self.project}/locations/europe-west3/functions/uploadConversationPhoto"
            reader.responses[key]["serviceConfig"]["environmentVariables"] = {field: value}
            with self.assertRaisesRegex(cloud.VerificationError, message):
                cloud.verify_deployed(reader, self.project, self.bucket, ROOT)
        reader = self.deployed_reader()
        key = f"https://cloudfunctions.googleapis.com/v2/projects/{self.project}/locations/europe-west3/functions/notifyConversationMessage"
        reader.responses[key]["eventTrigger"]["eventFilters"]["database"] = "(default)"
        with self.assertRaisesRegex(cloud.VerificationError, "Push trigger"):
            cloud.verify_deployed(reader, self.project, self.bucket, ROOT)

    def test_success_report_never_contains_cloud_responses_or_secrets(self):
        with tempfile.TemporaryDirectory() as directory:
            config = Path(directory) / "config.json"
            report = Path(directory) / "report.json"
            config.write_text(json.dumps(self.config))
            argv = ["verify", "--project", self.project, "--key-id", "key-id", "--config", str(config), "--firebase-config", str(ROOT / "firebase.json"), "--report", str(report)]
            with patch.object(sys, "argv", argv), patch.object(cloud, "validate_adc"), patch.object(cloud, "access_token", return_value="secret-access-token"), patch.object(cloud, "CloudReader", return_value=FakeReader(self.routes)):
                self.assertEqual(0, cloud.main())
            saved = report.read_text()
            for value in ("secret-test-key", "secret-access-token", "secret-smtp-password", "private-number", "secret-test-code", "testPhoneNumbers", "smtp"):
                self.assertNotIn(value, saved)
            self.assertFalse(json.loads(saved)["physicalDeviceVerified"])


if __name__ == "__main__":
    unittest.main()
