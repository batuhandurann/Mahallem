package com.example.auth

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

class UserProfileRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    suspend fun ensureUserProfile(user: FirebaseUser) {
        val ref = firestore.collection("users").document(user.uid)
        val snapshot = ref.get().await()
        if (!snapshot.exists()) {
            val profile = mutableMapOf<String, Any>(
                "uid" to user.uid,
                "displayName" to (user.displayName ?: ""),
                "role" to "user",
                "createdAt" to Timestamp.now(),
                "updatedAt" to Timestamp.now()
            )
            user.photoUrl?.toString()?.takeIf { it.isNotBlank() }?.let { profile["photoUrl"] = it }
            user.phoneNumber?.takeIf { it.isNotBlank() }?.let { profile["phoneNumber"] = it }
            ref.set(profile, SetOptions.merge()).await()
        } else {
            ref.set(mapOf("updatedAt" to Timestamp.now()), SetOptions.merge()).await()
        }
    }

    suspend fun saveNotificationPreferences(
        messagesEnabled: Boolean,
        marketingEnabled: Boolean
    ) {
        val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
            ?: error("Giriş gerekli.")
        firestore.collection("users").document(user.uid).set(
            mapOf(
                "notificationPreferences" to mapOf(
                    "messages" to messagesEnabled,
                    "offers" to marketingEnabled
                ),
                "updatedAt" to Timestamp.now()
            ),
            SetOptions.merge()
        ).await()
    }
}