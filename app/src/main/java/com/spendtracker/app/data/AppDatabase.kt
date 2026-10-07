package com.spendtracker.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE transactions ADD COLUMN type TEXT NOT NULL DEFAULT 'EXPENSE'")
        db.execSQL("ALTER TABLE transactions ADD COLUMN destinationAccountId INTEGER")
        db.execSQL("ALTER TABLE transactions ADD COLUMN pairedTransactionId INTEGER")
        db.execSQL("ALTER TABLE transactions ADD COLUMN excludeFromSpending INTEGER NOT NULL DEFAULT 0")
        // Existing expenses converted to signed amounts:
        db.execSQL("UPDATE transactions SET amount = -ABS(amount)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_accountId ON transactions(accountId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_timestamp ON transactions(timestamp)")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_transactions_pairedTransactionId " +
                "ON transactions(pairedTransactionId)"
        )
    }
}

@Database(
    entities = [AccountEntity::class, TransactionEntity::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun accountDao(): AccountDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "spend_tracker_room.db"
            )
                .addMigrations(MIGRATION_1_2)
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("INSERT INTO accounts(name) VALUES ('Checking'), ('Savings'), ('Credit Card')")
                    }
                })
                .build()
                .also { instance = it }
        }
    }
}
