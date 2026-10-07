package com.example.data.remote

import com.example.BuildConfig
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings

/** Configure the named database once, before any document operation starts. */
object FirebaseServices {
    val firestore: FirebaseFirestore by lazy {
        FirebaseFirestore.getInstance(BuildConfig.FIRESTORE_DATABASE_ID).apply {
            firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                .build()
        }
    }
}
