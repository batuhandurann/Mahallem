package com.example.notification

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await

class PushTokenRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val messaging: FirebaseMessaging = FirebaseMessaging.getInstance()
) {
    suspend fun registerCurrentDevice() {
        val uid = auth.currentUser?.uid ?: return
        val token = messaging.token.await()
        firestore.collection("users").document(uid).collection("devices").document(token)
            .set(mapOf("platform" to "android", "updatedAt" to com.google.firebase.Timestamp.now())).await()
    }
}