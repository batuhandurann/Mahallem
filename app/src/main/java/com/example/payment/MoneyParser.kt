package com.example.payment

import java.math.BigInteger

private val TRY_AMOUNT_PATTERN =
    Regex("""^(?:\d+|\d{1,3}(?:\.\d{3})+)(?:,\d{1,2})?$""")

fun parseTryAmountMinor(input: String): Long? {
    val raw = input
        .replace("₺", "")
        .replace("TL", "", ignoreCase = true)
        .trim()
        .replace(Regex("""\s+"""), "")

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

    if (minor <= BigInteger.ZERO || minor > BigInteger.valueOf(Long.MAX_VALUE)) return null
    return minor.toLong()
}
