package com.spendtracker.app.domain

import java.util.Locale

/** What the on-device model said about one alert, before it is checked. */
data class AiReading(
    val merchant: String?,
    /** "IN", "OUT" or null when the model was unsure. */
    val direction: String?,
    val ownTransfer: Boolean
)

/**
 * Prompt and answer checks for reading an unclear bank alert with the on-device model.
 * Pure code: the model only proposes; [validMerchant] decides what is trusted. The amount is
 * never taken from the model.
 */
object AiAlertReader {

    /** First words of every note line this feature writes; alert text is cut before it when re-parsed. */
    const val NOTE_MARKER = "On-device AI:"

    const val SYSTEM_PROMPT =
        "You read one UK bank or wallet notification and extract facts. " +
            "Reply with exactly three lines and nothing else:\n" +
            "MERCHANT: <the shop, company or person the money went to or came from, copied exactly from " +
            "the notification; NONE if it is not stated>\n" +
            "DIRECTION: <OUT if money left the account, IN if money arrived, UNKNOWN if unclear>\n" +
            "OWN_TRANSFER: <YES if the money moved between two accounts, pots or savings of the same person, otherwise NO>\n" +
            "Never invent a name. Never use the bank's own name as the merchant."

    fun prompt(alertText: String): String = "Notification:\n" + alertText.trim().take(600)

    private val MERCHANT_LINE = Regex("(?im)^[\\s*#>-]*MERCHANT\\s*:\\s*(.*)$")
    private val DIRECTION_LINE = Regex("(?im)^[\\s*#>-]*DIRECTION\\s*:\\s*(.*)$")
    private val TRANSFER_LINE = Regex("(?im)^[\\s*#>-]*OWN_TRANSFER\\s*:\\s*(.*)$")

    private fun clean(value: String): String = value.trim().trim('*', '"', '\'', '`', '<', '>', '.', ' ')

    /** Reads the three answer lines; null when the reply has none of them. */
    fun parseReply(reply: String): AiReading? {
        val merchant = MERCHANT_LINE.find(reply)?.groupValues?.get(1)?.let(::clean)
        val direction = DIRECTION_LINE.find(reply)?.groupValues?.get(1)?.let(::clean)?.uppercase(Locale.ROOT)
        val transfer = TRANSFER_LINE.find(reply)?.groupValues?.get(1)?.let(::clean)?.uppercase(Locale.ROOT)
        if (merchant == null && direction == null && transfer == null) return null
        return AiReading(
            merchant = merchant?.takeIf { it.isNotEmpty() && !it.equals("NONE", ignoreCase = true) },
            direction = direction?.takeIf { it == "IN" || it == "OUT" },
            ownTransfer = transfer?.startsWith("YES") == true
        )
    }

    private val GENERIC = setOf(
        "none", "unknown", "n a", "payment", "card payment", "transaction", "money in", "money out",
        "transfer", "account", "your account", "bank", "card", "you"
    )
    private val WORD = Regex("[a-z0-9]+")

    private fun words(text: String): List<String> =
        WORD.findAll(text.lowercase(Locale.ROOT)).map { it.value }.toList()

    /**
     * The model's merchant if it can be trusted, else null: 2 to 40 characters, not a generic word,
     * not the bank itself, and every word of it must appear in the alert, so the model cannot
     * invent a name.
     */
    fun validMerchant(alertText: String, merchant: String?, ownBank: String?): String? {
        val candidate = merchant?.trim()?.replace(Regex("\\s+"), " ") ?: return null
        if (candidate.length !in 2..40) return null
        val candidateWords = words(candidate)
        if (candidateWords.isEmpty() || candidateWords.none { w -> w.length >= 2 && w.any { it.isLetter() } }) return null
        if (candidateWords.joinToString(" ") in GENERIC) return null
        if (!ownBank.isNullOrBlank() && candidateWords == words(ownBank)) return null
        val alertWords = words(alertText).toSet()
        if (!candidateWords.all { it in alertWords }) return null
        return candidate
    }
}
