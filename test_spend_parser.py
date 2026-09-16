import re
import unittest

CATEGORY_RULES = {
    "Groceries": ["tesco", "sainsbury", "asda", "morrison", "aldi", "lidl", "waitrose", "m&s", "marks & spencer", "co-op", "grocery", "supermarket", "costco"],
    "Dining & Drinks": ["costa", "starbucks", "mcdonald", "kfc", "subway", "greggs", "nando", "deliveroo", "uber eats", "just eat", "caffe", "coffee", "restaurant", "pub", "bar", "pizza", "burger"],
    "Transport & Fuel": ["uber", "tfl", "transport for london", "trainline", "national rail", "shell", "bp", "esso", "texaco", "petrol", "parking", "bolt", "railway"],
    "Bills & Subscriptions": ["netflix", "spotify", "disney", "prime", "amazon prime", "apple.com", "google storage", "youtube", "broadband", "bt", "virgin media", "ee", "o2", "vodafone", "three", "water", "british gas", "octopus", "edf", "e.on", "gym", "puregym"],
    "Shopping": ["amazon", "ebay", "argos", "boots", "currys", "john lewis", "zara", "h&m", "primark", "tk maxx", "next", "asos", "shein", "ikea"],
    "Entertainment": ["steam", "playstation", "xbox", "nintendo", "cinema", "odeon", "vue", "cineworld", "ticketmaster"]
}

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
    return "Card Payment"

BANK_PATTERNS = [
    re.compile(r"(?i)(?:you spent|card ending \d+ spent|payment of|paid|a charge of|transaction of|spent)\s*([£$€]|GBP)?\s*([0-9,]+\.[0-9]{2})\s*(?:GBP\s*)?(?:at|to|with your card at)?\s*([^.,\n]+)"),
    re.compile(r"(?i)(?:approved:\s*)?([£$€]|GBP)?\s*([0-9,]+\.[0-9]{2})\s*(?:GBP\s*)?(?:spent at|at|to)\s*([^.,\n]+)"),
    re.compile(r"(?i)([£$€]|GBP)\s*([0-9,]+\.[0-9]{2})\s*(?:at|to)\s*([^.,\n]+)")
]

def parse_spend(pkg: str, title: str, text: str):
    full = f"{title or ''} {text or ''}".strip()
    source = detect_source(pkg, full)

    for pattern in BANK_PATTERNS:
        m = pattern.search(full)
        if m:
            curr = m.group(1) or "£"
            amount = float(m.group(2).replace(",", ""))
            merchant = clean_merchant(m.group(3))
            category = categorize(merchant)
            return {
                "source": source,
                "amount": amount,
                "currency": "£" if "GBP" in curr else curr,
                "merchant": merchant,
                "category": category
            }
    return None


class TestSpendParser(unittest.TestCase):

    def test_chase_asda_notification(self):
        res = parse_spend(
            "com.jpmorgan.chase.uk",
            "Chase UK",
            "You spent £18.50 with your card at ASDA"
        )
        self.assertIsNotNone(res)
        self.assertEqual(res["source"], "Chase")
        self.assertEqual(res["amount"], 18.50)
        self.assertIn("Asda", res["merchant"])
        self.assertEqual(res["category"], "Groceries")

    def test_monzo_pret_notification(self):
        res = parse_spend(
            "co.uk.getmondo",
            "Monzo",
            "You spent £12.40 at Pret A Manger"
        )
        self.assertIsNotNone(res)
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
        self.assertEqual(res["source"], "Amex")
        self.assertEqual(res["amount"], 85.00)

    def test_hsbc_tesco_notification(self):
        res = parse_spend(
            "uk.co.hsbc.hsbcukmobilebanking",
            "HSBC Card Alert",
            "You spent £14.80 at TESCO STORES on 07/09"
        )
        self.assertIsNotNone(res)
        self.assertEqual(res["source"], "HSBC")
        self.assertEqual(res["amount"], 14.80)
        self.assertEqual(res["category"], "Groceries")

    def test_paypal_steam(self):
        res = parse_spend(
            "com.paypal.android.p2pmobile",
            "PayPal",
            "You paid £29.99 to Steam Games"
        )
        self.assertIsNotNone(res)
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
        self.assertEqual(res["source"], "PayPal")
        self.assertEqual(res["amount"], 18.50)
        self.assertEqual(res["category"], "Dining & Drinks")

    def test_ignore_unrelated_notification(self):
        res = parse_spend(
            "com.whatsapp",
            "Alice",
            "Hey, are we still meeting for lunch?"
        )
        self.assertIsNone(res)


if __name__ == "__main__":
    unittest.main()
