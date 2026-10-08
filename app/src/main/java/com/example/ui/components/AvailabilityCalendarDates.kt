package com.example.ui.components

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Returns the next ten local calendar days, including today.
 * Calendar is used to support Android minSdk 24 without java.time desugaring.
 *
 * This is the source of truth for the availability calendar. The existing
 * screen must switch from its 2026 demo constant to this function before
 * the UX issue is considered resolved.
 */
fun upcomingCalendarDays(startDate: Calendar = Calendar.getInstance()): List<CalendarDay> {
    val day = (startDate.clone() as Calendar).apply {
        set(Calendar.HOUR_OF_DAY, 12)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply {
        timeZone = day.timeZone
    }
    val weekdays = listOf("Paz", "Pzt", "Sal", "Çar", "Per", "Cum", "Cmt")
    return List(10) {
        val weekday = day.get(Calendar.DAY_OF_WEEK)
        CalendarDay(
            dateIso = formatter.format(day.time),
            dayName = weekdays[weekday - Calendar.SUNDAY],
            dayNumber = day.get(Calendar.DAY_OF_MONTH).toString(),
            isWeekend = weekday == Calendar.SATURDAY || weekday == Calendar.SUNDAY
        ).also { day.add(Calendar.DAY_OF_MONTH, 1) }
    }
}
