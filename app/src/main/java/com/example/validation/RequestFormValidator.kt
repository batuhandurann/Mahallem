package com.example.validation

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
    private val trPhoneRegex = Regex("""^(?:\+90|0090|0)?5\d{9}$""")

    fun validate(input: RequestFormInput): ValidationResult {
        val errors = buildList {
            if (input.district.isBlank() || input.district == "Tüm İlçeler") {
                add("İlçe seçilmelidir.")
            }
            if (!RequestSchedules.isValidDate(input.date)) add("Geçerli bir tarih girin (YYYY-AA-GG).")
            if (!RequestSchedules.isValidTime(input.time)) add("Geçerli bir saat girin (SS:DD, 00:00–23:59).")
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
