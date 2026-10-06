package com.example.backend

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

class FirestoreRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    suspend fun saveUserProfile(
        userId: String,
        data: Map<String, Any?>
    ) {
        firestore.collection("users")
            .document(userId)
            .set(data, SetOptions.merge())
            .await()
    }

    suspend fun saveJobRequest(
        requestId: String,
        data: Map<String, Any?>
    ) {
        firestore.collection("jobRequests")
            .document(requestId)
            .set(data, SetOptions.merge())
            .await()
    }

    suspend fun saveProvider(
        providerId: String,
        data: Map<String, Any?>
    ) {
        firestore.collection("providers")
            .document(providerId)
            .set(data, SetOptions.merge())
            .await()
    }
}