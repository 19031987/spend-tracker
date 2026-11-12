# UI components and tokens

Use these instead of private copies or ad-hoc styling. Rules apply literally.

## Never

- `RoundedCornerShape(n.dp)` for cards/sheets/buttons: use `AppCard` / theme shapes (`MaterialTheme.shapes.small|medium|large`).
- `fontSize = …sp` or `fontWeight = FontWeight.Bold` on text: pick a `MaterialTheme.typography` style (weights are set in `theme/Type.kt`). Money hero figures may use `headline*`/`display*`.
- `Color(0x…)` in screens/sheets: use `MaterialTheme.colorScheme.*` or `SpendTrackerTheme.finance.*` (both have dark values).
- `shape =` / `fontWeight =` on buttons: use the button components below.
- A literal "+" in a label next to an Add icon ("+ Add goal").

## Tokens (`ui/theme/Dimens.kt`)

| Token | Value | Use |
|---|---|---|
| `Spacing.xs/sm/md/lg/xl/xxl` | 4/8/12/16/24/32 dp | gaps and padding |
| `screenContentPadding()` | 16 sides+top, 96 bottom | `contentPadding` of every screen LazyColumn |
| `ScreenPadding.listItemSpacing` | 12 dp | `Arrangement.spacedBy(...)` between screen items |
| `ScreenPadding.cardPadding` | 16 dp | inside cards (AppCard default) |
| `ScreenPadding.sheetHorizontalPadding` / `sheetBottomPadding` / `sheetItemSpacing` | 20 / 24 / 16 dp | sheets (SheetContainer applies them) |
| `ScreenPadding.buttonHeight` | 48 dp | buttons (components apply it) |

```kotlin
LazyColumn(
    contentPadding = screenContentPadding(),
    verticalArrangement = Arrangement.spacedBy(ScreenPadding.listItemSpacing)
) { … }
```

## Colours

- Material roles: `MaterialTheme.colorScheme.primary/onSurface/onSurfaceVariant/error/errorContainer/...`
- Money semantics: `SpendTrackerTheme.finance` (`import com.spendtracker.app.ui.theme.SpendTrackerTheme`)
  - `income` / `expense`: amount text for money in / out (replaces `0xFF059669` / `0xFFDC2626`)
  - `surplusContainer` + `onSurplusContainer`: green hint boxes (replaces `0xFFECFDF5`, `0xFF065F46`, `0xFF047857`; use `outlineVariant` or no border instead of `0xFFA7F3D0`)
  - `deficitContainer` + `onDeficitContainer`: red hint boxes
  - `warning`, `warningContainer` + `onWarningContainer`: amber cautions (replaces `0xFFFEF3C7`, `0xFFB45309`, border `0xFFF59E0B` → `warning`)
- Hint boxes: `Surface(shape = MaterialTheme.shapes.small, color = finance.warningContainer, contentColor = finance.onWarningContainer)`.

## Components (`com.spendtracker.app.ui.components`)

| Need | Use |
|---|---|
| Any card on a screen | `AppCard(modifier, onClick = null, containerColor = surfaceContainerLow, contentColor = auto, contentPadding = 16.dp, verticalSpacing = 0.dp) { … }` |
| Section title above cards | `SectionHeader(title, subtitle = null, small = false, actionLabel = null, actionIcon = null, actionIconLeading = false, onAction = null)` |
| Group label inside a section ("Ignored", "Essentials") | `SectionHeader(title, small = true)` |
| "See all" link | `SectionHeader("Budgets", actionLabel = "See all", actionIcon = Icons.Default.ChevronRight, onAction = …)` |
| Nothing to show | `EmptyState(body, title = null, icon = null, emoji = null, actionLabel = null, actionIcon = Icons.Filled.Add, onAction = null)` |
| KPI / metric tile | `StatTile(label, value, supportingText = null, valueColor = onSurface, containerColor = surfaceContainerLow, compact = false, centered = false, reserveSupportingLine = false)` |
| Status label | `StatusChip(text, tone = StatusTone.Positive/Negative/Warning/Info/Neutral)` |
| Sheet body | `SheetContainer(scrollable = true) { … }` |
| Sheet title | `SheetHeader(title, subtitle = null, onClose = null)` |
| Sheet / form buttons | `SheetActions(confirmLabel, onConfirm, onDismiss, dismissLabel = "Cancel", confirmEnabled = true, destructive = false)` |
| Main action | `PrimaryButton(text, onClick, enabled, leadingIcon, fullWidth)` |
| Cancel / alternative | `SecondaryButton(...)` same params |
| Medium emphasis | `TonalButton(...)` same params |
| Confirm a delete | `DestructiveButton(...)` same params (errorContainer) |
| Low-emphasis delete link | `DestructiveTextButton(text, onClick, enabled, leadingIcon)` |

`MetricCard` is legacy (defaults now match AppCard); prefer `AppCard`/`StatTile`. `CategoryBadge`, `StatusPill`, `TrendBadge`, `ComparisonBadge`, `BaselineBanner`, `TransactionRow`, `AccountSelector` are unchanged in API.

### Mapping old private composables

- `EmptyState` / `EmptyGoals` / `EmptyCard` / ReviewScreen + CashFlow empty → `EmptyState(...)` (EmptyCard(text) → `EmptyState(body = text)`)
- `SectionHeader` / `SectionHeaderRow` / `SectionLabel` / `GroupHeader` → `SectionHeader(...)` (`SectionLabel` → `small = true`). Inside a LazyColumn with `screenContentPadding()` don't add extra horizontal padding.
- `DashboardCard(title, onAction) { … }` → `AppCard { SectionHeader(title, actionLabel = "See all", actionIcon = Icons.Default.ChevronRight, onAction = onAction); content() }`
- `StatTile` (CashFlow, Goals) → `StatTile(...)` (CashFlow: `compact = true, reserveSupportingLine = true`); `KpiPill` (Analytics) → `StatTile(compact = true, centered = true, containerColor = MaterialTheme.colorScheme.surface)`
- Private status pills (Recurring) → `StatusChip` (PAID Positive, OVERDUE Negative, PAID_DIFFERENT_AMOUNT Warning, UPCOMING Info)

## Conventions

**Screens.** LazyColumn with `screenContentPadding()` + `spacedBy(ScreenPadding.listItemSpacing)`; every block is an `AppCard`; sections start with `SectionHeader`.

**Add-new.** On a screen: `ExtendedFloatingActionButton(onClick, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Add goal") })`, or, when the screen has no FAB, a `SectionHeader` action (`actionLabel = "Add", actionIcon = Icons.Filled.Add, actionIconLeading = true`). In a sheet or empty state: `PrimaryButton("Add payment", onClick, leadingIcon = Icons.Filled.Add)`. Label is "Add …"/"New …", never with a "+".

**Sheets.**
```kotlin
ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
    SheetContainer {
        SheetHeader("Edit payment")
        OutlinedTextField(…, modifier = Modifier.fillMaxWidth())
        SheetActions(confirmLabel = "Save", onConfirm = { … }, onDismiss = onDismiss, confirmEnabled = valid)
        // optional, edit sheets only:
        DestructiveTextButton("Delete payment", onClick = { confirmDelete = true }, leadingIcon = Icons.Outlined.Delete,
            modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}
```
Use `SheetContainer(scrollable = false)` if the sheet contains a LazyColumn.

**Save / cancel / delete.** Confirm label is "Save" for edits, "Add" (or "Add …") for creation. Dismiss is always on the left. A delete confirmation uses `SheetActions(confirmLabel = "Delete", destructive = true, …)` or an `AlertDialog` with `DestructiveButton` as confirmButton and `TextButton("Cancel")` as dismissButton. Swipe/row-level deletes use an `IconButton` with `Icons.Outlined.Delete` tinted `colorScheme.error`.
