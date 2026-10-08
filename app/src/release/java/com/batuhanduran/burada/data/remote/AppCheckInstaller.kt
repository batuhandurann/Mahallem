package com.batuhanduran.burada.data.remote

import com.batuhanduran.burada.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

internal object AppCheckInstaller {
    fun install(app: FirebaseApp) {
        check(!BuildConfig.DEBUG && !BuildConfig.USE_FIREBASE_EMULATORS)
        check(!app.options.projectId.orEmpty().startsWith("demo-")) {
            "A release build cannot use a Firebase demo project"
        }
        FirebaseAppCheck.getInstance(app).apply {
            installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())
            setTokenAutoRefreshEnabled(true)
        }
    }
}
