package com.spendtracker.app.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.spendtracker.app.parser.ParsedExpense
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class TransactionRecord(
    val id: Long,
    val amount: Double,
    val currency: String,
    val merchant: String,
    val category: String,
    val source: String,
    val timestamp: Long,
    val formattedTime: String
)

data class CategorySpend(
    val category: String,
    val total: Double,
    val count: Int,
    val percentage: Int
)

data class MerchantSpend(
    val merchant: String,
    val total: Double,
    val count: Int
)

class SpendDatabase(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "spend_tracker.db"
        const val DATABASE_VERSION = 1

        const val TABLE_NAME = "transactions"
        const val COL_ID = "id"
        const val COL_AMOUNT = "amount"
        const val COL_CURRENCY = "currency"
        const val COL_MERCHANT = "merchant"
        const val COL_CATEGORY = "category"
        const val COL_SOURCE = "source"
        const val COL_TIMESTAMP = "timestamp"
        const val COL_RAW_TEXT = "raw_text"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTable = """
            CREATE TABLE $TABLE_NAME (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_AMOUNT REAL NOT NULL,
                $COL_CURRENCY TEXT NOT NULL,
                $COL_MERCHANT TEXT NOT NULL,
                $COL_CATEGORY TEXT NOT NULL,
                $COL_SOURCE TEXT NOT NULL,
                $COL_TIMESTAMP INTEGER NOT NULL,
                $COL_RAW_TEXT TEXT
            )
        """.trimIndent()
        db.execSQL(createTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        onCreate(db)
    }

    fun insertExpense(expense: ParsedExpense, timestamp: Long = System.currentTimeMillis()): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_AMOUNT, expense.amount)
            put(COL_CURRENCY, expense.currency)
            put(COL_MERCHANT, expense.merchant)
            put(COL_CATEGORY, expense.category)
            put(COL_SOURCE, expense.source)
            put(COL_TIMESTAMP, timestamp)
            put(COL_RAW_TEXT, expense.rawText)
        }
        return db.insert(TABLE_NAME, null, values)
    }

    fun getTotalMonth(): Double {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        val startOfMonth = cal.timeInMillis

        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT SUM($COL_AMOUNT) FROM $TABLE_NAME WHERE $COL_TIMESTAMP >= ?",
            arrayOf(startOfMonth.toString())
        )
        var total = 0.0
        if (cursor.moveToFirst()) {
            total = cursor.getDouble(0)
        }
        cursor.close()
        return total
    }

    fun getTotalToday(): Double {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        val startOfDay = cal.timeInMillis

        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT SUM($COL_AMOUNT) FROM $TABLE_NAME WHERE $COL_TIMESTAMP >= ?",
            arrayOf(startOfDay.toString())
        )
        var total = 0.0
        if (cursor.moveToFirst()) {
            total = cursor.getDouble(0)
        }
        cursor.close()
        return total
    }

    fun getSourceTotals(): Map<String, Double> {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT $COL_SOURCE, SUM($COL_AMOUNT) FROM $TABLE_NAME GROUP BY $COL_SOURCE",
            null
        )
        val map = mutableMapOf("HSBC" to 0.0, "PayPal" to 0.0)
        while (cursor.moveToNext()) {
            val source = cursor.getString(0)
            val sum = cursor.getDouble(1)
            map[source] = sum
        }
        cursor.close()
        return map
    }

    fun getCategoryTotals(): List<CategorySpend> {
        val totalMonth = getTotalMonth()
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT $COL_CATEGORY, SUM($COL_AMOUNT) as total, COUNT(*) as cnt FROM $TABLE_NAME GROUP BY $COL_CATEGORY ORDER BY total DESC",
            null
        )
        val list = mutableListOf<CategorySpend>()
        while (cursor.moveToNext()) {
            val cat = cursor.getString(0)
            val total = cursor.getDouble(1)
            val cnt = cursor.getInt(2)
            val pct = if (totalMonth > 0) ((total / totalMonth) * 100).toInt() else 0
            list.add(CategorySpend(cat, total, cnt, pct))
        }
        cursor.close()
        return list
    }

    fun getTopMerchants(limit: Int = 5): List<MerchantSpend> {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT $COL_MERCHANT, SUM($COL_AMOUNT) as total, COUNT(*) as cnt FROM $TABLE_NAME GROUP BY $COL_MERCHANT ORDER BY total DESC LIMIT ?",
            arrayOf(limit.toString())
        )
        val list = mutableListOf<MerchantSpend>()
        while (cursor.moveToNext()) {
            list.add(MerchantSpend(cursor.getString(0), cursor.getDouble(1), cursor.getInt(2)))
        }
        cursor.close()
        return list
    }

    fun getAllTransactions(limit: Int = 50): List<TransactionRecord> {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT $COL_ID, $COL_AMOUNT, $COL_CURRENCY, $COL_MERCHANT, $COL_CATEGORY, $COL_SOURCE, $COL_TIMESTAMP FROM $TABLE_NAME ORDER BY $COL_TIMESTAMP DESC LIMIT ?",
            arrayOf(limit.toString())
        )
        val list = mutableListOf<TransactionRecord>()
        val sdf = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())
        while (cursor.moveToNext()) {
            val ts = cursor.getLong(6)
            list.add(
                TransactionRecord(
                    id = cursor.getLong(0),
                    amount = cursor.getDouble(1),
                    currency = cursor.getString(2),
                    merchant = cursor.getString(3),
                    category = cursor.getString(4),
                    source = cursor.getString(5),
                    timestamp = ts,
                    formattedTime = sdf.format(Date(ts))
                )
            )
        }
        cursor.close()
        return list
    }
}
