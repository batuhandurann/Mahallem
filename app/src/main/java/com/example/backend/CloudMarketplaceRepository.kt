package com.example.backend

import com.example.data.local.JobRequestEntity
import com.example.data.local.QuoteEntity
import com.example.data.local.ServiceProviderEntity
import com.example.data.model.SectorType
import com.example.data.model.UrgencyMode
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

data class ProviderReview(
    val id: String,
    val rating: Int,
    val comment: String,
    val createdAt: Long,
    val verifiedTransaction: Boolean
)

class CloudMarketplaceRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private fun requireUid(): String = auth.currentUser?.uid ?: error("Giriş gerekli.")

    fun observeProviderReviews(providerId: String): Flow<List<ProviderReview>> = callbackFlow {
        requireUid()
        val listener = firestore.collection("reviews")
            .whereEqualTo("providerId", providerId)
            .limit(50)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                val reviews = snapshot?.documents.orEmpty().mapNotNull { doc ->
                    val rating = (doc.get("rating") as? Number)?.toInt() ?: return@mapNotNull null
                    ProviderReview(
                        id = doc.id,
                        rating = rating.coerceIn(1, 5),
                        comment = doc.getString("comment").orEmpty(),
                        createdAt = doc.getTimestamp("createdAt")?.toDate()?.time ?: 0L,
                        verifiedTransaction = doc.getBoolean("verifiedTransaction") == true
                    )
                }.sortedByDescending { it.createdAt }
                trySend(reviews)
            }
        awaitClose { listener.remove() }
    }

    fun observeFavoriteProviderIds(): Flow<Set<String>> = callbackFlow {
        val me = requireUid()
        val listener = firestore.collection("users").document(me)
            .collection("favorites")
            .limit(200)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                trySend(snapshot?.documents.orEmpty().map { it.id }.toSet())
            }
        awaitClose { listener.remove() }
    }

    suspend fun setFavorite(providerId: String, favorite: Boolean) {
        requireUid()
        FunctionsRepository().setFavorite(providerId, favorite)
    }

    fun observeProviders(
        categoryId: String? = null,
        sector: String? = null,
        district: String? = null,
        emergencyOnly: Boolean = false
    ): Flow<List<ServiceProviderEntity>> = callbackFlow {
        var query: Query = firestore.collection("publicProviders")
        if (!categoryId.isNullOrBlank()) query = query.whereEqualTo("categoryId", categoryId)
        if (!sector.isNullOrBlank()) query = query.whereEqualTo("sector", sector)
        if (emergencyOnly) query = query.whereEqualTo("isEmergencyAvailable", true)
        if (!district.isNullOrBlank()) query = query.whereEqualTo("district", district)
        if (categoryId.isNullOrBlank() && sector.isNullOrBlank() && !emergencyOnly && district.isNullOrBlank()) {
            query = query.orderBy("updatedAt", Query.Direction.DESCENDING)
        }
        val listener = query.limit(50).addSnapshotListener { snapshot, error ->
            if (error != null) { close(error); return@addSnapshotListener }
            val providers = snapshot?.documents.orEmpty().mapNotNull { doc ->
                runCatching { providerFromDocument(doc.id, doc.data.orEmpty()) }.getOrNull()
            }
            trySend(providers)
        }
        awaitClose { listener.remove() }
    }

    fun observeRequests(
        categoryId: String? = null,
        sector: String? = null,
        district: String? = null,
        urgency: String? = null
    ): Flow<List<JobRequestEntity>> = callbackFlow {
        var query: Query = firestore.collection("publicJobRequests")
        if (!categoryId.isNullOrBlank()) query = query.whereEqualTo("categoryId", categoryId)
        if (!sector.isNullOrBlank()) query = query.whereEqualTo("sector", sector)
        if (urgency == "EMERGENCY" || urgency == "PLANNED") {
            query = query.whereEqualTo("urgencyMode", urgency)
        }
        if (!district.isNullOrBlank()) query = query.whereEqualTo("district", district)
        if (categoryId.isNullOrBlank() && sector.isNullOrBlank() && urgency.isNullOrBlank() && district.isNullOrBlank()) {
            query = query.orderBy("updatedAt", Query.Direction.DESCENDING)
        }
        val listener = query.limit(50).addSnapshotListener { snapshot, error ->
            if (error != null) { close(error); return@addSnapshotListener }
            val requests = snapshot?.documents.orEmpty().mapNotNull { doc ->
                runCatching { requestFromDocument(doc.id, doc.data.orEmpty()) }.getOrNull()
            }
            trySend(requests)
        }
        awaitClose { listener.remove() }
    }

    fun observeMyRequests(): Flow<List<JobRequestEntity>> = callbackFlow {
        val me = requireUid()
        val listener = firestore.collection("jobRequests")
            .whereEqualTo("ownerId", me)
            .limit(50)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                val requests = snapshot?.documents.orEmpty().mapNotNull { doc ->
                    runCatching { requestFromDocument(doc.id, doc.data.orEmpty()) }.getOrNull()
                }
                trySend(requests)
            }
        awaitClose { listener.remove() }
    }

    fun observeQuotes(): kotlinx.coroutines.flow.Flow<List<QuoteEntity>> = kotlinx.coroutines.flow.callbackFlow {
        val me = requireUid()
        var customerQuotes = emptyList<QuoteEntity>()
        var providerQuotes = emptyList<QuoteEntity>()

        fun emitCombined() {
            trySend(
                (customerQuotes + providerQuotes)
                    .associateBy { it.id }
                    .values
                    .sortedByDescending { it.createdAt }
            )
        }

        fun mapSnapshot(snapshot: com.google.firebase.firestore.QuerySnapshot?): List<QuoteEntity> =
            snapshot?.documents.orEmpty().map { d ->
                QuoteEntity(
                    id = d.id.toLongOrNull() ?: d.id.hashCode().toLong().and(0x7fffffffL),
                    requestId = d.getString("requestId")?.toLongOrNull() ?: 0L,
                    providerId = d.getString("providerId").orEmpty(),
                    providerName = d.getString("providerName") ?: "Hizmet Sağlayıcı",
                    providerTitle = d.getString("providerTitle") ?: "Hizmet",
                    providerRating = (d.get("providerRating") as? Number)?.toDouble() ?: 0.0,
                    price = d.getString("price").orEmpty(),
                    amountMinor = (d.get("amountMinor") as? Number)?.toLong() ?: 0L,
                    durationOrArrival = d.getString("durationOrArrival").orEmpty(),
                    notes = d.getString("notes").orEmpty(),
                    status = d.getString("status") ?: "PENDING",
                    createdAt = d.getTimestamp("createdAt")?.toDate()?.time ?: 0L
                )
            }

        val customerListener = firestore.collection("quotes")
            .whereEqualTo("customerId", me)
            .limit(100)
            .addSnapshotListener { snap, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                customerQuotes = mapSnapshot(snap)
                emitCombined()
            }

        val providerListener = firestore.collection("quotes")
            .whereEqualTo("providerOwnerId", me)
            .limit(100)
            .addSnapshotListener { snap, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                providerQuotes = mapSnapshot(snap)
                emitCombined()
            }

        awaitClose {
            customerListener.remove()
            providerListener.remove()
        }
    }
    suspend fun saveProvider(provider: ServiceProviderEntity) {
        requireUid()
        FunctionsRepository().saveProviderListing(
            mapOf(
                "providerId" to provider.id,
                "displayName" to provider.name,
                "title" to provider.title,
                "bio" to provider.bio,
                "sector" to provider.sector,
                "categoryId" to provider.categoryId,
                "district" to provider.district,
                "city" to provider.city,
                "hourlyOrBasePrice" to provider.hourlyOrBasePrice,
                "isEmergencyAvailable" to provider.isEmergencyAvailable,
                "experienceYears" to provider.experienceYears,
                "latitude" to provider.latitude,
                "longitude" to provider.longitude,
                "isOpenForOffers" to provider.isOpenForOffers
            )
        )
    }

    suspend fun saveJobRequest(request: JobRequestEntity) {
        requireUid()
        com.example.validation.RequestSchedules.requireValid(
            request.title, request.eventOrJobDate, request.eventTime
        )
        FunctionsRepository().saveJobRequest(
            mapOf(
                "requestId" to request.id.toString(),
                "title" to request.title.trim(),
                "sector" to request.sector,
                "categoryId" to request.categoryId,
                "district" to request.district,
                "urgencyMode" to request.urgencyMode,
                "eventOrJobDate" to request.eventOrJobDate,
                "eventTime" to request.eventTime,
                "budgetEstimate" to request.budgetEstimate,
                "address" to request.address,
                "customerName" to request.customerName,
                "customerPhone" to request.customerPhone,
                "phoneVerified" to request.phoneVerified,
                "areaSquareMeters" to request.areaSquareMeters,
                "roomCount" to request.roomCount,
                "isFurnished" to request.isFurnished,
                "materialsIncluded" to request.materialsIncluded,
                "renovationNotes" to request.renovationNotes,
                "eventType" to request.eventType,
                "durationHours" to request.durationHours,
                "targetAgeGroup" to request.targetAgeGroup,
                "selectedCostumeOrCharacter" to request.selectedCostumeOrCharacter,
                "extraServicesRequested" to request.extraServicesRequested,
                "latitude" to request.latitude,
                "longitude" to request.longitude
            )
        )
    }

    suspend fun saveQuote(quote: QuoteEntity) {
        requireUid()
        FunctionsRepository().createQuote(
            quoteId = quote.id,
            requestId = quote.requestId,
            providerId = quote.providerId,
            price = quote.price,
            amountMinor = quote.amountMinor,
            durationOrArrival = quote.durationOrArrival,
            notes = quote.notes
        )
    }

    private fun providerFromDocument(id: String, d: Map<String, Any?>) = ServiceProviderEntity(
        id=id, name=d["displayName"]?.toString().orEmpty(), title=d["title"]?.toString().orEmpty(),
        sector=d["sector"]?.toString() ?: SectorType.ALL.name, categoryId=d["categoryId"]?.toString().orEmpty(),
        rating=(d["rating"] as? Number)?.toDouble() ?: 0.0, reviewCount=(d["reviewCount"] as? Number)?.toInt() ?: 0,
        experienceYears=(d["experienceYears"] as? Number)?.toInt() ?: 0, district=d["district"]?.toString().orEmpty(),
        city=d["city"]?.toString().orEmpty(), hourlyOrBasePrice=d["hourlyOrBasePrice"]?.toString().orEmpty(),
        isEmergencyAvailable=d["isEmergencyAvailable"] as? Boolean ?: false, verifiedSafeBadge=false, mykCertified=false,
        childSafeCertified=false, phoneVerified=d["phoneVerified"] as? Boolean ?: false, daysRemaining=30, isReported=false,
        paintBrandsJson=d["paintBrandsJson"]?.toString().orEmpty(),
        charactersOfferedJson=d["charactersOfferedJson"]?.toString().orEmpty(),
        includedEquipmentsJson=d["includedEquipmentsJson"]?.toString().orEmpty(),
        bookedDatesJson=((d["bookedDates"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()).let { dates ->
            "[" + dates.joinToString(",") { "\"$it\"" } + "]"
        },
        isOpenForOffers=d["isOpenForOffers"] as? Boolean ?: true, phone="", bio=d["bio"]?.toString().orEmpty(),
        beforeAfterJson="", videoShowcasesJson="", isFavorite=false,
        latitude=((d["serviceArea"] as? Map<*, *>)?.get("latitude") as? Number)?.toDouble() ?: 0.0,
        longitude=((d["serviceArea"] as? Map<*, *>)?.get("longitude") as? Number)?.toDouble() ?: 0.0
    )

    private fun requestFromDocument(id: String, d: Map<String, Any?>) = JobRequestEntity(
        id=id.toLongOrNull() ?: 0L, title=d["title"]?.toString().orEmpty(), sector=d["sector"]?.toString() ?: SectorType.ALL.name,
        categoryId=d["categoryId"]?.toString().orEmpty(), district=d["district"]?.toString().orEmpty(),
        urgencyMode=d["urgencyMode"]?.toString() ?: UrgencyMode.PLANNED.name,
        eventOrJobDate=d["eventOrJobDate"]?.toString().orEmpty(), eventTime=d["eventTime"]?.toString().orEmpty(),
        address="", status=d["status"]?.toString() ?: "PENDING", customerName="", customerPhone="",
        phoneVerified=false, daysRemaining=7, isReported=false, escrowStatus="NONE", escrowAmount="",
        budgetEstimate=d["budgetEstimate"]?.toString().orEmpty(), createdAt=(d["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
        latitude=((d["serviceArea"] as? Map<*, *>)?.get("latitude") as? Number)?.toDouble() ?: 0.0,
        longitude=((d["serviceArea"] as? Map<*, *>)?.get("longitude") as? Number)?.toDouble() ?: 0.0
    )
}
