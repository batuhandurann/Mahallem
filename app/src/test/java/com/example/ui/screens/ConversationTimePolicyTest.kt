package com.example.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.GregorianCalendar
import java.util.TimeZone

class ConversationTimePolicyTest {
    private val tz = TimeZone.getTimeZone("Europe/Istanbul")
    private fun at(day: Int, hour: Int): Long =
        GregorianCalendar(tz).apply { set(2026, 9, day, hour, 0, 0); set(java.util.Calendar.MILLISECOND, 0) }.timeInMillis

    @Test fun today() {
        assertEquals("14:00", formatConversationTimestamp(at(8, 14), at(8, 15), tz))
    }

    @Test fun yesterday() {
        assertEquals("Dün", formatConversationTimestamp(at(7, 23), at(8, 1), tz))
    }

    @Test fun unknown() {
        assertEquals("Tarih yok", formatConversationTimestamp(0L, at(8, 15), tz))
    }
}
