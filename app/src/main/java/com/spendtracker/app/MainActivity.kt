package com.spendtracker.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spendtracker.app.data.UserPreferences
import com.spendtracker.app.ui.AppLockSession
import com.spendtracker.app.ui.BiometricLockScreen
import com.spendtracker.app.ui.SpendTrackerScreen
import com.spendtracker.app.ui.theme.SpendTrackerTheme

class MainActivity : AppCompatActivity() {
    private val container by lazy { AppContainer(applicationContext) }

    // Session authentication state. Kept in a ViewModel so it survives rotation and other
    // configuration changes (which recreate this Activity); resets on a fresh launch.
    private val lockSession: AppLockSession by viewModels()

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
            val isAppAccessible = !isBiometricEnabled || lockSession.isUnlocked

            LaunchedEffect(isBiometricEnabled) {
                // Only auto-prompt once per session: after a rotation the prompt (if still open)
                // is restored by BiometricPrompt itself, otherwise the user taps "Unlock".
                if (isBiometricEnabled && !lockSession.isUnlocked && !lockSession.autoPromptShown) {
                    lockSession.markAutoPromptShown()
                    showBiometricPrompt()
                }
            }

            SpendTrackerTheme(darkTheme = isDark) {
                if (isAppAccessible) {
                    SpendTrackerScreen(container)
                } else {
                    BiometricLockScreen(
                        errorMessage = lockSession.errorMessage,
                        onAuthenticateClick = {
                            lockSession.errorMessage = null
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
            lockSession.markUnlocked()
            return
        }

        if (canAuth == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED) {
            lockSession.errorMessage = "No biometric or screen lock enrolled on this device. Please set up a fingerprint or PIN in device Settings."
            return
        }

        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    lockSession.markUnlocked()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                        errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON
                    ) {
                        lockSession.errorMessage = errString.toString()
                    }
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    lockSession.errorMessage = "Fingerprint not recognised. Please try again."
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
