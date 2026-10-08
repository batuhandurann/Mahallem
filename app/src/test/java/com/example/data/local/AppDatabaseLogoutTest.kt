package com.example.data.local

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppDatabaseLogoutTest {
    @Test fun mainThreadLogoutClearsPrivateTablesAndOldDaoRemainsUsable() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = AppDatabase.getDatabase(context)
        val dao = db.appDao()
        try {
            withContext(Dispatchers.IO) {
                dao.insertConversation(ConversationEntity("A-chat", "A", "Alice", "service", "private A", 1L))
                dao.insertMessage(ChatMessageEntity(conversationId = "A-chat", senderId = "A", senderName = "Alice", text = "private A", isFromMe = true))
                dao.insertRequest(JobRequestEntity(title = "private A", sector = "CLEANING", categoryId = "cleaning", district = "İzmir", urgencyMode = "PLANNED", eventOrJobDate = "2026-10-09", eventTime = "09:00", address = "private address", status = "PENDING", customerName = "Alice", customerPhone = "05321234567"))
                assertEquals(1, dao.getAllRequests().first().size)
            }
            assertEquals(Looper.getMainLooper(), Looper.myLooper())
            // Room's main-thread assertion would throw if clearAllTables weren't dispatched to IO.
            AppDatabase.clearLocalData()
            withContext(Dispatchers.IO) {
                assertTrue(dao.getAllRequests().first().isEmpty())
                assertTrue(dao.getAllConversations().first().isEmpty())
                assertTrue(dao.getMessagesForConversation("A-chat").first().isEmpty())
                dao.insertConversation(ConversationEntity("B-chat", "B", "Bob", "service", "private B", 2L))
                assertEquals(listOf("B-chat"), dao.getAllConversations().first().map { it.id })
                assertSame(db, AppDatabase.getDatabase(context))
            }
        } finally { AppDatabase.clearLocalData() }
    }

    @Test fun persistedPrivateDataIsClearedEvenWithoutInitializedSingleton() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = AppDatabase.getDatabase(context)
        withContext(Dispatchers.IO) {
            db.appDao().insertConversation(ConversationEntity("A-persisted", "A", "Alice", "service", "private A", 1L))
            db.close()
            // Simulate process restart: keep disk, drop the in-memory singleton.
            AppDatabase::class.java.getDeclaredField("INSTANCE").apply { isAccessible = true }.set(null, null)
        }
        try {
            AppDatabase.initialize(context)
            AppDatabase.clearLocalData()
            assertTrue(AppDatabase.getDatabase(context).appDao().getAllConversations().first().isEmpty())
        } finally { AppDatabase.clearLocalData() }
    }
}
