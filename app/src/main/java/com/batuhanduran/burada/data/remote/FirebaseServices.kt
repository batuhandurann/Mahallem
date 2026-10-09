package com.batuhanduran.burada.data.remote

import android.content.Context
import com.batuhanduran.burada.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings

/** One initialization path: attestation is installed before Auth/Firestore are obtained. */
object FirebaseServices {
    private lateinit var initializedApp: FirebaseApp
    val app: FirebaseApp
        get() {
            check(::initializedApp.isInitialized) { "FirebaseServices.initialize must run in Application.onCreate" }
            return initializedApp
        }

    @Synchronized
    fun initialize(context: Context) {
        if (::initializedApp.isInitialized) return
        check(!BuildConfig.USE_FIREBASE_EMULATORS || BuildConfig.DEBUG) {
            "Release builds must never connect to Firebase emulators"
        }
        val selectedApp = if (BuildConfig.USE_FIREBASE_EMULATORS) {
            FirebaseApp.getApps(context).find { it.name == "emulator" }
                ?: FirebaseApp.initializeApp(
                    context.applicationContext,
                    FirebaseOptions.Builder().setProjectId("demo-mahallem")
                        .setApplicationId("1:1234567890:android:0123456789abcdef")
                        // IID/Functions validate the SDK key shape even with an emulator.
                        // Deliberately nonfunctional placeholder: no production credential.
                        .setApiKey("A" + "0".repeat(38))
                        .setStorageBucket("demo-mahallem.appspot.com")
                        .build(),
                    "emulator"
                )
        } else {
            FirebaseApp.initializeApp(context.applicationContext)
                ?: error("Missing Firebase configuration: register this Android package and supply google-services.json")
        }
        AppCheckInstaller.install(selectedApp)
        initializedApp = selectedApp
    }

    val auth: FirebaseAuth by lazy {
        FirebaseAuth.getInstance(app).apply {
            if (BuildConfig.USE_FIREBASE_EMULATORS) useEmulator(BuildConfig.EMULATOR_HOST, 9099)
        }
    }
    val firestore: FirebaseFirestore by lazy {
        FirebaseFirestore.getInstance(app, BuildConfig.FIRESTORE_DATABASE_ID).apply {
            firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                .build()
            if (BuildConfig.USE_FIREBASE_EMULATORS) useEmulator(BuildConfig.EMULATOR_HOST, 8080)
        }
    }
}
