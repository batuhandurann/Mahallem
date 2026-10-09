package com.batuhanduran.burada.data.repository

import com.batuhanduran.burada.validation.quoteDraftError

import com.batuhanduran.burada.data.local.*
import com.batuhanduran.burada.data.model.SectorType
import com.batuhanduran.burada.data.model.UrgencyMode
import com.batuhanduran.burada.data.remote.FirebaseServices
import com.batuhanduran.burada.data.remote.AtomicWriteBudget
import com.batuhanduran.burada.data.remote.WriteOperation
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import com.squareup.moshi.Moshi
import com.batuhanduran.burada.BuildConfig
import com.batuhanduran.burada.auth.awaitResult
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** One authenticated account per repository. Room/demo data is never a fallback. */
class MarketplaceRepository(
    private val db: FirebaseFirestore = FirebaseServices.firestore,
    private val auth: FirebaseAuth = FirebaseServices.auth,
    val uid: String = requireNotNull(auth.currentUser).uid
) {
    private val moshi = Moshi.Builder().build()
    private val writeBudget = AtomicWriteBudget(db, uid)
    private val mutableErrors = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val errors: SharedFlow<String> = mutableErrors.asSharedFlow()
    private fun requireAccount() {
        check(auth.currentUser?.uid == uid) { "Oturum değişti. Yeniden giriş yapın." }
    }
    private fun <T> encode(value: T, type: Class<T>): Map<String, Any?> {
        @Suppress("UNCHECKED_CAST")
        return moshi.adapter(type).toJsonValue(value) as Map<String, Any?>
    }
    private fun <T> decode(document: DocumentSnapshot, type: Class<T>): T =
        requireNotNull(moshi.adapter(type).fromJsonValue(document.get("data")))
    private fun envelope(data: Map<String, Any?>, vararg identity: Pair<String, Any>) =
        mapOf("data" to data, "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()) + identity.toMap()
    private fun validateNeighborhood(provinceId: String, districtId: String, neighborhoodId: String) {
        val area = requireNotNull(com.batuhanduran.burada.data.model.PilotNeighborhoodCatalog.findById(neighborhoodId)) { "Bir mahalle seçin." }
        require(area.provinceId == provinceId && area.districtId == districtId)
    }
    private fun <T> observe(query: Query, transform: (DocumentSnapshot) -> T): Flow<List<T>> = callbackFlow {
        // A WhileSubscribed stream can start after its account has already left.
        // End read streams empty; write operations still strictly reject stale UIDs.
        if (auth.currentUser?.uid != uid) { trySend(emptyList()); close(); return@callbackFlow }
        val authListener = FirebaseAuth.AuthStateListener { current ->
            if (current.currentUser?.uid != uid) { trySend(emptyList()); close() }
        }
        auth.addAuthStateListener(authListener)
        val listener = query.addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
            if (auth.currentUser?.uid != uid) { trySend(emptyList()); close() }
            else if (error != null) {
                mutableErrors.tryEmit("Veriler yüklenemedi. Bağlantınızı ve erişim izinlerinizi kontrol edin.")
                trySend(emptyList())
            } else if (snapshot?.metadata?.isFromCache == true) {
                // Cached documents are not authorization evidence for the active UID.
                trySend(emptyList())
            } else try {
                trySend(snapshot?.documents.orEmpty().map(transform))
            } catch (exception: Exception) {
                mutableErrors.tryEmit("Buluttaki veri biçimi okunamadı. Lütfen destek ile iletişime geçin.")
                trySend(emptyList())
            }
        }
        awaitClose { listener.remove(); auth.removeAuthStateListener(authListener) }
    }
    fun getFilteredProviders(
        sector: SectorType,
        urgency: UrgencyMode,
        selectedCategory: String?,
        searchQuery: String,
        selectedDistrict: String?
    ): Flow<List<ServiceProviderEntity>> {
        return getAllProviders().map { list ->
            list.filter { p ->
                val matchesSector = when (sector) {
                    SectorType.ALL -> true
                    else -> p.sector == sector.name
                }

                val matchesUrgency = when (urgency) {
                    UrgencyMode.ALL -> true
                    UrgencyMode.EMERGENCY -> p.isEmergencyAvailable
                    UrgencyMode.PLANNED -> !p.isEmergencyAvailable || p.isOpenForOffers
                }

                val matchesCategory = selectedCategory.isNullOrBlank() || p.categoryId == selectedCategory

                val matchesDistrict = selectedDistrict.isNullOrBlank() ||
                        selectedDistrict == "Tüm İlçeler" ||
                        p.district.contains(selectedDistrict.split("/").first().trim(), ignoreCase = true)

                val matchesQuery = searchQuery.isBlank() ||
                        p.name.contains(searchQuery, ignoreCase = true) ||
                        p.title.contains(searchQuery, ignoreCase = true) ||
                        p.bio.contains(searchQuery, ignoreCase = true) ||
                        p.paintBrandsJson.contains(searchQuery, ignoreCase = true) ||
                        p.charactersOfferedJson.contains(searchQuery, ignoreCase = true)

                matchesSector && matchesUrgency && matchesCategory && matchesDistrict && matchesQuery
            }
        }
    }

    fun getFilteredRequests(
        sector: SectorType,
        urgency: UrgencyMode,
        selectedCategory: String?,
        searchQuery: String,
        selectedDistrict: String?
    ): Flow<List<JobRequestEntity>> {
        return getAllRequests().map { list ->
            list.filter { req ->
                val matchesSector = when (sector) {
                    SectorType.ALL -> true
                    else -> req.sector == sector.name
                }

                val matchesUrgency = when (urgency) {
                    UrgencyMode.ALL -> true
                    UrgencyMode.EMERGENCY -> req.urgencyMode == "EMERGENCY"
                    UrgencyMode.PLANNED -> req.urgencyMode == "PLANNED"
                }

                val matchesCategory = selectedCategory.isNullOrBlank() || req.categoryId == selectedCategory

                val matchesDistrict = selectedDistrict.isNullOrBlank() ||
                        selectedDistrict == "Tüm İlçeler" ||
                        req.district.contains(selectedDistrict.split("/").first().trim(), ignoreCase = true)

                val matchesQuery = searchQuery.isBlank() ||
                        req.title.contains(searchQuery, ignoreCase = true) ||
                        req.renovationNotes.contains(searchQuery, ignoreCase = true) ||
                        req.extraServicesRequested.contains(searchQuery, ignoreCase = true) ||
                        req.selectedCostumeOrCharacter.contains(searchQuery, ignoreCase = true)

                matchesSector && matchesUrgency && matchesCategory && matchesDistrict && matchesQuery
            }
        }
    }

    // A verified phone is computed from current server Auth + the private listing contact.
    // Strip all client-supplied trust claims, including certificates without a review workflow.
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun <T> withPhoneTrust(source: Flow<List<T>>, kind: String,
        id: (T) -> String, apply: (T, Boolean) -> T): Flow<List<T>> = source.transformLatest { rows ->
        if (auth.currentUser?.uid != uid) { emit(emptyList()); return@transformLatest }
        val unverified = rows.map { apply(it, false) }
        emit(unverified)
        if (rows.isEmpty()) return@transformLatest
        val functions = FirebaseFunctions.getInstance(FirebaseServices.app, "europe-west3").apply {
            if (BuildConfig.USE_FIREBASE_EMULATORS) useEmulator(BuildConfig.EMULATOR_HOST, 5001)
        }
        while (true) {
            if (auth.currentUser?.uid != uid) { emit(emptyList()); return@transformLatest }
            emit(unverified)
            try {
                requireAccount()
                val flags = mutableMapOf<String, Boolean>()
                for (batch in rows.map(id).distinct().chunked(50)) {
                    val response = withTimeout(15_000) {
                        functions.getHttpsCallable("getListingTrust")
                            .call(mapOf("kind" to kind, "ids" to batch)).awaitResult().data
                    } as? Map<*, *> ?: error("Invalid trust response")
                    requireAccount()
                    val results = response["listings"] as? Map<*, *> ?: error("Invalid trust evidence")
                    for (listingId in batch) flags[listingId] =
                        (results[listingId] as? Map<*, *>)?.get("phoneVerified") == true
                }
                emit(rows.map { apply(it, flags[id(it)] == true) })
            } catch (error: kotlinx.coroutines.TimeoutCancellationException) {
                emit(unverified)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (auth.currentUser?.uid != uid) { emit(emptyList()); return@transformLatest }
                emit(unverified)
            }
            // Revoked/disabled accounts lose evidence on the next refresh (maximum 60 seconds).
            delay(60_000)
        }
    }
    private fun providerTrust(source: Flow<List<ServiceProviderEntity>>) =
        withPhoneTrust(source, "providers", { it.id }) { row, verified ->
            row.copy(phoneVerified = verified, verifiedSafeBadge = false, mykCertified = false, childSafeCertified = false)
        }
    private fun requestTrust(source: Flow<List<JobRequestEntity>>) =
        withPhoneTrust(source, "requests", { it.id }) { row, verified -> row.copy(phoneVerified = verified) }

    fun getAllProviders(): Flow<List<ServiceProviderEntity>> = providerTrust(combine(
        observe(db.collection("providers").whereEqualTo("visibility", "published")) {
            decode(it, ServiceProviderEntity::class.java).copy(visibility = it.getString("visibility") ?: "hidden")
        },
        observe(db.collection("users").document(uid).collection("favorites")) { it.id }
    ) { providers, favorites -> providers.map { it.copy(isFavorite = it.id in favorites) } })
    fun getOwnedProviders(): Flow<List<ServiceProviderEntity>> = providerTrust(
        observe(db.collection("providers").whereEqualTo("ownerUid", uid)) {
            decode(it, ServiceProviderEntity::class.java).copy(visibility = it.getString("visibility") ?: "hidden")
        }.map { profiles -> ownedProviderProfiles(profiles, uid) })

    fun getAllRequests(): Flow<List<JobRequestEntity>> = requestTrust(observe(db.collection("requests").whereEqualTo("visibility", "published")) {
        decode(it, JobRequestEntity::class.java).copy(createdAt = it.getTimestamp("createdAt")?.toDate()?.time ?: 0,
            visibility = it.getString("visibility") ?: "hidden")
    })
    fun getMyRequests(): Flow<List<JobRequestEntity>> = requestTrust(observe(db.collection("requests").whereEqualTo("ownerUid", uid)) {
        decode(it, JobRequestEntity::class.java).copy(createdAt = it.getTimestamp("createdAt")?.toDate()?.time ?: 0,
            visibility = it.getString("visibility") ?: "hidden")
    })
    fun getProviderById(id: String) = getAllProviders().map { list -> list.find { it.id == id } }
    fun getRequestById(id: String) = getAllRequests().map { list -> list.find { it.id == id } }
    suspend fun publishProviderListing(provider: ServiceProviderEntity) {
        requireAccount()
        validateNeighborhood(provider.provinceId, provider.districtId, provider.neighborhoodId)
        val ref = db.collection("providers").document()
        val public = provider.copy(id = ref.id, ownerUid = uid, phone = "", rating = 0.0, reviewCount = 0,
            verifiedSafeBadge = false, mykCertified = false, childSafeCertified = false, phoneVerified = false, isFavorite = false)
        db.runTransaction { tx ->
            requireAccount()
            val budget = writeBudget.plan(tx, WriteOperation.LISTING, ref)
            budget.applyTo(tx)
            tx.set(ref, envelope(encode(public, ServiceProviderEntity::class.java), "ownerUid" to uid, "visibility" to "published"))
            tx.set(db.collection("providerContacts").document(ref.id), mapOf("ownerUid" to uid,
                "phone" to provider.phone, "updatedAt" to FieldValue.serverTimestamp()))
        }.awaitRemote()
    }
    suspend fun createJobRequest(request: JobRequestEntity): String {
        requireAccount()
        com.batuhanduran.burada.validation.RequestSchedules.requireValid(
            request.title, request.eventOrJobDate, request.eventTime
        )
        validateNeighborhood(request.provinceId, request.districtId, request.neighborhoodId)
        val ref = db.collection("requests").document()
        val public = request.copy(id = ref.id, title = request.title.trim(), ownerUid = uid, address = "", customerPhone = "",
            phoneVerified = false, status = "PENDING", escrowStatus = "NONE", escrowAmount = "")
        db.runTransaction { tx ->
            requireAccount()
            val budget = writeBudget.plan(tx, WriteOperation.LISTING, ref)
            budget.applyTo(tx)
            tx.set(ref, envelope(encode(public, JobRequestEntity::class.java), "ownerUid" to uid,
                "acceptedQuoteId" to "", "acceptedProviderUid" to "", "visibility" to "published"))
            tx.set(db.collection("requestContacts").document(ref.id), mapOf("ownerUid" to uid,
                "address" to request.address, "phone" to request.customerPhone, "updatedAt" to FieldValue.serverTimestamp()))
        }.awaitRemote()
        return ref.id
    }
    suspend fun toggleFavorite(id: String, currentFav: Boolean) {
        requireAccount()
        val ref = db.collection("users").document(uid).collection("favorites").document(id)
        if (currentFav) ref.delete().awaitRemote()
        else ref.set(mapOf("createdAt" to FieldValue.serverTimestamp())).awaitRemote()
    }
    private suspend fun updateProvider(id: String, field: String, value: Any) {
        requireAccount()
        db.collection("providers").document(id).update(mapOf("data.$field" to value,
            "updatedAt" to FieldValue.serverTimestamp())).awaitRemote()
    }
    suspend fun toggleOpenForOffers(id: String, isOpen: Boolean) = updateProvider(id, "isOpenForOffers", isOpen)
    suspend fun updateBookedDates(id: String, bookedDatesJson: String) = updateProvider(id, "bookedDatesJson", bookedDatesJson)
    fun getAllQuotes(): Flow<List<QuoteEntity>> = observe(db.collection("quotes").where(
        Filter.or(Filter.equalTo("customerUid", uid), Filter.equalTo("providerUid", uid))
    )) { decode(it, QuoteEntity::class.java).copy(status = it.getString("status") ?: "PENDING") }
    fun getQuotesForRequest(requestId: String) = getAllQuotes().map { list -> list.filter { it.requestId == requestId } }
    suspend fun sendQuote(quote: QuoteEntity): String {
        requireAccount()
        val validationError = quoteDraftError(quote.price, quote.durationOrArrival, quote.notes)
        require(validationError == null) { validationError ?: "Teklif bilgileri geçersiz." }
        val request = db.collection("requests").document(quote.requestId).get(Source.SERVER).awaitRemote()
        val customerUid = requireNotNull(request.getString("ownerUid"))
        check(customerUid != uid) { "Kendi ilanınıza teklif veremezsiniz." }
        val ref = db.collection("quotes").document("${quote.requestId}_$uid")
        val data = quote.copy(id = ref.id, providerUid = uid, customerUid = customerUid,
            status = "PENDING", escrowFunded = false, receiptCode = "", warrantyDuration = "")
        db.runTransaction { tx ->
            requireAccount()
            val budget = writeBudget.plan(tx, WriteOperation.QUOTE, ref)
            budget.applyTo(tx)
            tx.set(ref, envelope(encode(data, QuoteEntity::class.java), "providerUid" to uid,
                "customerUid" to customerUid, "requestId" to quote.requestId, "status" to "PENDING"))
        }.awaitRemote()
        return ref.id
    }
    suspend fun acceptQuote(requestId: String, quoteId: String) {
        requireAccount()
        val reqRef = db.collection("requests").document(requestId)
        val quoteRef = db.collection("quotes").document(quoteId)
        db.runTransaction { tx ->
            requireAccount()
            val request = tx.get(reqRef)
            val quote = tx.get(quoteRef)
            check(request.getString("ownerUid") == uid && quote.getString("customerUid") == uid)
            check(quote.getString("requestId") == requestId && quote.getString("status") == "PENDING")
            check(request.getString("acceptedQuoteId") == "") { "Bu ilan için zaten teklif kabul edildi." }
            tx.update(quoteRef, mapOf("status" to "ACCEPTED", "updatedAt" to FieldValue.serverTimestamp()))
            tx.update(reqRef, mapOf("data.status" to "ACCEPTED", "acceptedQuoteId" to quoteId,
                "acceptedProviderUid" to requireNotNull(quote.getString("providerUid")), "updatedAt" to FieldValue.serverTimestamp()))
        }.awaitRemote()
    }
    suspend fun rejectQuote(quoteId: String) {
        requireAccount()
        db.collection("quotes").document(quoteId).update(mapOf("status" to "REJECTED",
            "updatedAt" to FieldValue.serverTimestamp())).awaitRemote()
    }
    /** A provider may withdraw ONLY their own pending offer; Firestore rules enforce identity/status. */
    suspend fun withdrawQuote(quoteId: String) {
        requireAccount()
        db.collection("quotes").document(quoteId).update(mapOf("status" to "WITHDRAWN",
            "updatedAt" to FieldValue.serverTimestamp())).awaitRemote()
    }

    fun getAllConversations(): Flow<List<ConversationEntity>> = observe(
        db.collection("conversations").whereArrayContains("participantUids", uid)
    ) { doc ->
        val participants = doc.get("participantUids") as List<*>
        val other = participants.filterIsInstance<String>().single { it != uid }
        val names = doc.get("names") as Map<*, *>
        ConversationEntity(doc.id, other, names[other] as? String ?: "Mahalle Sakini", "",
            doc.getString("lastMessage") ?: "", doc.getTimestamp("updatedAt")?.toDate()?.time ?: 0,
            relatedItemTitle = doc.getString("relatedItemTitle") ?: "")
    }
    fun getConversationById(id: String) = getAllConversations().map { list -> list.find { it.id == id } }
    suspend fun startOrGetConversation(participantId: String, participantName: String,
        participantTitle: String, relatedItemTitle: String): String {
        requireAccount()
        require(participantId.isNotBlank() && participantId != uid) { "Sohbet için başka bir kullanıcı seçin." }
        val participants = listOf(uid, participantId).sorted()
        val id = participants.joinToString("") { "${it.length}:$it" }
        val ref = db.collection("conversations").document(id)
        db.runTransaction { tx ->
            requireAccount()
            if (!tx.get(ref).exists()) {
                val budget = writeBudget.plan(tx, WriteOperation.CONVERSATION, ref)
                budget.applyTo(tx)
                tx.set(ref, mapOf("participantUids" to participants,
                    "names" to mapOf(uid to (auth.currentUser?.displayName ?: "Mahalle Sakini"), participantId to participantName),
                    "relatedItemTitle" to relatedItemTitle, "lastMessage" to "",
                    "createdAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp()))
            }
        }.awaitRemote()
        return id
    }
    fun getMessagesForConversation(convId: String): Flow<List<ChatMessageEntity>> = observe(
        db.collection("conversations").document(convId).collection("messages").orderBy("createdAt")
    ) { doc -> decode(doc, ChatMessageEntity::class.java).copy(isFromMe = doc.getString("senderUid") == uid,
        timestamp = doc.getTimestamp("createdAt")?.toDate()?.time ?: 0) }
    suspend fun sendChatMessage(conversationId: String, senderName: String, text: String,
        isFromMe: Boolean, isOffer: Boolean = false, offerPrice: String = "",
        isVoiceNote: Boolean = false, voiceDurationSeconds: Int = 0,
        hasPhotoAttachment: Boolean = false, photoDescription: String = "", photoMediaId: String = "") {
        requireAccount()
        require(isFromMe && !isVoiceNote)
        require(!hasPhotoAttachment || photoMediaId.isNotBlank())
        require(text.isNotBlank() && text.length <= 4000) { "Mesaj 1–4000 karakter olmalı." }
        val conv = db.collection("conversations").document(conversationId)
        val ref = conv.collection("messages").document()
        val data = ChatMessageEntity(id = ref.id, conversationId = conversationId, senderId = uid,
            senderName = auth.currentUser?.displayName ?: "Mahalle Sakini", text = text.trim(),
            isFromMe = false, isOfferMessage = isOffer, offerPrice = offerPrice,
            hasPhotoAttachment = hasPhotoAttachment, photoDescription = photoDescription, photoMediaId = photoMediaId)
        db.runTransaction { tx ->
            requireAccount()
            val budget = writeBudget.plan(tx, WriteOperation.MESSAGE, ref)
            budget.applyTo(tx)
            tx.set(ref, envelope(encode(data, ChatMessageEntity::class.java), "senderUid" to uid))
            tx.update(conv, mapOf("lastMessage" to text.trim(), "updatedAt" to FieldValue.serverTimestamp()))
        }.awaitRemote()
    }
    // Only a verified payment backend may acknowledge funds or generate receipts.
    suspend fun fundEscrowPayment(requestId: String, quoteId: String, amount: String,
        jobTitle: String, customerName: String, providerName: String, providerTitle: String, district: String): String =
        error("Ödeme altyapısı henüz bağlı değil. Para bloke edilmedi.")
    suspend fun releaseEscrowPayment(requestId: String, quoteId: String, receiptCode: String): Unit =
        error("Ödeme altyapısı henüz bağlı değil. Para transferi yapılamaz.")
}
private suspend fun <T> Task<T>.awaitRemote(): T = withTimeout(30_000) {
    suspendCancellableCoroutine { continuation ->
        addOnCompleteListener { task ->
            if (!continuation.isActive) return@addOnCompleteListener
            when {
                task.isCanceled -> continuation.cancel()
                task.isSuccessful -> continuation.resume(task.result)
                else -> continuation.resumeWithException(task.exception ?: IllegalStateException("İşlem başarısız."))
            }
        }
    }
}
