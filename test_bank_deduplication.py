"""
Unit and regression test suite for Bank Deduplication and Normalization in SpendTracker.
Tests:
  1. normalizeBankName(): case-insensitivity, whitespace trimming, aliases, fallbacks.
  2. resolveBankName(): dynamic bank discovery from package name, appLabel with subtitles/slogans, parsed sources.
  3. Database Account Consolidation: SQLite in-memory simulation of cleanupAndConsolidateAccounts,
     verifying transaction remapping (accountId, destinationAccountId, source), duplicate purging,
     orphan remapping, and unique constraint enforcement.
  4. UI Deduplication Logic: AccountPicker, Carousel aggregation, and Filter Chip deduplication.
  5. Transaction duplicate rules (mirror of dedupDecision & co. in data/TransactionDao.kt): same type,
     never transfers, one shared same-bank window, wallet (PayPal / Google Pay) + bank pairs keep the bank row.
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
    if lower in ("hsbc", "hsbc uk", "hsbc bank", "premier", "premier account", "premier bank", "advance", "advance account", "advance bank") or lower.startswith("hsbc ") or lower.endswith(" hsbc") or lower.startswith("premier ") or lower.endswith(" premier"):
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
    if lower in ("checking", "checking account", "current account") or lower.startswith("checking "):
        return "Chase"
    if lower in ("savings", "saving", "savings account") or lower.startswith("savings ") or lower.startswith("saving "):
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



# ---------------------------------------------------------------------------------------------
# Duplicate rules: 1:1 mirror of the top-level functions in data/TransactionDao.kt
# (dedupDecision, pickDedupMatch, mergedDedupMerchant, mergedDedupCategory, dedupeRows).
# isMerchantDuplicate's SpendParser.cleanMerchantName fallback is reduced to a case-insensitive compare.
# ---------------------------------------------------------------------------------------------

DEDUP_WINDOW_MILLIS = 180_000
WALLET_DEDUP_WINDOW_MILLIS = 300_000
MAX_DEDUP_WINDOW_MILLIS = 300_000
WALLET_SOURCES = {"PayPal", "Google Pay", "Apple Pay", "Samsung Pay"}

_GENERIC_MERCHANTS = {
    "card payment", "payment", "unknown", "unknown vendor", "transaction", "money in", "money out",
    "hsbc payment", "chase payment", "premier payment", "hsbc", "premier", "chase",
}

WALLET_MERCHANT_PREFIX = re.compile(
    r"^\s*(?:paypal|pp(?=\s*\*)|google\s*pay|google|gpay|apple\s*pay|samsung\s*pay)\b\s*[*:\-]?\s*",
    re.IGNORECASE,
)


def is_unknown_or_generic_merchant(m, source_name=None):
    if m is None or not m.strip():
        return True
    t = m.strip().lower()
    if t in _GENERIC_MERCHANTS:
        return True
    return bool(source_name and source_name.strip() and t == source_name.strip().lower())


def is_premier_or_hsbc_bank(bank):
    return bool(bank and bank.strip()) and normalize_bank_name(bank).lower() == "hsbc"


def is_same_or_equivalent_bank(a, b):
    if not a or not a.strip() or not b or not b.strip():
        return False
    if a.strip().lower() == b.strip().lower():
        return True
    if is_premier_or_hsbc_bank(a) and is_premier_or_hsbc_bank(b):
        return True
    return normalize_bank_name(a).lower() == normalize_bank_name(b).lower()


def is_wallet_source(source):
    return bool(source and source.strip()) and normalize_bank_name(source) in WALLET_SOURCES


def resolve_preferred_bank_source(existing_source, new_source):
    if is_premier_or_hsbc_bank(existing_source) or is_premier_or_hsbc_bank(new_source):
        return "HSBC"
    if is_wallet_source(new_source) and existing_source and existing_source.strip() and not is_wallet_source(existing_source):
        return normalize_bank_name(existing_source)
    if is_wallet_source(existing_source) and new_source and new_source.strip() and not is_wallet_source(new_source):
        return normalize_bank_name(new_source)
    return normalize_bank_name(new_source if new_source is not None else existing_source)


def is_merchant_duplicate(existing, new, source_name=None):
    if is_unknown_or_generic_merchant(existing, source_name) or is_unknown_or_generic_merchant(new, source_name):
        return True
    return existing.strip().lower() == new.strip().lower()


def strip_wallet_merchant_prefix(merchant):
    if merchant is None:
        return ""
    return WALLET_MERCHANT_PREFIX.sub("", merchant, count=1).strip()


def merchant_names_wallet(merchant):
    return bool(merchant and merchant.strip()) and WALLET_MERCHANT_PREFIX.search(merchant) is not None


def wallet_merchant_key(merchant):
    return re.sub(r"[^a-z0-9]+", " ", strip_wallet_merchant_prefix(merchant).lower()).strip()


def _is_generic_for_pairing(merchant, source):
    if is_unknown_or_generic_merchant(merchant, source):
        return True
    stripped = strip_wallet_merchant_prefix(merchant)
    return merchant_names_wallet(merchant) and (not stripped or is_unknown_or_generic_merchant(stripped, source))


def is_wallet_merchant_compatible(bank_merchant, bank_source, wallet_merchant, wallet_source):
    if _is_generic_for_pairing(bank_merchant, bank_source) or _is_generic_for_pairing(wallet_merchant, wallet_source):
        return True
    b = wallet_merchant_key(bank_merchant)
    w = wallet_merchant_key(wallet_merchant)
    if not b or not w:
        return True
    if b == w or b.startswith(w) or w.startswith(b):
        return True
    b_first = b.split(" ")[0]
    w_first = w.split(" ")[0]
    return len(b_first) >= 3 and b_first == w_first


def dedup_key(amount, type_, source, merchant, timestamp, account_id=None, is_transfer=False):
    """Mirror of DedupKey (type_ is 'EXPENSE' / 'INCOME' / 'TRANSFER')."""
    return {"amount": amount, "type": type_, "source": source, "account_id": account_id,
            "merchant": merchant, "timestamp": timestamp, "is_transfer": is_transfer}


def dedup_decision(existing, incoming):
    """Returns (kind, survivor) with kind 'SAME_BANK' / 'WALLET_BANK' and survivor 'EXISTING' / 'INCOMING', or None."""
    if existing["is_transfer"] or incoming["is_transfer"]:
        return None
    if existing["type"] == "TRANSFER" or incoming["type"] == "TRANSFER":
        return None
    if existing["type"] != incoming["type"]:
        return None
    amount = abs(existing["amount"])
    if amount == 0 or amount != abs(incoming["amount"]):
        return None
    dt = abs(existing["timestamp"] - incoming["timestamp"])

    existing_wallet = is_wallet_source(existing["source"])
    incoming_wallet = is_wallet_source(incoming["source"])
    if existing_wallet != incoming_wallet:
        if dt > WALLET_DEDUP_WINDOW_MILLIS:
            return None
        bank = incoming if existing_wallet else existing
        wallet = existing if existing_wallet else incoming
        if normalize_bank_name(bank["source"]) == "Others":
            return None
        if not is_wallet_merchant_compatible(bank["merchant"], bank["source"], wallet["merchant"], wallet["source"]):
            return None
        return ("WALLET_BANK", "INCOMING" if existing_wallet else "EXISTING")

    if dt > DEDUP_WINDOW_MILLIS:
        return None
    same_account = existing["account_id"] is not None and existing["account_id"] == incoming["account_id"]
    if not same_account and not is_same_or_equivalent_bank(existing["source"], incoming["source"]):
        return None
    if not is_merchant_duplicate(existing["merchant"], incoming["merchant"],
                                 resolve_preferred_bank_source(existing["source"], incoming["source"])):
        return None
    return ("SAME_BANK", "EXISTING")


def pick_dedup_match(candidates, incoming):
    best = None
    for index, candidate in enumerate(candidates):
        decision = dedup_decision(candidate, incoming)
        if decision is None:
            continue
        both_known = (not is_unknown_or_generic_merchant(candidate["merchant"], candidate["source"])
                      and not is_unknown_or_generic_merchant(incoming["merchant"], incoming["source"]))
        tier = (0 if decision[0] == "SAME_BANK" else 2) + (0 if both_known else 1)
        rank = (tier, abs(candidate["timestamp"] - incoming["timestamp"]))
        if best is None or rank < best[2]:
            best = (index, decision, rank)
    return None if best is None else (best[0], best[1])


def merged_dedup_merchant(survivor, other, kind):
    survivor_generic = is_unknown_or_generic_merchant(survivor["merchant"], survivor["source"])
    if kind == "SAME_BANK":
        if not survivor_generic:
            return survivor["merchant"]
        if not is_unknown_or_generic_merchant(other["merchant"], other["source"]):
            return other["merchant"]
        return None
    other_usable = (not _is_generic_for_pairing(other["merchant"], other["source"])
                    and not merchant_names_wallet(other["merchant"]))
    if other_usable and (survivor_generic or merchant_names_wallet(survivor["merchant"])):
        return other["merchant"].strip()
    if survivor_generic:
        return None
    return survivor["merchant"]


def merged_dedup_category(survivor_category, other_category):
    def is_default(c):
        return not c or not c.strip() or c in ("OTHER_EXPENSE", "OTHER_INCOME", "General Spend")
    return other_category if is_default(survivor_category) and not is_default(other_category) else survivor_category


def dedupe_rows(rows, key, merge):
    kept, kept_keys = [], []
    for row in rows:
        incoming = key(row)
        hit = pick_dedup_match(kept_keys, incoming)
        if hit is None:
            kept.append(row)
            kept_keys.append(incoming)
            continue
        index, (kind, survivor) = hit
        merged = merge(kept[index], row, kind) if survivor == "EXISTING" else merge(row, kept[index], kind)
        kept[index] = merged
        kept_keys[index] = key(merged)
    return kept


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


class TestTransactionDeduplication(unittest.TestCase):
    def setUp(self):
        self.conn = sqlite3.connect(":memory:")
        self.cur = self.conn.cursor()
        self.cur.execute("""
            CREATE TABLE transactions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                accountId INTEGER,
                amount INTEGER,
                merchant TEXT,
                source TEXT,
                timestamp INTEGER,
                type TEXT
            )
        """)

    def tearDown(self):
        self.conn.close()

    def simulate_find_duplicate(self, abs_amount, source_name, account_id, timestamp, new_merchant,
                                tx_type="INCOME", window_millis=MAX_DEDUP_WINDOW_MILLIS):
        """Mirror of TransactionDao.findDedupCandidates (SQL) + findDedupMatch (dedup_decision ranking)."""
        self.cur.execute("""
            SELECT id, accountId, amount, merchant, source, timestamp, type
            FROM transactions
            WHERE ABS(amount) = ?
              AND type = ?
              AND type != 'TRANSFER'
              AND ABS(timestamp - ?) <= ?
            ORDER BY ABS(timestamp - ?) ASC
        """, (abs_amount, tx_type, timestamp, window_millis, timestamp))
        rows = self.cur.fetchall()
        keys = [dedup_key(abs(r[2]), r[6], r[4], r[3], r[5], account_id=r[1]) for r in rows]
        incoming = dedup_key(abs_amount, tx_type, source_name, new_merchant, timestamp, account_id=account_id)
        hit = pick_dedup_match(keys, incoming)
        return None if hit is None else rows[hit[0]]

    def test_dedup_same_amount_same_bank_different_merchants(self):
        # Two payments of £10 within 60s from different senders should both be inserted
        ts = 100000
        # 1. John sends £10
        dup1 = self.simulate_find_duplicate(1000, "HSBC", 1, ts, "John Smith")
        self.assertIsNone(dup1)
        self.cur.execute("INSERT INTO transactions(accountId, amount, merchant, source, timestamp, type) VALUES (1, 1000, 'John Smith', 'HSBC', ?, 'INCOME')", (ts,))

        # 2. Sarah sends £10 15 seconds later
        dup2 = self.simulate_find_duplicate(1000, "HSBC", 1, ts + 15000, "Sarah Jones")
        self.assertIsNone(dup2, "Different merchants must NOT be treated as duplicate")
        self.cur.execute("INSERT INTO transactions(accountId, amount, merchant, source, timestamp, type) VALUES (1, 1000, 'Sarah Jones', 'HSBC', ?, 'INCOME')", (ts + 15000,))

        self.cur.execute("SELECT COUNT(*) FROM transactions")
        self.assertEqual(self.cur.fetchone()[0], 2)

    def test_dedup_same_amount_same_bank_same_merchant(self):
        ts = 100000
        dup1 = self.simulate_find_duplicate(1000, "HSBC", 1, ts, "John Smith")
        self.assertIsNone(dup1)
        self.cur.execute("INSERT INTO transactions(accountId, amount, merchant, source, timestamp, type) VALUES (1, 1000, 'John Smith', 'HSBC', ?, 'INCOME')", (ts,))

        # Duplicate notification arrives 5 seconds later with identical merchant
        dup2 = self.simulate_find_duplicate(1000, "HSBC", 1, ts + 5000, "John Smith")
        self.assertIsNotNone(dup2, "Identical merchant within 60s must be detected as duplicate")

    def test_dedup_one_with_merchant_one_without_enriches(self):
        ts = 100000
        # SMS arrives first without merchant (or generic "Card Payment")
        dup1 = self.simulate_find_duplicate(1000, "HSBC", 1, ts, "Card Payment")
        self.assertIsNone(dup1)
        self.cur.execute("INSERT INTO transactions(accountId, amount, merchant, source, timestamp, type) VALUES (1, 1000, 'Card Payment', 'HSBC', ?, 'INCOME')", (ts,))

        # App notification arrives 2 seconds later with specific sender name "John Smith"
        dup2 = self.simulate_find_duplicate(1000, "HSBC", 1, ts + 2000, "John Smith")
        self.assertIsNotNone(dup2, "Generic merchant must match specific merchant for enrichment")
        # Enrich the record
        self.cur.execute("UPDATE transactions SET merchant = ? WHERE id = ?", ("John Smith", dup2[0]))

        self.cur.execute("SELECT COUNT(*), merchant FROM transactions")
        row = self.cur.fetchone()
        self.assertEqual(row[0], 1)
        self.assertEqual(row[1], "John Smith")

    def test_dedup_premier_and_hsbc_same_amount_same_vendor_saves_hsbc(self):
        ts = 100000
        # First row arrives as Premier
        self.cur.execute("INSERT INTO transactions(accountId, amount, merchant, source, timestamp, type) VALUES (5, -1550, 'Costa Coffee', 'Premier', ?, 'EXPENSE')", (ts,))
        # Second row arrives as HSBC with same amount and same vendor
        dup = self.simulate_find_duplicate(1550, "HSBC", 1, ts + 3000, "Costa Coffee", tx_type="EXPENSE")
        self.assertIsNotNone(dup, "Premier and HSBC with same amount and same vendor must be detected as duplicate")
        # Update existing record to save the HSBC one
        self.cur.execute("UPDATE transactions SET accountId = 1, source = 'HSBC' WHERE id = ?", (dup[0],))
        self.cur.execute("SELECT COUNT(*), source, merchant FROM transactions")
        row = self.cur.fetchone()
        self.assertEqual(row[0], 1)
        self.assertEqual(row[1], "HSBC")
        self.assertEqual(row[2], "Costa Coffee")

    def test_dedup_premier_and_hsbc_unknown_vendor_same_amount_saves_hsbc(self):
        ts = 100000
        # First row arrives as Premier with unknown vendor
        self.cur.execute("INSERT INTO transactions(accountId, amount, merchant, source, timestamp, type) VALUES (5, -2000, NULL, 'Premier', ?, 'EXPENSE')", (ts,))
        # Second row arrives as HSBC with same amount and unknown vendor
        dup = self.simulate_find_duplicate(2000, "HSBC", 1, ts + 4000, None, tx_type="EXPENSE")
        self.assertIsNotNone(dup, "Premier and HSBC unknown transaction with same amount within window must be duplicate")
        self.cur.execute("UPDATE transactions SET accountId = 1, source = 'HSBC' WHERE id = ?", (dup[0],))
        self.cur.execute("SELECT COUNT(*), source FROM transactions")
        row = self.cur.fetchone()
        self.assertEqual(row[0], 1)
        self.assertEqual(row[1], "HSBC")

    def test_dedup_prefers_exact_vendor_over_closer_unknown_candidate(self):
        ts = 100000
        # Row 1: Premier with Costa Coffee at t=100s
        self.cur.execute("INSERT INTO transactions(accountId, amount, merchant, source, timestamp, type) VALUES (5, -1550, 'Costa Coffee', 'Premier', ?, 'EXPENSE')", (ts,))
        # Row 2: HSBC with Unknown vendor at t=102s
        self.cur.execute("INSERT INTO transactions(accountId, amount, merchant, source, timestamp, type) VALUES (1, -1550, NULL, 'HSBC', ?, 'EXPENSE')", (ts + 2000,))
        # Incoming HSBC notification with Costa Coffee at t=103s should match Row 1 (Costa Coffee) first
        dup = self.simulate_find_duplicate(1550, "HSBC", 1, ts + 3000, "Costa Coffee", tx_type="EXPENSE")
        self.assertIsNotNone(dup)
        self.assertEqual(dup[3], "Costa Coffee")

    def test_unknown_transactions_different_amount_and_different_time_not_duplicate(self):
        ts = 100000
        # 1. Unknown transaction of £12.00 at t=100s
        dup1 = self.simulate_find_duplicate(1200, "HSBC", 1, ts, None, tx_type="EXPENSE")
        self.assertIsNone(dup1)
        self.cur.execute("INSERT INTO transactions(accountId, amount, merchant, source, timestamp, type) VALUES (1, -1200, NULL, 'HSBC', ?, 'EXPENSE')", (ts,))

        # 2. Unknown transaction of £35.00 at t=500s (different amount and different time)
        dup2 = self.simulate_find_duplicate(3500, "HSBC", 1, ts + 400000, None, tx_type="EXPENSE")
        self.assertIsNone(dup2, "Unknown transaction with different amount and different time must NOT be duplicate")
        self.cur.execute("INSERT INTO transactions(accountId, amount, merchant, source, timestamp, type) VALUES (1, -3500, NULL, 'HSBC', ?, 'EXPENSE')", (ts + 400000,))

        # 3. Unknown transaction of £12.00 at t=500s (same amount, different time outside window)
        dup3 = self.simulate_find_duplicate(1200, "HSBC", 1, ts + 400000, None, tx_type="EXPENSE")
        self.assertIsNone(dup3, "Unknown transaction at different time outside window must NOT be duplicate")
        self.cur.execute("INSERT INTO transactions(accountId, amount, merchant, source, timestamp, type) VALUES (1, -1200, NULL, 'HSBC', ?, 'EXPENSE')", (ts + 400000,))

        self.cur.execute("SELECT COUNT(*) FROM transactions")
        self.assertEqual(self.cur.fetchone()[0], 3)


class TestDedupRules(unittest.TestCase):
    """Mirror of app/src/test/java/com/spendtracker/app/data/DedupRulesTest.kt."""
    T0 = 1_700_000_000_000

    def k(self, amount, source, merchant, at=None, type_="EXPENSE", account_id=None, is_transfer=False):
        return dedup_key(amount, type_, source, merchant, self.T0 if at is None else at, account_id, is_transfer)

    def test_paypal_and_chase_same_purchase_bank_kept(self):
        paypal = self.k(3000, "PayPal", "Amazon")
        chase = self.k(3000, "Chase", "PAYPAL *AMAZON", at=self.T0 + 90_000)
        self.assertEqual(dedup_decision(paypal, chase), ("WALLET_BANK", "INCOMING"))
        self.assertEqual(dedup_decision(chase, paypal), ("WALLET_BANK", "EXISTING"))

    def test_google_pay_and_bank_same_purchase(self):
        bank = self.k(450, "Monzo", "PRET A MANGER", at=self.T0 + 5_000)
        self.assertEqual(dedup_decision(bank, self.k(450, "Google Pay", "Pret"))[0], "WALLET_BANK")
        self.assertEqual(dedup_decision(bank, self.k(450, "Google Wallet", None))[0], "WALLET_BANK")

    def test_wallet_merchant_enriches_bank(self):
        bank = self.k(1250, "Chase", "PAYPAL *UBER")
        paypal = self.k(1250, "PayPal", "Uber")
        self.assertEqual(merged_dedup_merchant(bank, paypal, "WALLET_BANK"), "Uber")
        self.assertEqual(merged_dedup_merchant(self.k(1250, "Chase", None), paypal, "WALLET_BANK"), "Uber")
        self.assertEqual(merged_dedup_merchant(self.k(1250, "Chase", "Uber Eats"), paypal, "WALLET_BANK"), "Uber Eats")
        self.assertEqual(merged_dedup_merchant(bank, self.k(1250, "PayPal", "PayPal"), "WALLET_BANK"), "PAYPAL *UBER")

    def test_unrelated_merchants_not_merged(self):
        self.assertIsNone(dedup_decision(self.k(3000, "Chase", "Tesco"), self.k(3000, "PayPal", "Amazon", at=self.T0 + 10_000)))

    def test_wallet_window(self):
        bank = self.k(3000, "Chase", None)
        self.assertIsNotNone(dedup_decision(bank, self.k(3000, "PayPal", "Amazon", at=self.T0 + WALLET_DEDUP_WINDOW_MILLIS)))
        self.assertIsNone(dedup_decision(bank, self.k(3000, "PayPal", "Amazon", at=self.T0 + WALLET_DEDUP_WINDOW_MILLIS + 1)))

    def test_same_wallet_different_merchants_never_merged(self):
        self.assertIsNone(dedup_decision(self.k(1000, "PayPal", "Uber"), self.k(1000, "PayPal", "Amazon", at=self.T0 + 5_000)))
        self.assertEqual(dedup_decision(self.k(1000, "PayPal", "Uber"), self.k(1000, "PayPal", "Uber", at=self.T0 + 5_000))[0], "SAME_BANK")

    def test_two_wallets_or_unknown_source_not_merged(self):
        self.assertIsNone(dedup_decision(self.k(1000, "PayPal", None), self.k(1000, "Google Pay", None, at=self.T0 + 1_000)))
        self.assertIsNone(dedup_decision(self.k(1000, "Others", None), self.k(1000, "PayPal", None, at=self.T0 + 1_000)))

    def test_preferred_source_is_bank(self):
        self.assertEqual(resolve_preferred_bank_source("Chase", "PayPal"), "Chase")
        self.assertEqual(resolve_preferred_bank_source("PayPal", "Chase"), "Chase")
        self.assertEqual(resolve_preferred_bank_source("Google Pay", "Premier"), "HSBC")
        self.assertEqual(resolve_preferred_bank_source("Premier", "HSBC"), "HSBC")

    def test_card_payment_and_money_received_never_merged(self):
        spend = self.k(2500, "HSBC", None, at=self.T0 + 10_000)
        received = self.k(2500, "HSBC", None, at=self.T0 + 40_000, type_="INCOME")
        self.assertIsNone(dedup_decision(spend, received))
        self.assertIsNone(dedup_decision(received, spend))

    def test_transfers_never_deduplicated(self):
        leg = self.k(5000, "HSBC", None, type_="TRANSFER")
        self.assertIsNone(dedup_decision(leg, self.k(5000, "HSBC", None, type_="TRANSFER", at=self.T0 + 1_000)))
        self.assertIsNone(dedup_decision(self.k(5000, "HSBC", None, is_transfer=True), self.k(5000, "HSBC", None, at=self.T0 + 1_000)))

    def test_hsbc_premier_shared_window(self):
        premier = self.k(1550, "Premier", None)
        self.assertEqual(dedup_decision(premier, self.k(1550, "HSBC", "Costa Coffee", at=self.T0 + 150_000))[0], "SAME_BANK")
        self.assertEqual(dedup_decision(premier, self.k(1550, "HSBC", None, at=self.T0 + DEDUP_WINDOW_MILLIS))[0], "SAME_BANK")
        self.assertIsNone(dedup_decision(premier, self.k(1550, "HSBC", None, at=self.T0 + DEDUP_WINDOW_MILLIS + 1)))

    def test_pick_prefers_same_bank_known_then_closest(self):
        incoming = self.k(1550, "HSBC", "Costa Coffee", at=self.T0 + 3_000)
        candidates = [
            self.k(1550, "HSBC", None, at=self.T0 + 2_000),
            self.k(1550, "Premier", "Costa Coffee", at=self.T0),
            self.k(1550, "PayPal", "Costa Coffee", at=self.T0 + 3_000),
        ]
        self.assertEqual(pick_dedup_match(candidates, incoming)[0], 1)
        self.assertIsNone(pick_dedup_match([self.k(1550, "HSBC", None, type_="INCOME")], incoming))

    def test_category_merge(self):
        self.assertEqual(merged_dedup_category("OTHER_EXPENSE", "SHOPPING"), "SHOPPING")
        self.assertEqual(merged_dedup_category("DINING", "SHOPPING"), "DINING")
        self.assertEqual(merged_dedup_category("DINING", "OTHER_EXPENSE"), "DINING")
        self.assertEqual(merged_dedup_category(None, "SHOPPING"), "SHOPPING")

    def test_view_dedup_counts_paypal_purchase_once(self):
        rows = [
            (3, self.k(3000, "Chase", "PAYPAL *AMAZON", at=self.T0 + 60_000)),
            (2, self.k(3000, "PayPal", "Amazon")),
            (1, self.k(500, "Chase", "Tesco", at=self.T0 - 10_000)),
        ]

        def merge(survivor, other, kind):
            k = dict(survivor[1])
            k["merchant"] = merged_dedup_merchant(survivor[1], other[1], kind)
            return (survivor[0], k)

        out = dedupe_rows(rows, key=lambda r: r[1], merge=merge)
        self.assertEqual([r[0] for r in out], [3, 1])
        self.assertEqual(out[0][1]["merchant"], "Amazon")
        self.assertEqual(sum(r[1]["amount"] for r in out), 3500)

    def test_view_dedup_keeps_bank_when_wallet_first(self):
        rows = [(2, self.k(3000, "PayPal", "Amazon", at=self.T0 + 60_000)), (1, self.k(3000, "Chase", None))]
        out = dedupe_rows(rows, key=lambda r: r[1], merge=lambda s, o, kind: s)
        self.assertEqual([r[0] for r in out], [1])

    def test_view_dedup_never_merges_expense_with_income_or_transfer(self):
        rows = [
            (1, self.k(2500, "HSBC", None)),
            (2, self.k(2500, "HSBC", None, at=self.T0 + 30_000, type_="INCOME")),
            (3, self.k(2500, "HSBC", None, at=self.T0 + 40_000, type_="TRANSFER")),
        ]
        self.assertEqual(len(dedupe_rows(rows, key=lambda r: r[1], merge=lambda s, o, kind: s)), 3)

    def test_wallet_merchant_helpers(self):
        self.assertEqual(strip_wallet_merchant_prefix("PAYPAL *UBER"), "UBER")
        self.assertEqual(strip_wallet_merchant_prefix("Google Pay"), "")
        self.assertEqual(strip_wallet_merchant_prefix("Tesco"), "Tesco")
        self.assertTrue(merchant_names_wallet("PayPal Payment"))
        self.assertFalse(merchant_names_wallet("Premier Inn"))
        self.assertTrue(is_wallet_merchant_compatible("PAYPAL *UBER TRIP", "Chase", "Uber", "PayPal"))
        self.assertFalse(is_wallet_merchant_compatible("Tesco", "Chase", "Amazon", "PayPal"))

    def test_insert_time_scenarios_sqlite(self):
        """End-to-end with the candidate SQL: PayPal + Chase stored once; spend + income both stored."""
        conn = sqlite3.connect(":memory:")
        cur = conn.cursor()
        cur.execute("CREATE TABLE transactions (id INTEGER PRIMARY KEY AUTOINCREMENT, accountId INTEGER, amount INTEGER, "
                    "merchant TEXT, source TEXT, timestamp INTEGER, type TEXT)")

        def find(abs_amount, source, account_id, ts, merchant, type_):
            cur.execute("SELECT id, accountId, amount, merchant, source, timestamp, type FROM transactions "
                        "WHERE ABS(amount) = ? AND type = ? AND type != 'TRANSFER' AND ABS(timestamp - ?) <= ? "
                        "ORDER BY ABS(timestamp - ?) ASC", (abs_amount, type_, ts, MAX_DEDUP_WINDOW_MILLIS, ts))
            rows = cur.fetchall()
            hit = pick_dedup_match([dedup_key(abs(r[2]), r[6], r[4], r[3], r[5], r[1]) for r in rows],
                                   dedup_key(abs_amount, type_, source, merchant, ts, account_id))
            return None if hit is None else rows[hit[0]]

        t = self.T0
        cur.execute("INSERT INTO transactions(accountId, amount, merchant, source, timestamp, type) VALUES (7, -3000, 'Amazon', 'PayPal', ?, 'EXPENSE')", (t,))
        self.assertIsNotNone(find(3000, "Chase", 1, t + 120_000, None, "EXPENSE"), "Chase alert must merge into the PayPal row")
        cur.execute("INSERT INTO transactions(accountId, amount, merchant, source, timestamp, type) VALUES (2, -2500, NULL, 'HSBC', ?, 'EXPENSE')", (t + 10_000,))
        self.assertIsNone(find(2500, "HSBC", 2, t + 40_000, None, "INCOME"), "Money received must not merge into a card payment")
        conn.close()


if __name__ == "__main__":
    unittest.main()
