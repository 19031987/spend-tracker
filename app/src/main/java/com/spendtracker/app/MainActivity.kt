package com.spendtracker.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spendtracker.app.data.UserPreferences
import com.spendtracker.app.ui.SpendTrackerScreen

class MainActivity : ComponentActivity() {
    private val container by lazy { AppContainer(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeMode by container.preferences.observeTheme().collectAsStateWithLifecycle(
                initialValue = UserPreferences.THEME_SYSTEM
            )
            val isDark = when (themeMode) {
                UserPreferences.THEME_DARK -> true
                UserPreferences.THEME_LIGHT -> false
                else -> isSystemInDarkTheme()
            }

            MaterialTheme(
                colorScheme = if (isDark) darkColorScheme() else lightColorScheme()
            ) {
                SpendTrackerScreen(container)
            }
        }
    }
}
