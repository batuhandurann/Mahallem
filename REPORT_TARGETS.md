# Report target authority and deployment

Reports require a canonical Firestore `targetRef` in the same named `mahallem`
database. This is separate from the write-budget `target`, which points to the
new report document and charges one report creation atomically.

| Target | Evidence required by Rules |
| --- | --- |
| Listing | Existing published provider/request, matching `provider:id` / `request:id`, and stored owner matching `targetUid` |
| Conversation | Existing conversation, reporter membership, and the other participant as `targetUid` |
| Message | Explicit `conversationId`, existing parent with reporter membership, exact nested message reference, and actual sender matching `targetUid` |
| User | Matching `targetId`, `targetUid` and `users/{uid}` reference; no claim that Firebase Auth existence was checked |

Self-reports, inaccessible conversations/messages, fabricated owners, nonexistent
content and references to another database are rejected. The target must exist
before the write: fabricating and reporting content in one batch does not establish
evidence. Participants can still report historical messages after blocking.

Firebase Rules cannot query Auth user existence. Private profiles are optional,
so USER reports do not require reading them. Before adding any user suspension
action, the trusted moderator backend must verify the actual Auth user. Current
actions remain dismissing a report or hiding a verified matching listing.

Deploy the updated Rules and Android client together. Older clients omit
`targetRef` / `conversationId` and their new reports fail closed after the Rules
change. Existing reports remain readable and reviewable. The moderation callable
returns `targetRefPath` as a string, or null for legacy reports, and never exposes
Admin SDK reference internals.

Local Rules, callable and configuration tests are automated. Actual Android SDK
regressions run in Android Quality CI. Emulator evidence does not establish live
deployment, App Check enforcement or physical-device verification.
