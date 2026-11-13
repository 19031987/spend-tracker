package com.spendtracker.app.domain

import com.spendtracker.app.data.AccountEntity
import com.spendtracker.app.data.BudgetEntity
import com.spendtracker.app.data.CategoryEntity
import com.spendtracker.app.data.CategoryGroupEntity
import com.spendtracker.app.data.GoalContributionEntity
import com.spendtracker.app.data.GoalEntity
import com.spendtracker.app.data.MerchantRuleEntity
import com.spendtracker.app.data.RecurringItemEntity
import com.spendtracker.app.data.TransactionEntity
import com.spendtracker.app.data.TransactionType
import com.spendtracker.app.data.normalizeBankName
import java.util.Locale

/** Everything a full backup holds. Rows keep their database ids so references survive. */
data class BackupData(
    val exportedAt: Long,
    val accounts: List<AccountEntity>,
    val groups: List<CategoryGroupEntity>,
    val categories: List<CategoryEntity>,
    val rules: List<MerchantRuleEntity>,
    val transactions: List<TransactionEntity>,
    val budgets: List<BudgetEntity>,
    val goals: List<GoalEntity>,
    val contributions: List<GoalContributionEntity>,
    val recurring: List<RecurringItemEntity>
) {
    val counts: BackupCounts
        get() = BackupCounts(
            accounts = accounts.size,
            groups = groups.size,
            categories = categories.size,
            rules = rules.size,
            transactions = transactions.size,
            budgets = budgets.size,
            goals = goals.size,
            contributions = contributions.size,
            recurring = recurring.size
        )

    companion object {
        val EMPTY = BackupData(
            0L, emptyList(), emptyList(), emptyList(), emptyList(),
            emptyList(), emptyList(), emptyList(), emptyList(), emptyList()
        )
    }
}

data class BackupCounts(
    val accounts: Int = 0,
    val groups: Int = 0,
    val categories: Int = 0,
    val rules: Int = 0,
    val transactions: Int = 0,
    val budgets: Int = 0,
    val goals: Int = 0,
    val contributions: Int = 0,
    val recurring: Int = 0
)

/** Thrown by [BackupCodec.decode]; [message] is shown to the user as is. */
class BackupFormatException(message: String) : Exception(message)

/**
 * Full backup file: `{"format": "spendtracker-backup", "version": 1, ...}` with one array
 * per table. Pure Kotlin (no org.json, which is stubbed in JVM unit tests).
 */
object BackupCodec {
    const val FORMAT = "spendtracker-backup"
    const val VERSION = 1L

    // ---------- encode ----------

    fun encode(data: BackupData): String {
        val sb = StringBuilder("{\n")
        sb.append("  \"format\": ").append(Json.quote(FORMAT)).append(",\n")
        sb.append("  \"version\": ").append(VERSION).append(",\n")
        sb.append("  \"exportedAt\": ").append(data.exportedAt)
        fun section(name: String, rows: List<Map<String, Any?>>) {
            sb.append(",\n  ").append(Json.quote(name)).append(": [")
            rows.forEachIndexed { i, row ->
                sb.append(if (i == 0) "\n    " else ",\n    ").append(Json.stringify(row))
            }
            sb.append(if (rows.isEmpty()) "]" else "\n  ]")
        }
        section("accounts", data.accounts.map { mapOf("id" to it.id, "name" to it.name) })
        section("categoryGroups", data.groups.map {
            mapOf("id" to it.id, "name" to it.name, "emoji" to it.emoji, "sortOrder" to it.sortOrder.toLong(), "isBuiltIn" to it.isBuiltIn)
        })
        section("categories", data.categories.map {
            mapOf(
                "key" to it.key, "groupId" to it.groupId, "name" to it.name, "emoji" to it.emoji,
                "colorHex" to it.colorHex, "type" to it.type.name, "isBuiltIn" to it.isBuiltIn, "isHidden" to it.isHidden
            )
        })
        section("rules", data.rules.map {
            mapOf("id" to it.id, "matchType" to it.matchType, "pattern" to it.pattern, "categoryKey" to it.categoryKey, "createdAt" to it.createdAt)
        })
        section("transactions", data.transactions.map {
            mapOf(
                "id" to it.id, "accountId" to it.accountId, "amount" to it.amount, "category" to it.category,
                "note" to it.note, "timestamp" to it.timestamp, "type" to it.type.name,
                "destinationAccountId" to it.destinationAccountId, "pairedTransactionId" to it.pairedTransactionId,
                "excludeFromSpending" to it.excludeFromSpending, "merchant" to it.merchant, "source" to it.source,
                "needsReview" to it.needsReview
            )
        })
        section("budgets", data.budgets.map {
            mapOf("categoryKey" to it.categoryKey, "amountMinor" to it.amountMinor, "rollover" to it.rollover, "updatedAt" to it.updatedAt)
        })
        section("goals", data.goals.map {
            mapOf(
                "id" to it.id, "name" to it.name, "emoji" to it.emoji, "colorHex" to it.colorHex,
                "targetMinor" to it.targetMinor, "targetDate" to it.targetDate, "startingMinor" to it.startingMinor,
                "createdAt" to it.createdAt, "archived" to it.archived
            )
        })
        section("goalContributions", data.contributions.map {
            mapOf("id" to it.id, "goalId" to it.goalId, "amountMinor" to it.amountMinor, "timestamp" to it.timestamp, "note" to it.note)
        })
        section("recurringItems", data.recurring.map {
            mapOf(
                "id" to it.id, "merchantKey" to it.merchantKey, "displayName" to it.displayName,
                "categoryKey" to it.categoryKey, "amountMinor" to it.amountMinor, "frequency" to it.frequency,
                "anchorDate" to it.anchorDate, "status" to it.status, "isIncome" to it.isIncome, "createdAt" to it.createdAt
            )
        })
        sb.append("\n}\n")
        return sb.toString()
    }

    // ---------- decode + validate ----------

    /** Parses and validates a backup. Throws [BackupFormatException] with a user-facing message. */
    fun decode(text: String): BackupData {
        val root = try {
            Json.parse(text)
        } catch (e: BackupFormatException) {
            throw BackupFormatException("This file isn't valid JSON (${e.message}).")
        }
        if (root !is Map<*, *>) throw BackupFormatException("This file isn't a Spend Tracker backup.")
        val top = Obj(root, "backup")
        val format = root["format"]
        val version = root["version"]
        if (format == null && version == null) {
            if (root.containsKey("transactions")) {
                throw BackupFormatException(
                    "This is an older export without a version number and can't be imported. " +
                        "Only files saved with \"Export backup\" can be restored."
                )
            }
            throw BackupFormatException("This file isn't a Spend Tracker backup.")
        }
        if (format != FORMAT) throw BackupFormatException("This file isn't a Spend Tracker backup.")
        if (version !is Long) throw BackupFormatException("The backup's version number is missing or invalid.")
        if (version > VERSION) {
            throw BackupFormatException("This backup was made by a newer version of the app (format $version). Update the app first.")
        }
        if (version < VERSION) throw BackupFormatException("Backup format $version is no longer supported.")

        val accounts = top.array("accounts").map { o ->
            val name = o.string("name").trim()
            if (name.isEmpty()) o.fail("name", "must not be empty")
            AccountEntity(id = o.long("id"), name = name)
        }
        val groups = top.array("categoryGroups").map { o ->
            CategoryGroupEntity(
                id = o.long("id"), name = o.string("name"), emoji = o.string("emoji"),
                sortOrder = o.int("sortOrder"), isBuiltIn = o.bool("isBuiltIn")
            )
        }
        val categories = top.array("categories").map { o ->
            CategoryEntity(
                key = o.string("key"), groupId = o.long("groupId"), name = o.string("name"),
                emoji = o.string("emoji"), colorHex = o.string("colorHex"), type = o.txType("type"),
                isBuiltIn = o.bool("isBuiltIn"), isHidden = o.bool("isHidden")
            )
        }
        val rules = top.array("rules").map { o ->
            val match = o.string("matchType")
            if (MatchType.values().none { it.name == match }) o.fail("matchType", "unknown value \"$match\"")
            MerchantRuleEntity(
                id = o.long("id"), matchType = match, pattern = o.string("pattern"),
                categoryKey = o.string("categoryKey"), createdAt = o.long("createdAt")
            )
        }
        val transactions = top.array("transactions").map { o ->
            TransactionEntity(
                id = o.long("id"), accountId = o.long("accountId"), amount = o.long("amount"),
                category = o.stringOrNull("category"), note = o.stringOrNull("note"),
                timestamp = o.long("timestamp"), type = o.txType("type"),
                destinationAccountId = o.longOrNull("destinationAccountId"),
                pairedTransactionId = o.longOrNull("pairedTransactionId"),
                excludeFromSpending = o.bool("excludeFromSpending"),
                merchant = o.stringOrNull("merchant"), source = o.stringOrNull("source"),
                needsReview = o.bool("needsReview")
            )
        }
        val budgets = top.array("budgets").map { o ->
            BudgetEntity(
                categoryKey = o.string("categoryKey"), amountMinor = o.long("amountMinor"),
                rollover = o.bool("rollover"), updatedAt = o.long("updatedAt")
            )
        }
        val goals = top.array("goals").map { o ->
            GoalEntity(
                id = o.long("id"), name = o.string("name"), emoji = o.string("emoji"), colorHex = o.string("colorHex"),
                targetMinor = o.long("targetMinor"), targetDate = o.longOrNull("targetDate"),
                startingMinor = o.long("startingMinor"), createdAt = o.long("createdAt"), archived = o.bool("archived")
            )
        }
        val contributions = top.array("goalContributions").map { o ->
            GoalContributionEntity(
                id = o.long("id"), goalId = o.long("goalId"), amountMinor = o.long("amountMinor"),
                timestamp = o.long("timestamp"), note = o.stringOrNull("note")
            )
        }
        val recurring = top.array("recurringItems").map { o ->
            val status = o.string("status")
            if (status != "APPROVED" && status != "IGNORED") o.fail("status", "unknown value \"$status\"")
            val merchantKey = o.string("merchantKey")
            if (merchantKey.isBlank()) o.fail("merchantKey", "must not be empty")
            RecurringItemEntity(
                id = o.long("id"), merchantKey = merchantKey, displayName = o.string("displayName"),
                categoryKey = o.stringOrNull("categoryKey"), amountMinor = o.long("amountMinor"),
                frequency = o.string("frequency"), anchorDate = o.long("anchorDate"), status = status,
                isIncome = o.bool("isIncome"), createdAt = o.long("createdAt")
            )
        }

        val data = BackupData(
            exportedAt = top.long("exportedAt"),
            accounts = accounts, groups = groups, categories = categories, rules = rules,
            transactions = transactions, budgets = budgets, goals = goals,
            contributions = contributions, recurring = recurring
        )
        checkReferences(data)
        return data
    }

    private fun checkReferences(d: BackupData) {
        fun <T, K> unique(section: String, rows: List<T>, key: (T) -> K) {
            val seen = HashSet<K>()
            rows.forEach { if (!seen.add(key(it))) throw BackupFormatException("$section: duplicate entry ${key(it)}.") }
        }
        unique("accounts", d.accounts) { it.id }
        unique("categoryGroups", d.groups) { it.id }
        unique("categories", d.categories) { it.key }
        unique("rules", d.rules) { it.id }
        unique("transactions", d.transactions) { it.id }
        unique("budgets", d.budgets) { it.categoryKey }
        unique("goals", d.goals) { it.id }
        unique("goalContributions", d.contributions) { it.id }
        unique("recurringItems", d.recurring) { it.id }

        if (d.groups.isNotEmpty() && d.categories.isEmpty()) {
            throw BackupFormatException("This backup has category groups but no categories, so it can't be restored.")
        }
        val accountIds = d.accounts.map { it.id }.toSet()
        val groupIds = d.groups.map { it.id }.toSet()
        val goalIds = d.goals.map { it.id }.toSet()
        d.categories.forEach {
            if (it.groupId !in groupIds) throw BackupFormatException("Category \"${it.name}\" points to a missing group (${it.groupId}).")
        }
        d.transactions.forEachIndexed { i, t ->
            if (t.accountId !in accountIds) throw BackupFormatException("transactions[$i] points to a missing account (${t.accountId}).")
            val dest = t.destinationAccountId
            if (dest != null && dest !in accountIds) throw BackupFormatException("transactions[$i] points to a missing destination account ($dest).")
        }
        d.contributions.forEachIndexed { i, c ->
            if (c.goalId !in goalIds) throw BackupFormatException("goalContributions[$i] points to a missing goal (${c.goalId}).")
        }
    }

    /** Typed, path-aware access to one JSON object. */
    private class Obj(val map: Map<*, *>, val path: String) {
        fun fail(key: String, problem: String): Nothing =
            throw BackupFormatException("Invalid backup: $path.$key $problem.")

        fun array(key: String): List<Obj> {
            val v = map[key] as? List<*> ?: fail(key, if (map.containsKey(key)) "must be a list" else "is missing")
            return v.mapIndexed { i, item ->
                if (item !is Map<*, *>) throw BackupFormatException("Invalid backup: $key[$i] must be an object.")
                Obj(item, "$key[$i]")
            }
        }

        fun long(key: String): Long = map[key] as? Long ?: fail(key, "must be a whole number")
        fun longOrNull(key: String): Long? = when (val v = map[key]) {
            null -> null
            is Long -> v
            else -> fail(key, "must be a whole number or null")
        }
        fun int(key: String): Int {
            val v = long(key)
            if (v < Int.MIN_VALUE || v > Int.MAX_VALUE) fail(key, "is out of range")
            return v.toInt()
        }
        fun string(key: String): String = map[key] as? String ?: fail(key, "must be text")
        fun stringOrNull(key: String): String? = when (val v = map[key]) {
            null -> null
            is String -> v
            else -> fail(key, "must be text or null")
        }
        fun bool(key: String): Boolean = map[key] as? Boolean ?: fail(key, "must be true or false")
        fun txType(key: String): TransactionType {
            val s = string(key)
            return TransactionType.values().firstOrNull { it.name == s } ?: fail(key, "unknown type \"$s\"")
        }
    }
}

/** Result of planning an import: rows to insert (ids already assigned) and what was skipped. */
data class ImportPlan(
    val toInsert: BackupData,
    val added: BackupCounts,
    val skipped: BackupCounts
)

/**
 * Merges a backup into existing data without duplicating anything:
 * - accounts by normalised bank name, groups by name, categories by key, rules by
 *   (match, pattern, category), budgets by category, recurring by merchant key,
 *   goals by (name, createdAt), contributions by (goal, time, amount);
 * - transactions by (time, signed amount, type, account, merchant) against existing rows.
 * Incoming ids are kept when free, otherwise new ones are allocated after the current max,
 * and all references (account, group, goal, transfer pair) are remapped.
 * "Replace" is `plan(BackupData.EMPTY, incoming)` after wiping the tables.
 */
object BackupMerger {

    fun transactionKey(tx: TransactionEntity, accountName: String): String =
        listOf(
            tx.timestamp.toString(),
            tx.amount.toString(),
            tx.type.name,
            normalizeBankName(accountName).lowercase(Locale.ROOT),
            tx.merchant?.trim()?.lowercase(Locale.ROOT).orEmpty()
        ).joinToString("|")

    private class IdAllocator(existing: Collection<Long>) {
        private val used = existing.toHashSet()
        private var next = (existing.maxOrNull() ?: 0L) + 1
        fun take(preferred: Long): Long {
            val id = if (preferred > 0 && used.add(preferred)) preferred else {
                while (!used.add(next)) next++
                next
            }
            if (id >= next) next = id + 1
            return id
        }
    }

    fun plan(existing: BackupData, incoming: BackupData): ImportPlan {
        // Accounts
        val accountIds = IdAllocator(existing.accounts.map { it.id })
        val accountByName = HashMap<String, AccountEntity>()
        existing.accounts.forEach { accountByName.putIfAbsent(normalizeBankName(it.name).lowercase(Locale.ROOT), it) }
        val accountMap = HashMap<Long, AccountEntity>()
        val newAccounts = mutableListOf<AccountEntity>()
        var skippedAccounts = 0
        incoming.accounts.forEach { a ->
            val name = normalizeBankName(a.name)
            val key = name.lowercase(Locale.ROOT)
            val found = accountByName[key]
            if (found != null) {
                accountMap[a.id] = found
                skippedAccounts++
            } else {
                val created = AccountEntity(id = accountIds.take(a.id), name = name)
                newAccounts += created
                accountByName[key] = created
                accountMap[a.id] = created
            }
        }

        // Groups
        val groupIds = IdAllocator(existing.groups.map { it.id })
        val groupByName = HashMap<String, Long>()
        existing.groups.forEach { groupByName.putIfAbsent(it.name.trim().lowercase(Locale.ROOT), it.id) }
        var sortOrder = existing.groups.maxOfOrNull { it.sortOrder } ?: 0
        val groupMap = HashMap<Long, Long>()
        val newGroups = mutableListOf<CategoryGroupEntity>()
        var skippedGroups = 0
        incoming.groups.sortedWith(compareBy({ it.sortOrder }, { it.id })).forEach { g ->
            val key = g.name.trim().lowercase(Locale.ROOT)
            val found = groupByName[key]
            if (found != null) {
                groupMap[g.id] = found
                skippedGroups++
            } else {
                val created = g.copy(id = groupIds.take(g.id), sortOrder = ++sortOrder)
                newGroups += created
                groupByName[key] = created.id
                groupMap[g.id] = created.id
            }
        }

        // Categories
        val categoryKeys = existing.categories.map { it.key }.toHashSet()
        val fallbackGroup = (existing.groups + newGroups).firstOrNull { it.id == com.spendtracker.app.data.CatalogSeed.OTHER_GROUP_ID }?.id
            ?: (existing.groups + newGroups).firstOrNull()?.id
            ?: com.spendtracker.app.data.CatalogSeed.OTHER_GROUP_ID
        val newCategories = mutableListOf<CategoryEntity>()
        incoming.categories.forEach { c ->
            if (categoryKeys.add(c.key)) newCategories += c.copy(groupId = groupMap[c.groupId] ?: fallbackGroup)
        }

        // Rules
        fun ruleKey(r: MerchantRuleEntity) = "${r.matchType}|${r.pattern.trim().lowercase(Locale.ROOT)}|${r.categoryKey}"
        val ruleKeys = existing.rules.map(::ruleKey).toHashSet()
        val ruleIds = IdAllocator(existing.rules.map { it.id })
        val newRules = mutableListOf<MerchantRuleEntity>()
        incoming.rules.forEach { r ->
            if (ruleKeys.add(ruleKey(r))) newRules += r.copy(id = ruleIds.take(r.id), pattern = r.pattern.trim())
        }

        // Transactions (dedupe only against rows already on the device)
        val existingAccountNames = existing.accounts.associate { it.id to it.name }
        // Each existing row can absorb only one incoming row, so N identical incoming rows
        // against M existing ones insert max(0, N - M).
        val existingTxKeys = HashMap<String, ArrayDeque<Long>>()
        existing.transactions.forEach { t ->
            val name = existingAccountNames[t.accountId] ?: t.source.orEmpty()
            existingTxKeys.getOrPut(transactionKey(t, name)) { ArrayDeque() }.addLast(t.id)
        }
        val insertedIncomingIds = HashSet<Long>()
        val txIds = IdAllocator(existing.transactions.map { it.id })
        val txMap = HashMap<Long, Long>()
        val newTx = mutableListOf<TransactionEntity>()
        incoming.transactions.forEach { t ->
            val account = accountMap[t.accountId]
            val accountName = account?.name ?: normalizeBankName(t.source)
            val dup = existingTxKeys[transactionKey(t, accountName)]?.removeFirstOrNull()
            if (dup != null) {
                txMap[t.id] = dup
            } else {
                val id = txIds.take(t.id)
                txMap[t.id] = id
                insertedIncomingIds += t.id
                newTx += t.copy(
                    id = id,
                    accountId = account?.id ?: t.accountId,
                    destinationAccountId = t.destinationAccountId?.let { accountMap[it]?.id },
                    source = account?.name ?: t.source
                )
            }
        }
        // Keep a transfer link only when both legs are new; a leg that matched an existing row
        // already has its own partner, so linking to it would leave a one-sided pair.
        val pairedTx = newTx.map { t ->
            val partner = t.pairedTransactionId
            t.copy(pairedTransactionId = if (partner != null && partner in insertedIncomingIds) txMap[partner] else null)
        }

        // Budgets
        val budgetKeys = existing.budgets.map { it.categoryKey }.toHashSet()
        val newBudgets = incoming.budgets.filter { budgetKeys.add(it.categoryKey) }

        // Goals + contributions
        fun goalKey(g: GoalEntity) = "${g.name.trim().lowercase(Locale.ROOT)}|${g.createdAt}"
        val goalByKey = HashMap<String, Long>()
        existing.goals.forEach { goalByKey.putIfAbsent(goalKey(it), it.id) }
        val goalIds = IdAllocator(existing.goals.map { it.id })
        val goalMap = HashMap<Long, Long>()
        val newGoals = mutableListOf<GoalEntity>()
        var skippedGoals = 0
        incoming.goals.forEach { g ->
            val found = goalByKey[goalKey(g)]
            if (found != null) {
                goalMap[g.id] = found
                skippedGoals++
            } else {
                val created = g.copy(id = goalIds.take(g.id))
                newGoals += created
                goalByKey[goalKey(g)] = created.id
                goalMap[g.id] = created.id
            }
        }
        fun contributionKey(goalId: Long, c: GoalContributionEntity) = "$goalId|${c.timestamp}|${c.amountMinor}"
        val contributionKeys = existing.contributions.map { contributionKey(it.goalId, it) }.toHashSet()
        val contributionIds = IdAllocator(existing.contributions.map { it.id })
        val newContributions = mutableListOf<GoalContributionEntity>()
        incoming.contributions.forEach { c ->
            val goalId = goalMap[c.goalId] ?: return@forEach
            if (contributionKeys.add(contributionKey(goalId, c))) {
                newContributions += c.copy(id = contributionIds.take(c.id), goalId = goalId)
            }
        }

        // Recurring (merchantKey is unique in the table)
        val recurringKeys = existing.recurring.map { it.merchantKey }.toHashSet()
        val recurringIds = IdAllocator(existing.recurring.map { it.id })
        val newRecurring = mutableListOf<RecurringItemEntity>()
        incoming.recurring.forEach { r ->
            if (recurringKeys.add(r.merchantKey)) newRecurring += r.copy(id = recurringIds.take(r.id))
        }

        val toInsert = BackupData(
            exportedAt = incoming.exportedAt,
            accounts = newAccounts,
            groups = newGroups,
            categories = newCategories,
            rules = newRules,
            transactions = pairedTx,
            budgets = newBudgets,
            goals = newGoals,
            contributions = newContributions,
            recurring = newRecurring
        )
        val added = toInsert.counts
        val inc = incoming.counts
        val skipped = BackupCounts(
            accounts = skippedAccounts,
            groups = skippedGroups,
            categories = inc.categories - added.categories,
            rules = inc.rules - added.rules,
            transactions = inc.transactions - added.transactions,
            budgets = inc.budgets - added.budgets,
            goals = skippedGoals,
            contributions = inc.contributions - added.contributions,
            recurring = inc.recurring - added.recurring
        )
        return ImportPlan(toInsert, added, skipped)
    }
}

/** Minimal JSON reader/writer: objects -> LinkedHashMap, arrays -> List, integers -> Long, other numbers -> Double. */
internal object Json {

    fun parse(text: String): Any? {
        val p = Parser(text)
        p.skipWs()
        val v = p.value()
        p.skipWs()
        if (p.i != text.length) p.error("unexpected text after the end")
        return v
    }

    fun stringify(value: Any?): String = StringBuilder().also { write(it, value) }.toString()

    private fun write(sb: StringBuilder, v: Any?) {
        when (v) {
            null -> sb.append("null")
            is String -> sb.append(quote(v))
            is Boolean, is Int, is Long -> sb.append(v.toString())
            is Double -> sb.append(if (v.isFinite()) v.toString() else "null")
            is Map<*, *> -> {
                sb.append('{')
                var first = true
                for ((k, value) in v) {
                    if (!first) sb.append(", ")
                    first = false
                    sb.append(quote(k.toString())).append(": ")
                    write(sb, value)
                }
                sb.append('}')
            }
            is List<*> -> {
                sb.append('[')
                v.forEachIndexed { i, item -> if (i > 0) sb.append(", "); write(sb, item) }
                sb.append(']')
            }
            else -> sb.append(quote(v.toString()))
        }
    }

    fun quote(value: String): String {
        val sb = StringBuilder("\"")
        for (c in value) {
            when {
                c == '"' -> sb.append("\\\"")
                c == '\\' -> sb.append("\\\\")
                c == '\n' -> sb.append("\\n")
                c == '\r' -> sb.append("\\r")
                c == '\t' -> sb.append("\\t")
                c < ' ' -> sb.append("\\u%04x".format(c.code))
                else -> sb.append(c)
            }
        }
        return sb.append('"').toString()
    }

    private class Parser(val s: String) {
        var i = 0
        private var depth = 0

        fun error(msg: String): Nothing = throw BackupFormatException("$msg at position $i")

        fun skipWs() {
            while (i < s.length && (s[i] == ' ' || s[i] == '\n' || s[i] == '\r' || s[i] == '\t' || s[i] == '﻿')) i++
        }

        fun value(): Any? {
            if (i >= s.length) error("unexpected end of file")
            return when (val c = s[i]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> str()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> if (c == '-' || c in '0'..'9') num() else error("unexpected character '$c'")
            }
        }

        private fun literal(word: String, v: Any?): Any? {
            if (!s.startsWith(word, i)) error("unexpected token")
            i += word.length
            return v
        }

        private fun obj(): Map<String, Any?> {
            if (++depth > 64) error("nesting too deep")
            i++
            val m = LinkedHashMap<String, Any?>()
            skipWs()
            if (i < s.length && s[i] == '}') { i++; depth--; return m }
            while (true) {
                skipWs()
                if (i >= s.length || s[i] != '"') error("expected a key")
                val k = str()
                skipWs()
                if (i >= s.length || s[i] != ':') error("expected ':'")
                i++
                skipWs()
                m[k] = value()
                skipWs()
                if (i >= s.length) error("unexpected end of file")
                when (s[i]) {
                    ',' -> i++
                    '}' -> { i++; depth--; return m }
                    else -> error("expected ',' or '}'")
                }
            }
        }

        private fun arr(): List<Any?> {
            if (++depth > 64) error("nesting too deep")
            i++
            val l = ArrayList<Any?>()
            skipWs()
            if (i < s.length && s[i] == ']') { i++; depth--; return l }
            while (true) {
                skipWs()
                l.add(value())
                skipWs()
                if (i >= s.length) error("unexpected end of file")
                when (s[i]) {
                    ',' -> i++
                    ']' -> { i++; depth--; return l }
                    else -> error("expected ',' or ']'")
                }
            }
        }

        private fun str(): String {
            i++
            val sb = StringBuilder()
            while (true) {
                if (i >= s.length) error("unterminated text")
                val c = s[i++]
                when {
                    c == '"' -> return sb.toString()
                    c == '\\' -> {
                        if (i >= s.length) error("unterminated text")
                        when (val e = s[i++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (i + 4 > s.length) error("bad \\u escape")
                                val code = s.substring(i, i + 4).toIntOrNull(16) ?: error("bad \\u escape")
                                sb.append(code.toChar())
                                i += 4
                            }
                            else -> error("bad escape '\\$e'")
                        }
                    }
                    c < ' ' -> error("control character in text")
                    else -> sb.append(c)
                }
            }
        }

        private fun num(): Any {
            val start = i
            if (s[i] == '-') i++
            while (i < s.length && s[i] in '0'..'9') i++
            var isInt = true
            if (i < s.length && s[i] == '.') { isInt = false; i++; while (i < s.length && s[i] in '0'..'9') i++ }
            if (i < s.length && (s[i] == 'e' || s[i] == 'E')) {
                isInt = false
                i++
                if (i < s.length && (s[i] == '+' || s[i] == '-')) i++
                while (i < s.length && s[i] in '0'..'9') i++
            }
            val t = s.substring(start, i)
            if (t == "-" || t.isEmpty()) error("bad number")
            return if (isInt) t.toLongOrNull() ?: error("number too large") else t.toDoubleOrNull() ?: error("bad number")
        }
    }
}
