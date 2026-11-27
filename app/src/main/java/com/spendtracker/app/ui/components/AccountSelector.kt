package com.spendtracker.app.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.spendtracker.app.data.AccountEntity
import com.spendtracker.app.data.normalizeBankName
import java.util.Locale

/**
 * Shared account dropdown for the Add and Edit transaction sheets: a read-only field with a
 * trailing arrow, the same control the Recurring sheet uses for its dropdowns.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountDropdownSelector(
    accounts: List<AccountEntity>,
    selected: AccountEntity?,
    onAccountSelected: (AccountEntity) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Account"
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val uniqueAccounts = remember(accounts) {
        accounts
            .map { it.copy(name = normalizeBankName(it.name)) }
            .distinctBy { it.name.lowercase(Locale.ROOT) }
            .sortedBy { it.name }
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selected?.name?.trim() ?: "Select account",
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            uniqueAccounts.forEach { account ->
                DropdownMenuItem(
                    text = { Text(account.name.trim()) },
                    onClick = {
                        onAccountSelected(account)
                        expanded = false
                    }
                )
            }
        }
    }
}
