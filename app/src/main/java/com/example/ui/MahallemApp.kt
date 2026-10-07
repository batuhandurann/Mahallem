package com.example.ui

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.MarketplaceApp
import com.example.auth.AuthUser
import com.example.auth.AuthViewModel
import com.example.data.remote.ProfileSyncState
import com.example.data.remote.UserProfileViewModel
import com.example.ui.screens.AuthScreen

@Composable
fun MahallemApp(
    authViewModel: AuthViewModel = viewModel(),
    sessionStore: SessionStoreViewModel = viewModel()
) {
    val authState by authViewModel.state.collectAsStateWithLifecycle()
    val user = authState.user
    if (authState.initialized && user == null) {
        SideEffect { sessionStore.clearSession() }
    }
    when {
        !authState.initialized -> Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) { CircularProgressIndicator() }
        user == null || authState.busy -> AuthScreen(authViewModel)
        else -> key(user.uid) {
            AuthenticatedMarketplace(
                user = user,
                owner = sessionStore.ownerFor(user.uid),
                notice = authState.message,
                onDismissNotice = authViewModel::clearMessage,
                onSignOut = {
                    sessionStore.clearSession()
                    authViewModel.signOut()
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
    onDismissNotice: () -> Unit,
    onSignOut: () -> Unit
) {
    val application = LocalContext.current.applicationContext as Application
    // The Activity retains this UID-scoped store across configuration changes.
    val marketplaceFactory = remember(application) {
        ViewModelProvider.AndroidViewModelFactory(application)
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
    MarketplaceApp(viewModel = marketplace, accountHeader = {
        AccountHeader(user, profileState, notice, onDismissNotice, profile::retry, onSignOut)
    })
}

@Composable
private fun AccountHeader(
    user: AuthUser,
    profile: ProfileSyncState,
    notice: String?,
    onDismissNotice: () -> Unit,
    onRetry: () -> Unit,
    onSignOut: () -> Unit
) {
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
                TextButton(onClick = onSignOut) { Text("Çıkış yap") }
            }
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
            notice?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = onDismissNotice) { Text("Tamam") }
            }
            Text(
                "İlanlar, teklifler ve mesajlar bu cihazdaki ortak demo verileridir.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
