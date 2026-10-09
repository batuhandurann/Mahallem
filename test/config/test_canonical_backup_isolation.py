"""Fail-closed regression for canonical Android local data backup policy."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
ANDROID_NS = "{http://schemas.android.com/apk/res/android}"


class CanonicalBackupIsolationTest(unittest.TestCase):
    def test_backup_disabled_in_canonical_manifest(self):
        manifest = ET.parse(ROOT / "app/src/main/AndroidManifest.xml").getroot()
        app = manifest.find("application")
        self.assertIsNotNone(app)
        self.assertEqual(app.get(ANDROID_NS + "allowBackup"), "false")
        self.assertEqual(app.get(ANDROID_NS + "fullBackupContent"), "@xml/backup_rules")
        self.assertEqual(app.get(ANDROID_NS + "dataExtractionRules"), "@xml/data_extraction_rules")

    def test_legacy_auto_backup_excludes_all_private_files(self):
        rules = ET.parse(ROOT / "app/src/main/res/xml/backup_rules.xml").getroot()
        self.assertEqual(rules.tag, "full-backup-content")
        expected = {("root", "."), ("device_root", "."), ("external", ".")}
        actual = {(e.get("domain"), e.get("path")) for e in rules.findall("exclude")}
        self.assertTrue(expected.issubset(actual))
        self.assertFalse(rules.findall("include"))

    def test_android_12_cloud_and_device_transfer_exclude_all(self):
        rules = ET.parse(ROOT / "app/src/main/res/xml/data_extraction_rules.xml").getroot()
        self.assertEqual(rules.tag, "data-extraction-rules")
        expected = {("root", "."), ("device_root", "."), ("external", ".")}
        for section in ("cloud-backup", "device-transfer"):
            node = rules.find(section)
            self.assertIsNotNone(node, f"Missing {section}")
            actual = {(e.get("domain"), e.get("path")) for e in node.findall("exclude")}
            self.assertTrue(expected.issubset(actual))
            self.assertFalse(node.findall("include"))


if __name__ == "__main__":
    unittest.main()
