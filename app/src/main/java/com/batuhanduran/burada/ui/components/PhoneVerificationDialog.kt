package com.batuhanduran.burada.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.batuhanduran.burada.auth.PhoneVerificationViewModel

private fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

@Composable
fun PhoneVerificationDialog(model: PhoneVerificationViewModel, onDismiss: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val activity = LocalContext.current.activity()
    var phone by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Telefon doğrulaması") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Numaranız doğrulama ve kötüye kullanım önleme için Google tarafından işlenir. SMS kodunu göndererek bunu kabul edersiniz.")
                if (!state.linked) {
                    OutlinedTextField(value = phone, onValueChange = { if (it.length <= 40) phone = it },
                        label = { Text("Cep telefonu (05xx…)") }, singleLine = true,
                        enabled = !state.busy && !state.codeSent,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.testTag("phone_number"))
                    TextButton(onClick = { activity?.let { model.sendCode(it, phone) } },
                        enabled = !state.busy && activity != null, modifier = Modifier.testTag("phone_send_code")) {
                        Text(if (state.codeSent) "Yeni kod gönder" else "SMS kodu gönder")
                    }
                    if (state.codeSent) OutlinedTextField(value = code,
                        onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) code = it },
                        label = { Text("6 haneli SMS kodu") }, singleLine = true, enabled = !state.busy,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.testTag("phone_sms_code"))
                    if (state.codeSent) TextButton(onClick = { model.changeNumber(); code = "" },
                        enabled = !state.busy) { Text("Numarayı değiştir") }
                } else Text("Hesabınıza doğrulanmış bir telefon bağlı.", modifier = Modifier.testTag("phone_linked"))
                if (state.busy) CircularProgressIndicator()
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                state.message?.let { Text(it) }
            }
        },
        confirmButton = {
            if (state.codeSent && !state.linked) TextButton(onClick = { model.verifyCode(code) },
                enabled = !state.busy, modifier = Modifier.testTag("phone_verify_code")) { Text("Doğrula") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Kapat") } }
    )
}
