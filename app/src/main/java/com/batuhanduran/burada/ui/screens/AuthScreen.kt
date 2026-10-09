package com.batuhanduran.burada.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.batuhanduran.burada.R
import com.batuhanduran.burada.auth.AuthViewModel

private enum class AuthMode { SIGN_IN, REGISTER, RESET_PASSWORD }

@Composable
fun AuthScreen(viewModel: AuthViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var mode by rememberSaveable { mutableStateOf(AuthMode.SIGN_IN) }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    // Passwords stay in memory and are not saved in the Activity's state bundle.
    var password by remember { mutableStateOf("") }
    var passwordConfirmation by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }
    val keyboard = LocalSoftwareKeyboardController.current
    val enabled = state.initialized && !state.busy
    val passwordSupportingText: (@Composable () -> Unit)? = if (mode == AuthMode.REGISTER) {
        { Text("En az 6 karakter kullanın.") }
    } else null

    fun changeMode(nextMode: AuthMode) {
        mode = nextMode
        password = ""
        passwordConfirmation = ""
        passwordVisible = false
        localError = null
        viewModel.clearMessage()
    }

    fun submit() {
        if (!enabled) return
        localError = null
        keyboard?.hide()
        when (mode) {
            AuthMode.SIGN_IN -> viewModel.signIn(email, password)
            AuthMode.REGISTER -> {
                if (password != passwordConfirmation) {
                    localError = "Şifreler eşleşmiyor. Şifrenizi tekrar kontrol edin."
                } else {
                    viewModel.register(name, email, password)
                }
            }
            AuthMode.RESET_PASSWORD -> viewModel.resetPassword(email)
        }
    }

    Scaffold { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(18.dp).size(44.dp)
                    )
                }
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = when (mode) {
                        AuthMode.SIGN_IN -> "Yakınındaki hizmet verenleri keşfet, ihtiyacın için teklif al."
                        AuthMode.REGISTER -> "Hizmet bulmak veya teklif vermek için hesabını oluştur."
                        AuthMode.RESET_PASSWORD -> "Şifreni yenilemek için e-posta adresini yaz."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (mode != AuthMode.RESET_PASSWORD) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FilterChip(
                            selected = mode == AuthMode.SIGN_IN,
                            onClick = { changeMode(AuthMode.SIGN_IN) },
                            enabled = enabled,
                            label = { Text("Giriş Yap") }
                        )
                        FilterChip(
                            selected = mode == AuthMode.REGISTER,
                            onClick = { changeMode(AuthMode.REGISTER) },
                            enabled = enabled,
                            label = { Text("Kayıt Ol") }
                        )
                    }
                }

                if (mode == AuthMode.REGISTER) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it; localError = null },
                        label = { Text("Adınız ve soyadınız") },
                        singleLine = true,
                        enabled = enabled,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            imeAction = ImeAction.Next
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("auth_name")
                    )
                }

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it; localError = null },
                    label = { Text("E-posta adresi") },
                    singleLine = true,
                    enabled = enabled,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = if (mode == AuthMode.RESET_PASSWORD) ImeAction.Done else ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier.fillMaxWidth().testTag("auth_email")
                )

                if (mode != AuthMode.RESET_PASSWORD) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it; localError = null },
                        label = { Text("Şifre") },
                        singleLine = true,
                        enabled = enabled,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = if (mode == AuthMode.REGISTER) ImeAction.Next else ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { submit() }),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }, enabled = enabled) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (passwordVisible) "Şifreyi gizle" else "Şifreyi göster"
                                )
                            }
                        },
                        supportingText = passwordSupportingText,
                        modifier = Modifier.fillMaxWidth().testTag("auth_password")
                    )
                }

                if (mode == AuthMode.REGISTER) {
                    OutlinedTextField(
                        value = passwordConfirmation,
                        onValueChange = { passwordConfirmation = it; localError = null },
                        label = { Text("Şifre tekrar") },
                        singleLine = true,
                        enabled = enabled,
                        isError = localError != null,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submit() }),
                        modifier = Modifier.fillMaxWidth().testTag("auth_password_confirmation")
                    )
                }

                (localError ?: state.error)?.let { error ->
                    AuthNotice(text = error, error = true, onDismiss = {
                        localError = null
                        viewModel.clearMessage()
                    })
                }
                state.message?.let { message ->
                    AuthNotice(text = message, error = false, onDismiss = viewModel::clearMessage)
                }

                Button(
                    onClick = { submit() },
                    enabled = enabled,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(52.dp).testTag("auth_submit")
                ) {
                    Text(
                        text = when (mode) {
                            AuthMode.SIGN_IN -> "Giriş Yap"
                            AuthMode.REGISTER -> "Hesabımı Oluştur"
                            AuthMode.RESET_PASSWORD -> "Şifre Yenileme Bağlantısı Gönder"
                        },
                        fontWeight = FontWeight.Bold
                    )
                }

                if (!state.initialized || state.busy) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                    ) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp).semantics { contentDescription = "İşlem sürüyor" }
                        )
                        Text(
                            text = if (state.initialized) "İşlem sürüyor…" else "Oturum kontrol ediliyor…",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                if (mode == AuthMode.SIGN_IN) {
                    TextButton(onClick = { changeMode(AuthMode.RESET_PASSWORD) }, enabled = enabled) {
                        Text("Şifremi unuttum")
                    }
                } else if (mode == AuthMode.RESET_PASSWORD) {
                    TextButton(onClick = { changeMode(AuthMode.SIGN_IN) }, enabled = enabled) {
                        Text("Giriş ekranına dön")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun AuthNotice(text: String, error: Boolean, onDismiss: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
        ),
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = text,
                color = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                style = MaterialTheme.typography.bodyMedium
            )
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                Text("Kapat")
            }
        }
    }
}
