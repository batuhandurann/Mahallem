package com.example.auth

object PhoneAuthInputPolicy {
    private val mobile = Regex("^(?:\\+90|0090|0)?5\\d{9}$")
    private val sms = Regex("^\\d{6}$")

    fun normalizedPhoneOrNull(input: String): String? {
        val compact = input.trim().replace(Regex("[ ()-]"), "")
        if (!mobile.matches(compact)) return null
        return when {
            compact.startsWith("+90") -> compact
            compact.startsWith("0090") -> "+" + compact.drop(2)
            compact.startsWith("0") -> "+9" + compact
            else -> "+90" + compact
        }
    }

    fun validSmsCode(input: String): Boolean = sms.matches(input)
}
