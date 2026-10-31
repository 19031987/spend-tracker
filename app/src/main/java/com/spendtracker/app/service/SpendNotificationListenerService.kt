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
import com.spendtracker.app.data.isUnknownOrGenericMerchant
import com.spendtracker.app.data.normalizeBankName
import com.spendtracker.app.data.resolvePreferredBankSource
import com.spendtracker.app.domain.CategorizationEngine
import com.spendtracker.app.domain.Classification
import com.spendtracker.app.domain.MatchType
import com.spendtracker.app.domain.MerchantRule
import com.spendtracker.app.parser.SpendParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.roundToLong

class SpendNotificationListenerService : NotificationListenerService() {

    companion object {
        const val ACTION_NEW_EXPENSE = "com.spendtracker.app.ACTION_NEW_EXPENSE"
        private const val TAG = "SpendListenerService"
        private val roomDedupMutex = Mutex()
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
            val eventTimestamp = if (sbn.postTime > 0) sbn.postTime else System.currentTimeMillis()

            // Dynamic Bank Discovery: extract app label & package name and auto-register active account source
            val appLabel = runCatching {
                val pm = applicationContext.packageManager
                val appInfo = pm.getApplicationInfo(packageName, 0)
                pm.getApplicationLabel(appInfo).toString()
            }.getOrNull()?.trim()

            val detectedBankName = normalizeBankName(resolveBankName(packageName, parsed.source, appLabel))
            val parsedWithBank = parsed.copy(source = detectedBankName)

            Log.i(TAG, "Detected [${parsedWithBank.type}] ${parsedWithBank.source}: ${parsedWithBank.currency}${parsedWithBank.amount} at ${parsedWithBank.merchant} (${parsedWithBank.category})")

            // 1. Save to legacy SQLite database with duplicate protection
            val db = SpendDatabase(applicationContext)
            val rowId = db.insertExpense(parsedWithBank, eventTimestamp)
            if (rowId > 0) {
                // Send local broadcast to notify any legacy listeners
                val intent = Intent(ACTION_NEW_EXPENSE).apply {
                    putExtra("type", parsedWithBank.type)
                    putExtra("amount", parsedWithBank.amount)
                    putExtra("merchant", parsedWithBank.merchant)
                    putExtra("category", parsedWithBank.category)
                    putExtra("source", parsedWithBank.source)
                    setPackage(applicationContext.packageName)
                }
                sendBroadcast(intent)
            } else {
                Log.d(TAG, "Skipped duplicate notification event in SQLite: ${parsedWithBank.merchant} ${parsedWithBank.currency}${parsedWithBank.amount}")
            }

            // 2. Save directly into Room AppDatabase so Compose UI & Analytics update in real time!
            serviceScope.launch {
                roomDedupMutex.withLock {
                    try {
                        val roomDb = AppDatabase.get(applicationContext)

                        val sourceAccount = roomDb.accountDao().getOrCreate(detectedBankName)
                        val minorAmount = (parsedWithBank.amount * 100.0).roundToLong().coerceAtLeast(1L)
                        val txType = if (parsedWithBank.type.equals("IN", ignoreCase = true)) TransactionType.INCOME else TransactionType.EXPENSE

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
                        val validKeys = categories.filter { !it.isHidden && it.type == txType }.map { it.key }.toSet()

                        val classification = CategorizationEngine.classify(
                            merchant = parsedWithBank.merchant,
                            rules = rules,
                            history = emptyList(),
                            validKeys = validKeys
                        )

                        val resolvedCategoryKey = when (classification) {
                            is Classification.Matched -> classification.categoryKey
                            else -> mapParsedCategoryToCatalogKey(parsedWithBank.category, txType, validKeys)
                        }

                        val signedAmount = if (txType == TransactionType.EXPENSE) -minorAmount else minorAmount
                        val merchantName = if (parsedWithBank.merchantIsFallback || isUnknownOrGenericMerchant(parsedWithBank.merchant, sourceAccount.name)) null else parsedWithBank.merchant

                        // Time-Window Fingerprinting: |t_new - t_existing| <= 60s AND amount matches AND source bank matches (including Premier <-> HSBC) AND merchant matches
                        val existingTx = roomDb.transactionDao().findMatchingDuplicate(
                            absAmountMinor = minorAmount,
                            sourceName = sourceAccount.name,
                            accountId = sourceAccount.id,
                            timestamp = eventTimestamp,
                            merchant = merchantName,
                            windowMillis = 60_000L
                        )

                        if (existingTx != null) {
                            val preferredSource = resolvePreferredBankSource(existingTx.source, sourceAccount.name)
                            val preferredAccount = if (preferredSource.equals(sourceAccount.name, ignoreCase = true)) {
                                sourceAccount
                            } else {
                                roomDb.accountDao().getOrCreate(preferredSource)
                            }

                            // Update existing record (enrichment with vendor/reference and HSBC bank preference) rather than creating a duplicate row
                            val enrichedMerchant = when {
                                !merchantName.isNullOrBlank() && isUnknownOrGenericMerchant(existingTx.merchant, existingTx.source) -> merchantName
                                isUnknownOrGenericMerchant(existingTx.merchant, existingTx.source) -> null
                                else -> existingTx.merchant
                            }
                            val rawAlertText = parsedWithBank.rawText.ifBlank { content.orEmpty() }
                            val enrichedNote = when {
                                existingTx.note.isNullOrBlank() && rawAlertText.isNotBlank() ->
                                    "Captured from ${preferredAccount.name} alert: $rawAlertText"
                                existingTx.note != null && (!merchantName.isNullOrBlank() && isUnknownOrGenericMerchant(existingTx.merchant, existingTx.source)) && rawAlertText.isNotBlank() ->
                                    "Captured from ${preferredAccount.name} alert: $rawAlertText"
                                existingTx.note != null && existingTx.note.contains("Premier", ignoreCase = true) ->
                                    if (rawAlertText.isNotBlank() && !isUnknownOrGenericMerchant(merchantName, preferredAccount.name)) {
                                        "Captured from ${preferredAccount.name} alert: $rawAlertText"
                                    } else {
                                        existingTx.note.replace(
                                            Regex("(?i)^Captured from\\s+(?:HSBC\\s+)?Premier(?:\\s+Account|\\s+Bank)?"),
                                            "Captured from ${preferredAccount.name}"
                                        )
                                    }
                                else -> existingTx.note
                            }
                            val defaultCat = if (txType == TransactionType.INCOME) "OTHER_INCOME" else "OTHER_EXPENSE"
                            val enrichedCategory = if ((existingTx.category.isNullOrBlank() ||
                                                         existingTx.category == "OTHER_EXPENSE" ||
                                                         existingTx.category == "OTHER_INCOME" ||
                                                         existingTx.category == "General Spend") &&
                                                        resolvedCategoryKey != defaultCat) {
                                resolvedCategoryKey
                            } else if (existingTx.type == TransactionType.EXPENSE && txType == TransactionType.INCOME && existingTx.category == "OTHER_EXPENSE") {
                                resolvedCategoryKey
                            } else {
                                existingTx.category
                            }
                            val enrichedType = if (existingTx.type == TransactionType.EXPENSE && txType == TransactionType.INCOME) {
                                txType
                            } else {
                                existingTx.type
                            }
                            val enrichedAmount = if (enrichedType == TransactionType.INCOME) minorAmount else -minorAmount

                            val enriched = existingTx.copy(
                                accountId = preferredAccount.id,
                                source = preferredAccount.name,
                                merchant = enrichedMerchant,
                                note = enrichedNote,
                                category = enrichedCategory,
                                type = enrichedType,
                                amount = enrichedAmount
                            )
                            roomDb.transactionDao().update(enriched)
                            Log.i(TAG, "Deduplication: enriched existing transaction id=${existingTx.id} (bank=${preferredAccount.name}) instead of creating duplicate row")
                        } else {
                            val rawAlertText = parsedWithBank.rawText.ifBlank { content.orEmpty() }
                            val transaction = TransactionEntity(
                                accountId = sourceAccount.id,
                                amount = signedAmount,
                                category = resolvedCategoryKey,
                                note = "Captured from ${sourceAccount.name} alert: $rawAlertText",
                                timestamp = eventTimestamp,
                                type = txType,
                                excludeFromSpending = false,
                                merchant = merchantName,
                                source = sourceAccount.name
                            )

                            val insertedId = roomDb.transactionDao().insert(transaction)
                            Log.i(TAG, "Successfully inserted transaction into Room db (id=$insertedId, source=${sourceAccount.name}, merchant=${parsedWithBank.merchant}, cat=$resolvedCategoryKey)")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to insert parsed notification into Room: ${e.message}", e)
                    }
                }
            }
        }
    }

    private fun mapParsedCategoryToCatalogKey(
        parsedCategory: String,
        txType: TransactionType,
        validKeys: Set<String>
    ): String {
        val normalized = parsedCategory.trim().uppercase(java.util.Locale.ROOT).replace(" ", "_")
        if (normalized in validKeys) return normalized

        val mapped = when (parsedCategory.trim().lowercase(java.util.Locale.ROOT)) {
            "groceries" -> "GROCERIES"
            "dining & drinks", "dining" -> "DINING"
            "transport & fuel", "transport" -> "TRANSPORT"
            "bills & subscriptions", "subscriptions" -> "SUBSCRIPTIONS"
            "utilities" -> "UTILITIES"
            "shopping" -> "SHOPPING"
            "entertainment" -> "ENTERTAINMENT"
            "income & salary", "salary" -> "SALARY"
            "freelance" -> "FREELANCE"
            "investment" -> "INVESTMENT"
            "gift" -> "GIFT"
            "refunds", "transfers in", "cashback & rewards", "top-ups & deposits", "money in", "other income" -> "OTHER_INCOME"
            else -> if (txType == TransactionType.INCOME) "OTHER_INCOME" else "OTHER_EXPENSE"
        }
        return if (mapped in validKeys) mapped else if (txType == TransactionType.INCOME) "OTHER_INCOME" else "OTHER_EXPENSE"
    }

    private fun resolveBankName(packageName: String, parsedSource: String, appLabel: String?): String {
        val normParsed = normalizeBankName(parsedSource)
        if (normParsed != "Others" && normParsed.isNotBlank()) {
            return normParsed
        }

        val pkgLower = packageName.lowercase(java.util.Locale.ROOT)
        val isSmsApp = pkgLower.contains("messaging") || pkgLower.contains("mms") ||
            packageName in listOf(
                "com.google.android.apps.messaging", "com.samsung.android.messaging",
                "com.android.mms", "com.android.messaging", "com.oneplus.mms"
            )

        if (!appLabel.isNullOrBlank() && !isSmsApp) {
            val labelLower = appLabel.lowercase(java.util.Locale.ROOT)
            when {
                labelLower.contains("chase") -> return "Chase"
                labelLower.contains("hsbc") || labelLower.contains("premier") -> return "HSBC"
                labelLower.contains("monzo") -> return "Monzo"
                labelLower.contains("starling") -> return "Starling"
                labelLower.contains("revolut") -> return "Revolut"
                labelLower.contains("barclay") -> return "Barclays"
                labelLower.contains("santander") -> return "Santander"
                labelLower.contains("natwest") || labelLower.contains("nat west") -> return "NatWest"
                labelLower.contains("lloyds") -> return "Lloyds"
                labelLower.contains("halifax") -> return "Halifax"
                labelLower.contains("nationwide") -> return "Nationwide"
                labelLower.contains("paypal") -> return "PayPal"
                labelLower.contains("google") || labelLower.contains("gpay") -> return "Google Pay"
                labelLower.contains("apple") -> return "Apple Pay"
                labelLower.contains("samsung") -> return "Samsung Pay"
                labelLower.contains("amex") || labelLower.contains("american express") -> return "Amex"
                labelLower.contains("tsb") -> return "TSB"
                labelLower.contains("rbs") || labelLower.contains("royal bank") -> return "RBS"
                labelLower.contains("first direct") || labelLower.contains("firstdirect") -> return "First Direct"
                labelLower.contains("virgin money") -> return "Virgin Money"
                labelLower.contains("metro") -> return "Metro Bank"
                labelLower.contains("kroo") -> return "Kroo"
                else -> {
                    val clean = appLabel
                        .replace(Regex("(?i)\\s*[:\\-•|].*"), "")
                        .replace(Regex("(?i)\\b(?:mobile banking|banking|mobile|uk|app|messages?|messaging|sms)\\b"), "")
                        .trim()
                    val cleanLower = clean.lowercase(java.util.Locale.ROOT)
                    if (clean.isNotBlank() && cleanLower !in listOf("bank", "banking", "messages", "messaging", "message", "sms", "mms")) {
                        val norm = normalizeBankName(clean)
                        if (norm != "Others" && norm.isNotBlank()) return norm
                    }
                }
            }
        }

        return when {
            pkgLower.contains("chase") || pkgLower.contains("jpmorgan") -> "Chase"
            pkgLower.contains("hsbc") || pkgLower.contains("premier") -> "HSBC"
            pkgLower.contains("monzo") || pkgLower.contains("getmondo") -> "Monzo"
            pkgLower.contains("starling") -> "Starling"
            pkgLower.contains("revolut") -> "Revolut"
            pkgLower.contains("barclay") -> "Barclays"
            pkgLower.contains("santander") -> "Santander"
            pkgLower.contains("natwest") -> "NatWest"
            pkgLower.contains("lloyds") -> "Lloyds"
            pkgLower.contains("halifax") -> "Halifax"
            pkgLower.contains("nationwide") -> "Nationwide"
            pkgLower.contains("paypal") -> "PayPal"
            pkgLower.contains("amex") || pkgLower.contains("americanexpress") -> "Amex"
            pkgLower.contains("tsb") -> "TSB"
            pkgLower.contains("rbs") -> "RBS"
            pkgLower.contains("firstdirect") -> "First Direct"
            pkgLower.contains("virginmoney") -> "Virgin Money"
            pkgLower.contains("metrobank") -> "Metro Bank"
            pkgLower.contains("kroo") -> "Kroo"
            pkgLower.contains("walletnfcrel") || pkgLower.contains("wallet") || pkgLower.contains("paisa") -> "Google Pay"
            pkgLower.contains("samsung") && pkgLower.contains("pay") -> "Samsung Pay"
            isSmsApp -> "Credit Card"
            else -> {
                val parts = packageName.split(".").filter {
                    it.length > 2 && it !in listOf("com", "org", "net", "android", "uk", "co", "app", "mobile", "banking", "messaging", "mms")
                }
                parts.lastOrNull()?.let {
                    val norm = normalizeBankName(it)
                    if (norm.equals("Bank", ignoreCase = true) || norm.equals("Banking", ignoreCase = true) || norm.equals("Others", ignoreCase = true)) "Others" else norm
                } ?: "Others"
            }
        }
    }
}
