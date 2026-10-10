import importlib.util
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

SCRIPTS = Path(__file__).resolve().parents[2] / "scripts"
sys.path.insert(0, str(SCRIPTS))
import production_preflight as preflight


class ProductionPreflightTest(unittest.TestCase):
    def test_missing_signing_values_never_echo_environment(self):
        with tempfile.TemporaryDirectory() as directory:
            key = Path(directory) / "private-key.jks"
            key.write_bytes(b"fixture")
            values = {"KEYSTORE_PATH": str(key), "STORE_PASSWORD": "SECRET_STORE",
                      "KEY_PASSWORD": "SECRET_KEY", "KEY_ALIAS": "PRIVATE_ALIAS"}
            result = preflight.signing_inputs(values)
            self.assertTrue(all(result.values()))
            serialized = json.dumps(result)
            for value in values.values():
                self.assertNotIn(value, serialized)
            self.assertFalse(preflight.signing_inputs({})["KEYSTORE_FILE"])

    def test_absent_device_and_config_remain_blocked(self):
        with patch.dict(os.environ, {}, clear=True), patch.object(preflight, "adb_program", return_value=None):
            result = preflight.inspect(Path("/absent/config.json"))
        self.assertEqual("BLOCKED", result["status"])
        self.assertEqual("ADB_UNAVAILABLE", result["physicalDevice"])
        self.assertEqual({"NOT_VERIFIED"}, set(result["verification"].values()))

    def test_authorized_adb_emulator_cannot_count_as_phone(self):
        def run(command, **kwargs):
            output = "phone-looking-id device" if command[-1] == "devices" else "1"
            return subprocess.CompletedProcess(command, 0, stdout=output)
        with patch.dict(os.environ, {}, clear=True), patch.object(preflight, "adb_program", return_value="adb"), \
                patch.object(preflight.subprocess, "run", side_effect=run):
            result = preflight.inspect(Path("/absent/config.json"))
        self.assertEqual("UNSUPPORTED_OR_VIRTUAL", result["physicalDevice"])
        self.assertNotIn("phone-looking-id", json.dumps(result))

    def test_adb_timeout_is_reported_without_command_or_credentials(self):
        with patch.object(preflight, "adb_program", return_value="private/path/adb"), \
                patch.object(preflight.subprocess, "run", side_effect=subprocess.TimeoutExpired("private-command", 15)):
            result = preflight.inspect(Path("/absent/config.json"))
        self.assertEqual("ADB_CHECK_FAILED", result["physicalDevice"])
        self.assertNotIn("private", json.dumps(result))

    def test_windows_sdk_finds_exe_without_path_entry(self):
        import physical_android_smoke as physical
        with tempfile.TemporaryDirectory() as directory:
            platform = Path(directory) / "platform-tools"
            platform.mkdir()
            adb = platform / ("adb.exe" if os.name == "nt" else "adb")
            adb.write_bytes(b"fixture")
            with patch.dict(os.environ, {"ANDROID_HOME": directory}), patch.object(physical.shutil, "which", return_value=None):
                self.assertEqual(str(adb), physical.adb_program(None))
