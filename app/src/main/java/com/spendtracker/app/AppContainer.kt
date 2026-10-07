package com.spendtracker.app

import android.content.Context
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.spendtracker.app.data.AppDatabase
import com.spendtracker.app.data.SpendRepository
import com.spendtracker.app.ui.AddTransactionViewModel
import com.spendtracker.app.ui.AnalyticsViewModel

class AppContainer(context: Context) {
    val db = AppDatabase.get(context)
    val repository = SpendRepository(db.transactionDao(), db.accountDao())

    val viewModelFactory = viewModelFactory {
        initializer { AnalyticsViewModel(repository) }
        initializer { AddTransactionViewModel(repository) }
    }
}
