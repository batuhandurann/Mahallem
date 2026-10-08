package com.example.validation

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class RequestSchedule(val date: String, val time: String) {
    companion object {
        const val EMERGENCY_DATE = "Hemen / Bugün"
        const val EMERGENCY_TIME = "En geç 1 saat içinde"

        fun resolve(date: String, time: String, isEmergency: Boolean, now: Date = Date()): RequestSchedule {
            if (date == EMERGENCY_DATE || time == EMERGENCY_TIME) {
                require(isEmergency && date == EMERGENCY_DATE && time == EMERGENCY_TIME) {
                    "Acil hizmet için tarih ve saat birlikte seçilmelidir."
                }
                return RequestSchedule(
                    SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(now),
                    SimpleDateFormat("HH:mm", Locale.ROOT).format(now)
                )
            }
            require(Regex("""^\d{4}-\d{2}-\d{2}$""").matches(date)) {
                "Tarih YYYY-AA-GG biçiminde geçerli bir gün olmalıdır."
            }
            val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { isLenient = false }
            val parsed = runCatching { formatter.parse(date) }.getOrNull()
            require(parsed != null && formatter.format(parsed) == date) {
                "Tarih YYYY-AA-GG biçiminde geçerli bir gün olmalıdır."
            }
            require(Regex("""^(?:[01]\d|2[0-3]):[0-5]\d$""").matches(time)) {
                "Saat SS:DD biçiminde geçerli olmalıdır."
            }
            return RequestSchedule(date, time)
        }
    }
}
