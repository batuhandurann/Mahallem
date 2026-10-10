package com.batuhanduran.burada.payment

import com.batuhanduran.burada.BuildConfig
import com.batuhanduran.burada.data.remote.FirebaseServices
import com.google.android.gms.tasks.Task
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Server owns amounts, accepted provider, buyer billing, charge state and merchant secrets. */
class PaymentRepository(private val uid: String) {
    private val functions = FirebaseFunctions.getInstance(FirebaseServices.app, "europe-west3").apply {
        if (BuildConfig.USE_FIREBASE_EMULATORS) useEmulator(BuildConfig.EMULATOR_HOST, 5001)
    }
    fun requireAccount() = check(FirebaseServices.auth.currentUser?.uid == uid) { "Oturum değişti. Yeniden giriş yapın." }

    private suspend fun call(name: String, payload: Map<String, String> = emptyMap()): Map<*, *> {
        requireAccount()
        val result = functions.getHttpsCallable(name).call(payload).awaitPayment().data
        requireAccount()
        return result as? Map<*, *> ?: error("Ödeme durumu doğrulanamadı.")
    }
    suspend fun availability(): PaymentAvailability {
        val data = call("getPaymentAvailability")
        val environment = HostedCheckoutPolicy.environment(data["environment"])
        return PaymentAvailability(data["available"] == true && environment != CheckoutEnvironment.DISABLED, environment)
    }
    suspend fun status(requestId: String) = HostedCheckoutPolicy.decode(call("getHostedCheckoutStatus", mapOf("requestId" to requestId)))
    suspend fun start(requestId: String) = HostedCheckoutPolicy.decode(call("startHostedCheckout", mapOf("requestId" to requestId)))
}

private suspend fun <T> Task<T>.awaitPayment(): T = withTimeout(30_000) {
    suspendCancellableCoroutine { c -> addOnCompleteListener { task ->
        if (c.isActive) when {
            task.isCanceled -> c.cancel()
            task.isSuccessful -> c.resume(task.result)
            else -> c.resumeWithException(task.exception ?: IllegalStateException("Ödeme durumu doğrulanamadı."))
        }
    } }
}
