"""
Python mirror of app/src/main/java/com/spendtracker/app/parser/SpendParser.kt.

The Android project cannot be compiled on every dev machine, so this file mirrors the
Kotlin parser 1:1 (same regexes, same precedence) and holds the regression tests for:
  * random / phantom amounts (dates, balances, OTP approvals, non-bank apps)
  * inflows (e.g. "£23.00 has been added") being recorded as spend
  * "purchase" being detected as the Chase bank
Keep both files in sync when changing parsing rules.
"""
import re
import unittest

TYPE_IN, TYPE_OUT = "IN", "OUT"
I = re.IGNORECASE

KNOWN_SOURCES = [
    "Chase", "Monzo", "Revolut", "Amex", "Barclays", "Apple Pay", "Google Pay", "Samsung Pay",
    "Starling", "HSBC", "PayPal", "Santander", "NatWest", "Lloyds", "Halifax", "Nationwide",
]

BANK_PACKAGE_HINTS = [
    ("jpmorgan", "Chase"), ("chase", "Chase"), ("getmondo", "Monzo"), ("monzo", "Monzo"),
    ("revolut", "Revolut"), ("americanexpress", "Amex"), ("amex", "Amex"),
    ("barclays", "Barclays"), ("barclaycard", "Barclays"), ("starling", "Starling"),
    ("hsbc", "HSBC"), ("paypal", "PayPal"), ("santander", "Santander"), ("natwest", "NatWest"),
    ("lloyds", "Lloyds"), ("halifax", "Halifax"), ("nationwide", "Nationwide"),
]
WALLET_PACKAGE_HINTS = ["walletnfcrel", "com.google.android.apps.wallet", "nbu.paisa",
                        "samsung.android.spay", "samsungpay", "samsung.android.samsungpay"]
FINTECH_PACKAGE_HINTS = ["bank", "curve", "transferwise", "kroo", "tsb", "metrobank", "firstdirect",
                         "virginmoney", "capitalone", "mbna", "moneybox", "zopa", "tide", "monese",
                         "vanquis", "klarna", "clearpay", "rbs", "ulster", "coop", "marcus", "venmo",
                         "squareup.cash"]
SMS_PACKAGES = ["com.google.android.apps.messaging", "com.samsung.android.messaging",
                "com.android.mms", "com.android.messaging", "com.oneplus.mms"]

BANK_CONTENT_WORDS = [(re.compile(r"\b(?:%s)\b" % w, I), n) for w, n in [
    ("chase", "Chase"), ("monzo", "Monzo"), ("revolut", "Revolut"),
    ("amex|american express", "Amex"), ("barclays|barclaycard", "Barclays"),
    ("starling", "Starling"), ("hsbc", "HSBC"), ("paypal", "PayPal"),
    ("santander", "Santander"), ("natwest", "NatWest"), ("lloyds", "Lloyds"),
    ("halifax", "Halifax"), ("nationwide", "Nationwide"),
]]
GOOGLE_PAY_WORDS = re.compile(r"\b(?:google pay|google wallet|gpay)\b", I)
APPLE_PAY_WORDS = re.compile(r"\bapple pay\b", I)
SAMSUNG_PAY_WORDS = re.compile(r"\bsamsung (?:pay|wallet)\b", I)

CUR_ALT = r"[£€$]|GBP|EUR|USD"
NUM = r"(?:[0-9]{1,3}(?:,[0-9]{3})+|[0-9]+)(?:\.[0-9]{1,2})?"
MONEY_PREFIX = re.compile(r"([+\-\u2212]?)(%s)\s?(%s)(?![0-9])" % (CUR_ALT, NUM), I)
MONEY_SUFFIX = re.compile(r"(?<![0-9.,])([+\-\u2212]?)(%s)\s?(GBP|EUR|USD)\b" % NUM, I)

BALANCE_PHRASES = [
    re.compile(r"\b(?:(?:available|new|current|remaining|account|card)\s+)?"
               r"(?:balance|bal|credit limit|available to spend|left to spend|spending limit)\b\.?"
               r"\s*(?:is|of|now|was|:|=|-)?\s*(?:now\s+)?[+\-\u2212]?(?:%s)\s?%s" % (CUR_ALT, NUM), I),
    re.compile(r"\bavailable(?:\s+funds)?\s*(?:is|of|:|=|-)?\s*(?:%s)\s?%s" % (CUR_ALT, NUM), I),
    re.compile(r"(?:%s)\s?%s\s*(?:available|remaining|balance|left to spend)\b" % (CUR_ALT, NUM), I),
]

IGNORE = re.compile(
    r"\b(?:approve|confirm|verify|authori[sz]e|passcode|one[- ]time|otp|security code|verification|"
    r"declined|unsuccessful|failed|insufficient|requested|request|requesting|statement|"
    r"minimum payment|payment due|is due|due on|due date|offer|offers|voucher|promo|promotion|"
    r"discount|win|chance to|earn up to|save up to|get up to|this week|this month|last week|"
    r"last month|so far|summary|in total|total spend|reminder|scheduled|upcoming|"
    r"will be taken|will be paid|will leave)\b", I)
PROMO = re.compile(r"[0-9]+\s?%\s?off\b", I)

STRONG_IN = re.compile(
    r"\b(?:received from|you(?:'ve|’ve| have)? received|sent you|refund(?:ed)?|salary|wages|payroll|"
    r"paid in|paid into|credited|deposit(?:ed)?|top(?:ped)?[ -]?up|added to your|has been added|"
    r"have been added|you(?:'ve|’ve| have)? added|added money|money in|reimburs(?:ed|ement)|"
    r"transfer from|reversal|reversed|incoming payment|incoming transfer)\b", I)
STRONG_OUT = re.compile(
    r"\b(?:you(?:'ve|’ve| have)? (?:spent|paid|sent|bought|made a payment)|spent|paid to|payment to|"
    r"direct debit|standing order|purchase[ds]?|charged|debited|withdrawal|withdrawn|withdrew|"
    r"card payment|sent to|transfer to)\b", I)
WEAK_IN = re.compile(r"\b(?:received|incoming|has arrived|arrived|cashback|interest|credit)\b(?!\s*card)", I)
WEAK_OUT = re.compile(
    r"\b(?:payment|paid|pay|transaction|approved|contactless|card ending|debit|subscription|bill|order|spend|charge)\b", I)

NAME = r"([A-Za-z0-9&'][^.,;:!?\n•*()|]{0,48}?)"
TERM = r"(?=\s+(?:on|via|using|with|ref|reference|card|was|has|is|have|and|for)\b|\s+at\s+[0-9]|\s+-\s|[.,;:!?\n•*()|]|\s*$)"
IN_SENT_YOU = re.compile(NAME + r"\s+(?:has\s+)?sent you\b", I)
IN_FROM = re.compile(r"\bfrom\s+" + NAME + TERM, I)
OUT_DD = re.compile(r"\b(?:direct debit|standing order|payment|transfer)\s+to\s+" + NAME + r"(?=\s+(?:of|for)\b)", I)
OUT_AT_TO = re.compile(r"\b(?:at|to)\s+" + NAME + TERM, I)

GENERIC_TITLE = re.compile(
    r"\b(?:alert|alerts|notification|payment|payments|purchase|transaction|activity|account|card|"
    r"received|money|spend|spending|spent|update|credit|debit|wallet|pay|paid|deposit|refund|"
    r"salary|transfer|bank|banking)\b", I)

CATEGORY_RULES_OUT = {
    "Groceries": ["tesco", "sainsbury", "asda", "morrison", "aldi", "lidl", "waitrose", "m&s", "marks & spencer", "co-op", "grocery", "supermarket", "costco", "iceland", "ocado"],
    "Dining & Drinks": ["costa", "starbucks", "mcdonald", "kfc", "subway", "greggs", "nando", "deliveroo", "uber eats", "just eat", "caffe", "coffee", "restaurant", "pub", "bar", "pizza", "burger", "pret", "five guys"],
    "Transport & Fuel": ["uber", "tfl", "transport for london", "trainline", "national rail", "shell", "bp", "esso", "texaco", "petrol", "parking", "bolt", "railway", "ringgo"],
    "Bills & Subscriptions": ["netflix", "spotify", "disney", "prime", "amazon prime", "apple.com", "google storage", "youtube", "broadband", "bt", "virgin media", "ee", "o2", "vodafone", "three", "water", "british gas", "octopus", "edf", "e.on", "gym", "puregym"],
    "Shopping": ["amazon", "ebay", "argos", "boots", "currys", "john lewis", "zara", "h&m", "primark", "tk maxx", "next", "asos", "shein", "ikea", "apple store"],
    "Entertainment": ["steam", "playstation", "xbox", "nintendo", "cinema", "odeon", "vue", "cineworld", "ticketmaster"],
}
CATEGORY_RULES_IN = {
    "Income & Salary": ["salary", "payroll", "wages", "employer", "dividend", "earnings", "work"],
    "Refunds": ["refund", "returned", "chargeback", "reimbursement"],
    "Transfers In": ["sent you", "received from", "transfer from", "friends", "gift", "john", "dave", "sarah", "alex", "tom", "jane"],
    "Cashback & Rewards": ["cashback", "reward", "interest", "bonus"],
    "Top-ups & Deposits": ["top up", "topped up", "top-up", "topup", "added", "deposit", "paid in"],
}


def detect_source(pkg: str, content: str) -> str:
    pkg = pkg.lower()
    for hint, name in BANK_PACKAGE_HINTS:
        if hint in pkg:
            return name
    hits = [(m.start(), n) for r, n in BANK_CONTENT_WORDS for m in [r.search(content)] if m]
    if hits:
        return min(hits)[1]
    if "walletnfcrel" in pkg or "nbu.paisa" in pkg or GOOGLE_PAY_WORDS.search(content):
        return "Google Pay"
    if ("samsung" in pkg and "pay" in pkg) or SAMSUNG_PAY_WORDS.search(content):
        return "Samsung Pay"
    if APPLE_PAY_WORDS.search(content):
        return "Apple Pay"
    return "Card Payment"


def classify_package(pkg: str) -> str:
    if not pkg:
        return "TRUSTED"
    if any(h in pkg for h, _ in BANK_PACKAGE_HINTS) or any(h in pkg for h in WALLET_PACKAGE_HINTS) \
            or any(h in pkg for h in FINTECH_PACKAGE_HINTS):
        return "FINANCE"
    if pkg in SMS_PACKAGES:
        return "SMS"
    return "UNTRUSTED"


def normalize_currency(raw: str) -> str:
    return {"GBP": "£", "EUR": "€", "USD": "$"}.get(raw.upper(), raw)


def find_first_money(text: str):
    found = []
    m = MONEY_PREFIX.search(text)
    if m:
        found.append((m.start(), m.group(1), normalize_currency(m.group(2)), float(m.group(3).replace(",", "")), m.end()))
    m = MONEY_SUFFIX.search(text)
    if m:
        found.append((m.start(), m.group(1), normalize_currency(m.group(3)), float(m.group(2).replace(",", "")), m.end()))
    return min(found) if found else None


def is_usable_name(c) -> bool:
    if c is None:
        return False
    c = c.strip()
    if len(c) < 2:
        return False
    low = c.lower()
    if low in ("you", "your") or low.startswith("your ") or low.startswith("you "):
        return False
    if re.fullmatch(r"[0-9:./ -]+", c):
        return False
    return True


def clean_merchant(raw: str, source: str = "") -> str:
    clean = raw.strip()
    for s in KNOWN_SOURCES:
        clean = re.sub(r"(?i)^%s\s*[:\-]?\s*" % s, "", clean, count=1).strip()
    clean = re.sub(r"(?i)^(?:at|to|from|with\s+your\s+card\s+at|spent\s+at|payment\s+to|payment\s+from)\s+", "", clean).strip()
    clean = re.sub(r"(?i)\s+on\s+\d{1,2}[/-]\d{1,2}(?:[/-]\d{2,4})?.*$", "", clean)
    clean = re.sub(r"(?i)\s+at\s+\d{1,2}:\d{2}.*$", "", clean)
    clean = re.sub(r"(?i)\s+(?:via contactless|using card.*|card ending \d+.*|was approved.*|ref:?.*)$", "", clean)
    clean = re.sub(r"(?i)\b(?:ltd|limited|uk)\b\.?", "", clean)
    clean = re.sub(r"[^a-zA-Z0-9 &'. -]", " ", clean)
    clean = re.sub(r"\s+", " ", clean).strip()
    low = clean.lower()
    for key, name in [("tesco", "Tesco Stores"), ("sainsbury", "Sainsbury's"), ("asda", "ASDA"),
                      ("costa", "Costa Coffee"), ("pret", "Pret A Manger"), ("starbucks", "Starbucks"),
                      ("steam", "Steam Games"), ("netflix", "Netflix"), ("uber eats", "Uber Eats")]:
        if key in low:
            return name
    if "uber" in low and "eats" not in low:
        return "Uber"
    for key, name in [("amazon", "Amazon"), ("shell", "Shell Petrol"), ("apple store", "Apple Store"),
                      ("harrods", "Harrods"), ("british gas", "British Gas")]:
        if key in low:
            return name
    words = [w for w in clean.split(" ") if w]
    if not words:
        return ("%s Account" % source) if source else "Retailer"
    return " ".join(w.lower()[:1].upper() + w.lower()[1:] for w in words)


def categorize(text: str, tx_type: str = TYPE_OUT) -> str:
    low = text.lower()
    rules = CATEGORY_RULES_IN if tx_type == TYPE_IN else CATEGORY_RULES_OUT
    for cat, kws in rules.items():
        for kw in kws:
            if len(kw) <= 3:
                if re.search(r"(?i)\b" + re.escape(kw) + r"\b", low):
                    return cat
            elif kw in low:
                return cat
    return "Money In" if tx_type == TYPE_IN else "General Spend"


def extract_merchant(tx_type, text, amount_end, title, source):
    if tx_type == TYPE_IN:
        m = IN_SENT_YOU.search(text)
        if m and is_usable_name(m.group(1)):
            return m.group(1).strip(), False
        for m in IN_FROM.finditer(text):
            if is_usable_name(m.group(1)):
                return m.group(1).strip(), False
    else:
        m = OUT_DD.search(text)
        if m and is_usable_name(m.group(1)):
            return m.group(1).strip(), False
        tail = text[amount_end:] if 0 <= amount_end <= len(text) else ""
        for scope in (tail, text):
            for m in OUT_AT_TO.finditer(scope):
                if is_usable_name(m.group(1)):
                    return m.group(1).strip(), False
    t = (title or "").strip()
    if t and not GENERIC_TITLE.search(t) and find_first_money(t) is None \
            and not any(r.search(t) for r, _ in BANK_CONTENT_WORDS) and is_usable_name(t):
        return t, False
    return ("%s Deposit" % source if tx_type == TYPE_IN else "%s Payment" % source), True


def parse_spend(pkg: str, title, text):
    pkg = (pkg or "").strip().lower()
    content = " ".join(p.strip() for p in (title, text) if p and p.strip())
    if not content:
        return None
    trust = classify_package(pkg)
    if trust == "UNTRUSTED":
        return None
    if IGNORE.search(content) or PROMO.search(content):
        return None
    scrubbed = content
    for r in BALANCE_PHRASES:
        scrubbed = r.sub(" ", scrubbed)
    money = find_first_money(scrubbed)
    if not money:
        return None
    _, sign, currency, amount, end = money
    if amount <= 0 or amount >= 1_000_000:
        return None
    if sign == "+":
        tx_type = TYPE_IN
    elif sign in ("-", "\u2212"):
        tx_type = TYPE_OUT
    elif STRONG_IN.search(scrubbed):
        tx_type = TYPE_IN
    elif STRONG_OUT.search(scrubbed):
        tx_type = TYPE_OUT
    elif WEAK_IN.search(scrubbed):
        tx_type = TYPE_IN
    elif WEAK_OUT.search(scrubbed):
        tx_type = TYPE_OUT
    elif trust == "SMS":
        return None
    else:
        tx_type = TYPE_OUT
    source = detect_source(pkg, content)
    raw, fallback = extract_merchant(tx_type, scrubbed, end, title, source)
    merchant = raw if fallback else clean_merchant(raw, source)
    return {"type": tx_type, "source": source, "amount": amount, "currency": currency,
            "merchant": merchant, "category": categorize("%s %s" % (merchant, content), tx_type)}


HSBC = "uk.co.hsbc.hsbcukmobilebanking"
PAYPAL = "com.paypal.android.p2pmobile"
CHASE = "com.jpmorgan.chase.uk"
GWALLET = "com.google.android.apps.walletnfcrel"


class TestSpendParser(unittest.TestCase):

    def check(self, res, tx_type, amount, merchant=None, category=None, source=None):
        self.assertIsNotNone(res)
        self.assertEqual(res["type"], tx_type, res)
        self.assertAlmostEqual(res["amount"], amount)
        if merchant is not None:
            self.assertEqual(res["merchant"], merchant, res)
        if category is not None:
            self.assertEqual(res["category"], category, res)
        if source is not None:
            self.assertEqual(res["source"], source, res)

    # --- Money Out ---
    def test_chase_asda(self):
        self.check(parse_spend(CHASE, "Chase UK", "You spent £18.50 with your card at ASDA"),
                   TYPE_OUT, 18.50, "ASDA", "Groceries", "Chase")

    def test_monzo_pret(self):
        self.check(parse_spend("co.uk.getmondo", "Monzo", "You spent £12.40 at Pret A Manger"),
                   TYPE_OUT, 12.40, "Pret A Manger", "Dining & Drinks", "Monzo")

    def test_revolut_apple_store(self):
        self.check(parse_spend("com.revolut.revolut", "Revolut", "You spent £32.00 at Apple Store"),
                   TYPE_OUT, 32.00, "Apple Store", "Shopping", "Revolut")

    def test_amex_harrods(self):
        self.check(parse_spend("com.americanexpress.android.acctsvcs.uk", "Amex", "A charge of £85.00 at Harrods was approved"),
                   TYPE_OUT, 85.00, "Harrods", source="Amex")

    def test_hsbc_tesco_with_date(self):
        self.check(parse_spend(HSBC, "HSBC Card Alert", "You spent £14.80 at TESCO STORES on 07/09"),
                   TYPE_OUT, 14.80, "Tesco Stores", "Groceries", "HSBC")

    def test_costa_card_ending(self):
        self.check(parse_spend(HSBC, "HSBC Spend Alert", "Card ending 8219 spent £4.20 at COSTA COFFEE"),
                   TYPE_OUT, 4.20, "Costa Coffee", "Dining & Drinks")

    def test_direct_debit(self):
        self.check(parse_spend(HSBC, "HSBC Direct Debit", "Direct debit to British Gas of £65.00"),
                   TYPE_OUT, 65.00, "British Gas", "Bills & Subscriptions")

    def test_paypal_steam(self):
        self.check(parse_spend(PAYPAL, "PayPal", "You paid £29.99 to Steam Games"),
                   TYPE_OUT, 29.99, "Steam Games", "Entertainment", "PayPal")

    def test_paypal_netflix(self):
        self.check(parse_spend(PAYPAL, "PayPal Payment", "Payment of £8.99 to NETFLIX"),
                   TYPE_OUT, 8.99, "Netflix", "Bills & Subscriptions")

    def test_payment_to_colon(self):
        self.check(parse_spend(HSBC, "HSBC", "Payment to Netflix: £8.99"), TYPE_OUT, 8.99, "Netflix")

    def test_google_wallet_title_is_merchant(self):
        self.check(parse_spend(GWALLET, "Tesco", "£4.00 with Visa ••1234"),
                   TYPE_OUT, 4.00, "Tesco Stores", "Groceries", "Google Pay")

    def test_google_wallet_paypal_card(self):
        self.check(parse_spend(GWALLET, "Pret A Manger", "£4.00 with PayPal"),
                   TYPE_OUT, 4.00, "Pret A Manger", source="PayPal")

    def test_credit_card_spend_is_out(self):
        # "credit" used to flip this to income
        self.check(parse_spend("com.barclays.android.barclaysmobilebanking", "Barclaycard",
                               "You spent £12.00 on your credit card at Boots"), TYPE_OUT, 12.00, "Boots")

    # --- Money In ---
    def test_received_from(self):
        self.check(parse_spend(HSBC, "HSBC Credit Alert", "You received £250.00 from John Smith"),
                   TYPE_IN, 250.00, "John Smith", "Transfers In")

    def test_sent_you(self):
        self.check(parse_spend(PAYPAL, "PayPal", "Dave sent you £40.00"), TYPE_IN, 40.00, "Dave")

    def test_refund(self):
        self.check(parse_spend(HSBC, "HSBC Alert", "Refund of £24.99 from Amazon"),
                   TYPE_IN, 24.99, "Amazon", "Refunds")

    def test_salary(self):
        self.check(parse_spend(HSBC, "Salary Alert", "Salary credit: £2,850.00 from Employer Corp"),
                   TYPE_IN, 2850.00, "Employer Corp", "Income & Salary")

    def test_payment_received_from(self):
        self.check(parse_spend(PAYPAL, "PayPal", "Payment of £50.00 received from Tom"), TYPE_IN, 50.00, "Tom")

    def test_chase_money_added_is_positive(self):
        # Regression: "adding £23.00" rendered as -£23.00
        self.check(parse_spend(CHASE, "Chase", "£23.00 has been added to your account"),
                   TYPE_IN, 23.00, category="Top-ups & Deposits", source="Chase")

    def test_you_added(self):
        self.check(parse_spend(CHASE, "Chase", "You've added £23.00 to your account"), TYPE_IN, 23.00)

    def test_topped_up(self):
        self.check(parse_spend("com.revolut.revolut", "Revolut", "You topped up £23.00 with Apple Pay"), TYPE_IN, 23.00)

    def test_paid_in(self):
        self.check(parse_spend(HSBC, "HSBC", "£23.00 paid in to your account"), TYPE_IN, 23.00)

    def test_explicit_plus_sign(self):
        self.check(parse_spend("com.starlingbank.android", "Starling", "+£23.00 Mum"), TYPE_IN, 23.00)

    # --- Phantom / random values ---
    def test_balance_not_captured(self):
        self.check(parse_spend(HSBC, "HSBC", "You spent £8.99 at Netflix. Available balance £1,650.99"),
                   TYPE_OUT, 8.99, "Netflix")

    def test_balance_only_ignored(self):
        self.assertIsNone(parse_spend(HSBC, "HSBC", "Your balance is now £1,650.99"))

    def test_date_not_amount(self):
        self.assertIsNone(parse_spend(HSBC, "HSBC", "Statement ready 05.10.26"))
        self.assertIsNone(parse_spend(HSBC, "HSBC", "Card update on 06.10 at 17.12"))

    def test_otp_approval_ignored(self):
        self.assertIsNone(parse_spend(HSBC, "HSBC", "Approve your payment of £1,650.99 to Google in the app"))

    def test_declined_ignored(self):
        self.assertIsNone(parse_spend(HSBC, "HSBC", "Payment of £5.18 at Apple was declined"))

    def test_request_ignored(self):
        self.assertIsNone(parse_spend(PAYPAL, "PayPal", "Dave requested £10.00 from you"))

    def test_weekly_summary_ignored(self):
        self.assertIsNone(parse_spend("co.uk.getmondo", "Monzo", "You spent £230.00 this week"))

    def test_promo_ignored(self):
        self.assertIsNone(parse_spend(PAYPAL, "PayPal", "Get 20% off when you spend £50.00"))

    def test_non_bank_app_ignored(self):
        self.assertIsNone(parse_spend("com.whatsapp", "Alice", "I paid £20.00 for dinner"))
        self.assertIsNone(parse_spend("com.google.android.gm", "Amazon", "Your order of £12.99 has shipped"))

    def test_unrelated_notification(self):
        self.assertIsNone(parse_spend("com.whatsapp", "Alice", "Hey, are we still meeting for lunch?"))

    def test_sms_needs_verb(self):
        self.assertIsNone(parse_spend("com.google.android.apps.messaging", "Bob", "£20.00"))
        self.check(parse_spend("com.google.android.apps.messaging", "HSBC", "You spent £20.00 at Tesco"), TYPE_OUT, 20.00)

    def test_amount_without_currency_ignored(self):
        self.assertIsNone(parse_spend(HSBC, "HSBC", "Card ending 1650.99 activity"))

    # --- Source detection ---
    def test_purchase_is_not_chase(self):
        self.assertEqual(detect_source(GWALLET, "Google Pay Purchase £4.00"), "Google Pay")

    def test_trusted_blank_package(self):
        self.check(parse_spend("", None, "Chase £23.00 has been added to your account"), TYPE_IN, 23.00, source="Chase")


if __name__ == "__main__":
    unittest.main()
