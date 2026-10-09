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
            val eventTimestamp = if (sbn.postTime > 0) sbn.postTime else System.currentTimeMillis()

            // Dynamic Bank Discovery: extract app label & package name and auto-register active account source
            val appLabel = runCatching {
                val pm = applicationContext.packageManager
                val appInfo = pm.getApplicationInfo(packageName, 0)
                pm.getApplicationLabel(appInfo).toString()
            }.getOrNull()?.trim()

            val detectedBankName = resolveBankName(packageName, parsed.source, appLabel)
            val parsedWithBank = if (parsed.source.isBlank() || parsed.source.equals("Card Payment", ignoreCase = true) || parsed.source.equals("Others", ignoreCase = true)) {
                parsed.copy(source = detectedBankName)
            } else {
                parsed
            }

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
                try {
                    val roomDb = AppDatabase.get(applicationContext)

                    val sourceAccount = roomDb.accountDao().findByName(detectedBankName) ?: run {
                        val id = roomDb.accountDao().insert(com.spendtracker.app.data.AccountEntity(name = detectedBankName))
                        Log.i(TAG, "Dynamically auto-registered new bank entity: $detectedBankName (id=$id)")
                        com.spendtracker.app.data.AccountEntity(id = id, name = detectedBankName)
                    }

                    val allAccounts = roomDb.accountDao().getAll()
                    val fullAlertText = "${title.orEmpty()} ${content.orEmpty()} ${parsedWithBank.merchant}".lowercase(java.util.Locale.ROOT)

                    // Transfer intent check: MUST have transfer intent phrasing to be considered a transfer
                    val hasExplicitTransferPhrase = fullAlertText.contains("internal transfer") ||
                        fullAlertText.contains("transfer between accounts") ||
                        fullAlertText.contains("transferred between") ||
                        fullAlertText.contains("moved money between")

                    // Find mentioned other registered account using flexible transfer action pattern
                    val mentionedOtherAccount = allAccounts.firstOrNull { other ->
                        if (other.id == sourceAccount.id) return@firstOrNull false
                        val otherName = other.name.trim().lowercase(java.util.Locale.ROOT)
                        if (otherName.isBlank()) return@firstOrNull false
                        val toPattern = Regex("\\b(?:transfer(?:red)?|sent|moved|paid)\\b.*?\\bto\\s+${Regex.escape(otherName)}\\b", RegexOption.IGNORE_CASE)
                        val fromPattern = Regex("\\b(?:transfer(?:red)?|received|moved)\\b.*?\\bfrom\\s+${Regex.escape(otherName)}\\b", RegexOption.IGNORE_CASE)
                        if (toPattern.containsMatchIn(fullAlertText) || fromPattern.containsMatchIn(fullAlertText)) {
                            return@firstOrNull true
                        }
                        if (hasExplicitTransferPhrase && Regex("\\b${Regex.escape(otherName)}\\b", RegexOption.IGNORE_CASE).containsMatchIn(fullAlertText)) {
                            return@firstOrNull true
                        }
                        false
                    }

                    val isInternalTransfer = hasExplicitTransferPhrase || (mentionedOtherAccount != null)
                    val destinationAccount = mentionedOtherAccount
                        ?: if (hasExplicitTransferPhrase) allAccounts.firstOrNull { it.id != sourceAccount.id } else null

                    val minorAmount = (parsedWithBank.amount * 100.0).roundToLong().coerceAtLeast(1L)

                    if (isInternalTransfer && destinationAccount != null) {
                        // Check if a transfer pair already exists within deduplication window
                        val existingTransfer = roomDb.transactionDao().findDuplicate(
                            absAmountMinor = minorAmount,
                            sourceName = sourceAccount.name,
                            accountId = sourceAccount.id,
                            timestamp = eventTimestamp,
                            windowMillis = 60_000L
                        )
                        if (existingTransfer != null) {
                            Log.i(TAG, "Deduplication: skipped duplicate internal transfer alert within 60s window (amount=$minorAmount)")
                        } else {
                            val isOutflow = !parsedWithBank.type.equals("IN", ignoreCase = true)
                            val srcId = if (isOutflow) sourceAccount.id else destinationAccount.id
                            val dstId = if (isOutflow) destinationAccount.id else sourceAccount.id
                            val srcName = if (isOutflow) sourceAccount.name else destinationAccount.name
                            val dstName = if (isOutflow) destinationAccount.name else sourceAccount.name

                            val transferPair = roomDb.transactionDao().insertTransferPair(
                                sourceAccountId = srcId,
                                destinationAccountId = dstId,
                                amountMinor = minorAmount,
                                timestamp = eventTimestamp,
                                note = "Captured from ${sourceAccount.name} alert",
                                sourceName = srcName,
                                destinationName = dstName
                            )
                            Log.i(TAG, "Successfully inserted INTERNAL TRANSFER pair (source=$srcName, dest=$dstName, amount=$minorAmount, nullified from spending)")
                        }
                    } else {
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
                            merchant = parsedWithBank.merchant,
                            rules = rules,
                            history = emptyList(),
                            validKeys = validKeys
                        )

                        val resolvedCategoryKey = when (classification) {
                            is Classification.Matched -> classification.categoryKey
                            else -> {
                                val normalizedParsed = parsedWithBank.category.uppercase().replace(" ", "_")
                                if (normalizedParsed in validKeys) normalizedParsed else "OTHER_EXPENSE"
                            }
                        }

                        val txType = if (parsedWithBank.type.equals("IN", ignoreCase = true)) TransactionType.INCOME else TransactionType.EXPENSE
                        val signedAmount = if (txType == TransactionType.EXPENSE) -minorAmount else minorAmount
                        val merchantName = if (parsedWithBank.merchantIsFallback || parsedWithBank.merchant.isBlank()) null else parsedWithBank.merchant

                        // Time-Window Fingerprinting: |t_new - t_existing| < 60s AND amount matches AND source bank matches
                        val existingTx = roomDb.transactionDao().findDuplicate(
                            absAmountMinor = minorAmount,
                            sourceName = sourceAccount.name,
                            accountId = sourceAccount.id,
                            timestamp = eventTimestamp,
                            windowMillis = 60_000L
                        )

                        if (existingTx != null) {
                            // Update existing record (enrichment with vendor/reference) rather than creating a duplicate row
                            val enrichedMerchant = if (!merchantName.isNullOrBlank() &&
                                (existingTx.merchant.isNullOrBlank() ||
                                 existingTx.merchant.equals(existingTx.source, ignoreCase = true) ||
                                 existingTx.merchant.equals("Card Payment", ignoreCase = true) ||
                                 existingTx.merchant.equals("Payment", ignoreCase = true))) {
                                merchantName
                            } else {
                                existingTx.merchant
                            }
                            val enrichedNote = if (!content.isNullOrBlank() && (existingTx.note.isNullOrBlank() || existingTx.note.startsWith("Captured from"))) {
                                "Captured from ${sourceAccount.name}: $content"
                            } else {
                                existingTx.note
                            }
                            val enrichedCategory = if ((existingTx.category.isNullOrBlank() ||
                                                         existingTx.category == "OTHER_EXPENSE" ||
                                                         existingTx.category == "General Spend") &&
                                                        resolvedCategoryKey != "OTHER_EXPENSE") {
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
                                merchant = enrichedMerchant,
                                note = enrichedNote,
                                category = enrichedCategory,
                                type = enrichedType,
                                amount = enrichedAmount
                            )
                            roomDb.transactionDao().update(enriched)
                            Log.i(TAG, "Deduplication: enriched existing transaction id=${existingTx.id} with vendor/reference instead of creating duplicate row")
                        } else {
                            val transaction = TransactionEntity(
                                accountId = sourceAccount.id,
                                amount = signedAmount,
                                category = resolvedCategoryKey,
                                note = "Captured from ${sourceAccount.name} alert",
                                timestamp = eventTimestamp,
                                type = txType,
                                excludeFromSpending = false,
                                merchant = merchantName,
                                source = sourceAccount.name
                            )

                            val insertedId = roomDb.transactionDao().insert(transaction)
                            Log.i(TAG, "Successfully inserted transaction into Room db (id=$insertedId, source=${sourceAccount.name}, merchant=${parsedWithBank.merchant}, cat=$resolvedCategoryKey)")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to insert parsed notification into Room: ${e.message}", e)
                }
            }
        }
    }

    private fun resolveBankName(packageName: String, parsedSource: String, appLabel: String?): String {
        if (!parsedSource.equals("Card Payment", ignoreCase = true) &&
            !parsedSource.equals("Others", ignoreCase = true) &&
            parsedSource.isNotBlank()
        ) {
            return parsedSource
        }
        if (!appLabel.isNullOrBlank()) {
            val clean = appLabel
                .replace(Regex("(?i)\\b(?:mobile banking|banking|mobile|uk|app)\\b"), "")
                .trim()
            if (clean.isNotBlank()) return clean
        }
        val pkgLower = packageName.lowercase(java.util.Locale.ROOT)
        return when {
            pkgLower.contains("chase") -> "Chase"
            pkgLower.contains("hsbc") -> "HSBC"
            pkgLower.contains("monzo") -> "Monzo"
            pkgLower.contains("starling") -> "Starling"
            pkgLower.contains("revolut") -> "Revolut"
            pkgLower.contains("barclay") -> "Barclays"
            pkgLower.contains("santander") -> "Santander"
            pkgLower.contains("natwest") -> "NatWest"
            pkgLower.contains("lloyds") -> "Lloyds"
            pkgLower.contains("halifax") -> "Halifax"
            pkgLower.contains("nationwide") -> "Nationwide"
            else -> {
                val parts = packageName.split(".").filter {
                    it.length > 2 && it !in listOf("com", "org", "net", "android", "uk", "co", "app")
                }
                parts.lastOrNull()?.replaceFirstChar {
                    if (it.isLowerCase()) it.titlecase(java.util.Locale.ROOT) else it.toString()
                } ?: "Card Payment"
            }
        }
    }
}
