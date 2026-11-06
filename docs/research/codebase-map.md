# SpendTracker Android: Architecture Map

Snapshot of `/home/user/spend-tracker` at commit `f6ecb60` ("Redesign app with a Material 3 theme and navigation (v2.0.0)").
All paths below are relative to `app/src/main/java/com/spendtracker/app/` unless they start with `app/` or `.github/`.
Line numbers are 1-based and refer to the file as it stands at that commit.

Use this document instead of reading the code. Nothing in it has been compiled locally (there is no Android SDK here); it is based on reading the source.

---

## 0. Build facts and CI

| Item | Value | Where |
|---|---|---|
| AGP / Kotlin / KSP | 8.3.0 / 1.9.22 / 1.9.22-1.0.17 | `build.gradle.kts:2-4` |
| Compose compiler ext | 1.5.8 | `app/build.gradle.kts:26` |
| compileSdk / targetSdk / minSdk | 36 / 36 / 26 (`android.suppressUnsupportedCompileSdk=36` in `gradle.properties`) | `app/build.gradle.kts:9-14` |
| versionCode / versionName | 27 / "2.0.0" | `app/build.gradle.kts:15-16` |
| JVM target | 17 | `app/build.gradle.kts:51-57` |
| Compose BOM | 2024.02.00 (compose ui/foundation 1.6.1, material3 1.2.0) | `app/build.gradle.kts:67` |
| material-icons-extended | yes (from BOM) | `app/build.gradle.kts:75` |
| activity-compose | 1.8.2 | `:76` |
| lifecycle-viewmodel-compose / lifecycle-runtime-compose | 2.7.0 | `:77-78` |
| Room (runtime, ktx, compiler via `ksp`) | 2.6.1 | `:81-84` |
| appcompat 1.6.1, core-ktx 1.12.0, material (MDC) 1.11.0, biometric 1.1.0 | | `:61-64` |
| Test deps | **only** `junit:junit:4.13.2` (no Robolectric, no mockk/mockito, no kotlinx-coroutines-test, no Room testing, no Turbine) | `:86` |
| Minify | off for release (`isMinifyEnabled = false`) | `:40` |

**Libraries NOT present** (adding any of them means editing `app/build.gradle.kts`): navigation-compose, DataStore, WorkManager, Hilt/Koin, kotlinx-serialization, Paging, Room testing, coroutines-test, Robolectric, any charting library, `androidx.compose.material3:material3-adaptive`.

CI: `.github/workflows/build-apk.yml`. JDK 17. Step 1 runs `./gradlew :app:testReleaseUnitTest` (line 39). Step 2 runs `:app:assembleRelease :app:bundleRelease` (line 51). On push to `main` it publishes a GitHub release with the hard-coded tag `v2.0.0` (lines 75-78). That tag is a hotspot if anyone bumps the version. CI is the **only** compiler. A compile error anywhere fails both steps. A Room schema mismatch does **not** fail CI: it crashes at runtime on upgrade (see section 8).

Manifest (`app/src/main/AndroidManifest.xml`): permission `USE_BIOMETRIC` only (no `POST_NOTIFICATIONS`, no `SCHEDULE_EXACT_ALARM`, no `RECEIVE_BOOT_COMPLETED`). There is one activity, `.MainActivity` (launcher). There is one service, `.service.SpendNotificationListenerService` (`BIND_NOTIFICATION_LISTENER_SERVICE`). There is **no custom `Application` class**. Window theme: `Theme.SpendTracker` → parent `Theme.Material3.DayNight.NoActionBar` (`res/values/themes.xml`).

---

## 1. Data layer

### 1.1 Databases: two coexist

| | `AppDatabase` (Room) | `SpendDatabase` (legacy) |
|---|---|---|
| File | `data/AppDatabase.kt` | `data/SpendDatabase.kt` |
| DB file name | `spend_tracker_room.db` (`AppDatabase.kt:580`) | `spend_tracker.db` (`SpendDatabase.kt:42`) |
| Kind | `RoomDatabase` | raw `SQLiteOpenHelper`, `DATABASE_VERSION = 3` (`:43`) |
| Amounts | `Long` signed minor units | `REAL` positive major units, direction in `type` 'IN'/'OUT' |
| Who writes | UI (via `SpendRepository`), notification service, onOpen repair | notification service only (`SpendNotificationListenerService.kt:68-69`), plus its own `onOpen` repair (`SpendDatabase.kt:95-98`) and the Room repair routine (`AppDatabase.kt:290-339`) |
| Who reads | the entire UI | nobody in the UI. Only Room's repair routine reads it (`cleanupAndRepairTransactions`, step 2) |

**Rule for new features:** read and write Room only. `SpendDatabase` is a legacy write-through log. Do not build on it. The Room `onOpen` callback reads `spend_tracker.db` and **mutates** it (`AppDatabase.kt:291-336`).

Legacy table `transactions` (SpendDatabase): `id INTEGER PK AUTOINCREMENT, type TEXT NOT NULL DEFAULT 'OUT', amount REAL NOT NULL, currency TEXT NOT NULL DEFAULT '£', merchant TEXT NOT NULL, category TEXT NOT NULL, source TEXT NOT NULL, timestamp INTEGER NOT NULL, raw_text TEXT`. Indices: `idx_transactions_timestamp`, `idx_transactions_type`, `idx_transactions_source` (`SpendDatabase.kt:57-75`). Legacy data classes: `TransactionRecord` (`:13-24`), `CategorySpend` (`:26-31`), `MerchantSpend` (`:33-37`). The legacy query helpers (`getTotalMoneyOutMonth` etc., `:467-621`) are unused by the UI.

### 1.2 Room entities

All Kotlin `Long` → `INTEGER NOT NULL`, `Long?` → `INTEGER`, `String` → `TEXT NOT NULL`, `String?` → `TEXT`, `Boolean` → `INTEGER NOT NULL` (0/1), `Int` → `INTEGER NOT NULL`. `TransactionType` → `TEXT` via `Converters` (`TransactionEntity.kt:42-50`; unknown strings decode to `EXPENSE`). There are **no foreign keys** anywhere.

#### `TransactionEntity` (table `transactions`): `data/TransactionEntity.kt:55-76`
Indices: `Index("accountId")`, `Index("timestamp")`, `Index("pairedTransactionId")` (non-unique) → Room names them `index_transactions_accountId`, `index_transactions_timestamp`, `index_transactions_pairedTransactionId`.

| Field | Kotlin type | Default (Kotlin) | SQL default (`@ColumnInfo`) | Notes |
|---|---|---|---|---|
| `id` | `Long` | `0` | n/a | `@PrimaryKey(autoGenerate = true)` |
| `accountId` | `Long` | (required) | none | → `accounts.id` (no FK) |
| `amount` | `Long` | (required) | none | **signed minor units**: EXPENSE < 0, INCOME > 0, TRANSFER source −/dest + (KDoc line 52-54) |
| `category` | `String?` | `null` | none | key into `categories.key` (built-in enum name like `"GROCERIES"` or custom `"c_<epochMillis>"`). The onOpen repair rewrites null to `OTHER_EXPENSE`/`OTHER_INCOME` |
| `note` | `String?` | `null` | none | Notification captures write `"Captured from <Bank> alert: <raw text>"` |
| `timestamp` | `Long` | (required) | none | epoch **millis** (UTC instant). Period math converts with `ZoneId.systemDefault()` |
| `type` | `TransactionType` | `EXPENSE` | `'EXPENSE'` | stored as TEXT name |
| `destinationAccountId` | `Long?` | `null` | none | transfers only (feature removed) |
| `pairedTransactionId` | `Long?` | `null` | none | transfers only |
| `excludeFromSpending` | `Boolean` | `false` | `0` | the "Nullify from spend" switch |
| `merchant` | `String?` | `null` | none | null means unknown vendor. Generic labels are scrubbed to null |
| `source` | `String?` | `null` | none | normalized bank name, duplicates the account name |

#### `TransactionType` / `TransactionCategory`: `data/TransactionEntity.kt:9-40`
- `enum class TransactionType { EXPENSE, INCOME, TRANSFER }` (line 9). **TRANSFER rows are deleted on every DB open** (see 1.5), so in practice only EXPENSE/INCOME exist.
- `enum class TransactionCategory(val label: String, val type: TransactionType)` (lines 11-40): GROCERIES, UTILITIES, RENT, TRANSPORT, DINING, SHOPPING, HEALTH, ENTERTAINMENT, SUBSCRIPTIONS, TRAVEL, EDUCATION, OTHER_EXPENSE("Other") (EXPENSE); SALARY, FREELANCE, INVESTMENT, GIFT, OTHER_INCOME("Other income") (INCOME); INTERNAL_TRANSFER (TRANSFER). Companion: `fun forType(type: TransactionType): List<TransactionCategory>`, `fun fromName(name: String?): TransactionCategory` (falls back to `OTHER_EXPENSE`). This is a legacy enum. The DB catalog (`categories` table) is the source of truth for names, emoji and colors.

#### `AccountEntity` (table `accounts`): `data/AccountEntity.kt:61-68`
`indices = [Index(value = ["name"], unique = true)]`.
- `id: Long = 0` (`@PrimaryKey(autoGenerate = true)`)
- `name: String` with `@ColumnInfo(collate = ColumnInfo.NOCASE)`
- Account names always go through `normalizeBankName(raw: String?, fallback: String = "Others"): String` (`AccountEntity.kt:15-59`). Aliases collapse: Premier/Advance → "HSBC"; "checking"/"current account"/"savings" → "Chase"; messaging/SMS/generic → "Others". Unknown names come back Title Case.
- There is no balance, type or institution column. "Balance" in the UI is computed (carried-over income minus spend).

#### `CategoryGroupEntity` (table `category_groups`): `data/CatalogEntities.kt:14-21`
`id: Long = 0` (autoGenerate PK), `name: String`, `emoji: String`, `sortOrder: Int`, `isBuiltIn: Boolean`. No indices.

#### `CategoryEntity` (table `categories`): `data/CatalogEntities.kt:24-34`
`indices = [Index(value = ["groupId"])]`.
`key: String` (`@PrimaryKey`, **not** autoGenerate; the SQL column is `` `key` `` and must be backtick-quoted in raw SQL), `groupId: Long`, `name: String`, `emoji: String`, `colorHex: String` (e.g. `"#D1FAE5"`), `type: TransactionType` (this is how a category's "type" is represented: EXPENSE/INCOME/TRANSFER, TEXT via converter), `isBuiltIn: Boolean`, `isHidden: Boolean`.
Custom keys are `"c_" + System.currentTimeMillis()` (`SpendRepository.kt:315`).

#### `MerchantRuleEntity` (table `merchant_rules`): `data/CatalogEntities.kt:36-43` (entity annotation at 36, class at 37)
`id: Long = 0` (autoGenerate PK), `matchType: String` (must be a `domain.MatchType` name: `"CONTAINS" | "STARTS_WITH" | "EQUALS"`), `pattern: String`, `categoryKey: String`, `createdAt: Long` (epoch ms).
**Bug to know about:** `TransactionsViewModel` stores `"EXACT"` (`TransactionsViewModel.kt:56, 85`). That is not a valid `MatchType`. Every consumer does `runCatching { MatchType.valueOf(...) }`, so these rules are **silently ignored** (`AddTransactionViewModel.kt:28-36`, `SpendNotificationListenerService.kt:96-105`).

Non-entity row class: `data class HistoryRow(val merchant: String, val category: String, val uses: Int)` (`CatalogEntities.kt:45`).

#### Seed data: `object CatalogSeed` (`CatalogEntities.kt:147-209`)
- `const val OTHER_GROUP_ID = 6L`
- Groups (ids fixed): 1 "Food & Dining", 2 "Living", 3 "Transport", 4 "Lifestyle", 5 "Income", 6 "Other" (all built-in).
- Categories: GROCERIES(1), DINING(1), RENT(2), UTILITIES(2), HEALTH(2), TRANSPORT(3), TRAVEL(3), SHOPPING(4), ENTERTAINMENT(4), SUBSCRIPTIONS(4), EDUCATION(4), OTHER_EXPENSE(6). Income in group 5: SALARY, FREELANCE, INVESTMENT, GIFT, OTHER_INCOME. INTERNAL_TRANSFER (TRANSFER, group 6; hidden on every open).
- `fun seed(db: SupportSQLiteDatabase)` inserts groups and categories with raw SQL (`:190-208`).

### 1.3 DAOs (all `abstract class`, mixing abstract Room methods with `open` Kotlin helpers)

#### `TransactionDao`: `data/TransactionDao.kt:86-331`
Supporting types in the same file: `data class BucketTotal(val bucket: String, val total: Long)` (:9), `data class CategoryTotal(val category: String, val total: Long)` (:10), `data class TransferIds(val sourceId: Long, val destinationId: Long)` (:11).
`private const val SPEND_WHERE` (:13-14) = `type = 'EXPENSE' AND excludeFromSpending = 0 AND (category IS NULL OR category != 'INTERNAL_TRANSFER') AND timestamp >= :start AND timestamp < :end`. **This is the canonical definition of "spending".** Reuse it for budgets and cash flow.

Top-level pure helpers (JVM-testable, already tested in `SpendParserTest`):
- `fun isUnknownOrGenericMerchant(m: String?, sourceName: String? = null): Boolean` (:16)
- `fun isPremierOrHsbcBank(bank: String?): Boolean` (:37)
- `fun isSameOrEquivalentBank(bankA: String?, bankB: String?): Boolean` (:42)
- `fun resolvePreferredBankSource(existingSource: String?, newSource: String?): String` (:49)
- `fun isMerchantDuplicate(existingMerchant: String?, newMerchant: String?, sourceName: String? = null): Boolean` (:56)
- `fun isTransactionDuplicate(existingAmountMinor: Long, existingMerchant: String?, existingSource: String?, existingTimestamp: Long, newAmountMinor: Long, newMerchant: String?, newSource: String?, newTimestamp: Long, windowMillis: Long = 60_000L): Boolean` (:69)

DAO methods:
| Line | Signature | Kind |
|---|---|---|
| 89-90 | `@Insert abstract suspend fun insert(transaction: TransactionEntity): Long` | suspend |
| 92-93 | `@Update abstract suspend fun update(transaction: TransactionEntity)` | suspend |
| 95-96 | `@Query("SELECT * FROM transactions WHERE id = :id") abstract suspend fun getById(id: Long): TransactionEntity?` | suspend |
| 102-125 | `abstract suspend fun findDuplicate(absAmountMinor: Long, sourceName: String, accountId: Long, timestamp: Long, windowMillis: Long = 60_000L): TransactionEntity?` | suspend |
| 130-152 | `abstract suspend fun findDuplicates(...same params...): List<TransactionEntity>` | suspend |
| 157-172 | `suspend fun findMatchingDuplicate(absAmountMinor: Long, sourceName: String, accountId: Long, timestamp: Long, merchant: String?, windowMillis: Long = 60_000L): TransactionEntity?` (final, non-open helper) | suspend |
| 174-175 | `abstract suspend fun setPairedId(id: Long, pairedId: Long)` | suspend |
| 177-179 | `abstract suspend fun deleteWithPair(id: Long): Int` (`DELETE ... WHERE id = :id OR pairedTransactionId = :id`) | suspend |
| 185-231 | `@Transaction open suspend fun insertTransferPair(sourceAccountId: Long, destinationAccountId: Long, amountMinor: Long, timestamp: Long, note: String?, sourceName: String? = null, destinationName: String? = null): TransferIds` | suspend |
| 233-294 | `@Transaction open suspend fun updateTransferPair(sourceTxId: Long, sourceAccountId: Long, destinationAccountId: Long, amountMinor: Long, note: String?, sourceName: String?, destinationName: String?)` | suspend |
| 296-297 | `@Query("SELECT * FROM transactions ORDER BY timestamp DESC") abstract fun observeAll(): Flow<List<TransactionEntity>>` | **Flow** |
| 299-300 | `abstract fun observeTotalSpend(start: Long, end: Long): Flow<Long>` (`COALESCE(-SUM(amount),0)` over SPEND_WHERE → positive) | Flow |
| 302-307 | `abstract fun observeDailyTotals(start: Long, end: Long): Flow<List<BucketTotal>>` (bucket `'%Y-%m-%d'`, localtime) | Flow |
| 309-314 | `abstract fun observeMonthlyTotals(start: Long, end: Long): Flow<List<BucketTotal>>` (bucket `'%Y-%m'`, localtime) | Flow |
| 316-321 | `abstract fun observeCategoryTotals(start: Long, end: Long): Flow<List<CategoryTotal>>` (null category → `'OTHER_EXPENSE'`, positive totals, `ORDER BY total DESC`) | Flow |
| 323-324 | `abstract suspend fun getAllTransactions(): List<TransactionEntity>` | suspend |
| 326-327 | `abstract suspend fun deleteAll(): Int` | suspend |
| 329-330 | `@Insert abstract suspend fun insertAll(transactions: List<TransactionEntity>)` | suspend |

Nothing exists for search, LIKE, paging, income totals, per-account totals or per-merchant totals. You must add these.

#### `AccountDao`: `data/AccountEntity.kt:70-192`
| Line | Signature | Kind |
|---|---|---|
| 72-73 | `abstract fun observeAllRaw(): Flow<List<AccountEntity>>` | Flow |
| 75-80 | `open fun observeAll(): Flow<List<AccountEntity>>` (normalized, distinct by lowercase name, sorted) | Flow |
| 82-88 | `abstract suspend fun findByNameExact(name: String): AccountEntity?` | suspend |
| 90-98 | `open suspend fun findByName(name: String): AccountEntity?` | suspend |
| 100-101 | `abstract suspend fun getAllRaw(): List<AccountEntity>` | suspend |
| 103-108 | `open suspend fun getAll(): List<AccountEntity>` | suspend |
| 110-111 | `@Insert(onConflict = IGNORE) abstract suspend fun insertRaw(account: AccountEntity): Long` | suspend |
| 113-128 | `open suspend fun getOrCreate(name: String): AccountEntity` | suspend |
| 130-141 | `open suspend fun insert(account: AccountEntity): Long` | suspend |
| 143-144 | `abstract suspend fun deleteById(id: Long): Int` | suspend |
| 146-147 | `@Update abstract suspend fun update(account: AccountEntity)` | suspend |
| 149-156 | `remapTransactionAccountId(duplicateId: Long, canonicalId: Long): Int`, `remapTransactionDestinationAccountId(...)`, `updateTransactionSource(canonicalId: Long, canonicalName: String): Int` | suspend |
| 158-189 | `@Transaction open suspend fun consolidateDuplicates(): Int` | suspend |
| 191 | `open suspend fun deleteDuplicates(): Int = consolidateDuplicates()` | suspend |

#### `CatalogDao`: `data/CatalogEntities.kt:47-145`
| Line | Signature | Kind |
|---|---|---|
| 50-51 | `abstract fun observeGroups(): Flow<List<CategoryGroupEntity>>` (`ORDER BY sortOrder, id`) | Flow |
| 53-54 | `abstract fun observeCategories(): Flow<List<CategoryEntity>>` (`ORDER BY name COLLATE NOCASE`) | Flow |
| 56-57 | `abstract fun observeRules(): Flow<List<MerchantRuleEntity>>` (`createdAt DESC, id DESC`) | Flow |
| 59-66 | `getCategories(): List<CategoryEntity>`, `getGroups(): List<CategoryGroupEntity>`, `getRules(): List<MerchantRuleEntity>` | suspend |
| 68-73 | `abstract fun observeHistory(): Flow<List<HistoryRow>>` (merchant/category use counts, excludes TRANSFER) | Flow |
| 75-82 | `insertGroup(group): Long`, `insertGroups(groups)`, `insertCategory(category)`, `insertCategories(categories)`, `insertRule(rule): Long`, `updateGroup`, `updateCategory`, `updateRule` (all `@Insert`/`@Update`, default ABORT) | suspend |
| 84-85 | `maxGroupOrder(): Int` | suspend |
| 87-103 | `deleteCategory(key: String)`, `deleteGroup(id: Long)`, `deleteRule(id: Long)`, `deleteRulesForCategory(key: String)`, `uncategorizeTransactions(key: String)` (sets `transactions.category = NULL`), `moveCategories(fromGroup: Long, toGroup: Long)` | suspend |
| 105-107 | `clearCategories()`, `clearGroups()`, `clearRules()` | suspend |
| 109-118 | `@Transaction open suspend fun insertCategoryWithRule(newGroup: CategoryGroupEntity?, category: CategoryEntity, rule: MerchantRuleEntity?)` | suspend |
| 120-129 | `@Transaction open suspend fun removeCategory(key: String, hide: Boolean, category: CategoryEntity)` | suspend |
| 131-135 | `@Transaction open suspend fun removeGroup(id: Long, fallbackGroupId: Long)` | suspend |
| 137-144 | `@Transaction open suspend fun resetCatalog()` | suspend |

### 1.4 `AppDatabase`: `data/AppDatabase.kt:556-599`
```kotlin
@Database(
    entities = [AccountEntity::class, TransactionEntity::class, CategoryGroupEntity::class,
                CategoryEntity::class, MerchantRuleEntity::class],
    version = 7,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun accountDao(): AccountDao
    abstract fun catalogDao(): CatalogDao
    companion object { fun get(context: Context): AppDatabase  // @Volatile singleton, synchronized }
}
```
- **Current version: 7. The next one is 8.**
- `exportSchema = false`. There are no schema JSON files, so you cannot copy Room's generated `CREATE TABLE` SQL. You have to write it by hand to match exactly.
- **No `fallbackToDestructiveMigration`**. A missing migration crashes at runtime.
- Builder (`:577-595`): `.addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)` (line 582), then `.addCallback(object : Callback() { onCreate; onOpen })`.
- `onCreate` (:584-587): `INSERT OR IGNORE INTO accounts(name) VALUES ('Chase'), ('HSBC'), ('Credit Card')`, then `CatalogSeed.seed(db)`.
- `onOpen` (:589-593): runs on **every** open, before any query: `cleanupAndConsolidateAccounts(db)` then `cleanupAndRepairTransactions(db, context.applicationContext)`.

Migrations: each is a top-level `val MIGRATION_X_Y = object : Migration(X, Y) { override fun migrate(db: SupportSQLiteDatabase) { db.execSQL(...) } }` in `AppDatabase.kt`.
| Object | Lines | What it does |
|---|---|---|
| `MIGRATION_1_2` | 11-26 | `ALTER TABLE transactions ADD COLUMN type TEXT NOT NULL DEFAULT 'EXPENSE'`, `destinationAccountId INTEGER`, `pairedTransactionId INTEGER`, `excludeFromSpending INTEGER NOT NULL DEFAULT 0`; `UPDATE transactions SET amount = -ABS(amount)`; creates the 3 transaction indices with `CREATE INDEX IF NOT EXISTS index_transactions_<col>` |
| `MIGRATION_2_3` | 28-70 | `ADD COLUMN merchant TEXT`; `CREATE TABLE IF NOT EXISTS category_groups(...)`, `categories(...)` (+ `index_categories_groupId`), `merchant_rules(...)`; `CatalogSeed.seed(db)`. **Use this as the template for hand-written CREATE TABLE SQL** (`id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL`, `TEXT NOT NULL`, etc.) |
| `MIGRATION_3_4` | 72-90 | `ADD COLUMN source TEXT`; ensures 'Chase' and 'HSBC' accounts exist; `INSERT OR IGNORE` the INTERNAL_TRANSFER category |
| `MIGRATION_4_5` | 538-542 | `cleanupAndConsolidateAccounts(db)` |
| `MIGRATION_5_6` | 544-548 | `cleanupAndConsolidateAccounts(db)` |
| `MIGRATION_6_7` | 550-554 | `cleanupAndConsolidateAccounts(db)` |

### 1.5 The onOpen repair routines (high risk for new features)

`fun cleanupAndConsolidateAccounts(db: SupportSQLiteDatabase)` (`AppDatabase.kt:92-247`). In one transaction it:
- merges duplicate accounts by normalized name and remaps `accountId`/`destinationAccountId`;
- re-links orphan transactions;
- re-creates the default accounts Chase, HSBC and Credit Card if they are missing (deleted accounts come back);
- forces Premier/HSBC rows onto the HSBC account;
- rewrites every `transactions.source` to the account name;
- **deletes accounts named messages/savings/messaging/others/bank alert/message** and moves their transactions to Chase;
- drops and re-creates `index_accounts_name` as `ON accounts(name COLLATE NOCASE)`.

`fun cleanupAndRepairTransactions(db: SupportSQLiteDatabase, context: Context)` (`AppDatabase.kt:249-536`), **on every open**:
1. Re-parses notes that contain `|` with `SpendParser` and rewrites `merchant`, `type` and `amount` sign.
2. Opens the legacy `spend_tracker.db` (if present), re-parses pipe alerts, updates the legacy rows, and fuzzy-updates Room rows within 60 s and ±1 minor unit.
3. Nulls generic merchant labels. Flips rows to INCOME when `note`/`category` contains "money in".
4. **`DELETE FROM transactions WHERE type = 'TRANSFER' OR category = 'INTERNAL_TRANSFER'`** (:361-366). It also sets `isHidden = 1` on TRANSFER categories. It rewrites null/legacy categories to `OTHER_INCOME`/`OTHER_EXPENSE`.
5. **Deduplicates the whole table**: rows with the same |amount| and an equivalent bank are merged, with a time window of 600 s (10 min) when both merchants are known, 300 s when Premier is involved, else 180 s. The later row is **deleted**. The kept row is updated with `UPDATE transactions SET accountId, source, merchant, category, type, amount, note WHERE id = ?` (:504-507). Columns not in that list are kept on the kept row. On the deleted row they are lost.

Consequences for new features:
- Two genuine identical purchases (for example two £3.20 coffees at the same merchant within 10 min) are merged on the next app open. Recurring bills or generated transactions that land with the same amount, bank and merchant within the window are merged too.
- Transfers cannot be modelled with `TransactionType.TRANSFER` or category `INTERNAL_TRANSFER`, because they are deleted on open. Use `excludeFromSpending = true` instead.
- A "reviewed", "tags" or "notes" value on a row that dedup deletes is lost. Store such data on the transaction row and accept this, or merge it in the dedup step (that means editing `AppDatabase.kt`).
- A Room write inside onOpen runs before the first DAO call returns. Keep any new onOpen work cheap.

### 1.6 Money and time conventions
- Room: `amount: Long` is **signed minor units** (pence/cents). EXPENSE is negative, INCOME positive. `TransactionItem.amountMinor` is **always the absolute value** (`SpendRepository.kt:91`). Sign is derived from `type`.
- `NewTransaction.Entry.amountMinor` is positive. The repository applies the sign (`SpendRepository.kt:157`).
- Spend aggregations in SQL return **positive** totals (`-SUM(amount)`).
- `ComparisonViewModel` converts to `Double` major units (`/ 100.0`). Analytics stays in `Long` minor units.
- Parser side: `ParsedExpense.amount: Double` major units, positive. The service converts with `(amount * 100.0).roundToLong().coerceAtLeast(1L)` (`SpendNotificationListenerService.kt:92`).
- Timestamps: `Long` epoch millis. SQL buckets use `strftime(..., timestamp / 1000, 'unixepoch', 'localtime')`. Kotlin windows use `LocalDate.atStartOfDay(zone)` with `ZoneId.systemDefault()` (or `clock.zone` in the repository). There is no stored time zone.
- Currency: there is **one implicit currency**. `CurrencyUtils.formatMinor` uses `NumberFormat.getCurrencyInstance(Locale.getDefault())`. The user preference `currencySymbol` is **not** used by any formatter (see 4.3). Add/Edit sheets hard-code `"£"` as the amount prefix (`AddTransactionSheet.kt:310`).

---

## 2. Repository: `data/SpendRepository.kt`

```kotlin
class SpendRepository(
    private val transactionDao: TransactionDao,
    private val accountDao: AccountDao,
    private val catalogDao: CatalogDao,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val firstDayOfWeek: DayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek,
    private val locale: Locale = Locale.getDefault()
)
```
(lines 28-35). It is constructed once in `AppContainer` (`AppContainer.kt:17`). The service does not use it.

Public API (exact):
| Line | Signature | Notes |
|---|---|---|
| 37-41 | `fun observeAccounts(): Flow<List<AccountEntity>>` | normalized, distinct, sorted |
| 42 | `fun observeGroups(): Flow<List<CategoryGroupEntity>>` | |
| 43 | `fun observeCategories(): Flow<List<CategoryEntity>>` | includes hidden ones; callers filter `isHidden` |
| 44 | `fun observeRules(): Flow<List<MerchantRuleEntity>>` | |
| 45-47 | `fun observeHistory(): Flow<List<HistoryEntry>>` | |
| 49 | `fun observeTransactions(): Flow<List<TransactionEntity>>` | raw entities, newest first. Used by Analytics/Comparison VMs |
| 51-140 | `fun observeTransactionItems(): Flow<List<com.spendtracker.app.domain.TransactionItem>>` | combine(tx, accountsRaw, categories) → map → **UI-level dedupe** (see below) |
| 143-223 | `suspend fun add(transaction: NewTransaction)` | throws `IllegalArgumentException` when the amount is ≤ 0. Runs **dedup on insert**: a manual entry with the same amount and bank within 60 s and a matching merchant **updates** the existing row instead of inserting |
| 225-274 | `suspend fun updateTransaction(id: Long, type: TransactionType, accountId: Long, destinationAccountId: Long?, amountMinor: Long, categoryKey: String?, merchant: String?, note: String?, excludeFromSpending: Boolean)` | `categoryKey ?: "OTHER_EXPENSE"`. Deletes the paired leg if any. Timestamp cannot be edited |
| 276-278 | `suspend fun delete(transactionId: Long)` | `deleteWithPair` |
| 280-289 | `suspend fun addRule(matchType: String, pattern: String, categoryKey: String): Long` | `createdAt = System.currentTimeMillis()` |
| 291-293 | `suspend fun deleteRule(id: Long)` | |
| 295-337 | `suspend fun createCategoryWithRule(name: String, groupId: Long?, newGroupName: String?, emoji: String, colorHex: String, type: TransactionType, alwaysMatchMerchant: String?)` | key `"c_"+millis`. Rule is `CONTAINS` |
| 339-344 | `suspend fun deleteCategory(key: String, hide: Boolean = true)` | built-ins are hidden. Custom ones are deleted, their rules removed and transactions uncategorized |
| 346-429 | `suspend fun resetDemoData()` | resets the catalog, **deletes all transactions**, inserts 7 demo rows |
| 431-461 | `suspend fun exportJson(): String` | |
| 463-488 | `suspend fun exportCsv(): String` | |
| 498-531 | `fun observeAnalytics(period: Period): Flow<AnalyticsSnapshot>` | a `flow {}` that computes the windows at collection time, then `emitAll(combine(...))` |
| 533-535 | `private fun bucketFlow(period, range)` | YEAR → monthly, else daily |
| 537-564 | `internal fun toShares(rows: List<CategoryTotal>, total: Long, categories: List<CategoryEntity> = emptyList()): List<CategoryShare>` | `internal`, so it is testable from `app/src/test` in the same module |

Mapping entity → `TransactionItem` (`:65-106`):
- `amountMinor = abs(tx.amount)`
- `accountId`/`accountName`: canonical account (Premier → HSBC), falling back to the normalized `source`
- `categoryKey = cat?.key ?: fallbackCatKey` (`OTHER_INCOME` for INCOME, else `OTHER_EXPENSE`). `categoryName`/`categoryEmoji`/`categoryColorHex` come from the `categories` row with fallbacks ("Other"/"Other income", "🏷️"/"💰", "#E2E8F0")
- `merchant` = null when generic (`isUnknownOrGenericMerchant`)
- `source = acc?.name ?: normSource`
- `excludeFromSpending = tx.excludeFromSpending || isTransfer`

**UI-level dedupe** (`:108-139`): HSBC items with the same amount within 180 s and a duplicate merchant are collapsed into one item (category enriched). The list a user sees can therefore have **fewer rows than the table**. Search, review counts and budget totals computed from `TransactionItem` can differ slightly from SQL aggregates.

The `combine` operator takes at most 5 typed flows. The repository nests combines (`:505-529`) to get past that. Follow the same pattern.

---

## 3. Domain (`domain/`)

All of `domain/` is **pure Kotlin + java.time** (no Android imports). It is JVM unit-testable. It imports `data.BucketTotal`, `data.TransactionCategory` and `data.TransactionType`, which are plain Kotlin classes (Room annotations are on the classpath for tests).

### 3.1 `domain/SpendModels.kt`
- `enum class Period(val label: String) { WEEK("Week"), MONTH("Month"), YEAR("Year") }` (:15)
- `data class TimeRange(val start: Long, val end: Long)` (:17). Half-open `[start, end)` epoch ms.
- `data class PeriodWindow(val period: Period, val startDate: LocalDate, val endExclusive: LocalDate) { fun toRange(zone: ZoneId): TimeRange }` (:19-24)
- `object PeriodCalculator` (:26-47):
  - `fun current(period: Period, today: LocalDate, firstDay: DayOfWeek): PeriodWindow`. WEEK = `previousOrSame(firstDay)` + 7d; MONTH = 1st of the month + 1 month; YEAR = Jan 1 + 1 year.
  - `fun previous(w: PeriodWindow): PeriodWindow`
  - There is no `next()`, no arbitrary-range helper and no "last N months" helper. Add them here for cash-flow reports.
- `data class ChartBar(val label: String, val current: Long, val previous: Long)` (:49)
- `data class CategoryShare(val category: TransactionCategory, val amountMinor: Long, val percent: Float, val categoryKey: String = category.name, val categoryName: String = category.label, val categoryEmoji: String = "") { val displayLabel: String }` (:51-61)
- `data class AnalyticsSnapshot(val period: Period, val currentTotal: Long, val previousTotal: Long, val bars: List<ChartBar>, val categories: List<CategoryShare>)` (:63-69)
- `sealed interface NewTransaction` (:71-93):
  - `data class Entry(val type: TransactionType, val accountId: Long, val amountMinor: Long, val category: TransactionCategory? = null, val categoryKey: String? = category?.name, val merchant: String? = null, val note: String?, val timestamp: Long)`. **`note` has no default.**
  - `data class Transfer(val sourceAccountId: Long, val destinationAccountId: Long, val amountMinor: Long, val note: String?, val timestamp: Long, val sourceName: String? = null, val destinationName: String? = null)`. This is dead in practice: transfers are deleted on open.
- **`data class TransactionItem`** (:95-112), with every field:
  ```kotlin
  val id: Long,
  val type: TransactionType,
  val amountMinor: Long,              // absolute value
  val accountId: Long,
  val accountName: String,
  val destinationAccountId: Long? = null,
  val destinationAccountName: String? = null,
  val categoryKey: String? = null,
  val categoryName: String = "Other",
  val categoryEmoji: String = "🏷️",
  val categoryColorHex: String = "#E2E8F0",
  val merchant: String? = null,
  val note: String? = null,
  val source: String = "Chase",
  val timestamp: Long = 0L,
  val excludeFromSpending: Boolean = false
  ```
  It is constructed only in `SpendRepository.observeTransactionItems` (named args). Appending new fields **with defaults** is source-compatible.
- `object ChartBucketing { fun build(current: PeriodWindow, previous: PeriodWindow, currentRows: List<BucketTotal>, previousRows: List<BucketTotal>, locale: Locale): List<ChartBar> }` (:114-165). WEEK = 7 day bars (short day names); MONTH = `ceil(maxLen/7)` bars labelled "W1".."W5" (day-of-month `(d-1)/7`); YEAR = 12 month bars. Rows outside the window are ignored.

### 3.2 `domain/CategorizationEngine.kt`
- `enum class MatchType(val label: String, val priority: Int) { CONTAINS("Contains",1), STARTS_WITH("Starts with",2), EQUALS("Equals",3) }` (:5-9)
- `data class MerchantRule(val id: Long, val matchType: MatchType, val pattern: String, val categoryKey: String)` (:11-16)
- `data class HistoryEntry(val merchant: String, val categoryKey: String, val uses: Int)` (:19)
- `enum class ClassificationSource(val label: String) { RULE, HISTORY, DICTIONARY }` (:21-25)
- `sealed interface Classification { data class Matched(val categoryKey: String, val source: ClassificationSource, val matchedOn: String); object Unknown }` (:27-35)
- `object MerchantNormalizer { fun normalize(raw: String): String }` (:37-55). Lowercases, splits on non-word characters, drops noise words (ltd, plc, uk, card…), all-digit tokens and single letters.
- `object MerchantDictionary { val keywords: Map<String, List<String>>; fun match(normalized: String): Pair<String, String>? }` (:58-94)
- `object CategorizationEngine { fun classify(merchant: String, rules: List<MerchantRule>, history: List<HistoryEntry>, validKeys: Set<String>): Classification }` (:96-164). Pipeline order: rules (EQUALS > STARTS_WITH > CONTAINS, then longer pattern, then newest id), then history (most uses), then dictionary.

### 3.3 `domain/ExportFormatter.kt`
- `data class ExportRow(val id: Long, val date: String, val type: String, val account: String, val merchant: String?, val category: String, val group: String, val amountMinor: Long, val note: String?)` (:3-13)
- `data class ExportRule(val matchType: String, val pattern: String, val category: String)` (:15)
- `object ExportFormatter { fun amount(minor: Long): String; fun toCsv(rows: List<ExportRow>): String; fun toJson(exportedAt: String, rows: List<ExportRow>, rules: List<ExportRule>): String }` (:17-79). CSV uses CRLF and escapes formula-looking cells. JSON is hand-built (no serialization library).

### 3.4 Pure logic living outside `domain/`
- `AnalyticsViewModel.Companion` (`ui/AnalyticsViewModel.kt:144-219`): `fun calculateDaysElapsed(period: Period, today: LocalDate = LocalDate.now()): Long`, `fun calculateTotalDaysInPeriod(period: Period, today: LocalDate = LocalDate.now()): Long`, `fun generateInsights(period: Period, deltaPercent: Double?, snapshot: AnalyticsSnapshot, currentTxs: List<TransactionEntity>): List<SpendingTrendInsight>`. These are pure and tested.
- `ComparisonViewModel.calculateIntervalComparison(period: ComparisonPeriod, transactions: List<TransactionEntity>, today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()): List<DataPoint>` (`ComparisonViewModel.kt:289-377`), `isInitialBaseline(historyDays: Long, period: ComparisonPeriod): Boolean` (:137), `calculateHistoryDays(transactions: List<TransactionEntity>): Long` (:146). These are instance methods, tested by constructing `ComparisonViewModel()` with no repository.
- `ui/components/TransactionRow.kt:40` `fun groupTransactionsByDay(transactions: List<TransactionItem>): List<DayGroup>`. Pure, but uses `Calendar.getInstance()` / `Locale.getDefault()` for "Today/Yesterday".
- `ui/util/CurrencyUtils`, `ui/util/DateUtils` are pure JVM. `ColorUtils` depends on `androidx.compose.ui.graphics.Color` (a JVM class, so it works in unit tests).
- `parser/SpendParser` is pure Kotlin (tested).
- `data/TransactionDao.kt` and `data/AccountEntity.kt` top-level helpers (`normalizeBankName`, the `is*Duplicate` helpers) are pure.

**Inconsistency to be aware of:** the repository uses the locale's first day of the week for the Analytics windows (`SpendRepository.kt:33`). `AnalyticsViewModel` computes the income window with a hard-coded `DayOfWeek.MONDAY` (`AnalyticsViewModel.kt:91`), and `calculateDaysElapsed` uses MONDAY. `ComparisonViewModel` category mode uses the locale (`:219`) while interval mode uses MONDAY (`:304`). A new shared period utility should pick one and inject it.

---

## 4. DI and wiring

### 4.1 `AppContainer.kt` (whole file, 26 lines)
```kotlin
class AppContainer(context: Context) {
    val db = AppDatabase.get(context)
    val preferences = UserPreferences(context)
    val repository = SpendRepository(db.transactionDao(), db.accountDao(), db.catalogDao())

    val viewModelFactory = viewModelFactory {
        initializer { AnalyticsViewModel(repository) }
        initializer { AddTransactionViewModel(repository) }
        initializer { SettingsViewModel(repository, preferences) }
        initializer { TransactionsViewModel(repository) }
        initializer { com.spendtracker.app.ui.ComparisonViewModel(repository) }
    }
}
```
- This is manual DI. `AppContainer` is created **per Activity instance** (`MainActivity.kt:23`, `private val container by lazy { AppContainer(applicationContext) }`). It is not an app singleton. The DB is a singleton via `AppDatabase.get`.
- **To register a new ViewModel:** add one line `initializer { MyViewModel(repository /*, preferences */) }` inside `viewModelFactory { }` (lines 19-25), plus an import. `initializer<VM>` is keyed by the reified class, so each VM class can appear once. Then obtain it in a composable with `val vm: MyViewModel = viewModel(factory = container.viewModelFactory)`. Every existing VM is obtained in `SpendTrackerScreen` (lines 104-108) and scoped to the Activity.
- If a feature needs a new repository (for example `BudgetRepository`), add a `val` in `AppContainer` and build it from `db.<newDao>()`. The notification service does **not** use `AppContainer`; it calls `AppDatabase.get(applicationContext)` directly.

### 4.2 `MainActivity.kt`
- `class MainActivity : AppCompatActivity()` (AppCompat is required for `BiometricPrompt`).
- `onCreate` → `enableEdgeToEdge()` → `setContent { ... }`. It collects `preferences.observeTheme()` and `observeBiometricLock()` with `collectAsStateWithLifecycle(initialValue = ...)` and computes `isDark`. It wraps everything in `SpendTrackerTheme(darkTheme = isDark)`, then shows either `SpendTrackerScreen(container)` or `BiometricLockScreen(...)` (lines 33-72).
- Lock state lives in `isUnlocked` / `biometricErrorMessage` (`mutableStateOf` on the Activity, lines 26-27). Biometric prompt logic is at lines 75-134.
- There is no intent or deep-link handling and no `onNewIntent`.

### 4.3 `data/UserPreferences.kt`: plain **SharedPreferences** (not DataStore)
- File `"spend_tracker_prefs"`, `MODE_PRIVATE` (line 12).
- Keys (private consts, lines 15-18): `KEY_CURRENCY = "pref_currency_symbol"` (default "£"), `KEY_DEFAULT_PERIOD = "pref_default_period"` (Period name, default WEEK), `KEY_THEME = "pref_theme_mode"` (default `THEME_SYSTEM`), `KEY_BIOMETRIC_LOCK = "pref_biometric_lock_enabled"` (default false).
- Public consts: `THEME_SYSTEM = "SYSTEM"`, `THEME_LIGHT = "LIGHT"`, `THEME_DARK = "DARK"`.
- Properties (get/set, `apply()`): `var currencySymbol: String`, `var defaultPeriod: Period`, `var themeMode: String`, `var isBiometricLockEnabled: Boolean`.
- Observers built with `callbackFlow` + `OnSharedPreferenceChangeListener`: `fun observeCurrency(): Flow<String>`, `fun observeTheme(): Flow<String>`, `fun observeBiometricLock(): Flow<Boolean>` (lines 43-68). There is no `observeDefaultPeriod`.
- **Unused prefs:** `defaultPeriod` is not read by `AnalyticsViewModel` (it starts at `Period.WEEK`). `currencySymbol` is not used by `CurrencyUtils`.
- To add a pref: add a key const, a `var`, and if needed an `observeX()` that copies the callbackFlow pattern. This file is a hotspot.

---

## 5. UI layer

### 5.1 Conventions
- Single-Activity, single-screen, **no navigation library**. Tabs are an `Int` in `rememberSaveable`. Sheets and dialogs are boolean or nullable state in `SpendTrackerScreen`.
- Screens come in a stateful/stateless pair: `XScreen(viewModel, contentPadding, modifier)` collects `viewModel.uiState.collectAsStateWithLifecycle()` and delegates to a stateless `XContent(state, callbacks..., contentPadding, modifier)` (Analytics, Comparison).
- Sheets are **stateless** with respect to VMs: they take lists and callbacks. The exception is `SettingsSheet(viewModel, ...)`.
- VMs expose `StateFlow`s built with `.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)` or a private `MutableStateFlow` + `asStateFlow()`. One-shot callbacks are passed as `onSuccess: () -> Unit` lambdas into VM methods. There are no event channels. Errors are mostly swallowed (`catch (e: Exception) { // handle error }`).
- Collection uses `androidx.lifecycle.compose.collectAsStateWithLifecycle` everywhere (never plain `collectAsState`).
- Opt-ins: several files use `@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)` (`SpendTrackerScreen.kt:1`, `AddTransactionSheet.kt:1`, `CategoryPickerSheet.kt:1`), plus per-function `@OptIn(ExperimentalMaterial3Api::class)` and `@OptIn(ExperimentalLayoutApi::class)` for `FlowRow`.
- Icons: `androidx.compose.material.icons.Icons` with `Icons.Default.*`, `Icons.Filled.*` and `Icons.Outlined.*` (for example `Icons.Outlined.ReceiptLong`, `Icons.Filled.Insights`, `Icons.Outlined.CompareArrows`, `Icons.Outlined.AccountBalance`, `Icons.Outlined.Settings`, `Icons.Default.Add/Check/Close/Delete/Search/FileDownload/Refresh/ArrowDownward/ArrowUpward`, `Icons.Outlined.Edit`, `Icons.Outlined.NotificationsActive`). Extended icons are available. Some icons such as `ReceiptLong` and `CompareArrows` have AutoMirrored replacements in icons 1.6, but the old ones still compile, with deprecation warnings at most. Warnings are not errors in this build.
- Spacing idioms: horizontal screen padding 16.dp, section headers 20.dp. Shapes come from `MaterialTheme.shapes.*`. Older sheets hard-code `RoundedCornerShape(12/14.dp)` and hex `Color(0xFF...)` (Add/Edit/CategoryPicker/Settings were not fully migrated to theme tokens). New code should use `MaterialTheme.colorScheme` + `SpendTrackerTheme.finance`.
- Strings are hard-coded in Kotlin (no `strings.xml` usage beyond `app_name`).
- Emoji are used as category icons (`CategoryEntity.emoji`).

### 5.2 `ui/SpendTrackerScreen.kt` (822 lines, **the main hotspot**)
`@Composable fun SpendTrackerScreen(container: AppContainer)` (:100-339):
- VMs (:104-108): `analyticsVm`, `comparisonVm`, `addVm`, `settingsVm`, `transactionsVm`, all via `viewModel(factory = container.viewModelFactory)`.
- Collected state (:110-114): `accounts`, `groups`, `categories`, `transactionsList` (from `transactionsVm`), `addState` (from `addVm.state`).
- Local UI state (:116-121, 138): `selectedTab: Int` (rememberSaveable; 0 = Activity, 1 = Insights, 2 = Compare), `showSheet` (Add), `showSettings`, `showCategoryCreateSheet`, `editingTransaction: TransactionItem?` (`remember`, not saveable), `filterType: String` (`"ALL"|"EXPENSE"|"INCOME"|"BANK_<lowercase name>"`), `quickCategoryTx: TransactionItem?`.
- Notification permission check with a lifecycle observer on ON_RESUME (:124-136).
- `deduplicatedAccounts` filters out others/messages/savings/messaging (:140-149).
- `Scaffold` (:154-242): `Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)` with `TopAppBarDefaults.pinnedScrollBehavior()`.
  - `topBar` = `HeaderBar(title = tabs[selectedTab].title, subtitle = first 3 account names, scrollBehavior, actions = { Settings IconButton → showSettings = true })`.
  - `bottomBar` = `NavigationBar { tabs.forEachIndexed { NavigationBarItem(selected, onClick = { selectedTab = index }, icon = selectedIcon/icon, label) } }` (:173-190).
  - `floatingActionButton` = `ExtendedFloatingActionButton("Add")`, visible only when `selectedTab == 0` (:191-203).
  - Content (:204-241): a `Column(Modifier.padding(padding).fillMaxSize())` with `NotificationAccessBanner` (AnimatedVisibility), then `when (selectedTab) { 0 -> TransactionsFeed(...); 1 -> AnalyticsScreen(analyticsVm, PaddingValues(0.dp), Modifier.weight(1f)); 2 -> ComparisonScreen(comparisonVm, contentPadding = PaddingValues(0.dp), modifier = Modifier.weight(1f)) }`.
- Sheets and dialogs are rendered **after the Scaffold**, at the bottom of `SpendTrackerScreen` (:244-338): `AddTransactionSheet` (if `showSheet`), `EditTransactionSheet` (if `editingTransaction != null`), `CategoryPickerDialog` (if `quickCategoryTx != null`), `SettingsSheet` (if `showSettings`), `CategoryPickerSheet` (if `showCategoryCreateSheet`).
- `private enum class MainTab(val label: String, val title: String, val icon: ImageVector, val selectedIcon: ImageVector)` (:531-540): `TRANSACTIONS("Activity","Activity", Outlined.ReceiptLong, Filled.ReceiptLong)`, `ANALYTICS("Insights","Insights", Outlined.Insights, Filled.Insights)`, `COMPARE("Compare","Compare", Outlined.CompareArrows, Filled.CompareArrows)`. **The tab index is positional** and the `when (selectedTab)` uses literal ints 0/1/2, so inserting a tab in the middle shifts the others. The FAB visibility check uses `selectedTab == 0`.
- Private composables in this file: `TransactionsFeed(transactions, accounts, filterType, onFilterChange, onTransactionClick, onCategoryPillClick, contentPadding, modifier)` (:343-529). It computes the month hero (monthSpent, monthIncome, carriedOverSalary, netRemaining), account cards, filter chips and the day-grouped list. `SectionHeader(text)` (:543), `NotificationAccessBanner(onEnable)` (:553), `MonthSummaryCard(monthName, monthSpent, monthIncome, carriedOver, netRemaining, modifier)` (:589), `SummaryStat(icon, label, value, modifier)` (:675), `AutoSizeAmount(text, style)` (:711), `AccountCard(name, spent, income, remaining)` (:728), `EmptyTransactions()` (:792).
- The month and account totals are computed **inside composables** from `TransactionItem`s (:354-441). There is no VM state for them. A dashboard should move this into a pure function or VM.

### 5.3 Screens and sheets
| File | Main composable(s) and params |
|---|---|
| `ui/AnalyticsScreen.kt` | `fun formatMoney(minor: Long): String` (:55, public top-level). `@Composable fun AnalyticsScreen(viewModel: AnalyticsViewModel, contentPadding: PaddingValues, modifier: Modifier = Modifier)` (:58). `@Composable fun AnalyticsContent(state: AnalyticsUiState, onPeriodSelected: (Period) -> Unit, contentPadding: PaddingValues, modifier: Modifier = Modifier)` (:69): a LazyColumn with `SingleChoiceSegmentedButtonRow`/`SegmentedButton` period selector, `SummaryCard`, bar-chart card, insights, top categories. `@Composable fun DeltaBadge(deltaPercent: Double?, currentTotal: Long, isInitialPeriod: Boolean = false, modifier: Modifier = Modifier)` (:306). `@Composable fun SpendBarChart(bars: List<ChartBar>, modifier: Modifier = Modifier)` (:373): a Canvas grouped bar chart, 220.dp tall, using `rememberTextMeasurer` + `drawText`. Private: `SummaryCard`, `MicroKpiStrip`, `KpiPill`, `SpendingInsightCard`, `ChartLegend`, `LegendDot`, `CategoryRow` (uses `LinearProgressIndicator(progress = { ... })`, :454) |
| `ui/ComparisonScreen.kt` | `@Composable fun ComparisonScreen(viewModel: ComparisonViewModel = viewModel(), modifier: Modifier = Modifier, contentPadding: PaddingValues = PaddingValues(0.dp))` (:71). `ComparisonContent(state: ComparisonUiState, onSelectPeriod: (ComparisonPeriod) -> Unit, onSelectMode: (ComparisonMode) -> Unit = {}, modifier, contentPadding)` (:90). `ComparisonModeSelector(selected, onSelect, modifier)` (:201). `PeriodSelector(selected: ComparisonPeriod, onSelect, modifier)` (:240). `SummaryComparisonCard(state, modifier)` (:278). `OnboardingBaselineBanner(message = ..., modifier)` (:370). `ComparisonBadge(percentageChange: Double, isIncrease: Boolean = ..., referenceContext: String = "", modifier)` (:381, delegates to `components.ComparisonBadge`). `ComparisonBreakdownCard(item: DataPoint, maxValue: Double, modifier)` (:399). Private previews use `lightColorScheme()`/`darkColorScheme()` |
| `ui/AddTransactionSheet.kt` | `@Composable fun AddTransactionSheet(accounts: List<AccountEntity>, groups: List<CategoryGroupEntity>, categories: List<CategoryEntity>, isSaving: Boolean, errorMessage: String?, onDismiss: () -> Unit, onEvaluateCategory: (String) -> Classification, onCreateCategory: (name: String, groupId: Long?, newGroupName: String?, emoji: String, colorHex: String, type: TransactionType, ruleMerchant: String?) -> Unit, onSubmit: (NewTransaction) -> Unit)` (:72-90). A `ModalBottomSheet` with `rememberModalBottomSheetState(skipPartiallyExpanded = true)`. The date picker is the **platform `android.app.DatePickerDialog`** (:215), not the M3 DatePicker. `internal fun parseMinorUnits(input: String): Long?` (:485) |
| `ui/EditTransactionSheet.kt` | `@Composable fun EditTransactionSheet(transaction: TransactionItem, accounts: List<AccountEntity>, groups: List<CategoryGroupEntity>, categories: List<CategoryEntity>, onDismiss: () -> Unit, onSave: (id: Long, type: TransactionType, accountId: Long, destinationAccountId: Long?, amountMinor: Long, categoryKey: String?, merchant: String?, note: String?, excludeFromSpending: Boolean, ruleMerchant: String?) -> Unit, onDelete: (Long) -> Unit)` (:77-96). It has a delete `AlertDialog` and opens `CategoryPickerDialog`. Its `onCreateCategory` is a **no-op** (:428-430). The 10-arg positional `onSave` lambda is fragile: adding a field (tags, reviewed) means changing this signature **and** the call site in `SpendTrackerScreen.kt:270-285` **and** `TransactionsViewModel.updateTransaction` |
| `ui/CategoryPickerSheet.kt` | `val DEFAULT_EMOJIS`, `val DEFAULT_PASTEL_COLORS` (:68-69), `fun parseColorHex(hex: String): Color` (:71). `@Composable fun CategoryPickerDialog(groups, categories, selectedCategoryKey: String?, currentMerchant: String?, transactionType: TransactionType, onDismiss, onCategorySelected: (CategoryEntity) -> Unit, onCreateCategory: (7-arg lambda as above))` (:74), a full-screen-ish `Dialog(usePlatformDefaultWidth = false)`. `CategoryPickerSheet(...same...)` (:122), a ModalBottomSheet. `CategoryPickerContent(...same..., modifier: Modifier = Modifier)` (:162) has the search field, the grouped list (filters out hidden, TRANSFER and mismatched type) and the create form (FlowRow emoji and colour swatches, a group `DropdownMenu`, an auto-rule `Checkbox`) |
| `ui/SettingsSheet.kt` | `@Composable fun SettingsSheet(viewModel: SettingsViewModel, onDismiss: () -> Unit, onOpenAddCategory: () -> Unit)` (:83). A ModalBottomSheet (0.92 height) with `SecondaryTabRow` + `Tab` over `SettingsTab.values()`, then `when (currentTab)` → private `CategoriesTabContent` (:191), `RulesTabContent` (:293; always adds `"CONTAINS"` rules), `PreferencesTabContent(viewModel)` (:441; currency £/$/€/¥, default period, theme, biometric `Switch`), `DataActionsTabContent(viewModel, onResetComplete)` (:624; export JSON/CSV **to clipboard** + Toast, reset demo data `AlertDialog`) |
| `ui/HeaderBar.kt` | `@Composable fun HeaderBar(title: String, subtitle: String?, modifier: Modifier = Modifier, scrollBehavior: TopAppBarScrollBehavior? = null, actions: @Composable RowScope.() -> Unit = {})` (:21), wrapping `TopAppBar` |
| `ui/BiometricLockScreen.kt` | `@Composable fun BiometricLockScreen(errorMessage: String? = null, onAuthenticateClick: () -> Unit, onBypassFallback: (() -> Unit)? = null)` (:37) |

### 5.4 ViewModels: UiState and public API
- **`TransactionsViewModel(repository)`** (`ui/TransactionsViewModel.kt:16-103`): `val accounts: StateFlow<List<AccountEntity>>`, `val groups: StateFlow<List<CategoryGroupEntity>>`, `val categories: StateFlow<List<CategoryEntity>>`, `val transactions: StateFlow<List<TransactionItem>>`. Methods: `fun updateTransaction(id: Long, type: TransactionType, accountId: Long, destinationAccountId: Long?, amountMinor: Long, categoryKey: String?, merchant: String?, note: String?, excludeFromSpending: Boolean, ruleMerchant: String? = null, onSuccess: () -> Unit)`, `fun quickSetCategory(id: Long, categoryKey: String, ruleMerchant: String? = null)`, `fun deleteTransaction(id: Long, onSuccess: () -> Unit)`. Rules are created with the invalid `"EXACT"` match type.
- **`AddTransactionViewModel(repository)`** (`ui/AddTransactionViewModel.kt`): `data class AddTransactionUiState(val isSaving: Boolean = false, val error: String? = null)` (:25-28). StateFlows: `accounts`, `groups`, `categories`, `rules: StateFlow<List<MerchantRuleEntity>>`, `history: StateFlow<List<HistoryEntry>>`, `state: StateFlow<AddTransactionUiState>`. Methods: `fun evaluateCategory(merchant: String): Classification` (synchronous; reads `.value` of `rules`/`categories`/`history`, which are only populated while subscribed), `fun createCategory(name: String, groupId: Long?, newGroupName: String?, emoji: String, colorHex: String, type: TransactionType, ruleMerchant: String?, onComplete: (CategoryEntity) -> Unit)`, `fun submit(transaction: NewTransaction, onSuccess: () -> Unit)`, `fun clearError()`.
  - Latent bug: `rules`/`history`/`categories` in this VM use `WhileSubscribed` with initial `emptyList()`. `SpendTrackerScreen` only collects `addVm.state`, so nothing ever subscribes to them. `evaluateCategory` therefore runs with an empty `validKeys` and always returns `Classification.Unknown`, which means live auto-categorization in the Add sheet never fires. Fix it with `SharingStarted.Eagerly` or by collecting the flows in the UI.
- **`SettingsViewModel(repository, preferences)`** (`ui/SettingsViewModel.kt`): `data class SettingsUiState(val selectedTab: SettingsTab = SettingsTab.CATEGORIES, val isPerformingAction: Boolean = false, val feedbackMessage: String? = null)`. `enum class SettingsTab(val label: String) { CATEGORIES, RULES, PREFERENCES, DATA }` (:26-31). StateFlows: `uiState`, `groups`, `categories`, `rules`. Flows: `currencySymbol: Flow<String>`, `themeMode: Flow<String>`, `isBiometricLockEnabled: Flow<Boolean>`. Methods: `getCurrentCurrency()/setCurrency(symbol)`, `getDefaultPeriod()/setDefaultPeriod(period)`, `getThemeMode()/setThemeMode(mode)`, `isBiometricEnabled()/setBiometricEnabled(enabled)`, `selectTab(tab)`, `addRule(matchType: String, pattern: String, categoryKey: String)`, `deleteRule(id: Long)`, `addCategory(name: String, groupId: Long?, newGroupName: String?, emoji: String, colorHex: String, type: TransactionType = EXPENSE, ruleMerchant: String? = null)`, `deleteCategory(key: String)`, `resetDemoData(onComplete: () -> Unit)`, `suspend fun getExportJson(): String`, `suspend fun getExportCsv(): String`, `clearFeedback()`.
- **`AnalyticsViewModel(repository)`** (`ui/AnalyticsViewModel.kt`): `data class SpendingTrendInsight(val title: String, val description: String, val emoji: String)` (:31). `data class AnalyticsUiState(period: Period = WEEK, isLoading: Boolean = true, currentTotalMinor: Long = 0, previousTotalMinor: Long = 0, deltaPercent: Double? = null, dailyAverageMinor: Long = 0, projectedPeriodTotalMinor: Long? = null, totalIncomeMinor: Long = 0, netSavingsMinor: Long = 0, savingsRatePercent: Double? = null, bars: List<ChartBar> = emptyList(), categories: List<CategoryShare> = emptyList(), insights: List<SpendingTrendInsight> = emptyList(), error: String? = null, isInitialPeriod: Boolean = false, onboardingMessage: String = "Building your baseline: ...")` (:37-55). `val uiState: StateFlow<AnalyticsUiState>` = `selectedPeriod.flatMapLatest { combine(repository.observeAnalytics(period), repository.observeTransactions()) {...} .onStart{...}.catch{...} }.stateIn(...)` (:62-138; annotated `@OptIn(ExperimentalCoroutinesApi::class)`). `fun onPeriodSelected(period: Period)`. Companion functions are in 3.4.
- **`ComparisonViewModel(repository: SpendRepository? = null)`** (`ui/ComparisonViewModel.kt`): `enum class ComparisonPeriod(val label: String, val referenceContext: String) { WEEK, MONTH, YEAR }` (:31-38), `enum class ComparisonMode(val label: String) { CATEGORIES("By Category"), INTERVAL("By Time") }` (:40-43), `data class DataPoint(val label: String, val currentValue: Double, val previousValue: Double) { val difference: Double; val percentageChange: Double }` (:48-62), `data class ComparisonUiState(selectedPeriod = WEEK, comparisonMode = CATEGORIES, currentPeriodTotal: Double = 0.0, previousPeriodTotal: Double = 0.0, percentageChange: Double = 0.0, isIncrease: Boolean = false, breakdownItems: List<DataPoint> = emptyList(), isLoading: Boolean = false, isInitialPeriod: Boolean = false, historyDays: Long = 0L, onboardingMessage: String = ...)` (:67-80). `val uiState: StateFlow<ComparisonUiState>`. Methods: `setInitialPeriod(isInitial: Boolean)`, `isInitialBaseline(...)`, `calculateHistoryDays(...)`, `selectPeriod(period: ComparisonPeriod)`, `selectMode(mode: ComparisonMode)`, `calculateIntervalComparison(...)`. **Falls back to hard-coded sample data** when there are no transactions (`generateSampleCategoryData`, :261-287). It collects in `init` via `viewModelScope.launch { combine(...).collect {} }` (eager, not `stateIn`).

### 5.5 Reusable components (`ui/components/`)
| File:line | Signature |
|---|---|
| `AccountSelector.kt:30` | `@Composable fun AccountDropdownSelector(accounts: List<AccountEntity>, selected: AccountEntity?, onAccountSelected: (AccountEntity) -> Unit, modifier: Modifier = Modifier, label: String = "Account (Source)")` (uses `DropdownMenu`) |
| `CategoryBadge.kt:35` | `@Composable fun CategoryEmojiBox(emoji: String, colorHex: String?, size: Dp = 44.dp, emojiSize: TextUnit = 20.sp, cornerRadius: Dp? = null, modifier: Modifier = Modifier)` (circle by default) |
| `CategoryBadge.kt:60` | `@Composable fun CategoryPill(categoryName: String, emoji: String, modifier: Modifier = Modifier, showChevron: Boolean = true, onClick: (() -> Unit)? = null)` |
| `MetricCard.kt:23` | `@Composable fun MetricCard(modifier: Modifier = Modifier, containerColor: Color = MaterialTheme.colorScheme.surface, cornerRadius: Dp = 16.dp, elevation: Dp = 2.dp, contentPadding: Dp = 18.dp, spacing: Dp = 10.dp, content: @Composable ColumnScope.() -> Unit)` (Card-based; currently unused, so it suits dashboard tiles) |
| `StatusBadge.kt:39` | `@Composable fun StatusPill(isPositive: Boolean, amountMinor: Long, modifier: Modifier = Modifier)` ("Left"/"Over" + amount, finance colours) |
| `StatusBadge.kt:80` | `@Composable fun TrendBadge(deltaPercent: Double?, currentTotal: Long = 0L, isInitialPeriod: Boolean = false, modifier: Modifier = Modifier)` |
| `StatusBadge.kt:134` | `@Composable fun ComparisonBadge(percentageChange: Double, isIncrease: Boolean = percentageChange >= 0.0, referenceContext: String = "", modifier: Modifier = Modifier)` |
| `StatusBadge.kt:186` | `@Composable fun BaselineBanner(message: String, modifier: Modifier = Modifier)` |
| `TransactionRow.kt:33` | `data class DayGroup(val dayKey: String, val dayTitle: String, val daySpendTotal: Long, val items: List<TransactionItem>)` |
| `TransactionRow.kt:40` | `fun groupTransactionsByDay(transactions: List<TransactionItem>): List<DayGroup>` (input must already be sorted desc) |
| `TransactionRow.kt:101` | `@Composable fun TransactionRowItem(tx: TransactionItem, onClick: () -> Unit, onCategoryClick: () -> Unit, modifier: Modifier = Modifier, isFirst: Boolean = true, isLast: Boolean = true)` (`Surface(onClick=...)` row; unknown vendor is shown in error colour with an edit icon; amount uses `CurrencyUtils.formatSignedMinor`, income in `finance.income`). This is a natural place for "needs review" and tag chips. It is a shared hotspot |

Name collisions: package `com.spendtracker.app.ui` already defines the public top-level names `formatMoney`, `DeltaBadge`, `SpendBarChart`, `ComparisonBadge`, `PeriodSelector`, `ComparisonModeSelector`, `SummaryComparisonCard`, `OnboardingBaselineBanner`, `ComparisonBreakdownCard`, `parseColorHex`, `DEFAULT_EMOJIS`, `DEFAULT_PASTEL_COLORS`, `CategoryPickerContent`, `HeaderBar`, plus the file-private `SummaryCard`, `SectionHeader`, `AccountCard`, `CategoryRow`, `KpiPill`, `LegendDot`, `LegendIndicator`, `MonthSummaryCard`, `SummaryStat`, `EmptyTransactions`. **`ComparisonBadge` exists in both `ui` and `ui.components`.** Do not reuse these names for new public top-level declarations in `ui`. Prefer a sub-package per feature (`ui.budgets`, `ui.recurring`, etc.) to avoid conflicts between parallel engineers.

### 5.6 Theme (`ui/theme/`)
- `@Composable fun SpendTrackerTheme(darkTheme: Boolean, content: @Composable () -> Unit)` (`Theme.kt:123-149`). It provides `LocalFinanceColors`, `MaterialTheme(colorScheme = Light/DarkColors, typography = SpendTypography, shapes = SpendShapes)`, and sets light/dark system-bar icons.
- `object SpendTrackerTheme { val finance: FinanceColors @Composable get() }` (`Theme.kt:118-121`). There is a function and an object with the same name. Call it as `SpendTrackerTheme.finance.income` from composables.
- `@Immutable data class FinanceColors(val income: Color, val expense: Color, val surplusContainer: Color, val onSurplusContainer: Color, val deficitContainer: Color, val onDeficitContainer: Color)` (`Theme.kt:88-96`). Light/Dark values at :98-114. `val LocalFinanceColors = staticCompositionLocalOf { LightFinance }` (:116). For budgets (over/under), use `surplusContainer`/`deficitContainer`.
- The colour scheme sets primary, secondary, tertiary, error (+ containers), background, surface, surfaceVariant, outline/outlineVariant, `surfaceTint`, and **all `surfaceContainer*` roles** (`Theme.kt:17-81`; values in `Color.kt`, all `internal val`). The palette is an indigo primary `#3D5AA8` and an evergreen tertiary.
- `val SpendTypography` (`Type.kt:16-27`): M3 defaults with SemiBold display/headline/title and Medium labels.
- `val SpendShapes = Shapes(extraSmall = 6.dp, small = 10.dp, medium = 16.dp, large = 24.dp, extraLarge = 32.dp)` (`Type.kt:29-35`).
- Legacy `ColorUtils` constants (`InflowGreen`, `OutflowRed`, `Surplus*/Deficit*Light/Dark`) ignore the theme. Prefer `SpendTrackerTheme.finance`.

### 5.7 Utils (`ui/util/`)
- `object CurrencyUtils` (`CurrencyUtils.kt`): `fun formatMinor(minor: Long, locale: Locale = Locale.getDefault()): String` (:16), `fun formatMajor(amount: Double, locale: Locale = Locale.getDefault()): String` (:24), `fun formatSignedMinor(minor: Long, isPositive: Boolean, locale: Locale = Locale.getDefault()): String` (:31; uses "+" and U+2212 "−"), `fun formatSignedMinor(minor: Long, locale: Locale = Locale.getDefault()): String` (:39), `fun minorToDecimalString(minor: Long): String` (:46), `fun parseDecimalToMinor(input: String): Long?` (:54; null for ≤0, >2 dp, or invalid input).
- `object DateUtils` (`DateUtils.kt`): `formatTime(timestamp: Long, locale = default): String` ("HH:mm"), `formatDateShort` ("d MMM"), `formatDateFull` ("EEEE, d MMM"), `formatDateTime` ("dd MMM yyyy, HH:mm"), `formatDateYear` ("d MMM yyyy"), `formatCurrentMonthName(locale)` ("MMMM"), `getStartOfMonthTimestamp(calendar: Calendar = Calendar.getInstance()): Long`. These use `SimpleDateFormat`/`Calendar` (not java.time).
- `object ColorUtils` (`ColorUtils.kt`): the colour constants above, `fun parseColorHex(hex: String): Color` (fallback `#FED7AA`), `fun getBankBrandColors(bankName: String): Pair<Color, Color>` (background tint, accent).

### 5.8 Material3 1.2.0 / Compose 1.6 API notes (inferred from usage plus library versions)
Already used and known to compile here: `Scaffold`, `TopAppBar` + `TopAppBarDefaults.pinnedScrollBehavior()` (experimental), `NavigationBar`/`NavigationBarItem`, `ExtendedFloatingActionButton`, `ModalBottomSheet` + `rememberModalBottomSheetState(skipPartiallyExpanded = true)` (experimental), `SingleChoiceSegmentedButtonRow`/`SegmentedButton`/`SegmentedButtonDefaults.itemShape(index, count)` (experimental), `SecondaryTabRow` + `Tab` (new in 1.2; `PrimaryTabRow` is imported too), `FilterChip` + `FilterChipDefaults.IconSize`, `Card`/`OutlinedCard`/`ElevatedCard`/`CardDefaults`, `Surface(onClick = ...)`, `HorizontalDivider`, `LinearProgressIndicator(progress = { float })` (lambda overload; the Float overload is deprecated in 1.2), `CircularProgressIndicator`, `OutlinedTextField(prefix = ...)`, `DropdownMenu`/`DropdownMenuItem`, `AlertDialog(onDismissRequest, title, text, confirmButton, dismissButton)`, `Switch`, `Checkbox`, `IconButton`, `TextButton`/`Button`/`OutlinedButton`, `ButtonDefaults.buttonColors`, `colorScheme.surfaceContainer*`. Compose UI/foundation: `FlowRow` (`@OptIn(ExperimentalLayoutApi::class)`), `Canvas` + `rememberTextMeasurer()` + `DrawScope.drawText`, `LazyColumn`/`LazyRow` with keys, `AnimatedVisibility`, `androidx.compose.ui.window.Dialog(properties = DialogProperties(usePlatformDefaultWidth = false))`, `imePadding()`, `navigationBarsPadding()`, `LocalLifecycleOwner` from `androidx.compose.ui.platform` (correct for 1.6; it moved in later versions), `LocalClipboardManager`.

Available in material3 1.2.0 but **not yet used** (all need `@OptIn(ExperimentalMaterial3Api::class)`):
- `SearchBar` / `DockedSearchBar` use the **1.2 signature** `(query: String, onQueryChange, onSearch, active: Boolean, onActiveChange, ...)`. The `inputField =`/`expanded` API is 1.3+ and **does not exist** here. For a simple filter field, the existing pattern `OutlinedTextField(leadingIcon = Search)` (`CategoryPickerSheet.kt:216`) is lower-risk.
- `DatePicker`, `DatePickerDialog`, `rememberDatePickerState`, `DateRangePicker`, `rememberDateRangePickerState`: available. `DatePickerDialog(onDismissRequest, confirmButton, ...) { DatePicker(state) }`. The `selectableDates` parameter on `rememberDatePickerState` exists in 1.2 (it is an `interface SelectableDates`). The app currently uses the platform `android.app.DatePickerDialog`.
- Pull-to-refresh: in 1.2 it is `PullToRefreshContainer(state)` + `rememberPullToRefreshState()` + `Modifier.nestedScroll(state.nestedScrollConnection)`. **`PullToRefreshBox` does not exist (1.3+).** There is nothing to refresh from (data is local Flow), so avoid it.
- `SwipeToDismissBox` + `rememberSwipeToDismissBoxState` (1.2 names; `SwipeToDismiss`/`DismissState` are the deprecated 1.1 names).
- `ExposedDropdownMenuBox` uses the **no-arg** `Modifier.menuAnchor()` (the `menuAnchor(type, enabled)` overload is 1.3+).
- `TooltipBox` + `PlainTooltip` + `rememberTooltipState()` + `TooltipDefaults.rememberPlainTooltipPositionProvider()`.
- `ListItem`, `Badge`/`BadgedBox`, `AssistChip`/`InputChip`/`SuggestionChip`/`ElevatedFilterChip`, `Slider`/`RangeSlider`, `TimePicker`, `CenterAlignedTopAppBar`/`LargeTopAppBar`, `ModalNavigationDrawer`: all available.
- `MultiChoiceSegmentedButtonRow` exists in 1.2 (experimental). Foundation 1.6 items that need `@OptIn(ExperimentalFoundationApi::class)`: `HorizontalPager`, `LazyListScope.stickyHeader`, `Modifier.animateItemPlacement()`.
- **Not available in this BOM:** `PullToRefreshBox`, the M3 carousel, `material3-adaptive`/`NavigationSuiteScaffold`, `SearchBar(inputField = …)`/`expanded`, `MaterialTheme.motionScheme`, `LazyItemScope.animateItem()` (1.7+).
- Coroutines `combine` has typed overloads up to 5 flows; beyond that use the `combine(listOf(...)) { arr -> }` / nested form.

---

## 6. Tests (`app/src/test/java/com/spendtracker/app/`)

Style: **plain JUnit4** (`org.junit.Test`, `org.junit.Assert.*`). Test names are backtick sentences. There is no Robolectric, no mocking framework, no coroutine test utilities, and no Android framework calls. Tests pin `Locale.US` and `ZoneId.of("UTC")` explicitly and use fixed `LocalDate`s (for example `LocalDate.of(2026, 11, 4)`). They build `TransactionEntity(...)` directly with named args. There are 70+ tests.

| File | Covers |
|---|---|
| `parser/SpendParserTest.kt` (325 lines, 23 tests) | `SpendParser.parse` for pipe formats, Money In/Out, untrusted packages, Premier → HSBC; `isMerchantDuplicate`, `isTransactionDuplicate`, `normalizeBankName`, `resolvePreferredBankSource` |
| `domain/CategorizationEngineTest.kt` (6 tests) | rule > history > dictionary precedence, word boundaries, longest keyword, `ExportFormatter` JSON/CSV |
| `domain/ChartBucketingTest.kt` (6) | `ChartBucketing.build` WEEK/MONTH/YEAR/empty |
| `domain/PeriodCalculatorTest.kt` (9) | `PeriodCalculator.current/previous`, `PeriodWindow.toRange` |
| `ui/AnalyticsCalculationTest.kt` (6) | `AnalyticsViewModel.calculateDaysElapsed/TotalDays/generateInsights`, `CategoryShare.displayLabel` |
| `ui/ComparisonIntervalTest.kt` (5) | `ComparisonViewModel().calculateIntervalComparison` |
| `ui/ComparisonCalculationTest.kt` (8) | `DataPoint`, `ComparisonPeriod`, baseline guard, `setInitialPeriod`/`selectPeriod` on a repository-less VM |
| `ui/util/CurrencyUtilsTest.kt` (9) | `CurrencyUtils` with `Locale.US` |

How to add tests for new pure logic: put the logic in an `object` or top-level functions with **no `android.*` imports**. Pass `today: LocalDate`, `zone: ZoneId` and `locale: Locale` as parameters (do not call `LocalDate.now()` inside). Place the test under `app/src/test/java/com/spendtracker/app/<pkg>/XxxTest.kt` with JUnit4.
Pitfalls:
- `android.util.Log`, `TextUtils`, `Uri`, `Context`, etc. throw "Method ... not mocked" in JVM unit tests, because `unitTests.isReturnDefaultValues` is not set. `AppDatabase.kt` repair functions call `android.util.Log`, so do not test them.
- Do not test Flows that need `viewModelScope` / `Dispatchers.Main`: there is no `kotlinx-coroutines-test` and no Main dispatcher on the JVM. `runBlocking` from kotlinx-coroutines-core (transitively on the test classpath) works for plain suspend functions and `flow.first()`. A VM whose `init` uses `viewModelScope.launch` crashes in a JVM test (`ComparisonViewModel` avoids this only when `repository == null`).
- DAOs and migrations cannot be tested (no Room testing, no instrumented tests in CI).

---

## 7. Notification pipeline (`service/SpendNotificationListenerService.kt`)

`class SpendNotificationListenerService : NotificationListenerService()`. `onNotificationPosted(sbn)` (:37-218):
1. Reads `EXTRA_TITLE`, `EXTRA_TEXT` and `EXTRA_BIG_TEXT`, then `SpendParser.parse(packageName, title, content)` (:51) → `ParsedExpense?` (`parser/SpendParser.kt:18-29`: `type` "IN"/"OUT", `amount: Double`, `currency`, `merchant`, `category` (a human label like "Groceries"/"General Spend"), `source`, `rawText`, `merchantIsFallback: Boolean`, `direction`). Returns null for untrusted packages, promos, OTPs and balances.
2. Resolves the bank with `resolveBankName(packageName, parsed.source, appLabel)` (:246-331) + `normalizeBankName`.
3. Legacy write: `SpendDatabase(applicationContext).insertExpense(parsedWithBank, eventTimestamp)` (:68-69) plus a package-scoped broadcast `ACTION_NEW_EXPENSE` (no receivers exist).
4. Room write on `CoroutineScope(Dispatchers.IO)` under a companion `Mutex` (:86-216):
   - `roomDb.accountDao().getOrCreate(detectedBankName)` (auto-creates accounts)
   - minor amount, `txType` = INCOME if "IN" else EXPENSE
   - rules from `catalogDao().getRules()` → `MerchantRule` (invalid matchType dropped), `validKeys` = non-hidden categories of the same type
   - `CategorizationEngine.classify(merchant, rules, history = emptyList(), validKeys)` (:109-114). **History is not used here.**
   - `resolvedCategoryKey` = the matched key, or `mapParsedCategoryToCatalogKey(parsed.category, txType, validKeys)` (:220-244), which falls back to `OTHER_EXPENSE`/`OTHER_INCOME`
   - `merchantName` = null if `merchantIsFallback` or generic (:122)
   - `transactionDao().findMatchingDuplicate(..., windowMillis = 60_000L)` (:125-132)
   - **Duplicate found** → enrich and `update(existingTx.copy(...))` (:134-194)
   - **New** → `insert(TransactionEntity(..., note = "Captured from ${sourceAccount.name} alert: $rawAlertText", excludeFromSpending = false, ...))` (:195-211)

**Where to set a "needs review" flag:** in the `TransactionEntity(...)` constructor at `SpendNotificationListenerService.kt:197-207`, for example `needsReview = classification !is Classification.Matched || merchantName == null`. You have both `classification` (:109) and `merchantName` (:122) in scope. Also consider the enrich path (:184-192), and manual entries in `SpendRepository.add` (:193-205; manual entries are probably reviewed by definition). Auto-captured rows can be recognized today by `note` starting with `"Captured from "`, but a column is more robust.

Not a Room migration risk, but note: the service opens Room via `AppDatabase.get`. That triggers the same `onOpen` repair and the same migrations as the UI, in the same process.

---

## 8. Extension points and risks

### 8.1 Add a new Room entity + DAO + migration (version 7 → 8)
1. Create the entity, for example `data/BudgetEntity.kt`, with `@Entity(tableName = "budgets", indices = [...])`. Use only `Long`/`Int`/`String`/`Boolean`/`Double` and nullable variants. For enums, store the `String` name (like `matchType`), or reuse `TransactionType` (converter exists). Do not add new `@TypeConverter`s unless needed: a converter class must be added to `@TypeConverters` on `AppDatabase`. Money is `Long` minor units. Category references are `categoryKey: String`. Account references are `accountId: Long`. Do not use foreign keys: none exist, and FKs would make the repair routines that delete or remap rows fail.
2. Create the DAO as an `abstract class` or `interface` with `@Dao`. Use `Flow<...>` for observers and `suspend` for one-shots, following existing DAOs.
3. Edit `data/AppDatabase.kt` (**the single most contended file**):
   - add the entity class to `entities = [...]` (:557-563)
   - bump `version = 7` → `8` (:564). **Only one feature can own 7→8.** If several features add tables, either one engineer writes a combined `MIGRATION_7_8`, or features are serialized as 7→8, 8→9, etc., each with its own `val MIGRATION_X_Y` and its own registration in `.addMigrations(...)` (:582). Decide this up front.
   - add `abstract fun budgetDao(): BudgetDao` (:569-571 region)
   - add `val MIGRATION_7_8 = object : Migration(7, 8) { override fun migrate(db: SupportSQLiteDatabase) { db.execSQL("CREATE TABLE IF NOT EXISTS `budgets` (...)"); db.execSQL("CREATE INDEX IF NOT EXISTS `index_budgets_<col>` ON `budgets` (`<col>`)") } }`, following the `MIGRATION_2_3` style
   - register it in `.addMigrations(MIGRATION_1_2, ..., MIGRATION_6_7, MIGRATION_7_8)` (:582)
   - fresh installs get the table from Room automatically (`onCreate` only seeds). If the feature needs seed rows, add them to **both** `onCreate` (:584-587) **and** the migration.
4. **The hand-written SQL must match Room's expected schema exactly** (validated only on upgrade, at runtime, with no CI coverage):
   - `@PrimaryKey(autoGenerate = true) val id: Long = 0` → `` `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL ``
   - non-null `Long`/`Int`/`Boolean` → `INTEGER NOT NULL`; `String` → `TEXT NOT NULL`; `Double` → `REAL NOT NULL`; nullable → no `NOT NULL`
   - if the entity has `@ColumnInfo(defaultValue = "X")`, the SQL must have `DEFAULT X` with an identical literal. If the entity has no defaultValue, Room ignores DB defaults.
   - index names must be `index_<table>_<col1>_<col2>`, and `unique` must match
   - a non-autoGenerate `String` PK (like `categories.key`) → `TEXT NOT NULL` + `PRIMARY KEY(...)`
   - a mismatch throws `IllegalStateException: Migration didn't properly handle...` on first launch after update. A missing migration throws "A migration from 7 to 8 was required but not found" (no destructive fallback).
5. Add a repository (new class, or new methods on `SpendRepository`; prefer a new class to avoid conflicts), wire it in `AppContainer`, and register a VM.
6. Changing any entity or index without bumping the version → "Room cannot verify the data integrity" crash on every existing install.

### 8.2 Add a new column on `transactions`
- Add a field **with a Kotlin default** at the end of `TransactionEntity` (`TransactionEntity.kt:63-76`), for example `@ColumnInfo(defaultValue = "0") val needsReview: Boolean = false` or `val tags: String? = null`.
- Migration: `db.execSQL("ALTER TABLE transactions ADD COLUMN needsReview INTEGER NOT NULL DEFAULT 0")`. For non-null columns, both `NOT NULL DEFAULT x` in SQL **and** `@ColumnInfo(defaultValue = "x")` on the entity are required. This is the existing pattern (`MIGRATION_1_2` + `excludeFromSpending`). Nullable: `ALTER TABLE transactions ADD COLUMN note2 TEXT` with no `@ColumnInfo`.
- Version bump and migration as in 8.1, in the same shared `MIGRATION_7_8` if several features touch it.
- Every `TransactionEntity(...)` construction uses named args, so a defaulted field is source-compatible. Call sites: `TransactionDao.kt` (3), `SpendRepository.kt` (insert and demo seeds), `SpendNotificationListenerService.kt:197`, tests.
- `.copy(...)` updates keep the new column automatically (`SpendRepository.updateTransaction` uses `existing.copy`). The onOpen dedup UPDATE does not touch it.
- Raw SQL in `AppDatabase.kt` repair selects explicit columns, so it is unaffected.
- To surface the column in the UI: add a field to `TransactionItem` (`SpendModels.kt:95-112`, with a default), map it in `SpendRepository.observeTransactionItems` (:88-105), and pass it through `EditTransactionSheet.onSave` → `SpendTrackerScreen` → `TransactionsViewModel.updateTransaction` → `SpendRepository.updateTransaction`. Those are four signatures in four files. Consider a dedicated repository method instead (for example `setReviewed(id: Long, reviewed: Boolean)` via a DAO `@Query("UPDATE transactions SET needsReview = :v WHERE id = :id")`).
- Tags: there is no tags table. Either add a `tags TEXT` column (comma-separated) or create `tags` + `transaction_tags(transactionId INTEGER NOT NULL, tagId INTEGER NOT NULL, PRIMARY KEY(transactionId, tagId))` tables. With a join table, orphan rows remain after the onOpen dedup deletes transactions (there are no FKs, so no cascade). Clean them up in queries (`WHERE transactionId IN (SELECT id FROM transactions)`).

### 8.3 Add a bottom-nav tab, a full screen or a sheet
- **Tab**: edit `SpendTrackerScreen.kt`. Add an entry to `private enum class MainTab` (:531-540) and a branch to `when (selectedTab)` (:218-240). Append new tabs at the end, or change the `when` to switch on `tabs[selectedTab]` (the enum) instead of literal ints. Note the FAB condition `selectedTab == 0` (:193). The M3 guidance is 3-5 destinations, so with a Dashboard/Budgets/Recurring/Goals set you will exceed 5. Decide on the IA early (for example Dashboard · Activity · Budgets · Insights + "More"). `rememberSaveable` stores the Int index, so reordering tabs only affects restore after process death.
- **Full screen without navigation-compose**: add state in `SpendTrackerScreen` (for example `var openScreen by rememberSaveable { mutableStateOf<String?>(null) }`) and render the screen over the Scaffold, or as a `when` inside the content, or as a full-height `ModalBottomSheet`. Handle back with `androidx.activity.compose.BackHandler(enabled = openScreen != null) { openScreen = null }` (activity-compose 1.8.2 has `BackHandler`). There is no existing back handling: sheets dismiss themselves.
- **Sheet**: copy the pattern of `AddTransactionSheet`: a stateless composable taking data + callbacks, `ModalBottomSheet(onDismissRequest, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true))`, an inner `Column(Modifier.fillMaxWidth().imePadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp))`. Show it from `SpendTrackerScreen` with an `if (showX)` block after the Scaffold (:244-338).
- Header actions: `HeaderBar(actions = { ... })` at :166-170. Add icons there (search, filters).
- Each screen gets `contentPadding = PaddingValues(0.dp)` and `Modifier.weight(1f)` from the parent Column (the Scaffold padding is already applied).

### 8.4 Add a new ViewModel
1. `class XViewModel(private val repository: SpendRepository /*, preferences: UserPreferences */) : ViewModel()`. Expose `val uiState: StateFlow<XUiState>` via `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), XUiState())`. Put calculation in pure `companion object` or `domain/` functions so they can be unit-tested.
2. Register it in `AppContainer.viewModelFactory` (`AppContainer.kt:19-25`) with `initializer { XViewModel(repository) }` + an import.
3. Obtain it: `val xVm: XViewModel = viewModel(factory = container.viewModelFactory)` in `SpendTrackerScreen`, or anywhere `container` is reachable. `container` is only passed to `SpendTrackerScreen`, so child screens receive the VM as a parameter.
4. Collect with `collectAsStateWithLifecycle()`.
5. Do not read `StateFlow.value` of `WhileSubscribed` flows that nobody collects (see the `AddTransactionViewModel.evaluateCategory` bug). Use `SharingStarted.Eagerly`, or collect in UI.

### 8.5 Hotspot files (assign one owner each)
| File | Why multiple features touch it |
|---|---|
| `data/AppDatabase.kt` | entities list, `version`, migrations, `addMigrations`, DAO getters, onCreate seeding, onOpen repair (dedup, transfer deletion) |
| `data/TransactionEntity.kt` | new columns (review flag, tags, recurring link, goal link) |
| `domain/SpendModels.kt` | `TransactionItem` fields, `Period`/`PeriodCalculator` extensions (cash-flow ranges) |
| `data/SpendRepository.kt` | mapping to `TransactionItem`, new queries, `updateTransaction` signature |
| `data/TransactionDao.kt` | new aggregate and search queries (`SPEND_WHERE` lives here) |
| `AppContainer.kt` | every new VM and repository |
| `ui/SpendTrackerScreen.kt` | tabs (`MainTab`), `when(selectedTab)`, FAB, header actions, all sheet and dialog state, `TransactionsFeed` (search, filters, review chips), hero card (dashboard) |
| `ui/components/TransactionRow.kt` | review badge, tags chips, recurring icon on rows |
| `ui/EditTransactionSheet.kt` + `ui/TransactionsViewModel.kt` | the 10-arg `onSave` / `updateTransaction` chain (tags, review, notes) |
| `data/UserPreferences.kt` | any new setting (budget rollover, dashboard layout, first day of week) |
| `ui/SettingsSheet.kt` + `ui/SettingsViewModel.kt` | `SettingsTab` enum (new settings tabs, for example Budgets or Tags management) |
| `service/SpendNotificationListenerService.kt` | needs-review flag, recurring-bill matching on capture |
| `app/build.gradle.kts` | any new dependency, versionCode/versionName |
| `.github/workflows/build-apk.yml` | release tag and notes (hard-coded `v2.0.0`) |
| `app/src/main/AndroidManifest.xml` | `POST_NOTIFICATIONS`, alarm and boot permissions, receivers (bill reminders) |

### 8.6 Biggest risks and pitfalls (summary)
1. **Room migrations are hand-written, unvalidated by CI and non-destructive.** `exportSchema = false`, there are no migration tests, and no device is available. Any mismatch crashes on upgrade for existing users. Keep schemas simple and copy the `MIGRATION_2_3` SQL style exactly. Bump the version exactly once per release and coordinate a single `MIGRATION_7_8`.
2. **The onOpen repair runs on every DB open** (`AppDatabase.kt:589-593`). It deletes `TRANSFER` / `INTERNAL_TRANSFER` rows, rewrites categories, merges and **deletes** look-alike transactions (same |amount| + bank + merchant within up to 10 min), and resurrects default accounts while deleting "Others"/"Savings"/"Messages" accounts. Recurring, goal or transfer features must not depend on any of those. A "Savings" account for goals would be **deleted and remapped to Chase** on open, and `normalizeBankName("savings")` returns "Chase".
3. **Two dedup layers** (DB on insert and open, plus UI in `observeTransactionItems`) mean list counts differ from SQL aggregates. Budget and cash-flow numbers should come from SQL using `SPEND_WHERE` semantics, or both paths must use the same filter.
4. **Definition of spend**: `type = EXPENSE AND excludeFromSpending = 0 AND category != 'INTERNAL_TRANSFER'`. Income has no SQL aggregate yet (Analytics sums income in Kotlin from entities, using `!excludeFromSpending`).
5. **The currency preference is cosmetic.** Formatting uses the device locale and the sheets hard-code "£". Do not assume `currencySymbol` drives display.
6. **Period first-day-of-week is inconsistent** (locale vs Monday). Reports should take `firstDayOfWeek` as a parameter.
7. **`"EXACT"` rule bug** in `TransactionsViewModel` (rules silently ignored), the **no-op create-category** in `EditTransactionSheet`, and **`AddTransactionViewModel.evaluateCategory` reading uncollected `WhileSubscribed` StateFlows**. Be aware of these when building rules, review or auto-categorize features.
8. **Composable-embedded business logic** (`TransactionsFeed` month totals, carry-over balance, per-account balance) is untested and duplicated. A dashboard should extract it into a pure, tested function first.
9. **Experimental M3 APIs**: every M3 sheet, top bar, segmented button and DatePicker needs `@OptIn(ExperimentalMaterial3Api::class)`. Remember the 1.2-specific signatures (`SearchBar(active=…)`, `PullToRefreshContainer`, `SwipeToDismissBox`, `menuAnchor()` without args, `LinearProgressIndicator(progress = { })`).
10. **No background-work infrastructure** (no WorkManager, no AlarmManager usage, no `POST_NOTIFICATIONS` permission, no `Application` class). Recurring-bill reminders need new dependencies or manifest entries and runtime permission handling on API 33+. Detection of recurring bills from history can be pure Kotlin over `observeTransactions()` without any of that.
11. **No navigation library.** All navigation is booleans and an int in `SpendTrackerScreen`, so parallel UI work will conflict there. Consider having one owner add a small `AppDestination` sealed class/enum + `BackHandler` first, then let features plug in.
12. **`resetDemoData()` deletes all transactions** and resets the catalog. New tables (budgets, goals) are not cleared by it. Decide whether they should be (edit `SpendRepository.resetDemoData` / `CatalogDao.resetCatalog`).
