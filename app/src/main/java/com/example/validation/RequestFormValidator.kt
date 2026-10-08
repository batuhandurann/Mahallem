package com.example.validation

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class RequestFormInput(
    val title: String,
    val district: String,
    val date: String,
    val time: String,
    val address: String,
    val customerName: String,
    val customerPhone: String,
    val areaSquareMeters: Int,
    val isPhysicalService: Boolean,
    val isEmergency: Boolean = false
)

data class ValidationResult(
    val isValid: Boolean,
    val errors: List<String>
)

object RequestFormValidator {
    private val trPhoneRegex = Regex("""^(?:\+90|0090|0)?5\d{9}$""")
    private val isoDate = Regex("""^\d{4}-\d{2}-\d{2}$""")
    private val clockTime = Regex("""^(?:[01]\d|2[0-3]):[0-5]\d$""")

    fun validate(input: RequestFormInput, now: Date = Date()): ValidationResult {
        val emergencyDate = "Hemen / Bugün"
        val emergencyTime = "En geç 1 saat içinde"
        val isEmergency = input.isEmergency && input.date == emergencyDate && input.time == emergencyTime
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { isLenient = false }
        val timeFormat = SimpleDateFormat("HH:mm", Locale.ROOT)
        val today = dateFormat.format(now)

        val errors = buildList {
            if (input.district.isBlank() || input.district == "Tüm İlçeler") {
                add("İlçe seçilmelidir.")
            }
            if (input.date.isBlank()) {
                add("Tarih girilmelidir.")
            } else if (!isEmergency && (input.date == emergencyDate || input.time == emergencyTime)) {
                add("Acil hizmet için tarih ve saat birlikte seçilmelidir.")
            } else if (!isEmergency) {
                val validDay = isoDate.matches(input.date) &&
                    runCatching { dateFormat.parse(input.date) }.getOrNull() != null
                if (!validDay) {
                    add("Tarih YYYY-AA-GG biçiminde geçerli bir gün olmalıdır.")
                } else if (input.date < today) {
                    add("Geçmiş bir tarih seçilemez.")
                }
            }

            if (input.time.isBlank()) {
                add("Saat girilmelidir.")
            } else if (!isEmergency && input.time != emergencyTime) {
                if (!clockTime.matches(input.time)) {
                    add("Saat SS:DD biçiminde geçerli olmalıdır.")
                } else if (input.date == today && input.time < timeFormat.format(now)) {
                    add("Geçmiş bir saat seçilemez.")
                }
            }
            if (input.address.trim().length < 8) add("Geçerli bir adres veya mahalle girilmelidir.")
            if (input.customerName.trim().length < 2) add("Ad soyad girilmelidir.")
            val normalizedPhone = input.customerPhone.replace(Regex("""[\s()-]"""), "")
            if (!trPhoneRegex.matches(normalizedPhone)) {
                add("Geçerli bir Türkiye cep telefonu girilmelidir.")
            }
            if (input.isPhysicalService && input.areaSquareMeters <= 0) {
                add("Hizmet alanı 1 m² veya daha büyük olmalıdır.")
            }
            if (input.title.isBlank()) add("Başlık girilmelidir.")
            if (input.title.trim().length > 120) {
                add("Başlık en fazla 120 karakter olabilir.")
            }
        }
        return ValidationResult(errors.isEmpty(), errors)
    }
}
