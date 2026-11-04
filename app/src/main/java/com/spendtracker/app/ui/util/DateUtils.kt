package com.spendtracker.app.ui.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Centralized date and time formatting utilities.
 */
object DateUtils {

    fun formatTime(timestamp: Long, locale: Locale = Locale.getDefault()): String {
        return SimpleDateFormat("HH:mm", locale).format(Date(timestamp))
    }

    fun formatDateShort(timestamp: Long, locale: Locale = Locale.getDefault()): String {
        return SimpleDateFormat("d MMM", locale).format(Date(timestamp))
    }

    fun formatDateFull(timestamp: Long, locale: Locale = Locale.getDefault()): String {
        return SimpleDateFormat("EEEE, d MMM", locale).format(Date(timestamp))
    }

    fun formatDateTime(timestamp: Long, locale: Locale = Locale.getDefault()): String {
        return SimpleDateFormat("dd MMM yyyy, HH:mm", locale).format(Date(timestamp))
    }

    fun formatDateYear(timestamp: Long, locale: Locale = Locale.getDefault()): String {
        return SimpleDateFormat("d MMM yyyy", locale).format(Date(timestamp))
    }

    fun formatCurrentMonthName(locale: Locale = Locale.getDefault()): String {
        return SimpleDateFormat("MMMM", locale).format(Date())
    }

    fun getStartOfMonthTimestamp(calendar: Calendar = Calendar.getInstance()): Long {
        return (calendar.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}
