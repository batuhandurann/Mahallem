package com.example.payment

import java.math.BigInteger

private val TRY_AMOUNT_PATTERN =
    Regex("""^(?:\d+|\d{1,3}(?:\.\d{3})+)(?:,\d{1,2})?$""")
private val TRY_CURRENCY_SUFFIX = Regex("""(?:TL|₺)$""", RegexOption.IGNORE_CASE)
private val MAX_SAFE_MINOR_UNITS = BigInteger.valueOf(9_007_199_254_740_991L)

fun parseTryAmountMinor(input: String): Long? {
    // Currency markers are only valid at the beginning (₺) or end (TL/₺).
    // Removing them globally would silently turn "1TL2" into a payment of 12 TL.
    val compact = input.trim().replace(Regex("""\s+"""), "")
    val raw = if (compact.startsWith("₺")) {
        compact.drop(1)
    } else {
        compact.replace(TRY_CURRENCY_SUFFIX, "")
    }

    if (!TRY_AMOUNT_PATTERN.matches(raw)) return null

    val normalized = raw.replace(".", "").replace(",", ".")
    val parts = normalized.split(".")
    if (parts.size > 2) return null

    val whole = parts[0]
    val fraction = parts.getOrElse(1) { "" }
    if (whole.isEmpty() || !whole.all(Char::isDigit)) return null
    if (fraction.length > 2 || !fraction.all(Char::isDigit)) return null

    val wholeValue = whole.toBigIntegerOrNull() ?: return null
    val fractionValue = (fraction + "00").take(2).toBigIntegerOrNull() ?: return null
    val minor = wholeValue.multiply(BigInteger.valueOf(100L)).add(fractionValue)

    // Cloud Functions uses JS numbers; do not accept amounts it cannot represent exactly.
    if (minor <= BigInteger.ZERO || minor > MAX_SAFE_MINOR_UNITS) return null
    return minor.toLong()
}
