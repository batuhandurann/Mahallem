package com.example.privacy

import android.content.Context

internal fun consentPreferenceKey(userId: String, name: String): String =
    userId.trim() + ":" + name

class ConsentRepository(
    context: Context,
    private val userId: String
) {
    init {
        require(userId.isNotBlank()) { "Kullanıcı kimliği gerekli." }
    }

    private val prefs = context.getSharedPreferences("privacy_consent", Context.MODE_PRIVATE)

    private fun key(name: String): String = consentPreferenceKey(userId, name)

    val privacyNoticeAcknowledged: Boolean
        get() = prefs.getBoolean(key("privacy_notice_ack"), false)

    val analyticsConsent: Boolean
        get() = prefs.getBoolean(key("analytics_consent"), false)

    val marketingConsent: Boolean
        get() = prefs.getBoolean(key("marketing_consent"), false)

    fun save(analytics: Boolean, marketing: Boolean) {
        prefs.edit()
            .putBoolean(key("privacy_notice_ack"), true)
            .putBoolean(key("analytics_consent"), analytics)
            .putBoolean(key("marketing_consent"), marketing)
            .apply()
    }
}
