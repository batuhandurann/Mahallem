"""Fail-closed regression against accidentally replacing canonical Burada with legacy QA.

The Android Quality build-test-lint job discovers this test before Gradle compilation.
"""
from pathlib import Path
import re
import tempfile
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
PACKAGE = "com.batuhanduran.burada"
JAVA_ROOT = Path("app/src/main/java")
ANDROID_NAME = "{http://schemas.android.com/apk/res/android}name"
ANDROID_EXPORTED = "{http://schemas.android.com/apk/res/android}exported"


def validate_repository(root: Path) -> list[str]:
    errors = []
    gradle = root / "app/build.gradle.kts"
    manifest = root / "app/src/main/AndroidManifest.xml"
    if not gradle.is_file() or not manifest.is_file():
        return ["Missing canonical Android Gradle configuration or manifest"]

    build_text = gradle.read_text(encoding="utf-8")
    for key in ("namespace", "applicationId"):
        match = re.search(rf'^\s*{key}\s*=\s*"([^"]+)"\s*$', build_text, re.MULTILINE)
        if match is None or match.group(1) != PACKAGE:
            errors.append(f"Expected canonical {key}={PACKAGE}")

    source_root = root / JAVA_ROOT
    for cls in ("MainActivity", "BuradaApplication"):
        source = source_root / "com/batuhanduran/burada" / f"{cls}.kt"
        if not source.is_file():
            errors.append(f"Missing canonical entry point {cls}.kt")
        elif not re.search(rf'^\s*package\s+{re.escape(PACKAGE)}\s*$',
                           source.read_text(encoding="utf-8"), re.MULTILINE):
            errors.append(f"Wrong package in {cls}.kt")

    if any((source_root / "com/example").rglob("*.kt")):
        errors.append("Legacy com.example Android source tree must not enter main")

    try:
        app = ET.parse(manifest).getroot().find("application")
        if app is None:
            errors.append("Missing manifest application")
        else:
            def full_name(value: str | None) -> str:
                if not value:
                    return ""
                return PACKAGE + value if value.startswith(".") else value

            if full_name(app.get(ANDROID_NAME)) != PACKAGE + ".BuradaApplication":
                errors.append("Wrong canonical Application class")
            launchers = []
            for activity in app.findall("activity"):
                for intent in activity.findall("intent-filter"):
                    actions = {x.get(ANDROID_NAME) for x in intent.findall("action")}
                    categories = {x.get(ANDROID_NAME) for x in intent.findall("category")}
                    if ("android.intent.action.MAIN" in actions and
                            "android.intent.category.LAUNCHER" in categories):
                        launchers.append(activity)
            if len(launchers) != 1 or full_name(launchers[0].get(ANDROID_NAME)) != PACKAGE + ".MainActivity" or launchers[0].get(ANDROID_EXPORTED) != "true":
                errors.append("Expected one exported canonical MainActivity launcher")
    except ET.ParseError as exc:
        errors.append(f"Invalid AndroidManifest.xml: {exc}")
    return errors


class CanonicalAndroidIdentityTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.fixture = Path(self.temp.name)
        self.gradle = self.fixture / "app/build.gradle.kts"
        self.manifest = self.fixture / "app/src/main/AndroidManifest.xml"
        self.gradle.parent.mkdir(parents=True)
        self.gradle.write_text('android {\n namespace = "com.batuhanduran.burada"\n defaultConfig {\n applicationId = "com.batuhanduran.burada"\n }\n}\n', encoding="utf-8")
        self.manifest.parent.mkdir(parents=True)
        self.manifest.write_text('''<manifest xmlns:android="http://schemas.android.com/apk/res/android"><application android:name=".BuradaApplication"><activity android:name=".MainActivity" android:exported="true"><intent-filter><action android:name="android.intent.action.MAIN"/><category android:name="android.intent.category.LAUNCHER"/></intent-filter></activity></application></manifest>''', encoding="utf-8")
        self.sources = self.fixture / JAVA_ROOT / "com/batuhanduran/burada"
        self.sources.mkdir(parents=True)
        for cls in ("MainActivity", "BuradaApplication"):
            (self.sources / f"{cls}.kt").write_text(f"package {PACKAGE}\nclass {cls}\n", encoding="utf-8")

    def test_checked_out_main_is_canonical(self):
        self.assertEqual([], validate_repository(ROOT))

    def test_canonical_fixture_passes(self):
        self.assertEqual([], validate_repository(self.fixture))

    def test_legacy_namespace_and_application_id_fail(self):
        self.gradle.write_text('namespace = "com.example"\napplicationId = "com.example"\n', encoding="utf-8")
        errors = validate_repository(self.fixture)
        self.assertTrue(any("namespace" in x for x in errors))
        self.assertTrue(any("applicationId" in x for x in errors))

    def test_missing_entry_point_or_legacy_source_fails(self):
        (self.sources / "MainActivity.kt").unlink()
        legacy = self.fixture / JAVA_ROOT / "com/example/OldActivity.kt"
        legacy.parent.mkdir(parents=True)
        legacy.write_text("package com.example", encoding="utf-8")
        errors = validate_repository(self.fixture)
        self.assertTrue(any("MainActivity.kt" in x for x in errors))
        self.assertTrue(any("Legacy" in x for x in errors))

    def test_legacy_launcher_fails(self):
        self.manifest.write_text(self.manifest.read_text(encoding="utf-8").replace('.MainActivity', '.LegacyActivity'), encoding="utf-8")
        self.assertTrue(any("launcher" in x for x in validate_repository(self.fixture)))


if __name__ == "__main__":
    unittest.main()
