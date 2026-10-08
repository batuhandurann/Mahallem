package com.batuhanduran.burada.firebase

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.batuhanduran.burada.BuildConfig
import com.batuhanduran.burada.MainActivity
import com.batuhanduran.burada.data.remote.FirebaseServices
import com.google.android.gms.tasks.Tasks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Real Activity and Firebase Auth emulator: never runs against production accounts. */
@RunWith(AndroidJUnit4::class)
class AccountLifecycleInstrumentedTest {
    @Test
    fun guestSurvivesBackgroundForegroundAndActivityRecreation() {
        check(BuildConfig.DEBUG && BuildConfig.USE_FIREBASE_EMULATORS) {
            "Android lifecycle smoke tests must use the isolated Firebase emulator."
        }
        val auth = FirebaseServices.auth
        auth.signOut()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.moveToState(Lifecycle.State.STARTED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.recreate()
            scenario.onActivity { activity -> assertFalse(activity.isFinishing) }
            assertNull(auth.currentUser)
        }
    }

    @Test
    fun signedInUidIsPreservedThroughBackgroundForegroundAndRecreation() {
        check(BuildConfig.DEBUG && BuildConfig.USE_FIREBASE_EMULATORS) {
            "Android lifecycle smoke tests must use the isolated Firebase emulator."
        }
        val auth = FirebaseServices.auth
        auth.signOut()
        val email = "lifecycle-${UUID.randomUUID()}@example.com"
        val user = Tasks.await(
            auth.createUserWithEmailAndPassword(email, "SecurePass123!"),
            30,
            TimeUnit.SECONDS
        ).user ?: error("Firebase Auth emulator did not create user")
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.moveToState(Lifecycle.State.STARTED)
                scenario.moveToState(Lifecycle.State.RESUMED)
                scenario.recreate()
                scenario.onActivity { activity -> assertFalse(activity.isFinishing) }
                assertEquals(user.uid, auth.currentUser?.uid)
            }
        } finally {
            try {
                auth.currentUser?.takeIf { it.uid == user.uid }?.let {
                    Tasks.await(it.delete(), 30, TimeUnit.SECONDS)
                }
            } finally {
                auth.signOut()
            }
        }
    }
}
