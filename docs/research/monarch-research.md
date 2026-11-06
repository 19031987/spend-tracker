# Monarch Money: feature research for SpendTracker

Researched 2026-11-06 from public sources only. No binaries, private APIs, brand assets or exact copy were used.

## Method and confidence

- In this session, direct page fetching was blocked: `help.monarch.com`, `www.monarch.com` and `monarchmoney.com` did not resolve because they are outside the network allowlist. Everything here comes from web-search result summaries of Monarch's help center, "What's New" changelog, App Store listing and third-party reviews, plus general product knowledge of Monarch's UI.
- Confidence tags:
  - **[doc]** means the claim came from a search summary of an official Monarch help or changelog page.
  - **[3p]** means a review, Reddit post or third-party tool.
  - **[obs]** means general product knowledge that wasn't re-verified this session. Check these against the app before relying on precise details.
- Monarch moved its domain from monarchmoney.com to monarch.com; help pages now live on help.monarch.com. To verify pixel-level details, allow those hosts and re-fetch the cited URLs.

---

## 1. Mobile navigation and Dashboard

**What the user sees**
- Bottom navigation has **5 tabs by default on iPhone**. Everything else sits in a "More"/sidebar menu. [doc] https://www.monarchmoney.com/ipad-support-budget-enhancements-and-more
- Users can **edit the navigation**: Settings → Display & Navigation → Edit Navigation. They choose which items go in the bottom bar and the sidebar, and in what order. [doc] (Oct 2024 post, same URL)
- The usual default set is Dashboard, Accounts, Transactions, Cash Flow/Reports, Budget/Plan. Recurring, Goals, Investments, Reports and Settings are in the overflow. A Feb 2022 update put "Plan" back into the main 5. [doc] https://www.monarchmoney.com/new-mobile-navigation, [obs] for the exact default set.
- The Dashboard is a vertical stack of cards. Each card has a title row with a chevron or "View all" that deep-links to the full screen. Available cards include: Getting-started guide, Budget, Net worth, Spending (this month vs last month), Recent transactions, Recurring/upcoming bills, Investments, Goals, Credit score, Transactions to review, Weekly recap and AI Insights (sparkle icon). [doc] https://help.monarch.com/hc/en-us/articles/360058127551-Customize-Your-Dashboard, App Store https://apps.apple.com/app/id1459319842, [3p] Motley Fool / CFO Club reviews.
- The **Spending card** shows a cumulative line chart of this month's spending, day by day, over a faint line for last month, with "$X spent this month" as the headline. [obs] This is a strong pattern: at any point in the month you can see whether you're ahead of or behind last month.

**Behaviour**
- The mobile dashboard has a **"Customize" button at the bottom**. It opens a list where cards can be toggled on or off and reordered by drag handle. Web and mobile layouts are independent and don't sync. [doc] Customize Your Dashboard article.
- Home-screen widgets exist on iOS and Android, showing budget and spending snapshots. [doc] https://help.monarch.com/hc/en-us/articles/8611706513684-Using-Mobile-Widgets

**Mobile UX**: Pull to refresh. The greeting header shows the date. Cards are tappable as a whole, and a sparkle icon marks AI insights.

**Verdict: FEASIBLE.** An ordered list of card IDs plus a visible flag is easy to keep in DataStore or Room. Every card except net worth and investments can be built from local data. A Glance home-screen widget ("spent this month / left to spend") is also feasible.

## 2. Transactions

**What the user sees** [obs + doc]
- A list **grouped by date headers** ("Today", "Yesterday", "Mon, Nov 3"). Each row shows:
  - left: merchant logo or a circular initial
  - centre: cleaned merchant name, with the category (emoji + name) as a secondary line
  - right: amount, with income in **green and a +**, expenses in neutral/default text
  - small badges for pending, needs-review (dot), split, note, attachment
- Top bar has a search field and filter icon. The FAB/"+" action is now separate from "Edit Multiple". [doc] https://www.monarch.com/blog/quicker-and-easier-transaction-review-and-more (2025: "Add New" and "Edit Multiple" separated)
- **Transaction detail** is a full-screen sheet with a large amount, merchant (editable), date, account, then rows for category, tags, notes, attachments, "Split", "Hide from reports/budget" and "Needs review". Quick-action buttons at the top let you **hide** or **mark reviewed**. [doc] transaction-review post https://monarchmoney.com/whats-new/transaction-review

**Behaviour**
- **Search** covers merchant/description and amount. **Filters** cover:
  - categories / category groups, accounts, merchants, tags
  - date range, amount range, type (income/expense/transfer)
  - has attachments / has splits / has notes
  - include or exclude hidden transactions
  - needs review / reviewed

  [doc] help "Tips & Tricks" https://help.monarch.com/hc/en-us/articles/28953573066260-Tips-Tricks-for-Using-Monarch
- **Bulk edit** (mobile since Aug 2021): multi-select, then set category, merchant, date, tags, notes, hide or review status. Bulk edit can also *clear* tags and notes. [doc] https://www.monarchmoney.com/new-mobile-navigation and the changelog
- **Splits**: divide one transaction into N lines, each with its own category, amount, note and tags. Splits can be by **fixed amount or percentage**, and rules can split automatically ("Smart split"). [doc] https://www.monarchmoney.com/review-on-the-go-and-smart-split
- **Tags**: free-form coloured labels; a transaction can have several. They're used in filters and reports (e.g. "Vacation 2025", "Reimbursable"). [doc]
- **Notes and attachments**: free text plus photos/PDFs of receipts. [doc]
- **Review status**: transactions can be "Needs review" or "Reviewed". Rules can set review status, e.g. flag anything over $X for review. Transactions can also be assigned to a household member for review. [doc] https://www.monarchmoney.com/assign-transactions-to-a-household-member-for-review. A swipe-based **rapid review** flow on mobile shows one card per transaction with category chips and approve/next. [doc] "review on the go"
- **Hide**: hidden transactions are excluded from budgets, cash flow and reports, but stay visible with a filter. [doc]
- **Merchant cleanup**: raw bank descriptors ("SQ *BLUE BOTTLE 1234 SF") are shown as clean merchant names with logos. Users can rename a merchant, and the rename applies to all of that merchant's transactions. A Merchants settings page lists merchants with counts. [obs/doc via rules]

**Verdict: FEASIBLE** for date-grouped list, search, filters, bulk edit, splits, tags, notes, review status, hide, merchant rename and alias. **Attachments: FEASIBLE**, stored locally under app-private storage. **Logos: PARTIAL.** Use emoji or initial avatars, or a user-picked icon, rather than a logo service. Raw notification text is the perfect input for merchant alias cleanup: map the raw sender/descriptor to a display name.

## 3. Categories and rules

**Structure** [doc] https://help.monarch.com/hc/en-us/articles/360048883771-Creating-Custom-Categories-and-Groups, https://help.monarch.com/hc/en-us/articles/360048883851
- There are three top-level **types**: Income, Expenses, Transfers. Groups belong to a type, and categories belong to a group. Each category has an emoji icon and a name.
- A group is configured as **budget by category or by group**. Group-level budgeting arrived later. [doc] https://www.monarchmoney.com/group-level-budgeting
- Default income categories: Paychecks, Interest, Business Income, Other Income. Transfers: Credit Card Payment, Transfer, plus Buy/Sell for investments. Default expense groups include Auto & Transport, Bills & Utilities, Food & Dining, Shopping, Travel, Gifts & Donations, Health, Children, Education, Housing and others.
- **Transfers don't count** toward spending, income or budgets. Card payments are transfers, because the purchases were already counted. [doc]
- A user report says a category can't be moved between types. [3p]

**Rules** [doc] https://help.monarch.com/hc/en-us/articles/360048393372
- **Conditions**:
  - merchant name (exact or contains)
  - original statement text
  - amount (equals / greater than / less than / between)
  - expense or income direction
  - category
  - account
  - all conditions are ANDed; OR groups aren't supported [3p]
- **Actions**:
  - rename merchant
  - set category
  - add tags
  - hide transaction
  - set review status (needs review / reviewed)
  - link to goal
  - split (by amount or %)
- **Creation**: rules live in Settings → Rules, and are ordered so the first match wins for conflicting actions. When you edit a transaction, a prompt offers to "create a rule" for similar transactions, applying to future ones and optionally to existing ones. Users complain this auto-prompt creates duplicate rules. [3p]
- Rules run on new transactions at sync time, and can be applied retroactively.

**Verdict: FEASIBLE.** SpendTracker already has merchant rules. The gaps are the **type** field on groups (Income / Expense / Transfer, with transfers excluded from totals) and richer rules: amount and account conditions; tag, hide, review and rename actions; priority order; "apply to existing". The "create rule from this edit?" snackbar after a re-categorisation adds a lot of value for little work.

## 4. Budgets

**Two modes** [doc] https://help.monarch.com/hc/en-us/articles/360048883631-Creating-Your-Budget-in-Monarch, https://www.monarch.com/flex-vs-category-budgeting-how-to-choose-whats-right-for-you
- **Category budgeting**: a planned amount for every category (or group).
- **Flex budgeting** (the default): every expense category is assigned to one of three **buckets**:
  - **Fixed**: predictable, steady, hard to cut (rent, insurance, phone).
  - **Non-monthly**: irregular or annual (car registration, holidays, annual subscriptions). It's budgeted as a monthly set-aside with **rollover always on**, so the balance builds up and is drawn down when the bill arrives.
  - **Flexible**: everything discretionary. Only **one number** is tracked for the whole bucket, the "Flex number".
- **Flex number = expected income − fixed − non-monthly − goal contributions.** [doc] https://www.monarch.com/flex-budgeting-simplify-your-spending-with-just-one-number. The UI shows "Unallocated flexible budget" (renamed from "Available"). [3p]

**What the user sees** [obs + doc]
- A **month switcher** at the top: "‹ November 2026 ›" with arrows.
- A summary header: Income budgeted vs earned, Expenses budgeted vs spent, and **"Left to budget"** (income budget − expense budget). In flex mode the headline is the flex number with a large progress bar: "$X left of $Y flexible".
- Below the header, sections per bucket or group (Income, Fixed, Flexible, Non-monthly). Each row shows emoji, category name, a thin **progress bar** (Actual / Budget) and the **remaining** amount on the right:
  - green or neutral while under budget
  - amber/yellow when close to the limit
  - red with a negative number when over
- Web shows Budget / Actual / Remaining columns. Mobile collapses this to a bar plus "remaining", and tapping a row opens category detail: monthly bar history, budget edit, rollover toggle and transactions.
- A **monthly progress view** compares this month against the prior month or average. [doc] https://www.monarch.com/blog/monthly-progress-view

**Rollover** [doc] https://help.monarch.com/hc/en-us/articles/4411119762196-Rollover-Budgets
- Per category, toggle "rollover". **Remaining = previous rollover + this month's budget − actual spending.** Under-spend carries forward as a positive amount and overspend as a negative one.
- **Starting balance** and start month are configurable, so a non-monthly fund can start pre-filled. Rollover can be reset at year end.
- Flex-bucket rollover is a single bucket-level setting.

**Other**
- "Move money" between budget lines covers re-allocation. [3p]
- Budgets auto-fill suggestions from historical averages. [obs]

**Verdict: FEASIBLE.** All of this works on categorised local transactions. Recommend:
- category and group budgets
- a "bucket" attribute (Fixed / Flexible / Non-monthly)
- a hero card showing "Flexible: $X left" with a pace marker: today's position in the month, as a tick on the bar, against % spent
- per-category rollover with a starting balance

SpendTracker's existing monthly carry-over balance fits naturally as the income/flex rollover.

## 5. Cash Flow

[doc] https://help.monarch.com/hc/en-us/articles/20504904768020-Cash-Flow, https://help.monarch.com/hc/en-us/articles/21846787088916-Using-Reports, https://www.monarch.com/blog/visualize-your-cash-flow-like-never-before

**What the user sees**
- A time-range selector: month, quarter, year, or custom. Choosing the month, quarter or year granularity changes the bar buckets.
- A **bar chart of income (green, above) vs expenses (red/orange, below)** per period, with a net-savings line or dots. [obs]
- A **summary card**: Total income, Total expenses, **Net cash flow (savings)**, **Savings rate**.
- Breakdown lists for **Income** and **Expenses**, by category / group / merchant. Rows show emoji, name, amount and % of total, with a horizontal bar.
- Cash Flow now also appears as a tab in Reports.

**Behaviour**
- **Savings rate = (income − expenses) / income.** Transfers are excluded, so investment contributions moved by transfer don't count as savings, which users complain about. [3p] Hidden transactions are excluded.
- **Sankey diagram**: income sources flow into a central node, then into expense groups/categories plus a "Savings" node. Modes are by Category, Group, or Category & Group. The **Sankey is web only.** [doc]

**Verdict: FEASIBLE.** SpendTracker has income/spend per month already. Add savings and savings rate, bar charts by month for income vs expense, and breakdown by group, category and merchant. A Sankey on a phone is a nice-to-have, PARTIAL: Monarch itself keeps it on web. A simplified vertical "income → groups → savings" flow or a stacked bar is more mobile-friendly.

## 6. Recurring

[doc] https://help.monarch.com/hc/en-us/articles/4890751141908, https://www.monarchmoney.com/track-recurring-bills-and-subscriptions, https://monarchmoney.com/features/recurring

**What the user sees**
- A **Monthly** view with a **list/calendar toggle** and month arrows.
  - **List** is split into "**Upcoming**" and "**Complete**" sections. Each row shows merchant, logo, due date, expected amount, frequency, account and category.
  - **Calendar** is a month grid with merchant avatars on due days. Colour status:
    - **green with check** = paid as expected
    - **yellow** = paid but a different amount
    - **blue** = upcoming
    - past-due unpaid items are hidden from the calendar
- An **All recurring** view lists every recurring merchant regardless of month, sectioned **Active / Canceled**. It can be filtered and grouped by frequency, category, account or amount.
- Summary numbers: total expected this month, paid so far, remaining due. [obs]
- A dashboard card shows upcoming bills. [obs]

**Behaviour**
- **Detection** runs on each sync, scanning for merchants with a regular cadence and similar amounts.
  - Frequencies are weekly, every 2 weeks, twice a month, monthly, every 2 months, quarterly, every 6 months and yearly. [obs]
  - The expected amount is the last or typical amount, and amounts can vary (marked "~").
  - New candidates go into a **Recurring Review** flow where you approve or dismiss each one. They aren't considered recurring until approved.
  - Transfers and card payments are excluded from auto-detection. Users can add recurring items manually, or mark a merchant "not recurring" from the calendar or list.
  - Some false positives are reported (Uber, DoorDash, Venmo). [3p]
- **Notifications**: push or email **3 days before** an expected charge. Configurable in Settings. [doc/3p]
- "Bill Sync" pulls statement balances and due dates from credit cards and loans. [doc] https://monarchmoney.com/introducing-bill-sync. This one needs aggregation.

**Verdict: FEASIBLE**, and probably the single highest-value addition. Detect cadence from local transaction history:
- group by normalised merchant
- require 3 or more occurrences with interval variance under a threshold and amounts within ±X%
- suggest candidates in a review sheet

Show a list/calendar with paid / different amount / upcoming status, matching incoming notification transactions to the expected item. Schedule a local reminder via WorkManager N days before. Bill Sync is NOT FEASIBLE.

## 7. Goals

[doc] https://help.monarch.com/hc/en-us/articles/15000751305108-Using-Goals, https://help.monarch.com/hc/en-us/articles/44373182867476-Using-Save-Up-Goals, https://help.monarch.com/hc/en-us/articles/47822256713236-Growth-Rates-in-Save-Up-Goals

**What the user sees**
- A goals list of cards. Each card has an image or emoji, name, a **progress bar or ring**, "$saved of $target", target date and a status chip: **On track / Ahead / At risk (behind)**.
- The goal detail page has:
  - a chart of the balance over time
  - a **dotted projection line** to the target date
  - spending from the goal as a separate (black) line
  - a monthly contribution history

**Behaviour**
- **Save-up goals** need three inputs for status: **target amount, target date, planned monthly contribution**. The recommended monthly amount is (target − current) / months remaining. Optional growth rate is compounded monthly as (1+r)^(1/12)−1.
- Contributions can be edited for this month only or for all future months. Goals link to accounts or transactions, and a rule can link transactions to a goal. Goal contributions are subtracted in the flex-number formula.
- "Goals 3.0" (2025/26) separated save-up goals from debt-payoff goals. [3p]

**Verdict: PARTIAL to FEASIBLE.** Without account balances, track goals as an **envelope ledger**: manual "add contribution" entries, transactions tagged or ruled to a goal, or a monthly auto-allocation from the carry-over surplus. Status math and projection are pure computation, so they're feasible.

## 8. Reports

[doc] https://help.monarch.com/hc/en-us/articles/21846787088916-Using-Reports, https://www.monarch.com/blog/saved-reports-and-more
- Three tabs: **Cash Flow, Spending, Income**.
- Chart types:
  - Cash Flow: **Sankey** (web only) and **Breakdown** (ranked horizontal bars)
  - Spending: **Pie/donut, Breakdown, Trend bars** (stacked over time), **Treemap**
  - Income: same as Spending minus Sankey
- Group by **category, category group, or merchant**.
- Filters: date range (presets plus custom), accounts, categories, merchants, tags, and include or exclude hidden transactions. A 2026 update reportedly reduced the exclude-category filters. [3p]
- Tapping a slice or bar **drills down** to the filtered transaction list.
- **Saved reports** bookmark the chart type, grouping, filters and time frame.

**Verdict: FEASIBLE.** SpendTracker's Insights and Compare tabs already cover part of this. Adding donut / trend-bars / treemap with grouping by group, category, merchant or tag, drill-down to transactions and **saved report presets** is all local.

## 9. Accounts and Net worth

[doc] https://Monarchmoney.com/features/net-worth, https://help.monarch.com/hc/en-us/articles/14882425704212-Import-Account-Balance-History-Manually
- **Accounts screen**:
  - a **net-worth chart** at the top (area/line, ranges 1M/3M/6M/YTD/1Y/All, scrub to see the daily change)
  - an **Assets vs Liabilities** summary
  - accounts **grouped by type**: Cash, Credit cards, Investments, Real estate, Vehicles, Loans, Other
  - each group has a subtotal and % change; each account row shows institution logo, name, last-synced time and balance, with a sparkline
- Daily snapshot of balances. Manual accounts are supported, with CSV import of balance history (date, balance), past only.
- **NOT FEASIBLE** as Monarch does it: there are no live balances. **PARTIAL alternative**: let the user enter an opening balance per account, derive a **running balance** from local transactions (balance = opening + Σ inflows − Σ outflows), and let them "reconcile" by entering the true balance occasionally, which records an adjustment. Some bank SMS/notifications include "Avl bal" or "available balance". **Parsing the balance from notifications** gives real balance snapshots and makes a per-account balance-history chart and a simple net-worth line PARTIALLY feasible.

## 10. Other notable features

- **Weekly Recap** (AI): total spending and main drivers, cash-flow trend, subscription and recurring changes, week-over-week net-worth change. Shown on the dashboard plus a weekly email. [doc] https://help.monarch.com/hc/en-us/articles/16116906962452-About-Monarch-s-AI-Features. **FEASIBLE** as a templated (non-AI) recap: a Monday notification plus a recap card that compares the week with the 4-week average, lists the top 3 categories that drove the change, new recurring charges detected, and budget status.
- **AI Insights** (sparkle chips on Dashboard and Accounts) and an **AI Assistant** chat. Users can opt out in Settings → Preferences. Rule-based insights are FEASIBLE; the assistant is NOT relevant for a local app.
- **Notifications**: budget nearing or exceeded, large transaction, upcoming recurring (3 days), new transactions to review, weekly recap. [3p] All FEASIBLE locally.
- **Household and collaboration** (assign review to a partner, shared login): NOT relevant for local-only.
- **Investments / credit score / Bill Sync**: NOT FEASIBLE.
- **Monthly review / "month in review"** recap screens: a year-in-review story format. [obs] FEASIBLE.
- **Plan / forecast** (projected income and expenses for future months, built from budgets): FEASIBLE later.

---

## Visual language

These notes describe the spirit of the design; don't copy it.
- **Calm, airy, card-based.** White or near-white background in light mode, deep navy/charcoal in dark mode. Content sits in rounded cards (~12–16dp radius) with very soft or no shadows and 1dp hairline dividers inside.
- **One warm brand accent** (orange) used sparingly for primary actions and the selected tab. Data colours are separate from the brand.
- **Semantic money colour**: income and positive numbers in **green with a "+"**; expenses in **default text colour**, not red, so the list doesn't feel alarming. Red or orange is reserved for **over budget / negative remaining**, and yellow/amber for "close" or "different amount".
- **Typography**: a geometric sans. Card headers are small, medium-weight and muted. The **hero number** on each card is large and bold (e.g. "$2,431 spent"). Secondary lines are smaller and grey. Amounts use tabular figures and right alignment.
- **Density**: medium. Transaction rows are about 56–64dp with avatar, two text lines and an amount. Budget rows have a thin 4–6dp rounded progress bar under the name.
- **Charts**: minimal axes, light gridlines, no chart junk. Rounded bar tops; smooth lines with a light gradient fill (net worth, spending); a dashed or faint line for comparison periods and projections. Scrubbable tooltips. Category colours follow each category's own colour; donut charts have a thin ring with the total in the centre.
- **Emoji category icons**: SpendTracker already does this, and it's a good match.
- **Mobile patterns**:
  - 5-tab bottom nav, customisable
  - full-screen detail pages pushed onto the stack
  - bottom sheets for pickers (category, date range, filters)
  - month switcher with chevrons at the top of Budget, Cash Flow and Recurring
  - segmented controls for list/calendar and chart type
  - swipe card stack for review
  - "Customize" at the end of the dashboard

---

## Prioritised recommendations for SpendTracker

Ordered by value ÷ effort, given local, notification-derived data.

1. **Recurring bills and subscriptions (detection + review + list/calendar)**. Group transactions by normalised merchant, detect a stable cadence (weekly / monthly / yearly …) and similar amounts from three or more occurrences, and offer candidates in an approve/dismiss review sheet. Show "Upcoming / Paid" lists and a month calendar coloured paid, different amount or upcoming, and auto-match incoming notification transactions. Fire a local reminder N days before each expected charge.
2. **Category budgets with "left to spend" and pace**. Per-category (or per-group) monthly budget with a thin progress bar, a remaining amount that turns amber near the limit and red when over, and a month switcher. Add a hero card, "₹X left to spend this month", with a today-marker on the bar so users can see whether they're ahead of pace. Prefill suggested budgets from the 3-month average.
3. **Needs-review inbox and swipe review flow**. Every auto-captured (notification/SMS) transaction starts as "needs review" unless a rule marked it reviewed. Show a dashboard badge and a card-stack flow (merchant, amount, suggested category chips, confirm or next) so users can clean a week of captures in seconds. This fits notification parsing, where errors are common, particularly well.
4. **"Create a rule from this edit?" and richer rules**. When a user changes a category or merchant name, show a snackbar that offers a rule, applied to future and optionally past matches. Extend rules with amount and account conditions, actions for rename, tag, hide and mark reviewed, plus priority ordering.
5. **Cash-flow view with savings rate**. Monthly bar chart of income vs expenses with net savings, and a summary of income, expenses, saved and savings rate ((income − expense) / income). Add breakdown lists by group, category or merchant that drill into transactions. Add a Transfer type for groups so transfers between own accounts are excluded from both sides.
6. **Customisable dashboard cards**. Home becomes an ordered stack of cards: left-to-spend, spending-vs-last-month cumulative line, upcoming bills, to review, top categories, recent transactions, goals. Add a "Customize" sheet at the bottom to reorder and toggle them.
7. **Weekly recap (templated)**. A Monday notification and a recap card: spent this week vs the 4-week average, the top 3 categories driving the difference, new or changed recurring charges, and budget status. It's cheap to compute and builds the habit of opening the app.
8. **Tags, notes, splits and hide-from-reports**. Multi-tags (e.g. "Trip-Goa", "Reimbursable") usable in filters and reports. Split one transaction into category lines by amount or %, and attach a note or receipt photo. Hidden transactions stay in the list (behind a filter) but are excluded from all totals.
9. **Powerful search, filters and bulk edit**. Search merchant, notes and amount, and add a filter sheet: date range, accounts, categories, tags, amount range, type, reviewed, hidden, has note or attachment. Long-press enters multi-select for bulk category, tag, hide or review changes.
10. **Flex budgeting buckets plus rollover**. Assign categories to Fixed / Flexible / Non-monthly. Show a single "Flexible: X left" number (income − fixed − non-monthly set-asides), and enable per-category rollover with a starting balance for non-monthly sinking funds. Build this on top of #2, and reuse the existing carry-over logic.
11. **Savings goals as local envelopes**. Each goal has a target, a date and a planned monthly contribution. Progress comes from manual contributions or transactions linked by rule or tag, with an On track / Behind status and a dashed projection line. Contributions reduce the flexible "left to spend".
12. **Account balances from notifications (running balance + reconcile)**. Parse "available balance" from bank notifications when present; otherwise compute opening balance + transactions and let users reconcile occasionally. This enables a per-account balance chart and a simple net-worth line without aggregation.

Feasibility recap:
- **Not feasible without aggregation**: investments, credit score, Bill Sync statement data, automatic institution logos and live balances.
- **Not relevant to a local app**: household or partner review and the AI chat assistant.

## Sources (from search results)
- Budgets: https://help.monarch.com/hc/en-us/articles/360048883631-Creating-Your-Budget-in-Monarch · https://help.monarch.com/hc/en-us/articles/4411119762196-Rollover-Budgets · https://www.monarch.com/flex-budgeting-simplify-your-spending-with-just-one-number · https://www.monarch.com/flex-vs-category-budgeting-how-to-choose-whats-right-for-you · https://www.monarchmoney.com/group-level-budgeting · https://www.monarchmoney.com/rollovers-and-referral-rewards · https://www.monarch.com/blog/monthly-progress-view
- Dashboard and nav: https://help.monarch.com/hc/en-us/articles/360058127551-Customize-Your-Dashboard · https://help.monarch.com/hc/en-us/articles/8611706513684-Using-Mobile-Widgets · https://www.monarchmoney.com/new-mobile-navigation · https://www.monarchmoney.com/ipad-support-budget-enhancements-and-more · https://apps.apple.com/app/id1459319842
- Transactions and rules: https://help.monarch.com/hc/en-us/articles/360048393372 · https://monarchmoney.com/whats-new/transaction-review · https://www.monarchmoney.com/review-on-the-go-and-smart-split · https://www.monarch.com/blog/quicker-and-easier-transaction-review-and-more · https://help.monarch.com/hc/en-us/articles/28953573066260-Tips-Tricks-for-Using-Monarch
- Categories: https://help.monarch.com/hc/en-us/articles/360048883771-Creating-Custom-Categories-and-Groups · https://help.monarch.com/hc/en-us/articles/360048883851
- Cash flow and reports: https://help.monarch.com/hc/en-us/articles/20504904768020-Cash-Flow · https://help.monarch.com/hc/en-us/articles/21846787088916-Using-Reports · https://www.monarch.com/blog/saved-reports-and-more · https://www.monarch.com/blog/visualize-your-cash-flow-like-never-before
- Recurring: https://help.monarch.com/hc/en-us/articles/4890751141908 · https://www.monarchmoney.com/track-recurring-bills-and-subscriptions · https://monarchmoney.com/introducing-bill-sync · https://robberger.com/best-bill-reminder-apps/
- Goals: https://help.monarch.com/hc/en-us/articles/15000751305108-Using-Goals · https://help.monarch.com/hc/en-us/articles/44373182867476-Using-Save-Up-Goals · https://help.monarch.com/hc/en-us/articles/47822256713236-Growth-Rates-in-Save-Up-Goals
- Net worth: https://Monarchmoney.com/features/net-worth · https://help.monarch.com/hc/en-us/articles/14882425704212-Import-Account-Balance-History-Manually
- AI and recap: https://help.monarch.com/hc/en-us/articles/16116906962452-About-Monarch-s-AI-Features
- Reviews: https://www.fool.com/money/personal-finance/monarch-money-review · https://thecfoclub.com/tools/monarch-money-review/ · https://experian.com/blogs/ask-experian/monarch-money-review/
