"""Validate Android build archives and record reproducible build evidence."""
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


def validate_archive(path: Path) -> None:
    if not path.is_file() or path.stat().st_size == 0:
        raise SystemExit(f'Missing or empty build artifact: {path.name}')
    expected = (
        ('BundleConfig.pb', 'base/manifest/AndroidManifest.xml', 'base/dex/classes.dex')
        if path.suffix == '.aab' else
        ('AndroidManifest.xml', 'classes.dex', 'resources.arsc')
    )
    try:
        with zipfile.ZipFile(path) as archive:
            names = set(archive.namelist())
            if archive.testzip() or any(
                name not in names or archive.getinfo(name).file_size == 0
                for name in expected
            ):
                raise SystemExit(f'Invalid Android archive: {path.name}')
    except (zipfile.BadZipFile, OSError) as error:
        raise SystemExit(f'Unreadable Android archive: {path.name}: {error}') from error


def main() -> None:
    if len(sys.argv) != 2 or sys.argv[1] not in variants:
        raise SystemExit('Usage: python3 scripts/build-evidence.py {debug|release}')
    variant = sys.argv[1]
    commit = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root, text=True).strip()
    workflow_sha = os.environ.get('GITHUB_SHA')
    if workflow_sha and workflow_sha != commit:
        raise SystemExit(f'Workflow SHA mismatch: checked-out commit {commit} != GITHUB_SHA {workflow_sha}')
    artifacts = []
    for filename in variants[variant]:
        path = root / filename
        validate_archive(path)
        with path.open('rb') as source:
            sha256 = hashlib.file_digest(source, 'sha256').hexdigest()
        artifacts.append({
            'path': filename, 'bytes': path.stat().st_size,
            'sha256': sha256,
        })
    report = {
        'commit': commit,
        'workflow_sha': workflow_sha,
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


if __name__ == '__main__':
    main()
