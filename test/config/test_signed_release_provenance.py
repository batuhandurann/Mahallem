"""Fail-closed provenance tests using synthetic SHAs; no production keys or network."""
import importlib.util
import os
from pathlib import Path
import unittest
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location(
    "signed_release_provenance",
    Path(__file__).parents[2] / "scripts/verify_signed_release.py",
)
release = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(release)

SOURCE_SHA = "a" * 40
OTHER_SHA = "b" * 40


class SignedReleaseProvenanceTest(unittest.TestCase):
    def test_github_actions_checkout_must_match_immutable_source_sha(self):
        with patch.dict(os.environ, {"GITHUB_ACTIONS": "true", "GITHUB_SHA": SOURCE_SHA}, clear=True):
            with patch.object(release, "run", side_effect=[SOURCE_SHA.encode() + b"\n", b""]) as command:
                proof = release.verified_checkout_provenance()
        self.assertEqual(proof, {
            "source_commit_sha": SOURCE_SHA,
            "github_actions_source_verified": True,
        })
        command.assert_any_call(["git", "diff", "--quiet", "HEAD", "--"])
        self.assertEqual(command.call_count, 2)

    def test_actions_missing_or_mismatched_sha_fails_closed(self):
        for expected in ("", OTHER_SHA, "short", "z" * 40):
            with self.subTest(expected=expected):
                with patch.dict(os.environ, {"GITHUB_ACTIONS": "true", "GITHUB_SHA": expected}, clear=True):
                    with patch.object(release, "run", side_effect=[SOURCE_SHA.encode(), b""]):
                        with self.assertRaises(ValueError):
                            release.verified_checkout_provenance()

    def test_invalid_checkout_sha_fails_closed(self):
        for actual in ("", "short", "z" * 40):
            with self.subTest(actual=actual):
                with patch.dict(os.environ, {"GITHUB_ACTIONS": "true", "GITHUB_SHA": SOURCE_SHA}, clear=True):
                    with patch.object(release, "run", return_value=actual.encode()):
                        with self.assertRaises(ValueError):
                            release.verified_checkout_provenance()

    def test_modified_tracked_source_fails_closed(self):
        with patch.dict(os.environ, {"GITHUB_ACTIONS": "true", "GITHUB_SHA": SOURCE_SHA}, clear=True):
            with patch.object(release, "run", side_effect=[SOURCE_SHA.encode(), ValueError("dirty tree")]):
                with self.assertRaises(ValueError):
                    release.verified_checkout_provenance()

    def test_local_checkout_does_not_claim_github_actions_attestation(self):
        with patch.dict(os.environ, {}, clear=True):
            with patch.object(release, "run", side_effect=[SOURCE_SHA.encode(), b""]):
                proof = release.verified_checkout_provenance()
        self.assertEqual(proof["source_commit_sha"], SOURCE_SHA)
        self.assertFalse(proof["github_actions_source_verified"])


if __name__ == "__main__":
    unittest.main()
