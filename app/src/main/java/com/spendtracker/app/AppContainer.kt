package com.spendtracker.app

import android.content.Context
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.spendtracker.app.data.AppDatabase
import com.spendtracker.app.data.SpendRepository
import com.spendtracker.app.data.UserPreferences
import com.spendtracker.app.ui.AddTransactionViewModel
import com.spendtracker.app.ui.AnalyticsViewModel
import com.spendtracker.app.ui.SettingsViewModel
import com.spendtracker.app.ui.TransactionsViewModel

class AppContainer(context: Context) {
    val db = AppDatabase.get(context)
    val preferences = UserPreferences(context)
    val repository = SpendRepository(db.transactionDao(), db.accountDao(), db.catalogDao())

    val viewModelFactory = viewModelFactory {
        initializer { AnalyticsViewModel(repository) }
        initializer { AddTransactionViewModel(repository) }
        initializer { SettingsViewModel(repository, preferences) }
        initializer { TransactionsViewModel(repository) }
        initializer { com.spendtracker.app.ui.ComparisonViewModel(repository) }
    }
}
