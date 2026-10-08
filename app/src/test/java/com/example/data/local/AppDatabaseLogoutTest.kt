package com.example.data.local

import android.content.Context
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
    @Test fun mainThreadLogoutClearsEveryTableAndKeepsDaoUsableForNextAccount() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = AppDatabase.getDatabase(context)
        val dao = database.appDao()
        AppDatabase.clearLocalData()
        dao.insertProviders(InitialData.getSeedProviders())
        InitialData.getSeedRequests().forEach { dao.insertRequest(it) }
        InitialData.getSeedQuotes().forEach { dao.insertQuote(it) }
        InitialData.getSeedConversations().forEach { dao.insertConversation(it) }
        InitialData.getSeedMessages().forEach { dao.insertMessage(it) }
        dao.insertReceipt(DigitalReceiptEntity(
            "account-a-receipt", 1, 1, "Boya", "Account A", "Usta", "Boyacı",
            "100", "LOCKED", "Garanti", "2026-10-08", "Karşıyaka"
        ))
        val tables = listOf("service_providers", "job_requests", "quotes", "conversations", "chat_messages", "digital_receipts")
        suspend fun count(table: String): Int = withContext(Dispatchers.IO) {
            database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use {
                it.moveToFirst()
                it.getInt(0)
            }
        }
        tables.forEach { assertTrue("$it must be populated", count(it) > 0) }

        // Call directly from the UI/test thread: clearLocalData must dispatch its own IO.
        AppDatabase.clearLocalData()
        tables.forEach { assertEquals("Account A data in $it", 0, count(it)) }
        assertSame(database, AppDatabase.getDatabase(context))
        assertTrue(dao.getAllRequests().first().isEmpty())
        assertTrue(dao.getAllConversations().first().isEmpty())
        val next = InitialData.getSeedRequests().first().copy(id = 9001, title = "Account B request")
        dao.insertRequest(next)
        assertEquals(listOf("Account B request"), dao.getAllRequests().first().map { it.title })
        AppDatabase.clearLocalData()
        AppDatabase.clearLocalData() // Repeated logout is safe.
        assertTrue(dao.getAllRequests().first().isEmpty())
    }
}
