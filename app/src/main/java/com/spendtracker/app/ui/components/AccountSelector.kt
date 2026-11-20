package com.spendtracker.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.spendtracker.app.data.AccountEntity
import com.spendtracker.app.data.normalizeBankName
import java.util.Locale

/**
 * Shared account/source dropdown selector for Add and Edit transaction sheets.
 */
@Composable
fun AccountDropdownSelector(
    accounts: List<AccountEntity>,
    selected: AccountEntity?,
    onAccountSelected: (AccountEntity) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Account (Source)"
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val uniqueAccounts = remember(accounts) {
        accounts
            .map { it.copy(name = normalizeBankName(it.name)) }
            .distinctBy { it.name.lowercase(Locale.ROOT) }
            .sortedBy { it.name }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = selected?.name?.trim() ?: "Select account",
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
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
}
