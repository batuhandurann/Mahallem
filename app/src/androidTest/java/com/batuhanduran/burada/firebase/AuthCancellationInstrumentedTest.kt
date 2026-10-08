package com.batuhanduran.burada.firebase

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.batuhanduran.burada.auth.awaitResult
import com.batuhanduran.burada.auth.asTurkishMessage
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class AuthCancellationInstrumentedTest {
    @Test
    fun abandonedSuccessfulFirebaseTaskInvokesRollbackCallback() = runBlocking {
        val task = TaskCompletionSource<String>()
        val cleanedUp = AtomicReference<String?>(null)
        val waiter = launch(start = CoroutineStart.UNDISPATCHED) {
            task.task.awaitResult { result -> cleanedUp.set(result) }
        }
        waiter.cancelAndJoin()
        task.setResult("abandoned-login")
        withTimeout(10_000) {
            while (cleanedUp.get() == null) delay(20)
        }
        assertEquals("abandoned-login", cleanedUp.get())
    }

    @Test
    fun authRateLimitErrorsReturnSafeUserFeedback() {
        val throttled = "Çok fazla deneme yapıldı. Bir süre bekleyip yeniden deneyin."
        assertEquals(throttled, FirebaseTooManyRequestsException("rate limited").asTurkishMessage())
        assertEquals(throttled, FirebaseAuthException("ERROR_TOO_MANY_REQUESTS", "rate limited").asTurkishMessage())
    }
}
