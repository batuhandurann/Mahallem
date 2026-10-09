"""P0 fail-closed guard: canonical Android must use the named 'mahallem' database.

This tests repository configuration, not live Firebase rules, deployment or credentials.
The existing Android Quality build-test-lint job discovers test/config/test_*.py.
"""
import json
from pathlib import Path
import re
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]
EXPECTED_DATABASE = "mahallem"


def validate_database_identity(root: Path) -> list[str]:
    errors: list[str] = []
    config_path = root / "firebase.json"
    gradle_path = root / "app/build.gradle.kts"
    services_path = (
        root / "app/src/main/java/com/batuhanduran/burada/data/remote/FirebaseServices.kt"
    )
    if not config_path.is_file() or not gradle_path.is_file() or not services_path.is_file():
        return ["Missing Firebase, canonical Gradle, or FirebaseServices configuration"]

    try:
        config = json.loads(config_path.read_text(encoding="utf-8"))
    except (ValueError, OSError) as exc:
        return [f"Invalid firebase.json: {exc}"]
    firestore = config.get("firestore") if isinstance(config, dict) else None
    if not isinstance(firestore, dict):
        errors.append("firebase.json must configure a named Firestore database")
    else:
        if firestore.get("database") != EXPECTED_DATABASE:
            errors.append("firebase.json Firestore database must be mahallem")
        if firestore.get("rules") != "firestore.rules":
            errors.append("Canonical Firestore rules path changed unexpectedly")
        if firestore.get("indexes") != "firestore.indexes.json":
            errors.append("Canonical Firestore indexes path changed unexpectedly")

    gradle = gradle_path.read_text(encoding="utf-8")
    # A literal escaped Kotlin string is required, not a raw / default database ID.
    matches = re.findall(
        r'buildConfigField\s*\(\s*"String"\s*,\s*"FIRESTORE_DATABASE_ID"\s*,\s*"((?:\\.|[^"\\])*)"\s*\)',
        gradle,
    )
    if len(matches) != 1 or matches[0] != r'\"mahallem\"':
        errors.append("Canonical FIRESTORE_DATABASE_ID must be exactly mahallem")

    services = services_path.read_text(encoding="utf-8")
    collapsed = re.sub(r"\s+", "", services)
    if "FirebaseFirestore.getInstance(app,BuildConfig.FIRESTORE_DATABASE_ID)" not in collapsed:
        errors.append("FirebaseServices must open the canonical named Firestore database")
    if re.search(r'FirebaseFirestore\s*\.\s*getInstance\s*\(\s*(?:\)|app\s*\))', services):
        errors.append("Default Firestore database fallback must not be used")
    if "check(!BuildConfig.USE_FIREBASE_EMULATORS||BuildConfig.DEBUG)" not in collapsed:
        errors.append("Release builds must forbid Firebase emulators")
    if "if(BuildConfig.USE_FIREBASE_EMULATORS)useEmulator(" not in collapsed:
        errors.append("Firestore emulator must be explicitly gated to test builds")
    return errors


class CanonicalFirestoreIdentityTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.fixture = Path(self.temp.name)
        self.config = self.fixture / "firebase.json"
        self.gradle = self.fixture / "app/build.gradle.kts"
        self.services = (
            self.fixture
            / "app/src/main/java/com/batuhanduran/burada/data/remote/FirebaseServices.kt"
        )
        self.config.parent.mkdir(parents=True, exist_ok=True)
        self.gradle.parent.mkdir(parents=True, exist_ok=True)
        self.services.parent.mkdir(parents=True, exist_ok=True)
        self.config.write_text(
            json.dumps({"firestore": {
                "database": EXPECTED_DATABASE,
                "rules": "firestore.rules",
                "indexes": "firestore.indexes.json",
            }}),
            encoding="utf-8",
        )
        self.gradle.write_text(
            r'buildConfigField("String", "FIRESTORE_DATABASE_ID", "\"mahallem\"")',
            encoding="utf-8",
        )
        self.services.write_text(
            "val firestore = FirebaseFirestore.getInstance(app, "
            "BuildConfig.FIRESTORE_DATABASE_ID)\n"
            "check(!BuildConfig.USE_FIREBASE_EMULATORS || BuildConfig.DEBUG)\n"
            "if (BuildConfig.USE_FIREBASE_EMULATORS) useEmulator(host, 8080)\n",
            encoding="utf-8",
        )

    def test_checked_out_canonical_main_contract(self):
        self.assertEqual([], validate_database_identity(ROOT))

    def test_minimal_canonical_fixture_passes(self):
        self.assertEqual([], validate_database_identity(self.fixture))

    def test_default_database_is_rejected(self):
        cfg = json.loads(self.config.read_text(encoding="utf-8"))
        cfg["firestore"]["database"] = "(default)"
        self.config.write_text(json.dumps(cfg), encoding="utf-8")
        self.assertTrue(any("database must be mahallem" in e
                            for e in validate_database_identity(self.fixture)))

    def test_missing_rules_and_indexes_are_rejected(self):
        cfg = json.loads(self.config.read_text(encoding="utf-8"))
        cfg["firestore"].pop("rules")
        cfg["firestore"]["indexes"] = "old.indexes.json"
        self.config.write_text(json.dumps(cfg), encoding="utf-8")
        failures = validate_database_identity(self.fixture)
        self.assertTrue(any("rules path" in e for e in failures))
        self.assertTrue(any("indexes path" in e for e in failures))

    def test_gradle_wrong_named_database_is_rejected(self):
        self.gradle.write_text(
            r'buildConfigField("String", "FIRESTORE_DATABASE_ID", "\"(default)\"")',
            encoding="utf-8",
        )
        self.assertTrue(any("FIRESTORE_DATABASE_ID" in e
                            for e in validate_database_identity(self.fixture)))

    def test_runtime_default_instance_is_rejected(self):
        self.services.write_text(
            self.services.read_text(encoding="utf-8").replace(
                "FirebaseFirestore.getInstance(app, BuildConfig.FIRESTORE_DATABASE_ID)",
                "FirebaseFirestore.getInstance()"
            ),
            encoding="utf-8",
        )
        failures = validate_database_identity(self.fixture)
        self.assertTrue(any("named Firestore" in e for e in failures))
        self.assertTrue(any("fallback" in e for e in failures))

    def test_release_emulator_guard_cannot_be_removed(self):
        self.services.write_text(
            self.services.read_text(encoding="utf-8").replace(
                "check(!BuildConfig.USE_FIREBASE_EMULATORS || BuildConfig.DEBUG)", ""
            ),
            encoding="utf-8",
        )
        self.assertTrue(any("Release builds" in e
                            for e in validate_database_identity(self.fixture)))

    def test_missing_configuration_fails_closed(self):
        self.config.unlink()
        self.assertTrue(any("Missing Firebase" in e
                            for e in validate_database_identity(self.fixture)))


if __name__ == "__main__":
    unittest.main()
