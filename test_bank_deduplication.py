"""
Unit and regression test suite for Bank Deduplication and Normalization in SpendTracker.
Tests:
  1. normalizeBankName(): case-insensitivity, whitespace trimming, aliases, fallbacks.
  2. resolveBankName(): dynamic bank discovery from package name, appLabel with subtitles/slogans, parsed sources.
  3. Database Account Consolidation: SQLite in-memory simulation of cleanupAndConsolidateAccounts,
     verifying transaction remapping (accountId, destinationAccountId, source), duplicate purging,
     orphan remapping, and unique constraint enforcement.
  4. UI Deduplication Logic: AccountPicker, Carousel aggregation, and Filter Chip deduplication.
"""

import sqlite3
import unittest
import re

def normalize_bank_name(raw, fallback="Others"):
    if not raw or not raw.strip():
        return fallback
    trimmed = re.sub(r"\s+", " ", raw.strip())
    lower = trimmed.lower()

    if lower in ("chase", "chase bank", "chase uk") or lower.startswith("chase ") or lower.endswith(" chase"):
        return "Chase"
    if lower in ("hsbc", "hsbc uk", "hsbc bank") or lower.startswith("hsbc ") or lower.endswith(" hsbc"):
        return "HSBC"
    if lower in ("monzo", "monzo bank") or lower.startswith("monzo ") or lower.endswith(" monzo"):
        return "Monzo"
    if lower in ("starling", "starling bank") or lower.startswith("starling ") or lower.endswith(" starling"):
        return "Starling"
    if lower in ("revolut", "revolut bank") or lower.startswith("revolut ") or lower.endswith(" revolut"):
        return "Revolut"
    if lower in ("barclays", "barclay", "barclaycard") or lower.startswith("barclays ") or lower.startswith("barclay ") or lower.endswith(" barclays"):
        return "Barclays"
    if lower in ("santander", "santander uk") or lower.startswith("santander ") or lower.endswith(" santander"):
        return "Santander"
    if lower in ("natwest", "nat west") or lower.startswith("natwest ") or lower.startswith("nat west ") or lower.endswith(" natwest"):
        return "NatWest"
    if lower in ("lloyds", "lloyds bank") or lower.startswith("lloyds ") or lower.endswith(" lloyds"):
        return "Lloyds"
    if lower in ("halifax", "halifax bank") or lower.startswith("halifax ") or lower.endswith(" halifax"):
        return "Halifax"
    if lower in ("nationwide", "nationwide building society") or lower.startswith("nationwide ") or lower.endswith(" nationwide"):
        return "Nationwide"
    if lower == "paypal" or lower.startswith("paypal ") or lower.endswith(" paypal"):
        return "PayPal"
    if lower in ("google pay", "google wallet", "gpay") or lower.startswith("google pay") or lower.startswith("google wallet") or lower.startswith("gpay"):
        return "Google Pay"
    if lower == "apple pay" or lower.startswith("apple pay") or lower.endswith(" apple pay"):
        return "Apple Pay"
    if lower in ("samsung pay", "samsung wallet") or lower.startswith("samsung pay") or lower.startswith("samsung wallet"):
        return "Samsung Pay"
    if lower in ("amex", "american express", "americanexpress") or lower.startswith("amex ") or lower.startswith("american express"):
        return "Amex"
    if lower in ("tsb", "tsb bank") or lower.startswith("tsb "):
        return "TSB"
    if lower in ("rbs", "royal bank of scotland") or lower.startswith("rbs "):
        return "RBS"
    if lower in ("first direct", "firstdirect") or lower.startswith("first direct") or lower.startswith("firstdirect"):
        return "First Direct"
    if lower in ("virgin money",) or lower.startswith("virgin money"):
        return "Virgin Money"
    if lower in ("metro bank", "metro") or lower.startswith("metro bank") or lower.startswith("metro "):
        return "Metro Bank"
    if lower in ("kroo", "kroo bank") or lower.startswith("kroo "):
        return "Kroo"
    if lower in ("savings", "saving") or lower.startswith("savings ") or lower.startswith("saving "):
        return "Savings"
    if lower in ("credit card", "creditcard") or lower.startswith("credit card") or lower.startswith("creditcard"):
        return "Credit Card"
    if lower in ("others", "other", "card payment", "bank alert", "unknown", "bank", "banking", "mobile banking", "mobile", "app") or lower.startswith("other "):
        return "Others"

    # Title-casing fallback
    return " ".join(w.capitalize() for w in trimmed.split(" "))


def resolve_bank_name(package_name, parsed_source, app_label=None):
    norm_parsed = normalize_bank_name(parsed_source)
    if norm_parsed != "Others" and norm_parsed.strip():
        return norm_parsed

    if app_label and app_label.strip():
        label_lower = app_label.strip().lower()
        if "chase" in label_lower:
            return "Chase"
        if "hsbc" in label_lower:
            return "HSBC"
        if "monzo" in label_lower:
            return "Monzo"
        if "starling" in label_lower:
            return "Starling"
        if "revolut" in label_lower:
            return "Revolut"
        if "barclay" in label_lower:
            return "Barclays"
        if "santander" in label_lower:
            return "Santander"
        if "natwest" in label_lower or "nat west" in label_lower:
            return "NatWest"
        if "lloyds" in label_lower:
            return "Lloyds"
        if "halifax" in label_lower:
            return "Halifax"
        if "nationwide" in label_lower:
            return "Nationwide"
        if "paypal" in label_lower:
            return "PayPal"
        if "google" in label_lower or "gpay" in label_lower:
            return "Google Pay"
        if "apple" in label_lower:
            return "Apple Pay"
        if "samsung" in label_lower:
            return "Samsung Pay"
        if "amex" in label_lower or "american express" in label_lower:
            return "Amex"
        if "tsb" in label_lower:
            return "TSB"
        if "rbs" in label_lower or "royal bank" in label_lower:
            return "RBS"
        if "first direct" in label_lower or "firstdirect" in label_lower:
            return "First Direct"
        if "virgin money" in label_lower:
            return "Virgin Money"
        if "metro" in label_lower:
            return "Metro Bank"
        if "kroo" in label_lower:
            return "Kroo"

        clean = re.sub(r"(?i)\s*[:\-•|].*", "", app_label)
        clean = re.sub(r"(?i)\b(?:mobile banking|banking|mobile|uk|app)\b", "", clean).strip()
        if clean and clean.lower() not in ("bank", "banking"):
            norm = normalize_bank_name(clean)
            if norm != "Others" and norm.strip():
                return norm

    pkg_lower = package_name.lower()
    if "chase" in pkg_lower or "jpmorgan" in pkg_lower:
        return "Chase"
    if "hsbc" in pkg_lower:
        return "HSBC"
    if "monzo" in pkg_lower or "getmondo" in pkg_lower:
        return "Monzo"
    if "starling" in pkg_lower:
        return "Starling"
    if "revolut" in pkg_lower:
        return "Revolut"
    if "barclay" in pkg_lower:
        return "Barclays"
    if "santander" in pkg_lower:
        return "Santander"
    if "natwest" in pkg_lower:
        return "NatWest"
    if "lloyds" in pkg_lower:
        return "Lloyds"
    if "halifax" in pkg_lower:
        return "Halifax"
    if "nationwide" in pkg_lower:
        return "Nationwide"
    if "paypal" in pkg_lower:
        return "PayPal"
    if "amex" in pkg_lower or "americanexpress" in pkg_lower:
        return "Amex"
    if "tsb" in pkg_lower:
        return "TSB"
    if "rbs" in pkg_lower:
        return "RBS"
    if "firstdirect" in pkg_lower:
        return "First Direct"
    if "virginmoney" in pkg_lower:
        return "Virgin Money"
    if "metrobank" in pkg_lower:
        return "Metro Bank"
    if "kroo" in pkg_lower:
        return "Kroo"
    if "walletnfcrel" in pkg_lower or "wallet" in pkg_lower or "paisa" in pkg_lower:
        return "Google Pay"
    if "samsung" in pkg_lower and "pay" in pkg_lower:
        return "Samsung Pay"

    parts = [p for p in package_name.split(".") if len(p) > 2 and p not in ("com", "org", "net", "android", "uk", "co", "app", "mobile", "banking")]
    if parts:
        norm = normalize_bank_name(parts[-1])
        if norm.lower() in ("bank", "banking"):
            return "Others"
        return norm
    return "Others"


def cleanup_and_consolidate_accounts_db(conn):
    """Mirror of AppDatabase.cleanupAndConsolidateAccounts"""
    cur = conn.cursor()
    # 1. Read all accounts
    cur.execute("SELECT id, name FROM accounts ORDER BY id ASC")
    all_accounts = [(row[0], row[1], normalize_bank_name(row[1])) for row in cur.fetchall()]

    # 2. Group by canonical normalized name
    grouped = {}
    for acc_id, raw_name, norm_name in all_accounts:
        grouped.setdefault(norm_name.lower(), []).append((acc_id, raw_name, norm_name))

    for norm_lower, group in grouped.items():
        canonical_id, _, canonical_name = min(group, key=lambda x: x[0])
        duplicates = [x for x in group if x[0] != canonical_id]
        if duplicates:
            dup_ids = [str(x[0]) for x in duplicates]
            dup_ids_str = ",".join(dup_ids)
            cur.execute(f"UPDATE transactions SET accountId = {canonical_id} WHERE accountId IN ({dup_ids_str})")
            cur.execute(f"UPDATE transactions SET destinationAccountId = {canonical_id} WHERE destinationAccountId IN ({dup_ids_str})")
            cur.execute(f"DELETE FROM accounts WHERE id IN ({dup_ids_str})")

        cur.execute("UPDATE accounts SET name = ? WHERE id = ?", (canonical_name, canonical_id))

    # 3. Re-link orphan transactions having matching or recognizable source name
    cur.execute("SELECT id, name FROM accounts ORDER BY id ASC")
    remaining_accounts = list(cur.fetchall())

    cur.execute("SELECT id, source FROM transactions WHERE accountId IS NULL OR accountId NOT IN (SELECT id FROM accounts)")
    orphan_txs = list(cur.fetchall())
    for tx_id, raw_source in orphan_txs:
        norm = normalize_bank_name(raw_source)
        matched = next((a for a in remaining_accounts if a[1].lower() == norm.lower()), None)
        if matched:
            cur.execute("UPDATE transactions SET accountId = ?, source = ? WHERE id = ?", (matched[0], matched[1], tx_id))
        else:
            cur.execute("INSERT OR IGNORE INTO accounts(name) VALUES (?)", (norm,))
            cur.execute("SELECT id, name FROM accounts WHERE LOWER(TRIM(name)) = LOWER(TRIM(?)) LIMIT 1", (norm,))
            new_acc = cur.fetchone()
            if new_acc:
                cur.execute("UPDATE transactions SET accountId = ?, source = ? WHERE id = ?", (new_acc[0], new_acc[1], tx_id))
                remaining_accounts.append(new_acc)

    # Clean up any invalid destinationAccountId
    cur.execute("UPDATE transactions SET destinationAccountId = NULL WHERE destinationAccountId IS NOT NULL AND destinationAccountId NOT IN (SELECT id FROM accounts)")

    # 4. Remap transactions.source to accounts.name
    cur.execute("""
        UPDATE transactions
        SET source = (SELECT name FROM accounts WHERE accounts.id = transactions.accountId)
        WHERE accountId IS NOT NULL AND accountId IN (SELECT id FROM accounts)
    """)

    # 5. Core default accounts
    for b in ("Chase", "HSBC", "Savings", "Credit Card"):
        cur.execute("SELECT COUNT(*) FROM accounts WHERE LOWER(TRIM(name)) = ?", (b.lower(),))
        if cur.fetchone()[0] == 0:
            cur.execute("INSERT OR IGNORE INTO accounts(name) VALUES (?)", (b,))

    # 6. Ensure unique index on accounts(name COLLATE NOCASE)
    cur.execute("DROP INDEX IF EXISTS index_accounts_name")
    cur.execute("CREATE UNIQUE INDEX IF NOT EXISTS index_accounts_name ON accounts(name COLLATE NOCASE)")
    conn.commit()


class TestBankNormalization(unittest.TestCase):
    def test_chase_variations(self):
        for raw in ["Chase", "chase", "CHASE", "Chase Bank", "Chase UK", "  Chase  ", "chase uk", "JPMorgan Chase"]:
            self.assertEqual(normalize_bank_name(raw), "Chase")

    def test_hsbc_variations(self):
        for raw in ["HSBC", "hsbc", "Hsbc", "HSBC UK", "hsbc bank", "  hsbc  ", "HSBC Bank UK"]:
            self.assertEqual(normalize_bank_name(raw), "HSBC")

    def test_monzo_starling_revolut(self):
        self.assertEqual(normalize_bank_name("monzo"), "Monzo")
        self.assertEqual(normalize_bank_name("Monzo Bank"), "Monzo")
        self.assertEqual(normalize_bank_name("STARLING"), "Starling")
        self.assertEqual(normalize_bank_name("Starling Bank"), "Starling")
        self.assertEqual(normalize_bank_name("revolut"), "Revolut")
        self.assertEqual(normalize_bank_name("Revolut Bank"), "Revolut")

    def test_barclays_and_barclaycard(self):
        self.assertEqual(normalize_bank_name("barclays"), "Barclays")
        self.assertEqual(normalize_bank_name("Barclaycard"), "Barclays")
        self.assertEqual(normalize_bank_name("barclay card"), "Barclays")
        self.assertEqual(normalize_bank_name("Barclays Bank"), "Barclays")

    def test_natwest_santander_lloyds_halifax_nationwide(self):
        self.assertEqual(normalize_bank_name("natwest"), "NatWest")
        self.assertEqual(normalize_bank_name("Nat West"), "NatWest")
        self.assertEqual(normalize_bank_name("santander uk"), "Santander")
        self.assertEqual(normalize_bank_name("lloyds bank"), "Lloyds")
        self.assertEqual(normalize_bank_name("halifax bank"), "Halifax")
        self.assertEqual(normalize_bank_name("Nationwide Building Society"), "Nationwide")

    def test_digital_wallets_and_cards(self):
        self.assertEqual(normalize_bank_name("gpay"), "Google Pay")
        self.assertEqual(normalize_bank_name("Google Wallet"), "Google Pay")
        self.assertEqual(normalize_bank_name("apple pay"), "Apple Pay")
        self.assertEqual(normalize_bank_name("samsung wallet"), "Samsung Pay")
        self.assertEqual(normalize_bank_name("american express"), "Amex")
        self.assertEqual(normalize_bank_name("paypal"), "PayPal")

    def test_generic_fallbacks_to_others(self):
        for raw in ["Others", "other", "OTHER", "Card Payment", "Bank Alert", "Unknown", "bank", "banking", "mobile banking"]:
            self.assertEqual(normalize_bank_name(raw), "Others")

    def test_custom_user_bank_name(self):
        self.assertEqual(normalize_bank_name("oaknorth bank"), "Oaknorth Bank")
        self.assertEqual(normalize_bank_name("triodos"), "Triodos")


class TestDynamicBankDiscovery(unittest.TestCase):
    def test_app_label_with_slogans_and_subtitles(self):
        self.assertEqual(resolve_bank_name("com.monzo.android", "Card Payment", "Monzo - Bank & Budget"), "Monzo")
        self.assertEqual(resolve_bank_name("com.revolut.revolut", "Card Payment", "Revolut: Spend, Save, Invest"), "Revolut")
        self.assertEqual(resolve_bank_name("com.starlingbank.android", "Card Payment", "Starling Bank: Personal & Business"), "Starling")
        self.assertEqual(resolve_bank_name("com.barclays.mep.uk", "Card Payment", "Barclays Mobile Banking"), "Barclays")
        self.assertEqual(resolve_bank_name("com.chase.sig.android", "Card Payment", "Chase UK Mobile Banking"), "Chase")
        self.assertEqual(resolve_bank_name("uk.co.hsbc.hsbcukmobilebanking", "Card Payment", "HSBC UK"), "HSBC")

    def test_package_name_detection_when_no_label(self):
        self.assertEqual(resolve_bank_name("com.chase.sig.android", "Card Payment", None), "Chase")
        self.assertEqual(resolve_bank_name("co.uk.getmondo", "Card Payment", None), "Monzo")
        self.assertEqual(resolve_bank_name("uk.co.santander.santanderUK", "Card Payment", None), "Santander")
        self.assertEqual(resolve_bank_name("com.google.android.apps.walletnfcrel", "Card Payment", None), "Google Pay")
        self.assertEqual(resolve_bank_name("com.samsung.android.spay", "Card Payment", None), "Samsung Pay")

    def test_generic_package_name_fallback_to_others(self):
        self.assertEqual(resolve_bank_name("com.bank.banking", "Card Payment", None), "Others")
        self.assertEqual(resolve_bank_name("com.app.mobile.banking", "Others", "Banking"), "Others")


class TestDatabaseAccountConsolidation(unittest.TestCase):
    def setUp(self):
        self.conn = sqlite3.connect(":memory:")
        cur = self.conn.cursor()
        cur.execute("""
            CREATE TABLE accounts (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL
            )
        """)
        cur.execute("""
            CREATE TABLE transactions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                accountId INTEGER,
                destinationAccountId INTEGER,
                amount REAL,
                source TEXT
            )
        """)

    def tearDown(self):
        self.conn.close()

    def test_consolidates_duplicate_bank_sources(self):
        cur = self.conn.cursor()
        # Seed duplicate accounts with varying cases and aliases
        cur.executemany("INSERT INTO accounts (id, name) VALUES (?, ?)", [
            (1, "Chase"),
            (2, "chase"),
            (3, "Chase Bank"),
            (4, "Chase UK"),
            (5, "HSBC"),
            (6, "hsbc uk"),
            (7, "Barclays"),
            (8, "Barclaycard"),
            (9, "Others"),
            (10, "Other"),
            (11, "Card Payment"),
        ])

        # Seed transactions referencing duplicate IDs
        cur.executemany("INSERT INTO transactions (id, accountId, destinationAccountId, amount, source) VALUES (?, ?, ?, ?, ?)", [
            (101, 1, None, -50.0, "Chase"),
            (102, 2, None, -25.0, "chase"),
            (103, 3, None, -10.0, "Chase Bank"),
            (104, 4, 6, 100.0, "Chase UK"),     # transfer: Chase UK (4) -> hsbc uk (6)
            (105, 6, None, -15.0, "hsbc uk"),
            (106, 8, None, -80.0, "Barclaycard"),
            (107, 10, None, -5.0, "Other"),
            (108, None, None, -12.0, "Chase"),   # orphan transaction
        ])
        self.conn.commit()

        # Run consolidation routine
        cleanup_and_consolidate_accounts_db(self.conn)

        # 1. Accounts table check: exactly 1 Chase, 1 HSBC, 1 Barclays, 1 Others (+ default Savings & Credit Card)
        cur.execute("SELECT id, name FROM accounts ORDER BY id ASC")
        accounts = cur.fetchall()
        acc_names = [a[1] for a in accounts]
        self.assertEqual(acc_names.count("Chase"), 1)
        self.assertEqual(acc_names.count("HSBC"), 1)
        self.assertEqual(acc_names.count("Barclays"), 1)
        self.assertEqual(acc_names.count("Others"), 1)

        # Duplicate IDs (2, 3, 4, 6, 8, 10, 11) MUST BE DELETED
        acc_ids = [a[0] for a in accounts]
        for deleted_id in [2, 3, 4, 6, 8, 10, 11]:
            self.assertNotIn(deleted_id, acc_ids)

        # Canonical accounts retained with minimum IDs
        chase_canonical = [a for a in accounts if a[1] == "Chase"][0]
        self.assertEqual(chase_canonical[0], 1)
        hsbc_canonical = [a for a in accounts if a[1] == "HSBC"][0]
        self.assertEqual(hsbc_canonical[0], 5)
        barclays_canonical = [a for a in accounts if a[1] == "Barclays"][0]
        self.assertEqual(barclays_canonical[0], 7)
        others_canonical = [a for a in accounts if a[1] == "Others"][0]
        self.assertEqual(others_canonical[0], 9)

        # 2. Transactions remapping check
        cur.execute("SELECT id, accountId, destinationAccountId, source FROM transactions ORDER BY id ASC")
        txs = {row[0]: (row[1], row[2], row[3]) for row in cur.fetchall()}

        # Transactions 101, 102, 103, 104 remapped to accountId=1 (Chase)
        self.assertEqual(txs[101][0], 1)
        self.assertEqual(txs[102][0], 1)
        self.assertEqual(txs[103][0], 1)
        self.assertEqual(txs[104][0], 1)
        # Transfer destinationAccountId remapped from 6 to 5 (HSBC)
        self.assertEqual(txs[104][1], 5)

        # Transaction 105 remapped from 6 to 5 (HSBC)
        self.assertEqual(txs[105][0], 5)

        # Transaction 106 remapped from 8 to 7 (Barclays)
        self.assertEqual(txs[106][0], 7)

        # Transaction 107 remapped from 10 to 9 (Others)
        self.assertEqual(txs[107][0], 9)

        # Orphan transaction 108 remapped to Chase (1)
        self.assertEqual(txs[108][0], 1)

        # Sources are all normalized to canonical account names
        for tx_id, (_, _, src) in txs.items():
            self.assertIn(src, ["Chase", "HSBC", "Barclays", "Others"])

        # 3. Unique index prevents re-insertion of duplicates case-insensitively
        with self.assertRaises(sqlite3.IntegrityError):
            cur.execute("INSERT INTO accounts (name) VALUES ('chase')")
        with self.assertRaises(sqlite3.IntegrityError):
            cur.execute("INSERT INTO accounts (name) VALUES ('Chase')")
        with self.assertRaises(sqlite3.IntegrityError):
            cur.execute("INSERT INTO accounts (name) VALUES ('CHASE')")

    def test_orphan_relinking_with_aliases_and_new_banks(self):
        cur = self.conn.cursor()
        cur.executemany("INSERT INTO accounts (id, name) VALUES (?, ?)", [
            (1, "Chase"),
            (2, "Barclays"),
        ])
        # Orphans: 201 has alias 'Chase UK', 202 has alias 'Barclaycard', 203 has new bank 'Monzo'
        # 204 has invalid destinationAccountId (999)
        cur.executemany("INSERT INTO transactions (id, accountId, destinationAccountId, amount, source) VALUES (?, ?, ?, ?, ?)", [
            (201, None, None, -10.0, "Chase UK"),
            (202, None, None, -20.0, "Barclaycard"),
            (203, None, None, -30.0, "Monzo"),
            (204, 1, 999, -40.0, "Chase"),
        ])
        self.conn.commit()

        cleanup_and_consolidate_accounts_db(self.conn)

        cur.execute("SELECT id, accountId, destinationAccountId, source FROM transactions ORDER BY id ASC")
        txs = {row[0]: (row[1], row[2], row[3]) for row in cur.fetchall()}

        # 201 should be remapped to Chase (1)
        self.assertEqual(txs[201][0], 1)
        self.assertEqual(txs[201][2], "Chase")

        # 202 should be remapped to Barclays (2)
        self.assertEqual(txs[202][0], 2)
        self.assertEqual(txs[202][2], "Barclays")

        # 203 should have created Monzo account and remapped to it
        cur.execute("SELECT id, name FROM accounts WHERE name = 'Monzo'")
        monzo_acc = cur.fetchone()
        self.assertIsNotNone(monzo_acc)
        self.assertEqual(txs[203][0], monzo_acc[0])
        self.assertEqual(txs[203][2], "Monzo")

        # 204 invalid destinationAccountId 999 should be cleared to NULL
        self.assertIsNone(txs[204][1])


class TestUiAccountDeduplication(unittest.TestCase):
    def test_picker_and_carousel_deduplication(self):
        raw_accounts = [
            {"id": 1, "name": "Chase"},
            {"id": 2, "name": "chase"},
            {"id": 3, "name": "Chase Bank"},
            {"id": 5, "name": "HSBC"},
            {"id": 6, "name": "hsbc uk"},
            {"id": 7, "name": "Others"},
            {"id": 8, "name": "Other"},
        ]

        # The UI mapping logic: map to normalizeBankName, distinctBy lowercase, sorted
        mapped = [dict(acc, name=normalize_bank_name(acc["name"])) for acc in raw_accounts]
        deduped = {}
        for acc in mapped:
            key = acc["name"].lower()
            if key not in deduped:
                deduped[key] = acc
        deduped_list = sorted(deduped.values(), key=lambda x: x["name"])

        names = [d["name"] for d in deduped_list]
        self.assertEqual(names, ["Chase", "HSBC", "Others"])

    def test_filter_chip_aggregation(self):
        transactions = [
            {"accountName": "Chase", "source": "Chase", "amount": 10},
            {"accountName": "Chase Bank", "source": "Chase Bank", "amount": 20},
            {"accountName": "chase uk", "source": "chase", "amount": 30},
            {"accountName": "HSBC", "source": "HSBC", "amount": 40},
            {"accountName": "hsbc uk", "source": "hsbc", "amount": 50},
        ]
        # Filter chip counts when aggregated by normalizeBankName
        chase_count = sum(
            1 for t in transactions
            if normalize_bank_name(t["accountName"]).lower() == "chase" or normalize_bank_name(t["source"]).lower() == "chase"
        )
        hsbc_count = sum(
            1 for t in transactions
            if normalize_bank_name(t["accountName"]).lower() == "hsbc" or normalize_bank_name(t["source"]).lower() == "hsbc"
        )
        self.assertEqual(chase_count, 3)
        self.assertEqual(hsbc_count, 2)

    def test_repository_raw_account_mapping_simulation(self):
        # Simulates SpendRepository.observeTransactionItems resolution
        raw_accounts = [
            {"id": 1, "name": "Chase"},
            {"id": 2, "name": "chase"},
            {"id": 3, "name": "HSBC"},
        ]
        raw_acc_map = {a["id"]: a for a in raw_accounts}
        dedup_accounts = []
        seen = set()
        for a in raw_accounts:
            norm = normalize_bank_name(a["name"])
            key = norm.lower()
            if key not in seen:
                seen.add(key)
                dedup_accounts.append({"id": a["id"], "name": norm})
        canonical_id_map = {a["id"]: a for a in dedup_accounts}
        canonical_name_map = {a["name"].lower(): a for a in dedup_accounts}

        tx = {"id": 10, "accountId": 2, "source": None} # accountId 2 is duplicate
        raw_acc = raw_acc_map.get(tx["accountId"])
        acc = canonical_id_map.get(tx["accountId"]) or (
            canonical_name_map.get(normalize_bank_name(raw_acc["name"]).lower()) if raw_acc else None
        )
        # Should resolve to canonical Chase (id=1)
        self.assertIsNotNone(acc)
        self.assertEqual(acc["id"], 1)
        self.assertEqual(acc["name"], "Chase")


if __name__ == "__main__":
    unittest.main()
