import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location("physical", Path(__file__).resolve().parents[2] / "scripts/physical_android_smoke.py")
physical = importlib.util.module_from_spec(spec)
spec.loader.exec_module(physical)


class PhysicalRunnerTest(unittest.TestCase):
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
