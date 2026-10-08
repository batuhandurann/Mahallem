"""Fail unless real Android archives exist; record sizes, hashes and source SHA."""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import zipfile

root = Path(__file__).resolve().parents[1]
variants = {
    'debug': ['app/build/outputs/apk/debug/app-debug.apk'],
    'release': [
        'app/build/outputs/apk/release/app-release-unsigned.apk',
        'app/build/outputs/bundle/release/app-release.aab',
    ],
}
variant = sys.argv[1]
artifacts = []
for filename in variants[variant]:
    path = root / filename
    if not path.is_file() or path.stat().st_size == 0:
        raise SystemExit(f'Missing or empty build artifact: {filename}')
    with zipfile.ZipFile(path) as archive:
        manifest = 'base/manifest/AndroidManifest.xml' if path.suffix == '.aab' else 'AndroidManifest.xml'
        if archive.testzip() or manifest not in archive.namelist():
            raise SystemExit(f'Invalid Android archive: {filename}')
    artifacts.append({
        'path': filename, 'bytes': path.stat().st_size,
        'sha256': hashlib.sha256(path.read_bytes()).hexdigest(),
    })
report = {
    'commit': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root, text=True).strip(),
    'workflow_sha': os.environ.get('GITHUB_SHA'),
    'run_id': os.environ.get('GITHUB_RUN_ID'),
    'variant': variant,
    'production_ready': False,
    'limitation': 'CI has no production Firebase/Maps config; release archives are unsigned. Device/auth smoke approval remains required.',
    'artifacts': artifacts,
}
output = root / f'app/build/reports/build-evidence-{variant}.json'
output.parent.mkdir(parents=True, exist_ok=True)
output.write_text(json.dumps(report, indent=2) + '\n')
print(json.dumps(report, indent=2))
