import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location("production_audit", Path(__file__).parents[2] / "scripts/audit_firebase_production.py")
audit_module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(audit_module)


class ProductionAuditTests(unittest.TestCase):
    def setUp(self):
        self.config = {"project_info": {"project_id": "burada-staging", "project_number": "123456"}, "client": [{"client_info": {"mobilesdk_app_id": "1:123456:android:abcd", "android_client_info": {"package_name": audit_module.PACKAGE}}, "api_key": [{"current_key": "test-key"}]}]}
        self.evidence = {"androidApiKeyString": {"keyString": "test-key"}, "playIntegrityConfig": {"name": "projects/123456/apps/1:123456:android:abcd/playIntegrityConfig", "tokenTtl": "3600s"}, "appCheckServices": {"services": [{"name": f"projects/123456/services/{service}", "enforcementMode": "ENFORCED"} for service in audit_module.SERVICES]}, "androidApiKey": {"name": "projects/123456/locations/global/keys/key-id", "restrictions": {"androidKeyRestrictions": {"allowedApplications": [{"packageName": audit_module.PACKAGE, "sha1Fingerprint": "AB" * 20}]}, "apiTargets": [{"service": s} for s in audit_module.REQUIRED_APIS]}}}

    def test_complete_matching_evidence_passes(self):
        self.assertEqual([], audit_module.audit(self.config, self.evidence))

    def test_push_registration_apis_cannot_be_removed(self):
        targets = self.evidence["androidApiKey"]["restrictions"]["apiTargets"]
        for missing in ("firebaseinstallations.googleapis.com", "fcmregistrations.googleapis.com"):
            with self.subTest(missing=missing):
                self.evidence["androidApiKey"]["restrictions"]["apiTargets"] = [target for target in targets if target["service"] != missing]
                self.assertIn("Android API allowlist is missing one or more required Firebase APIs", audit_module.audit(self.config, self.evidence))

    def test_another_key_cannot_pass(self):
        self.evidence["androidApiKeyString"]["keyString"] = "another-key"
        self.assertTrue(audit_module.audit(self.config, self.evidence))

    def test_default_ttl_is_valid(self):
        self.evidence["playIntegrityConfig"].pop("tokenTtl")
        self.assertEqual([], audit_module.audit(self.config, self.evidence))

    def test_no_cloud_evidence_cannot_claim_enabled(self):
        self.assertTrue(audit_module.audit(self.config))

    def test_a_different_project_cannot_supply_enforcement(self):
        self.evidence["appCheckServices"]["services"][0]["name"] = "projects/999/services/firestore.googleapis.com"
        self.assertTrue(audit_module.audit(self.config, self.evidence))

    def test_old_package_is_rejected(self):
        self.config["client"][0]["client_info"]["android_client_info"]["package_name"] = "com.example"
        self.assertTrue(audit_module.audit(self.config, self.evidence))

    def test_unrestricted_android_key_is_rejected(self):
        self.evidence["androidApiKey"]["restrictions"].pop("androidKeyRestrictions")
        self.assertTrue(audit_module.audit(self.config, self.evidence))

    def test_unenforced_storage_is_rejected(self):
        for s in self.evidence["appCheckServices"]["services"]:
            if s["name"].endswith("firebasestorage.googleapis.com"):
                s["enforcementMode"] = "UNENFORCED"
        self.assertTrue(audit_module.audit(self.config, self.evidence))

    def test_other_app_key_cannot_pass(self):
        self.evidence["androidApiKey"]["restrictions"]["androidKeyRestrictions"]["allowedApplications"][0]["packageName"] = "com.example"
        self.assertTrue(audit_module.audit(self.config, self.evidence))


if __name__ == "__main__":
    unittest.main()
