package com.batuhanduran.burada.data.local

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ServiceProviderEntity(
    val id: String,
    val name: String,
    val title: String,
    val sector: String, // HOME_REPAIR, CLEANING, EVENT_ENTERTAINMENT, MOVING_ASSEMBLY, TUTORING_CONSULTING, PERSONAL_CARE
    val categoryId: String,
    val rating: Double,
    val reviewCount: Int,
    val experienceYears: Int,
    val district: String,
    val city: String,
    val hourlyOrBasePrice: String,
    val isEmergencyAvailable: Boolean,
    val verifiedSafeBadge: Boolean, // Sabıka Kaydı / Adli Sicil Onayı
    val mykCertified: Boolean, // Ustalık Belgesi
    val childSafeCertified: Boolean, // Çocuklu Aileler İçin Güvenli Rozeti
    val phoneVerified: Boolean = false, // SMS / Telefon Doğrulaması
    val daysRemaining: Int = 30, // İlan geçerlilik süresi
    val isReported: Boolean = false,
    val paintBrandsJson: String = "", // Boyacılar için
    val charactersOfferedJson: String = "", // Maskot/Palyaço için
    val includedEquipmentsJson: String = "", // Ekipmanlar / Malzemeler
    val bookedDatesJson: String = "", // Dolu günler
    val isOpenForOffers: Boolean = true, // Teklif alımına açık/kapalı
    val phone: String,
    val bio: String,
    val beforeAfterJson: String = "",
    val videoShowcasesJson: String = "",
    val isFavorite: Boolean = false,
    val ownerUid: String = "",
    val provinceId: String = "",
    val districtId: String = "",
    val neighborhoodId: String = "",
    val neighborhoodName: String = "",
    val publicGeoHash: String = ""
)

@JsonClass(generateAdapter = true)
data class JobRequestEntity(
    val id: String = "",
    val title: String,
    val sector: String,
    val categoryId: String,
    val district: String,
    val urgencyMode: String, // EMERGENCY, PLANNED
    val eventOrJobDate: String,
    val eventTime: String,
    val address: String,
    val status: String, // PENDING, QUOTED, ACCEPTED, COMPLETED
    val customerName: String,
    val customerPhone: String,
    val phoneVerified: Boolean = false,
    val daysRemaining: Int = 7, // İhtiyaç ilanları için bitiş süresi (Örn: 7 gün)
    val isReported: Boolean = false,
    // Escrow & Havuz Güvencesi
    val escrowStatus: String = "NONE", // NONE, LOCKED, RELEASED, DISPUTED
    val escrowAmount: String = "",
    // Tadilat alanları
    val areaSquareMeters: Int = 0,
    val roomCount: String = "",
    val isFurnished: Boolean = false,
    val materialsIncluded: Boolean = false,
    val renovationNotes: String = "",
    // Organizasyon alanları
    val eventType: String = "",
    val durationHours: Int = 1,
    val targetAgeGroup: String = "",
    val selectedCostumeOrCharacter: String = "",
    val extraServicesRequested: String = "",
    val budgetEstimate: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val ownerUid: String = "",
    val provinceId: String = "",
    val districtId: String = "",
    val neighborhoodId: String = "",
    val neighborhoodName: String = "",
    val publicGeoHash: String = ""
)

@JsonClass(generateAdapter = true)
data class QuoteEntity(
    val id: String = "",
    val requestId: String,
    val providerId: String,
    val providerName: String,
    val providerTitle: String,
    val providerRating: Double,
    val price: String,
    val durationOrArrival: String,
    val notes: String,
    val status: String = "PENDING", // PENDING, ACCEPTED, REJECTED, WITHDRAWN
    val escrowFunded: Boolean = false,
    val receiptCode: String = "",
    val warrantyDuration: String = "2 Yıl İşçilik & Malzeme Garantisi",
    val createdAt: Long = System.currentTimeMillis(),
    val providerUid: String = "",
    val customerUid: String = ""
)

@JsonClass(generateAdapter = true)
data class ChatMessageEntity(
    val id: String = "",
    val conversationId: String,
    val senderId: String,
    val senderName: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isFromMe: Boolean,
    val isOfferMessage: Boolean = false,
    val offerPrice: String = "",
    val isVoiceNote: Boolean = false,
    val voiceDurationSeconds: Int = 0,
    val hasPhotoAttachment: Boolean = false,
    val photoDescription: String = "",
    val photoMediaId: String = ""
)

@JsonClass(generateAdapter = true)
data class ConversationEntity(
    val id: String,
    val participantId: String,
    val participantName: String,
    val participantTitle: String,
    val lastMessage: String,
    val lastTimestamp: Long,
    val unreadCount: Int = 0,
    val relatedItemTitle: String = ""
)

@JsonClass(generateAdapter = true)
data class DigitalReceiptEntity(
    val receiptCode: String,
    val requestId: String,
    val quoteId: String,
    val jobTitle: String,
    val customerName: String,
    val providerName: String,
    val providerTitle: String,
    val totalAmount: String,
    val escrowStatus: String, // LOCKED, RELEASED
    val warrantyInfo: String,
    val createdAtDate: String,
    val district: String
)
