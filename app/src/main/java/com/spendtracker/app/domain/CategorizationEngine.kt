package com.spendtracker.app.domain

import java.util.Locale

enum class MatchType(val label: String, val priority: Int) {
    CONTAINS("Contains", 1),
    STARTS_WITH("Starts with", 2),
    EQUALS("Equals", 3)
}

data class MerchantRule(
    val id: Long,
    val matchType: MatchType,
    val pattern: String,
    val categoryKey: String
)

/** How many times [merchant] was filed under [categoryKey] in past transactions. */
data class HistoryEntry(val merchant: String, val categoryKey: String, val uses: Int)

enum class ClassificationSource(val label: String) {
    RULE("your rule"),
    HISTORY("past transactions"),
    DICTIONARY("built-in merchants")
}

sealed interface Classification {
    data class Matched(
        val categoryKey: String,
        val source: ClassificationSource,
        val matchedOn: String
    ) : Classification

    object Unknown : Classification
}

object MerchantNormalizer {
    private val NON_WORD = Regex("[^\\p{L}\\p{N}]+")
    private val NOISE = setOf("ltd", "limited", "plc", "uk", "gb", "pos", "card", "www", "com")

    /**
     * Lower-cases, strips punctuation, store numbers, legal suffixes and stray single letters
     * (so "McDonald's #4412 LTD" becomes "mcdonald").
     */
    fun normalize(raw: String): String =
        raw.lowercase(Locale.ROOT)
            .split(NON_WORD)
            .filter { token ->
                token.isNotEmpty() &&
                    token !in NOISE &&
                    !token.all { it.isDigit() } &&
                    !(token.length == 1 && token[0] in 'a'..'z')
            }
            .joinToString(" ")
}

/** Built-in fallback keywords. Keys are [com.spendtracker.app.data.TransactionCategory] names. */
object MerchantDictionary {
    val keywords: Map<String, List<String>> = linkedMapOf(
        "GROCERIES" to listOf(
            "tesco", "sainsbury", "asda", "aldi", "lidl", "waitrose", "whole foods",
            "morrisons", "co op", "ocado"
        ),
        "DINING" to listOf(
            "starbucks", "costa", "mcdonald", "pret", "deliveroo", "uber eats", "just eat",
            "greggs", "nero", "kfc", "nando"
        ),
        "TRANSPORT" to listOf("uber", "bolt", "trainline", "tfl", "shell", "bp", "esso", "lyft"),
        "SUBSCRIPTIONS" to listOf(
            "netflix", "spotify", "apple", "amazon prime", "prime video", "youtube", "disney"
        ),
        "UTILITIES" to listOf(
            "british gas", "octopus", "edison", "water", "broadband", "edf", "virgin media"
        ),
        "INTERNAL_TRANSFER" to listOf(
            "internal transfer", "transfer to chase", "transfer to hsbc", "transfer from chase",
            "transfer from hsbc", "transfer between accounts", "savings buffer"
        )
    )

    /** Whole-word / whole-phrase match; the longest keyword wins ("uber eats" beats "uber"). */
    fun match(normalized: String): Pair<String, String>? {
        val padded = " $normalized "
        var best: Pair<String, String>? = null
        for ((categoryKey, words) in keywords) {
            for (word in words) {
                if (padded.contains(" $word ") && (best == null || word.length > best.second.length)) {
                    best = categoryKey to word
                }
            }
        }
        return best
    }
}

object CategorizationEngine {

    /**
     * Pipeline: user rules, then payee history, then the built-in dictionary.
     * Only categories in [validKeys] can be returned, so hidden / wrong-type categories are skipped.
     */
    fun classify(
        merchant: String,
        rules: List<MerchantRule>,
        history: List<HistoryEntry>,
        validKeys: Set<String>
    ): Classification {
        val text = MerchantNormalizer.normalize(merchant)
        if (text.isEmpty()) return Classification.Unknown

        matchRule(text, rules, validKeys)?.let { return it }
        matchHistory(text, history, validKeys)?.let { return it }
        MerchantDictionary.match(text)?.let { (key, word) ->
            if (key in validKeys) return Classification.Matched(key, ClassificationSource.DICTIONARY, word)
        }
        return Classification.Unknown
    }

    private fun matchRule(
        text: String,
        rules: List<MerchantRule>,
        validKeys: Set<String>
    ): Classification.Matched? {
        val hit = rules
            .asSequence()
            .filter { it.categoryKey in validKeys }
            .map { it to MerchantNormalizer.normalize(it.pattern) }
            .filter { (rule, pattern) ->
                pattern.isNotEmpty() && when (rule.matchType) {
                    MatchType.CONTAINS -> text.contains(pattern)
                    MatchType.STARTS_WITH -> text.startsWith(pattern)
                    MatchType.EQUALS -> text == pattern
                }
            }
            // Most specific wins: EQUALS > STARTS_WITH > CONTAINS, then longer pattern, then newest.
            .sortedWith(
                compareByDescending<Pair<MerchantRule, String>> { it.first.matchType.priority }
                    .thenByDescending { it.second.length }
                    .thenByDescending { it.first.id }
            )
            .firstOrNull() ?: return null
        return Classification.Matched(hit.first.categoryKey, ClassificationSource.RULE, hit.first.pattern)
    }

    private fun matchHistory(
        text: String,
        history: List<HistoryEntry>,
        validKeys: Set<String>
    ): Classification.Matched? {
        val best = history
            .filter { it.categoryKey in validKeys && MerchantNormalizer.normalize(it.merchant) == text }
            .groupBy { it.categoryKey }
            .mapValues { (_, rows) -> rows.sumOf { it.uses } }
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .firstOrNull() ?: return null
        return Classification.Matched(best.key, ClassificationSource.HISTORY, text)
    }
}
