package com.batuhanduran.burada.data.remote

import com.batuhanduran.burada.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/** Debug SDK is a debugImplementation, so its bypass factory cannot enter release APKs. */
internal object AppCheckInstaller {
    fun install(app: FirebaseApp) {
        check(BuildConfig.DEBUG)
        FirebaseAppCheck.getInstance(app).apply {
            installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
            // Local emulators do not attest; avoid requesting production tokens in emulator tests.
            setTokenAutoRefreshEnabled(!BuildConfig.USE_FIREBASE_EMULATORS)
        }
    }
}
