package com.spendtracker.app.service

import android.app.Notification
import android.content.Intent
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.spendtracker.app.data.SpendDatabase
import com.spendtracker.app.parser.SpendParser

class SpendNotificationListenerService : NotificationListenerService() {

    companion object {
        const val ACTION_NEW_EXPENSE = "com.spendtracker.app.ACTION_NEW_EXPENSE"
        private const val TAG = "SpendListenerService"
    }

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
            Log.i(TAG, "Detected ${parsed.source} spend: ${parsed.currency}${parsed.amount} at ${parsed.merchant} (${parsed.category})")
            
            // Save to SQLite
            val db = SpendDatabase(applicationContext)
            db.insertExpense(parsed)

            // Send local broadcast to update UI immediately
            val intent = Intent(ACTION_NEW_EXPENSE).apply {
                putExtra("amount", parsed.amount)
                putExtra("merchant", parsed.merchant)
                putExtra("category", parsed.category)
                putExtra("source", parsed.source)
                setPackage(applicationContext.packageName)
            }
            sendBroadcast(intent)
        }
    }
}
