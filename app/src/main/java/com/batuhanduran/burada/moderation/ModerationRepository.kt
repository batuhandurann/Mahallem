package com.batuhanduran.burada.moderation

import com.batuhanduran.burada.data.remote.FirebaseServices
import com.batuhanduran.burada.data.remote.AtomicWriteBudget
import com.batuhanduran.burada.data.remote.WriteOperation
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** A repository instance belongs to exactly one signed-in UID, never a device-global account. */
class ModerationRepository(
    private val db: FirebaseFirestore = FirebaseServices.firestore,
    private val auth: FirebaseAuth = FirebaseServices.auth,
    val uid: String = requireNotNull(auth.currentUser).uid
) {
    private val writeBudget = AtomicWriteBudget(db, uid)
    private fun requireAccount() {
        check(auth.currentUser?.uid == uid) { "Oturum değişti. Yeniden giriş yapın." }
    }

    fun observeBlockedUids(): Flow<Set<String>> = callbackFlow {
        if (auth.currentUser?.uid != uid) { trySend(emptySet()); close(); return@callbackFlow }
        val authListener = FirebaseAuth.AuthStateListener { currentAuth ->
            if (currentAuth.currentUser?.uid != uid) {
                trySend(emptySet())
                close()
            }
        }
        auth.addAuthStateListener(authListener)
        val registration = db.collection("users").document(uid).collection("blocks")
            .addSnapshotListener { snapshot, error ->
                if (auth.currentUser?.uid != uid) {
                    trySend(emptySet())
                    close()
                } else if (error != null) {
                    close(error)
                } else {
                    trySend(snapshot?.documents.orEmpty().map { it.id }.toSet())
                }
            }
        awaitClose {
            registration.remove()
            auth.removeAuthStateListener(authListener)
        }
    }

    suspend fun setBlocked(blockedUid: String, blocked: Boolean) {
        requireAccount()
        ModerationPolicy.validateBlock(uid, blockedUid)
        val ref = db.collection("users").document(uid).collection("blocks").document(blockedUid)
        if (blocked) ref.set(mapOf("blockedUid" to blockedUid, "createdAt" to FieldValue.serverTimestamp())).awaitModeration()
        else ref.delete().awaitModeration()
        requireAccount()
    }

    /** Only server/moderator code can change status, resolve, or access another reporter's report. */
    suspend fun submitReport(draft: ReportDraft): String {
        requireAccount()
        val report = draft.validated(uid)
        val ref = db.collection("reports").document()
        val fields = mapOf(
            "reporterUid" to uid,
            "targetType" to report.targetType.code,
            "targetId" to report.targetId,
            "targetUid" to report.targetUid,
            "reason" to report.reason.code,
            "details" to report.details,
            "status" to "pending",
            "createdAt" to FieldValue.serverTimestamp()
        )
        db.runTransaction { tx ->
            requireAccount()
            val budget = writeBudget.plan(tx, WriteOperation.REPORT, ref)
            budget.applyTo(tx)
            tx.set(ref, fields)
        }.awaitModeration()
        requireAccount()
        return ref.id
    }
}

private suspend fun <T> Task<T>.awaitModeration(): T = withTimeout(20_000) {
    suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
        addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
        addOnCanceledListener { continuation.cancel() }
    }
}
