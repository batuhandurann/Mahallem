package com.batuhanduran.burada.payment

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyParserTest {
    @Test fun parsesTurkishThousands() {
        assertEquals(1250000L, parseTryAmountMinor("12.500 ₺"))
        assertEquals(1250000L, parseTryAmountMinor("₺12.500"))
    }

    @Test fun parsesDecimal() {
        assertEquals(125050L, parseTryAmountMinor("1.250,50 TL"))
        assertEquals(150L, parseTryAmountMinor("1,5 tl"))
        assertEquals(1L, parseTryAmountMinor("0,01"))
        assertEquals(2500000L, parseTryAmountMinor("25000"))
    }

    @Test fun rejectsRangesAndSigns() {
        for (value in listOf("3.000 - 6.000 ₺", "-5 TL", "+5", "1..000", "1.00.000")) {
            assertNull(value, parseTryAmountMinor(value))
        }
    }

    @Test fun rejectsZero() {
        assertNull(parseTryAmountMinor("0 ₺"))
        assertNull(parseTryAmountMinor("0,00"))
    }

    @Test fun rejectsMalformedInputInsteadOfSilentlyRemovingText() {
        for (value in listOf(
            "1abc2", "12 ₺ ücretsiz", "1 TL ve 2 TL", "₺12 TL",
            "1,234", "1.234,567", "1.2", "1.2.3", "1,",
            "1e5", "1 000", "NaN", "Infinity", "", "   "
        )) {
            assertNull(value, parseTryAmountMinor(value))
        }
    }

    @Test fun rejectsOverflowWithoutThrowing() {
        assertNull(parseTryAmountMinor("92233720368547758,08"))
        assertNull(parseTryAmountMinor("99999999999999999999999999999999"))
        assertEquals(9223372036854775807L,
            parseTryAmountMinor("92233720368547758,07"))
    }

    @Test fun rejectsHugeInputsBeforeParsing() {
        assertNull(parseTryAmountMinor("9".repeat(10000)))
    }
}
