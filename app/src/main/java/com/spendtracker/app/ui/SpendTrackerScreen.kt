@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.spendtracker.app.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.spendtracker.app.AppContainer
import com.spendtracker.app.data.TransactionType
import com.spendtracker.app.domain.TransactionItem
import java.math.BigDecimal
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.material3.ExperimentalMaterial3Api

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpendTrackerScreen(container: AppContainer) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val analyticsVm: AnalyticsViewModel = viewModel(factory = container.viewModelFactory)
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

    Scaffold(
        topBar = {
            HeaderBar(
                title = "Spend Tracker",
                subtitle = "Chase & HSBC · Nullified transfers",
                onAddClick = { showSheet = true },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        contentWindowInsets = WindowInsets.navigationBars
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(top = padding.calculateTopPadding())
                .fillMaxSize()
        ) {
            // Notification Permission Warning Banner if not granted
            AnimatedVisibility(visible = !hasNotificationPermission) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f)
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.NotificationsActive,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Notification Access Needed",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontSize = 13.sp
                            )
                            Text(
                                "SpendTracker needs permission to read bank spend alerts.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
                                fontSize = 11.sp
                            )
                        }
                        Button(
                            onClick = {
                                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Enable", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Navigation Tabs
            val tabs = listOf("📋 Transactions", "📊 Analytics", "⚖️ Compare")
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                tabs.forEachIndexed { index, tabTitle ->
                    SegmentedButton(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        shape = SegmentedButtonDefaults.itemShape(index, tabs.size)
                    ) {
                        Text(tabTitle, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
                }
            }

            when (selectedTab) {
                0 -> {
                    // Transactions Screen
                    TransactionsFeed(
                        transactions = transactionsList,
                        filterType = filterType,
                        onFilterChange = { filterType = it },
                        onTransactionClick = { editingTransaction = it },
                        onCategoryPillClick = { quickCategoryTx = it },
                        contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + 16.dp),
                        modifier = Modifier.weight(1f)
                    )
                }
                1 -> {
                    // Analytics Screen
                    AnalyticsScreen(
                        viewModel = analyticsVm,
                        contentPadding = PaddingValues(bottom = padding.calculateBottomPadding()),
                        modifier = Modifier.weight(1f)
                    )
                }
                2 -> {
                    // Compare Screen
                    ComparisonScreen(
                        contentPadding = PaddingValues(bottom = padding.calculateBottomPadding()),
                        modifier = Modifier.weight(1f)
                    )
                }
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
    filterType: String,
    onFilterChange: (String) -> Unit,
    onTransactionClick: (TransactionItem) -> Unit,
    onCategoryPillClick: (TransactionItem) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    // Exclude internal transfers and excludeFromSpending rows from spending/income totals
    val nonTransferTxs = transactions.filter { !it.excludeFromSpending && it.type != TransactionType.TRANSFER && it.categoryKey != "INTERNAL_TRANSFER" }
    val totalSpent = nonTransferTxs.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amountMinor }
    val totalIncome = nonTransferTxs.filter { it.type == TransactionType.INCOME }.sumOf { it.amountMinor }
    val transferCount = transactions.count { it.type == TransactionType.TRANSFER || it.categoryKey == "INTERNAL_TRANSFER" || it.excludeFromSpending }
    val transferVolume = transactions.filter { it.type == TransactionType.TRANSFER || it.categoryKey == "INTERNAL_TRANSFER" }.sumOf { it.amountMinor }

    // Account breakdown (Chase vs HSBC)
    val chaseTxs = nonTransferTxs.filter { it.source.contains("Chase", ignoreCase = true) || it.accountName.contains("Chase", ignoreCase = true) }
    val chaseSpent = chaseTxs.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amountMinor }
    val chaseIncome = chaseTxs.filter { it.type == TransactionType.INCOME }.sumOf { it.amountMinor }

    val hsbcTxs = nonTransferTxs.filter { it.source.contains("HSBC", ignoreCase = true) || it.accountName.contains("HSBC", ignoreCase = true) }
    val hsbcSpent = hsbcTxs.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amountMinor }
    val hsbcIncome = hsbcTxs.filter { it.type == TransactionType.INCOME }.sumOf { it.amountMinor }

    val filtered = when (filterType) {
        "EXPENSE" -> transactions.filter { it.type == TransactionType.EXPENSE && !it.excludeFromSpending }
        "INCOME" -> transactions.filter { it.type == TransactionType.INCOME && !it.excludeFromSpending }
        "TRANSFER" -> transactions.filter { it.type == TransactionType.TRANSFER || it.categoryKey == "INTERNAL_TRANSFER" || it.excludeFromSpending }
        else -> transactions
    }

    val dayGroups = remember(filtered) { groupTransactionsByDay(filtered) }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Hero Cash Flow card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Net Spending (Transfers Nullified)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFEDE9FE))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("🔄 Transfers Excluded", fontSize = 10.sp, color = Color(0xFF6D28D9), fontWeight = FontWeight.Bold)
                        }
                    }

                    Text(
                        text = formatMoney(totalSpent),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.ArrowDownward, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                            Text("Spent: ${formatMoney(totalSpent)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.ArrowUpward, contentDescription = null, tint = Color(0xFF059669), modifier = Modifier.size(16.dp))
                            Text("Income: +${formatMoney(totalIncome)}", style = MaterialTheme.typography.bodySmall, color = Color(0xFF059669), fontWeight = FontWeight.Medium)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.SwapHoriz, contentDescription = null, tint = Color(0xFF7C3AED), modifier = Modifier.size(16.dp))
                            Text("Transfers: ${formatMoney(transferVolume)}", style = MaterialTheme.typography.bodySmall, color = Color(0xFF7C3AED), fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }

        // Bank Source Breakdown (Chase and HSBC)
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Bank Accounts (Sources)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Chase Card
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp)),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E3A8A).copy(alpha = 0.08f))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF1D4ED8)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.AccountBalance, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                }
                                Text("Chase", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF1E3A8A))
                            }
                            Text(
                                "Spent: ${formatMoney(chaseSpent)}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "In: +${formatMoney(chaseIncome)}",
                                fontSize = 11.sp,
                                color = Color(0xFF059669),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // HSBC Card
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp)),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFDC2626).copy(alpha = 0.08f))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFDC2626)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.AccountBalance, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                }
                                Text("HSBC", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF991B1B))
                            }
                            Text(
                                "Spent: ${formatMoney(hsbcSpent)}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "In: +${formatMoney(hsbcIncome)}",
                                fontSize = 11.sp,
                                color = Color(0xFF059669),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        // Filter Pills
        item {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val filters = listOf(
                    "ALL" to "All (${transactions.size})",
                    "EXPENSE" to "Outflow",
                    "INCOME" to "Inflow",
                    "TRANSFER" to "🔄 Transfers (${transferCount})"
                )
                items(filters) { (key, label) ->
                    FilterChip(
                        selected = filterType == key,
                        onClick = { onFilterChange(key) },
                        label = { Text(label, fontSize = 12.sp) },
                        shape = RoundedCornerShape(20.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }
        }

        // Transaction list items grouped by Day
        if (dayGroups.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No transactions found",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            dayGroups.forEach { group ->
                item(key = "day_header_${group.dayKey}") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = group.dayTitle,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (group.daySpendTotal > 0) {
                            Text(
                                text = "Spent: ${formatMoney(group.daySpendTotal)}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                items(group.items, key = { it.id }) { tx ->
                    TransactionRowItem(
                        tx = tx,
                        onClick = { onTransactionClick(tx) },
                        onCategoryClick = { onCategoryPillClick(tx) }
                    )
                }
            }
        }
    }
}

private data class DayGroup(
    val dayKey: String,
    val dayTitle: String,
    val daySpendTotal: Long,
    val items: List<TransactionItem>
)

private fun groupTransactionsByDay(transactions: List<TransactionItem>): List<DayGroup> {
    if (transactions.isEmpty()) return emptyList()

    val calNow = java.util.Calendar.getInstance()
    val todayYear = calNow.get(java.util.Calendar.YEAR)
    val todayDayOfYear = calNow.get(java.util.Calendar.DAY_OF_YEAR)

    calNow.add(java.util.Calendar.DAY_OF_YEAR, -1)
    val yestYear = calNow.get(java.util.Calendar.YEAR)
    val yestDayOfYear = calNow.get(java.util.Calendar.DAY_OF_YEAR)

    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val headerDateFormat = SimpleDateFormat("d MMM", Locale.getDefault())
    val fullDateFormat = SimpleDateFormat("EEEE, d MMM", Locale.getDefault())

    val grouped = linkedMapOf<String, MutableList<TransactionItem>>()
    for (tx in transactions) {
        val key = if (tx.timestamp > 0) dateFormat.format(Date(tx.timestamp)) else "unknown"
        grouped.getOrPut(key) { mutableListOf() }.add(tx)
    }

    return grouped.map { (key, items) ->
        val firstTimestamp = items.firstOrNull()?.timestamp ?: 0L
        val dayTitle = if (firstTimestamp > 0) {
            val calTx = java.util.Calendar.getInstance().apply { timeInMillis = firstTimestamp }
            val txYear = calTx.get(java.util.Calendar.YEAR)
            val txDayOfYear = calTx.get(java.util.Calendar.DAY_OF_YEAR)
            when {
                txYear == todayYear && txDayOfYear == todayDayOfYear -> "Today · ${headerDateFormat.format(Date(firstTimestamp))}"
                txYear == yestYear && txDayOfYear == yestDayOfYear -> "Yesterday · ${headerDateFormat.format(Date(firstTimestamp))}"
                else -> fullDateFormat.format(Date(firstTimestamp))
            }
        } else {
            "Other Transactions"
        }

        val daySpend = items
            .filter { it.type == TransactionType.EXPENSE && !it.excludeFromSpending && it.categoryKey != "INTERNAL_TRANSFER" }
            .sumOf { it.amountMinor }

        DayGroup(
            dayKey = key,
            dayTitle = dayTitle,
            daySpendTotal = daySpend,
            items = items
        )
    }
}

@Composable
private fun TransactionRowItem(
    tx: TransactionItem,
    onClick: () -> Unit,
    onCategoryClick: () -> Unit
) {
    val isTransfer = tx.type == TransactionType.TRANSFER || tx.categoryKey == "INTERNAL_TRANSFER" || tx.excludeFromSpending
    val isUnknownVendor = !isTransfer && (tx.merchant.isNullOrBlank() || tx.merchant.equals("Chase Payment", ignoreCase = true) || tx.merchant.equals("HSBC Payment", ignoreCase = true))

    val timeStr = if (tx.timestamp > 0) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(tx.timestamp))
    } else ""

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Category Emoji or Transfer icon
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isTransfer) Color(0xFFEDE9FE)
                        else parseColorHex(tx.categoryColorHex)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isTransfer) "🔄" else tx.categoryEmoji,
                    fontSize = 20.sp
                )
            }

            // Transaction Details
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (isUnknownVendor) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Unknown Vendor",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { onClick() }
                        ) {
                            Text(
                                "✏️ Tap to set",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                } else {
                    Text(
                        text = tx.merchant?.takeIf { it.isNotEmpty() }
                            ?: if (isTransfer) "${tx.source} ➔ ${tx.destinationAccountName ?: "HSBC"}" else "Transaction",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Source badge (Chase / HSBC)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                when {
                                    tx.source.contains("Chase", ignoreCase = true) -> Color(0xFFDBEAFE)
                                    tx.source.contains("HSBC", ignoreCase = true) -> Color(0xFFFEE2E2)
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                }
                            )
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = tx.source,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                tx.source.contains("Chase", ignoreCase = true) -> Color(0xFF1E40AF)
                                tx.source.contains("HSBC", ignoreCase = true) -> Color(0xFF991B1B)
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }

                    // Tappable Category pill
                    Surface(
                        color = if (isTransfer) Color(0xFFEDE9FE) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { if (!isTransfer) onCategoryClick() else onClick() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                text = if (isTransfer) "🔄 Internal Transfer" else "${tx.categoryEmoji} ${tx.categoryName}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isTransfer) Color(0xFF6D28D9) else MaterialTheme.colorScheme.onSurface
                            )
                            if (!isTransfer) {
                                Text("▼", fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    if (timeStr.isNotEmpty()) {
                        Text(
                            text = "· $timeStr",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Amount column
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = when {
                        isTransfer -> formatMoney(tx.amountMinor)
                        tx.type == TransactionType.INCOME -> "+${formatMoney(tx.amountMinor)}"
                        else -> "−${formatMoney(tx.amountMinor)}"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        isTransfer -> Color(0xFF7C3AED)
                        tx.type == TransactionType.INCOME -> Color(0xFF059669)
                        else -> Color(0xFFDC2626)
                    }
                )

                if (isTransfer || tx.excludeFromSpending) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFFEDE9FE))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(
                            "Nullified",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF6D28D9)
                        )
                    }
                }
            }
        }
    }
}
