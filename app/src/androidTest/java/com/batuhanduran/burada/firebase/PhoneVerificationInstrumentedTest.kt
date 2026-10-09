package com.batuhanduran.burada.firebase

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.lifecycle.ViewModelStore
import com.batuhanduran.burada.BuildConfig
import com.batuhanduran.burada.MainActivity
import com.batuhanduran.burada.auth.PhoneVerificationViewModel
import com.batuhanduran.burada.data.remote.FirebaseServices
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class PhoneVerificationInstrumentedTest {
    @Test fun realSmsCallbackWrongCodeLinkAndAccountSwitch(): Unit = runBlocking {
        check(BuildConfig.USE_FIREBASE_EMULATORS && BuildConfig.DEBUG)
        val auth = FirebaseServices.auth
        val password = "SecurePhone123!"
        val email = "phone-${UUID.randomUUID()}@example.com"
        val first = Tasks.await(auth.createUserWithEmailAndPassword(email, password), 30, TimeUnit.SECONDS).user!!
        val uid = first.uid
        val suffix = (System.nanoTime() % 1_000_000_000).toString().padStart(9, '0')
        val number = "+905$suffix"
        val store = ViewModelStore()
        lateinit var model: PhoneVerificationViewModel
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            model = PhoneVerificationViewModel()
            store.put("phone", model)
        }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { model.sendCode(it, number) }
                val sent = withTimeout(30_000) { model.state.first { it.codeSent || it.error != null } }
                assertNull(sent.error)
                assertTrue(sent.codeSent)
                // Emulator-only inspection: production endpoints and fixed/demo OTP are forbidden.
                val connection = URL("http://${BuildConfig.EMULATOR_HOST}:9099/emulator/v1/projects/demo-mahallem/verificationCodes")
                    .openConnection() as HttpURLConnection
                connection.connectTimeout = 10_000
                connection.readTimeout = 10_000
                val body = try { connection.inputStream.bufferedReader().use { it.readText() } }
                    finally { connection.disconnect() }
                val codes = JSONObject(body).getJSONArray("verificationCodes")
                val record = (0 until codes.length()).map { codes.getJSONObject(it) }.last { it.getString("phoneNumber") == number }
                val code = record.getString("code")
                scenario.onActivity { model.verifyCode(if (code == "000000") "111111" else "000000") }
                val rejected = withTimeout(30_000) { model.state.first { !it.busy && it.error != null } }
                assertFalse(rejected.linked)
                assertEquals(uid, auth.currentUser?.uid)
                assertNull(auth.currentUser?.phoneNumber)
                scenario.recreate()
                scenario.onActivity { model.verifyCode(code) }
                val linked = withTimeout(30_000) { model.state.first { !it.busy && (it.linked || it.error != null) } }
                assertNull(linked.error)
                assertTrue(linked.linked)
                assertEquals(uid, auth.currentUser?.uid)
                assertEquals(number, auth.currentUser?.phoneNumber)
                auth.signOut()
                val second = Tasks.await(auth.createUserWithEmailAndPassword("other-${UUID.randomUUID()}@example.com", password), 30, TimeUnit.SECONDS).user!!
                withTimeout(10_000) { model.state.first { !it.linked && !it.codeSent } }
                scenario.onActivity { model.verifyCode(code) }
                assertEquals(second.uid, auth.currentUser?.uid)
                assertNull(auth.currentUser?.phoneNumber)
                Tasks.await(second.delete(), 30, TimeUnit.SECONDS)
                Tasks.await(auth.signInWithEmailAndPassword(email, password), 30, TimeUnit.SECONDS)
                assertEquals(uid, auth.currentUser?.uid)
                assertEquals(number, auth.currentUser?.phoneNumber)
            }
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync { store.clear() }
            if (auth.currentUser?.uid == uid) Tasks.await(auth.currentUser!!.delete(), 30, TimeUnit.SECONDS)
            auth.signOut()
        }
    }
}
