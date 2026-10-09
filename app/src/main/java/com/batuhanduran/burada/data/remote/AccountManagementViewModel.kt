package com.batuhanduran.burada.data.remote

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batuhanduran.burada.BuildConfig
import com.batuhanduran.burada.auth.AuthValidation
import com.batuhanduran.burada.auth.awaitResult
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

data class ManagedListing(val kind: String, val id: String, val revision: String, val visibility: String,
    val editable: Boolean, val removable: Boolean, val fields: Map<String,String>)
data class AccountManagementState(val busy: Boolean = false, val loaded: Boolean = false,
    val displayName: String = "", val bio: String = "", val email: String = "", val revision: String = "",
    val listing: ManagedListing? = null, val message: String? = null, val error: String? = null)
/** Immutable UID binding also guards late callbacks after logout or account change. */
class AccountManagementViewModel(private val uid: String) : ViewModel() {
    private val auth = FirebaseServices.auth
    private val functions = FirebaseFunctions.getInstance(FirebaseServices.app,"europe-west3").apply {
        if (BuildConfig.USE_FIREBASE_EMULATORS) useEmulator(BuildConfig.EMULATOR_HOST,5001)
    }
    private val mutableState = MutableStateFlow(AccountManagementState())
    val state = mutableState.asStateFlow()
    private var cleared = false
    init { loadProfile() }
    private fun account() = requireNotNull(auth.currentUser?.takeIf { !cleared && it.uid == uid }) { "Oturum değişti. Yeniden giriş yapın." }
    private suspend fun call(name: String, data: Map<String,Any>): Map<*,*> {
        account()
        val result = withTimeout(30_000) { functions.getHttpsCallable(name).call(data).awaitResult().data }
        account()
        return result as? Map<*,*> ?: error("Sunucu yanıtı okunamadı.")
    }
    private fun action(block: suspend () -> Unit) {
        if (state.value.busy || cleared) return
        mutableState.value = state.value.copy(busy = true,error = null,message = null)
        viewModelScope.launch {
            try { block() }
            catch (_: TimeoutCancellationException) {
                if (!cleared) mutableState.value = state.value.copy(error = "İşlem süresi doldu. Yeniden açarak sonucu kontrol edin.")
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (!cleared && auth.currentUser?.uid == uid) mutableState.value = state.value.copy(error =
                    if (e is FirebaseFunctionsException && e.code in listOf(FirebaseFunctionsException.Code.INVALID_ARGUMENT,
                        FirebaseFunctionsException.Code.FAILED_PRECONDITION,FirebaseFunctionsException.Code.ABORTED)) e.message
                    else "İşlem tamamlanamadı. Bağlantınızı kontrol edin; hesap silme için doğru şifrenizi girin ve yeniden deneyin.")
            } finally { if (!cleared) mutableState.value = state.value.copy(busy = false) }
        }
    }
    fun loadProfile() = action {
        val r = call("getAccountProfile",emptyMap())
        mutableState.value = state.value.copy(loaded = true,displayName = r["displayName"] as? String ?: "",
            bio = r["bio"] as? String ?: "",email = r["email"] as? String ?: "",revision = r["revision"] as? String ?: "")
    }
    fun saveProfile(name: String,bio: String) = action {
        require(AuthValidation.nameError(name) == null && bio.length <= 1000)
        val r = call("updateAccountProfile",mapOf("displayName" to name.trim(),"bio" to bio.trim(),"revision" to state.value.revision))
        mutableState.value = state.value.copy(displayName = r["displayName"] as? String ?: name.trim(),
            bio = r["bio"] as? String ?: bio.trim(),revision = r["revision"] as? String ?: "",message = "Profiliniz kaydedildi.")
    }
    fun openListing(kind: String,id: String) = action {
        mutableState.value = state.value.copy(listing = null)
        val r = call("getListingManagement",mapOf("kind" to kind,"id" to id))
        val fields = (r["fields"] as? Map<*,*>)?.entries?.associate { it.key.toString() to it.value.toString() }.orEmpty()
        mutableState.value = state.value.copy(listing = ManagedListing(kind,id,r["revision"].toString(),r["visibility"].toString(),r["editable"] == true,r["removable"] == true,fields))
    }
    fun closeListing() { if (!state.value.busy) mutableState.value = state.value.copy(listing = null) }
    fun manageListing(actionName: String,fields: Map<String,String> = emptyMap()) = action {
        val listing = requireNotNull(state.value.listing)
        call("manageListing",mapOf("kind" to listing.kind,"id" to listing.id,"revision" to listing.revision,"action" to actionName,"fields" to fields))
        mutableState.value = state.value.copy(listing = null,message = if (actionName == "remove") "İlanınız keşfetten kaldırıldı." else "İlanınız güncellendi.")
    }
    fun deleteAccount(password: String,confirmation: String) = action {
        require(confirmation == "HESABIMI SİL" && password.isNotBlank())
        val user = account()
        user.reauthenticate(EmailAuthProvider.getCredential(requireNotNull(user.email),password)).awaitResult()
        account().getIdToken(true).awaitResult()
        call("requestAccountDeletion",mapOf("confirmation" to confirmation))
        // Only the durable request is acknowledged here, not a completed purge.
        account()
        auth.signOut()
    }
    override fun onCleared() { cleared = true; super.onCleared() }
}
