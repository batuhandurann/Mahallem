package com.example.notification

import com.example.backend.FunctionsRepository
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import androidx.core.app.NotificationCompat
import android.app.NotificationManager
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class MahallemFirebaseMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                FunctionsRepository().registerDeviceToken(token)
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title ?: "Mahallem"
        val body = "Yeni bir mesajınız var."
        val notification = NotificationCompat.Builder(this, "mahallem_messages")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .build()
        getSystemService(Context.NOTIFICATION_SERVICE)
            .let { (it as NotificationManager).notify(System.currentTimeMillis().toInt(), notification) }
    }
}