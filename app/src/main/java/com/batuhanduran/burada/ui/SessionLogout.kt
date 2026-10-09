package com.batuhanduran.burada.ui

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

/** Remote device cleanup must not prevent logout or sign out a replacement account. */
internal suspend fun finishSessionLogout(
    leavingUid: String,
    currentUid: () -> String?,
    releasePush: suspend () -> Unit,
    signOut: () -> Unit
) {
    try {
        withTimeoutOrNull(5_000) { releasePush() }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        // Local session data was already cleared. Network/SDK cleanup is best effort.
    } finally {
        if (currentUid() == leavingUid) signOut()
    }
}
