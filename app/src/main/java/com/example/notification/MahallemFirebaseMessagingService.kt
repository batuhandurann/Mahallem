package com.example.notification

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.backend.FunctionsRepository
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
        val destination = message.data["destination"].orEmpty()
        val conversationId = message.data["conversationId"].orEmpty()
        val isConversation = conversationId.matches(Regex("^[a-f0-9]{64}$"))
        val openRequests = destination == "MY_REQUESTS"

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (isConversation) putExtra(MainActivity.EXTRA_CONVERSATION_ID, conversationId)
            if (openRequests) putExtra(MainActivity.EXTRA_OPEN_MY_REQUESTS, true)
        }
        val requestCode = when {
            isConversation -> conversationId.hashCode()
            openRequests -> 0x4D5251
            else -> 0
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = message.notification?.title ?: "Mahallem"
        val body = message.notification?.body ?: when {
            openRequests -> "Talebiniz için yeni bir teklif geldi."
            else -> "Yeni bir mesajınız var."
        }
        val channel = if (openRequests) "mahallem_offers" else "mahallem_messages"

        val notification = NotificationCompat.Builder(this, channel)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(System.currentTimeMillis().toInt(), notification)
    }
}
