"""Regression checks for early, non-disclosing production-signing preflight."""
import os
from pathlib import Path
import subprocess
import unittest

ROOT = Path(__file__).resolve().parents[2]
WORKFLOW = ROOT / ".github/workflows/signed-production-aab.yml"
INPUTS = ("FIREBASE_CONFIG_B64", "KEYSTORE_B64", "STORE_PASSWORD", "KEY_PASSWORD")


def release_preflight():
    content = WORKFLOW.read_text(encoding="utf-8")
    start = content.index("      - name: Check protected signing inputs before downloading Android SDK")
    end = content.index("      - name: Checkout", start)
    block = content[start:end].split("        run: |\n", 1)[1]
    lines = block.splitlines()
    return content, "\n".join(line[10:] if line.startswith("          ") else line for line in lines)


class ReleaseWorkflowPreflightTests(unittest.TestCase):
    def test_checks_secrets_before_toolchain_setup(self):
        content, script = release_preflight()
        self.assertLess(content.index("      - name: Check protected signing inputs"),
                        content.index("      - name: Set up JDK 21"))
        self.assertLess(content.index("      - name: Check protected signing inputs"),
                        content.index("      - name: Set up Android SDK"))
        for name in INPUTS:
            self.assertIn(name, script)
        self.assertIn('if [[ -z "${!name:-}" ]]', script)

    def run_preflight(self, missing=()):
        # Use synthetic values only; never access protected CI secrets.
        env = {**os.environ, **{key: "fixture-SENSITIVE-" + key for key in INPUTS}}
        for key in missing:
            env.pop(key, None)
        _, script = release_preflight()
        return subprocess.run(["bash", "-c", script], env=env,
                              text=True, capture_output=True, check=False)

    def test_missing_each_required_input_fails_without_printing_secret(self):
        for missing in INPUTS:
            with self.subTest(missing=missing):
                result = self.run_preflight((missing,))
                self.assertNotEqual(0, result.returncode)
                self.assertIn(missing, result.stdout + result.stderr)
                self.assertNotIn("fixture-SENSITIVE-", result.stdout + result.stderr)

    def test_all_inputs_present_succeeds_without_disclosing_values(self):
        result = self.run_preflight()
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertNotIn("fixture-SENSITIVE-", result.stdout + result.stderr)


if __name__ == "__main__":
    unittest.main()
