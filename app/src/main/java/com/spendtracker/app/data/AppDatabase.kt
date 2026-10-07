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

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE transactions ADD COLUMN merchant TEXT")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS category_groups (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                emoji TEXT NOT NULL,
                sortOrder INTEGER NOT NULL,
                isBuiltIn INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS categories (
                `key` TEXT PRIMARY KEY NOT NULL,
                groupId INTEGER NOT NULL,
                name TEXT NOT NULL,
                emoji TEXT NOT NULL,
                colorHex TEXT NOT NULL,
                type TEXT NOT NULL,
                isBuiltIn INTEGER NOT NULL,
                isHidden INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_categories_groupId ON categories(groupId)")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS merchant_rules (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                matchType TEXT NOT NULL,
                pattern TEXT NOT NULL,
                categoryKey TEXT NOT NULL,
                createdAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
        CatalogSeed.seed(db)
    }
}

@Database(
    entities = [
        AccountEntity::class,
        TransactionEntity::class,
        CategoryGroupEntity::class,
        CategoryEntity::class,
        MerchantRuleEntity::class
    ],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun accountDao(): AccountDao
    abstract fun catalogDao(): CatalogDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "spend_tracker_room.db"
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("INSERT INTO accounts(name) VALUES ('Checking'), ('Savings'), ('Credit Card')")
                        CatalogSeed.seed(db)
                    }
                })
                .build()
                .also { instance = it }
        }
    }
}
