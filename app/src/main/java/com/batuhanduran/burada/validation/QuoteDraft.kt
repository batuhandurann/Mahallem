package com.batuhanduran.burada.validation

import java.math.BigDecimal

/**
 * The provider, not the app, must supply a real price and an achievable arrival time.
 * Keep client-side validation fail-closed; Firestore rules remain a separate security boundary.
 */
private val PRICE_FORMAT = Regex(
    """(?:0|[1-9][0-9]{0,8}|[1-9][0-9]{0,2}(?:\.[0-9]{3}){1,2})(?:,[0-9]{1,2})?(?:\s*(?:TL|₺))?""",
    RegexOption.IGNORE_CASE
)
private val MAX_PRICE = BigDecimal("999999999.99")

internal fun quoteDraftError(price: String, arrival: String, notes: String): String? {
    val value = price.trim()
    if (value.isEmpty()) return "Teklif fiyatını kendiniz girin."
    if (value.length > 32 || !PRICE_FORMAT.matches(value)) {
        return "Geçerli fiyat girin (örn. 1.250,50 TL)."
    }
    val numeric = value.replace(Regex("""\s*(?:TL|₺)$""", RegexOption.IGNORE_CASE), "")
        .replace(".", "")
        .replace(",", ".")
    val amount = numeric.toBigDecimalOrNull()
    if (amount == null || amount <= BigDecimal.ZERO || amount > MAX_PRICE) {
        return "Sıfırdan büyük, geçerli bir teklif fiyatı girin."
    }
    if (arrival.trim().isEmpty()) return "Gerçekçi bir varış veya iş süresi yazın."
    if (arrival.trim().length > 120) return "Varış veya iş süresi çok uzun."
    if (notes.trim().length > 1000) return "Teklif açıklaması çok uzun."
    return null
}
