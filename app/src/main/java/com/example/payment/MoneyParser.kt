package com.example.payment

fun parseTryAmountMinor(input: String): Long? {
    val raw = input.trim()
        .replace("₺", "")
        .replace("TL", "", ignoreCase = true)
        .trim()

    if (raw.contains("-")) return null
    val normalized = raw.replace(".", "").replace(",", ".")
    val first = normalized.filter { it.isDigit() || it == '.' }
    val amount = first.toBigDecimalOrNull() ?: return null
    if (amount <= java.math.BigDecimal.ZERO) return null
    return amount.movePointRight(2).longValueExact()
}
