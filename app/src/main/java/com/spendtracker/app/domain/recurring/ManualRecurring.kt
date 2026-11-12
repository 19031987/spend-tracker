package com.spendtracker.app.domain.recurring

import com.spendtracker.app.data.RecurringItemEntity
import java.time.LocalDate
import java.time.ZoneId

/**
 * What the user entered in the add/edit recurring sheet, after the amount text has been parsed.
 *
 * @param date next (or any known) payment date. `null` on an edit means "date not changed":
 *   the stored anchor is kept so the item's past occurrences stay where they were.
 */
data class RecurringDraft(
    val name: String,
    val amountMinor: Long,
    val isIncome: Boolean,
    val frequency: RecurringFrequency,
    val date: LocalDate?,
    val categoryKey: String?
)

/** Field errors of the add/edit recurring form; both null when the form can be saved. */
data class RecurringFormErrors(
    val name: String? = null,
    val amount: String? = null
) {
    val isValid: Boolean get() = name == null && amount == null
}

/** Outcome of saving a [RecurringDraft] against the rows already stored. */
sealed interface RecurringSaveDecision {
    /** Insert a brand-new row (id = 0). */
    data class Insert(val entity: RecurringItemEntity) : RecurringSaveDecision

    /** Overwrite an existing row, keeping its id (an edit, or an IGNORED row being reused). */
    data class Update(val entity: RecurringItemEntity) : RecurringSaveDecision

    /** Nothing is saved; [message] is shown to the user. */
    data class Reject(val message: String) : RecurringSaveDecision
}

/**
 * Pure rules for recurring payments the user adds or edits by hand. The merchant key always comes
 * from [RecurringDetector.merchantKey] when a new item is added (edits keep the stored key), and a
 * key is never silently overwritten (the DAO upsert uses REPLACE on the unique merchantKey index).
 */
object ManualRecurring {

    const val STATUS_APPROVED: String = "APPROVED"
    const val STATUS_IGNORED: String = "IGNORED"
    const val MAX_NAME_LENGTH: Int = 60

    /** Trimmed name with inner whitespace collapsed, as stored in `displayName`. */
    fun cleanName(name: String): String = name.trim().replace(Regex("\\s+"), " ")

    /**
     * Validates the raw form input.
     *
     * @param amountText what the user typed in the amount field.
     * @param parsedAmountMinor [amountText] parsed to minor units (e.g. by
     *   `CurrencyUtils.parseDecimalToMinor`), or null when it could not be parsed.
     */
    fun validate(name: String, amountText: String, parsedAmountMinor: Long?): RecurringFormErrors {
        val cleaned: String = cleanName(name)
        val nameError: String? = when {
            cleaned.isEmpty() -> "Enter a name"
            cleaned.length > MAX_NAME_LENGTH -> "Keep the name under $MAX_NAME_LENGTH characters"
            RecurringDetector.merchantKey(cleaned).isEmpty() -> "Enter a name"
            else -> null
        }
        val amountError: String? = when {
            amountText.isBlank() -> "Enter an amount"
            parsedAmountMinor == null -> "Enter an amount like 9.99"
            parsedAmountMinor <= 0L -> "Amount must be more than zero"
            else -> null
        }
        return RecurringFormErrors(name = nameError, amount = amountError)
    }

    /**
     * Decides how to store a new item:
     * - no row with the same merchant key → [RecurringSaveDecision.Insert] (APPROVED);
     * - an APPROVED row with that key → [RecurringSaveDecision.Reject] "You already track …";
     * - an IGNORED row with that key → [RecurringSaveDecision.Update] of that row (same id and
     *   createdAt) with the new values and status APPROVED.
     *
     * @param today used as the anchor when [RecurringDraft.date] is null.
     */
    fun decideAdd(
        draft: RecurringDraft,
        existing: List<RecurringItemEntity>,
        zone: ZoneId,
        today: LocalDate,
        nowMillis: Long
    ): RecurringSaveDecision {
        val name: String = cleanName(draft.name)
        val key: String = RecurringDetector.merchantKey(name)
        if (key.isEmpty() || draft.amountMinor <= 0L) return RecurringSaveDecision.Reject("Check the name and amount")
        val anchor: Long = toEpochMillis(draft.date ?: today, zone)
        val conflict: RecurringItemEntity? = existing.firstOrNull { it.merchantKey == key }
        return when {
            conflict == null -> RecurringSaveDecision.Insert(
                RecurringItemEntity(
                    merchantKey = key,
                    displayName = name,
                    categoryKey = draft.categoryKey,
                    amountMinor = draft.amountMinor,
                    frequency = draft.frequency.name,
                    anchorDate = anchor,
                    status = STATUS_APPROVED,
                    isIncome = draft.isIncome,
                    createdAt = nowMillis
                )
            )
            conflict.status == STATUS_IGNORED -> RecurringSaveDecision.Update(
                conflict.copy(
                    displayName = name,
                    categoryKey = draft.categoryKey,
                    amountMinor = draft.amountMinor,
                    frequency = draft.frequency.name,
                    anchorDate = anchor,
                    status = STATUS_APPROVED,
                    isIncome = draft.isIncome
                )
            )
            else -> RecurringSaveDecision.Reject("You already track ${conflict.displayName}")
        }
    }

    /**
     * Decides how to store an edit of row [id]. The merchant key is never changed by an edit (it
     * links the item to its real transactions); only the display name and the other fields change.
     * A null [RecurringDraft.date] keeps the stored anchor. Status and createdAt are kept.
     */
    fun decideEdit(
        id: Long,
        draft: RecurringDraft,
        existing: List<RecurringItemEntity>,
        zone: ZoneId
    ): RecurringSaveDecision {
        val target: RecurringItemEntity = existing.firstOrNull { it.id == id }
            ?: return RecurringSaveDecision.Reject("This payment no longer exists")
        val name: String = cleanName(draft.name)
        if (name.isEmpty() || draft.amountMinor <= 0L) return RecurringSaveDecision.Reject("Check the name and amount")
        return RecurringSaveDecision.Update(
            target.copy(
                displayName = name,
                categoryKey = draft.categoryKey,
                amountMinor = draft.amountMinor,
                frequency = draft.frequency.name,
                anchorDate = draft.date?.let { toEpochMillis(it, zone) } ?: target.anchorDate,
                isIncome = draft.isIncome
            )
        )
    }

    private fun toEpochMillis(date: LocalDate, zone: ZoneId): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli()
}
