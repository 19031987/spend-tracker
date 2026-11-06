# SpendTracker v2.1 — Monarch-style planning features: spec

Status: approved for implementation. Inputs: `docs/research/monarch-research.md` (public-source
product research) and `docs/research/codebase-map.md` (architecture map). We copy *behaviour and
layout ideas* only — no Monarch branding, copy, logos or artwork.

## 0. Ground rules for every engineer

- Kotlin 1.9.22, Compose BOM 2024.02.00 (**material3 1.2.0**), Room 2.6.1, minSdk 26. Nothing can be
  compiled locally: CI compiles. Write conservative code: explicit types on public APIs, explicit
  imports (no wildcard), `@OptIn(ExperimentalMaterial3Api::class)` on anything using sheets, top bars,
  segmented buttons, DatePicker, SwipeToDismissBox. material3 1.2.0 signatures:
  `LinearProgressIndicator(progress = { f })`, `SwipeToDismissBox(state = rememberSwipeToDismissBoxState(confirmValueChange = …), backgroundContent = …) { … }`,
  `SearchBar(query, onQueryChange, onSearch, active, onActiveChange, …)`. `PullToRefreshBox` does NOT exist.
- Icons: `androidx.compose.material.icons.{filled,outlined}.*` (material-icons-extended is available).
- Theme: use `MaterialTheme.colorScheme.*`, `MaterialTheme.shapes.*`, `MaterialTheme.typography.*`
  and `SpendTrackerTheme.finance.{income, expense, surplusContainer, onSurplusContainer, deficitContainer, onDeficitContainer}`
  (`com.spendtracker.app.ui.theme.SpendTrackerTheme`). Cards: `Card` with
  `CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)` and
  `MaterialTheme.shapes.large`. Money: `CurrencyUtils.formatMinor(Long)` / `formatSignedMinor`.
- Money is `Long` minor units. `TransactionItem.amountMinor` is always positive; direction is `type`.
- "Counts as spending" = `type == EXPENSE && !excludeFromSpending && categoryKey != "INTERNAL_TRANSFER"`.
  "Counts as income" = `type == INCOME && !excludeFromSpending`.
- Pure logic lives in `domain/<feature>/` with **no `android.*` imports**; it takes `today: LocalDate`
  and `zone: ZoneId` as parameters (never calls `now()`), and has JUnit4 tests under
  `app/src/test/java/com/spendtracker/app/domain/<feature>/` in the existing style (backtick names,
  `ZoneId.of("UTC")`, fixed dates).
- ViewModels: `class XViewModel(…) : ViewModel()`, expose `val uiState: StateFlow<XUiState>` via
  `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), XUiState())`. Calculation is
  delegated to the pure domain function. Screens are stateless composables taking `uiState` + callbacks,
  plus a thin `XRoute(viewModel)` wrapper that collects with `collectAsStateWithLifecycle()`.
- Only edit files you own (section 6). If you need something from a file you don't own, note it in
  your final report instead of editing.
- Never depend on: TRANSFER rows or the INTERNAL_TRANSFER category (deleted on every DB open), an
  account named "Savings"/"Others" (deleted/remapped on open).

## 1. Information architecture

Bottom navigation, 5 destinations:

| Tab | Content |
|---|---|
| **Home** | Dashboard of cards (section 5.6) |
| **Transactions** | existing feed + search field + "Needs review" chip |
| **Cash flow** | section 5.4 |
| **Budget** | section 5.1 |
| **More** | list: Recurring, Goals, Review inbox, Insights (existing Analytics), Compare (existing), Settings |

Screens opened from More (and from Home cards) render full-screen inside the Scaffold content with a
back arrow in the top bar; system back returns (`BackHandler`). Owner: integration (section 6).

## 2. Data model — migration 7 → 8 (single owner: Foundation)

`AppDatabase.version = 8`, one `MIGRATION_7_8`, registered in `.addMigrations(…)`. New entities in
`data/PlanningEntities.kt`, new `@Dao abstract class PlanningDao` in the same file. **No
`@ColumnInfo(defaultValue)` on new tables; no foreign keys.** SQL must match Room exactly.

```sql
ALTER TABLE transactions ADD COLUMN needsReview INTEGER NOT NULL DEFAULT 0;
-- entity: @ColumnInfo(defaultValue = "0") val needsReview: Boolean = false   (last field)

CREATE TABLE IF NOT EXISTS `budgets` (`categoryKey` TEXT NOT NULL, `amountMinor` INTEGER NOT NULL,
  `rollover` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`categoryKey`));

CREATE TABLE IF NOT EXISTS `recurring_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
  `merchantKey` TEXT NOT NULL, `displayName` TEXT NOT NULL, `categoryKey` TEXT,
  `amountMinor` INTEGER NOT NULL, `frequency` TEXT NOT NULL, `anchorDate` INTEGER NOT NULL,
  `status` TEXT NOT NULL, `isIncome` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL);
CREATE UNIQUE INDEX IF NOT EXISTS `index_recurring_items_merchantKey` ON `recurring_items` (`merchantKey`);

CREATE TABLE IF NOT EXISTS `goals` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
  `name` TEXT NOT NULL, `emoji` TEXT NOT NULL, `colorHex` TEXT NOT NULL,
  `targetMinor` INTEGER NOT NULL, `targetDate` INTEGER, `startingMinor` INTEGER NOT NULL,
  `createdAt` INTEGER NOT NULL, `archived` INTEGER NOT NULL);

CREATE TABLE IF NOT EXISTS `goal_contributions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
  `goalId` INTEGER NOT NULL, `amountMinor` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL, `note` TEXT);
CREATE INDEX IF NOT EXISTS `index_goal_contributions_goalId` ON `goal_contributions` (`goalId`);
```

Entities (field order = column order above):
- `BudgetEntity(categoryKey: String @PrimaryKey, amountMinor: Long, rollover: Boolean, updatedAt: Long)`
- `RecurringItemEntity(id: Long = 0 autoGen, merchantKey: String, displayName: String, categoryKey: String?, amountMinor: Long, frequency: String /* RecurringFrequency.name */, anchorDate: Long /* epoch ms of a known occurrence */, status: String /* "APPROVED" | "IGNORED" */, isIncome: Boolean, createdAt: Long)`,
  `@Entity(tableName = "recurring_items", indices = [Index(value = ["merchantKey"], unique = true)])`
- `GoalEntity(id: Long = 0 autoGen, name: String, emoji: String, colorHex: String, targetMinor: Long, targetDate: Long?, startingMinor: Long, createdAt: Long, archived: Boolean)`
- `GoalContributionEntity(id: Long = 0 autoGen, goalId: Long, amountMinor: Long /* may be negative = withdrawal */, timestamp: Long, note: String?)`, index on `goalId`.

`PlanningDao`: `observeBudgets(): Flow<List<BudgetEntity>>`, `upsertBudget(b)` (`@Insert(onConflict = REPLACE)`),
`deleteBudget(categoryKey)`; `observeRecurring(): Flow<List<RecurringItemEntity>>`, `upsertRecurring(r): Long`
(REPLACE), `updateRecurring(r)`, `deleteRecurring(id)`; `observeGoals()`, `insertGoal(g): Long`,
`updateGoal(g)`, `deleteGoal(id)` (also deletes its contributions — `@Transaction` helper),
`observeContributions(): Flow<List<GoalContributionEntity>>`, `insertContribution(c): Long`, `deleteContribution(id)`.

`TransactionDao`: add `@Query("UPDATE transactions SET needsReview = :value WHERE id = :id") setNeedsReview(id, value)`
and `@Query("UPDATE transactions SET needsReview = 0 WHERE needsReview = 1") markAllReviewed(): Int`.

`TransactionItem` gains `val needsReview: Boolean = false` (mapped in `SpendRepository.observeTransactionItems`).
`SpendRepository` gains `suspend fun setNeedsReview(id: Long, value: Boolean)` and `suspend fun markAllReviewed()`.
Notification service: new captured rows get `needsReview = true`; manual entries `false`.

New `data/PlanningRepository.kt`: `class PlanningRepository(private val dao: PlanningDao)` exposing
the DAO flows and suspend writes 1:1 with domain-friendly names (`observeBudgets`, `setBudget(categoryKey, amountMinor, rollover)`,
`removeBudget`, `observeRecurring`, `saveRecurring(entity)`, `setRecurringStatus(id, status)`, `deleteRecurring`,
`observeGoals`, `createGoal(...)`, `updateGoal`, `deleteGoal`, `observeContributions`, `addContribution(goalId, amountMinor, note)`, `deleteContribution`).
`AppContainer` gets `val planningRepository = PlanningRepository(db.planningDao())`.

## 3. Shared domain helper (Foundation)

`domain/MonthMath.kt` (pure):
- `fun monthRange(month: YearMonth, zone: ZoneId): TimeRange` — `[1st 00:00, next 1st 00:00)`.
- `fun monthOf(timestamp: Long, zone: ZoneId): YearMonth`
- `fun monthProgress(month: YearMonth, today: LocalDate): Float` — 0 before month, 1 after, else `(today.dayOfMonth - 1 + 0.5f) / lengthOfMonth`.
- `fun TransactionItem.isSpending(): Boolean`, `fun TransactionItem.isIncome(): Boolean` (definitions in §0).

## 4. Pure domain APIs (each feature owns its package; exact names so other features can call them)

### 4.1 `domain/budget/BudgetCalculator.kt`
```kotlin
data class BudgetLine(val categoryKey: String, val name: String, val emoji: String, val colorHex: String,
  val groupId: Long, val groupName: String, val budgetedMinor: Long, val rolloverInMinor: Long,
  val spentMinor: Long) { val availableMinor: Long get() = budgetedMinor + rolloverInMinor - spentMinor
  val progress: Float get() = if (budgetedMinor + rolloverInMinor <= 0) (if (spentMinor > 0) 1f else 0f) else spentMinor.toFloat() / (budgetedMinor + rolloverInMinor) }
data class BudgetGroupSection(val groupId: Long, val groupName: String, val lines: List<BudgetLine>)
data class BudgetSummary(val month: YearMonth, val totalBudgetedMinor: Long, val totalSpentBudgetedMinor: Long,
  val unbudgetedSpentMinor: Long, val incomeMinor: Long, val leftToSpendMinor: Long,
  val monthProgress: Float, val sections: List<BudgetGroupSection>, val unbudgeted: List<BudgetLine>)
object BudgetCalculator {
  fun summarize(month: YearMonth, budgets: List<BudgetEntity>, categories: List<CategoryEntity>,
    groups: List<CategoryGroupEntity>, transactions: List<TransactionItem>, today: LocalDate, zone: ZoneId): BudgetSummary
}
```
Rules: spent per category = sum of spending items in `month`. Rollover (only when `rollover = true`):
`rolloverInMinor = budget − spent` of the **previous month only** (can be negative). `leftToSpend =
totalBudgeted + Σrollover − totalSpentBudgeted`. Unbudgeted = expense categories with spending but no
budget (budgeted 0). Sections ordered by group `sortOrder`, lines by spent desc. Hidden categories with
no budget and no spend are omitted.

### 4.2 `domain/recurring/RecurringDetector.kt`
```kotlin
enum class RecurringFrequency(val label: String, val days: Int) { WEEKLY("Weekly",7), BIWEEKLY("Every 2 weeks",14),
  MONTHLY("Monthly",30), QUARTERLY("Quarterly",91), YEARLY("Yearly",365) }
data class RecurringCandidate(val merchantKey: String, val displayName: String, val categoryKey: String?,
  val typicalAmountMinor: Long, val frequency: RecurringFrequency, val lastDate: LocalDate,
  val nextDate: LocalDate, val occurrences: Int, val amountVaries: Boolean, val isIncome: Boolean)
enum class OccurrenceStatus { PAID, PAID_DIFFERENT_AMOUNT, UPCOMING, OVERDUE }
data class RecurringOccurrence(val itemId: Long, val displayName: String, val categoryKey: String?,
  val expectedDate: LocalDate, val expectedAmountMinor: Long, val actualAmountMinor: Long?,
  val status: OccurrenceStatus, val isIncome: Boolean)
object RecurringDetector {
  fun merchantKey(merchant: String): String   // lowercase, trim, collapse spaces, strip digits/punctuation tails
  fun detect(transactions: List<TransactionItem>, today: LocalDate, zone: ZoneId): List<RecurringCandidate>
  fun nextDate(anchor: LocalDate, frequency: RecurringFrequency, onOrAfter: LocalDate): LocalDate
  fun occurrencesInMonth(items: List<RecurringItemEntity>, month: YearMonth, transactions: List<TransactionItem>,
    today: LocalDate, zone: ZoneId): List<RecurringOccurrence>
}
```
Detection: group non-excluded items with a non-null merchant by `merchantKey`, same direction;
need ≥ 3 occurrences (≥ 2 for YEARLY); median gap within tolerance of a frequency (WEEKLY 6–8,
BIWEEKLY 13–16, MONTHLY 26–35, QUARTERLY 85–98, YEARLY 350–380 days) and ≥ 70 % of gaps within
tolerance; last occurrence not older than 2× the period. `typicalAmount` = median; `amountVaries` if
max/min > 1.10. Calendar steps use `plusWeeks/plusMonths/plusYears` from the anchor (MONTHLY keeps day
of month, clamped). Occurrence matching: a transaction of the same merchantKey and direction within
±4 days of the expected date (±2 for weekly) → PAID, or PAID_DIFFERENT_AMOUNT if |actual−expected| >
max(5 % of expected, 100 minor); else expected date < today → OVERDUE, else UPCOMING. Sort by date.

### 4.3 `domain/cashflow/CashFlowCalculator.kt`
```kotlin
data class CashFlowMonth(val month: YearMonth, val incomeMinor: Long, val expenseMinor: Long) {
  val savingsMinor: Long get() = incomeMinor - expenseMinor
  val savingsRate: Float? get() = if (incomeMinor > 0) savingsMinor.toFloat() / incomeMinor else null }
enum class BreakdownDimension { CATEGORY, GROUP, MERCHANT }
data class BreakdownRow(val key: String, val label: String, val emoji: String, val colorHex: String,
  val amountMinor: Long, val share: Float, val transactionCount: Int)
data class CashFlowReport(val months: List<CashFlowMonth>, val selected: CashFlowMonth,
  val expenseBreakdown: List<BreakdownRow>, val incomeBreakdown: List<BreakdownRow>,
  val averageMonthlyExpenseMinor: Long, val averageMonthlyIncomeMinor: Long)
object CashFlowCalculator {
  fun monthly(transactions: List<TransactionItem>, endMonth: YearMonth, count: Int, zone: ZoneId): List<CashFlowMonth> // oldest→newest, zero-filled
  fun breakdown(transactions: List<TransactionItem>, month: YearMonth, dimension: BreakdownDimension, income: Boolean,
    categories: List<CategoryEntity>, groups: List<CategoryGroupEntity>, zone: ZoneId): List<BreakdownRow> // desc
  fun report(transactions: List<TransactionItem>, selectedMonth: YearMonth, monthsBack: Int, dimension: BreakdownDimension,
    categories: List<CategoryEntity>, groups: List<CategoryGroupEntity>, zone: ZoneId): CashFlowReport
}
```
Unknown merchant label "Unknown vendor". Averages over months in range that have any activity.

### 4.4 `domain/goals/GoalCalculator.kt`
```kotlin
enum class GoalStatus { COMPLETE, ON_TRACK, BEHIND, NO_TARGET_DATE }
data class GoalProgress(val goalId: Long, val name: String, val emoji: String, val colorHex: String,
  val targetMinor: Long, val savedMinor: Long, val remainingMinor: Long, val fraction: Float,
  val targetDate: LocalDate?, val monthlyNeededMinor: Long?, val averageMonthlyContributionMinor: Long,
  val projectedCompletion: LocalDate?, val status: GoalStatus)
object GoalCalculator {
  fun progress(goal: GoalEntity, contributions: List<GoalContributionEntity>, today: LocalDate, zone: ZoneId): GoalProgress
}
```
saved = startingMinor + Σcontributions (clamped ≥ 0). monthlyNeeded = remaining / months left
(ceil, min 1 month) when targetDate set. Average = contributions over last 3 months / 3. Projection =
today + remaining/avg months when avg > 0. Status: saved ≥ target → COMPLETE; no date → NO_TARGET_DATE;
avg ≥ monthlyNeeded (or projected ≤ targetDate) → ON_TRACK else BEHIND.

### 4.5 `domain/dashboard/MonthSummaryCalculator.kt` (Dashboard owner)
Extract the hero-card maths currently inline in `TransactionsFeed` (month spend, month income,
carried-over, net remaining) into a pure tested function; the feed keeps working unchanged
(integration may switch it later).

## 5. Screens

### 5.1 Budget (`ui/budget/`)
- Top: month selector (`‹ November 2026 ›`). Hero card: "Left to spend" big number (red if negative),
  `spent of budgeted`, a progress bar with a vertical **today marker** at `monthProgress` (draw with
  `Canvas`/`drawBehind`), and a hint "On pace" / "Ahead of pace" when progress > monthProgress + 0.05.
- Sections per category group (header: group name, group budgeted/spent). Row: emoji avatar, name,
  `£x left` or `£x over` (error colour), thin progress bar (colour: primary; ≥ 100 % error), rollover
  chip when rollover ≠ 0. Tap row → `EditBudgetSheet` (amount field, rollover switch, Remove budget).
- "Unbudgeted spending" section with "Set budget" action per row.
- Empty state: "Set budgets for your categories" + button that pre-fills each expense category with its
  last-3-month average spend (rounded up to whole currency units) — the user confirms in a sheet.

### 5.2 Recurring (`ui/recurring/`)
- Segmented control: **Upcoming · Calendar · All**.
- Summary card for current month: "Remaining due this month £x", counts paid/upcoming.
- Upcoming: occurrences from today in the next 30 days, grouped "This week / Later this month / Next month"; row shows date pill, name, amount, status chip (paid green / different amount amber-ish tertiary / upcoming blue-ish secondary / overdue error).
- Calendar: month grid (Mon-first), each day cell shows dots coloured by status; tapping a day lists its items. `‹ month ›` navigation.
- All: approved items with frequency, next date, typical amount; overflow menu: Edit (amount, frequency), Stop tracking (status IGNORED).
- **Suggestions** banner/section: candidates from `RecurringDetector.detect` whose merchantKey has no stored item: "We found N possible recurring payments" → list with Approve / Ignore buttons (both persist an item with the respective status).

### 5.3 Goals (`ui/goals/`)
- List of goal cards: emoji, name, `£saved of £target`, progress bar in goal colour, status chip
  (On track / Behind / Complete), "£x/month needed by Mar 2027".
- FAB/Button "New goal" → sheet: name, emoji (preset row), colour (preset swatches), target amount,
  optional target date (M3 `DatePicker` in `DatePickerDialog`), starting amount.
- Goal detail (full screen): big progress ring (Canvas arc), stats (saved, remaining, monthly needed,
  projected date), contribution history list, buttons "Add money" / "Withdraw" → amount + note sheet,
  overflow: Edit, Archive, Delete (confirm dialog).

### 5.4 Cash flow (`ui/cashflow/`)
- Month selector + range chips (6M / 12M).
- Summary row of 3 stat tiles: Income, Expenses, Saved (+ savings-rate % ). 
- Bar chart (Canvas): per month a pair of bars income (finance.income) / expense (colorScheme.primary),
  selected month highlighted; tap a bar selects its month. Y-axis gridlines with compact labels.
- Breakdown: segmented **Category · Group · Merchant**, tab row Expenses/Income; rows with avatar,
  label, amount, share % and a proportional bar. Tap row → bottom sheet listing that row's transactions.

### 5.5 Review inbox (`ui/review/`)
- Shows `needsReview` transactions newest first, as cards: merchant, amount, date, account, category pill.
- Each card: **Mark reviewed** (check) button; swipe right = reviewed (`SwipeToDismissBox`); tapping the
  category pill invokes a callback `onChangeCategory(tx)` (integration wires it to the existing
  `CategoryPickerDialog`). Top bar action "Mark all reviewed" with confirm. Empty state "All caught up".

### 5.6 Dashboard (`ui/dashboard/`)
Cards in order: (1) month summary (reuse visual of current hero: spent this month, income, left/over),
(2) Budget "Left to spend" mini card with progress + today marker, (3) Review inbox count ("N
transactions to review" → open review), (4) Upcoming recurring next 7 days (max 4 rows), (5) Top
spending categories this month (max 5 with bars), (6) Goals (max 3 mini progress rows), (7) Recent
transactions (5 rows, "See all"). Each card has a header with title and a chevron action. Navigation is
via callbacks (`onOpenBudget`, `onOpenRecurring`, `onOpenReview`, `onOpenGoals`, `onOpenTransactions`,
`onOpenTransaction(tx)`).

## 6. Work breakdown & file ownership

| Phase | Owner | Files (create unless marked edit) |
|---|---|---|
| 1 | **Foundation** | edit `data/AppDatabase.kt`, `data/TransactionEntity.kt`, `data/TransactionDao.kt`, `data/SpendRepository.kt`, `domain/SpendModels.kt`, `service/SpendNotificationListenerService.kt`, `AppContainer.kt`; create `data/PlanningEntities.kt`, `data/PlanningRepository.kt`, `domain/MonthMath.kt` + test. Also fix the `"EXACT"` → `"EQUALS"` rule bug in `ui/TransactionsViewModel.kt`. |
| 2 | **Budget** | `domain/budget/*`, `ui/budget/*`, tests |
| 2 | **Recurring** | `domain/recurring/*`, `ui/recurring/*`, tests |
| 2 | **Goals** | `domain/goals/*`, `ui/goals/*`, tests |
| 2 | **Cash flow** | `domain/cashflow/*`, `ui/cashflow/*`, tests |
| 2 | **Review** | `ui/review/*` |
| 3 | **Dashboard** | `domain/dashboard/*`, `ui/dashboard/*`, tests |
| 3 | **Integration** (lead) | `ui/SpendTrackerScreen.kt`, `AppContainer.kt` (VM registration), new `ui/MoreScreen.kt`, search/needs-review chip in the feed, version bump to 2.1.0 (versionCode 28), CI release notes |

Each Phase-2/3 agent's VM constructor takes only `SpendRepository` and/or `PlanningRepository` and
must be registrable as `initializer { XViewModel(repository, planningRepository) }`.

## 7. Out of scope for v2.1 (documented for later)
Bill reminders (needs WorkManager + POST_NOTIFICATIONS), splits, tags, attachments, rule builder with
amount/account conditions, customisable dashboard order, weekly recap notification, account balances /
net worth (no aggregation), flex-budget buckets, investments.
