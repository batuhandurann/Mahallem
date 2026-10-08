package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.Room
import androidx.room.RoomDatabase

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

        private const val DATABASE_NAME = "mahallemde_marketplace.db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun clearLocalData() {
            synchronized(this) {
                INSTANCE?.let {
                    it.clearAllTables()
                    it.close()
                }
                INSTANCE = null
            }
        }

        /**
         * Log-out cleanup must delete persisted rows even after a process restart,
         * when INSTANCE is null. Call from a background dispatcher.
         */
        fun clearLocalData(context: Context) {
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = null
                val appContext = context.applicationContext
                val deleted = appContext.deleteDatabase(DATABASE_NAME)
                check(deleted || !appContext.getDatabasePath(DATABASE_NAME).exists()) {
                    "Yerel veritabanı silinemedi."
                }
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                ).addMigrations(MIGRATION_3_4).build().also { INSTANCE = it }
            }
        }
    }
}
