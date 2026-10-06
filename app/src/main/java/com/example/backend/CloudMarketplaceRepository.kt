package com.example.backend

import com.example.data.local.JobRequestEntity
import com.example.data.local.QuoteEntity
import com.example.data.local.ServiceProviderEntity
import com.example.data.model.SectorType
import com.example.data.model.UrgencyMode
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class CloudMarketplaceRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private fun requireUid(): String = auth.currentUser?.uid ?: error("Giriş gerekli.")

    fun observeProviders(): Flow<List<ServiceProviderEntity>> = callbackFlow {
        val listener = firestore.collection("providers")
            .limit(200)
            .addSnapshotListener { snapshot, error ->
            if (error != null) { close(error); return@addSnapshotListener }
            val providers = snapshot?.documents.orEmpty().mapNotNull { doc ->
                runCatching { providerFromDocument(doc.id, doc.data.orEmpty()) }.getOrNull()
            }
            trySend(providers)
        }
        awaitClose { listener.remove() }
    }

    fun observeRequests(): Flow<List<JobRequestEntity>> = callbackFlow {
        val listener = firestore.collection("jobRequests")
            .limit(200)
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
        val listener = firestore.collection("quotes")
            .whereEqualTo("customerId", me)
            .addSnapshotListener { snap, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                val list = snap?.documents.orEmpty().map { d ->
                    QuoteEntity(
                        id = d.id.toLongOrNull() ?: d.id.hashCode().toLong().and(0x7fffffffL),
                        requestId = d.getString("requestId")?.toLongOrNull() ?: 0L,
                        providerId = d.getString("providerId").orEmpty(),
                        providerName = "Hizmet Sağlayıcı", providerTitle = "Hizmet",
                        providerRating = 0.0, price = d.getString("price").orEmpty(),
                        amountMinor = (d.get("amountMinor") as? Number)?.toLong() ?: 0L,
                        durationOrArrival = d.getString("durationOrArrival").orEmpty(),
                        notes = d.getString("notes").orEmpty(),
                        status = d.getString("status") ?: "PENDING"
                    )
                }
                trySend(list)
            }
        awaitClose { listener.remove() }
    }
    suspend fun saveProvider(provider: ServiceProviderEntity) {
        val uid = requireUid()
        val ref = firestore.collection("providers").document(provider.id)
        val exists = ref.get().await().exists()

        val data = mutableMapOf<String, Any>(
            "ownerId" to uid,
            "displayName" to provider.name,
            "title" to provider.title,
            "bio" to provider.bio,
            "sector" to provider.sector,
            "categoryId" to provider.categoryId,
            "district" to provider.district,
            "city" to provider.city,
            "experienceYears" to provider.experienceYears,
            "serviceArea" to mapOf(
                "latitude" to provider.latitude,
                "longitude" to provider.longitude
            ),
            "isOpenForOffers" to provider.isOpenForOffers,
            "updatedAt" to com.google.firebase.Timestamp.now()
        )
        if (!exists) {
            data["createdAt"] = com.google.firebase.Timestamp.now()
        }
        ref.set(data, SetOptions.merge()).await()
    }

    suspend fun saveJobRequest(request: JobRequestEntity) {
        val uid = requireUid()
        val id = request.id.toString()
        val publicRef = firestore.collection("jobRequests").document(id)
        val privateRef = firestore.collection("jobRequestPrivate").document(id)
        val batch = firestore.batch()

        batch.set(
            publicRef,
            mapOf(
                "ownerId" to uid,
                "title" to request.title,
                "sector" to request.sector,
                "categoryId" to request.categoryId,
                "district" to request.district,
                "urgencyMode" to request.urgencyMode,
                "eventOrJobDate" to request.eventOrJobDate,
                "eventTime" to request.eventTime,
                "budgetEstimate" to request.budgetEstimate,
                "status" to request.status,
                "createdAt" to request.createdAt,
                "updatedAt" to com.google.firebase.Timestamp.now()
            ),
            SetOptions.merge()
        )

        batch.set(
            privateRef,
            mapOf(
                "ownerId" to uid,
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
                "longitude" to request.longitude,
                "updatedAt" to com.google.firebase.Timestamp.now()
            ),
            SetOptions.merge()
        )

        batch.commit().await()
    }

    suspend fun saveQuote(quote: QuoteEntity, providerOwnerId: String) {
        val uid = requireUid()
        require(uid == providerOwnerId) { "Teklifi yalnızca hizmet veren hesabı oluşturabilir." }
        val requestDoc = firestore.collection("jobRequests").document(quote.requestId.toString()).get().await()
        val customerId = requestDoc.getString("ownerId") ?: error("Talep sahibi bulunamadı.")
        firestore.collection("quotes").document(quote.id.toString()).set(
            mapOf(
                "providerId" to quote.providerId, "providerOwnerId" to uid,
                "customerId" to customerId, "requestId" to quote.requestId.toString(),
                "price" to quote.price, "amountMinor" to quote.amountMinor, "durationOrArrival" to quote.durationOrArrival,
                "notes" to quote.notes, "status" to quote.status, "createdAt" to quote.createdAt
            ), SetOptions.merge()
        ).await()
    }

    private fun providerFromDocument(id: String, d: Map<String, Any?>) = ServiceProviderEntity(
        id=id, name=d["displayName"]?.toString().orEmpty(), title=d["title"]?.toString().orEmpty(),
        sector=d["sector"]?.toString() ?: SectorType.ALL.name, categoryId=d["categoryId"]?.toString().orEmpty(),
        rating=(d["rating"] as? Number)?.toDouble() ?: 0.0, reviewCount=(d["reviewCount"] as? Number)?.toInt() ?: 0,
        experienceYears=(d["experienceYears"] as? Number)?.toInt() ?: 0, district=d["district"]?.toString().orEmpty(),
        city=d["city"]?.toString().orEmpty(), hourlyOrBasePrice=d["hourlyOrBasePrice"]?.toString().orEmpty(),
        isEmergencyAvailable=d["isEmergencyAvailable"] as? Boolean ?: false, verifiedSafeBadge=false, mykCertified=false,
        childSafeCertified=false, phoneVerified=true, daysRemaining=30, isReported=false,
        paintBrandsJson="", charactersOfferedJson="", includedEquipmentsJson="", bookedDatesJson="",
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
        budgetEstimate=d["budgetEstimate"]?.toString().orEmpty(), createdAt=(d["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
    )
}