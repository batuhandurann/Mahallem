import importlib.util
import json
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location('guard', Path(__file__).resolve().parents[1] / 'verify-staging-target.py')
guard = importlib.util.module_from_spec(spec)
spec.loader.exec_module(guard)

class StagingTargetTests(unittest.TestCase):
    def test_rejects_missing_production(self):
        with self.assertRaises(ValueError):
            guard.verify_target('mahallem-staging', '', '{}')

    def test_rejects_same_project(self):
        with self.assertRaises(ValueError):
            guard.verify_target('mahallem-staging', 'mahallem-staging', '{}')

    def test_rejects_bad_service_account(self):
        with self.assertRaises(ValueError):
            guard.verify_target('mahallem-staging', 'mahallem-production', json.dumps({'type':'service_account','project_id':'mahallem-production'}))

    def test_accepts_matching_staging_service_account(self):
        creds = json.dumps({'type': 'service_account', 'project_id': 'mahallem-staging', 'client_email': 'deploy@mahallem-staging.iam.gserviceaccount.com'})
        guard.verify_target('mahallem-staging', 'mahallem-production', creds)

    def test_rejects_invalid_credentials_json(self):
        with self.assertRaises(ValueError):
            guard.verify_target('mahallem-staging', 'mahallem-production', '{')

    def test_rejects_mismatched_service_account_email(self):
        creds = json.dumps({'type': 'service_account', 'project_id': 'mahallem-staging', 'client_email': 'deploy@mahallem-production.iam.gserviceaccount.com'})
        with self.assertRaises(ValueError):
            guard.verify_target('mahallem-staging', 'mahallem-production', creds)

if __name__ == '__main__':
    unittest.main()
