package com.example.data.remote

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.auth.AuthUser
import com.example.auth.AuthValidation
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class ProfileSyncState(
    val busy: Boolean = false,
    val synced: Boolean = false,
    val error: String? = null
)

/** One fixed account per instance; the signed-in UI owns and clears this ViewModel. */
class UserProfileViewModel(private val user: AuthUser) : ViewModel() {
    private val auth = FirebaseServices.auth
    private val mutableState = MutableStateFlow(ProfileSyncState())
    val state: StateFlow<ProfileSyncState> = mutableState.asStateFlow()

    @Volatile
    private var cleared = false

    init {
        retry()
    }

    fun retry() {
        if (cleared || state.value.busy) return
        // Update before launching so repeated taps cannot start duplicate transactions.
        mutableState.value = ProfileSyncState(busy = true)
        viewModelScope.launch {
            try {
                synchronize()
                currentCoroutineContext().ensureActive()
                requireCurrentAccount()
                if (!cleared) mutableState.value = ProfileSyncState(synced = true)
            } catch (exception: CancellationException) {
                if (!cleared) {
                    mutableState.value = ProfileSyncState(error = "Profil kaydı tamamlanmadı. Yeniden deneyin.")
                }
                throw exception
            } catch (exception: Exception) {
                if (!cleared) {
                    mutableState.value = ProfileSyncState(error = exception.profileMessage())
                }
            }
        }
    }

    private suspend fun synchronize() {
        val name = user.displayName.trim()
        AuthValidation.nameError(name)?.let { throw InvalidProfileException(it) }

        val account = requireCurrentAccount()
        // Use the authenticated claim so the saved address matches Security Rules exactly.
        val token = account.getIdToken(false).awaitProfileResult()
        currentCoroutineContext().ensureActive()
        requireCurrentAccount()
        val email = token.claims["email"] as? String
        if (email.isNullOrEmpty() || email.length > 254 || email != account.email) {
            throw FirebaseFirestoreException(
                "Profil e-postası oturumla eşleşmiyor.",
                FirebaseFirestoreException.Code.UNAUTHENTICATED
            )
        }

        val db = FirebaseServices.firestore
        val reference = db.collection("users").document(user.uid)
        // Transactions require the server. An offline cache cannot produce a synced state.
        db.runTransaction { transaction ->
            requireCurrentAccount()
            val existing = transaction.get(reference)
            requireCurrentAccount()
            if (existing.exists()) {
                if (existing.getString("uid") != user.uid || existing.getTimestamp("createdAt") == null) {
                    throw FirebaseFirestoreException(
                        "Kayıtlı profil biçimi geçerli değil.",
                        FirebaseFirestoreException.Code.FAILED_PRECONDITION
                    )
                }
                transaction.update(
                    reference,
                    mapOf(
                        "displayName" to name,
                        "email" to email,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            } else {
                transaction.set(
                    reference,
                    mapOf(
                        "uid" to user.uid,
                        "displayName" to name,
                        "email" to email,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            Unit
        }.awaitProfileResult()
    }

    private fun requireCurrentAccount(): FirebaseUser {
        val account = auth.currentUser
        if (cleared || account == null || account.uid != user.uid) {
            throw FirebaseFirestoreException(
                "Profil için geçerli oturum bulunamadı.",
                FirebaseFirestoreException.Code.UNAUTHENTICATED
            )
        }
        return account
    }

    override fun onCleared() {
        // A Firebase Task cannot be cancelled; guard any transaction retries after disposal.
        cleared = true
        super.onCleared()
    }
}

private class InvalidProfileException(message: String) : IllegalArgumentException(message)

private fun Exception.profileMessage(): String = when (this) {
    is InvalidProfileException -> message ?: "Profil bilgilerinizi kontrol edin."
    is FirebaseNetworkException -> "Profil kaydedilemedi. İnternet bağlantınızı kontrol edip yeniden deneyin."
    is FirebaseAuthException -> "Oturumunuz doğrulanamadı. Çıkış yapıp yeniden giriş yapın."
    is FirebaseFirestoreException -> when (code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED ->
            "Profil kaydı için erişim izni alınamadı. Daha sonra yeniden deneyin."
        FirebaseFirestoreException.Code.UNAUTHENTICATED ->
            "Oturumunuz doğrulanamadı. Çıkış yapıp yeniden giriş yapın."
        FirebaseFirestoreException.Code.UNAVAILABLE,
        FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ->
            "Profil kaydedilemedi. İnternet bağlantınızı kontrol edip yeniden deneyin."
        FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED ->
            "Profil kaydı şu anda yoğun. Bir süre bekleyip yeniden deneyin."
        FirebaseFirestoreException.Code.FAILED_PRECONDITION ->
            "Profil kaydı şu anda kullanılamıyor. Daha sonra yeniden deneyin."
        else -> "Profil kaydedilemedi. Lütfen yeniden deneyin."
    }
    else -> "Profil kaydedilemedi. Lütfen yeniden deneyin."
}

/** Cancel waiting and ignore late completions when the account's ViewModel is cleared. */
private suspend fun <T> Task<T>.awaitProfileResult(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        if (!continuation.isActive) return@addOnCompleteListener
        when {
            task.isCanceled -> continuation.cancel()
            task.isSuccessful -> continuation.resume(task.result)
            else -> continuation.resumeWithException(
                task.exception ?: IllegalStateException("Profil kaydı tamamlanamadı.")
            )
        }
    }
}
