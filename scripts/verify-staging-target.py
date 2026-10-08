#!/usr/bin/env python3
"""Fail closed before manually deploying Firebase staging resources."""
import json
import os
import re
import sys

PROJECT_ID = re.compile(r"^[a-z][a-z0-9-]{4,28}[a-z0-9]$")


def verify_target(staging: str, production: str, credentials_json: str) -> None:
    if not staging or not production:
        raise ValueError("Both staging and production project IDs are required")
    if not PROJECT_ID.fullmatch(staging) or not PROJECT_ID.fullmatch(production):
        raise ValueError("Invalid Firebase project ID")
    if staging == production:
        raise ValueError("Staging and production project IDs must differ")
    if not credentials_json:
        raise ValueError("Staging service-account credentials are required")
    try:
        credentials = json.loads(credentials_json)
    except (ValueError, TypeError) as exc:
        raise ValueError("Staging credentials must be valid JSON") from exc
    if not isinstance(credentials, dict) or credentials.get("type") != "service_account":
        raise ValueError("A staging service account is required")
    if credentials.get("project_id") != staging:
        raise ValueError("Service-account project must match the staging target")
    email = credentials.get("client_email")
    if not isinstance(email, str) or not email.endswith("@" + staging + ".iam.gserviceaccount.com"):
        raise ValueError("Service-account email must belong to the staging project")


def main() -> int:
    try:
        verify_target(
            os.environ.get("FIREBASE_PROJECT_ID_STAGING", ""),
            os.environ.get("FIREBASE_PROJECT_ID_PRODUCTION", ""),
            os.environ.get("FIREBASE_SERVICE_ACCOUNT_STAGING", ""),
        )
    except ValueError as exc:
        print("Staging target verification failed: " + str(exc), file=sys.stderr)
        return 1
    print("Staging identity verified; no deployment performed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
