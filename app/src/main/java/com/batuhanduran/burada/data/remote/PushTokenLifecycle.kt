package com.batuhanduran.burada.data.remote

import android.content.Context
import com.batuhanduran.burada.BuildConfig
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.messaging.FirebaseMessaging
import java.security.MessageDigest
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Device registrations contain no profile data. Never log tokens. */
object PushTokenLifecycle {
    private var started = false
    @Volatile private var registeredUid: String? = null
    @Volatile private var registeredToken: String? = null

    fun start(context: Context) {
        BuradaNotifications.createChannel(context)
        if (started || BuildConfig.USE_FIREBASE_EMULATORS) return
        started = true
        FirebaseServices.auth.addAuthStateListener(FirebaseAuth.AuthStateListener { auth ->
            val uid = auth.currentUser?.uid
            if (registeredUid != uid) {
                // Immediately suppress delivery for the previous account, including cached notifications.
                BuradaNotifications.clear(context)
                registeredUid = null
                registeredToken = null
            }
            if (uid != null) FirebaseMessaging.getInstance().token.addOnSuccessListener { register(it) }
        })
    }

    fun register(token: String) {
        if (BuildConfig.USE_FIREBASE_EMULATORS || token.isBlank()) return
        val uid = FirebaseServices.auth.currentUser?.uid ?: return
        val ref = FirebaseServices.firestore.collection("users").document(uid)
            .collection("devices").document(tokenId(token))
        ref.set(mapOf("token" to token, "platform" to "android", "updatedAt" to FieldValue.serverTimestamp()))
            .addOnSuccessListener {
                if (FirebaseServices.auth.currentUser?.uid == uid) {
                    registeredUid = uid
                    registeredToken = token
                } else {
                    // No token is retained locally across account switches. A late server registration
                    // is harmless: service compares recipientUid against the active account.
                    FirebaseMessaging.getInstance().deleteToken()
                }
            }
    }

    /** Call while still authenticated, before signOut. Local suppression works even while offline. */
    suspend fun release(context: Context) {
        BuradaNotifications.clear(context)
        val uid = FirebaseServices.auth.currentUser?.uid
        val token = registeredToken
        registeredUid = null
        registeredToken = null
        if (BuildConfig.USE_FIREBASE_EMULATORS) return
        if (uid != null && token != null) {
            // Best effort remote removal; do not trap a user in an account if the network is unavailable.
            val task = FirebaseServices.firestore.collection("users").document(uid)
                .collection("devices").document(tokenId(token)).delete()
            kotlinx.coroutines.withTimeoutOrNull(5_000) {
                suspendCancellableCoroutine<Unit> { c ->
                    task.addOnCompleteListener { if (c.isActive) c.resume(Unit) }
                }
            }
        }
        FirebaseMessaging.getInstance().deleteToken()
    }

    internal fun tokenId(token: String): String = MessageDigest.getInstance("SHA-256")
        .digest(token.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
