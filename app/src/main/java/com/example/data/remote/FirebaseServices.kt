package com.example.data.remote

import com.example.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings

/** Configure the named database once, before any document operation starts. */
object FirebaseServices {
    private val app: FirebaseApp by lazy {
        if (BuildConfig.USE_FIREBASE_EMULATORS) {
            check(BuildConfig.DEBUG)
            FirebaseApp.getApps(FirebaseApp.getInstance().applicationContext)
                .find { it.name == "emulator" } ?: FirebaseApp.initializeApp(
                FirebaseApp.getInstance().applicationContext,
                FirebaseOptions.Builder().setProjectId("demo-mahallem")
                    .setApplicationId("1:1234567890:android:0123456789abcdef")
                    .setApiKey("fake-emulator-key").build(), "emulator")
        } else FirebaseApp.getInstance()
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
