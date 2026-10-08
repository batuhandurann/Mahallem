package com.example.validation

import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.GregorianCalendar

data class RequestSchedule(val date: String, val time: String)

object RequestSchedules {
    fun now(instant: Date = Date()): RequestSchedule = RequestSchedule(
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(instant),
        SimpleDateFormat("HH:mm", Locale.US).format(instant)
    )

    fun isValidDate(value: String): Boolean {
        if (!value.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}"))) return false
        if (value.take(4).toInt() < 100) return false
        val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            calendar = GregorianCalendar().apply { gregorianChange = Date(Long.MIN_VALUE) }
            isLenient = false
        }
        val position = ParsePosition(0)
        return parser.parse(value, position) != null && position.index == value.length
    }

    fun isValidTime(value: String): Boolean =
        value.matches(Regex("(?:[01][0-9]|2[0-3]):[0-5][0-9]"))

    fun requireValid(title: String, date: String, time: String) {
        require(title.trim().isNotEmpty()) { "Başlık girilmelidir." }
        require(title.trim().length <= 120) { "Başlık en fazla 120 karakter olabilir." }
        require(isValidDate(date)) { "Geçerli bir tarih girin (YYYY-AA-GG)." }
        require(isValidTime(time)) { "Geçerli bir saat girin (SS:DD, 00:00–23:59)." }
    }
}
