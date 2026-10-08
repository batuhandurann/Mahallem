package com.batuhanduran.burada.data.model

enum class SectorType(val titleTr: String, val subtitleTr: String, val icon: String) {
    ALL("Tümü", "Tüm Sektörler", "🌐"),
    HOME_REPAIR("Ev & Tamirat", "Tesisat, Elektrik, Boya, Çilingir", "🔨"),
    CLEANING("Temizlik & Bakım", "Ev, Koltuk, Halı, İlaçlama", "✨"),
    EVENT_ENTERTAINMENT("Etkinlik & Eğlence", "Palyaço, Maskot, DJ, Sihirbaz", "🎉"),
    MOVING_ASSEMBLY("Nakliye & Montaj", "Evden Eve, Parça Eşya, Mobilya", "🚚"),
    TUTORING_CONSULTING("Özel Ders & Eğitim", "Matematik, Müzik, Diyetisyen", "📚"),
    PERSONAL_CARE("Kişisel Bakım & Pet", "Evde Kuaför, Bakım, Pet Kuaför", "✂️")
}

enum class FeedFlowType(val labelTr: String, val badgeTr: String) {
    ALL("Tüm İlanlar", "Pazar Yeri"),
    PROVIDER_OFFERS("Esnaf/Usta İlanları", "Letgo/Sahibinden Vitrini"),
    SEEKER_REQUESTS("Müşteri Talepleri", "Armut Talep Panosu")
}

enum class UrgencyMode(val labelTr: String, val badgeTr: String) {
    ALL("Tümü", "Tüm İlanlar"),
    EMERGENCY("🚨 Acil / Hemen", "1 Saatte Kapında"),
    PLANNED("📅 Planlı Rezervasyon", "İleri Tarihli")
}

enum class RequestStatus(val labelTr: String) {
    PENDING("Teklif Bekleniyor"),
    QUOTED("Teklif Geldi"),
    ACCEPTED("Kabul Edildi"),
    COMPLETED("Tamamlandı")
}

enum class QuoteStatus(val labelTr: String) {
    PENDING("Değerlendirmede"),
    ACCEPTED("Kabul Edildi"),
    REJECTED("Reddedildi")
}
