# Working on Burada / Mahallem

- Start by reading `PROGRESS.md`, this file and `git status`. Fetch the relevant remote branch before using an older local checkout.
- Keep completed, tested features. Make changes in small, independently reviewable steps.
- Canonical Android identity: `com.batuhanduran.burada`, visible brand `Yakıno`. The repository and named Firebase database retain `Mahallem` / `mahallem` compatibility identifiers until an explicit registration/data migration is verified.
- Published listings intentionally appear in signed-in discovery. Profiles, contacts, quotes, favorites, device tokens and conversations are UID scoped. Preserve account-switch guards and server-side Rules checks.
- Use `demo-mahallem` and `-PfirebaseEmulators=true` for tests. Never silently fall back to production Firebase or demo data in live builds.
- Production configuration and signing credentials belong in protected secrets/temporary files. Never commit keys, passwords, service-account credentials or private Cloud evidence.
- Report actual build/test results, their commit and environment. Unsigned CI output, fixture keys, emulator tests and prepared workflows do not prove signed production builds, physical-device testing, deployment or Console enforcement.
- Run checks appropriate to the changed area; new Python release/device helpers are covered by `python3 -m unittest discover -s test/config -p 'test_*.py'`. Preserve the required GitHub checks; do not bypass protection to merge.
- Update `PROGRESS.md` with completed work, test evidence, remaining access requirements and the next concrete task. Do not repeat successful expensive checks unless a change or unresolved failure warrants them.
