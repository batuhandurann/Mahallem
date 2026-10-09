#!/usr/bin/env python3
"""Fail closed if a main-targeted PR reintroduces legacy Android identity."""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
GRADLE = ROOT / "app/build.gradle.kts"
MANIFEST = ROOT / "app/src/main/AndroidManifest.xml"
EXPECTED = "com.batuhanduran.burada"


def check() -> list[str]:
    problems = []
    if not GRADLE.is_file() or not MANIFEST.is_file():
        return ["Canonical Android Gradle file or manifest missing"]
    gradle = GRADLE.read_text(encoding="utf-8")
    manifest = MANIFEST.read_text(encoding="utf-8")
    for key in ("namespace", "applicationId"):
        match = re.search(r"(?m)^\s*" + key + r'\s*=\s*"([^"]+)"', gradle)
        if not match or match.group(1) != EXPECTED:
            problems.append(f"{key} must equal {EXPECTED}")
    if "com.example" in manifest:
        problems.append("Manifest contains legacy com.example reference")
    legacy = ROOT / "app/src/main/java/com/example"
    if legacy.exists() and any(legacy.rglob("*.kt")):
        problems.append("Legacy com.example Kotlin sources in canonical app")
    return problems


if __name__ == "__main__":
    errors = check()
    for error in errors:
        print("ERROR:", error, file=sys.stderr)
    if errors:
        sys.exit(1)
    print("Canonical Android identity verified")
