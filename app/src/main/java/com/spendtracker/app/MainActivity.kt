package com.spendtracker.app

import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.NotificationManagerCompat
import com.spendtracker.app.data.CategorySpend
import com.spendtracker.app.data.MerchantSpend
import com.spendtracker.app.data.SpendDatabase
import com.spendtracker.app.data.TransactionRecord
import com.spendtracker.app.parser.SpendParser
import com.spendtracker.app.service.SpendNotificationListenerService
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var db: SpendDatabase

    private lateinit var tvTotalMonth: TextView
    private lateinit var tvTotalToday: TextView
    private lateinit var tvHsbcTotal: TextView
    private lateinit var tvPayPalTotal: TextView
    private lateinit var layoutCategories: LinearLayout
    private lateinit var layoutTopMerchants: LinearLayout
    private lateinit var layoutTransactions: LinearLayout
    private lateinit var tvEmptyState: TextView
    private lateinit var cardPermissionWarning: CardView
    private lateinit var btnSimulate: Button

    private val expenseReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refreshData()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        db = SpendDatabase(this)

        initViews()
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        checkPermission()
        refreshData()

        val filter = IntentFilter(SpendNotificationListenerService.ACTION_NEW_EXPENSE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(expenseReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(expenseReceiver, filter)
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(expenseReceiver)
        } catch (ignored: Exception) {}
    }

    private fun initViews() {
        tvTotalMonth = findViewById(R.id.tvTotalMonth)
        tvTotalToday = findViewById(R.id.tvTotalToday)
        tvHsbcTotal = findViewById(R.id.tvHsbcTotal)
        tvPayPalTotal = findViewById(R.id.tvPayPalTotal)
        layoutCategories = findViewById(R.id.layoutCategories)
        layoutTopMerchants = findViewById(R.id.layoutTopMerchants)
        layoutTransactions = findViewById(R.id.layoutTransactions)
        tvEmptyState = findViewById(R.id.tvEmptyState)
        cardPermissionWarning = findViewById(R.id.cardPermissionWarning)
        btnSimulate = findViewById(R.id.btnSimulate)
    }

    private fun setupListeners() {
        cardPermissionWarning.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        btnSimulate.setOnClickListener {
            showSimulationDialog()
        }
    }

    private fun checkPermission() {
        val isGranted = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
        cardPermissionWarning.visibility = if (isGranted) View.GONE else View.VISIBLE
    }

    private fun refreshData() {
        val totalMonth = db.getTotalMonth()
        val totalToday = db.getTotalToday()
        val sources = db.getSourceTotals()

        tvTotalMonth.text = String.format(Locale.UK, "£%.2f", totalMonth)
        tvTotalToday.text = String.format(Locale.UK, "Today's spend: £%.2f", totalToday)

        tvHsbcTotal.text = String.format(Locale.UK, "£%.2f", sources["HSBC"] ?: 0.0)
        tvPayPalTotal.text = String.format(Locale.UK, "£%.2f", sources["PayPal"] ?: 0.0)

        renderCategories(db.getCategoryTotals())
        renderTopMerchants(db.getTopMerchants())
        renderTransactions(db.getAllTransactions())
    }

    private fun renderCategories(categories: List<CategorySpend>) {
        layoutCategories.removeAllViews()
        if (categories.isEmpty()) {
            val emptyTv = TextView(this).apply {
                text = "No category data yet"
                setTextColor(Color.parseColor("#94A3B8"))
                textSize = 13f
            }
            layoutCategories.addView(emptyTv)
            return
        }

        for (cat in categories) {
            val itemView = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 0, 0, 16)
            }

            val header = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                weightSum = 1f
            }

            val icon = when (cat.category) {
                "Groceries" -> "🛒"
                "Dining & Drinks" -> "☕"
                "Transport & Fuel" -> "🚗"
                "Bills & Subscriptions" -> "🍿"
                "Shopping" -> "🛍️"
                "Entertainment" -> "🎮"
                else -> "🏷️"
            }

            val titleView = TextView(this).apply {
                text = "$icon ${cat.category} (${cat.count})"
                setTextColor(Color.WHITE)
                textSize = 14f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.6f)
            }

            val amountView = TextView(this).apply {
                text = String.format(Locale.UK, "£%.2f (%d%%)", cat.total, cat.percentage)
                setTextColor(Color.parseColor("#38BDF8"))
                textSize = 14f
                gravity = Gravity.END
                setTypeface(null, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.4f)
            }

            header.addView(titleView)
            header.addView(amountView)

            val progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 100
                setProgress(cat.percentage)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 16).apply {
                    topMargin = 8
                }
            }

            itemView.addView(header)
            itemView.addView(progress)
            layoutCategories.addView(itemView)
        }
    }

    private fun renderTopMerchants(merchants: List<MerchantSpend>) {
        layoutTopMerchants.removeAllViews()
        if (merchants.isEmpty()) {
            val emptyTv = TextView(this).apply {
                text = "No merchant data yet"
                setTextColor(Color.parseColor("#94A3B8"))
                textSize = 13f
            }
            layoutTopMerchants.addView(emptyTv)
            return
        }

        for (m in merchants) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                weightSum = 1f
                setPadding(0, 6, 0, 6)
            }

            val nameTv = TextView(this).apply {
                text = "📍 ${m.merchant}"
                setTextColor(Color.WHITE)
                textSize = 13f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.7f)
            }

            val amtTv = TextView(this).apply {
                text = String.format(Locale.UK, "£%.2f", m.total)
                setTextColor(Color.parseColor("#F8FAFC"))
                setTypeface(null, Typeface.BOLD)
                textSize = 13f
                gravity = Gravity.END
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.3f)
            }

            row.addView(nameTv)
            row.addView(amtTv)
            layoutTopMerchants.addView(row)
        }
    }

    private fun renderTransactions(txns: List<TransactionRecord>) {
        layoutTransactions.removeAllViews()
        if (txns.isEmpty()) {
            tvEmptyState.visibility = View.VISIBLE
            return
        }
        tvEmptyState.visibility = View.GONE

        for (t in txns) {
            val card = CardView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = 10
                }
                radius = 8f
                setCardBackgroundColor(Color.parseColor("#131D31"))
                setContentPadding(16, 14, 16, 14)
            }

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                weightSum = 1f
            }

            val details = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.7f)
            }

            val sourceTag = if (t.source == "HSBC") "🔴 HSBC" else "🔵 PayPal"
            val titleTv = TextView(this).apply {
                text = "${t.merchant}"
                setTextColor(Color.WHITE)
                textSize = 15f
                setTypeface(null, Typeface.BOLD)
            }

            val subTv = TextView(this).apply {
                text = "$sourceTag • ${t.category} • ${t.formattedTime}"
                setTextColor(Color.parseColor("#94A3B8"))
                textSize = 12f
            }

            details.addView(titleTv)
            details.addView(subTv)

            val amountTv = TextView(this).apply {
                text = String.format(Locale.UK, "-%s%.2f", t.currency, t.amount)
                setTextColor(Color.parseColor("#F8FAFC"))
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0.3f)
            }

            row.addView(details)
            row.addView(amountTv)
            card.addView(row)
            layoutTransactions.addView(card)
        }
    }

    private fun showSimulationDialog() {
        val options = arrayOf(
            "🔴 HSBC: You spent £14.80 at TESCO STORES",
            "🔴 HSBC: Card ending 8219 spent £4.20 at COSTA COFFEE",
            "🔴 HSBC: Transaction of £55.00 at SHELL PETROL",
            "🔵 PayPal: You paid £29.99 to Steam Games",
            "🔵 PayPal: Payment of £8.99 to NETFLIX",
            "🔵 PayPal: You paid £18.50 to Uber Eats"
        )

        AlertDialog.Builder(this)
            .setTitle("Simulate Spending Notification")
            .setItems(options) { _, which ->
                val (pkg, title, text) = when (which) {
                    0 -> Triple("uk.co.hsbc.hsbcukmobilebanking", "HSBC Card Activity", "You spent £14.80 at TESCO STORES on 07/09")
                    1 -> Triple("uk.co.hsbc.hsbcukmobilebanking", "HSBC Spend Alert", "Card ending 8219 spent £4.20 at COSTA COFFEE")
                    2 -> Triple("uk.co.hsbc.hsbcukmobilebanking", "HSBC Transaction Alert", "Transaction of £55.00 at SHELL PETROL")
                    3 -> Triple("com.paypal.android.p2pmobile", "PayPal", "You paid £29.99 to Steam Games")
                    4 -> Triple("com.paypal.android.p2pmobile", "PayPal Payment", "Payment of £8.99 to NETFLIX")
                    else -> Triple("com.paypal.android.p2pmobile", "PayPal", "You paid £18.50 to Uber Eats")
                }

                val parsed = SpendParser.parse(pkg, title, text)
                if (parsed != null) {
                    db.insertExpense(parsed)
                    refreshData()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
