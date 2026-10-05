package com.spendtracker.app.parser

import java.util.Locale
import java.util.regex.Pattern

data class ParsedExpense(
    val type: String, // "IN" for Money In (green), "OUT" for Money Out (red)
    val amount: Double,
    val currency: String,
    val merchant: String,
    val category: String,
    val source: String,
    val rawText: String
)

object SpendParser {

    private val KNOWN_SOURCES = listOf(
        "Chase", "Monzo", "Revolut", "Amex", "Barclays", "Apple Pay",
        "Google Pay", "Starling", "HSBC", "PayPal", "Santander", "NatWest", "Lloyds", "Halifax"
    )

    // Money In Patterns: capture incoming funds, refunds, transfers, deposits, salary
    private val MONEY_IN_PATTERNS = listOf(
        // "You received £250.00 from John Smith" or "Payment of £50.00 received from Tom"
        Pattern.compile("(?i)(?:you received|received|payment of|transfer of)\\s*([£$€]|GBP)?\\s*([0-9,]+\\.[0-9]{2})\\s*(?:GBP\\s*)?from\\s+([^.,\\n]+)"),
        // "Dave sent you £40.00"
        Pattern.compile("(?i)([^.,\\n]+)\\s+sent you\\s*([£$€]|GBP)?\\s*([0-9,]+\\.[0-9]{2})"),
        // "Refund of £24.99 from Amazon" or "You received a refund of £12.50 from eBay"
        Pattern.compile("(?i)(?:refund(?:\\s+of)?|you received a refund of)\\s*([£$€]|GBP)?\\s*([0-9,]+\\.[0-9]{2})\\s*(?:GBP\\s*)?from\\s+([^.,\\n]+)"),
        // "Deposit of £1,500.00 from Employer Corp" or "Salary credit: £2,500.00 from TechCorp"
        Pattern.compile("(?i)(?:deposit of|salary(?:\\s+credit)?[:\\s]+)\\s*([£$€]|GBP)?\\s*([0-9,]+\\.[0-9]{2})\\s*(?:GBP\\s*)?(?:from\\s+([^.,\\n]+))?"),
        // "£50.00 received from Michael"
        Pattern.compile("(?i)([£$€]|GBP)\\s*([0-9,]+\\.[0-9]{2})\\s*(?:received from|deposited by)\\s*([^.,\\n]+)")
    )

    // Money Out Patterns: card spends, payments, direct debits, purchases
    private val MONEY_OUT_PATTERNS = listOf(
        // "Direct debit to British Gas of £65.00" or "Payment to Netflix of £8.99"
        Pattern.compile("(?i)(?:direct debit|standing order|payment)\\s+to\\s+([^.,\\n:]+?)\\s+(?:of|for)\\s*([£$€]|GBP)?\\s*([0-9,]+\\.[0-9]{2})"),
        // "You spent £18.50 with your card at ASDA" or "Card ending 8219 spent £4.20 at Costa"
        Pattern.compile("(?i)(?:you spent|card ending \\d+ spent|payment of|paid|a charge of|transaction of|spent)\\s*([£$€]|GBP)?\\s*([0-9,]+\\.[0-9]{2})\\s*(?:GBP\\s*)?(?:at|to|with your card at)?\\s*([^.,\\n]+)"),
        // "Approved: £34.20 at Waitrose" or "You paid £29.99 to Steam Games"
        Pattern.compile("(?i)(?:approved:\\s*)?([£$€]|GBP)?\\s*([0-9,]+\\.[0-9]{2})\\s*(?:GBP\\s*)?(?:spent at|paid to|at|to)\\s*([^.,\\n]+)"),
        // "You sent £30.00 to Landlord"
        Pattern.compile("(?i)(?:you sent|transfer to|direct debit to)\\s*([£$€]|GBP)?\\s*([0-9,]+\\.[0-9]{2})\\s*(?:GBP\\s*)?to\\s+([^.,\\n]+)"),
        // "Payment to Netflix: £8.99"
        Pattern.compile("(?i)(?:payment to|paid)\\s+([^.,\\n:]+)[:\\s]+([£$€]|GBP)?\\s*([0-9,]+\\.[0-9]{2})"),
        // "£12.50 spent at TESCO"
        Pattern.compile("(?i)([£$€]|GBP)\\s*([0-9,]+\\.[0-9]{2})\\s*(?:spent at|paid to|at|to)\\s*([^.,\\n]+)")
    )

    private val CATEGORY_RULES_OUT = mapOf(
        "Groceries" to listOf("tesco", "sainsbury", "asda", "morrison", "aldi", "lidl", "waitrose", "m&s", "marks & spencer", "co-op", "grocery", "supermarket", "costco", "iceland", "ocado"),
        "Dining & Drinks" to listOf("costa", "starbucks", "mcdonald", "kfc", "subway", "greggs", "nando", "deliveroo", "uber eats", "just eat", "caffe", "coffee", "restaurant", "pub", "bar", "pizza", "burger", "pret", "five guys"),
        "Transport & Fuel" to listOf("uber", "tfl", "transport for london", "trainline", "national rail", "shell", "bp", "esso", "texaco", "petrol", "parking", "bolt", "railway", "ringgo"),
        "Bills & Subscriptions" to listOf("netflix", "spotify", "disney", "prime", "amazon prime", "apple.com", "google storage", "youtube", "broadband", "bt", "virgin media", "ee", "o2", "vodafone", "three", "water", "british gas", "octopus", "edf", "e.on", "gym", "puregym"),
        "Shopping" to listOf("amazon", "ebay", "argos", "boots", "currys", "john lewis", "zara", "h&m", "primark", "tk maxx", "next", "asos", "shein", "ikea", "apple store"),
        "Entertainment" to listOf("steam", "playstation", "xbox", "nintendo", "cinema", "odeon", "vue", "cineworld", "ticketmaster")
    )

    private val CATEGORY_RULES_IN = mapOf(
        "Income & Salary" to listOf("salary", "payroll", "wages", "employer", "dividend", "earnings", "work"),
        "Refunds" to listOf("refund", "returned", "chargeback", "reimbursement"),
        "Transfers In" to listOf("sent you", "received from", "transfer from", "friends", "gift", "john", "dave", "sarah", "alex", "tom", "jane"),
        "Cashback & Rewards" to listOf("cashback", "reward", "interest", "bonus")
    )

    fun detectSource(packageName: String, content: String): String {
        val combined = "$packageName $content".lowercase(Locale.ROOT)
        return when {
            combined.contains("chase") -> "Chase"
            combined.contains("monzo") || combined.contains("mondo") -> "Monzo"
            combined.contains("revolut") -> "Revolut"
            combined.contains("amex") || combined.contains("americanexpress") -> "Amex"
            combined.contains("barclays") -> "Barclays"
            combined.contains("apple") || combined.contains("wallet") -> "Apple Pay"
            combined.contains("google") || combined.contains("gpay") -> "Google Pay"
            combined.contains("starling") -> "Starling"
            combined.contains("hsbc") -> "HSBC"
            combined.contains("paypal") -> "PayPal"
            combined.contains("santander") -> "Santander"
            combined.contains("natwest") -> "NatWest"
            combined.contains("lloyds") -> "Lloyds"
            combined.contains("halifax") -> "Halifax"
            else -> "Card Payment"
        }
    }

    fun parse(packageName: String, title: String?, text: String?): ParsedExpense? {
        val fullContent = listOfNotNull(title, text).joinToString(" ").trim()
        if (fullContent.isEmpty()) return null

        val source = detectSource(packageName, fullContent)

        // 1. Check Money In patterns first
        for (pattern in MONEY_IN_PATTERNS) {
            val matcher = pattern.matcher(fullContent)
            if (matcher.find()) {
                val groupCount = matcher.groupCount()
                var curr = "£"
                var amount = 0.0
                var merchantRaw = "Income Sender"

                if (groupCount >= 3) {
                    val g1 = matcher.group(1)
                    val g2 = matcher.group(2)
                    val g3 = matcher.group(3)

                    if (isCurrencyOrAmount(g1) || isNumber(g2)) {
                        curr = g1?.trim() ?: "£"
                        amount = g2?.replace(",", "")?.toDoubleOrNull() ?: 0.0
                        merchantRaw = g3?.trim() ?: "Income Sender"
                    } else {
                        // Pattern like "Dave sent you £40.00" -> g1 is Dave, g2 is curr, g3 is amt
                        merchantRaw = g1?.trim() ?: "Income Sender"
                        curr = g2?.trim() ?: "£"
                        amount = g3?.replace(",", "")?.toDoubleOrNull() ?: 0.0
                    }
                } else if (groupCount == 2) {
                    curr = matcher.group(1)?.trim() ?: "£"
                    amount = matcher.group(2)?.replace(",", "")?.toDoubleOrNull() ?: 0.0
                    merchantRaw = "Direct Deposit"
                }

                if (amount > 0.0) {
                    val cleanMerchant = cleanMerchantName(merchantRaw, source)
                    val category = categorize("$cleanMerchant $fullContent", "IN")
                    return ParsedExpense(
                        type = "IN",
                        amount = amount,
                        currency = if (curr.contains("GBP", ignoreCase = true)) "£" else curr,
                        merchant = cleanMerchant,
                        category = category,
                        source = source,
                        rawText = fullContent
                    )
                }
            }
        }

        // 2. Check Money Out patterns
        for (pattern in MONEY_OUT_PATTERNS) {
            val matcher = pattern.matcher(fullContent)
            if (matcher.find()) {
                val groupCount = matcher.groupCount()
                var curr = "£"
                var amount = 0.0
                var merchantRaw = "Retailer"

                if (groupCount >= 3) {
                    val g1 = matcher.group(1)
                    val g2 = matcher.group(2)
                    val g3 = matcher.group(3)

                    if (isNumber(g2) || isCurrencyOrAmount(g1)) {
                        curr = g1?.trim() ?: "£"
                        amount = g2?.replace(",", "")?.toDoubleOrNull() ?: 0.0
                        merchantRaw = g3?.trim() ?: "Retailer"
                    } else {
                        // e.g. Direct debit to British Gas of £65.00 -> g1: British Gas, g2: £, g3: 65.00
                        merchantRaw = g1?.trim() ?: "Retailer"
                        curr = g2?.trim() ?: "£"
                        amount = g3?.replace(",", "")?.toDoubleOrNull() ?: 0.0
                    }
                }

                if (amount > 0.0) {
                    val cleanMerchant = cleanMerchantName(merchantRaw, source)
                    val category = categorize("$cleanMerchant $fullContent", "OUT")
                    return ParsedExpense(
                        type = "OUT",
                        amount = amount,
                        currency = if (curr.contains("GBP", ignoreCase = true)) "£" else curr,
                        merchant = cleanMerchant,
                        category = category,
                        source = source,
                        rawText = fullContent
                    )
                }
            }
        }

        // 3. Fallback pattern: detect currency & amount, and determine direction
        val fallbackMatcher = Pattern.compile("([£$€]|GBP)?\\s*([0-9,]+\\.[0-9]{2})").matcher(fullContent)
        if (fallbackMatcher.find()) {
            val curr = fallbackMatcher.group(1) ?: "£"
            val amt = fallbackMatcher.group(2)?.replace(",", "")?.toDoubleOrNull() ?: return null

            val lower = fullContent.lowercase(Locale.ROOT)
            val isIn = listOf("received", "sent you", "deposit", "salary", "refund", "credit", "+").any { lower.contains(it) }
            val txType = if (isIn) "IN" else "OUT"
            val fallbackMerchant = if (isIn) "$source Credit" else "$source Payment"
            val category = if (isIn) "Money In" else "General Spend"

            return ParsedExpense(
                type = txType,
                amount = amt,
                currency = if (curr.contains("GBP", ignoreCase = true)) "£" else curr,
                merchant = fallbackMerchant,
                category = category,
                source = source,
                rawText = fullContent
            )
        }

        return null
    }

    private fun isNumber(str: String?): Boolean {
        if (str == null) return false
        val s = str.replace(",", "").trim()
        return s.toDoubleOrNull() != null
    }

    private fun isCurrencyOrAmount(str: String?): Boolean {
        if (str == null) return false
        val s = str.trim()
        return s.contains("£") || s.contains("$") || s.contains("€") || s.contains("GBP", ignoreCase = true)
    }

    fun cleanMerchantName(raw: String, source: String = ""): String {
        var clean = raw.trim()

        // Strip known source prefixes (e.g. "PayPal Dave" -> "Dave", "HSBC Tesco" -> "Tesco")
        for (s in KNOWN_SOURCES) {
            clean = clean.replace(Regex("(?i)^$s\\s*[:\\-]?\\s*"), "").trim()
        }

        // Strip leading indicators
        clean = clean.replace(Regex("(?i)^(?:at|to|from|with\\s+your\\s+card\\s+at|spent\\s+at|payment\\s+to|payment\\s+from)\\s+"), "").trim()

        // Strip trailing indicators & noise
        clean = clean.replace(Regex("(?i)\\s+on\\s+\\d{1,2}[/-]\\d{1,2}(?:[/-]\\d{2,4})?.*$"), "")
        clean = clean.replace(Regex("(?i)\\s+at\\s+\\d{1,2}:\\d{2}.*$"), "")
        clean = clean.replace(Regex("(?i)\\s+(?:via contactless|using card.*|card ending \\d+.*|was approved.*|ref:?.*)$"), "")
        clean = clean.replace(Regex("(?i)\\b(?:ltd|limited|uk)\\b\\.?"), "")
        clean = clean.replace(Regex("[^a-zA-Z0-9 &'. -]"), " ")
        clean = clean.replace(Regex("\\s+"), " ").trim()

        val lower = clean.lowercase(Locale.ROOT)
        when {
            lower.contains("tesco") -> return "Tesco Stores"
            lower.contains("sainsbury") -> return "Sainsbury's"
            lower.contains("asda") -> return "ASDA"
            lower.contains("costa") -> return "Costa Coffee"
            lower.contains("pret") -> return "Pret A Manger"
            lower.contains("starbucks") -> return "Starbucks"
            lower.contains("steam") -> return "Steam Games"
            lower.contains("netflix") -> return "Netflix"
            lower.contains("uber eats") -> return "Uber Eats"
            lower.contains("uber") && !lower.contains("eats") -> return "Uber"
            lower.contains("amazon") -> return "Amazon"
            lower.contains("shell") -> return "Shell Petrol"
            lower.contains("apple store") -> return "Apple Store"
            lower.contains("harrods") -> return "Harrods"
            lower.contains("british gas") -> return "British Gas"
        }

        val words = clean.split(" ").filter { it.isNotEmpty() }
        if (words.isEmpty()) return "Retailer"

        return words.joinToString(" ") { word ->
            word.lowercase(Locale.ROOT).replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
        }
    }

    fun categorize(textToCheck: String, txType: String = "OUT"): String {
        val lower = textToCheck.lowercase(Locale.ROOT)
        if (txType == "IN") {
            for ((category, keywords) in CATEGORY_RULES_IN) {
                for (kw in keywords) {
                    if (kw.length <= 3) {
                        val pattern = Regex("(?i)\\b" + Regex.escape(kw) + "\\b")
                        if (pattern.containsMatchIn(lower)) return category
                    } else {
                        if (lower.contains(kw)) return category
                    }
                }
            }
            return "Money In"
        } else {
            for ((category, keywords) in CATEGORY_RULES_OUT) {
                for (kw in keywords) {
                    if (kw.length <= 3) {
                        val pattern = Regex("(?i)\\b" + Regex.escape(kw) + "\\b")
                        if (pattern.containsMatchIn(lower)) return category
                    } else {
                        if (lower.contains(kw)) return category
                    }
                }
            }
            return "General Spend"
        }
    }
}
