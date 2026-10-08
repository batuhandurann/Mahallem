import importlib.util
from pathlib import Path
import shutil
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location("wrapper", ROOT / "scripts/verify-gradle-wrapper.py")
wrapper = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(wrapper)


class WrapperIntegrityTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        shutil.copy2(ROOT / "gradlew", self.root / "gradlew")
        shutil.copy2(ROOT / "gradlew.bat", self.root / "gradlew.bat")
        shutil.copytree(ROOT / "gradle/wrapper", self.root / "gradle/wrapper")

    def test_checked_in_wrapper_passes(self):
        wrapper.verify(self.root)

    def test_tampered_jar_fails_before_execution(self):
        with (self.root / "gradle/wrapper/gradle-wrapper.jar").open("ab") as stream:
            stream.write(b"tampered")
        with self.assertRaisesRegex(ValueError, "JAR SHA-256"):
            wrapper.verify(self.root)

    def test_missing_windows_launcher_fails(self):
        (self.root / "gradlew.bat").unlink()
        with self.assertRaisesRegex(ValueError, "Missing wrapper"):
            wrapper.verify(self.root)

    def test_unpinned_distribution_fails(self):
        props = self.root / "gradle/wrapper/gradle-wrapper.properties"
        props.write_text(props.read_text().replace(wrapper.DISTRIBUTION_SHA256, "0" * 64))
        with self.assertRaisesRegex(ValueError, "distribution SHA-256"):
            wrapper.verify(self.root)

    def test_untrusted_distribution_url_fails(self):
        props = self.root / "gradle/wrapper/gradle-wrapper.properties"
        props.write_text(props.read_text().replace("services.gradle.org", "untrusted.invalid"))
        with self.assertRaisesRegex(ValueError, "distribution URL"):
            wrapper.verify(self.root)

    def test_non_executable_launcher_fails(self):
        (self.root / "gradlew").chmod(0o644)
        with self.assertRaisesRegex(ValueError, "executable"):
            wrapper.verify(self.root)
