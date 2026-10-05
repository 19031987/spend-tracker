import re
import unittest

CATEGORY_RULES_OUT = {
    "Groceries": ["tesco", "sainsbury", "asda", "morrison", "aldi", "lidl", "waitrose", "m&s", "marks & spencer", "co-op", "grocery", "supermarket", "costco", "iceland", "ocado"],
    "Dining & Drinks": ["costa", "starbucks", "mcdonald", "kfc", "subway", "greggs", "nando", "deliveroo", "uber eats", "just eat", "caffe", "coffee", "restaurant", "pub", "bar", "pizza", "burger", "pret", "five guys"],
    "Transport & Fuel": ["uber", "tfl", "transport for london", "trainline", "national rail", "shell", "bp", "esso", "texaco", "petrol", "parking", "bolt", "railway", "ringgo"],
    "Bills & Subscriptions": ["netflix", "spotify", "disney", "prime", "amazon prime", "apple.com", "google storage", "youtube", "broadband", "bt", "virgin media", "ee", "o2", "vodafone", "three", "water", "british gas", "octopus", "edf", "e.on", "gym", "puregym"],
    "Shopping": ["amazon", "ebay", "argos", "boots", "currys", "john lewis", "zara", "h&m", "primark", "tk maxx", "next", "asos", "shein", "ikea", "apple store"],
    "Entertainment": ["steam", "playstation", "xbox", "nintendo", "cinema", "odeon", "vue", "cineworld", "ticketmaster"]
}

CATEGORY_RULES_IN = {
    "Income & Salary": ["salary", "payroll", "wages", "employer", "dividend", "earnings", "work"],
    "Refunds": ["refund", "returned", "chargeback", "reimbursement"],
    "Transfers In": ["sent you", "received from", "transfer from", "friends", "gift", "john", "dave", "sarah", "alex", "tom", "jane"],
    "Cashback & Rewards": ["cashback", "reward", "interest", "bonus"]
}

KNOWN_SOURCES = [
    "Chase", "Monzo", "Revolut", "Amex", "Barclays", "Apple Pay",
    "Google Pay", "Starling", "HSBC", "PayPal", "Santander", "NatWest", "Lloyds", "Halifax"
]

def detect_source(pkg: str, text: str) -> str:
    combined = f"{pkg} {text}".lower()
    if "chase" in combined: return "Chase"
    if "monzo" in combined or "mondo" in combined: return "Monzo"
    if "revolut" in combined: return "Revolut"
    if "amex" in combined or "americanexpress" in combined: return "Amex"
    if "barclays" in combined: return "Barclays"
    if "apple" in combined or "wallet" in combined: return "Apple Pay"
    if "google" in combined or "gpay" in combined: return "Google Pay"
    if "starling" in combined: return "Starling"
    if "hsbc" in combined: return "HSBC"
    if "paypal" in combined: return "PayPal"
    if "santander" in combined: return "Santander"
    if "natwest" in combined: return "NatWest"
    if "lloyds" in combined: return "Lloyds"
    if "halifax" in combined: return "Halifax"
    return "Card Payment"

def clean_merchant(raw: str, source: str = "") -> str:
    if not raw:
        return "Unknown Vendor"
    
    clean = raw.strip()

    # Strip source prefixes (e.g. "PayPal Dave" -> "Dave" or "HSBC Tesco" -> "Tesco")
    for s in KNOWN_SOURCES:
        clean = re.sub(rf"^(?:{s}\s*[:\-]?\s*)+", "", clean, flags=re.IGNORECASE).strip()
    
    # Strip prefixes like 'at ', 'to ', 'from '
    clean = re.sub(r"^(?:at|to|from|with\s+your\s+card\s+at|spent\s+at|payment\s+to|payment\s+from)\s+", "", clean, flags=re.IGNORECASE)
    
    # Strip common noise at end
    clean = re.sub(r"(?i)\s+on\s+\d{1,2}[/-]\d{1,2}(?:[/-]\d{2,4})?.*$", "", clean)
    clean = re.sub(r"(?i)\s+at\s+\d{1,2}:\d{2}.*$", "", clean)
    clean = re.sub(r"(?i)\s+(?:via contactless|using card.*|card ending \d+.*|was approved.*|ref:?.*)$", "", clean)
    clean = re.sub(r"(?i)\b(?:ltd|limited|uk)\b\.?", "", clean)
    clean = re.sub(r"[^a-zA-Z0-9 &'. -]", " ", clean)
    clean = re.sub(r"\s+", " ", clean).strip()

    # Known name mappings
    lower = clean.lower()
    if "tesco" in lower: return "Tesco Stores"
    if "sainsbury" in lower: return "Sainsbury's"
    if "asda" in lower: return "ASDA"
    if "costa" in lower: return "Costa Coffee"
    if "pret" in lower: return "Pret A Manger"
    if "starbucks" in lower: return "Starbucks"
    if "steam" in lower: return "Steam Games"
    if "netflix" in lower: return "Netflix"
    if "uber eats" in lower: return "Uber Eats"
    if "uber" in lower and "eats" not in lower: return "Uber"
    if "amazon" in lower: return "Amazon"
    if "shell" in lower: return "Shell Petrol"
    if "apple store" in lower: return "Apple Store"
    if "harrods" in lower: return "Harrods"

    # Title-case each word
    words = clean.split()
    if not words:
        return "Retailer"
    return " ".join([w.capitalize() for w in words])

def categorize(text_to_check: str, tx_type: str = "OUT") -> str:
    lower = text_to_check.lower()
    if tx_type == "IN":
        for cat, keywords in CATEGORY_RULES_IN.items():
            for kw in keywords:
                if len(kw) <= 3:
                    if re.search(r"\b" + re.escape(kw) + r"\b", lower):
                        return cat
                else:
                    if kw in lower:
                        return cat
        return "Money In"
    else:
        for cat, keywords in CATEGORY_RULES_OUT.items():
            for kw in keywords:
                if len(kw) <= 3:
                    if re.search(r"\b" + re.escape(kw) + r"\b", lower):
                        return cat
                else:
                    if kw in lower:
                        return cat
        return "General Spend"

# Money In patterns
MONEY_IN_PATTERNS = [
    # "You received £250.00 from John Smith" or "Payment of £50.00 received from Tom"
    re.compile(r"(?i)(?:you received|received|payment of|transfer of)\s*([£$€]|GBP)?\s*([0-9,]+\.[0-9]{2})\s*(?:GBP\s*)?from\s+([^.,\n]+)"),
    # "Dave sent you £40.00"
    re.compile(r"(?i)([^.,\n]+)\s+sent you\s*([£$€]|GBP)?\s*([0-9,]+\.[0-9]{2})"),
    # "Refund of £24.99 from Amazon" or "You received a refund of £12.50 from eBay"
    re.compile(r"(?i)(?:refund(?:\s+of)?|you received a refund of)\s*([£$€]|GBP)?\s*([0-9,]+\.[0-9]{2})\s*(?:GBP\s*)?from\s+([^.,\n]+)"),
    # "Deposit of £1,500.00 from Employer Corp" or "Salary credit: £2,500.00 from TechCorp"
    re.compile(r"(?i)(?:deposit of|salary(?:\s+credit)?[:\s]+)\s*([£$€]|GBP)?\s*([0-9,]+\.[0-9]{2})\s*(?:GBP\s*)?(?:from\s+([^.,\n]+))?"),
    # "£50.00 received from Michael"
    re.compile(r"(?i)([£$€]|GBP)\s*([0-9,]+\.[0-9]{2})\s*(?:received from|deposited by)\s*([^.,\n]+)")
]

# Money Out patterns
MONEY_OUT_PATTERNS = [
    # "You spent £18.50 with your card at ASDA" or "Card ending 8219 spent £4.20 at Costa"
    re.compile(r"(?i)(?:you spent|card ending \d+ spent|payment of|paid|a charge of|transaction of|spent)\s*([£$€]|GBP)?\s*([0-9,]+\.[0-9]{2})\s*(?:GBP\s*)?(?:at|to|with your card at)?\s*([^.,\n]+)"),
    # "Approved: £34.20 at Waitrose" or "You paid £29.99 to Steam Games"
    re.compile(r"(?i)(?:approved:\s*)?([£$€]|GBP)?\s*([0-9,]+\.[0-9]{2})\s*(?:GBP\s*)?(?:spent at|paid to|at|to)\s*([^.,\n]+)"),
    # "You sent £30.00 to Landlord"
    re.compile(r"(?i)(?:you sent|transfer to|direct debit to)\s*([£$€]|GBP)?\s*([0-9,]+\.[0-9]{2})\s*(?:GBP\s*)?to\s+([^.,\n]+)"),
    # "Payment to Netflix: £8.99"
    re.compile(r"(?i)(?:payment to|paid)\s+([^.,\n:]+)[:\s]+([£$€]|GBP)?\s*([0-9,]+\.[0-9]{2})"),
    # "£12.50 spent at TESCO"
    re.compile(r"(?i)([£$€]|GBP)\s*([0-9,]+\.[0-9]{2})\s*(?:spent at|paid to|at|to)\s*([^.,\n]+)")
]

def parse_spend(pkg: str, title: str, text: str):
    full = f"{title or ''} {text or ''}".strip()
    if not full:
        return None
        
    source = detect_source(pkg, full)

    # 1. Check Money In patterns first
    for pattern in MONEY_IN_PATTERNS:
        m = pattern.search(full)
        if m:
            groups = m.groups()
            curr = "£"
            amount = 0.0
            merchant_raw = "Unknown Sender"
            
            if len(groups) == 3:
                if any(c in str(groups[0]) for c in ["£", "$", "€", "GBP"]) or str(groups[1]).replace(".", "", 1).isdigit():
                    curr = groups[0] or "£"
                    amount = float(groups[1].replace(",", ""))
                    merchant_raw = groups[2] or "Income Sender"
                else:
                    merchant_raw = groups[0]
                    curr = groups[1] or "£"
                    amount = float(groups[2].replace(",", ""))
            elif len(groups) == 2:
                curr = groups[0] or "£"
                amount = float(groups[1].replace(",", ""))
                merchant_raw = "Direct Deposit"

            clean = clean_merchant(merchant_raw, source)
            category = categorize(f"{clean} {full}", "IN")
            return {
                "type": "IN",
                "source": source,
                "amount": amount,
                "currency": "£" if "GBP" in str(curr) else (curr or "£"),
                "merchant": clean,
                "category": category
            }

    # 2. Check Money Out patterns
    for pattern in MONEY_OUT_PATTERNS:
        m = pattern.search(full)
        if m:
            groups = m.groups()
            curr = "£"
            amount = 0.0
            merchant_raw = "Unknown Vendor"

            if len(groups) == 3:
                if str(groups[1]).replace(".", "", 1).isdigit() or (groups[0] and any(c in str(groups[0]) for c in ["£", "$", "€", "GBP"])):
                    curr = groups[0] or "£"
                    amount = float(groups[1].replace(",", ""))
                    merchant_raw = groups[2] or "Retailer"
                else:
                    merchant_raw = groups[0]
                    curr = groups[1] or "£"
                    amount = float(groups[2].replace(",", ""))

            clean = clean_merchant(merchant_raw, source)
            category = categorize(f"{clean} {full}", "OUT")
            return {
                "type": "OUT",
                "source": source,
                "amount": amount,
                "currency": "£" if "GBP" in str(curr) else (curr or "£"),
                "merchant": clean,
                "category": category
            }

    # 3. Fallback: check if notification indicates received/credit vs spent
    fallback_amt = re.search(r"([£$€]|GBP)?\s*([0-9,]+\.[0-9]{2})", full)
    if fallback_amt:
        curr = fallback_amt.group(1) or "£"
        amt = float(fallback_amt.group(2).replace(",", ""))
        is_in = any(w in full.lower() for w in ["received", "sent you", "deposit", "salary", "refund", "credit", "+"])
        tx_type = "IN" if is_in else "OUT"
        clean = f"{source} {'Credit' if is_in else 'Payment'}"
        cat = "Money In" if is_in else "General Spend"
        return {
            "type": tx_type,
            "source": source,
            "amount": amt,
            "currency": "£" if "GBP" in curr else curr,
            "merchant": clean,
            "category": cat
        }

    return None


class TestSpendParser(unittest.TestCase):

    # --- Money Out Tests ---
    def test_chase_asda_notification(self):
        res = parse_spend(
            "com.jpmorgan.chase.uk",
            "Chase UK",
            "You spent £18.50 with your card at ASDA"
        )
        self.assertIsNotNone(res)
        self.assertEqual(res["type"], "OUT")
        self.assertEqual(res["source"], "Chase")
        self.assertEqual(res["amount"], 18.50)
        self.assertIn("ASDA", res["merchant"])
        self.assertEqual(res["category"], "Groceries")

    def test_monzo_pret_notification(self):
        res = parse_spend(
            "co.uk.getmondo",
            "Monzo",
            "You spent £12.40 at Pret A Manger"
        )
        self.assertIsNotNone(res)
        self.assertEqual(res["type"], "OUT")
        self.assertEqual(res["source"], "Monzo")
        self.assertEqual(res["amount"], 12.40)
        self.assertEqual(res["category"], "Dining & Drinks")

    def test_revolut_apple_notification(self):
        res = parse_spend(
            "com.revolut.revolut",
            "Revolut",
            "You spent £32.00 at Apple Store"
        )
        self.assertIsNotNone(res)
        self.assertEqual(res["type"], "OUT")
        self.assertEqual(res["source"], "Revolut")
        self.assertEqual(res["amount"], 32.00)
        self.assertEqual(res["category"], "Shopping")

    def test_amex_harrods_notification(self):
        res = parse_spend(
            "com.americanexpress.android.acctsvcs.uk",
            "Amex",
            "A charge of £85.00 at Harrods was approved"
        )
        self.assertIsNotNone(res)
        self.assertEqual(res["type"], "OUT")
        self.assertEqual(res["source"], "Amex")
        self.assertEqual(res["amount"], 85.00)
        self.assertEqual(res["merchant"], "Harrods")

    def test_hsbc_tesco_notification(self):
        res = parse_spend(
            "uk.co.hsbc.hsbcukmobilebanking",
            "HSBC Card Alert",
            "You spent £14.80 at TESCO STORES on 07/09"
        )
        self.assertIsNotNone(res)
        self.assertEqual(res["type"], "OUT")
        self.assertEqual(res["source"], "HSBC")
        self.assertEqual(res["amount"], 14.80)
        self.assertEqual(res["merchant"], "Tesco Stores")
        self.assertEqual(res["category"], "Groceries")

    def test_paypal_steam(self):
        res = parse_spend(
            "com.paypal.android.p2pmobile",
            "PayPal",
            "You paid £29.99 to Steam Games"
        )
        self.assertIsNotNone(res)
        self.assertEqual(res["type"], "OUT")
        self.assertEqual(res["source"], "PayPal")
        self.assertEqual(res["amount"], 29.99)
        self.assertEqual(res["category"], "Entertainment")

    def test_paypal_netflix(self):
        res = parse_spend(
            "com.paypal.android.p2pmobile",
            "PayPal Payment",
            "Payment of £8.99 to NETFLIX"
        )
        self.assertIsNotNone(res)
        self.assertEqual(res["type"], "OUT")
        self.assertEqual(res["source"], "PayPal")
        self.assertEqual(res["amount"], 8.99)
        self.assertEqual(res["category"], "Bills & Subscriptions")

    def test_paypal_uber_eats(self):
        res = parse_spend(
            "com.paypal.android.p2pmobile",
            "PayPal",
            "You paid £18.50 to Uber Eats"
        )
        self.assertIsNotNone(res)
        self.assertEqual(res["type"], "OUT")
        self.assertEqual(res["source"], "PayPal")
        self.assertEqual(res["amount"], 18.50)
        self.assertEqual(res["category"], "Dining & Drinks")

    # --- Money In (Positive / Inflow) Tests ---
    def test_hsbc_money_in_received(self):
        res = parse_spend(
            "uk.co.hsbc.hsbcukmobilebanking",
            "HSBC Credit Alert",
            "You received £250.00 from John Smith"
        )
        self.assertIsNotNone(res)
        self.assertEqual(res["type"], "IN")
        self.assertEqual(res["amount"], 250.00)
        self.assertEqual(res["merchant"], "John Smith")
        self.assertEqual(res["category"], "Transfers In")

    def test_paypal_sent_you_money_in(self):
        res = parse_spend(
            "com.paypal.android.p2pmobile",
            "PayPal",
            "Dave sent you £40.00"
        )
        self.assertIsNotNone(res)
        self.assertEqual(res["type"], "IN")
        self.assertEqual(res["amount"], 40.00)
        self.assertEqual(res["merchant"], "Dave")

    def test_refund_money_in(self):
        res = parse_spend(
            "uk.co.hsbc.hsbcukmobilebanking",
            "HSBC Alert",
            "Refund of £24.99 from Amazon"
        )
        self.assertIsNotNone(res)
        self.assertEqual(res["type"], "IN")
        self.assertEqual(res["amount"], 24.99)
        self.assertEqual(res["merchant"], "Amazon")
        self.assertEqual(res["category"], "Refunds")

    def test_salary_credit_money_in(self):
        res = parse_spend(
            "uk.co.hsbc.hsbcukmobilebanking",
            "Salary Alert",
            "Salary credit: £2,850.00 from Employer Corp"
        )
        self.assertIsNotNone(res)
        self.assertEqual(res["type"], "IN")
        self.assertEqual(res["amount"], 2850.00)
        self.assertEqual(res["merchant"], "Employer Corp")
        self.assertEqual(res["category"], "Income & Salary")

    def test_ignore_unrelated_notification(self):
        res = parse_spend(
            "com.whatsapp",
            "Alice",
            "Hey, are we still meeting for lunch?"
        )
        self.assertIsNone(res)


if __name__ == "__main__":
    unittest.main()
