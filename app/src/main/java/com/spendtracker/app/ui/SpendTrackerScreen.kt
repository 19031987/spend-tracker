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
import com.spendtracker.app.data.normalizeBankName
import com.spendtracker.app.domain.TransactionItem
import com.spendtracker.app.ui.components.DayGroup
import com.spendtracker.app.ui.components.StatusPill
import com.spendtracker.app.ui.components.TransactionRowItem
import com.spendtracker.app.ui.components.groupTransactionsByDay
import com.spendtracker.app.ui.util.ColorUtils
import com.spendtracker.app.ui.util.CurrencyUtils
import com.spendtracker.app.ui.util.DateUtils
import java.util.Date
import java.util.Locale
import androidx.compose.material3.ExperimentalMaterial3Api

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

    Scaffold(
        topBar = {
            HeaderBar(
                title = "Spend Tracker",
                subtitle = if (deduplicatedAccounts.isEmpty()) "Real-time cash flow & spend tracker" else deduplicatedAccounts.take(3).joinToString(" · ") { it.name.trim() },
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
                        shape = SegmentedButtonDefaults.itemShape(index, tabs.size),
                        icon = {}
                    ) {
                        Text(
                            tabTitle,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            when (selectedTab) {
                0 -> {
                    // Transactions Screen
                    TransactionsFeed(
                        transactions = transactionsList,
                        accounts = deduplicatedAccounts,
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
                        viewModel = comparisonVm,
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
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Hero Cash Flow card (Monthly Spending resets each month, Salary & Balance carry over)
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
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Top Row: Month Label & Saved/Available Pill (moved here so amount below has full width)
                    val isPositive = netRemaining >= 0

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$currentMonthName Spending",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        StatusPill(
                            isPositive = isPositive,
                            amountMinor = kotlin.math.abs(netRemaining)
                        )
                    }

                    // Main Amount Row: Dedicated full-width display so amount NEVER truncates into "34..."
                    Text(
                        text = CurrencyUtils.formatMinor(monthSpent),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Breakdown Tier 1: Dedicated Outflow and Inflow with generous breathing room
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.ArrowDownward, contentDescription = null, tint = ColorUtils.OutflowRed, modifier = Modifier.size(15.dp))
                            Text(
                                "Out: ${CurrencyUtils.formatMinor(monthSpent)}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.ArrowUpward, contentDescription = null, tint = ColorUtils.InflowGreen, modifier = Modifier.size(15.dp))
                            Text(
                                "In: +${CurrencyUtils.formatMinor(monthIncome)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = ColorUtils.InflowGreen,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    // Breakdown Tier 2: Dedicated Carried Over Salary / Balance row
                    if (carriedOverSalary != 0L) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Prior Balance Carried Over",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${if (carriedOverSalary > 0) "+" else ""}${CurrencyUtils.formatMinor(carriedOverSalary)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }

        // Bank Source Breakdown (Dynamically discovered and stored accounts)
        if (deduplicatedAccounts.isNotEmpty()) {
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

                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
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
                            val accCarriedOver = accPriorIncome - accPriorSpent
                            val accRemaining = accCarriedOver + accIncome - accSpent

                            val (cardBgColor, badgeColor) = ColorUtils.getBankBrandColors(cleanName)

                            Card(
                                modifier = Modifier
                                    .width(160.dp)
                                    .clip(RoundedCornerShape(14.dp)),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = cardBgColor)
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
                                                .background(badgeColor),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.AccountBalance, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                        }
                                        Text(cleanName, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    val isAccPositive = accRemaining >= 0
                                    Text(
                                        "Out: ${CurrencyUtils.formatMinor(accSpent)}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Normal,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                    Text(
                                        "In: +${CurrencyUtils.formatMinor(accIncome)}",
                                        fontSize = 11.sp,
                                        color = ColorUtils.InflowGreen,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                    Text(
                                        text = "${if (isAccPositive) "Rem: +" else "Rem: -"}${CurrencyUtils.formatMinor(kotlin.math.abs(accRemaining))}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isAccPositive) ColorUtils.InflowGreen else MaterialTheme.colorScheme.error,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Filter Pills (Dynamically includes newly discovered and active bank sources)
        item {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val baseFilters = listOf(
                    "ALL" to "All (${visibleTransactions.size})",
                    "EXPENSE" to "Outflow",
                    "INCOME" to "Inflow"
                )
                val bankFilters = deduplicatedAccounts.map { acc ->
                    val cleanName = normalizeBankName(acc.name)
                    val count = visibleTransactions.count {
                        normalizeBankName(it.accountName).equals(cleanName, ignoreCase = true) ||
                        normalizeBankName(it.source).equals(cleanName, ignoreCase = true)
                    }
                    "BANK_${cleanName.lowercase(Locale.ROOT)}" to "$cleanName ($count)"
                }
                val allFilters = baseFilters + bankFilters

                items(allFilters) { (key, label) ->
                    FilterChip(
                        selected = filterType == key,
                        onClick = { onFilterChange(key) },
                        label = { Text(label, fontSize = 12.sp, maxLines = 1, softWrap = false) },
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
                                text = "Spent: ${CurrencyUtils.formatMinor(group.daySpendTotal)}",
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
