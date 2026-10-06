package com.example.privacy

import android.content.Context

class ConsentRepository(context: Context) {
    private val prefs = context.getSharedPreferences("privacy_consent", Context.MODE_PRIVATE)

    val privacyNoticeAcknowledged: Boolean
        get() = prefs.getBoolean("privacy_notice_ack", false)

    val analyticsConsent: Boolean
        get() = prefs.getBoolean("analytics_consent", false)

    val marketingConsent: Boolean
        get() = prefs.getBoolean("marketing_consent", false)

    fun save(analytics: Boolean, marketing: Boolean) {
        prefs.edit()
            .putBoolean("privacy_notice_ack", true)
            .putBoolean("analytics_consent", analytics)
            .putBoolean("marketing_consent", marketing)
            .apply()
    }
}