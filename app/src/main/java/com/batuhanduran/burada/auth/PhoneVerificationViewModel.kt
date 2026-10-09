package com.batuhanduran.burada.auth

import android.app.Activity
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batuhanduran.burada.data.remote.FirebaseServices
import com.google.firebase.FirebaseException
import com.google.firebase.auth.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

data class PhoneVerificationState(
    val busy: Boolean = false,
    val codeSent: Boolean = false,
    val linked: Boolean = false,
    val error: String? = null,
    val message: String? = null
)

/** Retained only in the UID-scoped session store. OTP and verification ID never go to disk. */
class PhoneVerificationViewModel : ViewModel() {
    private val auth = FirebaseServices.auth
    private val uid = requireNotNull(auth.currentUser).uid
    private val mutable = MutableStateFlow(PhoneVerificationState(linked = auth.currentUser?.phoneNumber != null))
    val state = mutable.asStateFlow()
    private var cleared = false
    private var attempt = 0
    private var verificationId: String? = null
    private var requestedPhone: String? = null
    private var codeExpiresAt = 0L
    private var nextSendAt = 0L
    private fun active() = !cleared && auth.currentUser?.uid == uid
    private val listener = FirebaseAuth.AuthStateListener {
        if (!active()) {
            attempt++
            verificationId = null
            requestedPhone = null
            mutable.value = PhoneVerificationState()
        }
    }
    init { auth.addAuthStateListener(listener) }

    fun sendCode(activity: Activity, input: String) {
        if (!active() || linking || state.value.busy || state.value.linked) return
        val phone = PhoneValidation.normalize(input)
        if (phone == null) {
            mutable.value = state.value.copy(error = "Geçerli bir Türkiye cep telefonu numarası yazın.")
            return
        }
        if (SystemClock.elapsedRealtime() < nextSendAt) {
            mutable.value = state.value.copy(error = "Yeni kod için 60 saniye bekleyin.")
            return
        }
        val generation = ++attempt
        nextSendAt = SystemClock.elapsedRealtime() + 60_000
        verificationId = null
        requestedPhone = phone
        mutable.value = PhoneVerificationState(busy = true)
        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                if (active() && attempt == generation) link(credential)
            }
            override fun onVerificationFailed(error: FirebaseException) {
                if (active() && attempt == generation && !linking)
                    mutable.value = state.value.copy(busy = false, error = phoneError(error))
            }
            override fun onCodeSent(id: String, token: PhoneAuthProvider.ForceResendingToken) {
                if (active() && attempt == generation && !state.value.linked && !linking) {
                    verificationId = id
                    codeExpiresAt = SystemClock.elapsedRealtime() + 300_000
                    mutable.value = state.value.copy(busy = false, codeSent = true, error = null,
                        message = "SMS kodu gönderildi. Numaranız doğrulama ve kötüye kullanım önleme amacıyla Google tarafından işlenir.")
                }
            }
            override fun onCodeAutoRetrievalTimeOut(id: String) {
                if (active() && attempt == generation && !state.value.linked && !linking)
                    mutable.value = state.value.copy(busy = false)
            }
        }
        try {
            PhoneAuthProvider.verifyPhoneNumber(PhoneAuthOptions.newBuilder(auth)
                .setPhoneNumber(phone).setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(activity).setCallbacks(callbacks).build())
        } catch (error: Exception) {
            mutable.value = state.value.copy(busy = false, error = phoneError(error))
        }
        // Activity stop can detach callbacks. Always let the user retry after the timeout.
        viewModelScope.launch {
            delay(75_000)
            if (active() && attempt == generation && state.value.busy && verificationId == null && !linking)
                mutable.value = state.value.copy(busy = false, error = "Kod isteği zaman aşımına uğradı. Yeniden deneyin.")
        }
    }

    fun verifyCode(code: String) {
        if (!active() || linking || state.value.busy || state.value.linked) return
        val id = verificationId
        if (id == null || SystemClock.elapsedRealtime() > codeExpiresAt) {
            mutable.value = state.value.copy(codeSent = false, error = "Kodun süresi doldu. Yeni kod isteyin.")
            return
        }
        if (!PhoneValidation.validCode(code)) {
            mutable.value = state.value.copy(error = "SMS içindeki 6 haneli kodu yazın.")
            return
        }
        link(PhoneAuthProvider.getCredential(id, code))
    }

    fun changeNumber() {
        if (!active() || linking || state.value.busy || state.value.linked) return
        attempt++
        verificationId = null
        requestedPhone = null
        mutable.value = PhoneVerificationState()
    }

    private var linking = false
    private fun link(credential: PhoneAuthCredential) {
        if (!active() || linking || state.value.linked) return
        val account = auth.currentUser ?: return
        val generation = attempt
        linking = true
        mutable.value = state.value.copy(busy = true, error = null, message = null)
        viewModelScope.launch {
            try {
                // Linking preserves the email account's UID; signing in here could switch accounts.
                account.linkWithCredential(credential).awaitResult()
                if (!active() || generation != attempt) return@launch
                account.reload().awaitResult()
                account.getIdToken(true).awaitResult()
                if (!active() || generation != attempt) return@launch
                check(account.uid == uid && account.phoneNumber == requestedPhone)
                verificationId = null
                mutable.value = PhoneVerificationState(linked = true,
                    message = "Telefonunuz hesabınıza bağlandı. Telefon rozeti yalnızca ilan numarası bu numarayla eşleştiğinde görünür.")
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (active() && generation == attempt)
                    mutable.value = state.value.copy(busy = false, error = phoneError(error))
            } finally {
                linking = false
                if (active() && generation == attempt) mutable.value = state.value.copy(busy = false)
            }
        }
    }
    override fun onCleared() {
        cleared = true
        attempt++
        verificationId = null
        requestedPhone = null
        auth.removeAuthStateListener(listener)
        super.onCleared()
    }
}

private fun phoneError(error: Exception): String = when ((error as? FirebaseAuthException)?.errorCode) {
    "ERROR_INVALID_VERIFICATION_CODE" -> "SMS kodu hatalı. Yeniden deneyin."
    "ERROR_SESSION_EXPIRED", "ERROR_INVALID_VERIFICATION_ID" -> "Kodun süresi doldu. Yeni kod isteyin."
    "ERROR_CREDENTIAL_ALREADY_IN_USE" -> "Bu numara başka bir hesaba bağlı. Başka bir numara kullanın."
    "ERROR_PROVIDER_ALREADY_LINKED" -> "Hesabınıza zaten bir telefon bağlı."
    "ERROR_INVALID_PHONE_NUMBER" -> "Telefon numarası geçerli değil."
    "ERROR_OPERATION_NOT_ALLOWED" -> "Telefon doğrulaması şu anda kullanılamıyor."
    "ERROR_TOO_MANY_REQUESTS", "ERROR_QUOTA_EXCEEDED" -> "SMS sınırına ulaşıldı. Daha sonra deneyin."
    "ERROR_REQUIRES_RECENT_LOGIN" -> "Güvenlik için çıkış yapıp yeniden giriş yapın."
    else -> "Telefon doğrulanamadı. Bağlantınızı kontrol edip yeniden deneyin."
}
