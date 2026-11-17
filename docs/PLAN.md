# PLAN: app evaluation fixes (v2.3.0)

## Goal
Fix the issues found by the three-part evaluation (parser corpus, UI flows, money logic): batches A–D, approved by the user.

## Team and file ownership (parallel; no two agents edit the same file)
| Agent | Owns | Items |
|---|---|---|
| Parser | `parser/SpendParser.kt`, `test_spend_parser.py`, `app/src/test/**/parser/*`, `domain/CategorizationEngine.kt` + `test_categorization_engine.py` (only if needed) | A3 PayPal "You sent" → OUT; A4 Chase round-up / "moved … to your … account" not spend; B7 `*` merchants (PAYPAL *NETFLIX, AMZN Mktp UK*…, UBER *TRIP); B8 title+body join (vendor from title, "sent you", "Paid €x at Lidl"); B9 "into your account", TfL "charge"; B10 Plc/AB/UAB, SumUp/SQ prefixes, Cafe Nero → Dining; mirror drift (Premier in OUT_VENDOR_AMOUNT) |
| Data | `data/TransactionDao.kt`, `data/SpendRepository.kt`, `service/SpendNotificationListenerService.kt`, `data/AppDatabase.kt` (repair only), `test_bank_deduplication.py`, data tests | A1 cross-source dedup (PayPal / Google Pay + bank); A2 dedup must match type, never flip sign, never touch TRANSFER; insert window = view window |
| Metrics | `ui/ComparisonViewModel.kt`, `ui/AnalyticsViewModel.kt`, `domain/**` except CategorizationEngine/recurring UI, `ui/dashboard/DashboardViewModel.kt`, `ui/budget/BudgetViewModel.kt`, `ui/cashflow/CashFlowViewModel.kt`, `ui/goals/GoalsViewModel.kt`, domain tests | A5 no fake sample data; A6 like-for-like period deltas; D20 rollover only from budget creation month; D21 goal pace / past deadline; D22 refunds reduce spend; D23 exclude current month from cash-flow averages; D24 overdue after match window; D25 one week start; D26 fresh "today" |
| UI | `ui/SpendTrackerScreen.kt`, `ui/TransactionsViewModel.kt`, `ui/EditTransactionSheet.kt`, `ui/CategoryPickerSheet.kt`, `ui/AddTransactionSheet.kt`, `ui/dashboard/DashboardScreen.kt`, `ui/goals/GoalsScreen.kt`, `ui/budget/BudgetScreen.kt`, `ui/settings/RulesSection.kt`, `ui/recurring/RecurringScreen.kt`, `ui/components/*`, `ui/cashflow/CashFlowScreen.kt`, `ui/ComparisonScreen.kt`, `ui/AnalyticsScreen.kt` | C11 pill must not create rules silently; C12 transfers visible + editable; C13 new category from Edit/quick picker/Add applied; C14 edit date; C15 save/delete feedback; C16 tappable Home rows + account cards; C17 delete confirmations; C18 rememberSaveable for open sheets; C19 "No matches" + clear filters; 0 amount handling; a11y (pill touch target, swatch labels); empty state for Comparison when no data |
| Reviewer | read-only | compile + logic review of the branch; fixes routed to owners |

## Steps
- [ ] 1. Four agents in parallel, each with regression tests.
- [ ] 2. Reviewer pass; owners fix findings.
- [ ] 3. Python mirror tests; bump to v2.3.0 with notes; one commit per batch; PR; CI green; merge (user asked for a release).

## Constraints
No Room schema change, no new dependencies, no network calls. Python mirrors stay 1:1. No Android SDK here: CI verifies Kotlin.
