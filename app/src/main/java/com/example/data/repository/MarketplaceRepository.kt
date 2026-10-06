package com.example.data.repository

import com.example.data.local.AppDao
import com.example.data.local.ChatMessageEntity
import com.example.data.local.ConversationEntity
import com.example.data.local.InitialData
import com.example.data.local.JobRequestEntity
import com.example.data.local.QuoteEntity
import com.example.data.local.ServiceProviderEntity
import com.example.data.model.FeedFlowType
import com.example.data.model.SectorType
import com.example.data.model.UrgencyMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class MarketplaceRepository(private val dao: AppDao) {

    suspend fun checkAndSeedInitialData() {
        if (dao.getProviderCount() == 0) {
            dao.insertProviders(InitialData.getSeedProviders())
            for (req in InitialData.getSeedRequests()) {
                val reqId = dao.insertRequest(req)
                val matchingQuote = InitialData.getSeedQuotes().find { it.requestId == req.id }
                if (matchingQuote != null) {
                    dao.insertQuote(matchingQuote.copy(requestId = reqId))
                }
            }

            for (conv in InitialData.getSeedConversations()) {
                dao.insertConversation(conv)
            }

            for (msg in InitialData.getSeedMessages()) {
                dao.insertMessage(msg)
            }
        }
    }

    fun getFilteredProviders(
        sector: SectorType,
        urgency: UrgencyMode,
        selectedCategory: String?,
        searchQuery: String,
        selectedDistrict: String?
    ): Flow<List<ServiceProviderEntity>> {
        return dao.getAllProviders().map { list ->
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
        return dao.getAllRequests().map { list ->
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

    fun getProviderById(id: String): Flow<ServiceProviderEntity?> = dao.getProviderById(id)

    suspend fun toggleFavorite(id: String, currentFav: Boolean) {
        dao.setFavorite(id, !currentFav)
    }

    suspend fun toggleOpenForOffers(id: String, isOpen: Boolean) {
        dao.setOpenForOffers(id, isOpen)
    }

    suspend fun updateBookedDates(id: String, bookedDatesJson: String) {
        dao.updateBookedDates(id, bookedDatesJson)
    }

    suspend fun reportProvider(id: String) {
        dao.reportProvider(id)
    }

    suspend fun reportJobRequest(id: Long) {
        dao.reportJobRequest(id)
    }

    // --- Flow A: Esnaf Hizmet İlanı Yayınlama ---
    suspend fun publishProviderListing(provider: ServiceProviderEntity) {
        dao.insertProvider(provider)
    }

    // --- Flow B: Hizmet Arayan İhtiyaç İlanı (Armut) ---
    fun getAllRequests(): Flow<List<JobRequestEntity>> = dao.getAllRequests()

    fun getRequestById(id: Long): Flow<JobRequestEntity?> = dao.getRequestById(id)

    suspend fun createJobRequest(request: JobRequestEntity): Long {
        return dao.insertRequest(request)
    }

    suspend fun deleteJobRequest(id: Long) {
        dao.deleteRequest(id)
    }

    // --- Quotes ---
    fun getQuotesForRequest(requestId: Long): Flow<List<QuoteEntity>> =
        dao.getQuotesForRequest(requestId)

    fun getAllQuotes(): Flow<List<QuoteEntity>> = dao.getAllQuotes()

    suspend fun sendQuote(quote: QuoteEntity): Long {
        val quoteId = dao.insertQuote(quote)
        dao.updateRequestStatus(quote.requestId, "QUOTED")
        return quoteId
    }

    suspend fun acceptQuote(requestId: Long, quoteId: Long) {
        dao.updateQuoteStatus(quoteId, "ACCEPTED")
        dao.updateRequestStatus(requestId, "ACCEPTED")
    }

    suspend fun deleteQuote(quoteId: Long) {
        dao.deleteQuote(quoteId)
    }

    suspend fun rejectQuote(quoteId: Long) {
        dao.updateQuoteStatus(quoteId, "REJECTED")
    }

    // --- In-App Chat & Messaging ---
    fun getAllConversations(): Flow<List<ConversationEntity>> = dao.getAllConversations()

    fun getConversationById(id: String): Flow<ConversationEntity?> = dao.getConversationById(id)

    fun getMessagesForConversation(convId: String): Flow<List<ChatMessageEntity>> =
        dao.getMessagesForConversation(convId)

    suspend fun startOrGetConversation(
        participantId: String,
        participantName: String,
        participantTitle: String,
        relatedItemTitle: String
    ): String {
        val convId = "conv-$participantId"
        val existing = dao.getAllConversations()
        val conv = ConversationEntity(
            id = convId,
            participantId = participantId,
            participantName = participantName,
            participantTitle = participantTitle,
            lastMessage = "Sohbet başlatıldı",
            lastTimestamp = System.currentTimeMillis(),
            unreadCount = 0,
            relatedItemTitle = relatedItemTitle
        )
        dao.insertConversation(conv)
        return convId
    }

    suspend fun sendChatMessage(
        conversationId: String,
        senderName: String,
        text: String,
        isFromMe: Boolean,
        isOffer: Boolean = false,
        offerPrice: String = "",
        isVoiceNote: Boolean = false,
        voiceDurationSeconds: Int = 0,
        hasPhotoAttachment: Boolean = false,
        photoDescription: String = ""
    ) {
        val msg = ChatMessageEntity(
            conversationId = conversationId,
            senderId = if (isFromMe) "user" else "provider",
            senderName = senderName,
            text = text,
            timestamp = System.currentTimeMillis(),
            isFromMe = isFromMe,
            isOfferMessage = isOffer,
            offerPrice = offerPrice,
            isVoiceNote = isVoiceNote,
            voiceDurationSeconds = voiceDurationSeconds,
            hasPhotoAttachment = hasPhotoAttachment,
            photoDescription = photoDescription
        )
        dao.insertMessage(msg)
        dao.updateConversationLastMessage(
            id = conversationId,
            lastMsg = when {
                isOffer -> "Fiyat Teklifi: $offerPrice"
                isVoiceNote -> "🎙️ Sesli Mesaj ($voiceDurationSeconds sn)"
                hasPhotoAttachment -> "📷 Fotoğraf: $photoDescription"
                else -> text
            },
            timestamp = System.currentTimeMillis()
        )
    }

    // --- Escrow Havuz Ödeme & Dijital İş Fişi ---
    suspend fun fundEscrowPayment(
        requestId: Long,
        quoteId: Long,
        amount: String,
        jobTitle: String,
        customerName: String,
        providerName: String,
        providerTitle: String,
        district: String
    ): String {
        val receiptCode = "MHL-2026-" + (1000..9999).random()
        dao.updateQuoteStatus(quoteId, "ACCEPTED")
        dao.updateRequestStatus(requestId, "ACCEPTED")
        dao.updateRequestEscrow(requestId, "LOCKED", amount)
        dao.updateQuoteEscrow(quoteId, funded = true, receiptCode = receiptCode)

        val receipt = com.example.data.local.DigitalReceiptEntity(
            receiptCode = receiptCode,
            requestId = requestId,
            quoteId = quoteId,
            jobTitle = jobTitle,
            customerName = customerName,
            providerName = providerName,
            providerTitle = providerTitle,
            totalAmount = amount,
            escrowStatus = "LOCKED",
            warrantyInfo = "2 Yıl İşçilik & Malzeme Mahallemde Güvencesi",
            createdAtDate = "05.10.2026",
            district = district
        )
        dao.insertReceipt(receipt)
        return receiptCode
    }

    suspend fun releaseEscrowPayment(requestId: Long, quoteId: Long, receiptCode: String) {
        dao.updateRequestEscrow(requestId, "RELEASED", "")
        dao.updateRequestStatus(requestId, "COMPLETED")
        val existingReceipt = dao.getReceiptByCodeDirect(receiptCode)
        if (existingReceipt != null) {
            dao.insertReceipt(existingReceipt.copy(escrowStatus = "RELEASED"))
        }
    }

    fun getReceiptByCode(code: String): Flow<com.example.data.local.DigitalReceiptEntity?> =
        dao.getReceiptByCode(code)

    fun getReceiptForRequest(requestId: Long): Flow<com.example.data.local.DigitalReceiptEntity?> =
        dao.getReceiptForRequest(requestId)
}
