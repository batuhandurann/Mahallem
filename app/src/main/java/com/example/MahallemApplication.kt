package com.example

import android.app.Application
import com.example.core.AppEnvironment
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
            FirebaseAnalytics.getInstance(this).setAnalyticsCollectionEnabled(false)
        }
    }
}
