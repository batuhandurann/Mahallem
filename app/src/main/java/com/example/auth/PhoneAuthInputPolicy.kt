package com.example.auth

/** Validates local Turkish mobile input before triggering a billable Firebase SMS request. */
object PhoneAuthInputPolicy {
    private val mobile = Regex("^(?:\\+90|0090|0)?5\\d{9}$")
    private val sms = Regex("^[0-9]{6}$")

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

    /** UI cooldown only. Server-side Firebase throttling remains authoritative. */
    fun resendWaitSeconds(sentAtElapsedMs: Long, nowElapsedMs: Long): Int {
        if (sentAtElapsedMs <= 0L) return 0
        val elapsed = (nowElapsedMs - sentAtElapsedMs).coerceAtLeast(0L)
        return ((60_000L - elapsed).coerceAtLeast(0L) + 999L).div(1000L).toInt()
    }
}
