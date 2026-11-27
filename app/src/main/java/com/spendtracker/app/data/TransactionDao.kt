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

fun isUnknownOrGenericMerchant(m: String?, sourceName: String? = null): Boolean {
    if (m.isNullOrBlank()) return true
    val trimmed = m.trim()
    if (trimmed.equals("Card Payment", ignoreCase = true) ||
        trimmed.equals("Payment", ignoreCase = true) ||
        trimmed.equals("Unknown", ignoreCase = true) ||
        trimmed.equals("Unknown Vendor", ignoreCase = true) ||
        trimmed.equals("Transaction", ignoreCase = true) ||
        trimmed.equals("Money In", ignoreCase = true) ||
        trimmed.equals("Money Out", ignoreCase = true) ||
        trimmed.equals("HSBC Payment", ignoreCase = true) ||
        trimmed.equals("Chase Payment", ignoreCase = true) ||
        trimmed.equals("Premier Payment", ignoreCase = true) ||
        trimmed.equals("HSBC", ignoreCase = true) ||
        trimmed.equals("Premier", ignoreCase = true) ||
        trimmed.equals("Chase", ignoreCase = true) ||
        (!sourceName.isNullOrBlank() && trimmed.equals(sourceName.trim(), ignoreCase = true))
    ) return true
    return false
}

fun isPremierOrHsbcBank(bank: String?): Boolean {
    if (bank.isNullOrBlank()) return false
    return normalizeBankName(bank).equals("HSBC", ignoreCase = true)
}

fun isSameOrEquivalentBank(bankA: String?, bankB: String?): Boolean {
    if (bankA.isNullOrBlank() || bankB.isNullOrBlank()) return false
    if (bankA.trim().equals(bankB.trim(), ignoreCase = true)) return true
    if (isPremierOrHsbcBank(bankA) && isPremierOrHsbcBank(bankB)) return true
    return normalizeBankName(bankA).equals(normalizeBankName(bankB), ignoreCase = true)
}

fun resolvePreferredBankSource(existingSource: String?, newSource: String?): String {
    if (isPremierOrHsbcBank(existingSource) || isPremierOrHsbcBank(newSource)) {
        return "HSBC"
    }
    // A wallet/aggregator alert (PayPal, Google Pay...) never replaces the bank that actually paid.
    if (isWalletSource(newSource) && !existingSource.isNullOrBlank() && !isWalletSource(existingSource)) {
        return normalizeBankName(existingSource)
    }
    if (isWalletSource(existingSource) && !newSource.isNullOrBlank() && !isWalletSource(newSource)) {
        return normalizeBankName(newSource)
    }
    return normalizeBankName(newSource ?: existingSource)
}

fun isMerchantDuplicate(existingMerchant: String?, newMerchant: String?, sourceName: String? = null): Boolean {
    val existingGeneric = isUnknownOrGenericMerchant(existingMerchant, sourceName)
    val newGeneric = isUnknownOrGenericMerchant(newMerchant, sourceName)

    if (existingGeneric || newGeneric) return true
    val exTrim = existingMerchant!!.trim()
    val newTrim = newMerchant!!.trim()
    if (exTrim.equals(newTrim, ignoreCase = true)) return true
    val exClean = com.spendtracker.app.parser.SpendParser.cleanMerchantName(exTrim, sourceName ?: "")
    val newClean = com.spendtracker.app.parser.SpendParser.cleanMerchantName(newTrim, sourceName ?: "")
    return exClean.isNotBlank() && exClean.equals(newClean, ignoreCase = true)
}

// ---------------------------------------------------------------------------------------------
// Duplicate detection rules (one set of rules for insert time, the on-open repair and the view).
// Mirrored 1:1 in test_bank_deduplication.py (dedup_decision & friends).
// ---------------------------------------------------------------------------------------------

/**
 * Two alerts from the same bank (e.g. HSBC app + HSBC Premier SMS) for one payment.
 * HSBC/Premier pairs have been seen up to ~3 minutes apart; the on-open repair already used 180 s,
 * so insert time, the repair and the view all share this one window.
 */
const val DEDUP_WINDOW_MILLIS = 180_000L

/**
 * Same bank and the same known merchant on both alerts: a real duplicate arrives within seconds, so
 * two genuine identical purchases ("Pret £3.20" twice a couple of minutes apart) stay separate.
 * Generic-vs-known pairs (e.g. HSBC app + Premier SMS) keep [DEDUP_WINDOW_MILLIS].
 */
const val SAME_MERCHANT_DEDUP_WINDOW_MILLIS = 60_000L

/**
 * A wallet/aggregator alert (PayPal, Google Pay, ...) and the alert of the bank card that funded it.
 * PayPal's push and the card issuer's alert can be a few minutes apart (PayPal charges the funding
 * card after its own confirmation). 5 minutes covers that lag while keeping the chance of pairing
 * two unrelated purchases of the exact same amount small; the pair must also have compatible merchants.
 */
const val WALLET_DEDUP_WINDOW_MILLIS = 300_000L

/** Widest window any rule uses; the SQL candidate query uses it, [dedupDecision] applies the exact one. */
const val MAX_DEDUP_WINDOW_MILLIS = 300_000L

/** Sources that relay a payment made with a bank card/account rather than holding the money themselves. */
val WALLET_SOURCES: Set<String> = setOf("PayPal", "Google Pay", "Apple Pay", "Samsung Pay")

fun isWalletSource(source: String?): Boolean =
    !source.isNullOrBlank() && normalizeBankName(source) in WALLET_SOURCES

private val WALLET_MERCHANT_PREFIX = Regex(
    "(?i)^\\s*(?:paypal|pp(?=\\s*\\*)|google\\s*pay|google|gpay|apple\\s*pay|samsung\\s*pay)\\b\\s*[*:\\-]?\\s*"
)

/** "PAYPAL *UBER" -> "UBER", "Google Pay" -> "", "Tesco" -> "Tesco". */
fun stripWalletMerchantPrefix(merchant: String?): String =
    merchant?.let { WALLET_MERCHANT_PREFIX.replace(it, "") }?.trim().orEmpty()

/** True when a bank-side merchant only names the wallet ("PayPal", "PAYPAL *UBER", "GOOGLE *Pret"). */
fun merchantNamesWallet(merchant: String?): Boolean =
    !merchant.isNullOrBlank() && WALLET_MERCHANT_PREFIX.containsMatchIn(merchant)

/** Loose comparison key: wallet prefix stripped, lower case, letters/digits only, single spaces. */
fun walletMerchantKey(merchant: String?): String =
    stripWalletMerchantPrefix(merchant)
        .lowercase(java.util.Locale.ROOT)
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()

/** Generic for its own source, or nothing left once the wallet name is stripped ("PayPal Payment"). */
private fun isGenericForPairing(merchant: String?, source: String?): Boolean {
    if (isUnknownOrGenericMerchant(merchant, source)) return true
    val stripped = stripWalletMerchantPrefix(merchant)
    return merchantNamesWallet(merchant) && (stripped.isBlank() || isUnknownOrGenericMerchant(stripped, source))
}

/**
 * Can a bank-side merchant and a wallet-side merchant describe the same purchase?
 * Yes when either is generic, or their keys are equal, one is a prefix of the other, or they share a
 * first word of 3+ characters ("PAYPAL *UBER TRIP" vs "Uber"; "PRET A MANGER" vs "Pret").
 */
fun isWalletMerchantCompatible(bankMerchant: String?, bankSource: String?, walletMerchant: String?, walletSource: String?): Boolean {
    if (isGenericForPairing(bankMerchant, bankSource) || isGenericForPairing(walletMerchant, walletSource)) return true
    val b = walletMerchantKey(bankMerchant)
    val w = walletMerchantKey(walletMerchant)
    if (b.isEmpty() || w.isEmpty()) return true
    if (b == w || b.startsWith(w) || w.startsWith(b)) return true
    val bFirst = b.substringBefore(' ')
    val wFirst = w.substringBefore(' ')
    return bFirst.length >= 3 && bFirst == wFirst
}

/** True for rows captured from a bank/wallet notification (the listener writes "Captured from ... alert"). */
fun isAutoCapturedNote(note: String?): Boolean =
    note?.trimStart()?.startsWith("Captured from", ignoreCase = true) == true

/** How far apart the two alerts of a move between the user's own accounts may be. */
const val TRANSFER_PAIR_WINDOW_MILLIS = 600_000L

/** Written into the note of both legs; also stops a pair the user re-filed from being matched again. */
const val TRANSFER_MATCH_MARKER = "Internal transfer: matched with"

/** The alert text of a "Captured from ..." note, without the lines the app added below it. */
fun alertTextOfNote(note: String): String =
    note.substringBefore("\n" + TRANSFER_MATCH_MARKER)
        .substringBefore("\n" + com.spendtracker.app.domain.AiAlertReader.NOTE_MARKER)

/** The facts the own-account transfer rule looks at, for a stored row or a new alert. */
data class TransferLeg(
    val amountMinor: Long,
    val type: TransactionType,
    val source: String?,
    val category: String?,
    val timestamp: Long,
    val excluded: Boolean = false,
    val linked: Boolean = false,
    val note: String? = null
)

fun TransactionEntity.toTransferLeg(): TransferLeg = TransferLeg(
    amountMinor = kotlin.math.abs(amount),
    type = type,
    source = source,
    category = category,
    timestamp = timestamp,
    excluded = excludeFromSpending,
    linked = pairedTransactionId != null,
    note = note
)

private fun isUncategorised(category: String?): Boolean =
    category.isNullOrBlank() || category == "OTHER_EXPENSE" || category == "OTHER_INCOME" ||
        category == "General Spend" || category == "Money In"

private fun TransferLeg.canBePaired(): Boolean =
    amountMinor > 0 && !excluded && !linked && isAutoCapturedNote(note) &&
        note?.contains(TRANSFER_MATCH_MARKER) != true &&
        !source.isNullOrBlank() && !source.trim().equals("Card Payment", ignoreCase = true) &&
        !isWalletSource(source) && isUncategorised(category)

/**
 * True when the two alerts are the two legs of a move between the user's own accounts: money out
 * of one bank and the same amount into a different bank within [TRANSFER_PAIR_WINDOW_MILLIS].
 * Both must be captured from alerts, uncategorised (a recognised shop or salary is a real payment),
 * not wallets (PayPal / Google Pay), and not already excluded, linked or matched before.
 */
fun isOwnTransferPair(a: TransferLeg, b: TransferLeg): Boolean {
    val types = setOf(a.type, b.type)
    if (types != setOf(TransactionType.EXPENSE, TransactionType.INCOME)) return false
    if (a.amountMinor != b.amountMinor) return false
    if (!a.canBePaired() || !b.canBePaired()) return false
    if (isSameOrEquivalentBank(a.source, b.source)) return false
    return kotlin.math.abs(a.timestamp - b.timestamp) <= TRANSFER_PAIR_WINDOW_MILLIS
}

/** Note for one leg of a matched pair, e.g. "... \nInternal transfer: matched with HSBC (money out)". */
fun transferMatchNote(note: String?, otherSource: String?, otherType: TransactionType): String {
    val side = if (otherType == TransactionType.INCOME) "money in" else "money out"
    val line = "$TRANSFER_MATCH_MARKER ${otherSource?.trim().orEmpty()} ($side)"
    return if (note.isNullOrBlank()) line else "${note.trimEnd()}\n$line"
}

/** The facts the duplicate rules look at, for a stored row, a list item or a new alert. */
data class DedupKey(
    val amountMinor: Long,
    val type: TransactionType,
    val source: String?,
    val accountId: Long?,
    val merchant: String?,
    val timestamp: Long,
    /** TRANSFER rows, INTERNAL_TRANSFER category or a paired leg: never deduplicated. */
    val isTransfer: Boolean = false,
    /** Entered by the user (not captured from an alert): saved as-is, never merged with another row. */
    val isManual: Boolean = false
)

fun TransactionEntity.toDedupKey(): DedupKey = DedupKey(
    amountMinor = kotlin.math.abs(amount),
    type = type,
    source = source,
    accountId = accountId,
    merchant = merchant,
    timestamp = timestamp,
    isTransfer = type == TransactionType.TRANSFER || category == "INTERNAL_TRANSFER" || pairedTransactionId != null,
    isManual = !isAutoCapturedNote(note)
)

enum class DedupKind { SAME_BANK, WALLET_BANK }
enum class DedupSurvivor { EXISTING, INCOMING }

/** How two rows are merged: which kind of duplicate, and which row's bank/account survives. */
data class DedupDecision(val kind: DedupKind, val survivor: DedupSurvivor)

/**
 * The single duplicate rule:
 *  - never for transfers or manual entries; type must match (an expense never merges with income, so no sign flip);
 *  - same absolute amount (non-zero);
 *  - SAME_BANK: equivalent bank (HSBC == Premier) or same account, within [DEDUP_WINDOW_MILLIS],
 *    merchants equal or one generic; when both merchants are known (and equal) only within
 *    [SAME_MERCHANT_DEDUP_WINDOW_MILLIS]. The existing row survives.
 *  - WALLET_BANK: one side a wallet (PayPal/Google Pay/...), the other a bank, within
 *    [WALLET_DEDUP_WINDOW_MILLIS], compatible merchants. The bank row survives.
 *  - two different wallets, or a wallet and an unknown ("Others") source: never.
 */
fun dedupDecision(existing: DedupKey, incoming: DedupKey): DedupDecision? {
    if (existing.isTransfer || incoming.isTransfer) return null
    if (existing.isManual || incoming.isManual) return null
    if (existing.type == TransactionType.TRANSFER || incoming.type == TransactionType.TRANSFER) return null
    if (existing.type != incoming.type) return null
    val amount = kotlin.math.abs(existing.amountMinor)
    if (amount == 0L || amount != kotlin.math.abs(incoming.amountMinor)) return null
    val dt = kotlin.math.abs(existing.timestamp - incoming.timestamp)

    val existingWallet = isWalletSource(existing.source)
    val incomingWallet = isWalletSource(incoming.source)
    if (existingWallet != incomingWallet) {
        if (dt > WALLET_DEDUP_WINDOW_MILLIS) return null
        val bank = if (existingWallet) incoming else existing
        val wallet = if (existingWallet) existing else incoming
        val bankName = normalizeBankName(bank.source)
        if (bankName == "Others") return null
        if (!isWalletMerchantCompatible(bank.merchant, bank.source, wallet.merchant, wallet.source)) return null
        return DedupDecision(DedupKind.WALLET_BANK, if (existingWallet) DedupSurvivor.INCOMING else DedupSurvivor.EXISTING)
    }

    if (dt > DEDUP_WINDOW_MILLIS) return null
    val sameAccount = existing.accountId != null && existing.accountId == incoming.accountId
    if (!sameAccount && !isSameOrEquivalentBank(existing.source, incoming.source)) return null
    val bank = resolvePreferredBankSource(existing.source, incoming.source)
    if (!isMerchantDuplicate(existing.merchant, incoming.merchant, bank)) return null
    val bothKnown = !isUnknownOrGenericMerchant(existing.merchant, bank) && !isUnknownOrGenericMerchant(incoming.merchant, bank)
    if (bothKnown && dt > SAME_MERCHANT_DEDUP_WINDOW_MILLIS) return null
    return DedupDecision(DedupKind.SAME_BANK, DedupSurvivor.EXISTING)
}

/**
 * Picks the stored row an incoming alert duplicates, or null. Preference: same-bank with both merchants
 * known, any same-bank, wallet pair with both merchants known, any wallet pair; ties go to the closest time.
 * Returns the index into [candidates].
 */
fun pickDedupMatch(candidates: List<DedupKey>, incoming: DedupKey): Pair<Int, DedupDecision>? {
    var best: Triple<Int, DedupDecision, Pair<Int, Long>>? = null
    candidates.forEachIndexed { index, candidate ->
        val decision = dedupDecision(candidate, incoming) ?: return@forEachIndexed
        val bothKnown = !isUnknownOrGenericMerchant(candidate.merchant, candidate.source) &&
            !isUnknownOrGenericMerchant(incoming.merchant, incoming.source)
        val tier = (if (decision.kind == DedupKind.SAME_BANK) 0 else 2) + (if (bothKnown) 0 else 1)
        val rank = tier to kotlin.math.abs(candidate.timestamp - incoming.timestamp)
        val current = best
        if (current == null || rank.first < current.third.first ||
            (rank.first == current.third.first && rank.second < current.third.second)
        ) {
            best = Triple(index, decision, rank)
        }
    }
    return best?.let { it.first to it.second }
}

/**
 * Merchant for the surviving row. SAME_BANK: the known merchant wins. WALLET_BANK: the wallet's
 * merchant replaces a bank merchant that is generic or only names the wallet ("PAYPAL *UBER" -> "Uber").
 */
fun mergedDedupMerchant(survivor: DedupKey, other: DedupKey, kind: DedupKind): String? {
    val survivorGeneric = isUnknownOrGenericMerchant(survivor.merchant, survivor.source)
    return when (kind) {
        DedupKind.SAME_BANK -> when {
            !survivorGeneric -> survivor.merchant
            !isUnknownOrGenericMerchant(other.merchant, other.source) -> other.merchant
            else -> null
        }
        DedupKind.WALLET_BANK -> {
            val otherUsable = !isGenericForPairing(other.merchant, other.source) && !merchantNamesWallet(other.merchant)
            when {
                otherUsable && (survivorGeneric || merchantNamesWallet(survivor.merchant)) -> other.merchant?.trim()
                survivorGeneric -> null
                else -> survivor.merchant
            }
        }
    }
}

/** Category for the surviving row: a specific category beats a default/blank one. */
fun mergedDedupCategory(survivorCategory: String?, otherCategory: String?): String? {
    fun isDefault(c: String?) = c.isNullOrBlank() || c == "OTHER_EXPENSE" || c == "OTHER_INCOME" || c == "General Spend"
    return if (isDefault(survivorCategory) && !isDefault(otherCategory)) otherCategory else survivorCategory
}

/**
 * View-level de-duplication with the same rules as insert time. Rows are processed in order; when a
 * row duplicates a kept one, [merge] (survivor, other, kind) produces the single row that is kept.
 */
fun <T> dedupeRows(rows: List<T>, key: (T) -> DedupKey, merge: (survivor: T, other: T, kind: DedupKind) -> T): List<T> {
    val kept = mutableListOf<T>()
    val keptKeys = mutableListOf<DedupKey>()
    for (row in rows) {
        val incoming = key(row)
        val hit = pickDedupMatch(keptKeys, incoming)
        if (hit == null) {
            kept.add(row)
            keptKeys.add(incoming)
            continue
        }
        val (index, decision) = hit
        val merged = if (decision.survivor == DedupSurvivor.EXISTING) {
            merge(kept[index], row, decision.kind)
        } else {
            merge(row, kept[index], decision.kind)
        }
        kept[index] = merged
        keptKeys[index] = key(merged)
    }
    return kept
}

fun isTransactionDuplicate(
    existingAmountMinor: Long,
    existingMerchant: String?,
    existingSource: String?,
    existingTimestamp: Long,
    newAmountMinor: Long,
    newMerchant: String?,
    newSource: String?,
    newTimestamp: Long,
    windowMillis: Long = DEDUP_WINDOW_MILLIS
): Boolean {
    if (kotlin.math.abs(existingTimestamp - newTimestamp) > windowMillis) return false
    fun typeOf(amount: Long) = if (amount > 0) TransactionType.INCOME else TransactionType.EXPENSE
    return dedupDecision(
        DedupKey(existingAmountMinor, typeOf(existingAmountMinor), existingSource, null, existingMerchant, existingTimestamp),
        DedupKey(newAmountMinor, typeOf(newAmountMinor), newSource, null, newMerchant, newTimestamp)
    ) != null
}

@Dao
abstract class TransactionDao {

    @Insert
    abstract suspend fun insert(transaction: TransactionEntity): Long

    @androidx.room.Update
    abstract suspend fun update(transaction: TransactionEntity)

    @Query("UPDATE transactions SET needsReview = :value WHERE id = :id")
    abstract suspend fun setNeedsReview(id: Long, value: Boolean)

    @Query("UPDATE transactions SET needsReview = 0 WHERE needsReview = 1")
    abstract suspend fun markAllReviewed(): Int

    @Query("SELECT * FROM transactions WHERE id = :id")
    abstract suspend fun getById(id: Long): TransactionEntity?

    /**
     * Candidate rows for duplicate detection: same absolute amount and same type within the widest
     * window, never transfers or paired legs. [dedupDecision] then applies the exact bank/merchant/window rule.
     */
    @Query(
        """
        SELECT * FROM transactions
        WHERE ABS(amount) = :absAmountMinor
          AND type = :type
          AND type != 'TRANSFER'
          AND (category IS NULL OR category != 'INTERNAL_TRANSFER')
          AND pairedTransactionId IS NULL
          AND ABS(timestamp - :timestamp) <= :windowMillis
        ORDER BY ABS(timestamp - :timestamp) ASC
        """
    )
    abstract suspend fun findDedupCandidates(
        absAmountMinor: Long,
        type: String,
        timestamp: Long,
        windowMillis: Long = MAX_DEDUP_WINDOW_MILLIS
    ): List<TransactionEntity>

    /** The stored row that [incoming] duplicates (see [dedupDecision]) and how to merge, or null. */
    suspend fun findDedupMatch(incoming: DedupKey): Pair<TransactionEntity, DedupDecision>? {
        if (incoming.isTransfer || incoming.type == TransactionType.TRANSFER) return null
        val candidates = findDedupCandidates(kotlin.math.abs(incoming.amountMinor), incoming.type.name, incoming.timestamp)
        val (index, decision) = pickDedupMatch(candidates.map { it.toDedupKey() }, incoming) ?: return null
        return candidates[index] to decision
    }

    /** Rows of the opposite type with the same amount near [timestamp]: possible other leg of a transfer. */
    @Query(
        """
        SELECT * FROM transactions
        WHERE ABS(amount) = :absAmountMinor
          AND type = :type
          AND ABS(timestamp - :timestamp) <= :windowMillis
        ORDER BY ABS(timestamp - :timestamp) ASC
        """
    )
    abstract suspend fun findSameAmountRows(
        absAmountMinor: Long,
        type: String,
        timestamp: Long,
        windowMillis: Long = TRANSFER_PAIR_WINDOW_MILLIS
    ): List<TransactionEntity>

    /** The stored opposite leg of an own-account transfer for a new alert (see [isOwnTransferPair]), or null. */
    suspend fun findTransferCounterpart(incoming: TransferLeg): TransactionEntity? {
        val opposite = when (incoming.type) {
            TransactionType.EXPENSE -> TransactionType.INCOME
            TransactionType.INCOME -> TransactionType.EXPENSE
            else -> return null
        }
        return findSameAmountRows(incoming.amountMinor, opposite.name, incoming.timestamp)
            .firstOrNull { isOwnTransferPair(incoming, it.toTransferLeg()) }
    }

    /** A second alert for a leg that was already matched as a transfer (same bank, amount, direction). */
    suspend fun isRepeatOfMatchedTransferLeg(incoming: TransferLeg): Boolean =
        findSameAmountRows(incoming.amountMinor, incoming.type.name, incoming.timestamp, MAX_DEDUP_WINDOW_MILLIS)
            .any { it.note?.contains(TRANSFER_MATCH_MARKER) == true && isSameOrEquivalentBank(it.source, incoming.source) }

    @Query("UPDATE transactions SET pairedTransactionId = :pairedId WHERE id = :id")
    abstract suspend fun setPairedId(id: Long, pairedId: Long)

    /** Deleting either leg of a transfer removes both, in one statement. */
    @Query("DELETE FROM transactions WHERE id = :id OR pairedTransactionId = :id")
    abstract suspend fun deleteWithPair(id: Long): Int

    /** Deletes exactly one row (e.g. the other leg when a transfer is re-filed as a payment). */
    @Query("DELETE FROM transactions WHERE id = :id")
    abstract suspend fun deleteById(id: Long): Int

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
