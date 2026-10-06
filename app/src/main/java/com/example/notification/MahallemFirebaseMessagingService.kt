package com.example.notification

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class MahallemFirebaseMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                FirebaseFirestore.getInstance().collection("users").document(uid).collection("devices").document(token)
                    .set(mapOf("platform" to "android", "updatedAt" to com.google.firebase.Timestamp.now())).await()
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        // Foreground notification UI can be implemented here.
    }
}