package com.example.ui.screens

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private val TURKISH = Locale.forLanguageTag("tr-TR")
private const val ALLOWED_CLOCK_SKEW_MILLIS = 5 * 60 * 1000L

/** Displays a real last-message time in the device's local time zone. */
internal fun formatConversationTimestamp(
    timestampMillis: Long,
    nowMillis: Long = System.currentTimeMillis(),
    timeZone: TimeZone = TimeZone.getDefault()
): String {
    if (timestampMillis <= 0L || timestampMillis > nowMillis + ALLOWED_CLOCK_SKEW_MILLIS) {
        return "Tarih yok"
    }

    val messageDay = Calendar.getInstance(timeZone).apply { timeInMillis = timestampMillis }
    val today = Calendar.getInstance(timeZone).apply { timeInMillis = nowMillis }

    fun sameLocalDay(a: Calendar, b: Calendar): Boolean =
        a.get(Calendar.ERA) == b.get(Calendar.ERA) &&
            a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
            a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

    if (sameLocalDay(messageDay, today)) {
        return SimpleDateFormat("HH:mm", TURKISH).apply {
            this.timeZone = timeZone
        }.format(Date(timestampMillis))
    }

    val yesterday = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
    if (sameLocalDay(messageDay, yesterday)) return "Dün"

    val pattern = if (messageDay.get(Calendar.YEAR) == today.get(Calendar.YEAR)) {
        "d MMM"
    } else {
        "d MMM yyyy"
    }
    return SimpleDateFormat(pattern, TURKISH).apply {
        this.timeZone = timeZone
    }.format(Date(timestampMillis))
}
