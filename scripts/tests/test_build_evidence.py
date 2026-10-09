"""Fast offline regression tests for Android APK/AAB build evidence."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest
import zipfile

SCRIPT = Path(__file__).resolve().parents[1] / 'build-evidence.py'


class BuildEvidenceTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        (self.root / 'scripts').mkdir()
        shutil.copyfile(SCRIPT, self.root / 'scripts' / 'build-evidence.py')
        subprocess.run(['git', 'init', '-q'], cwd=self.root, check=True)
        subprocess.run(['git', '-c', 'user.name=CI Test', '-c', 'user.email=ci@example.invalid',
                        'commit', '--allow-empty', '-qm', 'test'], cwd=self.root, check=True)
        self.sha = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=self.root, text=True).strip()

    def make_archive(self, relpath, names):
        target = self.root / relpath
        target.parent.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(target, 'w') as archive:
            for name in names:
                archive.writestr(name, b'non-empty test content')
        return target

    def invoke(self, variant='debug', sha=None):
        env = os.environ.copy()
        env.pop('GITHUB_SHA', None)
        if sha is not None:
            env['GITHUB_SHA'] = sha
        return subprocess.run([sys.executable, 'scripts/build-evidence.py', variant],
                              cwd=self.root, env=env, capture_output=True, text=True)

    def test_valid_debug_archive_records_sha(self):
        self.make_archive('app/build/outputs/apk/debug/app-debug.apk',
                          ['AndroidManifest.xml', 'classes.dex', 'resources.arsc'])
        result = self.invoke(sha=self.sha)
        self.assertEqual(result.returncode, 0, result.stderr)
        evidence = json.loads((self.root / 'app/build/reports/build-evidence-debug.json').read_text())
        self.assertEqual(evidence['commit'], self.sha)
        self.assertEqual(evidence['workflow_sha'], self.sha)
        self.assertFalse(evidence['production_ready'])
        self.assertEqual(len(evidence['artifacts'][0]['sha256']), 64)

    def test_rejects_mismatched_ci_sha(self):
        self.make_archive('app/build/outputs/apk/debug/app-debug.apk',
                          ['AndroidManifest.xml', 'classes.dex', 'resources.arsc'])
        result = self.invoke(sha='0' * 40)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('Workflow SHA mismatch', result.stderr)

    def test_rejects_missing_dex(self):
        self.make_archive('app/build/outputs/apk/debug/app-debug.apk', ['AndroidManifest.xml', 'resources.arsc'])
        result = self.invoke()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('Invalid Android archive', result.stderr)

    def test_rejects_corrupt_zip(self):
        path = self.root / 'app/build/outputs/apk/debug/app-debug.apk'
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(b'not an apk')
        result = self.invoke()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('Unreadable Android archive', result.stderr)

    def test_release_requires_both_apk_and_aab(self):
        self.make_archive('app/build/outputs/apk/release/app-release-unsigned.apk',
                          ['AndroidManifest.xml', 'classes.dex', 'resources.arsc'])
        self.assertNotEqual(self.invoke('release').returncode, 0)
        self.make_archive('app/build/outputs/bundle/release/app-release.aab',
                          ['BundleConfig.pb', 'base/manifest/AndroidManifest.xml', 'base/dex/classes.dex'])
        self.assertEqual(self.invoke('release').returncode, 0)

    def test_rejects_unknown_variant(self):
        result = self.invoke('unknown')
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('Usage:', result.stderr)


if __name__ == '__main__':
    unittest.main()
