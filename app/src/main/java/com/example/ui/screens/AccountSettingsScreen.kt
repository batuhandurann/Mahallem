package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.auth.AccountLifecycleRepository
import com.example.auth.AuthRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

private suspend fun signOutWithFeedback(onError: (String) -> Unit) {
    try {
        AuthRepository().signOutAndRemoveDevice()
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        onError("Yerel veriler temizlenemedi. Güvenli çıkış için tekrar deneyin.")
    }
}

@Composable
fun AccountSettingsScreen(isLocalMode: Boolean) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val user = if (isLocalMode) null else runCatching { FirebaseAuth.getInstance().currentUser }.getOrNull()

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).testTag("account_settings_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Hesap ve Gizlilik", style = MaterialTheme.typography.headlineSmall)
        Text(
            if (isLocalMode) "Yerel demo modunda gerçek hesap işlemleri kapalıdır."
            else user?.phoneNumber ?: user?.email ?: "Doğrulanmış hesap"
        )
        Text("Gizlilik ve analiz tercihleri bu hesaba özel tutulur.")

        if (!isLocalMode) {
            OutlinedButton(
                onClick = {
                    busy = true
                    scope.launch {
                        try {
                            signOutWithFeedback { message = it }
                        } finally {
                            busy = false
                        }
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().testTag("account_sign_out")
            ) { Text("Çıkış Yap") }

            Button(
                onClick = { confirmDelete = true },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().testTag("account_request_deletion")
            ) { Text("Hesabımı Sil") }
        }

        message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { if (!busy) confirmDelete = false },
            title = { Text("Hesap silme talebi") },
            text = {
                Text(
                    "Hesabınız 30 günlük bekleme süresinden sonra kalıcı olarak silinecek. " +
                        "Bu süre içinde tekrar giriş yaparak silme talebini iptal edebilirsiniz."
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        busy = true
                        scope.launch {
                            runCatching {
                                AccountLifecycleRepository().requestDeletion()
                                signOutWithFeedback { message = it }
                                busy = false
                                confirmDelete = false
                            }.onFailure {
                                message = it.message ?: "Hesap silme talebi oluşturulamadı."
                                busy = false
                                confirmDelete = false
                            }
                        }
                    }
                ) { Text("Silme Talebi Oluştur") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }, enabled = !busy) { Text("Vazgeç") }
            }
        )
    }
}

@Composable
fun AccountDeletionPendingScreen(
    status: String,
    onCanceled: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val canCancel = status == "REQUESTED"

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).testTag("account_deletion_pending_screen"),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Hesap silme işlemi", style = MaterialTheme.typography.headlineSmall)
        Text(
            if (canCancel) {
                "Hesabınız silinmek üzere işaretlendi. 30 günlük süre dolmadan talebi iptal edebilirsiniz."
            } else {
                "Hesabınızın kalıcı silme işlemi başladı. Bu aşama artık iptal edilemez."
            },
            modifier = Modifier.padding(top = 12.dp, bottom = 20.dp)
        )

        if (canCancel) {
            Button(
                enabled = !busy,
                onClick = {
                    busy = true
                    scope.launch {
                        runCatching { AccountLifecycleRepository().cancelDeletion() }
                            .onSuccess { canceled ->
                                if (canceled) onCanceled()
                                else message = "Silme talebi zaten aktif değil."
                            }
                            .onFailure {
                                message = it.message ?: "Silme talebi iptal edilemedi. Güvenlik için yeniden giriş gerekebilir."
                            }
                        busy = false
                    }
                },
                modifier = Modifier.fillMaxWidth().testTag("account_cancel_deletion")
            ) { Text("Silme Talebini İptal Et") }
        }

        OutlinedButton(
            enabled = !busy,
            onClick = {
                busy = true
                scope.launch {
                    try {
                        signOutWithFeedback { message = it }
                    } finally {
                        busy = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
        ) { Text("Çıkış Yap") }

        message?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp)) }
    }
}
