package com.spendtracker.app.ui.util

import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

/**
 * Centralized currency formatting and conversion utilities.
 * Handles minor units (pence / cents) and major units (pounds / dollars).
 */
object CurrencyUtils {

    /**
     * Formats a minor amount (e.g. 1250L -> "£12.50").
     */
    fun formatMinor(minor: Long, locale: Locale = Locale.getDefault()): String {
        return NumberFormat.getCurrencyInstance(locale)
            .format(BigDecimal.valueOf(minor).movePointLeft(2))
    }

    /**
     * Formats a major amount in double (e.g. 12.5 -> "£12.50").
     */
    fun formatMajor(amount: Double, locale: Locale = Locale.getDefault()): String {
        return NumberFormat.getCurrencyInstance(locale).format(amount)
    }

    /**
     * Formats a signed minor amount (e.g. 1250L, isPositive=true -> "+£12.50", false -> "−£12.50").
     */
    fun formatSignedMinor(minor: Long, isPositive: Boolean, locale: Locale = Locale.getDefault()): String {
        val sign = if (isPositive) "+" else "−"
        return "$sign${formatMinor(minor, locale)}"
    }

    /**
     * Formats a signed minor amount automatically inferring sign (e.g. 1250L -> "+£12.50", -500L -> "−£5.00").
     */
    fun formatSignedMinor(minor: Long, locale: Locale = Locale.getDefault()): String {
        return formatSignedMinor(kotlin.math.abs(minor), isPositive = minor >= 0, locale = locale)
    }

    /**
     * Converts minor units to decimal string without currency symbol (e.g. 1250L -> "12.50").
     */
    fun minorToDecimalString(minor: Long): String {
        return BigDecimal.valueOf(minor).movePointLeft(2).toPlainString()
    }

    private val THOUSANDS_GROUPS: Regex = Regex("^\\d{1,3}(,\\d{3})+(\\.\\d*)?$")
    private val DECIMAL_COMMA: Regex = Regex("^\\d*,\\d{1,2}$")
    private val IGNORED_CHARS: Regex = Regex("[\\s\u00A0£\$€]")

    /**
     * Parses amount input into minor units, e.g. "12.34" -> 1234, "9,99" -> 999,
     * "1,000" -> 100000, "1,234.56" -> 123456, "£ 1,234" -> 123400.
     *
     * Comma rules: with both ',' and '.', commas are thousands separators; with only commas,
     * groups of exactly 3 digits ("1,234,567") are thousands separators and a single comma
     * followed by 1–2 digits ("9,99") is a decimal comma. Anything else is invalid.
     * Returns null if invalid, negative, zero, or exceeds 2 decimal places.
     */
    fun parseDecimalToMinor(input: String): Long? {
        val normalised: String = normaliseAmount(input) ?: return null
        val value = normalised.toBigDecimalOrNull() ?: return null
        if (value.signum() <= 0 || value.stripTrailingZeros().scale() > 2) return null
        return runCatching { value.multiply(BigDecimal(100)).longValueExact() }.getOrNull()
    }

    /** [input] without spaces/currency symbols and with commas resolved to a plain "1234.56" form. */
    internal fun normaliseAmount(input: String): String? {
        val cleaned: String = input.replace(IGNORED_CHARS, "")
        if (cleaned.isEmpty()) return null
        if (!cleaned.contains(',')) return cleaned
        return when {
            THOUSANDS_GROUPS.matches(cleaned) -> cleaned.replace(",", "")
            !cleaned.contains('.') && DECIMAL_COMMA.matches(cleaned) -> cleaned.replace(',', '.')
            else -> null
        }
    }
}
