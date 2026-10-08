package com.example.validation

import java.util.GregorianCalendar
import java.util.TimeZone

data class RequestFormInput(
    val title: String,
    val district: String,
    val date: String,
    val time: String,
    val address: String,
    val customerName: String,
    val customerPhone: String,
    val areaSquareMeters: Int,
    val isPhysicalService: Boolean
)

data class ValidationResult(
    val isValid: Boolean,
    val errors: List<String>
)

object RequestFormValidator {
    private val dateRegex = Regex("""^\\d{4}-\\d{2}-\\d{2}$""")
    private val timeRegex = Regex("""^([01]\\d|2[0-3]):[0-5]\\d$""")

    private fun validDate(value: String): Boolean {
        if (!dateRegex.matches(value)) return false
        val year = value.substring(0, 4).toInt()
        if (year < 100) return false
        return runCatching {
            GregorianCalendar(TimeZone.getTimeZone("UTC")).apply {
                isLenient = false
                clear()
                set(year, value.substring(5, 7).toInt() - 1, value.substring(8, 10).toInt())
            }.time
        }.isSuccess
    }

    private val trPhoneRegex = Regex("""^(?:\+90|0090|0)?5\d{9}$""")

    fun validate(input: RequestFormInput): ValidationResult {
        val errors = buildList {
            if (input.district.isBlank() || input.district == "Tüm İlçeler") {
                add("İlçe seçilmelidir.")
            }
            if (!validDate(input.date)) add("Geçerli bir tarih girilmelidir (YYYY-MM-DD).")
            if (!timeRegex.matches(input.time)) add("Geçerli bir saat girilmelidir (HH:MM).")
            if (input.address.trim().length < 8) add("Geçerli bir adres veya mahalle girilmelidir.")
            if (input.customerName.trim().length < 2) add("Ad soyad girilmelidir.")
            val normalizedPhone = input.customerPhone.replace(Regex("""[\s()-]"""), "")
            if (!trPhoneRegex.matches(normalizedPhone)) {
                add("Geçerli bir Türkiye cep telefonu girilmelidir.")
            }
            if (input.isPhysicalService && input.areaSquareMeters <= 0) {
                add("Hizmet alanı 1 m² veya daha büyük olmalıdır.")
            }
            if (input.title.isBlank()) add("Başlık boş olamaz.")
            if (input.title.trim().length > 120) {
                add("Başlık en fazla 120 karakter olabilir.")
            }
        }
        return ValidationResult(errors.isEmpty(), errors)
    }
}
