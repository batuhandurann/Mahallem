package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.privacy.ConsentRepository
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

@Composable
fun PrivacyConsentScreen(userId: String, onCompleted: () -> Unit) {
    val context = LocalContext.current
    val repository = remember(context, userId) { ConsentRepository(context, userId) }
    var analytics by remember { mutableStateOf(false) }
    var marketing by remember { mutableStateOf(false) }
    var viewedNotice by remember { mutableStateOf(false) }
    var showNotice by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Gizlilik ve tercihler", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Mahallem, hesabın, hizmet taleplerin, iletişim ve güvenlik için gerekli verileri işleyebilir. " +
                "Aydınlatma metninin güncel hukuki metni üretime çıkmadan önce şirket bilgileriyle tamamlanmalıdır.",
            modifier = Modifier.padding(top = 12.dp)
        )
        Button(
            onClick = { showNotice = true },
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp).testTag("privacy_open_notice")
        ) { Text("Aydınlatma metnini görüntüle") }

        RowConsent("Ürün analitiğine izin ver", analytics) { analytics = it }
        RowConsent("Kampanya/pazarlama iletişimine izin ver", marketing) { marketing = it }

        Button(
            enabled = viewedNotice,
            onClick = {
                repository.save(analytics, marketing)
                coroutineScope.launch {
                    runCatching {
                        com.example.auth.UserProfileRepository().saveNotificationPreferences(
                            messagesEnabled = true,
                            marketingEnabled = marketing
                        )
                    }
                    onCompleted()
                }
            },
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp).testTag("privacy_continue")
        ) { Text("Devam Et") }
    }

    if (showNotice) {
        AlertDialog(
            modifier = Modifier.testTag("privacy_notice_dialog"),
            onDismissRequest = { showNotice = false },
            title = { Text("Aydınlatma metni") },
            text = {
                Text(
                    "Mahallem; hesap ve güvenlik, hizmet talepleri, teklifler, mesajlaşma, ödeme/itiraz " +
                        "ve kötüye kullanım önleme için gerekli verileri işler. Ürün analitiği ve pazarlama " +
                        "tercihe bağlıdır. Hesap silme talebi Hesap ve Gizlilik bölümünden yönetilir. " +
                        "Şirket/unvan, iletişim ve yayınlanmış gizlilik politikası bağlantısı üretim yayını " +
                        "öncesinde hukuk onayıyla bu metne eklenmelidir."
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    modifier = Modifier.testTag("privacy_notice_ack"),
                    onClick = {
                        viewedNotice = true
                        showNotice = false
                    }
                ) { Text("Okudum") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showNotice = false }) {
                    Text("Kapat")
                }
            }
        )
    }
}

@Composable
private fun RowConsent(text: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(text)
    }
}