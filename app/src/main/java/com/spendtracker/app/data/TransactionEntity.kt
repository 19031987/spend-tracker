package com.spendtracker.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter

enum class TransactionType { EXPENSE, INCOME, TRANSFER }

enum class TransactionCategory(val label: String, val type: TransactionType) {
    GROCERIES("Groceries", TransactionType.EXPENSE),
    UTILITIES("Utilities", TransactionType.EXPENSE),
    RENT("Rent", TransactionType.EXPENSE),
    TRANSPORT("Transport", TransactionType.EXPENSE),
    DINING("Dining", TransactionType.EXPENSE),
    SHOPPING("Shopping", TransactionType.EXPENSE),
    HEALTH("Health", TransactionType.EXPENSE),
    ENTERTAINMENT("Entertainment", TransactionType.EXPENSE),
    SUBSCRIPTIONS("Subscriptions", TransactionType.EXPENSE),
    TRAVEL("Travel", TransactionType.EXPENSE),
    EDUCATION("Education", TransactionType.EXPENSE),
    OTHER_EXPENSE("Other", TransactionType.EXPENSE),

    SALARY("Salary", TransactionType.INCOME),
    FREELANCE("Freelance", TransactionType.INCOME),
    INVESTMENT("Investment", TransactionType.INCOME),
    GIFT("Gift", TransactionType.INCOME),
    OTHER_INCOME("Other income", TransactionType.INCOME),

    INTERNAL_TRANSFER("Internal Transfer", TransactionType.TRANSFER);

    companion object {
        fun forType(type: TransactionType): List<TransactionCategory> =
            values().filter { it.type == type }

        fun fromName(name: String?): TransactionCategory =
            values().firstOrNull { it.name == name } ?: OTHER_EXPENSE
    }
}

class Converters {
    @TypeConverter
    fun fromType(type: TransactionType): String = type.name

    @TypeConverter
    fun toType(value: String): TransactionType = runCatching {
        TransactionType.valueOf(value)
    }.getOrDefault(TransactionType.EXPENSE)
}

/**
 * Signed minor units: EXPENSE < 0, INCOME > 0, TRANSFER -amount (source) / +amount (destination).
 */
@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["accountId"]),
        Index(value = ["timestamp"]),
        Index(value = ["pairedTransactionId"])
    ]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val amount: Long,
    val category: String?,
    val note: String?,
    val timestamp: Long,
    @ColumnInfo(defaultValue = "'EXPENSE'") val type: TransactionType = TransactionType.EXPENSE,
    val destinationAccountId: Long? = null,
    val pairedTransactionId: Long? = null,
    @ColumnInfo(defaultValue = "0") val excludeFromSpending: Boolean = false,
    val merchant: String? = null,
    val source: String? = null
)
