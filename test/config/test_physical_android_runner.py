import importlib.util
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import MagicMock, patch

spec = importlib.util.spec_from_file_location("physical", Path(__file__).resolve().parents[2] / "scripts/physical_android_smoke.py")
physical = importlib.util.module_from_spec(spec)
spec.loader.exec_module(physical)


class PhysicalRunnerTest(unittest.TestCase):
    def test_missing_functions_emulator_prevents_android_execution(self):
        environment = {"GCLOUD_PROJECT": "demo-mahallem", "FIREBASE_AUTH_EMULATOR_HOST": "127.0.0.1:9099",
                       "FIRESTORE_EMULATOR_HOST": "127.0.0.1:8080"}
        with patch.dict(os.environ, environment), patch.object(physical.socket, "create_connection", side_effect=OSError):
            with self.assertRaisesRegex(ValueError, "Functions emulator"):
                physical.require_demo_emulators()

    def test_demo_environment_required_before_functions_probe(self):
        with patch.dict(os.environ, {"GCLOUD_PROJECT": "production-project"}), patch.object(physical.socket, "create_connection") as connect:
            with self.assertRaisesRegex(ValueError, "isolated demo"):
                physical.require_demo_emulators()
            connect.assert_not_called()

    def run_fixture(self, child=False, mappings="", missing_functions=False):
        """Command fixtures exercise orchestration; they are not device evidence."""
        calls, cleanups = [], []
        def execute(args, **kwargs):
            calls.append(args)
            result = ""
            if args[1:] == ["devices"]:
                result = "List of devices attached\nphone-a device\n"
            elif "getprop" in args:
                result = {"ro.build.version.sdk": "35", "ro.product.model": "Fixture", "ro.kernel.qemu": "0", "ro.boot.qemu": "0"}[args[-1]]
            elif args[-2:] == ["reverse", "--list"]:
                result = mappings
            elif "instrument" in args:
                result = "OK (1 test)"
            return subprocess.CompletedProcess(args, 0, stdout=result)
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "evidence.json"
            def run(args, **kwargs):
                if "emulators:exec" in args:
                    output.write_text(json.dumps({"status": "PASS"}))
                    calls.append(args)
                else:
                    cleanups.append(args)
                return subprocess.CompletedProcess(args, 0)
            argv = ["physical", "--adb", "/fixture/adb", "--output", str(output)] + (["--child"] if child else [])
            environment = {"GCLOUD_PROJECT": "demo-mahallem", "FIREBASE_AUTH_EMULATOR_HOST": "127.0.0.1:9099",
                           "FIRESTORE_EMULATOR_HOST": "127.0.0.1:8080", "ANDROID_SERIAL": "phone-a"}
            with patch.dict(os.environ, environment), patch.object(physical.sys, "argv", argv), \
                    patch.object(physical, "execute", side_effect=execute), patch.object(physical.subprocess, "run", side_effect=run), \
                    patch.object(physical.socket, "create_connection", side_effect=OSError if missing_functions else None, return_value=MagicMock()), \
                    patch("builtins.print"):
                code = physical.main()
            return code, json.loads(output.read_text()), calls, cleanups

    def test_parent_starts_functions_with_dependencies(self):
        code, _, calls, _ = self.run_fixture()
        self.assertEqual(0, code)
        self.assertIn(["npm", "ci", "--prefix", "functions"], calls)
        runner = next(args for args in calls if "emulators:exec" in args)
        self.assertEqual("auth,firestore,functions", runner[runner.index("--only") + 1])

    def test_child_maps_functions_and_preserves_existing_reverse_mapping(self):
        code, _, calls, cleanups = self.run_fixture(child=True, mappings="phone-a tcp:8080 tcp:8080")
        self.assertEqual(0, code)
        self.assertIn(["/fixture/adb", "-s", "phone-a", "reverse", "tcp:5001", "tcp:5001"], calls)
        self.assertEqual({"tcp:9099", "tcp:5001"}, {args[-1] for args in cleanups})

    def test_conflicting_functions_mapping_cleans_up_and_skips_build(self):
        code, report, calls, cleanups = self.run_fixture(child=True, mappings="phone-a tcp:5001 tcp:9000")
        self.assertNotEqual(0, code)
        self.assertEqual("NOT_RUN", report["status"])
        self.assertEqual({"tcp:8080", "tcp:9099"}, {args[-1] for args in cleanups})
        self.assertFalse(any(args[0] == "./gradlew" or "install" in args for args in calls))

    def test_missing_functions_keeps_not_run_without_install_or_build(self):
        code, report, calls, cleanups = self.run_fixture(child=True, missing_functions=True)
        self.assertNotEqual(0, code)
        self.assertEqual("NOT_RUN", report["status"])
        self.assertEqual([], cleanups)
        self.assertFalse(any(args[0] == "./gradlew" or "install" in args for args in calls))

    def test_only_authorized_physical_device_is_auto_selected(self):
        devices = "List of devices attached\nemulator-5554\tdevice\nphone-a\tdevice\nphone-b\tunauthorized\n"
        self.assertEqual("phone-a", physical.choose_device(devices))

    def test_missing_unauthorized_emulator_or_ambiguous_selection_fails(self):
        for devices, selected in [("", None), ("a unauthorized", "a"), ("emulator-5554 device", "emulator-5554"),
                                  ("a device\nb device", None), ("a offline", "a")]:
            with self.subTest(devices=devices, selected=selected), self.assertRaises(ValueError):
                physical.choose_device(devices, selected)

    def test_explicit_authorized_selection_is_supported(self):
        self.assertEqual("b", physical.choose_device("a device\nb device", "b"))

    def test_positive_junit_result_counts_actual_tests(self):
        self.assertEqual(12, physical.successful_test_count("INSTRUMENTATION_RESULT: stream=\nTime: 7\nOK (12 tests)\nINSTRUMENTATION_CODE: -1"))

    def test_exit_zero_without_pass_or_with_failure_is_not_success(self):
        for result in ["INSTRUMENTATION_CODE: 0", "OK (0 tests)", "OK (12 tests)\nFAILURES!!!",
                       "INSTRUMENTATION_FAILED: runner missing", "Process crashed."]:
            with self.subTest(result=result), self.assertRaises(ValueError):
                physical.successful_test_count(result)
