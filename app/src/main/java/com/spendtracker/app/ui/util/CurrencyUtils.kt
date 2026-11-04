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

    /**
     * Parses decimal input string (e.g. "12.34" or "12,34") into minor units (1234L).
     * Returns null if invalid, negative, zero, or exceeds 2 decimal places.
     */
    fun parseDecimalToMinor(input: String): Long? {
        val value = input.trim().replace(',', '.').toBigDecimalOrNull() ?: return null
        if (value.signum() <= 0 || value.stripTrailingZeros().scale() > 2) return null
        return runCatching { value.multiply(BigDecimal(100)).longValueExact() }.getOrNull()
    }
}
