"""Release fixture tests; temporary test certificates are never production credentials."""
import copy
import importlib.util
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch
import zipfile

SPEC = importlib.util.spec_from_file_location("signed_release", Path(__file__).parents[2] / "scripts/verify_signed_release.py")
release = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(release)


def fixture_config():
    return {"project_info": {"project_id": "fixture-release", "project_number": "123456"}, "client": [{"client_info": {"mobilesdk_app_id": "1:123456:android:abcdef", "android_client_info": {"package_name": release.PACKAGE}}, "api_key": [{"current_key": "AIza" + "x" * 35}]}]}


class ReleaseConfigTest(unittest.TestCase):
    def test_canonical_fixture_passes_without_returning_api_key(self):
        result = release.validate_config(fixture_config())
        self.assertEqual(release.PACKAGE, result["package"])
        self.assertNotIn("AIza", str(result))

    def test_demo_wrong_package_cross_project_and_missing_api_key_rejected(self):
        configs = []
        demo = fixture_config()
        demo["project_info"]["project_id"] = "demo-release"
        configs.append(demo)
        wrong = fixture_config()
        wrong["client"][0]["client_info"]["android_client_info"]["package_name"] = "com.example.fake"
        configs.append(wrong)
        cross_project = fixture_config()
        cross_project["client"][0]["client_info"]["mobilesdk_app_id"] = "1:999:android:abcdef"
        configs.append(cross_project)
        no_key = fixture_config()
        no_key["client"][0]["api_key"] = []
        configs.append(no_key)
        duplicate = fixture_config()
        duplicate["client"].append(copy.deepcopy(duplicate["client"][0]))
        configs.append(duplicate)
        for config in configs:
            with self.subTest(config=config), self.assertRaises(ValueError):
                release.validate_config(config)

    def test_apk_rejects_wrong_certificate_or_unsigned_output(self):
        for output in (b"Signer #1 certificate SHA-256 digest: deadbeef\n", b"Not signed"):
            with patch.object(release, "run", return_value=output), self.assertRaises(ValueError):
                release.verify_apk(Path("fixture.apk"), Path("apksigner"), "abcdef")


class RealAabSignatureTest(unittest.TestCase):
    def test_self_signed_fixture_key_passes_wrong_signer_unsigned_and_tamper_fail(self):
        with tempfile.TemporaryDirectory(prefix="release-fixture-") as directory:
            root = Path(directory)
            key = root / "fixture.p12"
            password = "fixture-test-password"
            with patch.dict(os.environ, {"STORE_PASSWORD": password}):
                for alias in ("fixture-upload", "wrong-upload"):
                    release.run(["keytool", "-genkeypair", "-keystore", str(key), "-storepass:env", "STORE_PASSWORD", "-alias", alias, "-keyalg", "RSA", "-keysize", "2048", "-validity", "3650", "-dname", "CN=Release Fixture Only"])
                artifact = root / "fixture.aab"
                with zipfile.ZipFile(artifact, "w") as archive:
                    archive.writestr("base/manifest/AndroidManifest.xml", b"fixture manifest")
                with self.assertRaises(ValueError):
                    release.verify_aab(artifact, key, "fixture-upload")
                release.run(release.jarsigner_command() + ["-keystore", str(key), "-storepass:env", "STORE_PASSWORD", str(artifact), "fixture-upload"])
                release.verify_aab(artifact, key, "fixture-upload")
                with self.assertRaises(ValueError):
                    release.verify_aab(artifact, key, "wrong-upload")
                with zipfile.ZipFile(artifact, "a") as archive:
                    archive.writestr("base/assets/unsigned-file", b"tampered unsigned entry")
                with self.assertRaises(ValueError):
                    release.verify_aab(artifact, key, "fixture-upload")


if __name__ == "__main__":
    unittest.main()
