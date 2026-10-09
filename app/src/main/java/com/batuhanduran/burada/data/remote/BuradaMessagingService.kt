package com.batuhanduran.burada.data.remote

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.batuhanduran.burada.MainActivity
import com.batuhanduran.burada.R
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class BuradaMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) { PushTokenLifecycle.register(token) }

    override fun onMessageReceived(message: RemoteMessage) {
        // Serialize display with main-thread sign-out/account switches and notification cleanup.
        Handler(Looper.getMainLooper()).post { displayForCurrentAccount(message) }
    }

    private fun displayForCurrentAccount(message: RemoteMessage) {
        val uid = FirebaseServices.auth.currentUser?.uid ?: return
        // Backend sends data-only messages so Android never bypasses this account guard.
        if (!PushEnvelopePolicy.shouldDisplay(message.data, uid)) return
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val id = message.data["messageId"] ?: return
        BuradaNotifications.createChannel(this)
        val openApp = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(this, id.hashCode(), openApp,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, BuradaNotifications.CHANNEL)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Yeni mesaj")
            .setContentText(getString(R.string.chat_new_message_notification))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(pending).setAutoCancel(true).build()
        // No sender name, text, photo or precise location appears on a lock screen.
        getSystemService(NotificationManager::class.java).notify(id.hashCode(), notification)
    }
}

object BuradaNotifications {
    const val CHANNEL = "private_messages"
    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= 26) context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CHANNEL, "Mesajlar", NotificationManager.IMPORTANCE_DEFAULT))
    }
    fun clear(context: Context) { context.getSystemService(NotificationManager::class.java).cancelAll() }
}
