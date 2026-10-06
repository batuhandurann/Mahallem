package com.example.auth

import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await

class AccountLifecycleRepository(
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance("europe-west1")
) {
    suspend fun requestDeletion(): Int {
        val result = functions.getHttpsCallable("requestAccountDeletion").call().await()
        val data = result.data as? Map<*, *> ?: error("Sunucudan geçersiz silme yanıtı.")
        return (data["dueInDays"] as? Number)?.toInt() ?: 30
    }

    suspend fun cancelDeletion(): Boolean {
        val result = functions.getHttpsCallable("cancelAccountDeletion").call().await()
        val data = result.data as? Map<*, *> ?: error("Sunucudan geçersiz iptal yanıtı.")
        return data["canceled"] == true
    }
}
