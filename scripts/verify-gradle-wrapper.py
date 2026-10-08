"""Verify the checked-in wrapper before executing it, without network access.

Pins verified against https://services.gradle.org/distributions/
gradle-9.3.1-{wrapper.jar,bin.zip}.sha256 on 2026-10-08.
"""
import hashlib
from pathlib import Path
import sys

JAR_SHA256 = "b3a875ddc1f044746e1b1a55f645584505f4a10438c1afea9f15e92a7c42ec13"
DISTRIBUTION_SHA256 = "b266d5ff6b90eada6dc3b20cb090e3731302e553a27c5d3e4df1f0d76beaff06"


def verify(root: Path) -> None:
    for name in ("gradlew", "gradlew.bat", "gradle/wrapper/gradle-wrapper.jar"):
        if not (root / name).is_file():
            raise ValueError(f"Missing wrapper file: {name}")
    if not (root / "gradlew").stat().st_mode & 0o111:
        raise ValueError("gradlew must have an executable Git file mode")
    jar = root / "gradle/wrapper/gradle-wrapper.jar"
    if hashlib.sha256(jar.read_bytes()).hexdigest() != JAR_SHA256:
        raise ValueError("Gradle wrapper JAR SHA-256 mismatch")
    props = dict(line.split("=", 1) for line in
                 (root / "gradle/wrapper/gradle-wrapper.properties").read_text().splitlines()
                 if "=" in line and not line.startswith("#"))
    if props.get("distributionSha256Sum") != DISTRIBUTION_SHA256:
        raise ValueError("Gradle distribution SHA-256 mismatch")
    if props.get("distributionUrl") != r"https\://services.gradle.org/distributions/gradle-9.3.1-bin.zip":
        raise ValueError("Unexpected Gradle distribution URL")
    if props.get("validateDistributionUrl") != "true":
        raise ValueError("Distribution URL validation must stay enabled")


if __name__ == "__main__":
    try:
        verify(Path(__file__).resolve().parents[1])
    except (ValueError, OSError) as error:
        sys.exit(str(error))
    print("Gradle 9.3.1 wrapper JAR and distribution SHA-256 pins verified.")
