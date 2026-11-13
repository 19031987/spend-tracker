package com.spendtracker.app.ui.recurring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendtracker.app.data.CategoryEntity
import com.spendtracker.app.data.PlanningRepository
import com.spendtracker.app.data.RecurringItemEntity
import com.spendtracker.app.data.SpendRepository
import com.spendtracker.app.domain.TransactionItem
import com.spendtracker.app.domain.recurring.ManualRecurring
import com.spendtracker.app.domain.recurring.OccurrenceStatus
import com.spendtracker.app.domain.recurring.RecurringCandidate
import com.spendtracker.app.domain.recurring.RecurringDetector
import com.spendtracker.app.domain.recurring.RecurringDraft
import com.spendtracker.app.domain.recurring.RecurringFrequency
import com.spendtracker.app.domain.recurring.RecurringOccurrence
import com.spendtracker.app.domain.recurring.RecurringSaveDecision
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

enum class RecurringView(val label: String) {
    UPCOMING("Upcoming"),
    CALENDAR("Calendar"),
    ALL("All")
}

/** An approved recurring item together with its next expected date. */
data class RecurringItemRow(
    val item: RecurringItemEntity,
    val frequency: RecurringFrequency,
    val nextDate: LocalDate
)

/** Summary of the current calendar month (today's month, independent of calendar navigation). */
data class RecurringMonthSummary(
    val month: YearMonth,
    val remainingDueMinor: Long = 0L,
    val paidCount: Int = 0,
    val upcomingCount: Int = 0,
    val overdueCount: Int = 0
)

data class RecurringUiState(
    val view: RecurringView = RecurringView.UPCOMING,
    val today: LocalDate = LocalDate.now(),
    /** Month shown in the calendar view. */
    val month: YearMonth = YearMonth.now(),
    val selectedDay: LocalDate? = null,
    val items: List<RecurringItemRow> = emptyList(),
    /** Occurrences of [month], sorted by date. */
    val monthOccurrences: List<RecurringOccurrence> = emptyList(),
    /** Overdue payments of this month plus everything due in the next 30 days, sorted by date. */
    val upcoming: List<RecurringOccurrence> = emptyList(),
    val summary: RecurringMonthSummary = RecurringMonthSummary(YearMonth.now()),
    val suggestions: List<RecurringCandidate> = emptyList(),
    /** Items the user ignored or stopped tracking, sorted by name. */
    val ignored: List<RecurringItemEntity> = emptyList(),
    val categories: Map<String, CategoryEntity> = emptyMap()
)

internal data class Selection(
    val view: RecurringView = RecurringView.UPCOMING,
    val month: YearMonth = YearMonth.now(),
    val selectedDay: LocalDate? = null
)

class RecurringViewModel(
    private val repository: SpendRepository,
    private val planningRepository: PlanningRepository
) : ViewModel() {

    private val selection: MutableStateFlow<Selection> = MutableStateFlow(Selection())

    val uiState: StateFlow<RecurringUiState> = combine(
        repository.observeTransactionItems(),
        repository.observeCategories(),
        planningRepository.observeRecurring(),
        selection
    ) { transactions: List<TransactionItem>,
        categories: List<CategoryEntity>,
        stored: List<RecurringItemEntity>,
        sel: Selection ->
        buildState(transactions, categories, stored, sel)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecurringUiState())

    private fun buildState(
        transactions: List<TransactionItem>,
        categories: List<CategoryEntity>,
        stored: List<RecurringItemEntity>,
        sel: Selection
    ): RecurringUiState {
        val zone: ZoneId = ZoneId.systemDefault()
        val today: LocalDate = LocalDate.now(zone)
        val currentMonth: YearMonth = YearMonth.from(today)
        val approved: List<RecurringItemEntity> = stored.filter { it.status == STATUS_APPROVED }

        val rows: List<RecurringItemRow> = approved.map { item ->
            val frequency: RecurringFrequency = RecurringDetector.parseFrequency(item.frequency)
            val anchor: LocalDate = Instant.ofEpochMilli(item.anchorDate).atZone(zone).toLocalDate()
            RecurringItemRow(item, frequency, RecurringDetector.nextDate(anchor, frequency, today))
        }.sortedWith(compareBy<RecurringItemRow> { it.nextDate }.thenBy { it.item.displayName })

        val monthOccurrences: List<RecurringOccurrence> =
            RecurringDetector.occurrencesInMonth(approved, sel.month, transactions, today, zone)

        val thisMonth: List<RecurringOccurrence> =
            if (sel.month == currentMonth) monthOccurrences
            else RecurringDetector.occurrencesInMonth(approved, currentMonth, transactions, today, zone)

        val windowEnd: LocalDate = today.plusDays(30)
        val spanned: List<RecurringOccurrence> = buildList {
            var m: YearMonth = currentMonth
            val lastMonth: YearMonth = YearMonth.from(windowEnd)
            while (!m.isAfter(lastMonth)) {
                addAll(
                    if (m == currentMonth) thisMonth
                    else RecurringDetector.occurrencesInMonth(approved, m, transactions, today, zone)
                )
                m = m.plusMonths(1)
            }
        }
        val upcoming: List<RecurringOccurrence> = spanned
            .filter { occ ->
                (!occ.expectedDate.isBefore(today) && occ.expectedDate.isBefore(windowEnd)) ||
                    (occ.status == OccurrenceStatus.OVERDUE && YearMonth.from(occ.expectedDate) == currentMonth)
            }
            .sortedWith(compareBy<RecurringOccurrence> { it.expectedDate }.thenBy { it.displayName })

        val unpaid: List<RecurringOccurrence> = thisMonth.filter {
            it.status == OccurrenceStatus.UPCOMING || it.status == OccurrenceStatus.OVERDUE
        }
        val summary = RecurringMonthSummary(
            month = currentMonth,
            remainingDueMinor = unpaid.filter { !it.isIncome }.sumOf { it.expectedAmountMinor },
            paidCount = thisMonth.count {
                it.status == OccurrenceStatus.PAID || it.status == OccurrenceStatus.PAID_DIFFERENT_AMOUNT
            },
            upcomingCount = thisMonth.count { it.status == OccurrenceStatus.UPCOMING },
            overdueCount = thisMonth.count { it.status == OccurrenceStatus.OVERDUE }
        )

        val knownKeys: Set<String> = stored.map { it.merchantKey }.toSet()
        val suggestions: List<RecurringCandidate> = RecurringDetector
            .detect(transactions, today, zone)
            .filter { it.merchantKey !in knownKeys }

        return RecurringUiState(
            view = sel.view,
            today = today,
            month = sel.month,
            selectedDay = sel.selectedDay,
            items = rows,
            monthOccurrences = monthOccurrences,
            upcoming = upcoming,
            summary = summary,
            suggestions = suggestions,
            ignored = stored
                .filter { it.status == STATUS_IGNORED }
                .sortedBy { it.displayName.lowercase() },
            categories = categories.associateBy { it.key }
        )
    }

    fun selectView(view: RecurringView) {
        selection.update { it.copy(view = view) }
    }

    fun previousMonth() {
        selection.update { it.copy(month = it.month.minusMonths(1), selectedDay = null) }
    }

    fun nextMonth() {
        selection.update { it.copy(month = it.month.plusMonths(1), selectedDay = null) }
    }

    fun selectDay(day: LocalDate?) {
        selection.update { it.copy(selectedDay = day) }
    }

    fun approve(candidate: RecurringCandidate) {
        persistCandidate(candidate, STATUS_APPROVED)
    }

    fun ignore(candidate: RecurringCandidate) {
        persistCandidate(candidate, STATUS_IGNORED)
    }

    /** Moves an approved item to the Ignored list (it can be restored from there). */
    fun stopTracking(id: Long) {
        viewModelScope.launch {
            planningRepository.setRecurringStatus(id, STATUS_IGNORED)
        }
    }

    /** Ignored item back to tracked. */
    fun restore(id: Long) {
        viewModelScope.launch {
            planningRepository.setRecurringStatus(id, STATUS_APPROVED)
        }
    }

    /** Deletes the row, so the payment can be detected and suggested again. */
    fun delete(id: Long) {
        viewModelScope.launch {
            planningRepository.deleteRecurring(id)
        }
    }

    /**
     * Adds a recurring payment entered by hand. Returns an error message to show (nothing saved),
     * or null when the item is being saved.
     */
    fun addManual(draft: RecurringDraft): String? {
        val zone: ZoneId = ZoneId.systemDefault()
        val decision: RecurringSaveDecision = ManualRecurring.decideAdd(
            draft = draft,
            existing = storedItems(),
            zone = zone,
            today = LocalDate.now(zone),
            nowMillis = System.currentTimeMillis()
        )
        return persist(decision)
    }

    /**
     * Saves an edit of item [id] (name, amount, in/out, frequency, date, category). Returns an
     * error message to show (nothing saved), or null when the item is being saved.
     */
    fun updateItem(id: Long, draft: RecurringDraft): String? {
        val decision: RecurringSaveDecision = ManualRecurring.decideEdit(
            id = id,
            draft = draft,
            existing = storedItems(),
            zone = ZoneId.systemDefault()
        )
        return persist(decision)
    }

    private fun storedItems(): List<RecurringItemEntity> {
        val state: RecurringUiState = uiState.value
        return state.items.map { it.item } + state.ignored
    }

    private fun persist(decision: RecurringSaveDecision): String? {
        when (decision) {
            is RecurringSaveDecision.Reject -> return decision.message
            // decideAdd guarantees no other row owns the key, so the REPLACE insert never clobbers one.
            is RecurringSaveDecision.Insert -> viewModelScope.launch {
                planningRepository.saveRecurring(decision.entity)
            }
            is RecurringSaveDecision.Update -> viewModelScope.launch {
                planningRepository.updateRecurring(decision.entity)
            }
        }
        return null
    }

    private fun persistCandidate(candidate: RecurringCandidate, status: String) {
        val zone: ZoneId = ZoneId.systemDefault()
        val entity = RecurringItemEntity(
            merchantKey = candidate.merchantKey,
            displayName = candidate.displayName,
            categoryKey = candidate.categoryKey,
            amountMinor = candidate.typicalAmountMinor,
            frequency = candidate.frequency.name,
            anchorDate = candidate.lastDate.atStartOfDay(zone).toInstant().toEpochMilli(),
            status = status,
            isIncome = candidate.isIncome,
            createdAt = System.currentTimeMillis()
        )
        viewModelScope.launch {
            planningRepository.saveRecurring(entity)
        }
    }

    private companion object {
        const val STATUS_APPROVED: String = ManualRecurring.STATUS_APPROVED
        const val STATUS_IGNORED: String = ManualRecurring.STATUS_IGNORED
    }
}
