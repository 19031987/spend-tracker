package com.spendtracker.app.service

import android.app.Notification
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.spendtracker.app.data.AppDatabase
import com.spendtracker.app.data.SpendDatabase
import com.spendtracker.app.data.TransactionEntity
import com.spendtracker.app.data.TransactionType
import com.spendtracker.app.domain.CategorizationEngine
import com.spendtracker.app.domain.Classification
import com.spendtracker.app.domain.MatchType
import com.spendtracker.app.domain.MerchantRule
import com.spendtracker.app.parser.SpendParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.roundToLong

class SpendNotificationListenerService : NotificationListenerService() {

    companion object {
        const val ACTION_NEW_EXPENSE = "com.spendtracker.app.ACTION_NEW_EXPENSE"
        private const val TAG = "SpendListenerService"
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val packageName = sbn.packageName ?: ""
        val extras = sbn.notification?.extras ?: return

        val title = extras.getString(Notification.EXTRA_TITLE)
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        val content = bigText ?: text

        Log.d(TAG, "Notification received from pkg: $packageName | Title: $title | Content: $content")

        val parsed = SpendParser.parse(packageName, title, content)
        if (parsed != null) {
            Log.i(TAG, "Detected [${parsed.type}] ${parsed.source}: ${parsed.currency}${parsed.amount} at ${parsed.merchant} (${parsed.category})")

            // 1. Save to legacy SQLite database with duplicate protection
            val db = SpendDatabase(applicationContext)
            val rowId = db.insertExpense(parsed)
            if (rowId > 0) {
                // Send local broadcast to notify any legacy listeners
                val intent = Intent(ACTION_NEW_EXPENSE).apply {
                    putExtra("type", parsed.type)
                    putExtra("amount", parsed.amount)
                    putExtra("merchant", parsed.merchant)
                    putExtra("category", parsed.category)
                    putExtra("source", parsed.source)
                    setPackage(applicationContext.packageName)
                }
                sendBroadcast(intent)
            } else {
                Log.d(TAG, "Skipped duplicate notification event in SQLite: ${parsed.merchant} ${parsed.currency}${parsed.amount}")
            }

            // 2. Save directly into Room AppDatabase so Compose UI & Analytics update in real time!
            serviceScope.launch {
                try {
                    val roomDb = AppDatabase.get(applicationContext)

                    // Auto-categorize using rules from Room database
                    val rules = roomDb.catalogDao().getRules().mapNotNull { r ->
                        runCatching {
                            MerchantRule(
                                id = r.id,
                                matchType = MatchType.valueOf(r.matchType),
                                pattern = r.pattern,
                                categoryKey = r.categoryKey
                            )
                        }.getOrNull()
                    }
                    val categories = roomDb.catalogDao().getCategories()
                    val validKeys = categories.filter { !it.isHidden }.map { it.key }.toSet()

                    val classification = CategorizationEngine.classify(
                        merchant = parsed.merchant,
                        rules = rules,
                        history = emptyList(),
                        validKeys = validKeys
                    )

                    val resolvedCategoryKey = when (classification) {
                        is Classification.Matched -> classification.categoryKey
                        else -> {
                            // Map parsed category or fallback
                            val normalizedParsed = parsed.category.uppercase().replace(" ", "_")
                            if (normalizedParsed in validKeys) normalizedParsed else "OTHER_EXPENSE"
                        }
                    }

                    val txType = if (parsed.type.equals("IN", ignoreCase = true)) TransactionType.INCOME else TransactionType.EXPENSE
                    val minorAmount = (parsed.amount * 100.0).roundToLong().coerceAtLeast(1L)
                    val signedAmount = if (txType == TransactionType.EXPENSE) -minorAmount else minorAmount

                    val transaction = TransactionEntity(
                        accountId = 1L,
                        amount = signedAmount,
                        category = resolvedCategoryKey,
                        note = "Captured from ${parsed.source} alert",
                        timestamp = if (sbn.postTime > 0) sbn.postTime else System.currentTimeMillis(),
                        type = txType,
                        excludeFromSpending = false,
                        merchant = parsed.merchant
                    )

                    val insertedId = roomDb.transactionDao().insert(transaction)
                    Log.i(TAG, "Successfully inserted transaction into Room db (id=$insertedId, merchant=${parsed.merchant}, cat=$resolvedCategoryKey)")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to insert parsed notification into Room: ${e.message}", e)
                }
            }
        }
    }
}
