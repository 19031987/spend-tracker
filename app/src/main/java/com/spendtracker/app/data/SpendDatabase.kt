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
    val type: String, // "IN" for Money In (green), "OUT" for Money Out (red)
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
        const val DATABASE_VERSION = 2

        const val TABLE_NAME = "transactions"
        const val COL_ID = "id"
        const val COL_TYPE = "type" // "IN" or "OUT"
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
                $COL_TYPE TEXT NOT NULL DEFAULT 'OUT',
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
        db.execSQL("CREATE INDEX idx_transactions_timestamp ON $TABLE_NAME($COL_TIMESTAMP)")
        db.execSQL("CREATE INDEX idx_transactions_type ON $TABLE_NAME($COL_TYPE)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            try {
                db.execSQL("ALTER TABLE $TABLE_NAME ADD COLUMN $COL_TYPE TEXT NOT NULL DEFAULT 'OUT'")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_transactions_type ON $TABLE_NAME($COL_TYPE)")
            } catch (e: Exception) {
                // If migration fails, recreate
                db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
                onCreate(db)
            }
        }
    }

    fun insertExpense(expense: ParsedExpense, timestamp: Long = System.currentTimeMillis()): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_TYPE, expense.type)
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

    fun insertCustomTransaction(
        type: String,
        amount: Double,
        currency: String = "£",
        merchant: String,
        category: String,
        source: String,
        timestamp: Long = System.currentTimeMillis()
    ): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_TYPE, type)
            put(COL_AMOUNT, amount)
            put(COL_CURRENCY, currency)
            put(COL_MERCHANT, merchant)
            put(COL_CATEGORY, category)
            put(COL_SOURCE, source)
            put(COL_TIMESTAMP, timestamp)
            put(COL_RAW_TEXT, "Manual Entry: $type $currency$amount $merchant")
        }
        return db.insert(TABLE_NAME, null, values)
    }

    fun deleteTransaction(id: Long): Boolean {
        val db = writableDatabase
        return db.delete(TABLE_NAME, "$COL_ID = ?", arrayOf(id.toString())) > 0
    }

    fun clearAll() {
        val db = writableDatabase
        db.delete(TABLE_NAME, null, null)
    }

    private fun getStartOfMonth(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun getStartOfDay(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    // Money Out (Expenses) for current month
    fun getTotalMoneyOutMonth(): Double {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT SUM($COL_AMOUNT) FROM $TABLE_NAME WHERE $COL_TYPE = 'OUT' AND $COL_TIMESTAMP >= ?",
            arrayOf(getStartOfMonth().toString())
        )
        var total = 0.0
        if (cursor.moveToFirst() && !cursor.isNull(0)) {
            total = cursor.getDouble(0)
        }
        cursor.close()
        return total
    }

    // Money In (Income) for current month
    fun getTotalMoneyInMonth(): Double {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT SUM($COL_AMOUNT) FROM $TABLE_NAME WHERE $COL_TYPE = 'IN' AND $COL_TIMESTAMP >= ?",
            arrayOf(getStartOfMonth().toString())
        )
        var total = 0.0
        if (cursor.moveToFirst() && !cursor.isNull(0)) {
            total = cursor.getDouble(0)
        }
        cursor.close()
        return total
    }

    // Net Cash Flow for current month (In - Out)
    fun getNetCashFlowMonth(): Double {
        return getTotalMoneyInMonth() - getTotalMoneyOutMonth()
    }

    // Money Out today
    fun getTotalMoneyOutToday(): Double {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT SUM($COL_AMOUNT) FROM $TABLE_NAME WHERE $COL_TYPE = 'OUT' AND $COL_TIMESTAMP >= ?",
            arrayOf(getStartOfDay().toString())
        )
        var total = 0.0
        if (cursor.moveToFirst() && !cursor.isNull(0)) {
            total = cursor.getDouble(0)
        }
        cursor.close()
        return total
    }

    // Money In today
    fun getTotalMoneyInToday(): Double {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT SUM($COL_AMOUNT) FROM $TABLE_NAME WHERE $COL_TYPE = 'IN' AND $COL_TIMESTAMP >= ?",
            arrayOf(getStartOfDay().toString())
        )
        var total = 0.0
        if (cursor.moveToFirst() && !cursor.isNull(0)) {
            total = cursor.getDouble(0)
        }
        cursor.close()
        return total
    }

    // Source breakdown (HSBC vs PayPal vs Others) with (MoneyIn, MoneyOut)
    fun getSourceTotals(): Map<String, Pair<Double, Double>> {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT $COL_SOURCE, $COL_TYPE, SUM($COL_AMOUNT) FROM $TABLE_NAME GROUP BY $COL_SOURCE, $COL_TYPE",
            null
        )
        val map = mutableMapOf<String, Pair<Double, Double>>()
        while (cursor.moveToNext()) {
            val source = cursor.getString(0) ?: "Card Payment"
            val type = cursor.getString(1) ?: "OUT"
            val sum = cursor.getDouble(2)

            val current = map[source] ?: Pair(0.0, 0.0)
            if (type == "IN") {
                map[source] = Pair(current.first + sum, current.second)
            } else {
                map[source] = Pair(current.first, current.second + sum)
            }
        }
        cursor.close()
        return map
    }

    fun getCategoryTotals(type: String = "OUT"): List<CategorySpend> {
        val totalAmount = if (type == "OUT") getTotalMoneyOutMonth() else getTotalMoneyInMonth()
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT $COL_CATEGORY, SUM($COL_AMOUNT) as total, COUNT(*) as cnt FROM $TABLE_NAME WHERE $COL_TYPE = ? GROUP BY $COL_CATEGORY ORDER BY total DESC",
            arrayOf(type)
        )
        val list = mutableListOf<CategorySpend>()
        while (cursor.moveToNext()) {
            val cat = cursor.getString(0)
            val total = cursor.getDouble(1)
            val cnt = cursor.getInt(2)
            val pct = if (totalAmount > 0) ((total / totalAmount) * 100).toInt() else 0
            list.add(CategorySpend(cat, total, cnt, pct))
        }
        cursor.close()
        return list
    }

    fun getTopMerchants(limit: Int = 5, type: String = "OUT"): List<MerchantSpend> {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT $COL_MERCHANT, SUM($COL_AMOUNT) as total, COUNT(*) as cnt FROM $TABLE_NAME WHERE $COL_TYPE = ? GROUP BY $COL_MERCHANT ORDER BY total DESC LIMIT ?",
            arrayOf(type, limit.toString())
        )
        val list = mutableListOf<MerchantSpend>()
        while (cursor.moveToNext()) {
            list.add(MerchantSpend(cursor.getString(0), cursor.getDouble(1), cursor.getInt(2)))
        }
        cursor.close()
        return list
    }

    fun getAllTransactions(limit: Int = 100, typeFilter: String? = null): List<TransactionRecord> {
        val db = readableDatabase
        val query = if (typeFilter == null) {
            "SELECT $COL_ID, $COL_TYPE, $COL_AMOUNT, $COL_CURRENCY, $COL_MERCHANT, $COL_CATEGORY, $COL_SOURCE, $COL_TIMESTAMP FROM $TABLE_NAME ORDER BY $COL_TIMESTAMP DESC LIMIT ?"
        } else {
            "SELECT $COL_ID, $COL_TYPE, $COL_AMOUNT, $COL_CURRENCY, $COL_MERCHANT, $COL_CATEGORY, $COL_SOURCE, $COL_TIMESTAMP FROM $TABLE_NAME WHERE $COL_TYPE = ? ORDER BY $COL_TIMESTAMP DESC LIMIT ?"
        }

        val args = if (typeFilter == null) arrayOf(limit.toString()) else arrayOf(typeFilter, limit.toString())
        val cursor = db.rawQuery(query, args)
        val list = mutableListOf<TransactionRecord>()
        val sdf = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())

        while (cursor.moveToNext()) {
            val ts = cursor.getLong(7)
            list.add(
                TransactionRecord(
                    id = cursor.getLong(0),
                    type = cursor.getString(1),
                    amount = cursor.getDouble(2),
                    currency = cursor.getString(3),
                    merchant = cursor.getString(4),
                    category = cursor.getString(5),
                    source = cursor.getString(6),
                    timestamp = ts,
                    formattedTime = sdf.format(Date(ts))
                )
            )
        }
        cursor.close()
        return list
    }
}
