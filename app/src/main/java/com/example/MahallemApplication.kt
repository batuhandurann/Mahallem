package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.example.core.AppEnvironment
import com.example.privacy.ConsentRepository
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseException
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics

class MahallemApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("mahallem_messages", "Mahallem Mesajları", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Yeni mesaj ve hizmet güncellemeleri"
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        if (FirebaseApp.getApps(this).isEmpty()) return

        runCatching {
            val appCheck = FirebaseAppCheck.getInstance()
            if (AppEnvironment.mode == AppEnvironment.Mode.LOCAL) {
                appCheck.installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
            } else {
                appCheck.installAppCheckProviderFactory(
                    PlayIntegrityAppCheckProviderFactory.getInstance()
                )
            }
        }

        runCatching {
            FirebaseCrashlytics.getInstance().isCrashlyticsCollectionEnabled =
                AppEnvironment.mode != AppEnvironment.Mode.LOCAL
        }

        runCatching {
            FirebaseAnalytics.getInstance(this).setAnalyticsCollectionEnabled(ConsentRepository(this).analyticsConsent)
        }
    }
}
