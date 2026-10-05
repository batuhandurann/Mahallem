package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    // --- Service Providers (Flow A: Esnaf/Hizmet İlanları) ---
    @Query("SELECT * FROM service_providers WHERE isReported = 0 ORDER BY rating DESC, reviewCount DESC")
    fun getAllProviders(): Flow<List<ServiceProviderEntity>>

    @Query("SELECT * FROM service_providers WHERE sector = :sector AND isReported = 0 ORDER BY rating DESC")
    fun getProvidersBySector(sector: String): Flow<List<ServiceProviderEntity>>

    @Query("SELECT * FROM service_providers WHERE id = :id LIMIT 1")
    fun getProviderById(id: String): Flow<ServiceProviderEntity?>

    @Query("SELECT * FROM service_providers WHERE id = :id LIMIT 1")
    suspend fun getProviderByIdDirect(id: String): ServiceProviderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProviders(providers: List<ServiceProviderEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProvider(provider: ServiceProviderEntity)

    @Update
    suspend fun updateProvider(provider: ServiceProviderEntity)

    @Query("UPDATE service_providers SET isFavorite = :isFav WHERE id = :id")
    suspend fun setFavorite(id: String, isFav: Boolean)

    @Query("UPDATE service_providers SET isOpenForOffers = :isOpen WHERE id = :id")
    suspend fun setOpenForOffers(id: String, isOpen: Boolean)

    @Query("UPDATE service_providers SET bookedDatesJson = :bookedDatesJson WHERE id = :id")
    suspend fun updateBookedDates(id: String, bookedDatesJson: String)

    @Query("UPDATE service_providers SET isReported = 1 WHERE id = :id")
    suspend fun reportProvider(id: String)

    @Query("SELECT COUNT(*) FROM service_providers")
    suspend fun getProviderCount(): Int

    // --- Job Requests (Flow B: Hizmet Arayan İhtiyaç İlanları) ---
    @Query("SELECT * FROM job_requests WHERE isReported = 0 ORDER BY createdAt DESC")
    fun getAllRequests(): Flow<List<JobRequestEntity>>

    @Query("SELECT * FROM job_requests WHERE id = :id LIMIT 1")
    fun getRequestById(id: Long): Flow<JobRequestEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRequest(request: JobRequestEntity): Long

    @Query("UPDATE job_requests SET status = :status WHERE id = :id")
    suspend fun updateRequestStatus(id: Long, status: String)

    @Query("UPDATE job_requests SET isReported = 1 WHERE id = :id")
    suspend fun reportJobRequest(id: Long)

    @Query("DELETE FROM job_requests WHERE id = :id")
    suspend fun deleteRequest(id: Long)

    // --- Quotes (Armut Tarzı Teklifler) ---
    @Query("SELECT * FROM quotes WHERE requestId = :requestId ORDER BY createdAt DESC")
    fun getQuotesForRequest(requestId: Long): Flow<List<QuoteEntity>>

    @Query("SELECT * FROM quotes ORDER BY createdAt DESC")
    fun getAllQuotes(): Flow<List<QuoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuote(quote: QuoteEntity): Long

    @Query("UPDATE quotes SET status = :status WHERE id = :id")
    suspend fun updateQuoteStatus(id: Long, status: String)

    @Query("UPDATE quotes SET escrowFunded = :funded, receiptCode = :receiptCode WHERE id = :id")
    suspend fun updateQuoteEscrow(id: Long, funded: Boolean, receiptCode: String)

    @Query("UPDATE job_requests SET escrowStatus = :escrowStatus, escrowAmount = :escrowAmount WHERE id = :id")
    suspend fun updateRequestEscrow(id: Long, escrowStatus: String, escrowAmount: String)

    // --- Digital Receipts & Warranty Certificates ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReceipt(receipt: DigitalReceiptEntity)

    @Query("SELECT * FROM digital_receipts WHERE receiptCode = :code LIMIT 1")
    fun getReceiptByCode(code: String): Flow<DigitalReceiptEntity?>

    @Query("SELECT * FROM digital_receipts WHERE requestId = :requestId LIMIT 1")
    fun getReceiptForRequest(requestId: Long): Flow<DigitalReceiptEntity?>

    @Query("SELECT * FROM digital_receipts ORDER BY createdAtDate DESC")
    fun getAllReceipts(): Flow<List<DigitalReceiptEntity>>

    // --- In-App Chat & Messaging (Letgo / Sahibinden Tarzı Mesajlaşma) ---
    @Query("SELECT * FROM conversations ORDER BY lastTimestamp DESC")
    fun getAllConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id LIMIT 1")
    fun getConversationById(id: String): Flow<ConversationEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(conv: ConversationEntity)

    @Query("UPDATE conversations SET lastMessage = :lastMsg, lastTimestamp = :timestamp WHERE id = :id")
    suspend fun updateConversationLastMessage(id: String, lastMsg: String, timestamp: Long)

    @Query("SELECT * FROM chat_messages WHERE conversationId = :convId ORDER BY timestamp ASC")
    fun getMessagesForConversation(convId: String): Flow<List<ChatMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(msg: ChatMessageEntity): Long
}
