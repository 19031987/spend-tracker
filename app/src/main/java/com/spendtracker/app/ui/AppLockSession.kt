package com.spendtracker.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

/**
 * Holds the biometric unlock state for the current app session.
 *
 * Lives in a ViewModel so it survives configuration changes (screen rotation,
 * dark-mode switch, resizing) — the Activity is recreated on those, and keeping
 * the flag on the Activity made the lock screen reappear on every rotation.
 * It is deliberately NOT saved to the instance-state bundle, so a fresh launch
 * or process death still asks the user to unlock.
 */
class AppLockSession : ViewModel() {
    var isUnlocked by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)

    /** True once the automatic prompt has been shown, so a rotation doesn't stack a second prompt. */
    var autoPromptShown = false
        private set

    fun markUnlocked() {
        isUnlocked = true
        errorMessage = null
    }

    fun markAutoPromptShown() {
        autoPromptShown = true
    }
}
