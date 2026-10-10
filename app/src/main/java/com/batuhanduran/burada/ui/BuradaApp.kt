package com.batuhanduran.burada.ui

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.os.Build
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.batuhanduran.burada.data.remote.PushTokenLifecycle
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.batuhanduran.burada.MarketplaceApp
import com.batuhanduran.burada.auth.AuthUser
import com.batuhanduran.burada.auth.AuthUiState
import com.batuhanduran.burada.auth.AuthViewModel
import com.batuhanduran.burada.data.remote.ProfileSyncState
import com.batuhanduran.burada.data.remote.UserProfileViewModel
import com.batuhanduran.burada.ui.screens.AuthScreen

@Composable
fun BuradaApp(
    authViewModel: AuthViewModel = viewModel(),
    sessionStore: SessionStoreViewModel = viewModel()
) {
    val appContext = LocalContext.current.applicationContext
    val logoutScope = rememberCoroutineScope()
    var signingOut by remember { mutableStateOf(false) }
    val authState by authViewModel.state.collectAsStateWithLifecycle()
    val user = authState.user
    if (authState.initialized && user == null) {
        SideEffect { sessionStore.clearSession() }
    }
    when {
        !authState.initialized || signingOut -> Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) { CircularProgressIndicator() }
        user == null || (authState.busy && !authState.accountActionBusy) -> AuthScreen(authViewModel)
        else -> key(user.uid) {
            AuthenticatedMarketplace(
                user = user,
                owner = sessionStore.ownerFor(user.uid),
                notice = authState.message,
                authState = authState,
                onDismissNotice = authViewModel::clearMessage,
                onResendVerification = authViewModel::resendVerificationEmail,
                onRefreshVerification = authViewModel::refreshVerificationStatus,
                onSignOut = {
                    if (authViewModel.state.value.busy || signingOut) return@AuthenticatedMarketplace
                    signingOut = true
                    sessionStore.clearSession()
                    val leavingUid = user.uid
                    logoutScope.launch {
                        try {
                            finishSessionLogout(
                                leavingUid = leavingUid,
                                currentUid = { com.batuhanduran.burada.data.remote.FirebaseServices.auth.currentUser?.uid },
                                releasePush = { PushTokenLifecycle.release(appContext) },
                                signOut = authViewModel::signOut
                            )
                        } finally {
                            signingOut = false
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun AuthenticatedMarketplace(
    user: AuthUser,
    owner: ViewModelStoreOwner,
    notice: String?,
    authState: AuthUiState,
    onDismissNotice: () -> Unit,
    onResendVerification: () -> Unit,
    onRefreshVerification: () -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) PushTokenLifecycle.start(context.applicationContext)
    }
    val application = context.applicationContext as Application
    // The Activity retains this UID-scoped store across configuration changes.
    val marketplaceFactory = remember(application, user.uid) {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass == MarketplaceViewModel::class.java)
                return MarketplaceViewModel(application, user.uid) as T
            }
        }
    }
    val marketplace: MarketplaceViewModel = viewModel(
        viewModelStoreOwner = owner,
        factory = marketplaceFactory
    )
    val profileFactory = remember(user.uid) {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass == UserProfileViewModel::class.java)
                return UserProfileViewModel(user) as T
            }
        }
    }
    val profile: UserProfileViewModel = viewModel(
        viewModelStoreOwner = owner,
        factory = profileFactory
    )
    val profileState by profile.state.collectAsStateWithLifecycle()
    val phoneFactory = remember(user.uid) {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass == com.batuhanduran.burada.auth.PhoneVerificationViewModel::class.java)
                return com.batuhanduran.burada.auth.PhoneVerificationViewModel(user.uid) as T
            }
        }
    }
    val phone: com.batuhanduran.burada.auth.PhoneVerificationViewModel =
        viewModel(viewModelStoreOwner = owner, factory = phoneFactory)
    var showPhone by remember { mutableStateOf(false) }
    if (showPhone) com.batuhanduran.burada.ui.components.PhoneVerificationDialog(phone) { showPhone = false }
    MarketplaceApp(viewModel = marketplace, accountHeader = {
        AccountHeader(user, profileState, authState, notice, onDismissNotice, profile::retry,
            onSignOut, { showPhone = true }, onResendVerification, onRefreshVerification) {
            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            else PushTokenLifecycle.start(context.applicationContext)
        }
    })
}

@Composable
internal fun AccountHeader(
    user: AuthUser,
    profile: ProfileSyncState,
    authState: AuthUiState,
    notice: String?,
    onDismissNotice: () -> Unit,
    onRetry: () -> Unit,
    onSignOut: () -> Unit,
    onVerifyPhone: () -> Unit,
    onResendVerification: () -> Unit,
    onRefreshVerification: () -> Unit,
    onEnableNotifications: () -> Unit
) {
    var showAccount by remember(user.uid) { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
        Column(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(user.displayName, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(user.email, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = { showAccount = true }, modifier = Modifier.testTag("btn_account_settings")) {
                    Icon(Icons.Default.ManageAccounts, contentDescription = "Hesap ayarları")
                }
            }
            if (profile.error != null || authState.error != null || notice != null) {
                TextButton(onClick = { showAccount = true }) { Text("Hesap bildirimini görüntüle") }
            }
        }
    }
    if (showAccount) {
        AlertDialog(
            onDismissRequest = { showAccount = false },
            title = { Text("Hesabım") },
            confirmButton = { TextButton(onClick = { showAccount = false }) { Text("Kapat") } },
            text = { Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
            Text(user.displayName, style = MaterialTheme.typography.titleMedium)
            Text(user.email, style = MaterialTheme.typography.bodyMedium)
            Text(if (user.emailVerified) "E-posta doğrulandı" else "E-posta henüz doğrulanmadı",
                style = MaterialTheme.typography.bodySmall)
            if (!user.emailVerified) {
                TextButton(onClick = onResendVerification,
                    enabled = !authState.busy && authState.verificationResendSeconds == 0,
                    modifier = Modifier.testTag("btn_resend_verification")) {
                    Text(if (authState.verificationResendSeconds > 0)
                        "Tekrar gönder (${authState.verificationResendSeconds} sn)" else "Doğrulama e-postası gönder")
                }
                TextButton(onClick = onRefreshVerification, enabled = !authState.busy,
                    modifier = Modifier.testTag("btn_refresh_verification")) { Text("Doğrulamayı kontrol et") }
            }
            if (authState.accountActionBusy) {
                Text("İşlem sürüyor…", style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onVerifyPhone, enabled = !authState.busy) { Text("Telefon doğrulaması") }
            TextButton(onClick = onEnableNotifications, enabled = !authState.busy) { Text("Bildirimleri aç") }
            Text(
                text = when {
                    profile.busy -> "Profil buluta kaydediliyor…"
                    profile.synced -> "Profiliniz buluta kaydedildi."
                    else -> profile.error ?: "Profil henüz buluta kaydedilmedi."
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (profile.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (profile.error != null && !profile.busy) {
                TextButton(onClick = onRetry) { Text("Profil kaydını yeniden dene") }
            }
            (authState.error ?: notice)?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = onDismissNotice) { Text("Tamam") }
            }
            Text(
                "Yayınlanan ilanlar keşfette görünür. Teklifleriniz ve sohbetleriniz hesabınıza özeldir.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = onSignOut, enabled = !authState.busy,
                modifier = Modifier.testTag("btn_sign_out")) { Text("Çıkış yap") }
            } }
        )
    }
}
