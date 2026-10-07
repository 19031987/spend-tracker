package com.spendtracker.app.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.spendtracker.app.AppContainer

@Composable
fun SpendTrackerScreen(container: AppContainer) {
    val analyticsVm: AnalyticsViewModel = viewModel(factory = container.viewModelFactory)
    val addVm: AddTransactionViewModel = viewModel(factory = container.viewModelFactory)
    val accounts by addVm.accounts.collectAsStateWithLifecycle()
    val addState by addVm.state.collectAsStateWithLifecycle()
    var showSheet by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            HeaderBar(
                title = "Spend Tracker",
                subtitle = "Insights & transfers",
                onAddClick = { showSheet = true },
                actions = {
                    IconButton(onClick = { /* Settings action */ }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        contentWindowInsets = WindowInsets.navigationBars
    ) { padding ->
        AnalyticsScreen(viewModel = analyticsVm, contentPadding = padding, modifier = Modifier)
    }

    if (showSheet) {
        AddTransactionSheet(
            accounts = accounts,
            isSaving = addState.isSaving,
            errorMessage = addState.error,
            onDismiss = {
                addVm.clearError()
                showSheet = false
            },
            onSubmit = { tx -> addVm.submit(tx) { showSheet = false } }
        )
    }
}
