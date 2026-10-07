package com.spendtracker.app.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.spendtracker.app.AppContainer

@Composable
fun SpendTrackerScreen(container: AppContainer) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val analyticsVm: AnalyticsViewModel = viewModel(factory = container.viewModelFactory)
    val addVm: AddTransactionViewModel = viewModel(factory = container.viewModelFactory)
    val settingsVm: SettingsViewModel = viewModel(factory = container.viewModelFactory)

    val accounts by addVm.accounts.collectAsStateWithLifecycle()
    val groups by addVm.groups.collectAsStateWithLifecycle()
    val categories by addVm.categories.collectAsStateWithLifecycle()
    val addState by addVm.state.collectAsStateWithLifecycle()

    var showSheet by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showCategoryCreateSheet by rememberSaveable { mutableStateOf(false) }

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

    Scaffold(
        topBar = {
            HeaderBar(
                title = "Spend Tracker",
                subtitle = "Insights & transfers",
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
        Column(modifier = Modifier.padding(top = padding.calculateTopPadding())) {
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

            AnalyticsScreen(
                viewModel = analyticsVm,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = padding.calculateBottomPadding()),
                modifier = Modifier.weight(1f)
            )
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
            transactionType = com.spendtracker.app.data.TransactionType.EXPENSE,
            onDismiss = { showCategoryCreateSheet = false },
            onCategorySelected = { showCategoryCreateSheet = false },
            onCreateCategory = { name, groupId, newGroupName, emoji, colorHex, type, ruleMerchant ->
                settingsVm.addCategory(name, groupId, newGroupName, emoji, colorHex, type, ruleMerchant)
                showCategoryCreateSheet = false
            }
        )
    }
}
