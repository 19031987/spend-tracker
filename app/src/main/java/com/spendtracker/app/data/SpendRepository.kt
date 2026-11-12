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

    fun observeAccounts(): Flow<List<AccountEntity>> = accountDao.observeAll().map { list ->
        list.map { it.copy(name = normalizeBankName(it.name)) }
            .distinctBy { it.name.lowercase(Locale.ROOT) }
            .sortedBy { it.name }
    }
    fun observeGroups(): Flow<List<CategoryGroupEntity>> = catalogDao.observeGroups()
    fun observeCategories(): Flow<List<CategoryEntity>> = catalogDao.observeCategories()
    fun observeRules(): Flow<List<MerchantRuleEntity>> = catalogDao.observeRules()
    fun observeHistory(): Flow<List<HistoryEntry>> = catalogDao.observeHistory().map { rows ->
        rows.map { HistoryEntry(it.merchant, it.category, it.uses) }
    }

    fun observeTransactions(): Flow<List<TransactionEntity>> = transactionDao.observeAll()

    fun observeTransactionItems(): Flow<List<com.spendtracker.app.domain.TransactionItem>> = combine(
        transactionDao.observeAll(),
        accountDao.observeAllRaw(),
        catalogDao.observeCategories()
    ) { txs, accounts, cats ->
        val rawAccMap = accounts.associateBy { it.id }
        val dedupAccounts = accounts
            .map { it.copy(name = normalizeBankName(it.name)) }
            .sortedBy { if (it.name.equals("HSBC", ignoreCase = true) || it.name.equals("Chase", ignoreCase = true)) 0 else 1 }
            .distinctBy { it.name.lowercase(Locale.ROOT) }
        val canonicalIdMap = dedupAccounts.associateBy { it.id }
        val canonicalNameMap = dedupAccounts.associateBy { it.name.trim().lowercase(Locale.ROOT) }
        val catMap = cats.associateBy { it.key }

        val mappedItems = txs.map { tx ->
            val isTransfer = tx.type == TransactionType.TRANSFER || tx.category == "INTERNAL_TRANSFER"
            val rawAcc = rawAccMap[tx.accountId]
            val rawBankFromAcc = rawAcc?.name?.let { normalizeBankName(it) }
            val normTxSource = normalizeBankName(tx.source)
            val normSource = when {
                isPremierOrHsbcBank(tx.source) || isPremierOrHsbcBank(rawAcc?.name) -> "HSBC"
                normTxSource != "Others" -> normTxSource
                rawBankFromAcc != null && rawBankFromAcc != "Others" -> rawBankFromAcc
                else -> normTxSource
            }
            val acc = canonicalIdMap[tx.accountId]
                ?: rawBankFromAcc?.let { canonicalNameMap[it.lowercase(Locale.ROOT)] }
                ?: canonicalNameMap[normSource.lowercase(Locale.ROOT)]

            val rawDest = tx.destinationAccountId?.let { rawAccMap[it] }
            val destAcc = tx.destinationAccountId?.let { canonicalIdMap[it] }
                ?: rawDest?.let { canonicalNameMap[normalizeBankName(it.name).lowercase(Locale.ROOT)] }

            val fallbackCatKey = if (tx.type == TransactionType.INCOME) "OTHER_INCOME" else "OTHER_EXPENSE"
            val cat = catMap[tx.category] ?: catMap[fallbackCatKey]
            val cleanMerchant = tx.merchant?.takeIf { !isUnknownOrGenericMerchant(it, normSource) }

            com.spendtracker.app.domain.TransactionItem(
                id = tx.id,
                type = tx.type,
                amountMinor = kotlin.math.abs(tx.amount),
                accountId = acc?.id ?: tx.accountId,
                accountName = acc?.name ?: normSource,
                destinationAccountId = destAcc?.id ?: tx.destinationAccountId,
                destinationAccountName = destAcc?.name,
                categoryKey = cat?.key ?: fallbackCatKey,
                categoryName = cat?.name ?: if (tx.type == TransactionType.INCOME) "Other income" else "Other",
                categoryEmoji = cat?.emoji ?: if (tx.type == TransactionType.INCOME) "💰" else "🏷️",
                categoryColorHex = cat?.colorHex ?: "#E2E8F0",
                merchant = cleanMerchant,
                note = tx.note,
                source = acc?.name ?: normSource,
                timestamp = tx.timestamp,
                excludeFromSpending = tx.excludeFromSpending || isTransfer,
                needsReview = tx.needsReview
            )
        }

        val deduped = mutableListOf<com.spendtracker.app.domain.TransactionItem>()
        for (item in mappedItems) {
            fun matchesCandidate(existing: com.spendtracker.app.domain.TransactionItem, requireBothKnown: Boolean): Boolean {
                if (existing.amountMinor != item.amountMinor) return false
                if (!isSameOrEquivalentBank(existing.source, item.source) || !isPremierOrHsbcBank(existing.source)) return false
                if (kotlin.math.abs(existing.timestamp - item.timestamp) > 180_000L) return false
                val bothKnown = !isUnknownOrGenericMerchant(existing.merchant, "HSBC") && !isUnknownOrGenericMerchant(item.merchant, "HSBC")
                if (requireBothKnown && !bothKnown) return false
                return isMerchantDuplicate(existing.merchant, item.merchant, "HSBC")
            }
            val matchIdx = deduped.indexOfFirst { matchesCandidate(it, requireBothKnown = true) }
                .takeIf { it >= 0 }
                ?: deduped.indexOfFirst { matchesCandidate(it, requireBothKnown = false) }
            if (matchIdx >= 0) {
                val existing = deduped[matchIdx]
                val bestMerchant = existing.merchant ?: item.merchant
                val existingCatDefault = existing.categoryKey == "OTHER_EXPENSE" || existing.categoryKey == "OTHER_INCOME"
                val itemCatSpecific = item.categoryKey != "OTHER_EXPENSE" && item.categoryKey != "OTHER_INCOME"
                deduped[matchIdx] = existing.copy(
                    accountName = "HSBC",
                    source = "HSBC",
                    merchant = bestMerchant,
                    categoryKey = if (existingCatDefault && itemCatSpecific) item.categoryKey else existing.categoryKey,
                    categoryName = if (existingCatDefault && itemCatSpecific) item.categoryName else existing.categoryName,
                    categoryEmoji = if (existingCatDefault && itemCatSpecific) item.categoryEmoji else existing.categoryEmoji,
                    categoryColorHex = if (existingCatDefault && itemCatSpecific) item.categoryColorHex else existing.categoryColorHex,
                    needsReview = existing.needsReview || item.needsReview
                )
            } else {
                deduped.add(item)
            }
        }
        deduped
    }

    /** Throws [IllegalArgumentException] on invalid input. */
    suspend fun add(transaction: NewTransaction) {
        when (transaction) {
            is NewTransaction.Entry -> {
                require(transaction.amountMinor > 0) { "Amount must be greater than zero" }
                val rawAccounts = accountDao.getAllRaw().associateBy { it.id }
                val accName = normalizeBankName(rawAccounts[transaction.accountId]?.name ?: "Chase")
                val canonicalAccount = accountDao.getOrCreate(accName)
                val targetAccountId = canonicalAccount.id
                val isTransferCat = transaction.categoryKey == "INTERNAL_TRANSFER" || transaction.category == TransactionCategory.INTERNAL_TRANSFER
                val isInternalTransfer = transaction.type == TransactionType.TRANSFER || isTransferCat
                val defaultCatKey = if (transaction.type == TransactionType.INCOME) "OTHER_INCOME" else "OTHER_EXPENSE"
                val chosenCategory = if (isInternalTransfer) "INTERNAL_TRANSFER"
                    else transaction.categoryKey ?: transaction.category?.name ?: defaultCatKey
                val txType = if (isInternalTransfer) TransactionType.TRANSFER else transaction.type
                val signed = if (txType == TransactionType.EXPENSE) -transaction.amountMinor else transaction.amountMinor
                val cleanMerchant = transaction.merchant?.trim()?.takeIf {
                    it.isNotEmpty() && !isUnknownOrGenericMerchant(it, accName)
                }

                val existingDup = if (!isInternalTransfer) {
                    transactionDao.findMatchingDuplicate(
                        absAmountMinor = transaction.amountMinor,
                        sourceName = accName,
                        accountId = targetAccountId,
                        timestamp = transaction.timestamp,
                        merchant = cleanMerchant,
                        windowMillis = 60_000L
                    )
                } else null

                if (existingDup != null) {
                    val preferredBank = resolvePreferredBankSource(existingDup.source, accName)
                    val preferredAccount = if (preferredBank.equals(accName, ignoreCase = true)) {
                        canonicalAccount
                    } else {
                        accountDao.getOrCreate(preferredBank)
                    }
                    val enrichedMerchant = cleanMerchant ?: existingDup.merchant?.takeIf {
                        !isUnknownOrGenericMerchant(it, preferredBank)
                    }
                    transactionDao.update(
                        existingDup.copy(
                            accountId = preferredAccount.id,
                            source = preferredAccount.name,
                            merchant = enrichedMerchant,
                            category = if (existingDup.category.isNullOrBlank() || existingDup.category == "OTHER_EXPENSE" || existingDup.category == "OTHER_INCOME") chosenCategory else existingDup.category,
                            note = transaction.note?.trim()?.takeIf { it.isNotEmpty() } ?: existingDup.note
                        )
                    )
                } else {
                    transactionDao.insert(
                        TransactionEntity(
                            accountId = targetAccountId,
                            amount = signed,
                            category = chosenCategory,
                            note = transaction.note?.trim()?.takeIf { it.isNotEmpty() },
                            timestamp = transaction.timestamp,
                            type = txType,
                            excludeFromSpending = isInternalTransfer,
                            merchant = cleanMerchant,
                            source = canonicalAccount.name
                        )
                    )
                }
            }
            is NewTransaction.Transfer -> {
                val rawAccounts = accountDao.getAllRaw().associateBy { it.id }
                val srcName = normalizeBankName(transaction.sourceName ?: rawAccounts[transaction.sourceAccountId]?.name ?: "Chase")
                val dstName = normalizeBankName(transaction.destinationName ?: rawAccounts[transaction.destinationAccountId]?.name ?: "HSBC")
                transactionDao.insertTransferPair(
                    sourceAccountId = transaction.sourceAccountId,
                    destinationAccountId = transaction.destinationAccountId,
                    amountMinor = transaction.amountMinor,
                    timestamp = transaction.timestamp,
                    note = transaction.note?.trim()?.takeIf { it.isNotEmpty() },
                    sourceName = srcName,
                    destinationName = dstName
                )
            }
        }
    }

    suspend fun updateTransaction(
        id: Long,
        type: TransactionType,
        accountId: Long,
        destinationAccountId: Long?,
        amountMinor: Long,
        categoryKey: String?,
        merchant: String?,
        note: String?,
        excludeFromSpending: Boolean
    ) {
        val rawAccounts = accountDao.getAllRaw().associateBy { it.id }
        val srcName = normalizeBankName(rawAccounts[accountId]?.name ?: "Chase")
        val canonicalSrc = accountDao.getOrCreate(srcName)
        val dstName = normalizeBankName(destinationAccountId?.let { rawAccounts[it]?.name } ?: "HSBC")
        val isTransfer = type == TransactionType.TRANSFER || categoryKey == "INTERNAL_TRANSFER"

        if (isTransfer) {
            val targetDestId = destinationAccountId ?: rawAccounts.values.firstOrNull { it.id != canonicalSrc.id }?.id ?: canonicalSrc.id
            transactionDao.updateTransferPair(
                sourceTxId = id,
                sourceAccountId = canonicalSrc.id,
                destinationAccountId = targetDestId,
                amountMinor = amountMinor,
                note = note?.trim()?.takeIf { it.isNotEmpty() },
                sourceName = canonicalSrc.name,
                destinationName = dstName
            )
        } else {
            val existing = transactionDao.getById(id) ?: return
            if (existing.pairedTransactionId != null) {
                transactionDao.deleteWithPair(existing.pairedTransactionId)
            }
            val signed = if (type == TransactionType.EXPENSE) -amountMinor else amountMinor
            transactionDao.update(
                existing.copy(
                    accountId = canonicalSrc.id,
                    amount = signed,
                    category = categoryKey ?: "OTHER_EXPENSE",
                    note = note?.trim()?.takeIf { it.isNotEmpty() },
                    type = type,
                    destinationAccountId = null,
                    pairedTransactionId = null,
                    excludeFromSpending = excludeFromSpending,
                    merchant = merchant?.trim()?.takeIf { it.isNotEmpty() },
                    source = canonicalSrc.name
                )
            )
        }
    }

    suspend fun delete(transactionId: Long) {
        transactionDao.deleteWithPair(transactionId)
    }

    suspend fun setNeedsReview(id: Long, value: Boolean) {
        transactionDao.setNeedsReview(id, value)
    }

    suspend fun markAllReviewed() {
        transactionDao.markAllReviewed()
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

    // ---- Category / group / rule editing ----

    /** Updates name, emoji, colour and group. Key, type and built-in flag never change. */
    suspend fun updateCategory(key: String, name: String, emoji: String, colorHex: String, groupId: Long) {
        require(name.isNotBlank()) { "Name can't be empty" }
        val existing = catalogDao.getCategories().firstOrNull { it.key == key } ?: return
        catalogDao.updateCategory(
            existing.copy(
                name = name.trim(),
                emoji = emoji.trim().ifEmpty { existing.emoji },
                colorHex = colorHex,
                groupId = groupId
            )
        )
    }

    suspend fun restoreCategory(key: String) {
        val existing = catalogDao.getCategories().firstOrNull { it.key == key } ?: return
        if (existing.isHidden) catalogDao.updateCategory(existing.copy(isHidden = false))
    }

    suspend fun updateGroup(id: Long, name: String, emoji: String) {
        require(name.isNotBlank()) { "Name can't be empty" }
        val existing = catalogDao.getGroups().firstOrNull { it.id == id } ?: return
        catalogDao.updateGroup(existing.copy(name = name.trim(), emoji = emoji.trim().ifEmpty { existing.emoji }))
    }

    /** Deletes a user group, moving its categories (hidden ones too) to [moveToGroupId]. Built-in groups stay. */
    suspend fun deleteGroup(id: Long, moveToGroupId: Long) {
        val groups = catalogDao.getGroups()
        val group = groups.firstOrNull { it.id == id } ?: return
        require(!group.isBuiltIn) { "Built-in groups can't be deleted" }
        require(id != moveToGroupId && groups.any { it.id == moveToGroupId }) { "Choose another group" }
        catalogDao.removeGroup(id, moveToGroupId)
    }

    suspend fun updateRule(id: Long, matchType: String, pattern: String, categoryKey: String) {
        require(pattern.isNotBlank()) { "Pattern can't be empty" }
        val existing = catalogDao.getRules().firstOrNull { it.id == id } ?: return
        catalogDao.updateRule(existing.copy(matchType = matchType, pattern = pattern.trim(), categoryKey = categoryKey))
    }

    // ---- Accounts ----

    /** Transaction count per (raw) account id. */
    fun observeAccountTransactionCounts(): Flow<Map<Long, Int>> =
        accountDao.observeTransactionCounts().map { rows -> rows.associate { it.accountId to it.count } }

    suspend fun validateAccountName(name: String, editingId: Long? = null): AccountNameCheck =
        checkAccountName(name, accountDao.getAll(), editingId)

    /** Throws [IllegalArgumentException] with a user-facing message when the name is not allowed. */
    suspend fun addAccount(name: String): Long {
        val check = validateAccountName(name)
        if (check is AccountNameCheck.Invalid) throw IllegalArgumentException(check.reason)
        val id = accountDao.insertRaw(AccountEntity(name = (check as AccountNameCheck.Valid).name))
        require(id > 0) { "An account with that name already exists" }
        return id
    }

    suspend fun renameAccount(id: Long, newName: String) {
        val current = accountDao.getAllRaw().firstOrNull { it.id == id } ?: return
        require(!isProtectedAccount(current.name)) { "${normalizeBankName(current.name)} is a built-in account and can't be renamed" }
        val check = validateAccountName(newName, id)
        if (check is AccountNameCheck.Invalid) throw IllegalArgumentException(check.reason)
        accountDao.rename(id, (check as AccountNameCheck.Valid).name)
    }

    suspend fun countAccountTransactions(id: Long): Int = accountDao.countTransactionsFor(id)

    /**
     * Deletes an account. Transactions are never orphaned (the launch clean-up would recreate the
     * account from their source name): when it has any, [moveToAccountId] is required.
     */
    suspend fun deleteAccount(id: Long, moveToAccountId: Long?) {
        val raw = accountDao.getAllRaw()
        val current = raw.firstOrNull { it.id == id } ?: return
        require(!isProtectedAccount(current.name)) { "${normalizeBankName(current.name)} is a built-in account and can't be deleted" }
        val count = accountDao.countTransactionsFor(id)
        if (count == 0) {
            accountDao.deleteById(id)
            return
        }
        val target = raw.firstOrNull { it.id == moveToAccountId && it.id != id }
            ?: throw IllegalArgumentException("Choose an account to move its $count transactions to")
        accountDao.moveTransactionsAndDelete(id, target.id, normalizeBankName(target.name))
    }

    suspend fun resetDemoData() {
        catalogDao.resetCatalog()
        transactionDao.deleteAll()
        val now = System.currentTimeMillis()
        val day = 86_400_000L

        val accMap = accountDao.getAll().distinctBy { it.name.trim().lowercase(Locale.ROOT) }.associateBy { it.name.trim().lowercase(Locale.ROOT) }
        val chaseId = accMap["chase"]?.id ?: 1L
        val hsbcId = accMap["hsbc"]?.id ?: 2L

        val initialSeeds = listOf(
            TransactionEntity(
                accountId = hsbcId,
                amount = -1480,
                category = "GROCERIES",
                merchant = "Tesco Express",
                note = "Weekly lunch items",
                timestamp = now - 3_600_000L,
                type = TransactionType.EXPENSE,
                source = "HSBC"
            ),
            TransactionEntity(
                accountId = hsbcId,
                amount = -420,
                category = "DINING",
                merchant = "Costa Coffee",
                note = "Flat white",
                timestamp = now - 18_000_000L,
                type = TransactionType.EXPENSE,
                source = "HSBC"
            ),
            TransactionEntity(
                accountId = chaseId,
                amount = -1099,
                category = "SUBSCRIPTIONS",
                merchant = "Netflix Subscription",
                note = null,
                timestamp = now - day * 2,
                type = TransactionType.EXPENSE,
                source = "Chase"
            ),
            TransactionEntity(
                accountId = chaseId,
                amount = -1850,
                category = "TRANSPORT",
                merchant = "Uber Ride",
                note = "Airport terminal",
                timestamp = now - day * 3,
                type = TransactionType.EXPENSE,
                source = "Chase"
            ),
            TransactionEntity(
                accountId = hsbcId,
                amount = -6500,
                category = "UTILITIES",
                merchant = "British Gas DD",
                note = "Monthly statement",
                timestamp = now - day * 5,
                type = TransactionType.EXPENSE,
                source = "HSBC"
            ),
            TransactionEntity(
                accountId = chaseId,
                amount = -3250,
                category = null,
                merchant = "Apex Hardware",
                note = "Lightbulbs & paint",
                timestamp = now - day * 6,
                type = TransactionType.EXPENSE,
                source = "Chase"
            ),
            TransactionEntity(
                accountId = chaseId,
                amount = 285000,
                category = "SALARY",
                merchant = "TechCorp Payroll",
                note = "Salary credit",
                timestamp = now - day * 7,
                type = TransactionType.INCOME,
                source = "Chase"
            )
        )
        transactionDao.insertAll(initialSeeds)
    }

    suspend fun exportJson(): String {
        val txs = transactionDao.getAllTransactions()
        val accMap = accountDao.getAll().distinctBy { it.name.trim().lowercase(Locale.ROOT) }.associateBy { it.id }
        val categories = catalogDao.getCategories().associateBy { it.key }
        val groups = catalogDao.getGroups().associateBy { it.id }
        val rules = catalogDao.getRules()

        val dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault())

        val rows = txs.map { t ->
            val cat = categories[t.category]
            val grp = cat?.let { groups[it.groupId] }
            val accName = accMap[t.accountId]?.name ?: (normalizeBankName(t.source))
            ExportRow(
                id = t.id,
                date = dtf.format(Instant.ofEpochMilli(t.timestamp)),
                type = t.type.name,
                account = accName,
                merchant = t.merchant,
                category = if (t.type == TransactionType.TRANSFER) "Internal Transfer" else cat?.name ?: "Uncategorized",
                group = if (t.type == TransactionType.TRANSFER) "Transfers" else grp?.name ?: "Other",
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
        val accMap = accountDao.getAll().distinctBy { it.name.trim().lowercase(Locale.ROOT) }.associateBy { it.id }
        val categories = catalogDao.getCategories().associateBy { it.key }
        val groups = catalogDao.getGroups().associateBy { it.id }

        val dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault())

        val rows = txs.map { t ->
            val cat = categories[t.category]
            val grp = cat?.let { groups[it.groupId] }
            val accName = accMap[t.accountId]?.name ?: (normalizeBankName(t.source))
            ExportRow(
                id = t.id,
                date = dtf.format(Instant.ofEpochMilli(t.timestamp)),
                type = t.type.name,
                account = accName,
                merchant = t.merchant,
                category = if (t.type == TransactionType.TRANSFER) "Internal Transfer" else cat?.name ?: "Uncategorized",
                group = if (t.type == TransactionType.TRANSFER) "Transfers" else grp?.name ?: "Other",
                amountMinor = t.amount,
                note = t.note
            )
        }
        return ExportFormatter.toCsv(rows)
    }

    private data class SpendWindowTotals(
        val curTotal: Long,
        val prevTotal: Long,
        val curRows: List<BucketTotal>,
        val prevRows: List<BucketTotal>
    )

    /** Windows are computed when the flow is collected, so each new collection gets a fresh "today". */
    fun observeAnalytics(period: Period): Flow<AnalyticsSnapshot> = flow {
        val today = LocalDate.now(clock)
        val currentWindow = PeriodCalculator.current(period, today, firstDayOfWeek)
        val previousWindow = PeriodCalculator.previous(currentWindow)
        val cur = currentWindow.toRange(clock.zone)
        val prev = previousWindow.toRange(clock.zone)

        val windowTotalsFlow = combine(
            transactionDao.observeTotalSpend(cur.start, cur.end),
            transactionDao.observeTotalSpend(prev.start, prev.end),
            bucketFlow(period, cur),
            bucketFlow(period, prev)
        ) { curTotal, prevTotal, curRows, prevRows ->
            SpendWindowTotals(curTotal, prevTotal, curRows, prevRows)
        }

        emitAll(
            combine(
                windowTotalsFlow,
                transactionDao.observeCategoryTotals(cur.start, cur.end),
                catalogDao.observeCategories()
            ) { windowData, cats, categoryEntities ->
                AnalyticsSnapshot(
                    period = period,
                    currentTotal = windowData.curTotal,
                    previousTotal = windowData.prevTotal,
                    bars = ChartBucketing.build(
                        currentWindow, previousWindow, windowData.curRows, windowData.prevRows, locale
                    ),
                    categories = toShares(cats, windowData.curTotal, categoryEntities)
                )
            }
        )
    }

    private fun bucketFlow(period: Period, range: TimeRange): Flow<List<BucketTotal>> =
        if (period == Period.YEAR) transactionDao.observeMonthlyTotals(range.start, range.end)
        else transactionDao.observeDailyTotals(range.start, range.end)

    internal fun toShares(
        rows: List<CategoryTotal>,
        total: Long,
        categories: List<CategoryEntity> = emptyList()
    ): List<CategoryShare> {
        if (total <= 0) return emptyList()
        val catMap = categories.associateBy { it.key }
        return rows
            .groupBy { it.category }
            .map { (catKey, group) ->
                val amount = group.sumOf { it.total }
                val catEntity = catMap[catKey]
                val builtIn = TransactionCategory.fromName(catKey)
                val categoryName = catEntity?.name
                    ?: if (builtIn != TransactionCategory.OTHER_EXPENSE) builtIn.label
                    else catKey.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
                val emoji = catEntity?.emoji ?: ""
                CategoryShare(
                    category = builtIn,
                    amountMinor = amount,
                    percent = amount * 100f / total,
                    categoryKey = catKey,
                    categoryName = categoryName,
                    categoryEmoji = emoji
                )
            }
            .sortedByDescending { it.amountMinor }
    }
}
