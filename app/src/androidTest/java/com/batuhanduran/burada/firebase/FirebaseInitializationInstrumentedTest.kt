package com.batuhanduran.burada.firebase

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.batuhanduran.burada.BuildConfig
import com.batuhanduran.burada.BuradaApplication
import com.batuhanduran.burada.data.remote.FirebaseServices
import com.google.firebase.appcheck.FirebaseAppCheck
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/** Startup wiring test. A local emulator cannot prove Play Integrity attestation. */
@RunWith(AndroidJUnit4::class)
class FirebaseInitializationInstrumentedTest {
    @Test
    fun applicationInstallsFirebaseBeforeSdkAccessWithoutProductionConfiguration() {
        check(BuildConfig.DEBUG && BuildConfig.USE_FIREBASE_EMULATORS) {
            "This test is restricted to local Firebase emulator builds"
        }
        val application = ApplicationProvider.getApplicationContext<BuradaApplication>()
        val app = FirebaseServices.app
        assertEquals("emulator", app.name)
        assertEquals("demo-mahallem", app.options.projectId)
        assertSame(app, FirebaseServices.auth.app)
        assertSame(app, FirebaseServices.firestore.app)
        assertNotNull(FirebaseAppCheck.getInstance(app))
        FirebaseServices.initialize(application)
        assertSame(app, FirebaseServices.app)
    }
}
