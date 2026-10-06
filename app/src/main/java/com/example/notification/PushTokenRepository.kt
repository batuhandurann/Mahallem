package com.example.notification

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await
import com.example.backend.FunctionsRepository

class PushTokenRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val messaging: FirebaseMessaging = FirebaseMessaging.getInstance()
) {
    suspend fun registerCurrentDevice() {
        if (auth.currentUser == null) return
        val token = messaging.token.await()
        FunctionsRepository().registerDeviceToken(token)
    }

    suspend fun unregisterCurrentDevice() {
        if (auth.currentUser == null) return
        val token = messaging.token.await()
        FunctionsRepository().unregisterDeviceToken(token)
    }
}
