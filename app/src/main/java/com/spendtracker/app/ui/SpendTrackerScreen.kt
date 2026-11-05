@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.spendtracker.app.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.CompareArrows
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.spendtracker.app.AppContainer
import com.spendtracker.app.data.TransactionType
import com.spendtracker.app.data.normalizeBankName
import com.spendtracker.app.domain.TransactionItem
import com.spendtracker.app.ui.components.StatusPill
import com.spendtracker.app.ui.components.TransactionRowItem
import com.spendtracker.app.ui.components.groupTransactionsByDay
import com.spendtracker.app.ui.theme.SpendTrackerTheme
import com.spendtracker.app.ui.util.ColorUtils
import com.spendtracker.app.ui.util.CurrencyUtils
import com.spendtracker.app.ui.util.DateUtils
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpendTrackerScreen(container: AppContainer) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val analyticsVm: AnalyticsViewModel = viewModel(factory = container.viewModelFactory)
    val comparisonVm: ComparisonViewModel = viewModel(factory = container.viewModelFactory)
    val addVm: AddTransactionViewModel = viewModel(factory = container.viewModelFactory)
    val settingsVm: SettingsViewModel = viewModel(factory = container.viewModelFactory)
    val transactionsVm: TransactionsViewModel = viewModel(factory = container.viewModelFactory)

    val accounts by transactionsVm.accounts.collectAsStateWithLifecycle()
    val groups by transactionsVm.groups.collectAsStateWithLifecycle()
    val categories by transactionsVm.categories.collectAsStateWithLifecycle()
    val transactionsList by transactionsVm.transactions.collectAsStateWithLifecycle()
    val addState by addVm.state.collectAsStateWithLifecycle()

    var selectedTab by rememberSaveable { mutableStateOf(0) } // 0: Transactions, 1: Analytics, 2: Compare
    var showSheet by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showCategoryCreateSheet by rememberSaveable { mutableStateOf(false) }
    var editingTransaction by remember { mutableStateOf<TransactionItem?>(null) }
    var filterType by rememberSaveable { mutableStateOf("ALL") }

    // Check Notification Listener Permission
    var hasNotificationPermission by remember {
        mutableStateOf(NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName))
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasNotificationPermission = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var quickCategoryTx by remember { mutableStateOf<TransactionItem?>(null) }

    val deduplicatedAccounts = remember(accounts) {
        accounts
            .map { it.copy(name = normalizeBankName(it.name)) }
            .filter {
                val lower = it.name.lowercase(Locale.ROOT)
                lower != "others" && lower != "messages" && lower != "savings" && lower != "messaging"
            }
            .distinctBy { it.name.lowercase(Locale.ROOT) }
            .sortedBy { it.name }
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val tabs = remember { MainTab.values().toList() }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            HeaderBar(
                title = tabs[selectedTab].title,
                subtitle = if (deduplicatedAccounts.isEmpty()) {
                    "Spend Tracker"
                } else {
                    deduplicatedAccounts.take(3).joinToString(" · ") { it.name.trim() }
                },
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, tab ->
                    val selected = selectedTab == index
                    NavigationBarItem(
                        selected = selected,
                        onClick = { selectedTab = index },
                        icon = {
                            Icon(
                                if (selected) tab.selectedIcon else tab.icon,
                                contentDescription = null
                            )
                        },
                        label = { Text(tab.label) }
                    )
                }
            }
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = selectedTab == 0,
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut()
            ) {
                ExtendedFloatingActionButton(
                    onClick = { showSheet = true },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Add") }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            AnimatedVisibility(visible = !hasNotificationPermission) {
                NotificationAccessBanner(
                    onEnable = {
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    }
                )
            }

            when (selectedTab) {
                0 -> TransactionsFeed(
                    transactions = transactionsList,
                    accounts = deduplicatedAccounts,
                    filterType = filterType,
                    onFilterChange = { filterType = it },
                    onTransactionClick = { editingTransaction = it },
                    onCategoryPillClick = { quickCategoryTx = it },
                    // Leave room for the FAB so it never covers the last row.
                    contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
                    modifier = Modifier.weight(1f)
                )
                1 -> AnalyticsScreen(
                    viewModel = analyticsVm,
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.weight(1f)
                )
                2 -> ComparisonScreen(
                    viewModel = comparisonVm,
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    if (showSheet) {
        AddTransactionSheet(
            accounts = accounts,
            groups = groups,
            categories = categories,
            isSaving = addState.isSaving,
            errorMessage = addState.error,
            onDismiss = {
                addVm.clearError()
                showSheet = false
            },
            onEvaluateCategory = { merchant -> addVm.evaluateCategory(merchant) },
            onCreateCategory = { name, groupId, newGroupName, emoji, colorHex, type, ruleMerchant ->
                addVm.createCategory(name, groupId, newGroupName, emoji, colorHex, type, ruleMerchant) { }
            },
            onSubmit = { tx -> addVm.submit(tx) { showSheet = false } }
        )
    }

    editingTransaction?.let { tx ->
        EditTransactionSheet(
            transaction = tx,
            accounts = accounts,
            groups = groups,
            categories = categories,
            onDismiss = { editingTransaction = null },
            onSave = { id, type, accountId, destinationAccountId, amountMinor, categoryKey, merchant, note, excludeFromSpending, ruleMerchant ->
                transactionsVm.updateTransaction(
                    id = id,
                    type = type,
                    accountId = accountId,
                    destinationAccountId = destinationAccountId,
                    amountMinor = amountMinor,
                    categoryKey = categoryKey,
                    merchant = merchant,
                    note = note,
                    excludeFromSpending = excludeFromSpending,
                    ruleMerchant = ruleMerchant
                ) {
                    editingTransaction = null
                }
            },
            onDelete = { id ->
                transactionsVm.deleteTransaction(id) {
                    editingTransaction = null
                }
            }
        )
    }

    quickCategoryTx?.let { tx ->
        CategoryPickerDialog(
            groups = groups,
            categories = categories,
            selectedCategoryKey = tx.categoryKey,
            currentMerchant = tx.merchant?.takeIf { it.isNotEmpty() },
            transactionType = tx.type,
            onDismiss = { quickCategoryTx = null },
            onCategorySelected = { cat ->
                transactionsVm.quickSetCategory(tx.id, cat.key, tx.merchant?.takeIf { it.isNotEmpty() })
                quickCategoryTx = null
            },
            onCreateCategory = { name, groupId, newGroupName, emoji, colorHex, type, ruleMerchant ->
                settingsVm.addCategory(name, groupId, newGroupName, emoji, colorHex, type, ruleMerchant)
                quickCategoryTx = null
            }
        )
    }

    if (showSettings) {
        SettingsSheet(
            viewModel = settingsVm,
            onDismiss = { showSettings = false },
            onOpenAddCategory = {
                showSettings = false
                showCategoryCreateSheet = true
            }
        )
    }

    if (showCategoryCreateSheet) {
        CategoryPickerSheet(
            groups = groups,
            categories = categories,
            selectedCategoryKey = null,
            currentMerchant = null,
            transactionType = TransactionType.EXPENSE,
            onDismiss = { showCategoryCreateSheet = false },
            onCategorySelected = { showCategoryCreateSheet = false },
            onCreateCategory = { name, groupId, newGroupName, emoji, colorHex, type, ruleMerchant ->
                settingsVm.addCategory(name, groupId, newGroupName, emoji, colorHex, type, ruleMerchant)
                showCategoryCreateSheet = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransactionsFeed(
    transactions: List<TransactionItem>,
    accounts: List<com.spendtracker.app.data.AccountEntity>,
    filterType: String,
    onFilterChange: (String) -> Unit,
    onTransactionClick: (TransactionItem) -> Unit,
    onCategoryPillClick: (TransactionItem) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    // Exclude internal transfers and excludeFromSpending rows from spending/income totals
    val visibleTransactions = remember(transactions) {
        transactions.filter { it.type != TransactionType.TRANSFER && it.categoryKey != "INTERNAL_TRANSFER" }
    }
    val nonTransferTxs = visibleTransactions.filter { !it.excludeFromSpending }

    // Start of Current Month: spending resets every month, while salary & balance carry over
    val startOfMonth = remember { DateUtils.getStartOfMonthTimestamp() }
    val currentMonthName = remember { DateUtils.formatCurrentMonthName() }

    // Monthly Reset: Current month spending and income
    val currentMonthTxs = remember(nonTransferTxs, startOfMonth) {
        nonTransferTxs.filter { it.timestamp >= startOfMonth }
    }
    val monthSpent = remember(currentMonthTxs) {
        currentMonthTxs.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amountMinor }
    }
    val monthIncome = remember(currentMonthTxs) {
        currentMonthTxs.filter { it.type == TransactionType.INCOME }.sumOf { it.amountMinor }
    }

    // Carry Over Salary & Balance: unspent salary/funds from previous months
    val priorTxs = remember(nonTransferTxs, startOfMonth) {
        nonTransferTxs.filter { it.timestamp < startOfMonth }
    }
    val priorIncome = remember(priorTxs) {
        priorTxs.filter { it.type == TransactionType.INCOME }.sumOf { it.amountMinor }
    }
    val priorSpent = remember(priorTxs) {
        priorTxs.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amountMinor }
    }
    val carriedOverSalary = priorIncome - priorSpent

    // Total available net balance: carried salary/savings + this month's income - this month's spending
    val netRemaining = carriedOverSalary + monthIncome - monthSpent

    val deduplicatedAccounts = accounts

    val filtered = when {
        filterType == "EXPENSE" -> visibleTransactions.filter { it.type == TransactionType.EXPENSE && !it.excludeFromSpending }
        filterType == "INCOME" -> visibleTransactions.filter { it.type == TransactionType.INCOME && !it.excludeFromSpending }
        filterType.startsWith("BANK_") -> {
            val bankKey = filterType.removePrefix("BANK_").trim().lowercase(Locale.ROOT)
            visibleTransactions.filter {
                normalizeBankName(it.accountName).lowercase(Locale.ROOT) == bankKey ||
                normalizeBankName(it.source).lowercase(Locale.ROOT) == bankKey
            }
        }
        else -> visibleTransactions
    }

    val dayGroups = remember(filtered) { groupTransactionsByDay(filtered) }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        item(key = "hero") {
            MonthSummaryCard(
                monthName = currentMonthName,
                monthSpent = monthSpent,
                monthIncome = monthIncome,
                carriedOver = carriedOverSalary,
                netRemaining = netRemaining,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        if (deduplicatedAccounts.isNotEmpty()) {
            item(key = "accounts_header") { SectionHeader("Accounts") }
            item(key = "accounts") {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(deduplicatedAccounts, key = { it.id }) { acc ->
                        val cleanName = normalizeBankName(acc.name)
                        val accTxs = nonTransferTxs.filter {
                            normalizeBankName(it.source).equals(cleanName, ignoreCase = true) ||
                                normalizeBankName(it.accountName).equals(cleanName, ignoreCase = true)
                        }
                        val accCurrentTxs = accTxs.filter { it.timestamp >= startOfMonth }
                        val accSpent = accCurrentTxs.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amountMinor }
                        val accIncome = accCurrentTxs.filter { it.type == TransactionType.INCOME }.sumOf { it.amountMinor }
                        val accPriorIncome = accTxs.filter { it.timestamp < startOfMonth && it.type == TransactionType.INCOME }.sumOf { it.amountMinor }
                        val accPriorSpent = accTxs.filter { it.timestamp < startOfMonth && it.type == TransactionType.EXPENSE }.sumOf { it.amountMinor }
                        val accRemaining = (accPriorIncome - accPriorSpent) + accIncome - accSpent

                        AccountCard(
                            name = cleanName,
                            spent = accSpent,
                            income = accIncome,
                            remaining = accRemaining
                        )
                    }
                }
            }
        }

        item(key = "filters") {
            val baseFilters = listOf(
                "ALL" to "All · ${visibleTransactions.size}",
                "EXPENSE" to "Money out",
                "INCOME" to "Money in"
            )
            val bankFilters = deduplicatedAccounts.map { acc ->
                val cleanName = normalizeBankName(acc.name)
                val count = visibleTransactions.count {
                    normalizeBankName(it.accountName).equals(cleanName, ignoreCase = true) ||
                        normalizeBankName(it.source).equals(cleanName, ignoreCase = true)
                }
                "BANK_${cleanName.lowercase(Locale.ROOT)}" to "$cleanName · $count"
            }
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp, bottom = 4.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(baseFilters + bankFilters, key = { it.first }) { (key, label) ->
                    val selected = filterType == key
                    FilterChip(
                        selected = selected,
                        onClick = { onFilterChange(key) },
                        label = { Text(label, maxLines = 1, softWrap = false) },
                        leadingIcon = if (selected) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                        } else null
                    )
                }
            }
        }

        if (dayGroups.isEmpty()) {
            item(key = "empty") { EmptyTransactions() }
        } else {
            dayGroups.forEach { group ->
                item(key = "day_header_${group.dayKey}") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = group.dayTitle,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (group.daySpendTotal > 0) {
                            Text(
                                text = CurrencyUtils.formatMinor(group.daySpendTotal),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                val lastIndex = group.items.lastIndex
                itemsIndexed(group.items, key = { _, tx -> tx.id }) { index, tx ->
                    TransactionRowItem(
                        tx = tx,
                        onClick = { onTransactionClick(tx) },
                        onCategoryClick = { onCategoryPillClick(tx) },
                        isFirst = index == 0,
                        isLast = index == lastIndex
                    )
                }
            }
        }
    }
}

private enum class MainTab(
    val label: String,
    val title: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector
) {
    TRANSACTIONS("Activity", "Activity", Icons.Outlined.ReceiptLong, Icons.Filled.ReceiptLong),
    ANALYTICS("Insights", "Insights", Icons.Outlined.Insights, Icons.Filled.Insights),
    COMPARE("Compare", "Compare", Icons.Outlined.CompareArrows, Icons.Filled.CompareArrows)
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 12.dp)
    )
}

@Composable
private fun NotificationAccessBanner(onEnable: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Outlined.NotificationsActive, contentDescription = null)
            Column(modifier = Modifier.weight(1f)) {
                Text("Turn on notification access", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Needed to capture spend alerts from your banking apps.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            TextButton(onClick = onEnable) { Text("Enable") }
        }
    }
}

/**
 * Hero card: this month's spend as the headline figure, with money in / out and
 * the running balance (prior months' carry-over + this month's net) beneath it.
 */
@Composable
private fun MonthSummaryCard(
    monthName: String,
    monthSpent: Long,
    monthIncome: Long,
    carriedOver: Long,
    netRemaining: Long,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Spent in $monthName",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
                StatusPill(isPositive = netRemaining >= 0, amountMinor = kotlin.math.abs(netRemaining))
            }

            Spacer(Modifier.height(8.dp))

            // Full-width, single line, auto-shrinking so large amounts never truncate.
            AutoSizeAmount(
                text = CurrencyUtils.formatMinor(monthSpent),
                style = MaterialTheme.typography.displaySmall
            )

            Spacer(Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SummaryStat(
                    icon = Icons.Default.ArrowDownward,
                    label = "Money in",
                    value = CurrencyUtils.formatMinor(monthIncome),
                    modifier = Modifier.weight(1f)
                )
                SummaryStat(
                    icon = Icons.Default.ArrowUpward,
                    label = "Money out",
                    value = CurrencyUtils.formatMinor(monthSpent),
                    modifier = Modifier.weight(1f)
                )
            }

            if (carriedOver != 0L) {
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f))
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Carried over from earlier months",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Text(
                        CurrencyUtils.formatSignedMinor(carriedOver),
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryStat(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            }
            Column {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.titleSmall, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Single-line text that steps its font size down until it fits the available width. */
@Composable
private fun AutoSizeAmount(text: String, style: TextStyle) {
    var scaledStyle by remember(text, style) { mutableStateOf(style) }
    Text(
        text = text,
        style = scaledStyle,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier.fillMaxWidth(),
        onTextLayout = { result ->
            if (result.didOverflowWidth && scaledStyle.fontSize.value > 18f) {
                scaledStyle = scaledStyle.copy(fontSize = scaledStyle.fontSize * 0.9f)
            }
        }
    )
}

@Composable
private fun AccountCard(
    name: String,
    spent: Long,
    income: Long,
    remaining: Long
) {
    val (_, brand) = ColorUtils.getBankBrandColors(name)
    val finance = SpendTrackerTheme.finance
    OutlinedCard(
        modifier = Modifier.width(184.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(brand.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.AccountBalance, contentDescription = null, tint = brand, modifier = Modifier.size(18.dp))
                }
                Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(8.dp))
            Text("Balance", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = CurrencyUtils.formatSignedMinor(remaining),
                style = MaterialTheme.typography.titleLarge,
                color = if (remaining >= 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "+${CurrencyUtils.formatMinor(income)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = finance.income,
                    maxLines = 1,
                    softWrap = false
                )
                Text(
                    "−${CurrencyUtils.formatMinor(spent)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
private fun EmptyTransactions() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.ReceiptLong,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp)
            )
        }
        Text("No transactions yet", style = MaterialTheme.typography.titleMedium)
        Text(
            "Spend alerts from your bank will appear here, or tap Add to log one.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
