package com.example.data.repository

import com.example.data.local.AppDao
import com.example.data.local.ChatMessageEntity
import com.example.data.local.ConversationEntity
import com.example.data.local.DigitalReceiptEntity
import com.example.data.local.JobRequestEntity
import com.example.data.local.QuoteEntity
import com.example.data.local.ServiceProviderEntity
import com.example.data.model.SectorType
import com.example.data.model.UrgencyMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketplaceRepositoryTest {

    @Test
    fun providerFiltering_matchesSectorDistrictQueryAndUrgency() = runBlocking {
        val providers = listOf(
            provider("p1", "İbrahim Usta", "Dekoratif Boya", "HOME_REPAIR", "boyaci", "Kadıköy / Moda", emergency = false),
            provider("p2", "Selim Usta", "Acil Tesisatçı", "HOME_REPAIR", "tesisatci", "Kadıköy / Moda", emergency = true),
            provider("p3", "Ayşe Hanım", "Ev Temizliği", "CLEANING", "ev_temizligi", "Bornova / Özkanlar", emergency = false)
        )
        val repo = MarketplaceRepository(FakeDao(providers = providers))

        val result = repo.getFilteredProviders(
            SectorType.HOME_REPAIR,
            UrgencyMode.EMERGENCY,
            null,
            "tesisat",
            "Kadıköy / Moda"
        ).first()

        assertEquals(listOf("p2"), result.map { it.id })
    }

    @Test
    fun requestFiltering_matchesEmergencyAndCategory() = runBlocking {
        val requests = listOf(
            request(1, "Acil Su Kaçağı", "HOME_REPAIR", "tesisatci", "Kadıköy / Moda", "EMERGENCY"),
            request(2, "Ev Temizliği", "CLEANING", "ev_temizligi", "Bornova / Özkanlar", "PLANNED")
        )
        val repo = MarketplaceRepository(FakeDao(requests = requests))

        val result = repo.getFilteredRequests(
            SectorType.HOME_REPAIR,
            UrgencyMode.EMERGENCY,
            "tesisatci",
            "",
            "Kadıköy"
        ).first()

        assertEquals(listOf(1L), result.map { it.id })
    }

    @Test
    fun favoriteToggle_invertsCurrentState() = runBlocking {
        val dao = FakeDao(providers = listOf(provider("p1", "Usta", "Boyacı", "HOME_REPAIR", "boyaci", "Kadıköy")))
        val repo = MarketplaceRepository(dao)

        repo.toggleFavorite("p1", currentFav = false)

        assertTrue(dao.favoriteUpdates.single())
    }

    @Test
    fun releaseEscrow_marksReceiptReleased() = runBlocking {
        val receipt = DigitalReceiptEntity(
            receiptCode = "MHL-2026-1234", requestId = 42, quoteId = 7,
            jobTitle = "Test iş", customerName = "Test müşteri",
            providerName = "Test usta", providerTitle = "Usta",
            totalAmount = "1.000 ₺", escrowStatus = "LOCKED",
            warrantyInfo = "2 Yıl", createdAtDate = "06.10.2026", district = "Kadıköy"
        )
        val dao = FakeDao(receipt = receipt)
        val repo = MarketplaceRepository(dao)

        repo.releaseEscrowPayment(42, 7, receipt.receiptCode)

        assertEquals("COMPLETED", dao.requestStatuses[42L])
        assertEquals("RELEASED", dao.receipt?.escrowStatus)
    }

    @Test
    fun sendQuote_insertsQuoteAndMovesRequestToQuoted() = runBlocking {
        val dao = FakeDao()
        val repo = MarketplaceRepository(dao)

        val quoteId = repo.sendQuote(
            QuoteEntity(
                requestId = 42,
                providerId = "p1",
                providerName = "Usta",
                providerTitle = "Boyacı",
                providerRating = 4.9,
                price = "1.000 ₺",
                durationOrArrival = "Bugün",
                notes = "Test"
            )
        )

        assertEquals(1L, quoteId)
        assertEquals("QUOTED", dao.requestStatuses[42L])
        assertEquals("1.000 ₺", dao.insertedQuotes.single().price)
    }

    private fun provider(
        id: String,
        name: String,
        title: String,
        sector: String,
        category: String,
        district: String,
        emergency: Boolean = false
    ) = ServiceProviderEntity(
        id = id, name = name, title = title, sector = sector, categoryId = category,
        rating = 4.9, reviewCount = 10, experienceYears = 5, district = district,
        city = "İstanbul", hourlyOrBasePrice = "1000 ₺", isEmergencyAvailable = emergency,
        verifiedSafeBadge = true, mykCertified = true, childSafeCertified = false,
        phone = "05000000000", bio = title
    )

    private fun request(
        id: Long,
        title: String,
        sector: String,
        category: String,
        district: String,
        urgency: String
    ) = JobRequestEntity(
        id = id, title = title, sector = sector, categoryId = category, district = district,
        urgencyMode = urgency, eventOrJobDate = "2026-10-10", eventTime = "10:00",
        address = "Test adres", status = "PENDING", customerName = "Test", customerPhone = "05000000000"
    )

    private class FakeDao(
        providers: List<ServiceProviderEntity> = emptyList(),
        requests: List<JobRequestEntity> = emptyList()
    ) : AppDao {
        private var storedReceipt: DigitalReceiptEntity? = receipt
        private val providersFlow = MutableStateFlow(providers)
        private val requestsFlow = MutableStateFlow(requests)
        val favoriteUpdates = mutableListOf<Boolean>()
        val requestStatuses = mutableMapOf<Long, String>()
        val insertedQuotes = mutableListOf<QuoteEntity>()
        var receipt: DigitalReceiptEntity? = receipt

        override fun getAllProviders(): Flow<List<ServiceProviderEntity>> = providersFlow
        override fun getProvidersBySector(sector: String): Flow<List<ServiceProviderEntity>> = flowOf(emptyList())
        override fun getProviderById(id: String): Flow<ServiceProviderEntity?> = flowOf(providersFlow.value.find { it.id == id })
        override suspend fun getProviderByIdDirect(id: String): ServiceProviderEntity? = providersFlow.value.find { it.id == id }
        override suspend fun insertProviders(providers: List<ServiceProviderEntity>) = Unit
        override suspend fun insertProvider(provider: ServiceProviderEntity) = Unit
        override suspend fun updateProvider(provider: ServiceProviderEntity) = Unit
        override suspend fun setFavorite(id: String, isFav: Boolean) { favoriteUpdates += isFav }
        override suspend fun setOpenForOffers(id: String, isOpen: Boolean) = Unit
        override suspend fun updateBookedDates(id: String, bookedDatesJson: String) = Unit
        override suspend fun reportProvider(id: String) = Unit
        override suspend fun getProviderCount(): Int = providersFlow.value.size

        override fun getAllRequests(): Flow<List<JobRequestEntity>> = requestsFlow
        override fun getRequestById(id: Long): Flow<JobRequestEntity?> = flowOf(requestsFlow.value.find { it.id == id })
        override suspend fun insertRequest(request: JobRequestEntity): Long = request.id
        override suspend fun updateRequestStatus(id: Long, status: String) { requestStatuses[id] = status }
        override suspend fun reportJobRequest(id: Long) = Unit
        override suspend fun deleteRequest(id: Long) = Unit

        override fun getQuotesForRequest(requestId: Long): Flow<List<QuoteEntity>> = flowOf(emptyList())
        override fun getAllQuotes(): Flow<List<QuoteEntity>> = flowOf(emptyList())
        override suspend fun insertQuote(quote: QuoteEntity): Long { insertedQuotes += quote; return 1L }
        override suspend fun updateQuoteStatus(id: Long, status: String) = Unit
        override suspend fun updateQuoteEscrow(id: Long, funded: Boolean, receiptCode: String) = Unit
        override suspend fun updateRequestEscrow(id: Long, escrowStatus: String, escrowAmount: String) = Unit

        override suspend fun insertReceipt(receipt: DigitalReceiptEntity) { storedReceipt = receipt }
        override fun getReceiptByCode(code: String): Flow<DigitalReceiptEntity?> = flowOf(storedReceipt)
        override suspend fun getReceiptByCodeDirect(code: String): DigitalReceiptEntity? =
            storedReceipt?.takeIf { it.receiptCode == code }
        override fun getReceiptForRequest(requestId: Long): Flow<DigitalReceiptEntity?> = flowOf(null)
        override fun getAllReceipts(): Flow<List<DigitalReceiptEntity>> = flowOf(emptyList())

        override fun getAllConversations(): Flow<List<ConversationEntity>> = flowOf(emptyList())
        override fun getConversationById(id: String): Flow<ConversationEntity?> = flowOf(null)
        override suspend fun insertConversation(conv: ConversationEntity) = Unit
        override suspend fun updateConversationLastMessage(id: String, lastMsg: String, timestamp: Long) = Unit
        override fun getMessagesForConversation(convId: String): Flow<List<ChatMessageEntity>> = flowOf(emptyList())
        override suspend fun insertMessage(msg: ChatMessageEntity): Long = 1L
    }
}
