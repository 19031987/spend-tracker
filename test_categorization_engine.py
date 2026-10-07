# -*- coding: utf-8 -*-
import unittest
import re

# Mirror of Categorization Engine logic from Kotlin & Simulator
class CategorizationEngine:
    BUILT_IN_DICTIONARY = [
        # Groceries
        {"regex": re.compile(r"\b(tesco|sainsbury|asda|aldi|lidl|waitrose|whole\s?foods|morrisons|co-?op|ocado)\b", re.I), "cat": "Groceries"},
        # Dining / Coffee
        {"regex": re.compile(r"\b(uber\s?eats|deliveroo|just\s?eat)\b", re.I), "cat": "Dining & Drinks"},
        {"regex": re.compile(r"\b(starbucks|costa|pret|mcdonald|greggs|nero|caffe\s?nero|kfc|nando)\b", re.I), "cat": "Coffee & Cafes"},
        # Transport
        {"regex": re.compile(r"\b(uber|bolt|trainline|tfl|shell|bp|esso|lyft)\b", re.I), "cat": "Transit & Rideshare"},
        # Subscriptions
        {"regex": re.compile(r"\b(netflix|spotify|apple|amazon\s?prime|prime\s?video|youtube|disney)\b", re.I), "cat": "Subscriptions"},
        # Utilities
        {"regex": re.compile(r"\b(british\s?gas|octopus|edison|water|broadband|edf|virgin\s?media)\b", re.I), "cat": "Utilities & Bills"},
        # Salary
        {"regex": re.compile(r"\b(salary|payroll|direct\s?deposit|wages)\b", re.I), "cat": "Salary & Income"}
    ]

    @classmethod
    def evaluate(cls, merchant, rules=None, history=None):
        if not merchant or not merchant.strip():
            return {"category": "Uncategorized", "source": "none"}
        
        clean = merchant.strip()
        clean_key = re.sub(r"[^a-z0-9]", "", clean.lower())

        # 1. Custom User Rules (highest priority)
        if rules:
            for r in rules:
                if re.search(r["pattern"], clean, re.I):
                    return {"category": r["category"], "source": "rule", "pattern": r["pattern"]}

        # 2. Historical Payee Memory
        if history and clean_key in history:
            return {"category": history[clean_key], "source": "history"}

        # 3. Built-In Merchant Dictionary
        for item in cls.BUILT_IN_DICTIONARY:
            if item["regex"].search(clean):
                return {"category": item["cat"], "source": "dictionary"}

        # 4. Fallback Uncategorized
        return {"category": "Uncategorized", "source": "fallback"}

class TestCategorizationEngine(unittest.TestCase):

    def test_custom_rule_highest_priority(self):
        # User explicitly mapped Tesco to Dining
        rules = [{"pattern": "Tesco", "category": "Dining & Drinks"}]
        history = {"tescoexpress": "Groceries"}
        result = CategorizationEngine.evaluate("Tesco Express #441", rules, history)
        self.assertEqual(result["category"], "Dining & Drinks")
        self.assertEqual(result["source"], "rule")

    def test_history_priority_over_dictionary(self):
        # History overrides dictionary when no custom rule matches
        history = {"shellgarage": "Coffee & Cafes"}
        result = CategorizationEngine.evaluate("Shell Garage", rules=[], history=history)
        self.assertEqual(result["category"], "Coffee & Cafes")
        self.assertEqual(result["source"], "history")

    def test_builtin_dictionary_groceries(self):
        merchants = ["Tesco Stores Ltd", "Sainsbury's Local", "ASDA Supercentre", "Aldi UK", "Lidl GB", "Waitrose & Partners", "Whole Foods Market"]
        for m in merchants:
            res = CategorizationEngine.evaluate(m)
            self.assertEqual(res["category"], "Groceries", f"Failed for {m}")
            self.assertEqual(res["source"], "dictionary")

    def test_builtin_dictionary_transport(self):
        merchants = ["Uber Trip London", "Shell Petrol Station", "BP Express", "Esso Fuel", "Bolt Ride", "Trainline Tickets"]
        for m in merchants:
            res = CategorizationEngine.evaluate(m)
            self.assertEqual(res["category"], "Transit & Rideshare", f"Failed for {m}")

    def test_builtin_dictionary_subscriptions(self):
        merchants = ["Netflix.com", "Spotify AB", "Apple.com/bill", "Amazon Prime Video", "YouTube Premium"]
        for m in merchants:
            res = CategorizationEngine.evaluate(m)
            self.assertEqual(res["category"], "Subscriptions", f"Failed for {m}")

    def test_word_boundary_safety(self):
        # Short token 'bp' should not match 'Subpoena'
        res = CategorizationEngine.evaluate("Subpoena Service")
        self.assertEqual(res["category"], "Uncategorized")
        self.assertEqual(res["source"], "fallback")

    def test_unknown_merchant_fallback(self):
        res = CategorizationEngine.evaluate("Apex Hardware Ltd")
        self.assertEqual(res["category"], "Uncategorized")
        self.assertEqual(res["source"], "fallback")

    def test_custom_category_rule_remembers_apex_hardware(self):
        # 1. Unknown initially
        res1 = CategorizationEngine.evaluate("Apex Hardware")
        self.assertEqual(res1["category"], "Uncategorized")

        # 2. Add custom rule for Apex Hardware -> Garden & Hardware
        rules = [{"pattern": "Apex Hardware", "category": "Garden & Hardware"}]

        # 3. Next evaluation matches rule
        res2 = CategorizationEngine.evaluate("Apex Hardware Stores #9", rules=rules)
        self.assertEqual(res2["category"], "Garden & Hardware")
        self.assertEqual(res2["source"], "rule")

if __name__ == "__main__":
    unittest.main()
