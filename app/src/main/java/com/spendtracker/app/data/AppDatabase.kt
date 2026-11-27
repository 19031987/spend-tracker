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

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE transactions ADD COLUMN needsReview INTEGER NOT NULL DEFAULT 0")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `budgets` (
                `categoryKey` TEXT NOT NULL,
                `amountMinor` INTEGER NOT NULL,
                `rollover` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`categoryKey`)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `recurring_items` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `merchantKey` TEXT NOT NULL,
                `displayName` TEXT NOT NULL,
                `categoryKey` TEXT,
                `amountMinor` INTEGER NOT NULL,
                `frequency` TEXT NOT NULL,
                `anchorDate` INTEGER NOT NULL,
                `status` TEXT NOT NULL,
                `isIncome` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_recurring_items_merchantKey` ON `recurring_items` (`merchantKey`)")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `goals` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `emoji` TEXT NOT NULL,
                `colorHex` TEXT NOT NULL,
                `targetMinor` INTEGER NOT NULL,
                `targetDate` INTEGER,
                `startingMinor` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `archived` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `goal_contributions` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `goalId` INTEGER NOT NULL,
                `amountMinor` INTEGER NOT NULL,
                `timestamp` INTEGER NOT NULL,
                `note` TEXT
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_goal_contributions_goalId` ON `goal_contributions` (`goalId`)")
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
            db.execSQL("UPDATE accounts SET name = ? WHERE id = ?", arrayOf<Any?>(canonicalName, canonicalId))
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
                db.execSQL("UPDATE transactions SET accountId = ?, source = ? WHERE id = ?", arrayOf<Any?>(matched.first, matched.second, txId))
            } else {
                db.execSQL("INSERT OR IGNORE INTO accounts(name) VALUES (?)", arrayOf(norm))
                val getCursor = db.query("SELECT id, name FROM accounts WHERE LOWER(TRIM(name)) = LOWER(TRIM(?)) LIMIT 1", arrayOf(norm))
                if (getCursor.moveToFirst()) {
                    val newId = getCursor.getLong(0)
                    val newName = getCursor.getString(1)
                    db.execSQL("UPDATE transactions SET accountId = ?, source = ? WHERE id = ?", arrayOf<Any?>(newId, newName, txId))
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

            val cleanNote = alertTextOfNote(note)
                .replace(Regex("(?i)^Captured from [^:]+:\\s*"), "")
                .replace(Regex("(?i)^Captured from [^:]+ alert:?\\s*"), "")
                .trim()

            val parsed = com.spendtracker.app.parser.SpendParser.parse(source, "", cleanNote)
            // A wallet-only name ("PayPal") never replaces a real merchant already on the row
            // (e.g. one merged in from the PayPal app's own alert).
            val walletOnlyOverKnown = parsed != null && merchantNamesWallet(parsed.merchant) &&
                stripWalletMerchantPrefix(parsed.merchant).isBlank() &&
                !isUnknownOrGenericMerchant(existingMerchant, source)
            if (parsed != null && parsed.merchant.isNotBlank() && !walletOnlyOverKnown) {
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
                    arrayOf<Any?>(parsed.merchant, resolvedType, newAmount, txId)
                )
            }
        }
        cursor.close()
    } catch (e: Exception) {
        android.util.Log.w("AppDatabase", "Repair from note notice: ${e.message}")
    }

    try {
        // 1b. Fill in vendors that older parser versions missed in non-pipe alerts,
        // e.g. "your-saving.com £45.50 with HSBC Premier Credit Card". Only the merchant changes,
        // and only when the re-parsed amount matches the stored one.
        val cursor = db.query(
            "SELECT id, amount, note, merchant FROM transactions " +
                "WHERE note LIKE 'Captured from %' AND note NOT LIKE '%|%' AND note NOT LIKE '%¦%'"
        )
        while (cursor.moveToNext()) {
            val txId = cursor.getLong(0)
            val currentAmount = cursor.getLong(1)
            val note = cursor.getString(2) ?: ""
            val existingMerchant = if (cursor.isNull(3)) null else cursor.getString(3)
            val landed = currentAmount < 0 && Regex("(?i)\\blanded in\\b").containsMatchIn(note)
            if (!landed && !isUnknownOrGenericMerchant(existingMerchant)) continue

            val cleanNote = alertTextOfNote(note)
                .replace(Regex("(?i)^Captured from [^:]+:\\s*"), "")
                .trim()
            val parsed = com.spendtracker.app.parser.SpendParser.parse("", "", cleanNote) ?: continue
            // "£32.00 just landed in ... from X" was once saved as money out: it is money in.
            if (landed && parsed.type == com.spendtracker.app.parser.SpendParser.TYPE_IN) {
                db.execSQL(
                    "UPDATE transactions SET type = 'INCOME', amount = ?, " +
                        "category = CASE WHEN category IS NULL OR category IN ('OTHER_EXPENSE', 'General Spend') THEN 'OTHER_INCOME' ELSE category END " +
                        "WHERE id = ? AND type = 'EXPENSE'",
                    arrayOf<Any?>(kotlin.math.abs(currentAmount), txId)
                )
            }
            if (!isUnknownOrGenericMerchant(existingMerchant)) continue
            if (parsed.merchant.isBlank() || parsed.merchantIsFallback) continue
            if (kotlin.math.round(parsed.amount * 100).toLong() != kotlin.math.abs(currentAmount)) continue
            db.execSQL("UPDATE transactions SET merchant = ? WHERE id = ?", arrayOf<Any?>(parsed.merchant, txId))
        }
        cursor.close()
    } catch (e: Exception) {
        android.util.Log.w("AppDatabase", "Repair unknown vendors notice: ${e.message}")
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
                        arrayOf<Any?>(parsed.merchant, newType, newAmount, absMinor, timestamp)
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
        // 4. Normalize missing/mismatched default categories. (Transfers are user-created and are kept:
        //    this step used to delete every TRANSFER row on each app open.)
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

        // 5. Deduplicate transactions in Room with the same rules as insert time and the list view
        //    (dedupDecision): same type and amount, never transfers or paired legs, never a sign flip;
        //    same bank (HSBC == Premier) within DEDUP_WINDOW_MILLIS, or a wallet alert (PayPal / Google Pay)
        //    + the funding bank's alert within WALLET_DEDUP_WINDOW_MILLIS, keeping the bank row.
        //    Conservative: only a row captured from an alert ("Captured from ..." note) is ever deleted,
        //    rows excluded from spending are left alone, and a wallet row is only deleted while it is
        //    still unreviewed (needsReview = 1), i.e. the user has not touched it.
        val hsbcAccCursor = db.query("SELECT id FROM accounts WHERE LOWER(TRIM(name)) = 'hsbc' LIMIT 1")
        val hsbcAccountId = if (hsbcAccCursor.moveToFirst()) hsbcAccCursor.getLong(0) else null
        hsbcAccCursor.close()

        data class RoomTxRow(
            val id: Long,
            var accountId: Long,
            val amount: Long,
            var category: String?,
            var merchant: String?,
            var note: String?,
            var source: String?,
            val timestamp: Long,
            val type: String,
            val paired: Boolean,
            val excluded: Boolean,
            val needsReview: Boolean
        ) {
            val autoCaptured: Boolean get() = note?.startsWith("Captured from", ignoreCase = true) == true
            fun key(): DedupKey {
                val txType = runCatching { TransactionType.valueOf(type) }.getOrDefault(TransactionType.EXPENSE)
                return DedupKey(
                    amountMinor = kotlin.math.abs(amount),
                    type = txType,
                    source = source,
                    accountId = accountId,
                    merchant = merchant,
                    timestamp = timestamp,
                    isTransfer = txType == TransactionType.TRANSFER || category == "INTERNAL_TRANSFER" || paired,
                    isManual = !autoCaptured
                )
            }
        }

        val txCursor = db.query(
            "SELECT id, accountId, amount, category, merchant, note, source, timestamp, type, " +
                "pairedTransactionId, excludeFromSpending, needsReview FROM transactions ORDER BY timestamp ASC, id ASC"
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
                    type = txCursor.getString(8) ?: "EXPENSE",
                    paired = !txCursor.isNull(9),
                    excluded = !txCursor.isNull(10) && txCursor.getInt(10) != 0,
                    needsReview = !txCursor.isNull(11) && txCursor.getInt(11) != 0
                )
            )
        }
        txCursor.close()

        val keptRows = mutableListOf<RoomTxRow>()
        val duplicateIdsToDelete = mutableListOf<Long>()
        val premierPrefix = Regex("(?i)^Captured from\\s+(?:HSBC\\s+)?Premier(?:\\s+Account|\\s+Bank)?")

        fun canDelete(row: RoomTxRow, kind: DedupKind): Boolean =
            row.autoCaptured && !row.excluded && (kind == DedupKind.SAME_BANK || row.needsReview)

        for (row in allRows) {
            val eligible = keptRows.filter {
                !it.excluded && kotlin.math.abs(it.amount) == kotlin.math.abs(row.amount) &&
                    row.timestamp - it.timestamp <= MAX_DEDUP_WINDOW_MILLIS
            }
            val hit = if (row.excluded) null else pickDedupMatch(eligible.map { it.key() }, row.key())
            val match = hit?.let { eligible[it.first] }
            val decision = hit?.second
            val survivor = if (decision?.survivor == DedupSurvivor.INCOMING) row else match
            val dropped = if (decision?.survivor == DedupSurvivor.INCOMING) match else row

            if (match != null && decision != null && survivor != null && dropped != null && canDelete(dropped, decision.kind)) {
                val preferredBank = resolvePreferredBankSource(dropped.source, survivor.source)
                val targetAccountId = if (preferredBank.equals("HSBC", ignoreCase = true) && hsbcAccountId != null) {
                    hsbcAccountId
                } else {
                    survivor.accountId
                }
                val enrichedMerchant = mergedDedupMerchant(survivor.key(), dropped.key(), decision.kind)
                val enrichedCategory = mergedDedupCategory(survivor.category, dropped.category)
                val rawPreferredNote = when {
                    survivor.note.isNullOrBlank() && !dropped.note.isNullOrBlank() -> dropped.note
                    decision.kind == DedupKind.SAME_BANK &&
                        survivor.note?.contains("Premier", ignoreCase = true) == true &&
                        dropped.note?.contains("HSBC", ignoreCase = true) == true &&
                        !isUnknownOrGenericMerchant(dropped.merchant, preferredBank) -> dropped.note
                    else -> survivor.note
                }
                val enrichedNote = rawPreferredNote?.replace(premierPrefix, "Captured from $preferredBank")

                survivor.accountId = targetAccountId
                survivor.source = preferredBank
                survivor.merchant = enrichedMerchant
                survivor.category = enrichedCategory
                survivor.note = enrichedNote

                // Type and amount are never changed: both rows already have the same type and amount.
                db.execSQL(
                    "UPDATE transactions SET accountId = ?, source = ?, merchant = ?, category = ?, note = ? WHERE id = ?",
                    arrayOf<Any?>(targetAccountId, preferredBank, enrichedMerchant, enrichedCategory, enrichedNote, survivor.id)
                )
                duplicateIdsToDelete.add(dropped.id)
                if (survivor === row) {
                    keptRows[keptRows.indexOfFirst { it === match }] = row
                }
            } else {
                if (isPremierOrHsbcBank(row.source) || row.note?.contains("Captured from Premier", ignoreCase = true) == true) {
                    val targetAccountId = hsbcAccountId ?: row.accountId
                    val cleanNote = row.note?.replace(premierPrefix, "Captured from HSBC")
                    if (row.source != "HSBC" || row.accountId != targetAccountId || row.note != cleanNote) {
                        row.source = "HSBC"
                        row.accountId = targetAccountId
                        row.note = cleanNote
                        db.execSQL(
                            "UPDATE transactions SET accountId = ?, source = 'HSBC', note = ? WHERE id = ?",
                            arrayOf<Any?>(targetAccountId, cleanNote, row.id)
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

    // 6. Moves between the user's own accounts already saved as two separate transactions
    //    (money out of one bank + the same amount into another within minutes, see isOwnTransferPair):
    //    both legs become Internal transfer and are excluded from totals. A matched leg carries
    //    TRANSFER_MATCH_MARKER in its note, so a pair the user re-files is never matched again.
    try {
        data class LegRow(val id: Long, val leg: TransferLeg)
        val legCursor = db.query(
            "SELECT id, amount, type, source, category, timestamp, excludeFromSpending, pairedTransactionId, note " +
                "FROM transactions WHERE type IN ('EXPENSE', 'INCOME') AND excludeFromSpending = 0 " +
                "AND pairedTransactionId IS NULL AND note LIKE 'Captured from %' ORDER BY timestamp ASC, id ASC"
        )
        val legs = mutableListOf<LegRow>()
        while (legCursor.moveToNext()) {
            val type = runCatching { TransactionType.valueOf(legCursor.getString(2)) }.getOrNull() ?: continue
            legs.add(
                LegRow(
                    legCursor.getLong(0),
                    TransferLeg(
                        amountMinor = kotlin.math.abs(legCursor.getLong(1)),
                        type = type,
                        source = if (legCursor.isNull(3)) null else legCursor.getString(3),
                        category = if (legCursor.isNull(4)) null else legCursor.getString(4),
                        timestamp = legCursor.getLong(5),
                        note = if (legCursor.isNull(8)) null else legCursor.getString(8)
                    )
                )
            )
        }
        legCursor.close()
        val used = mutableSetOf<Long>()
        for ((_, sameAmount) in legs.groupBy { it.leg.amountMinor }) {
            if (sameAmount.size < 2) continue
            for (a in sameAmount) {
                if (a.id in used) continue
                val b = sameAmount
                    .filter { it.id != a.id && it.id !in used && isOwnTransferPair(a.leg, it.leg) }
                    .minByOrNull { kotlin.math.abs(it.leg.timestamp - a.leg.timestamp) } ?: continue
                used.add(a.id)
                used.add(b.id)
                for ((self, other) in listOf(a to b, b to a)) {
                    db.execSQL(
                        "UPDATE transactions SET category = 'INTERNAL_TRANSFER', excludeFromSpending = 1, note = ? WHERE id = ?",
                        arrayOf<Any?>(transferMatchNote(self.leg.note, other.leg.source, other.leg.type), self.id)
                    )
                }
            }
        }
    } catch (e: Exception) {
        android.util.Log.w("AppDatabase", "Match internal transfers notice: ${e.message}")
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
        MerchantRuleEntity::class,
        BudgetEntity::class,
        RecurringItemEntity::class,
        GoalEntity::class,
        GoalContributionEntity::class
    ],
    version = 8,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun accountDao(): AccountDao
    abstract fun catalogDao(): CatalogDao
    abstract fun planningDao(): PlanningDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "spend_tracker_room.db"
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
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
