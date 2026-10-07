package com.example.auth

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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resumeWithException

data class AuthUser(
    val uid: String,
    val displayName: String,
    val email: String
)

data class AuthUiState(
    val initialized: Boolean = false,
    val user: AuthUser? = null,
    val busy: Boolean = false,
    val error: String? = null,
    val message: String? = null
)

class AuthViewModel(private val auth: FirebaseAuth = com.example.data.remote.FirebaseServices.auth) : ViewModel() {
    private val mutableState = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = mutableState.asStateFlow()

    private val authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        mutableState.update {
            it.copy(initialized = true, user = firebaseAuth.currentUser?.asAuthUser())
        }
    }

    init {
        auth.setLanguageCode("tr")
        auth.addAuthStateListener(authListener)
    }

    fun signIn(email: String, password: String) {
        val validationError = AuthValidation.emailError(email)
            ?: AuthValidation.passwordError(password, registering = false)
        perform(validationError) {
            auth.signInWithEmailAndPassword(email.trim(), password).awaitResult()
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
                    user.updateProfile(profile).awaitResult()
                    // FirebaseAuth owns the latest user snapshot after a profile update.
                    val updatedUser = auth.currentUser
                    if (updatedUser == null || updatedUser.uid != user.uid || updatedUser.displayName != name.trim()) {
                        throw IllegalStateException("Profil adı kaydedilemedi.")
                    }
                } catch (exception: Exception) {
                    val rolledBack = try {
                        user.delete().awaitResult()
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
                user.sendEmailVerification().awaitResult()
                true
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                false
            }
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
        auth.signOut()
        mutableState.update { it.copy(user = null, error = null, message = null) }
    }

    fun clearMessage() {
        mutableState.update { it.copy(error = null, message = null) }
    }

    private fun perform(validationError: String?, action: suspend () -> Unit) {
        if (!state.value.initialized || state.value.busy) return
        if (validationError != null) {
            mutableState.update { it.copy(error = validationError, message = null) }
            return
        }
        // Set this before launching so repeated taps cannot start parallel auth requests.
        mutableState.update { it.copy(busy = true, error = null, message = null) }
        viewModelScope.launch {
            try {
                action()
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                mutableState.update { it.copy(error = exception.asTurkishMessage()) }
            } finally {
                mutableState.update {
                    it.copy(busy = false, user = auth.currentUser?.asAuthUser())
                }
            }
        }
    }

    override fun onCleared() {
        auth.removeAuthStateListener(authListener)
        super.onCleared()
    }
}

private fun FirebaseUser.asAuthUser() = AuthUser(
    uid = uid,
    displayName = displayName?.takeIf { it.isNotBlank() } ?: "Mahalle Sakini",
    email = email.orEmpty()
)

private class RegistrationProfileException(message: String, cause: Throwable) : Exception(message, cause)

private fun Exception.asTurkishMessage(): String = when (this) {
    is RegistrationProfileException -> message ?: "Hesabınızın adı kaydedilemedi."
    is FirebaseNetworkException -> "Bağlantı kurulamadı. İnternet bağlantınızı kontrol edip yeniden deneyin."
    is FirebaseTooManyRequestsException -> "Çok fazla deneme yapıldı. Bir süre bekleyip yeniden deneyin."
    is FirebaseAuthException -> when (errorCode) {
        "ERROR_INVALID_EMAIL" -> "Geçerli bir e-posta adresi yazın."
        "ERROR_EMAIL_ALREADY_IN_USE" -> "Bu e-posta adresi zaten kayıtlı. Giriş yapın veya şifrenizi yenileyin."
        "ERROR_USER_NOT_FOUND", "ERROR_WRONG_PASSWORD", "ERROR_INVALID_CREDENTIAL",
        "ERROR_INVALID_LOGIN_CREDENTIALS" -> "E-posta adresi veya şifre hatalı."
        "ERROR_USER_DISABLED" -> "Bu hesap devre dışı bırakılmış. Destek ile iletişime geçin."
        "ERROR_WEAK_PASSWORD" -> "Şifreniz yeterince güçlü değil. En az 6 karakterli daha güçlü bir şifre kullanın."
        "ERROR_OPERATION_NOT_ALLOWED" -> "E-posta ile giriş şu anda kullanılamıyor. Daha sonra yeniden deneyin."
        "ERROR_TOO_MANY_REQUESTS" -> "Çok fazla deneme yapıldı. Bir süre bekleyip yeniden deneyin."
        "ERROR_NETWORK_REQUEST_FAILED" -> "İnternet bağlantınızı kontrol edip yeniden deneyin."
        else -> "İşlem tamamlanamadı. Lütfen yeniden deneyin."
    }
    else -> "İşlem tamamlanamadı. Lütfen yeniden deneyin."
}

/** Wait without introducing the deprecated Firebase KTX or a new coroutine dependency. */
private suspend fun <T> Task<T>.awaitResult(onAbandonedSuccess: ((T) -> Unit)? = null): T =
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
