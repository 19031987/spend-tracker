package com.spendtracker.app

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spendtracker.app.data.UserPreferences
import com.spendtracker.app.ui.BiometricLockScreen
import com.spendtracker.app.ui.SpendTrackerScreen

class MainActivity : AppCompatActivity() {
    private val container by lazy { AppContainer(applicationContext) }

    // Session authentication state (persists across activity pauses, resets on fresh launch or lock)
    private var isUnlocked by mutableStateOf(false)
    private var biometricErrorMessage by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val themeMode by container.preferences.observeTheme().collectAsStateWithLifecycle(
                initialValue = UserPreferences.THEME_SYSTEM
            )
            val isBiometricEnabled by container.preferences.observeBiometricLock().collectAsStateWithLifecycle(
                initialValue = container.preferences.isBiometricLockEnabled
            )

            val isDark = when (themeMode) {
                UserPreferences.THEME_DARK -> true
                UserPreferences.THEME_LIGHT -> false
                else -> isSystemInDarkTheme()
            }

            // If biometric lock is disabled in settings, app is immediately unlocked
            val isAppAccessible = !isBiometricEnabled || isUnlocked

            LaunchedEffect(isBiometricEnabled) {
                if (isBiometricEnabled && !isUnlocked) {
                    showBiometricPrompt()
                }
            }

            MaterialTheme(
                colorScheme = if (isDark) darkColorScheme() else lightColorScheme()
            ) {
                if (isAppAccessible) {
                    SpendTrackerScreen(container)
                } else {
                    BiometricLockScreen(
                        errorMessage = biometricErrorMessage,
                        onAuthenticateClick = {
                            biometricErrorMessage = null
                            showBiometricPrompt()
                        },
                        onBypassFallback = {
                            showDeviceCredentialsPrompt()
                        }
                    )
                }
            }
        }
    }

    private fun showBiometricPrompt() {
        val biometricManager = BiometricManager.from(this)
        val canAuth = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )

        if (canAuth == BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE ||
            canAuth == BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE
        ) {
            // If device lacks biometric hardware, gracefully unlock
            isUnlocked = true
            return
        }

        if (canAuth == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED) {
            biometricErrorMessage = "No biometric or screen lock enrolled on this device. Please set up a fingerprint or PIN in device Settings."
            return
        }

        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    isUnlocked = true
                    biometricErrorMessage = null
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                        errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON
                    ) {
                        biometricErrorMessage = errString.toString()
                    }
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    biometricErrorMessage = "Fingerprint not recognised. Please try again."
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock SpendTracker")
            .setSubtitle("Authenticate using your fingerprint or device credentials")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()

        prompt.authenticate(promptInfo)
    }

    private fun showDeviceCredentialsPrompt() {
        showBiometricPrompt()
    }
}
