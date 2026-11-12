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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BackupCodecTest {

    private fun sample(): BackupData = BackupData(
        exportedAt = 1_731_400_000_000L,
        accounts = listOf(AccountEntity(1, "Chase"), AccountEntity(2, "HSBC"), AccountEntity(7, "Joint Pot")),
        groups = listOf(
            CategoryGroupEntity(1, "Food & Dining", "F", 1, true),
            CategoryGroupEntity(9, "Pets", "P", 7, false)
        ),
        categories = listOf(
            CategoryEntity("GROCERIES", 1, "Groceries", "G", "#D1FAE5", TransactionType.EXPENSE, true, false),
            CategoryEntity("c_123", 9, "Vet \"bills\"", "V", "#FCE7F3", TransactionType.EXPENSE, false, true)
        ),
        rules = listOf(MerchantRuleEntity(3, "STARTS_WITH", "Tesco", "GROCERIES", 100L)),
        transactions = listOf(
            TransactionEntity(
                id = 10, accountId = 1, amount = -1480, category = "GROCERIES",
                note = "Line1\nLine2 \\ tab\t", timestamp = 1_700_000_000_000L,
                merchant = "Tesco, Express", source = "Chase"
            ),
            TransactionEntity(
                id = 11, accountId = 1, amount = -5000, category = "INTERNAL_TRANSFER", timestamp = 1_700_000_100_000L,
                type = TransactionType.TRANSFER, destinationAccountId = 7, pairedTransactionId = 12,
                excludeFromSpending = true, merchant = "Chase ➔ Joint Pot", source = "Chase"
            ),
            TransactionEntity(
                id = 12, accountId = 7, amount = 5000, category = "INTERNAL_TRANSFER", timestamp = 1_700_000_100_000L,
                type = TransactionType.TRANSFER, destinationAccountId = 7, pairedTransactionId = 11,
                excludeFromSpending = true, merchant = "Chase ➔ Joint Pot", source = "Joint Pot", needsReview = true
            ),
            TransactionEntity(id = 13, accountId = 2, amount = 285000, category = "SALARY", timestamp = 1_700_000_200_000L,
                type = TransactionType.INCOME, merchant = null, source = "HSBC")
        ),
        budgets = listOf(BudgetEntity("GROCERIES", 30000, true, 5L)),
        goals = listOf(GoalEntity(4, "Holiday", "H", "#A5B4FC", 100_000, null, 500, 42L, false)),
        contributions = listOf(GoalContributionEntity(8, 4, -250, 43L, "oops ü")),
        recurring = listOf(RecurringItemEntity(5, "netflix", "Netflix", "SUBSCRIPTIONS", 1099, "MONTHLY", 44L, "APPROVED", false, 45L))
    )

    @Test
    fun `encode then decode round-trips every table`() {
        val data = sample()
        val decoded = BackupCodec.decode(BackupCodec.encode(data))
        assertEquals(data, decoded)
    }

    @Test
    fun `encoded file carries format and version`() {
        val json = BackupCodec.encode(sample())
        assertTrue(json.contains("\"format\": \"spendtracker-backup\""))
        assertTrue(json.contains("\"version\": 1"))
    }

    @Test
    fun `empty backup round-trips`() {
        assertEquals(BackupData.EMPTY, BackupCodec.decode(BackupCodec.encode(BackupData.EMPTY)))
    }

    private fun assertRejected(text: String, messagePart: String) {
        try {
            BackupCodec.decode(text)
            fail("Expected BackupFormatException")
        } catch (e: BackupFormatException) {
            assertTrue("'${e.message}' should mention '$messagePart'", e.message!!.contains(messagePart, ignoreCase = true))
        }
    }

    @Test
    fun `rejects malformed json`() {
        assertRejected("{\"format\": \"spendtracker-backup\", ", "valid JSON")
        assertRejected("not json at all", "valid JSON")
        assertRejected("[1, 2, 3]", "isn't a Spend Tracker backup")
    }

    @Test
    fun `rejects old unversioned export`() {
        val old = "{\n  \"exportedAt\": \"2024-01-01 10:00:00\",\n  \"transactions\": [],\n  \"rules\": []\n}\n"
        assertRejected(old, "older export")
    }

    @Test
    fun `rejects newer and older versions`() {
        val json = BackupCodec.encode(sample())
        assertRejected(json.replace("\"version\": 1", "\"version\": 2"), "newer version")
        assertRejected(json.replace("\"version\": 1", "\"version\": 0"), "no longer supported")
        assertRejected(json.replace("\"version\": 1", "\"version\": \"1\""), "version")
    }

    @Test
    fun `rejects wrong field types and missing sections`() {
        val json = BackupCodec.encode(sample())
        assertRejected(json.replace("\"amount\": -1480", "\"amount\": -14.80"), "transactions[0].amount")
        assertRejected(json.replace("\"type\": \"EXPENSE\"", "\"type\": \"SPEND\""), "unknown type")
        assertRejected(json.replace("\"matchType\": \"STARTS_WITH\"", "\"matchType\": \"REGEX\""), "matchType")
        assertRejected(json.replace("\"budgets\": [", "\"budgetz\": ["), "budgets is missing")
    }

    @Test
    fun `rejects broken references and duplicate ids`() {
        val data = sample()
        val badAccount = data.copy(transactions = data.transactions + data.transactions[0].copy(id = 99, accountId = 42))
        assertRejected(BackupCodec.encode(badAccount), "missing account")
        val badGoal = data.copy(contributions = listOf(data.contributions[0].copy(goalId = 77)))
        assertRejected(BackupCodec.encode(badGoal), "missing goal")
        val dupIds = data.copy(transactions = data.transactions + data.transactions[0])
        assertRejected(BackupCodec.encode(dupIds), "duplicate")
    }

    @Test
    fun `json parser handles escapes and unicode`() {
        val parsed = Json.parse("{\"a\": \"x\\u00e9\\n\\\"q\\\"\", \"b\": [true, false, null, -3, 1.5e2]}") as Map<*, *>
        assertEquals("xé\n\"q\"", parsed["a"])
        assertEquals(listOf(true, false, null, -3L, 150.0), parsed["b"])
    }

    // ---- merge ----

    @Test
    fun `merging a backup into the same data adds nothing`() {
        val data = sample()
        val plan = BackupMerger.plan(data, data)
        assertEquals(BackupCounts(), plan.added)
        assertEquals(data.transactions.size, plan.skipped.transactions)
        assertEquals(data.accounts.size, plan.skipped.accounts)
    }

    @Test
    fun `merge skips duplicate transactions and keeps new ones`() {
        val existing = sample()
        val newTx = TransactionEntity(id = 10, accountId = 1, amount = -999, category = "DINING", timestamp = 1_700_000_900_000L,
            merchant = "Costa", source = "Chase")
        // Same purchase as existing #10 but recategorised and with a different id: still a duplicate.
        val sameAsExisting = existing.transactions[0].copy(id = 50, category = "SHOPPING", note = null)
        val incoming = existing.copy(
            accounts = listOf(AccountEntity(1, "chase uk")),
            transactions = listOf(newTx, sameAsExisting)
        ).let { it.copy(contributions = emptyList()) }
        val plan = BackupMerger.plan(existing, incoming)
        assertEquals(1, plan.added.transactions)
        assertEquals(1, plan.skipped.transactions)
        val inserted = plan.toInsert.transactions.single()
        assertEquals(-999L, inserted.amount)
        assertEquals(14L, inserted.id) // id 10 is taken -> next after max(13)
        assertEquals(1L, inserted.accountId) // "chase uk" normalises to existing Chase
        assertEquals(0, plan.added.accounts)
    }

    @Test
    fun `merge remaps transfer pairs and new accounts`() {
        val existing = sample().copy(
            accounts = listOf(AccountEntity(1, "Chase"), AccountEntity(7, "HSBC")),
            transactions = listOf(TransactionEntity(id = 11, accountId = 1, amount = -1, timestamp = 1L, source = "Chase")),
            contributions = emptyList(), goals = emptyList()
        )
        val incoming = sample()
        val plan = BackupMerger.plan(existing, incoming)
        val joint = plan.toInsert.accounts.single { it.name == "Joint Pot" }
        assertTrue(joint.id != 7L) // 7 is taken locally
        val out = plan.toInsert.transactions.single { it.amount == -5000L }
        val into = plan.toInsert.transactions.single { it.amount == 5000L }
        assertEquals(joint.id, out.destinationAccountId)
        assertEquals(joint.id, into.accountId)
        assertEquals("Joint Pot", into.source)
        assertEquals(into.id, out.pairedTransactionId)
        assertEquals(out.id, into.pairedTransactionId)
        // HSBC in the backup maps to local id 7 (named HSBC)
        assertEquals(7L, plan.toInsert.transactions.single { it.amount == 285000L }.accountId)
    }

    @Test
    fun `merge skips existing catalogue, budgets, goals and recurring`() {
        val existing = sample()
        val incoming = sample().copy(
            groups = existing.groups + CategoryGroupEntity(1, "Travel stuff", "T", 2, false),
            rules = existing.rules + MerchantRuleEntity(3, "CONTAINS", "Shell", "TRANSPORT", 1L),
            recurring = existing.recurring + RecurringItemEntity(5, "spotify", "Spotify", null, 999, "MONTHLY", 1L, "IGNORED", false, 1L)
        )
        // duplicate id 1 in groups would be rejected by decode; plan() itself just remaps
        val plan = BackupMerger.plan(existing, incoming.copy(groups = incoming.groups.distinctBy { it.name }))
        assertEquals(1, plan.added.groups)
        assertEquals(1, plan.added.rules)
        assertEquals(4L, plan.toInsert.rules.single().id)
        assertEquals(1, plan.added.recurring)
        assertEquals("spotify", plan.toInsert.recurring.single().merchantKey)
        assertEquals(0, plan.added.budgets)
        assertEquals(0, plan.added.goals)
        assertEquals(0, plan.added.contributions)
        assertEquals(0, plan.added.categories)
    }

    @Test
    fun `replace plan keeps ids and references`() {
        val data = sample()
        val plan = BackupMerger.plan(BackupData.EMPTY, data)
        assertEquals(data.transactions.map { it.id }, plan.toInsert.transactions.map { it.id })
        assertEquals(data.accounts, plan.toInsert.accounts)
        assertEquals(data.transactions.map { it.pairedTransactionId }, plan.toInsert.transactions.map { it.pairedTransactionId })
        assertEquals(data.contributions, plan.toInsert.contributions)
        assertEquals(data.categories, plan.toInsert.categories)
        assertNull(plan.toInsert.transactions.first { it.id == 13L }.pairedTransactionId)
    }

    @Test
    fun `transaction key ignores category and note but not merchant`() {
        val t = sample().transactions[0]
        assertEquals(
            BackupMerger.transactionKey(t, "Chase"),
            BackupMerger.transactionKey(t.copy(category = "DINING", note = "x", id = 99), "chase bank")
        )
        assertTrue(BackupMerger.transactionKey(t, "Chase") != BackupMerger.transactionKey(t.copy(merchant = "Aldi"), "Chase"))
    }
}
