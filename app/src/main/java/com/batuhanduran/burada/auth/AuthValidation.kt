package com.batuhanduran.burada.auth

/** Local form checks; Firebase remains the authority for account and password policy checks. */
object AuthValidation {
    private val emailPattern = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

    fun emailError(email: String): String? = when {
        email.trim().isEmpty() -> "E-posta adresinizi yazın."
        email.trim().length > 254 || !emailPattern.matches(email.trim()) ->
            "Geçerli bir e-posta adresi yazın."
        else -> null
    }

    fun nameError(name: String): String? = when {
        name.trim().length < 2 -> "Adınız ve soyadınız için en az 2 karakter yazın."
        name.trim().length > 80 -> "Adınız ve soyadınız en fazla 80 karakter olabilir."
        name.any { it.isISOControl() } -> "Adınızı ve soyadınızı tek satırda yazın."
        else -> null
    }

    fun passwordError(password: String, registering: Boolean): String? = when {
        password.isEmpty() -> "Şifrenizi yazın."
        registering && password.length < 6 -> "Şifreniz en az 6 karakter olmalı."
        else -> null
    }
}
