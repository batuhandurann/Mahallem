package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Database(
    entities = [
        ServiceProviderEntity::class,
        JobRequestEntity::class,
        QuoteEntity::class,
        ChatMessageEntity::class,
        ConversationEntity::class,
        DigitalReceiptEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appDao(): AppDao

    companion object {
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE quotes ADD COLUMN amountMinor INTEGER NOT NULL DEFAULT 0")
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        suspend fun clearLocalData(context: Context? = null) = withContext(Dispatchers.IO) {
            synchronized(this) {
                // Keep existing DAO/Flow references valid. Closing the shared instance
                // races active collectors; clearAllTables is transactional instead.
                // After a process restart the disk cache can exist before INSTANCE does.
                val database = INSTANCE ?: context?.let { getDatabase(it) }
                database?.clearAllTables()
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mahallemde_marketplace.db"
                ).addMigrations(MIGRATION_3_4).build().also { INSTANCE = it }
            }
        }
    }
}
