# PLAN: manual recurring payments, missing management features, consistent styling (v2.2.0)

## Goal
1. Let the user add recurring payments by hand (More → Recurring), edit them fully and restore ignored items.
2. Fill the "can see but can't manage" gaps: archived goals, accounts, categories, export/import.
3. One consistent look across screens and sheets: spacing, cards, section headers, empty states, buttons (save / cancel / delete / add-new), dark-mode-safe colours.

## Out of scope
- Recurring payments in Cash Flow / Budget forecasts (separate change).
- Database schema changes unless unavoidable (would need a Room migration; flag first).
- Parser / categorisation logic (Python mirrors untouched).

## Team and file ownership (no two agents edit the same file)
| Agent | Owns |
|---|---|
| Design system (runs first) | `ui/theme/Dimens.kt` (new), `ui/theme/*`, `ui/components/*` |
| Recurring | `ui/recurring/*`, `domain/recurring/*`, `app/src/test/**/recurring/*` |
| Screens | `ui/dashboard/*`, `ui/SpendTrackerScreen.kt`, `ui/HeaderBar.kt`, `ui/Analytics*`, `ui/Comparison*`, `ui/budget/*`, `ui/cashflow/*` |
| Sheets + Goals | `ui/AddTransaction*`, `ui/EditTransactionSheet.kt`, `ui/TransactionsViewModel.kt`, `ui/CategoryPickerSheet.kt`, `ui/goals/*`, `ui/review/*`, `ui/MoreScreen.kt` |
| Settings + data | `ui/SettingsSheet.kt`, `ui/SettingsViewModel.kt`, new `ui/settings/*`, `data/*` (SpendRepository, PlanningRepository, AccountEntity, CatalogEntities), `domain/ExportFormatter.kt`, new import code, `AppContainer.kt`, matching tests |
| Reviewer | read-only review of the branch; fixes routed back to owners |

## Steps
- [x] 1. Design system: spacing tokens + shared `AppCard`, `SectionHeader`, `EmptyState`, `SheetActions` (Cancel left / primary right, both full-width), `PrimaryButton`, `DestructiveButton`, `StatTile`, `AddNewButton` conventions; document in `ui/components/README.md`.
- [x] 2a. Recurring: "Add recurring payment" sheet (name, amount, frequency, next/anchor date, category, in/out); edit name/date too; merchantKey via `RecurringDetector.merchantKey`; refuse duplicate key (no silent REPLACE); "Ignored" section with Restore; unit tests.
- [x] 2b. Screens: adopt shared components/tokens on Dashboard, Transactions, Analytics, Comparison, Budget, CashFlow; remove duplicated composables.
- [x] 2c. Sheets + Goals: remove hard-coded colours/fontSize/shapes in Add/Edit/CategoryPicker; one button style; fix doubled "+"; Goals archived view + unarchive; Review/More aligned; no silent "Chase"/"HSBC" default account.
- [x] 2d. Settings + data: Settings restyled; manage accounts (add/rename/delete); edit categories (rename, emoji, colour, group, unhide); export to file via system Save dialog (JSON incl. budgets, goals, recurring, accounts, categories, rules) + import JSON; unit tests for export/import round trip.
- [x] 3. Reviewer pass; owners fix findings.
- [x] 4. Python mirror tests, bump to v2.2.0, commit, push.

## Testing
- No Android SDK in cloud sessions: Kotlin compile + unit tests verified by CI, which only runs on PRs/pushes to `main` → needs a PR to get a build.
- `python -m pytest -q test_spend_parser.py test_bank_deduplication.py test_categorization_engine.py` must stay green.
- New Kotlin unit tests for recurring manual add and export/import.

## Risks
- Compose compile errors can't be caught locally; the reviewer checks imports/signatures by hand.
- Account rename/delete must respect bank-name normalisation/consolidation (`normalizeBankName`, `test_bank_deduplication.py`).
- Import must validate input and never wipe data without confirmation.
