package com.spendtracker.app.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spendtracker.app.ui.components.SheetHeader
import com.spendtracker.app.ui.settings.AccountsSection
import com.spendtracker.app.ui.settings.CategoriesSection
import com.spendtracker.app.ui.settings.DataSection
import com.spendtracker.app.ui.settings.PreferencesSection
import com.spendtracker.app.ui.settings.RulesSection
import com.spendtracker.app.ui.theme.ScreenPadding
import com.spendtracker.app.ui.theme.Spacing

/** Settings: categories, rules, accounts, preferences and data (sections live in `ui/settings`). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    viewModel: SettingsViewModel,
    onDismiss: () -> Unit,
    onOpenAddCategory: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val rules by viewModel.rules.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val accountCounts by viewModel.accountTransactionCounts.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(uiState.feedbackMessage) {
        val message = uiState.feedbackMessage ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        viewModel.clearFeedback()
    }

    val tabs = SettingsTab.values()
    val currentTab = uiState.selectedTab

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
        ) {
            SheetHeader(
                "Settings",
                subtitle = "Categories, rules, accounts and data",
                onClose = onDismiss,
                modifier = Modifier.padding(horizontal = ScreenPadding.sheetHorizontalPadding)
            )
            ScrollableTabRow(
                selectedTabIndex = currentTab.ordinal,
                edgePadding = ScreenPadding.sheetHorizontalPadding,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.sm)
            ) {
                tabs.forEach { tab ->
                    Tab(
                        selected = currentTab == tab,
                        onClick = { viewModel.selectTab(tab) },
                        text = { Text(tab.label, maxLines = 1) }
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (currentTab) {
                    SettingsTab.CATEGORIES -> CategoriesSection(
                        viewModel = viewModel,
                        groups = groups,
                        categories = categories,
                        onAddCategory = onOpenAddCategory
                    )
                    SettingsTab.RULES -> RulesSection(viewModel = viewModel, rules = rules, categories = categories)
                    SettingsTab.ACCOUNTS -> AccountsSection(
                        viewModel = viewModel,
                        accounts = accounts,
                        transactionCounts = accountCounts
                    )
                    SettingsTab.PREFERENCES -> PreferencesSection(viewModel = viewModel)
                    SettingsTab.DATA -> DataSection(
                        viewModel = viewModel,
                        uiState = uiState,
                        onResetComplete = onDismiss
                    )
                }
            }
        }
    }
}
