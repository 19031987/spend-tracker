package com.spendtracker.app.data

import androidx.room.withTransaction
import com.spendtracker.app.domain.BackupCodec
import com.spendtracker.app.domain.BackupData
import com.spendtracker.app.domain.BackupMerger
import com.spendtracker.app.domain.ImportPlan

enum class ImportMode { MERGE, REPLACE }

/** Full-backup export and import. Everything stays on the device; files go through the system file picker. */
class BackupRepository(private val db: AppDatabase) {

    private val transactionDao = db.transactionDao()
    private val accountDao = db.accountDao()
    private val catalogDao = db.catalogDao()
    private val planningDao = db.planningDao()

    /** Raw rows of every table (accounts not normalised, so ids line up with transactions). */
    suspend fun snapshot(): BackupData = db.withTransaction {
        BackupData(
            exportedAt = System.currentTimeMillis(),
            accounts = accountDao.getAllRaw(),
            groups = catalogDao.getGroups().sortedWith(compareBy({ it.sortOrder }, { it.id })),
            categories = catalogDao.getCategories(),
            rules = catalogDao.getRules(),
            transactions = transactionDao.getAllTransactions(),
            budgets = planningDao.getBudgets(),
            goals = planningDao.getGoals(),
            contributions = planningDao.getContributions(),
            recurring = planningDao.getRecurring()
        )
    }

    suspend fun exportBackupJson(): String = BackupCodec.encode(snapshot())

    /** What [import] would do, for the confirmation dialog. */
    suspend fun preview(incoming: BackupData, mode: ImportMode): ImportPlan =
        when (mode) {
            ImportMode.MERGE -> BackupMerger.plan(snapshot(), incoming)
            ImportMode.REPLACE -> BackupMerger.plan(BackupData.EMPTY, incoming)
        }

    /** Applies the import in one database transaction; on any error nothing changes. */
    suspend fun import(incoming: BackupData, mode: ImportMode): ImportPlan = db.withTransaction {
        val plan = when (mode) {
            ImportMode.MERGE -> BackupMerger.plan(snapshot(), incoming)
            ImportMode.REPLACE -> {
                transactionDao.deleteAll()
                accountDao.clearAll()
                catalogDao.clearRules()
                catalogDao.clearCategories()
                catalogDao.clearGroups()
                planningDao.clearContributions()
                planningDao.clearGoals()
                planningDao.clearBudgets()
                planningDao.clearRecurring()
                BackupMerger.plan(BackupData.EMPTY, incoming)
            }
        }
        val rows = plan.toInsert
        rows.accounts.forEach {
            // IGNORE would silently drop a clashing name and leave transactions pointing nowhere;
            // throwing rolls the whole import back.
            if (accountDao.insertRaw(it) <= 0) throw IllegalStateException("Couldn't restore account \"${it.name}\"")
        }
        if (mode == ImportMode.REPLACE && rows.groups.isEmpty() && rows.categories.isEmpty()) {
            catalogDao.insertGroups(CatalogSeed.groups)
            catalogDao.insertCategories(CatalogSeed.categories)
        } else {
            if (rows.groups.isNotEmpty()) catalogDao.insertGroups(rows.groups)
            if (rows.categories.isNotEmpty()) catalogDao.insertCategories(rows.categories)
        }
        if (rows.rules.isNotEmpty()) catalogDao.insertRules(rows.rules)
        if (rows.transactions.isNotEmpty()) transactionDao.insertAll(rows.transactions)
        if (rows.budgets.isNotEmpty()) planningDao.insertBudgets(rows.budgets)
        if (rows.goals.isNotEmpty()) planningDao.insertGoals(rows.goals)
        if (rows.contributions.isNotEmpty()) planningDao.insertContributions(rows.contributions)
        if (rows.recurring.isNotEmpty()) planningDao.insertRecurringItems(rows.recurring)
        plan
    }
}
