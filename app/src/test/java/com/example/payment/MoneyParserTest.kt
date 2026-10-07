package com.example.payment

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyParserTest {
    @Test fun parsesTurkishThousands() {
        assertEquals(1_250_000L, parseTryAmountMinor("12.500 ₺"))
    }

    @Test fun parsesDecimal() {
        assertEquals(125_050L, parseTryAmountMinor("1.250,50 TL"))
    }

    @Test fun parsesSingleDecimalDigitAsTenthsOfLira() {
        assertEquals(10_050L, parseTryAmountMinor("100,5 TL"))
    }

    @Test fun ignoresWhitespaceLikeBackendParser() {
        assertEquals(280_000L, parseTryAmountMinor(" 2 800 ₺ "))
    }

    @Test fun rejectsRanges() {
        assertNull(parseTryAmountMinor("3.000 - 6.000 ₺"))
    }

    @Test fun rejectsZeroAndNegativeValues() {
        assertNull(parseTryAmountMinor("0 ₺"))
        assertNull(parseTryAmountMinor("-10 ₺"))
    }

    @Test fun rejectsMalformedThousandsGrouping() {
        assertNull(parseTryAmountMinor("12.50 TL"))
        assertNull(parseTryAmountMinor("1.00.000 TL"))
    }

    @Test fun rejectsUnexpectedText() {
        assertNull(parseTryAmountMinor("yaklaşık 3.500 TL"))
    }

    @Test fun rejectsOverflowInsteadOfThrowing() {
        assertNull(parseTryAmountMinor("999999999999999999999999999999999999"))
    }
}
