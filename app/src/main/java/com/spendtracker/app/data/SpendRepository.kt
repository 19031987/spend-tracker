package com.spendtracker.app.data

import com.spendtracker.app.domain.AnalyticsSnapshot
import com.spendtracker.app.domain.CategoryShare
import com.spendtracker.app.domain.ChartBucketing
import com.spendtracker.app.domain.NewTransaction
import com.spendtracker.app.domain.Period
import com.spendtracker.app.domain.PeriodCalculator
import com.spendtracker.app.domain.TimeRange
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.WeekFields
import java.util.Locale

class SpendRepository(
    private val transactionDao: TransactionDao,
    private val accountDao: AccountDao,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val firstDayOfWeek: DayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek,
    private val locale: Locale = Locale.getDefault()
) {

    fun observeAccounts(): Flow<List<AccountEntity>> = accountDao.observeAll()

    /** Throws [IllegalArgumentException] on invalid input. */
    suspend fun add(transaction: NewTransaction) {
        when (transaction) {
            is NewTransaction.Entry -> {
                require(transaction.amountMinor > 0) { "Amount must be greater than zero" }
                require(transaction.type != TransactionType.TRANSFER) {
                    "Use a Transfer for moving money between accounts"
                }
                require(transaction.category.type == transaction.type) {
                    "Category does not match transaction type"
                }
                val signed =
                    if (transaction.type == TransactionType.EXPENSE) -transaction.amountMinor
                    else transaction.amountMinor
                transactionDao.insert(
                    TransactionEntity(
                        accountId = transaction.accountId,
                        amount = signed,
                        category = transaction.category.name,
                        note = transaction.note?.trim()?.takeIf { it.isNotEmpty() },
                        timestamp = transaction.timestamp,
                        type = transaction.type,
                        excludeFromSpending = false
                    )
                )
            }
            is NewTransaction.Transfer -> {
                transactionDao.insertTransferPair(
                    sourceAccountId = transaction.sourceAccountId,
                    destinationAccountId = transaction.destinationAccountId,
                    amountMinor = transaction.amountMinor,
                    timestamp = transaction.timestamp,
                    note = transaction.note?.trim()?.takeIf { it.isNotEmpty() }
                )
            }
        }
    }

    suspend fun delete(transactionId: Long) {
        transactionDao.deleteWithPair(transactionId)
    }

    /** Windows are computed when the flow is collected, so each new collection gets a fresh "today". */
    fun observeAnalytics(period: Period): Flow<AnalyticsSnapshot> = flow {
        val today = LocalDate.now(clock)
        val currentWindow = PeriodCalculator.current(period, today, firstDayOfWeek)
        val previousWindow = PeriodCalculator.previous(currentWindow)
        val cur = currentWindow.toRange(clock.zone)
        val prev = previousWindow.toRange(clock.zone)

        emitAll(
            combine(
                transactionDao.observeTotalSpend(cur.start, cur.end),
                transactionDao.observeTotalSpend(prev.start, prev.end),
                bucketFlow(period, cur),
                bucketFlow(period, prev),
                transactionDao.observeCategoryTotals(cur.start, cur.end)
            ) { curTotal, prevTotal, curRows, prevRows, cats ->
                AnalyticsSnapshot(
                    period = period,
                    currentTotal = curTotal,
                    previousTotal = prevTotal,
                    bars = ChartBucketing.build(
                        currentWindow, previousWindow, curRows, prevRows, locale
                    ),
                    categories = toShares(cats, curTotal)
                )
            }
        )
    }

    private fun bucketFlow(period: Period, range: TimeRange): Flow<List<BucketTotal>> =
        if (period == Period.YEAR) transactionDao.observeMonthlyTotals(range.start, range.end)
        else transactionDao.observeDailyTotals(range.start, range.end)

    private fun toShares(rows: List<CategoryTotal>, total: Long): List<CategoryShare> {
        if (total <= 0) return emptyList()
        return rows
            .groupBy { TransactionCategory.fromName(it.category) }
            .map { (category, group) ->
                val amount = group.sumOf { it.total }
                CategoryShare(category, amount, amount * 100f / total)
            }
            .sortedByDescending { it.amountMinor }
    }
}
