package com.batuhanduran.burada.payment

import java.net.URI

enum class CheckoutStatus { NOT_STARTED, READY, INITIALIZING, UNKNOWN, PAID, REVIEW, FAILED }
enum class CheckoutEnvironment { DISABLED, SANDBOX, PRODUCTION }
data class PaymentAvailability(val available: Boolean, val environment: CheckoutEnvironment)
data class HostedCheckout(val status: CheckoutStatus, val environment: CheckoutEnvironment, val paymentPageUrl: String? = null)

/** Only documented provider HTTPS hosts may leave the application. Never accept intent URLs. */
object HostedCheckoutPolicy {
    fun environment(value: Any?): CheckoutEnvironment = when (value) {
        "sandbox" -> CheckoutEnvironment.SANDBOX
        "production" -> CheckoutEnvironment.PRODUCTION
        else -> CheckoutEnvironment.DISABLED
    }

    fun allowedUrl(value: String?, environment: CheckoutEnvironment): Boolean = runCatching {
        if (value.isNullOrBlank() || value.length > 4096) return false
        val uri = URI(value)
        val host = when (environment) {
            CheckoutEnvironment.SANDBOX -> "sandbox-api.iyzipay.com"
            CheckoutEnvironment.PRODUCTION -> "api.iyzipay.com"
            CheckoutEnvironment.DISABLED -> return false
        }
        uri.scheme == "https" && uri.host == host && uri.rawUserInfo == null &&
            uri.port == -1 && uri.fragment == null &&
            (uri.path.startsWith("/checkoutform/") || uri.path == "/payment/checkoutform/initialize/auth/ecom")
    }.getOrDefault(false)

    fun decode(data: Map<*, *>): HostedCheckout {
        val environment = environment(data["environment"])
        val status = CheckoutStatus.entries.find { it.name == data["status"] } ?: CheckoutStatus.UNKNOWN
        val url = (data["paymentPageUrl"] as? String)?.takeIf { status == CheckoutStatus.READY && allowedUrl(it, environment) }
        return HostedCheckout(status, environment, url)
    }
}
