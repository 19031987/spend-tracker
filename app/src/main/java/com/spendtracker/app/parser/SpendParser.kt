package com.spendtracker.app.parser

import java.util.Locale

/**
 * SIGN CONVENTION (single source of truth for the whole app)
 * ----------------------------------------------------------
 *  - [amount] is ALWAYS stored as a positive magnitude.
 *  - [type] == [SpendParser.TYPE_IN]  -> inflow  (deposit, top-up, refund, salary) -> shown as "+£x" (green)
 *  - [type] == [SpendParser.TYPE_OUT] -> outflow (purchase, bill, transfer out)    -> shown as "−£x"
 * The sign is derived from [type] at display/aggregation time, never from the stored amount.
 */
enum class Direction {
    INCOMING,
    OUTGOING
}

data class ParsedExpense(
    val type: String, // "IN" for Money In (green), "OUT" for Money Out
    val amount: Double,
    val currency: String,
    val merchant: String,
    val category: String,
    val source: String,
    val rawText: String,
    /** True when no merchant could be extracted and a generic "<Source> Payment" label was used. */
    val merchantIsFallback: Boolean = false,
    val direction: Direction = if (type == SpendParser.TYPE_IN) Direction.INCOMING else Direction.OUTGOING
)

object SpendParser {

    const val TYPE_IN = "IN"
    const val TYPE_OUT = "OUT"

    private enum class Trust { TRUSTED, FINANCE, SMS, UNTRUSTED }

    private data class Money(val sign: String, val currency: String, val amount: Double, val start: Int, val end: Int)

    private val IC = RegexOption.IGNORE_CASE

    private val KNOWN_SOURCES = listOf(
        "Chase", "Monzo", "Revolut", "Amex", "Barclays", "Apple Pay", "Google Pay", "Samsung Pay",
        "Starling", "HSBC", "PayPal", "Santander", "NatWest", "Lloyds", "Halifax", "Nationwide"
    )

    // ---------------------------------------------------------------------------------------
    // Package allow-listing. FIX: previously EVERY notification on the phone (WhatsApp, email,
    // shopping apps, promos) was run through the parser, so any "£" figure became a transaction.
    // ---------------------------------------------------------------------------------------
    private val BANK_PACKAGE_HINTS = listOf(
        "jpmorgan" to "Chase", "chase" to "Chase", "getmondo" to "Monzo", "monzo" to "Monzo",
        "revolut" to "Revolut", "americanexpress" to "Amex", "amex" to "Amex",
        "barclays" to "Barclays", "barclaycard" to "Barclays", "starling" to "Starling",
        "hsbc" to "HSBC", "paypal" to "PayPal", "santander" to "Santander", "natwest" to "NatWest",
        "lloyds" to "Lloyds", "halifax" to "Halifax", "nationwide" to "Nationwide"
    )

    private val WALLET_PACKAGE_HINTS = listOf(
        "walletnfcrel", "com.google.android.apps.wallet", "nbu.paisa",
        "samsung.android.spay", "samsungpay", "samsung.android.samsungpay"
    )

    private val FINTECH_PACKAGE_HINTS = listOf(
        "bank", "curve", "transferwise", "kroo", "tsb", "metrobank", "firstdirect", "virginmoney",
        "capitalone", "mbna", "moneybox", "zopa", "tide", "monese", "vanquis", "klarna", "clearpay",
        "rbs", "ulster", "coop", "marcus", "venmo", "squareup.cash"
    )

    private val SMS_PACKAGES = listOf(
        "com.google.android.apps.messaging", "com.samsung.android.messaging",
        "com.android.mms", "com.android.messaging", "com.oneplus.mms"
    )

    // FIX: word-bounded matching. `contains("chase")` used to match "purCHASE", tagging every
    // "Google Pay Purchase" as a Chase transaction.
    private val BANK_CONTENT_WORDS = listOf(
        "chase" to "Chase", "monzo" to "Monzo", "revolut" to "Revolut",
        "amex|american express" to "Amex", "barclays|barclaycard" to "Barclays",
        "starling" to "Starling", "hsbc" to "HSBC", "paypal" to "PayPal",
        "santander" to "Santander", "natwest" to "NatWest", "lloyds" to "Lloyds",
        "halifax" to "Halifax", "nationwide" to "Nationwide"
    ).map { (words, name) -> Regex("\\b(?:$words)\\b", IC) to name }

    private val GOOGLE_PAY_WORDS = Regex("\\b(?:google pay|google wallet|gpay)\\b", IC)
    private val APPLE_PAY_WORDS = Regex("\\bapple pay\\b", IC)
    private val SAMSUNG_PAY_WORDS = Regex("\\bsamsung (?:pay|wallet)\\b", IC)

    // ---------------------------------------------------------------------------------------
    // Amount extraction. FIX: a currency marker (£ € $ GBP EUR USD) is now REQUIRED.
    // The old fallback `([£$€]|GBP)?\s*([0-9,]+\.[0-9]{2})` made the currency optional, so dates
    // ("05.10.26"), times, card digits and balances were captured as spend.
    // ---------------------------------------------------------------------------------------
    private const val CUR_ALT = "[£€\$]|GBP|EUR|USD"
    private const val NUM = "(?:[0-9]{1,3}(?:,[0-9]{3})+|[0-9]+)(?:\\.[0-9]{1,2})?"

    private val MONEY_PREFIX = Regex("([+\\-\u2212]?)($CUR_ALT)\\s?($NUM)(?![0-9])", IC)
    private val MONEY_SUFFIX = Regex("(?<![0-9.,])([+\\-\u2212]?)($NUM)\\s?(GBP|EUR|USD)\\b", IC)

    // FIX: balances / limits are stripped BEFORE looking for the transaction amount, so
    // "You spent £8.99 ... Balance £1,650.99" no longer records £1,650.99.
    private val BALANCE_PHRASES = listOf(
        Regex(
            "\\b(?:(?:available|new|current|remaining|account|card)\\s+)?" +
                "(?:balance|bal|credit limit|available to spend|left to spend|spending limit)\\b\\.?" +
                "\\s*(?:is|of|now|was|:|=|-)?\\s*(?:now\\s+)?[+\\-\u2212]?(?:$CUR_ALT)\\s?$NUM",
            IC
        ),
        Regex("\\bavailable(?:\\s+funds)?\\s*(?:is|of|:|=|-)?\\s*(?:$CUR_ALT)\\s?$NUM", IC),
        Regex("(?:$CUR_ALT)\\s?$NUM\\s*(?:available|remaining|balance|left to spend)\\b", IC)
    )

    // Notifications that mention money but are NOT a completed transaction
    // (3-D Secure approvals, OTPs, declines, requests, statements, summaries, promos, adverts).
    private val IGNORE = Regex(
        "\\b(?:approve|confirm|verify|authori[sz]e|passcode|one[- ]time|otp|security code|verification|" +
            "declined|unsuccessful|failed|insufficient|requested|request|requesting|statement|" +
            "minimum payment|payment due|is due|due on|due date|offer|offers|voucher|promo|promotion|" +
            "discount|win|chance to|earn up to|save up to|save\\s+(?:$CUR_ALT)|get up to|this week|this month|last week|" +
            "last month|so far|summary|in total|total spend|reminder|scheduled|upcoming|" +
            "will be taken|will be paid|will leave|deals?|deals?\\s+from|starting (?:at|from)|" +
            "coupon|code\\s+[a-z0-9]+|bogo|buy\\s+\\d+\\s+get|free\\s+(?:delivery|pizza|side|drink)|" +
            "piping hot|delicious|freshly made|order (?:now|online)|order today|taste|craving|special offer|" +
            "limited time|deal drop|exclusive offer|use code)\\b",
        IC
    )
    private val PROMO = Regex(
        "(?:[0-9]+\\s?%\\s?off\\b|" +
            "\\b(?:save|get|enjoy|claim)\\s+(?:$CUR_ALT)\\s?$NUM\\s+(?:off|when you spend|on your order)|" +
            "\\b(?:from|just|only)\\s+(?:$CUR_ALT)\\s?$NUM\\b|" +
            "\\b(?:pizzas?|meals?|burgers?)\\s+(?:from|for)\\s+(?:$CUR_ALT)\\s?$NUM)",
        IC
    )

    // ---------------------------------------------------------------------------------------
    // Direction (sign) detection. FIX: inflow phrasing such as "£23.00 has been added",
    // "You topped up", "paid in", "credited" was not recognised and fell through to OUT,
    // which is why added funds rendered as negative spend. Also "credit" used to match
    // "credit card", flipping card spends into income.
    // Precedence: explicit +/- sign > strong IN > strong OUT > weak IN > weak OUT.
    // ---------------------------------------------------------------------------------------
    private val STRONG_IN = Regex(
        "\\b(?:received from|you(?:'ve|’ve| have)? received|received|sent you|paid you|refund(?:ed)?|cashback|salary|wages|payroll|" +
            "paid in|paid into|credited|deposit(?:ed)?|top(?:ped)?[ -]?up|added to your|has been added|" +
            "have been added|you(?:'ve|’ve| have)? added|added money|money in|reimburs(?:ed|ement)|" +
            "transfer from|reversal|reversed|incoming payment|incoming transfer)\\b",
        IC
    )
    private val STRONG_OUT = Regex(
        "\\b(?:you(?:'ve|’ve| have)? (?:spent|paid|sent|bought|made a payment)|spent|paid to|payment to|paid|" +
            "direct debit|standing order|purchase[ds]?|charged|debited|withdrawal|withdrawn|withdrew|" +
            "card payment|sent to|sent|transfer to)\\b",
        IC
    )
    private val WEAK_IN = Regex("\\b(?:received|incoming|has arrived|arrived|cashback|interest|credit)\\b(?!\\s*card)", IC)
    private val WEAK_OUT = Regex(
        "\\b(?:payment|paid|pay|transaction|approved|contactless|card ending|debit|subscription|bill|charge)\\b",
        IC
    )
    // Verbs that explicitly indicate a financial transaction occurred (required for SMS)
    private val FINANCIAL_TX_VERB = Regex(
        "\\b(?:spent|paid|debited|charged|withdrawal|withdrawn|purchase[ds]?|card ending|received|credited|refund(?:ed)?|cashback|top(?:ped)?[ -]?up|direct debit|standing order)\\b",
        IC
    )

    // ---------------------------------------------------------------------------------------
    // Merchant extraction
    // ---------------------------------------------------------------------------------------
    private const val NAME = "([A-Za-z0-9&'][^.,;:!?\\n•*()|]{0,48}?)"
    private const val TERM =
        "(?=\\s+(?:on|via|using|with|ref|reference|card|was|has|is|have|and|for)\\b|\\s+at\\s+[0-9]|\\s+-\\s|[.,;:!?\\n•*()|]|\\s*$)"

    private val IN_SENT_YOU = Regex("$NAME\\s+(?:has\\s+)?sent you\\b", IC)
    private val IN_PAID_YOU = Regex("$NAME\\s+(?:has\\s+)?paid you\\b", IC)
    private val IN_FROM = Regex("\\bfrom\\s+$NAME$TERM", IC)
    private val OUT_DD = Regex("\\b(?:direct debit|standing order|payment|transfer)\\s+to\\s+$NAME(?=\\s+(?:of|for)\\b)", IC)
    private val OUT_PAID_VENDOR = Regex("\\bpaid\\s+$NAME(?=\\s+(?:$CUR_ALT)?\\s*$NUM)", IC)
    private val OUT_VENDOR_AMOUNT = Regex("^(?:(?:Chase|HSBC|Monzo|Revolut|Barclays|Amex)\\s+)?$NAME(?=\\s+(?:$CUR_ALT)\\s*$NUM)", IC)
    private val OUT_AT_TO = Regex("\\b(?:at|to)\\s+$NAME$TERM", IC)

    private val GENERIC_TITLE = Regex(
        "\\b(?:alert|alerts|notification|payment|payments|purchase|transaction|activity|account|card|" +
            "received|money|spend|spending|spent|update|credit|debit|wallet|pay|paid|deposit|refund|" +
            "salary|transfer|bank|banking)\\b",
        IC
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
        "Cashback & Rewards" to listOf("cashback", "reward", "interest", "bonus"),
        "Top-ups & Deposits" to listOf("top up", "topped up", "top-up", "topup", "added", "deposit", "paid in")
    )

    // ---------------------------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------------------------

    fun detectSource(packageName: String, content: String): String {
        val pkg = packageName.lowercase(Locale.ROOT)
        BANK_PACKAGE_HINTS.firstOrNull { pkg.contains(it.first) }?.let { return it.second }

        // Earliest bank mention in the text wins (title usually comes first).
        val fromContent = BANK_CONTENT_WORDS
            .mapNotNull { (regex, name) -> regex.find(content)?.let { it.range.first to name } }
            .minByOrNull { it.first }
            ?.second
        if (fromContent != null) return fromContent

        return when {
            pkg.contains("walletnfcrel") || pkg.contains("nbu.paisa") || GOOGLE_PAY_WORDS.containsMatchIn(content) -> "Google Pay"
            pkg.contains("samsung") && pkg.contains("pay") || SAMSUNG_PAY_WORDS.containsMatchIn(content) -> "Samsung Pay"
            APPLE_PAY_WORDS.containsMatchIn(content) -> "Apple Pay"
            else -> "Card Payment"
        }
    }

    /**
     * Parses a notification into a transaction, or returns null if it is not a completed
     * transaction from a trusted financial source.
     *
     * @param packageName posting app. Pass "" for trusted internal input (simulator / data repair).
     */
    fun parse(packageName: String, title: String?, text: String?): ParsedExpense? {
        val pkg = packageName.trim().lowercase(Locale.ROOT)
        val content = listOfNotNull(title?.trim(), text?.trim()).filter { it.isNotEmpty() }.joinToString(" ")
        if (content.isEmpty()) return null

        val trust = classifyPackage(pkg)
        if (trust == Trust.UNTRUSTED) return null
        if (IGNORE.containsMatchIn(content) || PROMO.containsMatchIn(content)) return null

        var scrubbed = content
        for (r in BALANCE_PHRASES) scrubbed = r.replace(scrubbed, " ")

        val money = findFirstMoney(scrubbed) ?: return null
        if (money.amount <= 0.0 || money.amount >= 1_000_000.0) return null

        if (trust == Trust.SMS && !FINANCIAL_TX_VERB.containsMatchIn(scrubbed)) {
            return null // SMS must have an explicit financial transaction verb (debited, spent, paid, etc.)
        }

        val type = when {
            money.sign == "+" -> TYPE_IN
            money.sign == "-" || money.sign == "\u2212" -> TYPE_OUT
            STRONG_IN.containsMatchIn(scrubbed) -> TYPE_IN
            STRONG_OUT.containsMatchIn(scrubbed) -> TYPE_OUT
            WEAK_IN.containsMatchIn(scrubbed) -> TYPE_IN
            WEAK_OUT.containsMatchIn(scrubbed) -> TYPE_OUT
            trust == Trust.SMS -> return null // SMS needs an explicit transaction verb
            else -> TYPE_OUT // bank/wallet alert with an amount but no verb, e.g. "£4.00 with Visa ••1234"
        }

        val source = detectSource(pkg, content)
        val (merchantRaw, isFallback) = extractMerchant(type, scrubbed, money.end, title, source)
        val cleaned = if (isFallback || merchantRaw.isBlank()) "" else cleanMerchantName(merchantRaw, source)
        val finalMerchant = cleaned.ifBlank { "" }
        val finalFallback = isFallback || finalMerchant.isBlank()

        return ParsedExpense(
            type = type,
            amount = money.amount,
            currency = money.currency,
            merchant = finalMerchant,
            category = categorize("$finalMerchant $content", type),
            source = source,
            rawText = content,
            merchantIsFallback = finalFallback
        )
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
        if (words.isEmpty()) return ""

        return words.joinToString(" ") { word ->
            word.lowercase(Locale.ROOT).replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
        }
    }

    fun categorize(textToCheck: String, txType: String = TYPE_OUT): String {
        val lower = textToCheck.lowercase(Locale.ROOT)
        val rules = if (txType == TYPE_IN) CATEGORY_RULES_IN else CATEGORY_RULES_OUT
        for ((category, keywords) in rules) {
            for (kw in keywords) {
                if (kw.length <= 3) {
                    if (Regex("(?i)\\b" + Regex.escape(kw) + "\\b").containsMatchIn(lower)) return category
                } else if (lower.contains(kw)) {
                    return category
                }
            }
        }
        return if (txType == TYPE_IN) "Money In" else "General Spend"
    }

    // ---------------------------------------------------------------------------------------
    // Internals
    // ---------------------------------------------------------------------------------------

    private fun classifyPackage(pkg: String): Trust = when {
        pkg.isEmpty() -> Trust.TRUSTED
        BANK_PACKAGE_HINTS.any { pkg.contains(it.first) } -> Trust.FINANCE
        WALLET_PACKAGE_HINTS.any { pkg.contains(it) } -> Trust.FINANCE
        FINTECH_PACKAGE_HINTS.any { pkg.contains(it) } -> Trust.FINANCE
        SMS_PACKAGES.any { pkg == it } -> Trust.SMS
        else -> Trust.UNTRUSTED
    }

    private fun findFirstMoney(text: String): Money? {
        val prefix = MONEY_PREFIX.find(text)?.let { m ->
            val amt = m.groupValues[3].replace(",", "").toDoubleOrNull() ?: return@let null
            Money(m.groupValues[1], normalizeCurrency(m.groupValues[2]), amt, m.range.first, m.range.last + 1)
        }
        val suffix = MONEY_SUFFIX.find(text)?.let { m ->
            val amt = m.groupValues[2].replace(",", "").toDoubleOrNull() ?: return@let null
            Money(m.groupValues[1], normalizeCurrency(m.groupValues[3]), amt, m.range.first, m.range.last + 1)
        }
        return listOfNotNull(prefix, suffix).minByOrNull { it.start }
    }

    private fun normalizeCurrency(raw: String): String = when (raw.uppercase(Locale.ROOT)) {
        "GBP" -> "£"
        "EUR" -> "€"
        "USD" -> "$"
        else -> raw
    }

    private val ACTION_WORDS = setOf(
        "you", "your", "card", "cards", "account", "accounts", "make", "payment", "payments",
        "direct debit", "standing order", "transfer", "transfers", "alert", "alerts", "notification",
        "chase", "hsbc", "monzo", "revolut", "barclays", "paypal", "amex", "sent", "spend", "spent",
        "charge", "charges", "transaction", "transactions", "ending", "approved"
    )

    private fun isUsableName(candidate: String?): Boolean {
        val c = candidate?.trim() ?: return false
        if (c.length < 2) return false
        var lower = c.lowercase(Locale.ROOT)
        for (s in KNOWN_SOURCES) {
            val sLow = s.lowercase(Locale.ROOT)
            if (lower.startsWith("$sLow ") || lower.startsWith("$sLow:")) {
                lower = lower.removePrefix(sLow).trim(' ', ':', '-', '\t')
            }
        }
        if (lower.length < 2) return false
        if (lower in ACTION_WORDS) return false
        val words = Regex("[a-z0-9]+").findAll(lower).map { it.value }.toSet()
        if (words.intersect(ACTION_WORDS).isNotEmpty()) return false
        if (lower.startsWith("your ") || lower.startsWith("you ") || lower.startsWith("card ending") || lower.startsWith("make ")) return false
        if (Regex("^[0-9:./ -]+$").matches(lower)) return false
        return true
    }

    private fun extractMerchant(type: String, text: String, amountEnd: Int, title: String?, source: String): Pair<String, Boolean> {
        if (type == TYPE_IN) {
            IN_SENT_YOU.find(text)?.groupValues?.get(1)?.takeIf { isUsableName(it) }?.let { return it.trim() to false }
            IN_PAID_YOU.find(text)?.groupValues?.get(1)?.takeIf { isUsableName(it) }?.let { return it.trim() to false }
            IN_FROM.findAll(text).map { it.groupValues[1] }.firstOrNull { isUsableName(it) }?.let { return it.trim() to false }
        } else {
            // Prefer a merchant that appears AFTER the amount ("£4.20 at Costa")...
            val tail = if (amountEnd in 0..text.length) text.substring(amountEnd) else ""
            OUT_AT_TO.findAll(tail).map { it.groupValues[1] }.firstOrNull { isUsableName(it) }?.let { return it.trim() to false }
            OUT_DD.find(text)?.groupValues?.get(1)?.takeIf { isUsableName(it) }?.let { return it.trim() to false }
            // ...then anywhere ("Payment to Netflix: £8.99").
            OUT_AT_TO.findAll(text).map { it.groupValues[1] }.firstOrNull { isUsableName(it) }?.let { return it.trim() to false }
            OUT_PAID_VENDOR.find(text)?.groupValues?.get(1)?.takeIf { isUsableName(it) }?.let { return it.trim() to false }
            OUT_VENDOR_AMOUNT.find(text)?.groupValues?.get(1)?.takeIf { isUsableName(it) }?.let { return it.trim() to false }
        }

        // Wallet apps (Google Wallet etc.) put the merchant in the title: "Tesco" / "£4.00 with Visa ••1234".
        val t = title?.trim().orEmpty()
        if (t.isNotEmpty() && !GENERIC_TITLE.containsMatchIn(t) && findFirstMoney(t) == null &&
            BANK_CONTENT_WORDS.none { it.first.containsMatchIn(t) } && isUsableName(t)
        ) {
            return t to false
        }

        return "" to true
    }
}
