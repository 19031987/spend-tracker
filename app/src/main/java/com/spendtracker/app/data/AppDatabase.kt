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
            val canonical = group.firstOrNull { it.rawName.trim().equals(it.normName, ignoreCase = true) }
                ?: group.minByOrNull { it.id } ?: continue
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

        // 4. Ensure core default accounts exist if missing
        val defaultBanks = listOf("Chase", "HSBC", "Credit Card")
        for (b in defaultBanks) {
            val lower = b.lowercase(java.util.Locale.ROOT)
            val countCursor = db.query("SELECT COUNT(*) FROM accounts WHERE LOWER(TRIM(name)) = ?", arrayOf(lower))
            val exists = countCursor.moveToFirst() && countCursor.getInt(0) > 0
            countCursor.close()
            if (!exists) {
                db.execSQL("INSERT OR IGNORE INTO accounts(name) VALUES (?)", arrayOf(b))
            }
        }

        // 4b. Link any transaction whose source or note indicates Premier/HSBC directly to the HSBC account
        val hsbcCursor = db.query("SELECT id FROM accounts WHERE LOWER(TRIM(name)) = 'hsbc' LIMIT 1")
        if (hsbcCursor.moveToFirst()) {
            val hsbcId = hsbcCursor.getLong(0)
            db.execSQL(
                """
                UPDATE transactions
                SET accountId = $hsbcId, source = 'HSBC'
                WHERE LOWER(TRIM(source)) IN ('hsbc', 'hsbc uk', 'hsbc bank', 'premier', 'hsbc premier', 'premier account', 'premier bank', 'advance', 'hsbc advance', 'advance account')
                   OR LOWER(note) LIKE 'captured from premier%'
                   OR LOWER(note) LIKE 'captured from hsbc%'
                """.trimIndent()
            )
        }
        hsbcCursor.close()

        // 5. Update transactions.source to canonical account name
        db.execSQL("""
            UPDATE transactions
            SET source = (
                SELECT name FROM accounts WHERE accounts.id = transactions.accountId
            )
            WHERE accountId IS NOT NULL AND accountId IN (SELECT id FROM accounts)
        """.trimIndent())

        // 5b. Purge unwanted 'Messages', 'Savings', 'Others' accounts and remap any linked transactions
        try {
            val chaseCursor = db.query("SELECT id FROM accounts WHERE LOWER(TRIM(name)) = 'chase' LIMIT 1")
            val chaseId = if (chaseCursor.moveToFirst()) chaseCursor.getLong(0) else 1L
            chaseCursor.close()

            db.execSQL("""
                UPDATE transactions 
                SET accountId = $chaseId, source = 'Chase'
                WHERE accountId IN (
                    SELECT id FROM accounts 
                    WHERE LOWER(TRIM(name)) IN ('messages', 'savings', 'messaging', 'others', 'bank alert', 'message')
                )
            """.trimIndent())

            db.execSQL("""
                DELETE FROM accounts 
                WHERE LOWER(TRIM(name)) IN ('messages', 'savings', 'messaging', 'others', 'bank alert', 'message')
            """.trimIndent())
        } catch (e: Exception) {
            android.util.Log.w("AppDatabase", "Purge junk accounts notice: ${e.message}")
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

fun cleanupAndRepairTransactions(db: SupportSQLiteDatabase, context: Context) {
    try {
        // 1. Repair from Room's transactions table where note contains pipe symbols
        val cursor = db.query(
            "SELECT id, amount, note, source, merchant, type FROM transactions WHERE note LIKE '%|%' OR note LIKE '%¦%'"
        )
        while (cursor.moveToNext()) {
            val txId = cursor.getLong(0)
            val currentAmount = cursor.getLong(1)
            val note = cursor.getString(2) ?: ""
            val source = cursor.getString(3) ?: ""
            val existingMerchant = if (cursor.isNull(4)) null else cursor.getString(4)
            val existingType = cursor.getString(5) ?: "EXPENSE"

            val cleanNote = note
                .replace(Regex("(?i)^Captured from [^:]+:\\s*"), "")
                .replace(Regex("(?i)^Captured from [^:]+ alert:?\\s*"), "")
                .trim()

            val parsed = com.spendtracker.app.parser.SpendParser.parse(source, "", cleanNote)
            if (parsed != null && parsed.merchant.isNotBlank()) {
                val hasExplicitInSignal = Regex("(?i)\\bmoney[ -]in\\b").containsMatchIn(note) ||
                    cleanNote.trimStart().startsWith("+")
                val resolvedType = if (existingType == "EXPENSE" && !hasExplicitInSignal && !isUnknownOrGenericMerchant(existingMerchant, source)) {
                    "EXPENSE"
                } else {
                    if (parsed.type == com.spendtracker.app.parser.SpendParser.TYPE_IN) "INCOME" else "EXPENSE"
                }
                val absMinor = kotlin.math.abs(currentAmount)
                val newAmount = if (resolvedType == "INCOME") absMinor else -absMinor
                db.execSQL(
                    "UPDATE transactions SET merchant = ?, type = ?, amount = ? WHERE id = ?",
                    arrayOf(parsed.merchant, resolvedType, newAmount, txId)
                )
            }
        }
        cursor.close()
    } catch (e: Exception) {
        android.util.Log.w("AppDatabase", "Repair from note notice: ${e.message}")
    }

    try {
        // 2. Repair from legacy spend_tracker.db if present
        val legacyDbFile = context.getDatabasePath("spend_tracker.db")
        if (legacyDbFile != null && legacyDbFile.exists()) {
            val legacyDb = android.database.sqlite.SQLiteDatabase.openDatabase(
                legacyDbFile.absolutePath,
                null,
                android.database.sqlite.SQLiteDatabase.OPEN_READWRITE
            )
            val expCursor = legacyDb.rawQuery(
                "SELECT id, amount, raw_text, timestamp, source FROM expenses WHERE raw_text LIKE '%|%' OR raw_text LIKE '%¦%'",
                null
            )
            while (expCursor.moveToNext()) {
                val expId = expCursor.getLong(0)
                val rawText = expCursor.getString(2) ?: ""
                val timestamp = expCursor.getLong(3)
                val source = expCursor.getString(4) ?: ""

                val parsed = com.spendtracker.app.parser.SpendParser.parse(source, "", rawText)
                if (parsed != null && parsed.merchant.isNotBlank()) {
                    val newType = if (parsed.type == com.spendtracker.app.parser.SpendParser.TYPE_IN) "INCOME" else "EXPENSE"
                    val absMinor = kotlin.math.abs((parsed.amount * 100.0).toLong())
                    val newAmount = if (newType == "INCOME") absMinor else -absMinor

                    val cv = android.content.ContentValues().apply {
                        put("merchant", parsed.merchant)
                        put("type", parsed.type)
                        put("category", parsed.category)
                    }
                    legacyDb.update("expenses", cv, "id = ?", arrayOf(expId.toString()))

                    db.execSQL(
                        """
                        UPDATE transactions 
                        SET merchant = ?, type = ?, amount = ?
                        WHERE ABS(ABS(amount) - ?) <= 1 
                          AND ABS(timestamp - ?) <= 60000
                          AND (merchant IS NULL OR merchant = '' OR merchant IN ('Money In', 'Money Out', 'Unknown Vendor', 'HSBC Payment', 'Chase Payment', 'Card Payment', 'Payment', 'HSBC', 'Chase'))
                        """.trimIndent(),
                        arrayOf(parsed.merchant, newType, newAmount, absMinor, timestamp)
                    )
                }
            }
            expCursor.close()
            legacyDb.close()
        }
    } catch (e: Exception) {
        android.util.Log.w("AppDatabase", "Repair from legacy db notice: ${e.message}")
    }

    try {
        // 3. Clear literal 'Money In' / 'Money Out' / generic unknown labels as merchant name and fix inverted sign
        db.execSQL(
            """
            UPDATE transactions
            SET merchant = NULL
            WHERE merchant IN ('Money In', 'Money Out', 'Unknown', 'Unknown Vendor', 'Transaction', 'Card Payment', 'Payment', 'HSBC Payment', 'Chase Payment', 'Premier Payment', 'Premier', 'HSBC')
            """.trimIndent()
        )
        db.execSQL(
            """
            UPDATE transactions
            SET type = 'INCOME', amount = ABS(amount)
            WHERE type = 'EXPENSE' AND (
                LOWER(note) LIKE '%money in%' OR
                LOWER(category) LIKE '%money in%'
            )
            """.trimIndent()
        )
        // 4. Remove between-account transfer records and normalize missing/mismatched default categories
        db.execSQL(
            """
            DELETE FROM transactions
            WHERE type = 'TRANSFER' OR category = 'INTERNAL_TRANSFER'
            """.trimIndent()
        )
        db.execSQL(
            """
            UPDATE categories
            SET isHidden = 1
            WHERE `key` = 'INTERNAL_TRANSFER' OR type = 'TRANSFER'
            """.trimIndent()
        )
        db.execSQL(
            """
            UPDATE transactions
            SET category = 'OTHER_INCOME'
            WHERE type = 'INCOME' AND (category IS NULL OR category = 'OTHER_EXPENSE' OR category = 'Money In' OR category = 'Transfers In')
            """.trimIndent()
        )
        db.execSQL(
            """
            UPDATE transactions
            SET category = 'OTHER_EXPENSE'
            WHERE type = 'EXPENSE' AND (category IS NULL OR category = 'General Spend')
            """.trimIndent()
        )

        // 5. Deduplicate transactions in Room (including Premier <-> HSBC duplicates with same amount & vendor,
        //    or unknown vendor with same amount within the time window), always saving the HSBC one.
        //    Unknown transactions with different amount or different time are NOT considered duplicates.
        val hsbcAccCursor = db.query("SELECT id FROM accounts WHERE LOWER(TRIM(name)) = 'hsbc' LIMIT 1")
        val hsbcAccountId = if (hsbcAccCursor.moveToFirst()) hsbcAccCursor.getLong(0) else null
        hsbcAccCursor.close()

        data class RoomTxRow(
            val id: Long,
            var accountId: Long,
            var amount: Long,
            var category: String?,
            var merchant: String?,
            var note: String?,
            var source: String?,
            val timestamp: Long,
            var type: String
        )

        val txCursor = db.query(
            "SELECT id, accountId, amount, category, merchant, note, source, timestamp, type FROM transactions ORDER BY timestamp ASC, id ASC"
        )
        val allRows = mutableListOf<RoomTxRow>()
        while (txCursor.moveToNext()) {
            allRows.add(
                RoomTxRow(
                    id = txCursor.getLong(0),
                    accountId = txCursor.getLong(1),
                    amount = txCursor.getLong(2),
                    category = if (txCursor.isNull(3)) null else txCursor.getString(3),
                    merchant = if (txCursor.isNull(4)) null else txCursor.getString(4),
                    note = if (txCursor.isNull(5)) null else txCursor.getString(5),
                    source = if (txCursor.isNull(6)) null else txCursor.getString(6),
                    timestamp = txCursor.getLong(7),
                    type = txCursor.getString(8) ?: "EXPENSE"
                )
            )
        }
        txCursor.close()

        val keptRows = mutableListOf<RoomTxRow>()
        val duplicateIdsToDelete = mutableListOf<Long>()

        fun isCandidateMatch(kept: RoomTxRow, row: RoomTxRow, requireBothKnown: Boolean): Boolean {
            if (kotlin.math.abs(kept.amount) != kotlin.math.abs(row.amount)) return false
            if (!isSameOrEquivalentBank(kept.source ?: "HSBC", row.source ?: "HSBC")) return false
            val preferredBank = resolvePreferredBankSource(kept.source, row.source)
            val bothKnown = !isUnknownOrGenericMerchant(kept.merchant, preferredBank) &&
                !isUnknownOrGenericMerchant(row.merchant, preferredBank)
            if (requireBothKnown && !bothKnown) return false
            if (!isMerchantDuplicate(kept.merchant, row.merchant, preferredBank)) return false

            val involvesPremier = (kept.source?.contains("premier", ignoreCase = true) == true) ||
                (row.source?.contains("premier", ignoreCase = true) == true) ||
                (kept.note?.contains("premier", ignoreCase = true) == true) ||
                (row.note?.contains("premier", ignoreCase = true) == true)

            val windowMillis = if (bothKnown) 600_000L else if (involvesPremier) 300_000L else 180_000L
            return kotlin.math.abs(kept.timestamp - row.timestamp) <= windowMillis
        }

        for (row in allRows) {
            val match = keptRows.lastOrNull { kept -> isCandidateMatch(kept, row, requireBothKnown = true) }
                ?: keptRows.lastOrNull { kept -> isCandidateMatch(kept, row, requireBothKnown = false) }

            if (match != null) {
                val preferredBank = resolvePreferredBankSource(match.source, row.source)
                val targetAccountId = if (preferredBank.equals("HSBC", ignoreCase = true) && hsbcAccountId != null) {
                    hsbcAccountId
                } else {
                    match.accountId
                }
                val enrichedMerchant = if (isUnknownOrGenericMerchant(match.merchant, preferredBank) &&
                    !isUnknownOrGenericMerchant(row.merchant, preferredBank)) {
                    row.merchant
                } else if (isUnknownOrGenericMerchant(match.merchant, preferredBank)) {
                    null
                } else {
                    match.merchant
                }
                val matchCatIsDefault = match.category.isNullOrBlank() || match.category == "OTHER_EXPENSE" || match.category == "OTHER_INCOME"
                val rowCatIsSpecific = !row.category.isNullOrBlank() && row.category != "OTHER_EXPENSE" && row.category != "OTHER_INCOME"
                val useRowCategoryAndType = matchCatIsDefault && rowCatIsSpecific

                val enrichedCategory = if (useRowCategoryAndType) row.category else match.category
                val enrichedType = if (useRowCategoryAndType) {
                    row.type
                } else if (match.type == "EXPENSE" && row.type == "INCOME" && (row.note?.contains("money in", ignoreCase = true) == true)) {
                    "INCOME"
                } else {
                    match.type
                }
                val absAmt = kotlin.math.abs(match.amount)
                val enrichedAmount = if (enrichedType == "INCOME") absAmt else -absAmt

                val rawPreferredNote = when {
                    match.note.isNullOrBlank() && !row.note.isNullOrBlank() -> row.note
                    match.note?.contains("Premier", ignoreCase = true) == true &&
                        row.note?.contains("HSBC", ignoreCase = true) == true &&
                        !isUnknownOrGenericMerchant(row.merchant, preferredBank) -> row.note
                    else -> match.note
                }
                val enrichedNote = rawPreferredNote?.replace(
                    Regex("(?i)^Captured from\\s+(?:HSBC\\s+)?Premier(?:\\s+Account|\\s+Bank)?"),
                    "Captured from $preferredBank"
                )

                match.accountId = targetAccountId
                match.source = preferredBank
                match.merchant = enrichedMerchant
                match.category = enrichedCategory
                match.type = enrichedType
                match.amount = enrichedAmount
                match.note = enrichedNote

                db.execSQL(
                    "UPDATE transactions SET accountId = ?, source = ?, merchant = ?, category = ?, type = ?, amount = ?, note = ? WHERE id = ?",
                    arrayOf(targetAccountId, preferredBank, enrichedMerchant, enrichedCategory, enrichedType, enrichedAmount, enrichedNote, match.id)
                )
                duplicateIdsToDelete.add(row.id)
            } else {
                if (isPremierOrHsbcBank(row.source) || row.note?.contains("Captured from Premier", ignoreCase = true) == true) {
                    val targetAccountId = hsbcAccountId ?: row.accountId
                    val cleanNote = row.note?.replace(
                        Regex("(?i)^Captured from\\s+(?:HSBC\\s+)?Premier(?:\\s+Account|\\s+Bank)?"),
                        "Captured from HSBC"
                    )
                    if (row.source != "HSBC" || row.accountId != targetAccountId || row.note != cleanNote) {
                        row.source = "HSBC"
                        row.accountId = targetAccountId
                        row.note = cleanNote
                        db.execSQL(
                            "UPDATE transactions SET accountId = ?, source = 'HSBC', note = ? WHERE id = ?",
                            arrayOf(targetAccountId, cleanNote, row.id)
                        )
                    }
                }
                keptRows.add(row)
            }
        }

        if (duplicateIdsToDelete.isNotEmpty()) {
            db.execSQL("DELETE FROM transactions WHERE id IN (${duplicateIdsToDelete.joinToString(",")})")
        }
    } catch (e: Exception) {
        android.util.Log.w("AppDatabase", "Clean Money In labels and deduplicate notice: ${e.message}")
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

val MIGRATION_6_7 = object : Migration(6, 7) {
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
    version = 7,
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
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("INSERT OR IGNORE INTO accounts(name) VALUES ('Chase'), ('HSBC'), ('Credit Card')")
                        CatalogSeed.seed(db)
                    }

                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        cleanupAndConsolidateAccounts(db)
                        cleanupAndRepairTransactions(db, context.applicationContext)
                    }
                })
                .build()
                .also { instance = it }
        }
    }
}
