package com.batuhanduran.burada.ui.components

import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone

data class CalendarDay(
    val dateIso: String,
    val dayName: String,
    val dayNumber: String,
    val isWeekend: Boolean
)

/**
 * Generates ten consecutive LOCAL calendar days, starting today.
 * Calendar.add(DATE) (not +24 hours) handles DST transitions and year rollovers.
 * The clock and zone are injectable for deterministic unit / Compose tests.
 */
fun generateUpcomingCalendarDays(
    today: Date = Date(),
    timeZone: TimeZone = TimeZone.getDefault()
): List<CalendarDay> {
    val calendar = GregorianCalendar(timeZone, Locale.forLanguageTag("tr-TR")).apply {
        time = today
    }
    return List(10) {
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        val dayName = when (dayOfWeek) {
            Calendar.MONDAY -> "Pzt"
            Calendar.TUESDAY -> "Sal"
            Calendar.WEDNESDAY -> "Çar"
            Calendar.THURSDAY -> "Per"
            Calendar.FRIDAY -> "Cum"
            Calendar.SATURDAY -> "Cmt"
            else -> "Paz"
        }
        val day = CalendarDay(
            dateIso = String.format(
                Locale.ROOT,
                "%04d-%02d-%02d",
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH) + 1,
                calendar.get(Calendar.DAY_OF_MONTH)
            ),
            dayName = dayName,
            dayNumber = calendar.get(Calendar.DAY_OF_MONTH).toString(),
            isWeekend = dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY
        )
        calendar.add(Calendar.DAY_OF_MONTH, 1)
        day
    }
}
