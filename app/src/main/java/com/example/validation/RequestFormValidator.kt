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
            if (input.date.isBlank()) add("Tarih girilmelidir.")
            if (input.time.isBlank()) add("Saat girilmelidir.")
            if (input.address.trim().length < 8) add("Geçerli bir adres veya mahalle girilmelidir.")
            if (input.customerName.trim().length < 2) add("Ad soyad girilmelidir.")
            val normalizedPhone = input.customerPhone.replace(Regex("[\s()-]"), "")
            if (!trPhoneRegex.matches(normalizedPhone)) {
                add("Geçerli bir Türkiye cep telefonu girilmelidir.")
            }
            if (input.isPhysicalService && input.areaSquareMeters <= 0) {
                add("Hizmet alanı 1 m² veya daha büyük olmalıdır.")
            }
            if (input.title.trim().length > 120) {
                add("Başlık en fazla 120 karakter olabilir.")
            }
        }
        return ValidationResult(errors.isEmpty(), errors)
    }
}
