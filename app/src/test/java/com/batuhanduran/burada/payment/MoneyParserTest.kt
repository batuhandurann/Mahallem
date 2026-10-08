package com.batuhanduran.burada.payment

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyParserTest {
    @Test fun parsesTurkishThousands() {
        assertEquals(1250000L, parseTryAmountMinor("12.500 ₺"))
    }

    @Test fun parsesDecimal() {
        assertEquals(125050L, parseTryAmountMinor("1.250,50 TL"))
    }

    @Test fun rejectsRanges() {
        assertNull(parseTryAmountMinor("3.000 - 6.000 ₺"))
    }

    @Test fun rejectsZero() {
        assertNull(parseTryAmountMinor("0 ₺"))
    }
}
