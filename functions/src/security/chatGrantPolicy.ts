/**
 * Pure, fail-closed policy for authorizing a new chat-media upload grant.
 * Firestore block documents MUST be read by the caller; this helper never
 * treats a missing/invalid participant list as an allowed conversation.
 */
const UID_PATTERN = /^[A-Za-z0-9_-]{1,80}$/;

export function chatGrantRecipient(participants: unknown, callerUid: string): string | null {
  if (!UID_PATTERN.test(callerUid)
    || !Array.isArray(participants)
    || participants.length !== 2
    || !participants.every((uid) => typeof uid === "string" && UID_PATTERN.test(uid))
    || !participants.includes(callerUid)) {
    return null;
  }
  const other = participants.find((uid: string) => uid !== callerUid);
  return typeof other === "string" ? other : null;
}

export function canIssueChatGrant(
  participants: unknown,
  callerUid: string,
  callerBlockedRecipient: boolean,
  recipientBlockedCaller: boolean,
): boolean {
  return chatGrantRecipient(participants, callerUid) !== null
    && !callerBlockedRecipient
    && !recipientBlockedCaller;
}
