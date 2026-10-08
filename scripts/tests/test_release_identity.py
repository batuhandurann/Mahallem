import importlib.util
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location("identity", ROOT / "scripts/verify-release-identity.py")
identity = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(identity)

if os.environ.get("GITHUB_ACTIONS") == "true" and not (shutil.which("jarsigner") and shutil.which("keytool")):
    raise RuntimeError("CI must install JDK signing tools; signature regression tests cannot be skipped")


class ReleaseIdentityConfigTest(unittest.TestCase):
    def config(self, project="approved-prod", package="com.test.app"):
        return {"project_info": {"project_id": project}, "client": [
            {"client_info": {"android_client_info": {"package_name": package}}}]}

    def test_matching_config(self):
        identity.validate_config(self.config(), "com.test.app", "approved-prod", "com.test.app")

    def test_unapproved_project(self):
        with self.assertRaisesRegex(ValueError, "Firebase project"):
            identity.validate_config(self.config("different-prod"), "com.test.app", "approved-prod", "com.test.app")

    def test_wrong_firebase_client(self):
        with self.assertRaisesRegex(ValueError, "Android client"):
            identity.validate_config(self.config(package="com.other.app"), "com.test.app", "approved-prod", "com.test.app")

    def test_wrong_build_id(self):
        with self.assertRaisesRegex(ValueError, "Build applicationId"):
            identity.validate_config(self.config(), "com.other.app", "approved-prod", "com.test.app")

    def test_missing_approval_is_blocked(self):
        with self.assertRaisesRegex(ValueError, "Missing protected approval"):
            identity.approved_value({}, "APPROVED_FIREBASE_PROJECT_ID")

    def test_demo_target_is_blocked(self):
        with self.assertRaisesRegex(ValueError, "demo/staging"):
            identity.validate_config(self.config("demo-app"), "com.test.app", "demo-app", "com.test.app")


@unittest.skipUnless(shutil.which("jarsigner") and shutil.which("keytool"), "JDK signing tools unavailable locally; mandatory on CI JDK 21")
class SignedArchiveIdentityTest(unittest.TestCase):
    def setUp(self):
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        self.root = Path(temp.name)
        self.env = dict(os.environ, STORE_PASSWORD="FixtureOnly-NotAProductionKey")
        self.key = self.root / "fixture.p12"
        subprocess.run(["keytool", "-genkeypair", "-alias", "upload", "-keystore", str(self.key),
                        "-storepass:env", "STORE_PASSWORD", "-keypass:env", "STORE_PASSWORD",
                        "-keyalg", "RSA", "-keysize", "2048", "-validity", "30", "-dname", "CN=CI Fixture"],
                       env=self.env, check=True, capture_output=True)
        pem = subprocess.check_output(["keytool", "-exportcert", "-rfc", "-alias", "upload", "-keystore", str(self.key),
                                       "-storepass:env", "STORE_PASSWORD"], env=self.env, text=True)
        self.fingerprint = identity.certificate_fingerprint(pem)
        self.archive = self.root / "fixture.aab"
        with zipfile.ZipFile(self.archive, "w") as jar:
            jar.writestr("base/manifest/AndroidManifest.xml", b"fixture-manifest")
            jar.writestr("base/dex/classes.dex", b"fixture-dex")
        subprocess.run(["jarsigner", "-keystore", str(self.key), "-storepass:env", "STORE_PASSWORD",
                        str(self.archive), "upload"], env=self.env, check=True, capture_output=True)

    def test_matching_real_signature(self):
        self.assertEqual(identity.verify_signed_archive(self.archive, self.key, "upload", self.fingerprint, self.env), self.fingerprint)

    def test_wrong_approved_certificate(self):
        with self.assertRaisesRegex(ValueError, "certificate differs"):
            identity.verify_signed_archive(self.archive, self.key, "upload", "0" * 64, self.env)

    def test_tampered_signed_payload_fails(self):
        with zipfile.ZipFile(self.archive, "a") as jar:
            jar.writestr("base/dex/injected.dex", b"unsigned-payload")
        with self.assertRaises(subprocess.CalledProcessError):
            identity.verify_signed_archive(self.archive, self.key, "upload", self.fingerprint, self.env)
