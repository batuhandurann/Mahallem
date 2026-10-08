package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.example.core.AppEnvironment
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics

class MahallemApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        com.example.data.local.AppDatabase.initialize(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("mahallem_messages", "Mahallem Mesajları", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Yeni mesaj ve hizmet güncellemeleri"
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
            val offersChannel = NotificationChannel(
                "mahallem_offers",
                "Mahallem Teklifleri",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Yeni teklif ve iş akışı bildirimleri"
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(offersChannel)
        }
        if (FirebaseApp.getApps(this).isEmpty()) return

        if (AppEnvironment.isLocal) {
            runCatching {
                FirebaseAuth.getInstance().useEmulator("10.0.2.2", 9099)
                FirebaseFirestore.getInstance().useEmulator("10.0.2.2", 8080)
                FirebaseFunctions.getInstance("europe-west1").useEmulator("10.0.2.2", 5001)
                FirebaseStorage.getInstance().useEmulator("10.0.2.2", 9199)
            }
        }

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

        // Analytics defaults to disabled in AndroidManifest. MarketplaceApp enables it
        // only after the active user's UID-scoped consent has been loaded.
        runCatching {
            FirebaseAnalytics.getInstance(this).setAnalyticsCollectionEnabled(false)
        }
    }
}
