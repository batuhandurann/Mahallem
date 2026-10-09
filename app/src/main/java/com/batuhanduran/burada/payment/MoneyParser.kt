package com.batuhanduran.burada.payment

/**
 * Parses an explicitly entered Turkish lira amount into integer kuruş.
 * Rejects partial matches, invalid thousand separators, extra decimal digits
 * and values outside Long range instead of silently removing characters.
 *
 * This helper does not process payments. Actual payment provider is disabled.
 */
fun parseTryAmountMinor(input: String): Long? {
    val raw = input.trim()
    if (raw.isEmpty() || raw.length > 64) return null

    // Dots group thousands; only a comma introduces one or two decimal digits.
    val match = Regex(
        """^(₺\s*|TL\s*)?([0-9]{1,3}(?:\.[0-9]{3})+|[0-9]+)(?:,([0-9]{1,2}))?(\s*(?:₺|TL))?$""",
        RegexOption.IGNORE_CASE
    ).matchEntire(raw) ?: return null

    // A number may have one currency marker, never both a prefix and suffix.
    if (match.groups[1] != null && match.groups[4] != null) return null

    val whole = match.groupValues[2].replace(".", "").toLongOrNull() ?: return null
    val fraction = match.groupValues[3].let {
        if (it.isEmpty()) 0L else it.padEnd(2, '0').toLongOrNull() ?: return null
    }
    // Overflow-safe: never throw from longValueExact on untrusted price strings.
    if (whole > (Long.MAX_VALUE - fraction) / 100) return null
    return (whole * 100 + fraction).takeIf { it > 0L }
}
