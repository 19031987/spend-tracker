package com.spendtracker.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spendtracker.app.domain.Period
import com.spendtracker.app.ui.SettingsViewModel
import com.spendtracker.app.ui.components.AppCard
import com.spendtracker.app.ui.components.SectionHeader
import com.spendtracker.app.ui.components.SheetContainer
import com.spendtracker.app.ui.theme.Spacing

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChoiceChips(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        options.forEach { (value, label) ->
            ChoiceChip(label = label, selected = value == selected, onClick = { onSelect(value) })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

@Composable
fun PreferencesSection(viewModel: SettingsViewModel) {
    var currency by rememberSaveable { mutableStateOf(viewModel.getCurrentCurrency()) }
    var period by rememberSaveable { mutableStateOf(viewModel.getDefaultPeriod()) }
    var theme by rememberSaveable { mutableStateOf(viewModel.getThemeMode()) }
    val isBiometricOn by viewModel.isBiometricLockEnabled.collectAsStateWithLifecycle(initialValue = viewModel.isBiometricEnabled())

    SheetContainer(modifier = Modifier.padding(top = Spacing.lg)) {
        AppCard(verticalSpacing = Spacing.md) {
            SectionHeader("Currency symbol", subtitle = "Shown on totals and transactions")
            ChoiceChips(
                options = listOf("£", "$", "€", "¥").map { it to it },
                selected = currency,
                onSelect = {
                    currency = it
                    viewModel.setCurrency(it)
                }
            )
        }

        AppCard(verticalSpacing = Spacing.md) {
            SectionHeader("Default analytics period")
            ChoiceChips(
                options = Period.values().map { it to it.label },
                selected = period,
                onSelect = {
                    period = it
                    viewModel.setDefaultPeriod(it)
                }
            )
        }

        AppCard(verticalSpacing = Spacing.md) {
            SectionHeader("Theme")
            ChoiceChips(
                options = listOf("SYSTEM" to "System", "LIGHT" to "Light", "DARK" to "Dark"),
                selected = theme,
                onSelect = {
                    theme = it
                    viewModel.setThemeMode(it)
                }
            )
        }

        AppCard {
            SettingsListRow(
                title = "Biometric lock",
                supporting = "Require fingerprint or face unlock to open the app",
                leading = {
                    Icon(Icons.Filled.Fingerprint, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                },
                trailing = {
                    Switch(checked = isBiometricOn, onCheckedChange = { viewModel.setBiometricEnabled(it) })
                }
            )
        }
    }
}
