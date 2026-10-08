package com.spendtracker.app.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "category_groups")
data class CategoryGroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val emoji: String,
    val sortOrder: Int,
    val isBuiltIn: Boolean
)

/** [key] is the legacy enum name for built-ins and `c_xxxxxxxx` for user categories. */
@Entity(tableName = "categories", indices = [Index(value = ["groupId"])])
data class CategoryEntity(
    @PrimaryKey val key: String,
    val groupId: Long,
    val name: String,
    val emoji: String,
    val colorHex: String,
    val type: TransactionType,
    val isBuiltIn: Boolean,
    val isHidden: Boolean
)

@Entity(tableName = "merchant_rules")
data class MerchantRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val matchType: String,
    val pattern: String,
    val categoryKey: String,
    val createdAt: Long
)

data class HistoryRow(val merchant: String, val category: String, val uses: Int)

@Dao
abstract class CatalogDao {

    @Query("SELECT * FROM category_groups ORDER BY sortOrder, id")
    abstract fun observeGroups(): Flow<List<CategoryGroupEntity>>

    @Query("SELECT * FROM categories ORDER BY name COLLATE NOCASE")
    abstract fun observeCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM merchant_rules ORDER BY createdAt DESC, id DESC")
    abstract fun observeRules(): Flow<List<MerchantRuleEntity>>

    @Query("SELECT * FROM categories")
    abstract suspend fun getCategories(): List<CategoryEntity>

    @Query("SELECT * FROM category_groups")
    abstract suspend fun getGroups(): List<CategoryGroupEntity>

    @Query("SELECT * FROM merchant_rules ORDER BY id")
    abstract suspend fun getRules(): List<MerchantRuleEntity>

    @Query(
        "SELECT merchant, category, COUNT(*) AS uses FROM transactions " +
            "WHERE merchant IS NOT NULL AND category IS NOT NULL AND type != 'TRANSFER' " +
            "GROUP BY merchant, category"
    )
    abstract fun observeHistory(): Flow<List<HistoryRow>>

    @Insert abstract suspend fun insertGroup(group: CategoryGroupEntity): Long
    @Insert abstract suspend fun insertGroups(groups: List<CategoryGroupEntity>)
    @Insert abstract suspend fun insertCategory(category: CategoryEntity)
    @Insert abstract suspend fun insertCategories(categories: List<CategoryEntity>)
    @Insert abstract suspend fun insertRule(rule: MerchantRuleEntity): Long
    @Update abstract suspend fun updateGroup(group: CategoryGroupEntity)
    @Update abstract suspend fun updateCategory(category: CategoryEntity)
    @Update abstract suspend fun updateRule(rule: MerchantRuleEntity)

    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM category_groups")
    abstract suspend fun maxGroupOrder(): Int

    @Query("DELETE FROM categories WHERE `key` = :key")
    abstract suspend fun deleteCategory(key: String)

    @Query("DELETE FROM category_groups WHERE id = :id")
    abstract suspend fun deleteGroup(id: Long)

    @Query("DELETE FROM merchant_rules WHERE id = :id")
    abstract suspend fun deleteRule(id: Long)

    @Query("DELETE FROM merchant_rules WHERE categoryKey = :key")
    abstract suspend fun deleteRulesForCategory(key: String)

    @Query("UPDATE transactions SET category = NULL WHERE category = :key")
    abstract suspend fun uncategorizeTransactions(key: String)

    @Query("UPDATE categories SET groupId = :toGroup WHERE groupId = :fromGroup")
    abstract suspend fun moveCategories(fromGroup: Long, toGroup: Long)

    @Query("DELETE FROM categories") abstract suspend fun clearCategories()
    @Query("DELETE FROM category_groups") abstract suspend fun clearGroups()
    @Query("DELETE FROM merchant_rules") abstract suspend fun clearRules()

    @Transaction
    open suspend fun insertCategoryWithRule(
        newGroup: CategoryGroupEntity?,
        category: CategoryEntity,
        rule: MerchantRuleEntity?
    ) {
        val groupId = if (newGroup != null) insertGroup(newGroup) else category.groupId
        insertCategory(category.copy(groupId = groupId))
        if (rule != null) insertRule(rule)
    }

    @Transaction
    open suspend fun removeCategory(key: String, hide: Boolean, category: CategoryEntity) {
        if (hide) {
            updateCategory(category.copy(isHidden = true))
        } else {
            deleteRulesForCategory(key)
            uncategorizeTransactions(key)
            deleteCategory(key)
        }
    }

    @Transaction
    open suspend fun removeGroup(id: Long, fallbackGroupId: Long) {
        moveCategories(id, fallbackGroupId)
        deleteGroup(id)
    }

    @Transaction
    open suspend fun resetCatalog() {
        clearRules()
        clearCategories()
        clearGroups()
        insertGroups(CatalogSeed.groups)
        insertCategories(CatalogSeed.categories)
    }
}

/** Built-in groups and categories. Category keys match [TransactionCategory] names. */
object CatalogSeed {
    const val OTHER_GROUP_ID = 6L

    val groups = listOf(
        CategoryGroupEntity(1, "Food & Dining", "\uD83C\uDF7D\uFE0F", 1, true),
        CategoryGroupEntity(2, "Living", "\uD83C\uDFE0", 2, true),
        CategoryGroupEntity(3, "Transport", "\uD83D\uDE97", 3, true),
        CategoryGroupEntity(4, "Lifestyle", "\uD83C\uDFAD", 4, true),
        CategoryGroupEntity(5, "Income", "\uD83D\uDCB0", 5, true),
        CategoryGroupEntity(OTHER_GROUP_ID, "Other", "\uD83D\uDCE6", 6, true)
    )

    private fun expense(key: String, group: Long, name: String, emoji: String, color: String) =
        CategoryEntity(key, group, name, emoji, color, TransactionType.EXPENSE, true, false)

    private fun income(key: String, name: String, emoji: String, color: String) =
        CategoryEntity(key, 5, name, emoji, color, TransactionType.INCOME, true, false)

    private fun transfer(key: String, group: Long, name: String, emoji: String, color: String) =
        CategoryEntity(key, group, name, emoji, color, TransactionType.TRANSFER, true, false)

    val categories = listOf(
        expense("GROCERIES", 1, "Groceries", "\uD83D\uDED2", "#D1FAE5"),
        expense("DINING", 1, "Dining", "\uD83C\uDF7D\uFE0F", "#FFEDD5"),
        expense("RENT", 2, "Rent", "\uD83C\uDFE0", "#DBEAFE"),
        expense("UTILITIES", 2, "Utilities", "\uD83D\uDCA1", "#FEF3C7"),
        expense("HEALTH", 2, "Health", "\uD83D\uDC8A", "#FEE2E2"),
        expense("TRANSPORT", 3, "Transport", "\uD83D\uDE87", "#CFFAFE"),
        expense("TRAVEL", 3, "Travel", "\u2708\uFE0F", "#E0E7FF"),
        expense("SHOPPING", 4, "Shopping", "\uD83D\uDECD\uFE0F", "#FCE7F3"),
        expense("ENTERTAINMENT", 4, "Entertainment", "\uD83C\uDFAE", "#EDE9FE"),
        expense("SUBSCRIPTIONS", 4, "Subscriptions", "\uD83D\uDCFA", "#FBCFE8"),
        expense("EDUCATION", 4, "Education", "\uD83D\uDCDA", "#FDE68A"),
        expense("OTHER_EXPENSE", OTHER_GROUP_ID, "Other", "\uD83C\uDFF7\uFE0F", "#E2E8F0"),
        income("SALARY", "Salary", "\uD83D\uDCB7", "#D1FAE5"),
        income("FREELANCE", "Freelance", "\uD83D\uDCBB", "#CFFAFE"),
        income("INVESTMENT", "Investment", "\uD83D\uDCC8", "#DDD6FE"),
        income("GIFT", "Gift", "\uD83C\uDF81", "#FCE7F3"),
        income("OTHER_INCOME", "Other income", "\uD83D\uDCB0", "#E2E8F0"),
        transfer("INTERNAL_TRANSFER", OTHER_GROUP_ID, "Internal Transfer", "\uD83D\uDD04", "#EDE9FE")
    )

    /** Used by Room's onCreate / migration, where only raw SQL is available. */
    fun seed(db: SupportSQLiteDatabase) {
        groups.forEach {
            db.execSQL(
                "INSERT INTO category_groups(id, name, emoji, sortOrder, isBuiltIn) VALUES (?, ?, ?, ?, ?)",
                arrayOf<Any?>(it.id, it.name, it.emoji, it.sortOrder, if (it.isBuiltIn) 1 else 0)
            )
        }
        categories.forEach {
            db.execSQL(
                "INSERT INTO categories(`key`, groupId, name, emoji, colorHex, type, isBuiltIn, isHidden) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>(
                    it.key, it.groupId, it.name, it.emoji, it.colorHex, it.type.name,
                    if (it.isBuiltIn) 1 else 0, if (it.isHidden) 1 else 0
                )
            )
        }
    }
}
