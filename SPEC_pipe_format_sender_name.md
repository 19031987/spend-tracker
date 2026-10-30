# Spec: Capture sender name from `amount | sender name | Bank` notifications

Status: ready for implementation (by another model). Project: `spend-tracker` (Android, Kotlin).

## 1. Goal
Notifications may arrive as `amount | sender name | Bank`, for example:

| Title | Text |
|---|---|
| (any / bank name) | `£50.00 \| John Smith \| HSBC` |
| `£120.00 \| ACME LTD \| Monzo` | (empty) |
| `Chase` | `£23.00 \| Sarah Jones \| Chase` |

The app must read all three fields: **amount**, **sender name** (stored as `merchant`) and **bank** (stored as `source` and used for account lookup).

## 2. Current behaviour (evidence)
Files:
- [SpendParser.kt](app/src/main/java/com/spendtracker/app/parser/SpendParser.kt)
- [SpendNotificationListenerService.kt](app/src/main/java/com/spendtracker/app/service/SpendNotificationListenerService.kt)

- `parse()` joins `title + " " + text`, finds the first currency amount, then calls `extractMerchant`.
- The sender is only extracted through the phrases `X sent you`, `X paid you`, `from X` (IN) and `at/to X`, `paid X`, and `X £amount` (OUT).
- The `NAME` and `TERM` regexes treat `|` as a terminator, but nothing parses `|` as a field separator.
- Result for `£50.00 | John Smith | HSBC`:
  - There is no direction verb, so `type` falls to the final `else -> TYPE_OUT`. **An incoming payment is recorded as spend.**
  - No merchant pattern matches, so `merchantIsFallback = true` and the row shows "Unknown Vendor".
  - The bank is detected correctly through `BANK_CONTENT_WORDS` only for the known banks.
- `TransactionEntity.merchant` and `.source` already exist. **No DB schema change is needed.**

## 3. Requirements

### R1. Pipe-format parser (`SpendParser.kt`)
Add a pipe-segment step in `parse()`, run **after** the trust and IGNORE/PROMO checks and **before** the generic merchant extraction.

1. Split `content` on `|` (also accept `¦`). Trim each segment. Drop empty segments.
2. The format matches when there are **≥ 3 segments**, **and**:
   - segment 1 contains a currency amount, found with the existing `findFirstMoney`. The currency marker stays mandatory, so the existing `MONEY_PREFIX`/`MONEY_SUFFIX` rules apply, and
   - segment 2 passes `isUsableName`.
3. Fields:
   - `amount` and `currency` come from segment 1.
   - `merchant` comes from segment 2, passed through `cleanMerchantName(seg2, source)`.
   - `source` is the bank from segment 3. Resolve it by `detectSource("", seg3)` first. If that returns the generic `"Card Payment"`, use the raw segment 3 and let the service's `normalizeBankName` handle it, so unknown banks still work.
4. Direction (`type`):
   - An explicit `+` or `-` sign on the amount wins, as it does today.
   - Otherwise, if STRONG_IN or STRONG_OUT matches anywhere in the content, use it.
   - Otherwise **default to `TYPE_IN`**. The sender-name format describes money received, so the old `else -> TYPE_OUT` fallback must not apply to this format.
   - See open question Q1.
5. If the title and text are separate fields, build the pipe string from whichever field contains the `|`. If the amount is in the title and the rest is in the text (`£50 | …` / `Name | Bank`), joining with `" | "` also has to work. Add a test for this.
6. Lines that do not match the format fall through to the existing logic unchanged. There must be no regression for the current tests.
7. `merchantIsFallback = false` when a sender is extracted.
8. Categorisation: call `categorize("$merchant $content", type)`. IN rules already map to "Transfers In".

### R2. Fix interaction with existing filters
- The `IGNORE` regex contains words such as `win`, `save`, `code` and `deals?`. A sender called "Winston" is protected by the `\b` anchors. Check that a name like `Win Tan`, `Reminder Ltd` or `Offer Systems` is not wrongly rejected. Run IGNORE on segments 1 and 3 plus any free text, **not** on the sender-name segment, when the pipe format matches.
- `isUsableName` rejects names containing `ACTION_WORDS` (`card`, `payment`, `account`, and so on). Keep that behaviour. When the sender is rejected, fall back to the existing flow.

### R3. Persistence and dedup (DB checked, a defect was found)
Two stores are written for every notification, and both are affected.

**Verified facts**
- Room: `transactions.merchant` and `.source` are nullable text and already exist. No migration is needed. `merchant = parsed.merchant` and `source = sourceAccount.name` are written in `SpendNotificationListenerService`.
- Legacy SQLite (`SpendDatabase`): `merchant TEXT NOT NULL`, `source TEXT NOT NULL`, `raw_text TEXT`. The full pipe string is saved in `raw_text`. No change needed.
- Both stores deduplicate with `findDuplicate`. The match is: same amount, same bank, and a timestamp within 60 s.
  - Room: `TransactionDao.findDuplicate` (`LIMIT 1`).
  - Legacy: `SpendDatabase.findDuplicate` and `insertExpense`.
- **Defect:** the sender name is **not** part of the match. Two different senders paying the same amount to the same bank within 60 s (for example, two people each sending £10) are treated as one payment. The second is dropped or merged, because enrichment only fills a merchant that is blank or generic. Money is silently lost. Before this feature the merchant was often blank, so this was rare. It becomes likely once sender names are captured.
- The legacy `cleanupAndRepairData` step 1 already includes `merchant` in its GROUP BY, so it is safe.

**Required changes**
1. Dedup rule: a candidate is a duplicate only if **both** merchants are blank or generic (`Card Payment`, `Payment`, equal to the source), **or** the normalised merchants (lower-case, trimmed) are equal. If both have a real, different sender, it is a **new transaction**.
2. Room: `findDuplicate` returns only one row, so a differing first row could hide a matching second. Either add a `findDuplicates(...)` DAO query that returns a list without `LIMIT 1` and filter in Kotlin, or add a `:merchant` parameter to the SQL. Apply the rule in the service's duplicate-check blocks, both the normal path and the internal-transfer path.
3. Legacy: apply the same rule in `SpendDatabase.findDuplicate`/`insertExpense`.
4. Enrichment still works for SMS plus app notification of the same payment. One side has the sender and the other has none, so the rule treats them as the same payment.
5. For new Room rows with a sender, keep `note = "Captured from <bank> alert"`.
6. Add tests:
   - same amount, same bank, same sender within 60 s: one row;
   - same amount, same bank, different senders within 60 s: two rows;
   - one row with a sender and one without: one row, enriched with the sender.
### R4. UI (`SpendTrackerScreen.kt`, `TransactionRowItem`, around line 819)
- For `INCOME` rows with a merchant, show the sender as the row title. This already happens through `tx.merchant`, and the bank badge already shows `tx.source`. **No UI change is required** unless the owner wants a "From " prefix (see Q2).
- Optional (Q2): prefix the title with `From ` for INCOME and `To ` for EXPENSE when the merchant is a person-like name.

### R5. Simulator and docs
- Add the pipe examples to the parser tests and the simulator ([generate_simulator.py](generate_simulator.py), `simulator/index.html`) if it has a sample list.
- Add one line to `README.md` under "Accurate Vendor & Merchant Capture".

## 4. Test cases
Add to [test_spend_parser.py](test_spend_parser.py). It mirrors the Kotlin parser, so keep both in sync. Also add a Kotlin unit test under `app/src/test/java/com/spendtracker/app/parser/`.

| # | Package | Title | Text | Expected |
|---|---|---|---|---|
| 1 | `com.hsbc.hsbcukmobilebanking` | `HSBC` | `£50.00 \| John Smith \| HSBC` | IN, 50.00, `John Smith`, source `HSBC` |
| 2 | `com.monzo.android` | `£120.00 \| Sarah Jones \| Monzo` | `` | IN, 120.00, `Sarah Jones`, `Monzo` |
| 3 | `com.chase.sig.android` | `Chase` | `£1,250.50 \| TechCorp Payroll \| Chase` | IN, 1250.50, `Techcorp Payroll` |
| 4 | `com.google.android.apps.messaging` | `HSBC` | `£23.00 \| Dave Brown \| HSBC` | SMS trust needs a verb. **Decide per Q3.** Expected: accepted. |
| 5 | HSBC | | `-£8.99 \| Netflix \| HSBC` | OUT (explicit sign) |
| 6 | HSBC | | `£50.00 \| John Smith \| Some Credit Union` | IN, source from `normalizeBankName("Some Credit Union")` |
| 7 | HSBC | | `£50.00 \| Win Tan \| HSBC` | IN, merchant `Win Tan` (IGNORE must not reject the name) |
| 8 | WhatsApp (`com.whatsapp`) | | `£50.00 \| John \| HSBC` | `null` (untrusted package) |
| 9 | HSBC | | `£50.00 \| Your card ending 1234 \| HSBC` | falls through to the old logic. No crash. |
| 10 | HSBC | | `Balance £1,650.99 \| John \| HSBC` | Balance scrubbing still happens. Segment 1 has no amount, so fall through. |
| 11 | HSBC | | `You spent £8.99 at Costa` | unchanged from today (regression) |

Run `python test_spend_parser.py` and the Gradle unit tests with `./gradlew testDebugUnitTest`.

## 5. Out of scope
- No DB migration or new column.
- No change to the trust and allow-list logic.
- No change to the internal-transfer detection in the service.

## 6. Decisions (confirmed by owner)
- Q1: the format is always money received. No sign and no direction word means IN.
- Q2: SMS in this format skips the `FINANCIAL_TX_VERB` requirement. Trust is still required: the package must be the SMS app, and the format must match all of R1.
- Q3: the DB path was checked. See R3.
- Q4 (still open, low risk): a real notification sample would confirm whether the pipe string is in the title or the text. The parser handles both, so implementation is not blocked.
