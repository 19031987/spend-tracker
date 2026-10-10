package com.spendtracker.app.data

import android.content.Context
import android.content.SharedPreferences
import com.spendtracker.app.domain.Period
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class UserPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("spend_tracker_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_CURRENCY = "pref_currency_symbol"
        private const val KEY_DEFAULT_PERIOD = "pref_default_period"
        private const val KEY_THEME = "pref_theme_mode"
        private const val KEY_BIOMETRIC_LOCK = "pref_biometric_lock_enabled"

        const val THEME_SYSTEM = "SYSTEM"
        const val THEME_LIGHT = "LIGHT"
        const val THEME_DARK = "DARK"
    }

    var currencySymbol: String
        get() = prefs.getString(KEY_CURRENCY, "£") ?: "£"
        set(value) = prefs.edit().putString(KEY_CURRENCY, value).apply()

    var defaultPeriod: Period
        get() = runCatching {
            Period.valueOf(prefs.getString(KEY_DEFAULT_PERIOD, Period.WEEK.name) ?: Period.WEEK.name)
        }.getOrDefault(Period.WEEK)
        set(value) = prefs.edit().putString(KEY_DEFAULT_PERIOD, value.name).apply()

    var themeMode: String
        get() = prefs.getString(KEY_THEME, THEME_SYSTEM) ?: THEME_SYSTEM
        set(value) = prefs.edit().putString(KEY_THEME, value).apply()

    var isBiometricLockEnabled: Boolean
        get() = prefs.getBoolean(KEY_BIOMETRIC_LOCK, false)
        set(value) = prefs.edit().putBoolean(KEY_BIOMETRIC_LOCK, value).apply()

    fun observeCurrency(): Flow<String> = callbackFlow {
        trySend(currencySymbol)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_CURRENCY) trySend(currencySymbol)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    fun observeTheme(): Flow<String> = callbackFlow {
        trySend(themeMode)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_THEME) trySend(themeMode)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    fun observeBiometricLock(): Flow<Boolean> = callbackFlow {
        trySend(isBiometricLockEnabled)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_BIOMETRIC_LOCK) trySend(isBiometricLockEnabled)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
}
