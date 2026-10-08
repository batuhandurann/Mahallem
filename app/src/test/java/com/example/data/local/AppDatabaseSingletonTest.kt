package com.example.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppDatabaseSingletonTest {
    @Test
    fun concurrentCallersShareOneRoomInstance() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        withContext(Dispatchers.IO) { AppDatabase.clearLocalData() }
        val executor = Executors.newFixedThreadPool(12)
        try {
            val gate = CountDownLatch(1)
            val tasks = (1..12).map {
                executor.submit<AppDatabase> {
                    gate.await()
                    AppDatabase.getDatabase(context)
                }
            }
            gate.countDown()
            val databases = tasks.map { it.get(20, TimeUnit.SECONDS) }
            databases.drop(1).forEach { assertSame(databases.first(), it) }
        } finally {
            executor.shutdownNow()
            withContext(Dispatchers.IO) { AppDatabase.clearLocalData() }
        }
    }

    @Test
    fun clearingOnIoPreservesSharedRoomForExistingDaoReferences() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        withContext(Dispatchers.IO) { AppDatabase.clearLocalData() }
        try {
            val first = withContext(Dispatchers.IO) {
                AppDatabase.getDatabase(context).also { it.openHelper.writableDatabase }
            }
            withContext(Dispatchers.IO) { AppDatabase.clearLocalData() }
            val second = withContext(Dispatchers.IO) {
                AppDatabase.getDatabase(context).also { it.openHelper.writableDatabase }
            }
            assertSame(first, second)
        } finally {
            withContext(Dispatchers.IO) { AppDatabase.clearLocalData() }
        }
    }

    @Test
    fun persistedDatabaseIsDeletedWhenSingletonIsNull() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = context.getDatabasePath("mahallemde_marketplace.db")
        withContext(Dispatchers.IO) {
            AppDatabase.clearLocalData(context)
            try {
                AppDatabase.getDatabase(context).openHelper.writableDatabase
                assertTrue("Room should create the DB on disk", file.exists())

                // Simulate a new-process logout with no currently open singleton.
                AppDatabase.clearLocalData()
                assertTrue("Account switching clears rows without invalidating DAO references", file.exists())

                AppDatabase.clearLocalData(context)
                assertFalse("Account data must not survive logout", file.exists())
            } finally {
                AppDatabase.clearLocalData(context)
            }
        }
    }
}
