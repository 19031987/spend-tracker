package com.spendtracker.app.parser

import java.util.Locale
import java.util.regex.Pattern

data class ParsedExpense(
    val amount: Double,
    val currency: String,
    val merchant: String,
    val category: String,
    val source: String,
    val rawText: String
)

object SpendParser {

    private val BANK_PATTERNS = listOf(
        // "You spent £18.50 with your card at ASDA" or "Card ending 8219 spent £4.20 at Costa"
        Pattern.compile("(?i)(?:you spent|card ending \\d+ spent|payment of|paid|a charge of|transaction of|spent)\\s*([£$€]|GBP)?\\s*([0-9,]+\\.[0-9]{2})\\s*(?:GBP\\s*)?(?:at|to|with your card at)?\\s*([^.,\\n]+)"),
        // "Approved: £34.20 at Waitrose" or "You paid £29.99 to Steam Games"
        Pattern.compile("(?i)(?:approved:\\s*)?([£$€]|GBP)?\\s*([0-9,]+\\.[0-9]{2})\\s*(?:GBP\\s*)?(?:spent at|at|to)\\s*([^.,\\n]+)"),
        // "£12.50 spent at TESCO"
        Pattern.compile("(?i)([£$€]|GBP)\\s*([0-9,]+\\.[0-9]{2})\\s*(?:at|to)\\s*([^.,\\n]+)")
    )

    private val CATEGORY_RULES = mapOf(
        "Groceries" to listOf("tesco", "sainsbury", "asda", "morrison", "aldi", "lidl", "waitrose", "m&s", "marks & spencer", "co-op", "grocery", "supermarket", "costco"),
        "Dining & Drinks" to listOf("costa", "starbucks", "mcdonald", "kfc", "subway", "greggs", "nando", "deliveroo", "uber eats", "just eat", "caffe", "coffee", "restaurant", "pub", "bar", "pizza", "burger", "pret"),
        "Transport & Fuel" to listOf("uber", "tfl", "transport for london", "trainline", "national rail", "shell", "bp", "esso", "texaco", "petrol", "parking", "bolt", "railway"),
        "Bills & Subscriptions" to listOf("netflix", "spotify", "disney", "prime", "amazon prime", "apple.com", "google storage", "youtube", "broadband", "bt", "virgin media", "ee", "o2", "vodafone", "three", "water", "british gas", "octopus", "edf", "e.on", "gym", "puregym"),
        "Shopping" to listOf("amazon", "ebay", "argos", "boots", "currys", "john lewis", "zara", "h&m", "primark", "tk maxx", "next", "asos", "shein", "ikea", "harrods", "apple store"),
        "Entertainment" to listOf("steam", "playstation", "xbox", "nintendo", "cinema", "odeon", "vue", "cineworld", "ticketmaster")
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
            else -> "Card Payment"
        }
    }

    fun parse(packageName: String, title: String?, text: String?): ParsedExpense? {
        val fullContent = listOfNotNull(title, text).joinToString(" ").trim()
        if (fullContent.isEmpty()) return null

        val source = detectSource(packageName, fullContent)

        for (pattern in BANK_PATTERNS) {
            val matcher = pattern.matcher(fullContent)
            if (matcher.find()) {
                val currSymbol = matcher.group(1)?.trim() ?: "£"
                val amountStr = matcher.group(2)?.replace(",", "") ?: continue
                val merchantRaw = matcher.group(3)?.trim() ?: "Unknown Merchant"

                val amount = amountStr.toDoubleOrNull() ?: continue
                val cleanMerchant = cleanMerchantName(merchantRaw)
                val category = categorize(cleanMerchant)

                return ParsedExpense(
                    amount = amount,
                    currency = if (currSymbol.contains("GBP", ignoreCase = true)) "£" else currSymbol,
                    merchant = cleanMerchant,
                    category = category,
                    source = source,
                    rawText = fullContent
                )
            }
        }

        // Generic fallback if specific phrases weren't captured but contains clear amount
        val fallbackMatcher = Pattern.compile("([£$€])\\s*([0-9,]+\\.[0-9]{2})").matcher(fullContent)
        if (fallbackMatcher.find()) {
            val curr = fallbackMatcher.group(1) ?: "£"
            val amt = fallbackMatcher.group(2)?.replace(",", "")?.toDoubleOrNull() ?: return null
            val fallbackMerchant = "$source Purchase"
            return ParsedExpense(
                amount = amt,
                currency = curr,
                merchant = fallbackMerchant,
                category = "General",
                source = source,
                rawText = fullContent
            )
        }

        return null
    }

    private fun cleanMerchantName(raw: String): String {
        var clean = raw
            .replace(Regex("(?i)\\b(on \\d{1,2}/\\d{1,2}|via contactless|card ending \\d+|using card|ltd|limited|uk)\\b"), "")
            .replace(Regex("[^a-zA-Z0-9 &'-]"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")

        return clean.split(" ").joinToString(" ") { word ->
            word.lowercase(Locale.ROOT).replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
        }.ifEmpty { "Retailer" }
    }

    fun categorize(merchantName: String): String {
        val lower = merchantName.lowercase(Locale.ROOT)
        for ((category, keywords) in CATEGORY_RULES) {
            for (kw in keywords) {
                if (kw.length <= 3) {
                    val pattern = Regex("(?i)\\b" + Regex.escape(kw) + "\\b")
                    if (pattern.containsMatchIn(merchantName)) return category
                } else {
                    if (lower.contains(kw)) {
                        return category
                    }
                }
            }
        }
        return "General"
    }
}
