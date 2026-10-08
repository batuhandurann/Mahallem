package com.example.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
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
}
