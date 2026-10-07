package com.spendtracker.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

data class BucketTotal(val bucket: String, val total: Long)
data class CategoryTotal(val category: String, val total: Long)
data class TransferIds(val sourceId: Long, val destinationId: Long)

private const val SPEND_WHERE =
    "type = 'EXPENSE' AND excludeFromSpending = 0 AND timestamp >= :start AND timestamp < :end"

@Dao
abstract class TransactionDao {

    @Insert
    abstract suspend fun insert(transaction: TransactionEntity): Long

    @Query("UPDATE transactions SET pairedTransactionId = :pairedId WHERE id = :id")
    abstract suspend fun setPairedId(id: Long, pairedId: Long)

    /** Deleting either leg of a transfer removes both, in one statement. */
    @Query("DELETE FROM transactions WHERE id = :id OR pairedTransactionId = :id")
    abstract suspend fun deleteWithPair(id: Long): Int

    /**
     * Atomically writes both legs of an internal transfer and cross-links them.
     * Source: -amount, Destination: +amount, both TRANSFER and excludeFromSpending.
     */
    @Transaction
    open suspend fun insertTransferPair(
        sourceAccountId: Long,
        destinationAccountId: Long,
        amountMinor: Long,
        timestamp: Long,
        note: String?
    ): TransferIds {
        require(amountMinor > 0) { "Transfer amount must be positive" }
        require(sourceAccountId != destinationAccountId) { "Source and destination must differ" }

        val sourceId = insert(
            TransactionEntity(
                accountId = sourceAccountId,
                amount = -amountMinor,
                category = null,
                note = note,
                timestamp = timestamp,
                type = TransactionType.TRANSFER,
                destinationAccountId = destinationAccountId,
                pairedTransactionId = null,
                excludeFromSpending = true
            )
        )
        val destinationId = insert(
            TransactionEntity(
                accountId = destinationAccountId,
                amount = amountMinor,
                category = null,
                note = note,
                timestamp = timestamp,
                type = TransactionType.TRANSFER,
                destinationAccountId = destinationAccountId,
                pairedTransactionId = sourceId,
                excludeFromSpending = true
            )
        )
        setPairedId(sourceId, destinationId)
        return TransferIds(sourceId, destinationId)
    }

    @Query("SELECT COALESCE(-SUM(amount), 0) FROM transactions WHERE $SPEND_WHERE")
    abstract fun observeTotalSpend(start: Long, end: Long): Flow<Long>

    @Query(
        "SELECT strftime('%Y-%m-%d', timestamp / 1000, 'unixepoch', 'localtime') AS bucket, " +
            "-SUM(amount) AS total FROM transactions WHERE " + SPEND_WHERE +
            " GROUP BY bucket ORDER BY bucket"
    )
    abstract fun observeDailyTotals(start: Long, end: Long): Flow<List<BucketTotal>>

    @Query(
        "SELECT strftime('%Y-%m', timestamp / 1000, 'unixepoch', 'localtime') AS bucket, " +
            "-SUM(amount) AS total FROM transactions WHERE " + SPEND_WHERE +
            " GROUP BY bucket ORDER BY bucket"
    )
    abstract fun observeMonthlyTotals(start: Long, end: Long): Flow<List<BucketTotal>>

    @Query(
        "SELECT COALESCE(category, 'OTHER_EXPENSE') AS category, -SUM(amount) AS total " +
            "FROM transactions WHERE " + SPEND_WHERE +
            " GROUP BY COALESCE(category, 'OTHER_EXPENSE') ORDER BY total DESC"
    )
    abstract fun observeCategoryTotals(start: Long, end: Long): Flow<List<CategoryTotal>>

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    abstract suspend fun getAllTransactions(): List<TransactionEntity>

    @Query("DELETE FROM transactions")
    abstract suspend fun deleteAll(): Int

    @Insert
    abstract suspend fun insertAll(transactions: List<TransactionEntity>)
}
