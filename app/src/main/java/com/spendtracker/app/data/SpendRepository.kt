package com.spendtracker.app.data

import com.spendtracker.app.domain.AnalyticsSnapshot
import com.spendtracker.app.domain.CategoryShare
import com.spendtracker.app.domain.ChartBucketing
import com.spendtracker.app.domain.ExportFormatter
import com.spendtracker.app.domain.ExportRow
import com.spendtracker.app.domain.ExportRule
import com.spendtracker.app.domain.HistoryEntry
import com.spendtracker.app.domain.NewTransaction
import com.spendtracker.app.domain.Period
import com.spendtracker.app.domain.PeriodCalculator
import com.spendtracker.app.domain.TimeRange
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale

class SpendRepository(
    private val transactionDao: TransactionDao,
    private val accountDao: AccountDao,
    private val catalogDao: CatalogDao,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val firstDayOfWeek: DayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek,
    private val locale: Locale = Locale.getDefault()
) {

    fun observeAccounts(): Flow<List<AccountEntity>> = accountDao.observeAll()
    fun observeGroups(): Flow<List<CategoryGroupEntity>> = catalogDao.observeGroups()
    fun observeCategories(): Flow<List<CategoryEntity>> = catalogDao.observeCategories()
    fun observeRules(): Flow<List<MerchantRuleEntity>> = catalogDao.observeRules()
    fun observeHistory(): Flow<List<HistoryEntry>> = catalogDao.observeHistory().map { rows ->
        rows.map { HistoryEntry(it.merchant, it.category, it.uses) }
    }

    /** Throws [IllegalArgumentException] on invalid input. */
    suspend fun add(transaction: NewTransaction) {
        when (transaction) {
            is NewTransaction.Entry -> {
                require(transaction.amountMinor > 0) { "Amount must be greater than zero" }
                require(transaction.type != TransactionType.TRANSFER) {
                    "Use a Transfer for moving money between accounts"
                }
                val chosenCategory = transaction.categoryKey
                    ?: transaction.category?.name
                    ?: "OTHER_EXPENSE"
                val signed =
                    if (transaction.type == TransactionType.EXPENSE) -transaction.amountMinor
                    else transaction.amountMinor
                transactionDao.insert(
                    TransactionEntity(
                        accountId = transaction.accountId,
                        amount = signed,
                        category = chosenCategory,
                        note = transaction.note?.trim()?.takeIf { it.isNotEmpty() },
                        timestamp = transaction.timestamp,
                        type = transaction.type,
                        excludeFromSpending = false,
                        merchant = transaction.merchant?.trim()?.takeIf { it.isNotEmpty() }
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

    suspend fun addRule(matchType: String, pattern: String, categoryKey: String): Long {
        return catalogDao.insertRule(
            MerchantRuleEntity(
                matchType = matchType,
                pattern = pattern.trim(),
                categoryKey = categoryKey,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteRule(id: Long) {
        catalogDao.deleteRule(id)
    }

    suspend fun createCategoryWithRule(
        name: String,
        groupId: Long?,
        newGroupName: String?,
        emoji: String,
        colorHex: String,
        type: TransactionType,
        alwaysMatchMerchant: String?
    ) {
        val newGroup = if (groupId == null && !newGroupName.isNullOrBlank()) {
            val maxOrder = catalogDao.maxGroupOrder()
            CategoryGroupEntity(
                name = newGroupName.trim(),
                emoji = emoji,
                sortOrder = maxOrder + 1,
                isBuiltIn = false
            )
        } else null

        val targetGroupId = groupId ?: 1L
        val categoryKey = "c_" + System.currentTimeMillis()
        val category = CategoryEntity(
            key = categoryKey,
            groupId = targetGroupId,
            name = name.trim(),
            emoji = emoji,
            colorHex = colorHex,
            type = type,
            isBuiltIn = false,
            isHidden = false
        )

        val rule = if (!alwaysMatchMerchant.isNullOrBlank()) {
            MerchantRuleEntity(
                matchType = "CONTAINS",
                pattern = alwaysMatchMerchant.trim(),
                categoryKey = categoryKey,
                createdAt = System.currentTimeMillis()
            )
        } else null

        catalogDao.insertCategoryWithRule(newGroup, category, rule)
    }

    suspend fun deleteCategory(key: String, hide: Boolean = true) {
        val existing = catalogDao.getCategories().firstOrNull { it.key == key }
        if (existing != null) {
            catalogDao.removeCategory(key, hide && existing.isBuiltIn, existing)
        }
    }

    suspend fun resetDemoData() {
        catalogDao.resetCatalog()
        transactionDao.deleteAll()
        val now = System.currentTimeMillis()
        val day = 86_400_000L
        val initialSeeds = listOf(
            TransactionEntity(
                accountId = 1,
                amount = -1480,
                category = "GROCERIES",
                merchant = "Tesco Express",
                note = "Weekly lunch items",
                timestamp = now - 3_600_000L,
                type = TransactionType.EXPENSE
            ),
            TransactionEntity(
                accountId = 1,
                amount = -420,
                category = "DINING",
                merchant = "Costa Coffee",
                note = "Flat white",
                timestamp = now - 18_000_000L,
                type = TransactionType.EXPENSE
            ),
            TransactionEntity(
                accountId = 1,
                amount = -1099,
                category = "SUBSCRIPTIONS",
                merchant = "Netflix Subscription",
                note = null,
                timestamp = now - day * 2,
                type = TransactionType.EXPENSE
            ),
            TransactionEntity(
                accountId = 1,
                amount = -1850,
                category = "TRANSPORT",
                merchant = "Uber Ride",
                note = "Airport terminal",
                timestamp = now - day * 3,
                type = TransactionType.EXPENSE
            ),
            TransactionEntity(
                accountId = 1,
                amount = -6500,
                category = "UTILITIES",
                merchant = "British Gas DD",
                note = "Monthly statement",
                timestamp = now - day * 5,
                type = TransactionType.EXPENSE
            ),
            TransactionEntity(
                accountId = 1,
                amount = -3250,
                category = null,
                merchant = "Apex Hardware",
                note = "Lightbulbs & paint",
                timestamp = now - day * 6,
                type = TransactionType.EXPENSE
            ),
            TransactionEntity(
                accountId = 1,
                amount = 285000,
                category = "SALARY",
                merchant = "TechCorp Payroll",
                note = "Salary credit",
                timestamp = now - day * 7,
                type = TransactionType.INCOME
            )
        )
        transactionDao.insertAll(initialSeeds)
        // Add a demo transfer:
        transactionDao.insertTransferPair(
            sourceAccountId = 1,
            destinationAccountId = 2,
            amountMinor = 25000,
            timestamp = now - day,
            note = "Savings buffer"
        )
    }

    suspend fun exportJson(): String {
        val txs = transactionDao.getAllTransactions()
        val accounts = accountDao.observeAll()
        val categories = catalogDao.getCategories().associateBy { it.key }
        val groups = catalogDao.getGroups().associateBy { it.id }
        val rules = catalogDao.getRules()

        val dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault())

        val rows = txs.map { t ->
            val cat = categories[t.category]
            val grp = cat?.let { groups[it.groupId] }
            ExportRow(
                id = t.id,
                date = dtf.format(Instant.ofEpochMilli(t.timestamp)),
                type = t.type.name,
                account = "Account #${t.accountId}",
                merchant = t.merchant,
                category = cat?.name ?: "Uncategorized",
                group = grp?.name ?: "Other",
                amountMinor = t.amount,
                note = t.note
            )
        }
        val exportRules = rules.map { r ->
            val cat = categories[r.categoryKey]
            ExportRule(r.matchType, r.pattern, cat?.name ?: r.categoryKey)
        }
        return ExportFormatter.toJson(dtf.format(Instant.now()), rows, exportRules)
    }

    suspend fun exportCsv(): String {
        val txs = transactionDao.getAllTransactions()
        val categories = catalogDao.getCategories().associateBy { it.key }
        val groups = catalogDao.getGroups().associateBy { it.id }

        val dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault())

        val rows = txs.map { t ->
            val cat = categories[t.category]
            val grp = cat?.let { groups[it.groupId] }
            ExportRow(
                id = t.id,
                date = dtf.format(Instant.ofEpochMilli(t.timestamp)),
                type = t.type.name,
                account = "Account #${t.accountId}",
                merchant = t.merchant,
                category = cat?.name ?: "Uncategorized",
                group = grp?.name ?: "Other",
                amountMinor = t.amount,
                note = t.note
            )
        }
        return ExportFormatter.toCsv(rows)
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
