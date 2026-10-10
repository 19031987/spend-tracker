package com.spendtracker.app.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Locale

fun normalizeBankName(raw: String?, fallback: String = "Others"): String {
    if (raw.isNullOrBlank()) return fallback
    val trimmed = raw.trim().replace(Regex("\\s+"), " ")
    val lower = trimmed.lowercase(Locale.ROOT)
    return when {
        lower == "chase" || lower == "chase bank" || lower == "chase uk" || lower.startsWith("chase ") || lower.endsWith(" chase") -> "Chase"
        lower == "hsbc" || lower == "hsbc uk" || lower == "hsbc bank" || lower.startsWith("hsbc ") || lower.endsWith(" hsbc") -> "HSBC"
        lower == "monzo" || lower == "monzo bank" || lower.startsWith("monzo ") || lower.endsWith(" monzo") -> "Monzo"
        lower == "starling" || lower == "starling bank" || lower.startsWith("starling ") || lower.endsWith(" starling") -> "Starling"
        lower == "revolut" || lower == "revolut bank" || lower.startsWith("revolut ") || lower.endsWith(" revolut") -> "Revolut"
        lower == "barclays" || lower == "barclay" || lower == "barclaycard" || lower.startsWith("barclays ") || lower.startsWith("barclay ") || lower.endsWith(" barclays") -> "Barclays"
        lower == "santander" || lower == "santander uk" || lower.startsWith("santander ") || lower.endsWith(" santander") -> "Santander"
        lower == "natwest" || lower == "nat west" || lower.startsWith("natwest ") || lower.startsWith("nat west ") || lower.endsWith(" natwest") -> "NatWest"
        lower == "lloyds" || lower == "lloyds bank" || lower.startsWith("lloyds ") || lower.endsWith(" lloyds") -> "Lloyds"
        lower == "halifax" || lower == "halifax bank" || lower.startsWith("halifax ") || lower.endsWith(" halifax") -> "Halifax"
        lower == "nationwide" || lower == "nationwide building society" || lower.startsWith("nationwide ") || lower.endsWith(" nationwide") -> "Nationwide"
        lower == "paypal" || lower.startsWith("paypal ") || lower.endsWith(" paypal") -> "PayPal"
        lower == "google pay" || lower == "google wallet" || lower == "gpay" || lower.startsWith("google pay") || lower.startsWith("google wallet") || lower.startsWith("gpay") -> "Google Pay"
        lower == "apple pay" || lower.startsWith("apple pay") || lower.endsWith(" apple pay") -> "Apple Pay"
        lower == "samsung pay" || lower == "samsung wallet" || lower.startsWith("samsung pay") || lower.startsWith("samsung wallet") -> "Samsung Pay"
        lower == "amex" || lower == "american express" || lower == "americanexpress" || lower.startsWith("amex ") || lower.startsWith("american express") -> "Amex"
        lower == "tsb" || lower == "tsb bank" || lower.startsWith("tsb ") -> "TSB"
        lower == "rbs" || lower == "royal bank of scotland" || lower.startsWith("rbs ") -> "RBS"
        lower == "first direct" || lower == "firstdirect" || lower.startsWith("first direct") || lower.startsWith("firstdirect") -> "First Direct"
        lower == "virgin money" || lower.startsWith("virgin money") -> "Virgin Money"
        lower == "metro bank" || lower == "metro" || lower.startsWith("metro bank") || lower.startsWith("metro ") -> "Metro Bank"
        lower == "kroo" || lower == "kroo bank" || lower.startsWith("kroo ") -> "Kroo"
        lower == "savings" || lower == "saving" || lower.startsWith("savings ") || lower.startsWith("saving ") -> "Savings"
        lower == "credit card" || lower == "creditcard" || lower.startsWith("credit card") || lower.startsWith("creditcard") -> "Credit Card"
        lower == "others" || lower == "other" || lower == "card payment" || lower == "bank alert" || lower == "unknown" || lower.startsWith("other ") || lower == "bank" || lower == "banking" || lower == "mobile banking" || lower == "mobile" || lower == "app" -> "Others"
        else -> {
            trimmed.split(" ").joinToString(" ") { word ->
                word.lowercase(Locale.ROOT).replaceFirstChar {
                    if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString()
                }
            }
        }
    }
}

@Entity(
    tableName = "accounts",
    indices = [Index(value = ["name"], unique = true)]
)
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val name: String
)

@Dao
abstract class AccountDao {
    @Query("SELECT * FROM accounts ORDER BY id ASC")
    abstract fun observeAllRaw(): Flow<List<AccountEntity>>

    open fun observeAll(): Flow<List<AccountEntity>> = observeAllRaw().map { accounts ->
        accounts
            .map { it.copy(name = normalizeBankName(it.name)) }
            .distinctBy { it.name.lowercase(Locale.ROOT) }
            .sortedBy { it.name }
    }

    @Query("""
        SELECT * FROM accounts 
        WHERE LOWER(TRIM(name)) = LOWER(TRIM(:name)) 
        ORDER BY id ASC 
        LIMIT 1
    """)
    abstract suspend fun findByNameExact(name: String): AccountEntity?

    open suspend fun findByName(name: String): AccountEntity? {
        if (name.isBlank()) return null
        val normalized = normalizeBankName(name)
        val match = findByNameExact(normalized) ?: findByNameExact(name.trim())
        if (match != null) return match.copy(name = normalizeBankName(match.name))
        return getAllRaw().firstOrNull {
            normalizeBankName(it.name).equals(normalized, ignoreCase = true)
        }?.let { it.copy(name = normalizeBankName(it.name)) }
    }

    @Query("SELECT * FROM accounts ORDER BY id ASC")
    abstract suspend fun getAllRaw(): List<AccountEntity>

    open suspend fun getAll(): List<AccountEntity> {
        return getAllRaw()
            .map { it.copy(name = normalizeBankName(it.name)) }
            .distinctBy { it.name.lowercase(Locale.ROOT) }
            .sortedBy { it.name }
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertRaw(account: AccountEntity): Long

    open suspend fun getOrCreate(name: String): AccountEntity {
        val normalized = normalizeBankName(name)
        val existing = findByName(normalized)
        if (existing != null) {
            return existing.copy(name = normalized)
        }
        val entity = AccountEntity(name = normalized)
        val id = insertRaw(entity)
        return if (id > 0) {
            entity.copy(id = id)
        } else {
            findByName(normalized)
                ?: getAll().firstOrNull { it.name.equals(normalized, ignoreCase = true) }
                ?: entity.copy(id = 1L)
        }
    }

    open suspend fun insert(account: AccountEntity): Long {
        val normalizedName = normalizeBankName(account.name)
        val existing = findByName(normalizedName)
        if (existing != null) {
            return existing.id
        }
        val id = insertRaw(account.copy(name = normalizedName))
        if (id <= 0) {
            return findByName(normalizedName)?.id ?: 0L
        }
        return id
    }

    @Query("DELETE FROM accounts WHERE id = :id")
    abstract suspend fun deleteById(id: Long): Int

    @androidx.room.Update
    abstract suspend fun update(account: AccountEntity)

    @Query("UPDATE transactions SET accountId = :canonicalId WHERE accountId = :duplicateId")
    abstract suspend fun remapTransactionAccountId(duplicateId: Long, canonicalId: Long): Int

    @Query("UPDATE transactions SET destinationAccountId = :canonicalId WHERE destinationAccountId = :duplicateId")
    abstract suspend fun remapTransactionDestinationAccountId(duplicateId: Long, canonicalId: Long): Int

    @Query("UPDATE transactions SET source = :canonicalName WHERE accountId = :canonicalId")
    abstract suspend fun updateTransactionSource(canonicalId: Long, canonicalName: String): Int

    @androidx.room.Transaction
    open suspend fun consolidateDuplicates(): Int {
        val all = getAllRaw()
        val grouped = all.groupBy { normalizeBankName(it.name).lowercase(Locale.ROOT) }
        var deleted = 0
        for ((_, group) in grouped) {
            if (group.size > 1) {
                val canonical = group.minByOrNull { it.id }!!
                val canonicalName = normalizeBankName(canonical.name)
                if (canonical.name != canonicalName) {
                    update(canonical.copy(name = canonicalName))
                }
                val duplicates = group.filter { it.id != canonical.id }
                for (dup in duplicates) {
                    remapTransactionAccountId(dup.id, canonical.id)
                    remapTransactionDestinationAccountId(dup.id, canonical.id)
                    deleteById(dup.id)
                    deleted++
                }
                updateTransactionSource(canonical.id, canonicalName)
            } else if (group.isNotEmpty()) {
                val single = group.first()
                val canonicalName = normalizeBankName(single.name)
                if (single.name != canonicalName) {
                    update(single.copy(name = canonicalName))
                    updateTransactionSource(single.id, canonicalName)
                }
            }
        }
        return deleted
    }

    open suspend fun deleteDuplicates(): Int = consolidateDuplicates()
}

