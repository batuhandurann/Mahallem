# Yakıno profile and account lifecycle

Base: main 8042ff209c3be84c44de74a8b99fe63c39a9cba9. Android/Firebase identity
is unchanged. This branch does not merge unrelated pending feature branches.

## Product references (2026-10-09)

Armut: Account / Data and Privacy / Delete account.
https://armuttr.zendesk.com/hc/tr/articles/24508182879132
Fiverr: edit/manage service ads and confirmed permanent account deletion.
https://help.fiverr.com/hc/en-us/articles/360010750318
https://help.fiverr.com/hc/en-us/articles/37331887967761
Taskrabbit: agreed task scope cannot be changed unilaterally.
https://support.taskrabbit.com/hc/en-gb/articles/46260446684571

## Implemented behavior

- Private profile name/bio editing; private email remains read-only. Login reads
  canonical Firestore profile and cannot overwrite edits with stale Auth data.
- Owner-only bounded listing text edits with timestamp revision conflict checks.
  Immutable identity, location/category and trust fields cannot be patched.
- Customer request scope locks after any quote. Pending requests can be removed
  with confirmation; agreed requests use job management instead. Provider ad
  removal does not alter accepted quote snapshots. Hidden/reported/archived ads
  cannot republish. Removal archives, stops discovery/new offers/acceptance and
  keeps history until account deletion.
- Deletion uses password reauthentication, fresh Auth evidence within 5 minutes
  and exact typed confirmation. Active work/locked payments/disputes block it.
  Historical accepted quotes are checked against terminal job status, not treated
  as active forever. Inconsistent agreement links fail closed.
- A server-owned durable UID tombstone prevents cached-token reads/writes and
  acceptance racing deletion, including after the private profile is removed.
  The callable only acknowledges REQUESTED. A retry-enabled worker disables and
  deletes Auth, own listings/contacts/quotes, own messages/media and private user
  subcollections. Other accounts/messages remain; shared names/previews anonymize.
  Late photo commits check the tombstone transactionally. A retry-enabled Storage
  finalization trigger erases late objects even after a process crash or completed purge.

## Data retention and limits

Moderation/security reports, audit evidence and minimal deletion tombstone remain.
Existing abuse/delivery metadata follows its TTL. Do not set TTL on deletion jobs.
No promise is made to purge every forensic record. Production retention wording
and operational review remain necessary. Account freezing, export, avatar upload,
email change and listing contact/location editing are outside this batch.

Pending job/reviews branches require integration validation before combined
release: terminal jobs/events and anonymous reviews need a defined retention
policy; do not claim their cleanup is covered by this main-based implementation.

## Release

Deploy Rules and trusted Functions together before shipping the Android screen.
New App Check enforced callables: getAccountProfile, updateAccountProfile,
getListingManagement, manageListing, requestAccountDeletion. New named-database
retry trigger: purgeDeletedAccount; Storage retry trigger: purgeDeletedAccountPhoto.
Production verifier checks all seven and the exact Storage bucket.
Existing Admin Auth/Firestore/Storage service permissions are required.
Monitor _accountDeletions jobs stuck REQUESTED/PURGING and retry failures; shared
conversation cleanup is paginated and checkpoints progress for timeout retries.

Tests run only on demo-mahallem: input policy, Rules stale-token and archived
listing/acceptance gates, actual callables and triggered deletion with real Auth,
Firestore and Storage emulators, Android screen editing/confirmation/relogin.
Source/CI success does not prove production deployment or real-device operation.
