package com.example.ui.screens

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.auth.PhoneAuthRepository
import com.example.auth.UserProfileRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Composable
fun PhoneAuthScreen(
    onAuthenticated: () -> Unit
) {
    val activity = LocalActivity.current
    val phoneAuth = remember { PhoneAuthRepository() }
    val profileRepository = remember { UserProfileRepository() }
    val scope = rememberCoroutineScope()
    var phone by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var verificationId by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    fun finishAuth() {
        scope.launch {
            val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
            if (user == null) {
                error = "Oturum oluşturulamadı."
                loading = false
                return@launch
            }
            runCatching { profileRepository.ensureUserProfile(user) }
                .onFailure { error = it.message ?: "Profil oluşturulamadı." }
                .onSuccess { onAuthenticated() }
            loading = false
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Mahallem", style = MaterialTheme.typography.headlineLarge)
        Text("Güvenli giriş", style = MaterialTheme.typography.titleMedium)
        Text("Devam etmek için telefon numaranı doğrula.", modifier = Modifier.padding(top = 8.dp, bottom = 20.dp))

        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it },
            label = { Text("Telefon numarası") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        if (verificationId != null) {
            OutlinedTextField(
                value = code,
                onValueChange = { code = it.take(6) },
                label = { Text("SMS kodu") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
            )
        }

        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 10.dp)) }

        Button(
            enabled = !loading,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            onClick = {
                error = null
                if (verificationId == null) {
                    val hostActivity = activity
                    if (hostActivity == null) {
                        error = "Doğrulama ekranı başlatılamadı."
                        return@Button
                    }
                    loading = true
                    runCatching {
                        phoneAuth.startVerification(
                            activity = hostActivity,
                            phoneNumber = phone,
                            onCodeSent = { id -> verificationId = id; loading = false },
                            onAutoVerified = { credential ->
                                scope.launch {
                                    runCatching { com.google.firebase.auth.FirebaseAuth.getInstance().signInWithCredential(credential).await() }
                                        .onFailure { error = it.message ?: "Otomatik doğrulama başarısız." }
                                        .onSuccess { finishAuth() }
                                }
                            },
                            onFailure = { ex -> error = ex.message ?: "SMS gönderilemedi."; loading = false }
                        )
                    }.onFailure { error = it.message ?: "Telefon numarası geçersiz."; loading = false }
                } else {
                    loading = true
                    scope.launch {
                        phoneAuth.verifyCode(verificationId!!, code)
                            .onFailure { error = it.message ?: "Kod doğrulanamadı."; loading = false }
                            .onSuccess { finishAuth() }
                    }
                }
            }
        ) {
            Text(if (verificationId == null) "SMS Kodu Gönder" else "Doğrula ve Devam Et")
        }
    }
}