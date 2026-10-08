"""Fail production signing while known release blockers remain."""
from pathlib import Path
import os
import re
import sys
from urllib.parse import urlparse

root = Path(__file__).resolve().parents[1]
gradle = (root / "app/build.gradle.kts").read_text(encoding="utf-8")
strings = (root / "app/src/main/res/values/strings.xml").read_text(encoding="utf-8")
privacy = (root / "app/src/main/java/com/example/ui/screens/PrivacyConsentScreen.kt").read_text(encoding="utf-8")

errors = []

version_code_raw = os.environ.get("ANDROID_VERSION_CODE", "").strip()
version_name = os.environ.get("ANDROID_VERSION_NAME", "").strip()
try:
    version_code = int(version_code_raw)
except ValueError:
    version_code = 0
if version_code < 1 or version_code > 2_100_000_000 or str(version_code) != version_code_raw:
    errors.append("ANDROID_VERSION_CODE must be a canonical positive integer <= 2100000000.")
if not version_name or version_name != version_name.strip() or len(version_name) > 100:
    errors.append("ANDROID_VERSION_NAME must be non-empty, trimmed and at most 100 characters.")

namespace = re.search(r'namespace\s*=\s*"([^"]+)"', gradle)
application_id = re.search(r'applicationId\s*=\s*"([^"]+)"', gradle)
app_name = re.search(r'<string\s+name="app_name">\s*([^<]+?)\s*</string>', strings)

if not namespace or namespace.group(1) == "com.example" or ".example" in namespace.group(1):
    errors.append("Android namespace is still a template/example namespace.")
if not application_id:
    errors.append("Android applicationId is missing.")
else:
    app_id = application_id.group(1)
    if app_id == "com.aistudio.mahallemde.kxqrvz" or "kxqrvz" in app_id or ".aistudio." in app_id:
        errors.append("Android applicationId is still the generated AI Studio identifier.")
if not app_name or not app_name.group(1).strip():
    errors.append("Application name is empty.")

legal_markers = [
    "üretime çıkmadan önce şirket bilgileriyle tamamlanmalıdır",
    "üretim yayını öncesinde hukuk onayıyla bu metne eklenmelidir",
]
if any(marker in privacy for marker in legal_markers):
    errors.append("Privacy notice still contains a production/legal placeholder.")

for env_name in ("PRIVACY_POLICY_URL", "ACCOUNT_DELETION_URL"):
    value = os.environ.get(env_name, "").strip()
    parsed = urlparse(value)
    if parsed.scheme != "https" or not parsed.netloc or parsed.hostname in {"example.com", "localhost"}:
        errors.append(f"{env_name} must be a real public HTTPS URL.")

if errors:
    print("Production release blocked:")
    for error in errors:
        print(f"- {error}")
    sys.exit(1)

print("Production preflight passed.")
print(f"namespace={namespace.group(1)}")
print(f"applicationId={application_id.group(1)}")
print(f"appName={app_name.group(1).strip()}")
