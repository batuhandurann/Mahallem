package com.batuhanduran.burada.auth

object PhoneValidation {
    fun normalize(value: String): String? {
        if (!Regex("^[+0-9 ()-]{1,40}$").matches(value)) return null
        val compact = value.replace(Regex("[ ()-]"), "")
        val number = when {
            Regex("^05\\d{9}$").matches(compact) -> "+90" + compact.drop(1)
            Regex("^5\\d{9}$").matches(compact) -> "+90$compact"
            Regex("^905\\d{9}$").matches(compact) -> "+$compact"
            else -> compact
        }
        return number.takeIf { Regex("^\\+905\\d{9}$").matches(it) }
    }
    fun validCode(code: String) = Regex("^[0-9]{6}$").matches(code)
}
