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

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE transactions ADD COLUMN source TEXT")
        db.execSQL("""
            INSERT OR IGNORE INTO accounts(name)
            SELECT 'Chase' WHERE NOT EXISTS (SELECT 1 FROM accounts WHERE LOWER(TRIM(name)) = 'chase')
        """.trimIndent())
        db.execSQL("""
            INSERT OR IGNORE INTO accounts(name)
            SELECT 'HSBC' WHERE NOT EXISTS (SELECT 1 FROM accounts WHERE LOWER(TRIM(name)) = 'hsbc')
        """.trimIndent())
        db.execSQL(
            """
            INSERT OR IGNORE INTO categories(`key`, groupId, name, emoji, colorHex, type, isBuiltIn, isHidden)
            VALUES ('INTERNAL_TRANSFER', 6, 'Internal Transfer', '🔄', '#EDE9FE', 'TRANSFER', 1, 0)
            """.trimIndent()
        )
    }
}

fun cleanupAndConsolidateAccounts(db: SupportSQLiteDatabase) {
    try {
        db.beginTransaction()

        // 1. Read all existing accounts and normalize them
        data class AccRow(val id: Long, val rawName: String, val normName: String)
        val allAccounts = mutableListOf<AccRow>()
        val cursor = db.query("SELECT id, name FROM accounts ORDER BY id ASC")
        while (cursor.moveToNext()) {
            val id = cursor.getLong(0)
            val name = cursor.getString(1) ?: ""
            allAccounts.add(AccRow(id, name, normalizeBankName(name)))
        }
        cursor.close()

        // 2. Group by canonical normalized name (case-insensitive)
        val grouped = allAccounts.groupBy { it.normName.lowercase(java.util.Locale.ROOT) }
        for ((_, group) in grouped) {
            val canonical = group.minByOrNull { it.id } ?: continue
            val canonicalId = canonical.id
            val canonicalName = canonical.normName

            val duplicates = group.filter { it.id != canonicalId }
            if (duplicates.isNotEmpty()) {
                val dupIds = duplicates.map { it.id }
                val dupIdsStr = dupIds.joinToString(",")

                // A. Re-link transactions pointing to duplicate accounts to canonical MIN(id)
                db.execSQL("UPDATE transactions SET accountId = $canonicalId WHERE accountId IN ($dupIdsStr)")
                db.execSQL("UPDATE transactions SET destinationAccountId = $canonicalId WHERE destinationAccountId IN ($dupIdsStr)")

                // B. Delete duplicate account rows
                db.execSQL("DELETE FROM accounts WHERE id IN ($dupIdsStr)")
            }

            // C. Ensure canonical account name is properly normalized and trimmed
            db.execSQL("UPDATE accounts SET name = ? WHERE id = ?", arrayOf(canonicalName, canonicalId))
        }

        // 3. Re-link orphan transactions having matching or recognizable source name to accounts
        val remainingCursor = db.query("SELECT id, name FROM accounts ORDER BY id ASC")
        val remainingAccounts = mutableListOf<Pair<Long, String>>()
        while (remainingCursor.moveToNext()) {
            remainingAccounts.add(remainingCursor.getLong(0) to (remainingCursor.getString(1) ?: ""))
        }
        remainingCursor.close()

        val orphanCursor = db.query("""
            SELECT id, source FROM transactions 
            WHERE accountId IS NULL OR accountId NOT IN (SELECT id FROM accounts)
        """.trimIndent())
        val orphanTxs = mutableListOf<Pair<Long, String>>()
        while (orphanCursor.moveToNext()) {
            orphanTxs.add(orphanCursor.getLong(0) to (orphanCursor.getString(1) ?: ""))
        }
        orphanCursor.close()

        for ((txId, rawSource) in orphanTxs) {
            val norm = normalizeBankName(rawSource)
            val matched = remainingAccounts.firstOrNull { it.second.equals(norm, ignoreCase = true) }
            if (matched != null) {
                db.execSQL("UPDATE transactions SET accountId = ?, source = ? WHERE id = ?", arrayOf(matched.first, matched.second, txId))
            } else {
                db.execSQL("INSERT OR IGNORE INTO accounts(name) VALUES (?)", arrayOf(norm))
                val getCursor = db.query("SELECT id, name FROM accounts WHERE LOWER(TRIM(name)) = LOWER(TRIM(?)) LIMIT 1", arrayOf(norm))
                if (getCursor.moveToFirst()) {
                    val newId = getCursor.getLong(0)
                    val newName = getCursor.getString(1)
                    db.execSQL("UPDATE transactions SET accountId = ?, source = ? WHERE id = ?", arrayOf(newId, newName, txId))
                    remainingAccounts.add(newId to newName)
                }
                getCursor.close()
            }
        }

        // Clean up any invalid destinationAccountId
        db.execSQL("UPDATE transactions SET destinationAccountId = NULL WHERE destinationAccountId IS NOT NULL AND destinationAccountId NOT IN (SELECT id FROM accounts)")

        // 4. Update transactions.source to canonical account name
        db.execSQL("""
            UPDATE transactions
            SET source = (
                SELECT name FROM accounts WHERE accounts.id = transactions.accountId
            )
            WHERE accountId IS NOT NULL AND accountId IN (SELECT id FROM accounts)
        """.trimIndent())

        // 5. Ensure core default accounts exist if missing
        val defaultBanks = listOf("Chase", "HSBC", "Savings", "Credit Card")
        for (b in defaultBanks) {
            val lower = b.lowercase(java.util.Locale.ROOT)
            val countCursor = db.query("SELECT COUNT(*) FROM accounts WHERE LOWER(TRIM(name)) = ?", arrayOf(lower))
            val exists = countCursor.moveToFirst() && countCursor.getInt(0) > 0
            countCursor.close()
            if (!exists) {
                db.execSQL("INSERT OR IGNORE INTO accounts(name) VALUES (?)", arrayOf(b))
            }
        }

        // 6. Ensure unique index on accounts(name COLLATE NOCASE)
        try {
            db.execSQL("DROP INDEX IF EXISTS index_accounts_name")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_accounts_name ON accounts(name COLLATE NOCASE)")
        } catch (e: Exception) {
            android.util.Log.w("AppDatabase", "Index creation notice: ${e.message}")
        }

        db.setTransactionSuccessful()
    } catch (e: Exception) {
        android.util.Log.e("AppDatabase", "cleanupAndConsolidateAccounts failed: ${e.message}", e)
    } finally {
        if (db.inTransaction()) {
            db.endTransaction()
        }
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        cleanupAndConsolidateAccounts(db)
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        cleanupAndConsolidateAccounts(db)
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
    version = 6,
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
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("INSERT OR IGNORE INTO accounts(name) VALUES ('Chase'), ('HSBC'), ('Savings'), ('Credit Card')")
                        CatalogSeed.seed(db)
                    }

                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        cleanupAndConsolidateAccounts(db)
                        db.execSQL(
                            """
                            INSERT OR IGNORE INTO categories(`key`, groupId, name, emoji, colorHex, type, isBuiltIn, isHidden)
                            VALUES ('INTERNAL_TRANSFER', 6, 'Internal Transfer', '🔄', '#EDE9FE', 'TRANSFER', 1, 0)
                            """.trimIndent()
                        )
                    }
                })
                .build()
                .also { instance = it }
        }
    }
}
