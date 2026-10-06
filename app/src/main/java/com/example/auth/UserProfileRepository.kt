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
            ref.set(
                mapOf(
                    "uid" to user.uid,
                    "displayName" to (user.displayName ?: ""),
                    "photoUrl" to user.photoUrl?.toString(),
                    "phoneNumber" to user.phoneNumber,
                    "role" to "user",
                    "createdAt" to Timestamp.now(),
                    "updatedAt" to Timestamp.now()
                ),
                SetOptions.merge()
            ).await()
        } else {
            ref.set(mapOf("updatedAt" to Timestamp.now()), SetOptions.merge()).await()
        }
    }
}