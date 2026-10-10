package com.batuhanduran.burada.auth

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resumeWithException

data class AuthUser(
    val uid: String,
    val displayName: String,
    val email: String,
    val emailVerified: Boolean = false
)

data class AuthUiState(
    val initialized: Boolean = false,
    val user: AuthUser? = null,
    val busy: Boolean = false,
    val accountActionBusy: Boolean = false,
    val verificationResendSeconds: Int = 0,
    val error: String? = null,
    val message: String? = null
)

class AuthViewModel(private val auth: FirebaseAuth = com.batuhanduran.burada.data.remote.FirebaseServices.auth) : ViewModel() {
    private val mutableState = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = mutableState.asStateFlow()
    private val verificationCooldown = VerificationEmailCooldown()
    private var verificationCooldownJob: Job? = null

    private val authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        val user = firebaseAuth.currentUser
        mutableState.update {
            val changedAccount = it.user?.uid != user?.uid
            it.copy(
                initialized = true,
                user = user?.asAuthUser(),
                error = if (changedAccount) null else it.error,
                message = if (changedAccount) null else it.message,
                verificationResendSeconds = user?.let { account ->
                    verificationCooldown.remainingSeconds(account.uid)
                } ?: 0
            )
        }
        user?.let { observeVerificationCooldown(it.uid) }
            ?: verificationCooldownJob?.cancel()
    }

    init {
        auth.setLanguageCode("tr")
        auth.addAuthStateListener(authListener)
    }

    fun signIn(email: String, password: String) {
        val validationError = AuthValidation.emailError(email)
            ?: AuthValidation.passwordError(password, registering = false)
        perform(validationError) {
            auth.signInWithEmailAndPassword(email.trim(), password).awaitResult { abandonedResult ->
                // Firebase Auth Tasks cannot be cancelled. If this screen is destroyed
                // while login is in flight, undo any late successful sign-in for that UID.
                abandonedResult.user?.let { signedInUser ->
                    if (auth.currentUser?.uid == signedInUser.uid) auth.signOut()
                }
            }
        }
    }

    fun register(name: String, email: String, password: String) {
        val validationError = AuthValidation.nameError(name)
            ?: AuthValidation.emailError(email)
            ?: AuthValidation.passwordError(password, registering = true)
        perform(validationError) {
            val result = auth.createUserWithEmailAndPassword(email.trim(), password)
                .awaitResult { abandonedResult ->
                    // A Firebase Task cannot be cancelled. Roll back only the new account if
                    // this ViewModel was cleared while the create-account request was in flight.
                    abandonedResult.user?.let { newUser ->
                        newUser.delete()
                        if (auth.currentUser?.uid == newUser.uid) auth.signOut()
                    }
                }
            val user = result.user ?: throw IllegalStateException("Kullanıcı oluşturulamadı.")

            // Finish this short account-creation step even if the screen closes. A newly
            // created account must not be treated as successful before its name is saved.
            withContext(NonCancellable) {
                try {
                    val profile = UserProfileChangeRequest.Builder()
                        .setDisplayName(name.trim())
                        .build()
                    withTimeout(30_000) { user.updateProfile(profile).awaitResult() }
                    // FirebaseAuth owns the latest user snapshot after a profile update.
                    val updatedUser = auth.currentUser
                    if (updatedUser == null || updatedUser.uid != user.uid || updatedUser.displayName != name.trim()) {
                        throw IllegalStateException("Profil adı kaydedilemedi.")
                    }
                } catch (exception: Exception) {
                    val rolledBack = try {
                        withTimeout(30_000) { user.delete().awaitResult() }
                        true
                    } catch (_: Exception) {
                        false
                    }
                    if (auth.currentUser?.uid == user.uid) auth.signOut()
                    throw RegistrationProfileException(
                        if (rolledBack) {
                            "Adınız kaydedilemedi. Bağlantınızı kontrol edip yeniden kayıt olun."
                        } else {
                            "Hesabınız oluşturuldu ancak adınız kaydedilemedi. " +
                                "Oturum kapatıldı; bağlantınızı kontrol edip yeniden giriş yapın."
                        },
                        exception
                    )
                }
            }
            currentCoroutineContext().ensureActive()

            val verificationSent = try {
                requireCurrentAccount(user.uid)
                recordVerificationAttempt(user.uid)
                withTimeout(30_000) { user.sendEmailVerification().awaitResult() }
                true
            } catch (_: TimeoutCancellationException) {
                false
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: AccountSessionException) {
                throw exception
            } catch (_: Exception) {
                false
            }
            requireCurrentAccount(user.uid)
            mutableState.update {
                it.copy(
                    message = if (verificationSent) {
                        "Hesabınız oluşturuldu. E-posta adresinize doğrulama bağlantısı gönderildi."
                    } else {
                        "Hesabınız oluşturuldu. Doğrulama e-postası şu anda gönderilemedi."
                    }
                )
            }
        }
    }

    fun resetPassword(email: String) {
        perform(AuthValidation.emailError(email)) {
            try {
                auth.sendPasswordResetEmail(email.trim()).awaitResult()
            } catch (exception: FirebaseAuthException) {
                // Keep the same response if enumeration protection is disabled on the project.
                if (exception.errorCode != "ERROR_USER_NOT_FOUND") throw exception
            }
            mutableState.update {
                it.copy(message = "Bu e-posta adresine kayıtlı bir hesap varsa şifre yenileme bağlantısı gönderildi.")
            }
        }
    }

    fun signOut() {
        if (state.value.busy) return
        verificationCooldownJob?.cancel()
        auth.signOut()
        mutableState.update { it.copy(user = null, error = null, message = null, verificationResendSeconds = 0) }
    }

    fun resendVerificationEmail() {
        if (!state.value.initialized || state.value.busy) return
        val user = auth.currentUser ?: return
        if (state.value.user?.uid != user.uid) return
        if (user.isEmailVerified) {
            mutableState.update { it.copy(user = user.asAuthUser(), message = "E-posta adresiniz zaten doğrulandı.", error = null) }
            return
        }
        val remaining = verificationCooldown.remainingSeconds(user.uid)
        if (remaining > 0) {
            mutableState.update {
                it.copy(verificationResendSeconds = remaining, error = "Yeni doğrulama e-postası için $remaining saniye bekleyin.", message = null)
            }
            return
        }
        perform(validationError = null, accountUid = user.uid) {
            val account = requireCurrentAccount(user.uid)
            // Failed attempts also count; Firebase quotas remain the server authority.
            recordVerificationAttempt(account.uid)
            account.sendEmailVerification().awaitResult()
            requireCurrentAccount(account.uid)
            mutableState.update {
                it.copy(message = "Doğrulama bağlantısı e-posta adresinize gönderildi. Gelen kutusu ve spam klasörünü kontrol edin.")
            }
        }
    }

    fun refreshVerificationStatus() {
        val uid = state.value.user?.uid ?: return
        perform(validationError = null, accountUid = uid) {
            requireCurrentAccount(uid).reload().awaitResult()
            // Refresh the claim as well as the user snapshot.
            requireCurrentAccount(uid).getIdToken(true).awaitResult()
            val user = requireCurrentAccount(uid)
            mutableState.update {
                it.copy(
                    user = user.asAuthUser(),
                    message = if (user.isEmailVerified) "E-posta adresiniz doğrulandı."
                    else "E-posta adresiniz henüz doğrulanmadı. E-postadaki bağlantıyı açtıktan sonra tekrar kontrol edin."
                )
            }
        }
    }

    private fun requireCurrentAccount(uid: String): FirebaseUser {
        val current = auth.currentUser
        if (current == null || current.uid != uid) throw AccountSessionException()
        return current
    }

    private fun recordVerificationAttempt(uid: String) {
        verificationCooldown.recordAttempt(uid)
        observeVerificationCooldown(uid)
    }

    private fun observeVerificationCooldown(uid: String) {
        verificationCooldownJob?.cancel()
        verificationCooldownJob = viewModelScope.launch {
            while (auth.currentUser?.uid == uid && state.value.user?.uid == uid) {
                val remaining = verificationCooldown.remainingSeconds(uid)
                mutableState.update { it.copy(verificationResendSeconds = remaining) }
                if (remaining == 0) break
                delay(1_000)
            }
        }
    }

    fun clearMessage() {
        mutableState.update { it.copy(error = null, message = null) }
    }

    private fun perform(validationError: String?, accountUid: String? = null, action: suspend () -> Unit) {
        if (!state.value.initialized || state.value.busy) return
        if (validationError != null) {
            mutableState.update { it.copy(error = validationError, message = null) }
            return
        }
        // Set this before launching so repeated taps cannot start parallel auth requests.
        mutableState.update { it.copy(busy = true, accountActionBusy = accountUid != null, error = null, message = null) }
        viewModelScope.launch {
            try {
                if (accountUid != null) withTimeout(30_000) { action() } else action()
            } catch (_: TimeoutCancellationException) {
                if (accountUid == null || auth.currentUser?.uid == accountUid) {
                    mutableState.update { it.copy(error = "İşlem zaman aşımına uğradı. Bağlantınızı kontrol edip yeniden deneyin.") }
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                if (accountUid == null || auth.currentUser?.uid == accountUid) {
                    mutableState.update { it.copy(error = exception.asTurkishMessage()) }
                }
            } finally {
                mutableState.update {
                    it.copy(busy = false, accountActionBusy = false, user = auth.currentUser?.asAuthUser())
                }
            }
        }
    }

    override fun onCleared() {
        verificationCooldownJob?.cancel()
        auth.removeAuthStateListener(authListener)
        super.onCleared()
    }
}

private fun FirebaseUser.asAuthUser() = AuthUser(
    uid = uid,
    displayName = displayName?.takeIf { it.isNotBlank() } ?: "Mahalle Sakini",
    email = email.orEmpty(),
    emailVerified = isEmailVerified
)

private class RegistrationProfileException(message: String, cause: Throwable) : Exception(message, cause)
private class AccountSessionException : IllegalStateException()

/** In-memory UX throttle only; survives Activity recreation through the ViewModel. */
internal class VerificationEmailCooldown(private val nowMillis: () -> Long = { SystemClock.elapsedRealtime() }) {
    private val attemptedAt = mutableMapOf<String, Long>()

    fun recordAttempt(uid: String) {
        attemptedAt[uid] = nowMillis()
    }

    fun remainingSeconds(uid: String): Int {
        val started = attemptedAt[uid] ?: return 0
        val remainingMillis = (30_000 - (nowMillis() - started).coerceAtLeast(0)).coerceAtLeast(0)
        return ((remainingMillis + 999) / 1_000).toInt()
    }
}

internal fun Exception.asTurkishMessage(): String = when (this) {
    is AccountSessionException -> "Oturumunuz değişti. Yeniden giriş yapıp tekrar deneyin."
    is RegistrationProfileException -> message ?: "Hesabınızın adı kaydedilemedi."
    is FirebaseNetworkException -> "Bağlantı kurulamadı. İnternet bağlantınızı kontrol edip yeniden deneyin."
    is FirebaseTooManyRequestsException -> "Çok fazla deneme yapıldı. Bir süre bekleyip yeniden deneyin."
    is FirebaseAuthException -> when (errorCode) {
        "ERROR_INVALID_EMAIL" -> "Geçerli bir e-posta adresi yazın."
        "ERROR_EMAIL_ALREADY_IN_USE" -> "Kayıt tamamlanamadı. Bilgilerinizi kontrol edin; hesabınız varsa giriş yapın veya şifrenizi yenileyin."
        "ERROR_USER_NOT_FOUND", "ERROR_WRONG_PASSWORD", "ERROR_INVALID_CREDENTIAL",
        "ERROR_INVALID_LOGIN_CREDENTIALS" -> "E-posta adresi veya şifre hatalı."
        "ERROR_USER_DISABLED" -> "Bu hesap devre dışı bırakılmış. Destek ile iletişime geçin."
        "ERROR_WEAK_PASSWORD", "ERROR_PASSWORD_DOES_NOT_MEET_REQUIREMENTS" ->
            "Şifreniz güvenlik koşullarını karşılamıyor. En az 8 karakterli daha güçlü bir şifre kullanın."
        "ERROR_OPERATION_NOT_ALLOWED" -> "E-posta ile giriş şu anda kullanılamıyor. Daha sonra yeniden deneyin."
        "ERROR_TOO_MANY_REQUESTS" -> "Çok fazla deneme yapıldı. Bir süre bekleyip yeniden deneyin."
        "ERROR_NETWORK_REQUEST_FAILED" -> "İnternet bağlantınızı kontrol edip yeniden deneyin."
        else -> "İşlem tamamlanamadı. Lütfen yeniden deneyin."
    }
    else -> "İşlem tamamlanamadı. Lütfen yeniden deneyin."
}

/** Wait without introducing the deprecated Firebase KTX or a new coroutine dependency. */
internal suspend fun <T> Task<T>.awaitResult(onAbandonedSuccess: ((T) -> Unit)? = null): T =
    suspendCancellableCoroutine { continuation ->
        addOnCompleteListener { task ->
            if (!continuation.isActive) {
                if (task.isSuccessful) runCatching { onAbandonedSuccess?.invoke(task.result) }
                return@addOnCompleteListener
            }
            when {
                task.isCanceled -> continuation.cancel()
                task.isSuccessful -> continuation.resume(task.result) { _, value, _ ->
                    // Cover cancellation after Task completion but before the coroutine resumes.
                    runCatching { onAbandonedSuccess?.invoke(value) }
                }
                else -> continuation.resumeWithException(
                    task.exception ?: IllegalStateException("Firebase işlemi tamamlanamadı.")
                )
            }
        }
    }
