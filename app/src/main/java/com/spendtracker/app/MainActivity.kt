package com.spendtracker.app

import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioGroup
import android.widget.Spinner
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var db: SpendDatabase

    // Monarch Cash Flow Views
    private lateinit var tvNetCashFlow: TextView
    private lateinit var tvCashFlowStatus: TextView
    private lateinit var tvNetSubtitle: TextView
    private lateinit var tvMoneyInMonth: TextView
    private lateinit var tvMoneyInToday: TextView
    private lateinit var tvMoneyOutMonth: TextView
    private lateinit var tvMoneyOutToday: TextView
    private lateinit var progressCashFlow: ProgressBar
    private lateinit var tvCashFlowRatio: TextView

    // Source Breakdown Cards
    private lateinit var tvHsbcOut: TextView
    private lateinit var tvHsbcIn: TextView
    private lateinit var tvPayPalOut: TextView
    private lateinit var tvPayPalIn: TextView

    // Dynamic Lists & Containers
    private lateinit var layoutCategories: LinearLayout
    private lateinit var layoutTopMerchants: LinearLayout
    private lateinit var layoutTransactions: LinearLayout
    private lateinit var tvEmptyState: TextView
    private lateinit var cardPermissionWarning: CardView

    // Action Buttons & Filters
    private lateinit var btnAddTransaction: Button
    private lateinit var btnSimulate: Button
    private lateinit var btnFilterAll: TextView
    private lateinit var btnFilterOut: TextView
    private lateinit var btnFilterIn: TextView

    private var currentFilter: String? = null // null: All, "OUT": Expenses, "IN": Income

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
        // Run data sanitization on resume to automatically clean up duplicate notification logs
        // and fix previously inverted signs (e.g. £23.00 added funds)
        db.cleanupAndRepairData()
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
        tvNetCashFlow = findViewById(R.id.tvNetCashFlow)
        tvCashFlowStatus = findViewById(R.id.tvCashFlowStatus)
        tvNetSubtitle = findViewById(R.id.tvNetSubtitle)
        tvMoneyInMonth = findViewById(R.id.tvMoneyInMonth)
        tvMoneyInToday = findViewById(R.id.tvMoneyInToday)
        tvMoneyOutMonth = findViewById(R.id.tvMoneyOutMonth)
        tvMoneyOutToday = findViewById(R.id.tvMoneyOutToday)
        progressCashFlow = findViewById(R.id.progressCashFlow)
        tvCashFlowRatio = findViewById(R.id.tvCashFlowRatio)

        tvHsbcOut = findViewById(R.id.tvHsbcOut)
        tvHsbcIn = findViewById(R.id.tvHsbcIn)
        tvPayPalOut = findViewById(R.id.tvPayPalOut)
        tvPayPalIn = findViewById(R.id.tvPayPalIn)

        layoutCategories = findViewById(R.id.layoutCategories)
        layoutTopMerchants = findViewById(R.id.layoutTopMerchants)
        layoutTransactions = findViewById(R.id.layoutTransactions)
        tvEmptyState = findViewById(R.id.tvEmptyState)
        cardPermissionWarning = findViewById(R.id.cardPermissionWarning)

        btnAddTransaction = findViewById(R.id.btnAddTransaction)
        btnSimulate = findViewById(R.id.btnSimulate)
        btnFilterAll = findViewById(R.id.btnFilterAll)
        btnFilterOut = findViewById(R.id.btnFilterOut)
        btnFilterIn = findViewById(R.id.btnFilterIn)
    }

    private fun setupListeners() {
        cardPermissionWarning.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        btnAddTransaction.setOnClickListener {
            showAddTransactionDialog()
        }

        btnSimulate.setOnClickListener {
            showSimulationDialog()
        }

        btnFilterAll.setOnClickListener {
            currentFilter = null
            updateFilterUi()
            refreshData()
        }

        btnFilterOut.setOnClickListener {
            currentFilter = "OUT"
            updateFilterUi()
            refreshData()
        }

        btnFilterIn.setOnClickListener {
            currentFilter = "IN"
            updateFilterUi()
            refreshData()
        }
    }

    private fun updateFilterUi() {
        val activeBg = R.drawable.bg_pill_active
        val inactiveBg = R.drawable.bg_pill_inactive

        btnFilterAll.setBackgroundResource(if (currentFilter == null) activeBg else inactiveBg)
        btnFilterAll.setTextColor(if (currentFilter == null) Color.WHITE else Color.parseColor("#94A3B8"))

        btnFilterOut.setBackgroundResource(if (currentFilter == "OUT") activeBg else inactiveBg)
        btnFilterOut.setTextColor(if (currentFilter == "OUT") Color.parseColor("#F43F5E") else Color.parseColor("#94A3B8"))

        btnFilterIn.setBackgroundResource(if (currentFilter == "IN") activeBg else inactiveBg)
        btnFilterIn.setTextColor(if (currentFilter == "IN") Color.parseColor("#10B981") else Color.parseColor("#94A3B8"))
    }

    private fun checkPermission() {
        val isGranted = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
        cardPermissionWarning.visibility = if (isGranted) View.GONE else View.VISIBLE
    }

    private fun refreshData() {
        val moneyInMonth = db.getTotalMoneyInMonth()
        val moneyOutMonth = db.getTotalMoneyOutMonth()
        val netCashFlow = moneyInMonth - moneyOutMonth

        val moneyInToday = db.getTotalMoneyInToday()
        val moneyOutToday = db.getTotalMoneyOutToday()

        // 1. Monarch Hero Cash Flow Display
        if (netCashFlow >= 0) {
            tvNetCashFlow.text = String.format(Locale.UK, "+£%.2f", netCashFlow)
            tvNetCashFlow.setTextColor(Color.parseColor("#10B981"))
            tvCashFlowStatus.text = "✓ Positive Flow"
            tvCashFlowStatus.setTextColor(Color.parseColor("#10B981"))
            tvNetSubtitle.text = "Net savings this month"
        } else {
            tvNetCashFlow.text = String.format(Locale.UK, "-£%.2f", Math.abs(netCashFlow))
            tvNetCashFlow.setTextColor(Color.parseColor("#F43F5E"))
            tvCashFlowStatus.text = "⚠ Net Deficit"
            tvCashFlowStatus.setTextColor(Color.parseColor("#F43F5E"))
            tvNetSubtitle.text = "Outflow exceeds inflow this month"
        }

        // Money In (Inflow / Deposits: Positive Emerald Green)
        tvMoneyInMonth.text = String.format(Locale.UK, "+£%.2f", moneyInMonth)
        tvMoneyInToday.text = String.format(Locale.UK, "Today: +£%.2f", moneyInToday)

        // Money Out (Outflow / Expenses: Distinct Negative / Rose)
        tvMoneyOutMonth.text = String.format(Locale.UK, "-£%.2f", moneyOutMonth)
        tvMoneyOutToday.text = String.format(Locale.UK, "Today: -£%.2f", moneyOutToday)

        // Monarch Cash Flow Ratio Progress Bar
        if (moneyInMonth > 0) {
            val pctSpent = ((moneyOutMonth / moneyInMonth) * 100).toInt().coerceIn(0, 100)
            progressCashFlow.progress = pctSpent
            tvCashFlowRatio.text = "$pctSpent% of income spent (${100 - pctSpent}% saved)"
        } else {
            progressCashFlow.progress = if (moneyOutMonth > 0) 100 else 0
            tvCashFlowRatio.text = if (moneyOutMonth > 0) "Expenses without recorded income" else "No activity yet"
        }

        // 2. Bank Source Split (HSBC, PayPal, and others)
        val sources = db.getSourceTotals()
        val hsbc = sources["HSBC"] ?: Pair(0.0, 0.0)
        val paypal = sources["PayPal"] ?: Pair(0.0, 0.0)

        tvHsbcOut.text = String.format(Locale.UK, "Spent: £%.2f", hsbc.second)
        tvHsbcIn.text = String.format(Locale.UK, "In: +£%.2f", hsbc.first)

        tvPayPalOut.text = String.format(Locale.UK, "Spent: £%.2f", paypal.second)
        tvPayPalIn.text = String.format(Locale.UK, "In: +£%.2f", paypal.first)

        // 3. Category & Merchant lists
        renderCategories(db.getCategoryTotals("OUT"))
        renderTopMerchants(db.getTopMerchants(limit = 5, type = "OUT"))
        renderTransactions(db.getAllTransactions(limit = 100, typeFilter = currentFilter))
    }

    private fun renderCategories(categories: List<CategorySpend>) {
        layoutCategories.removeAllViews()
        if (categories.isEmpty()) {
            val emptyTv = TextView(this).apply {
                text = "No category spending recorded yet"
                setTextColor(Color.parseColor("#64748B"))
                textSize = 13f
            }
            layoutCategories.addView(emptyTv)
            return
        }

        for (cat in categories) {
            val itemView = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 0, 0, 14)
            }

            val header = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                weightSum = 1f
            }

            val icon = getCategoryIcon(cat.category)

            val titleView = TextView(this).apply {
                text = "$icon ${cat.category} (${cat.count})"
                setTextColor(Color.parseColor("#0F172A"))
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.6f)
            }

            val amountView = TextView(this).apply {
                text = String.format(Locale.UK, "£%.2f (%d%%)", cat.total, cat.percentage)
                setTextColor(Color.parseColor("#0F172A"))
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
                progressTintList = ColorStateList.valueOf(Color.parseColor("#0F172A"))
                progressBackgroundTintList = ColorStateList.valueOf(Color.parseColor("#E2E8F0"))
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 14).apply {
                    topMargin = 6
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
                text = "No vendor spending recorded yet"
                setTextColor(Color.parseColor("#64748B"))
                textSize = 13f
            }
            layoutTopMerchants.addView(emptyTv)
            return
        }

        for ((index, m) in merchants.withIndex()) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                weightSum = 1f
                setPadding(0, 8, 0, 8)
                gravity = Gravity.CENTER_VERTICAL
            }

            val rankBadge = TextView(this).apply {
                text = "#${index + 1}"
                setTextColor(Color.parseColor("#64748B"))
                textSize = 12f
                setTypeface(null, Typeface.BOLD)
                setPadding(0, 0, 12, 0)
            }

            val nameTv = TextView(this).apply {
                text = "${m.merchant} (${m.count}x)"
                setTextColor(Color.parseColor("#0F172A"))
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.65f)
            }

            val amtTv = TextView(this).apply {
                text = String.format(Locale.UK, "£%.2f", m.total)
                setTextColor(Color.parseColor("#0F172A"))
                setTypeface(null, Typeface.BOLD)
                textSize = 14f
                gravity = Gravity.END
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.35f)
            }

            row.addView(rankBadge)
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
                radius = 16f
                cardElevation = 1.dp().toFloat()
                setCardBackgroundColor(Color.WHITE)
                setContentPadding(16, 14, 16, 14)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    showTransactionDetailsDialog(t)
                }
            }

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                weightSum = 1f
            }

            // Category Avatar (Squircle)
            val iconBadge = TextView(this).apply {
                text = getCategoryIcon(t.category)
                textSize = 18f
                gravity = Gravity.CENTER
                val bg = GradientDrawable().apply {
                    setColor(Color.parseColor(if (t.type == "IN") "#ECFDF5" else "#F8FAFC"))
                    setStroke(1.dp(), Color.parseColor(if (t.type == "IN") "#A7F3D0" else "#E2E8F0"))
                    cornerRadius = 20f
                }
                background = bg
                layoutParams = LinearLayout.LayoutParams(42.dp(), 42.dp()).apply {
                    marginEnd = 12.dp()
                }
            }

            // Vendor & Details Column
            val details = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.63f)
            }

            val titleTv = TextView(this).apply {
                text = t.merchant
                setTextColor(Color.parseColor("#0F172A"))
                textSize = 15f
                setTypeface(null, Typeface.BOLD)
            }

            // Pill tags container (Account pill, Category pill, Timestamp)
            val tagsRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = 3.dp()
                }
            }

            val sourcePill = TextView(this).apply {
                text = t.source
                setTextColor(Color.parseColor("#475569"))
                textSize = 10f
                setTypeface(null, Typeface.BOLD)
                setBackgroundResource(R.drawable.bg_tag)
                setPadding(6.dp(), 2.dp(), 6.dp(), 2.dp())
            }

            val catPill = TextView(this).apply {
                text = t.category
                setTextColor(Color.parseColor("#475569"))
                textSize = 10f
                setBackgroundResource(R.drawable.bg_tag)
                setPadding(6.dp(), 2.dp(), 6.dp(), 2.dp())
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginStart = 4.dp()
                }
            }

            val timeTv = TextView(this).apply {
                text = "• ${t.formattedTime}"
                setTextColor(Color.parseColor("#94A3B8"))
                textSize = 11f
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginStart = 4.dp()
                }
            }

            tagsRow.addView(sourcePill)
            tagsRow.addView(catPill)
            tagsRow.addView(timeTv)

            details.addView(titleTv)
            details.addView(tagsRow)

            // Amount Column (Inflow: Positive Emerald Green, Outflow: Clean Pitch Black / Red)
            val amountTv = TextView(this).apply {
                if (t.type == "IN") {
                    text = String.format(Locale.UK, "+%s%.2f", t.currency, t.amount)
                    setTextColor(Color.parseColor("#059669")) // Material Green
                } else {
                    text = String.format(Locale.UK, "-%s%.2f", t.currency, t.amount)
                    setTextColor(Color.parseColor("#0F172A")) // Material Pitch Black for Outflows
                }
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.37f)
            }

            row.addView(iconBadge)
            row.addView(details)
            row.addView(amountTv)
            card.addView(row)
            layoutTransactions.addView(card)
        }
    }

    private fun getCategoryIcon(cat: String): String {
        return when (cat) {
            "Groceries" -> "🛒"
            "Dining & Drinks" -> "☕"
            "Transport & Fuel" -> "🚗"
            "Bills & Subscriptions" -> "🍿"
            "Shopping" -> "🛍️"
            "Entertainment" -> "🎮"
            "Income & Salary" -> "💰"
            "Refunds" -> "↩️"
            "Transfers In" -> "📥"
            "Cashback & Rewards" -> "🎁"
            "Top-ups & Deposits" -> "💳"
            "Money In" -> "💵"
            else -> "🏷️"
        }
    }

    private fun showTransactionDetailsDialog(t: TransactionRecord) {
        val sdf = SimpleDateFormat("EEEE, dd MMMM yyyy 'at' HH:mm:ss", Locale.getDefault())
        val exactTime = sdf.format(Date(t.timestamp))
        val typeLabel = if (t.type == "IN") "Money In (Positive Inflow / Deposit)" else "Money Out (Expense / Outflow)"

        val message = """
            📍 Vendor / Sender: ${t.merchant}
            📊 Type: $typeLabel
            💰 Amount: ${t.currency}${String.format(Locale.UK, "%.2f", t.amount)}
            🏷️ Category: ${t.category}
            🏦 Source: ${t.source}
            🕒 Time: $exactTime
            ${if (!t.rawText.isNullOrEmpty()) "\n📝 Alert:\n\"${t.rawText}\"" else ""}
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle(t.merchant)
            .setMessage(message)
            .setPositiveButton("Close", null)
            .setNegativeButton("Delete") { _, _ ->
                db.deleteTransaction(t.id)
                refreshData()
            }
            .show()
    }

    private fun showAddTransactionDialog() {
        val context = this
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 20, 40, 20)
        }

        // Type selection (Money Out vs Money In)
        val radioGroup = RadioGroup(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 10, 0, 16)
        }

        val rbOut = android.widget.RadioButton(context).apply {
            text = "↓ Money Out (Expense)"
            id = View.generateViewId()
            isChecked = true
            setTextColor(Color.parseColor("#F43F5E"))
        }

        val rbIn = android.widget.RadioButton(context).apply {
            text = "↑ Money In (Income/Deposit)"
            id = View.generateViewId()
            setTextColor(Color.parseColor("#10B981"))
        }

        radioGroup.addView(rbOut)
        radioGroup.addView(rbIn)
        layout.addView(radioGroup)

        // Vendor input
        val etVendor = EditText(context).apply {
            hint = "Merchant / Sender Name (e.g. Tesco, Netflix, Employer)"
            textSize = 14f
            setPadding(0, 16, 0, 16)
        }
        layout.addView(etVendor)

        // Amount input
        val etAmount = EditText(context).apply {
            hint = "Amount (e.g. 14.80 or 23.00)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            textSize = 14f
            setPadding(0, 16, 0, 16)
        }
        layout.addView(etAmount)

        // Category spinner
        val tvCategoryLabel = TextView(context).apply {
            text = "Category:"
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 12f
            setPadding(0, 12, 0, 4)
        }
        layout.addView(tvCategoryLabel)

        val categories = arrayOf(
            "Groceries", "Dining & Drinks", "Transport & Fuel",
            "Bills & Subscriptions", "Shopping", "Entertainment",
            "Income & Salary", "Refunds", "Transfers In", "Top-ups & Deposits", "General Spend"
        )
        val spinnerCategory = Spinner(context).apply {
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, categories)
        }
        layout.addView(spinnerCategory)

        // Source spinner
        val tvSourceLabel = TextView(context).apply {
            text = "Account / Bank Source:"
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 12f
            setPadding(0, 12, 0, 4)
        }
        layout.addView(tvSourceLabel)

        val sources = arrayOf("Chase", "HSBC", "PayPal", "Monzo", "Revolut", "Barclays", "Google Pay", "Apple Pay", "Cash")
        val spinnerSource = Spinner(context).apply {
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, sources)
        }
        layout.addView(spinnerSource)

        AlertDialog.Builder(context)
            .setTitle("Record Transaction")
            .setView(layout)
            .setPositiveButton("Save") { _, _ ->
                val vendor = etVendor.text.toString().trim()
                val amount = etAmount.text.toString().trim().toDoubleOrNull() ?: 0.0
                val isIncome = rbIn.isChecked
                val type = if (isIncome) "IN" else "OUT"
                val category = spinnerCategory.selectedItem.toString()
                val source = spinnerSource.selectedItem.toString()

                if (vendor.isNotEmpty() && amount > 0.0) {
                    db.insertCustomTransaction(
                        type = type,
                        amount = amount,
                        currency = "£",
                        merchant = vendor,
                        category = category,
                        source = source,
                        timestamp = System.currentTimeMillis()
                    )
                    refreshData()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showSimulationDialog() {
        val options = arrayOf(
            "💰 Chase In: £23.00 has been added to your account",
            "💰 HSBC In: You received £250.00 from John Smith",
            "💰 Monzo In: Salary credit: £2,850.00 from TechCorp",
            "💰 PayPal In: Dave sent you £40.00",
            "💰 HSBC In: Refund of £24.99 from Amazon",
            "💰 Revolut In: You topped up £23.00 with Apple Pay",
            "🔴 HSBC Out: You spent £14.80 at TESCO STORES",
            "🔴 HSBC Out: Card ending 8219 spent £4.20 at COSTA COFFEE",
            "🔴 HSBC Out: Direct debit to British Gas of £65.00",
            "🔵 PayPal Out: You paid £29.99 to Steam Games",
            "🔵 PayPal Out: Payment of £8.99 to NETFLIX",
            "🟢 Monzo Out: You spent £12.40 at Pret A Manger",
            "🟡 Chase Out: You spent £18.50 with your card at ASDA",
            "📱 Google Pay Out: £4.00 with Visa ••1234 at Tesco"
        )

        AlertDialog.Builder(this)
            .setTitle("Simulate Bank Notification")
            .setItems(options) { _, which ->
                val (pkg, title, text) = when (which) {
                    0 -> Triple("com.jpmorgan.chase.uk", "Chase", "£23.00 has been added to your account")
                    1 -> Triple("uk.co.hsbc.hsbcukmobilebanking", "HSBC Credit Alert", "You received £250.00 from John Smith")
                    2 -> Triple("co.uk.getmondo", "Monzo Salary", "Salary credit: £2,850.00 from TechCorp")
                    3 -> Triple("com.paypal.android.p2pmobile", "PayPal", "Dave sent you £40.00")
                    4 -> Triple("uk.co.hsbc.hsbcukmobilebanking", "HSBC Refund", "Refund of £24.99 from Amazon")
                    5 -> Triple("com.revolut.revolut", "Revolut", "You topped up £23.00 with Apple Pay")
                    6 -> Triple("uk.co.hsbc.hsbcukmobilebanking", "HSBC Card Activity", "You spent £14.80 at TESCO STORES on 05/10")
                    7 -> Triple("uk.co.hsbc.hsbcukmobilebanking", "HSBC Spend Alert", "Card ending 8219 spent £4.20 at COSTA COFFEE")
                    8 -> Triple("uk.co.hsbc.hsbcukmobilebanking", "HSBC Direct Debit", "Direct debit to British Gas of £65.00")
                    9 -> Triple("com.paypal.android.p2pmobile", "PayPal", "You paid £29.99 to Steam Games")
                    10 -> Triple("com.paypal.android.p2pmobile", "PayPal Payment", "Payment of £8.99 to NETFLIX")
                    11 -> Triple("co.uk.getmondo", "Monzo", "You spent £12.40 at Pret A Manger")
                    12 -> Triple("com.jpmorgan.chase.uk", "Chase UK", "You spent £18.50 with your card at ASDA")
                    else -> Triple("com.google.android.apps.walletnfcrel", "Tesco", "£4.00 with Visa ••1234")
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

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()
}
