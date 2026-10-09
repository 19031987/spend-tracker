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
    "type = 'EXPENSE' AND excludeFromSpending = 0 AND (category IS NULL OR category != 'INTERNAL_TRANSFER') AND timestamp >= :start AND timestamp < :end"

@Dao
abstract class TransactionDao {

    @Insert
    abstract suspend fun insert(transaction: TransactionEntity): Long

    @androidx.room.Update
    abstract suspend fun update(transaction: TransactionEntity)

    @Query("SELECT * FROM transactions WHERE id = :id")
    abstract suspend fun getById(id: Long): TransactionEntity?

    /**
     * Time-window fingerprinting deduplication:
     * isDuplicate = exists where |t_new - t_existing| <= delta_t AND amount matches AND source bank matches.
     */
    @Query(
        """
        SELECT * FROM transactions
        WHERE ABS(amount) = :absAmountMinor
          AND (LOWER(source) = LOWER(:sourceName) OR accountId = :accountId)
          AND ABS(timestamp - :timestamp) <= :windowMillis
        ORDER BY ABS(timestamp - :timestamp) ASC
        LIMIT 1
        """
    )
    abstract suspend fun findDuplicate(
        absAmountMinor: Long,
        sourceName: String,
        accountId: Long,
        timestamp: Long,
        windowMillis: Long = 60_000L
    ): TransactionEntity?

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
        note: String?,
        sourceName: String? = null,
        destinationName: String? = null
    ): TransferIds {
        require(amountMinor > 0) { "Transfer amount must be positive" }
        require(sourceAccountId != destinationAccountId) { "Source and destination must differ" }

        val desc = "${sourceName ?: "Chase"} ➔ ${destinationName ?: "HSBC"}"
        val sourceId = insert(
            TransactionEntity(
                accountId = sourceAccountId,
                amount = -amountMinor,
                category = "INTERNAL_TRANSFER",
                note = note,
                timestamp = timestamp,
                type = TransactionType.TRANSFER,
                destinationAccountId = destinationAccountId,
                pairedTransactionId = null,
                excludeFromSpending = true,
                merchant = desc,
                source = sourceName ?: "Chase"
            )
        )
        val destinationId = insert(
            TransactionEntity(
                accountId = destinationAccountId,
                amount = amountMinor,
                category = "INTERNAL_TRANSFER",
                note = note,
                timestamp = timestamp,
                type = TransactionType.TRANSFER,
                destinationAccountId = destinationAccountId,
                pairedTransactionId = sourceId,
                excludeFromSpending = true,
                merchant = desc,
                source = destinationName ?: "HSBC"
            )
        )
        setPairedId(sourceId, destinationId)
        return TransferIds(sourceId, destinationId)
    }

    @Transaction
    open suspend fun updateTransferPair(
        sourceTxId: Long,
        sourceAccountId: Long,
        destinationAccountId: Long,
        amountMinor: Long,
        note: String?,
        sourceName: String?,
        destinationName: String?
    ) {
        val existingSource = getById(sourceTxId) ?: return
        val pairedId = existingSource.pairedTransactionId

        val desc = "${sourceName ?: "Chase"} ➔ ${destinationName ?: "HSBC"}"
        val updatedSource = existingSource.copy(
            accountId = sourceAccountId,
            destinationAccountId = destinationAccountId,
            amount = -amountMinor,
            category = "INTERNAL_TRANSFER",
            note = note,
            type = TransactionType.TRANSFER,
            excludeFromSpending = true,
            merchant = desc,
            source = sourceName ?: "Chase"
        )
        update(updatedSource)

        if (pairedId != null) {
            val existingDest = getById(pairedId)
            if (existingDest != null) {
                val updatedDest = existingDest.copy(
                    accountId = destinationAccountId,
                    destinationAccountId = destinationAccountId,
                    amount = amountMinor,
                    category = "INTERNAL_TRANSFER",
                    note = note,
                    type = TransactionType.TRANSFER,
                    excludeFromSpending = true,
                    merchant = desc,
                    source = destinationName ?: "HSBC"
                )
                update(updatedDest)
            }
        } else {
            val destId = insert(
                TransactionEntity(
                    accountId = destinationAccountId,
                    amount = amountMinor,
                    category = "INTERNAL_TRANSFER",
                    note = note,
                    timestamp = existingSource.timestamp,
                    type = TransactionType.TRANSFER,
                    destinationAccountId = destinationAccountId,
                    pairedTransactionId = sourceTxId,
                    excludeFromSpending = true,
                    merchant = desc,
                    source = destinationName ?: "HSBC"
                )
            )
            setPairedId(sourceTxId, destId)
        }
    }

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    abstract fun observeAll(): Flow<List<TransactionEntity>>

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
