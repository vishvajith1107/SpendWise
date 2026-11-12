package com.spendwise.data.repository

import com.spendwise.data.db.CategoryDao
import com.spendwise.data.db.RecurringRuleDao
import com.spendwise.data.db.TransactionDao
import com.spendwise.data.db.toDomain
import com.spendwise.data.db.toEntity
import com.spendwise.domain.model.AppCategory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CategoryRepository @Inject constructor(
    private val dao: CategoryDao,
    private val transactionDao: TransactionDao,
    private val recurringRuleDao: RecurringRuleDao
) {
    private val seedMutex = Mutex()
    private var seeded = false

    suspend fun ensureSeeded() {
        if (seeded) return
        seedMutex.withLock {
            if (!seeded) {
                dao.insertAllIgnore(AppCategory.builtInDefaults().map { it.toEntity() })
                seeded = true
            }
        }
    }

    fun getAllCategories(): Flow<List<AppCategory>> =
        dao.getAllCategories()
            .onStart { ensureSeeded() }
            .map { list -> list.map { it.toDomain() } }

    suspend fun getAllCategoriesSnapshot(): List<AppCategory> {
        ensureSeeded()
        return dao.getAllCategoriesOnce().map { it.toDomain() }
    }

    suspend fun saveCategory(category: AppCategory) {
        ensureSeeded()
        dao.upsert(category.toEntity())
    }

    suspend fun createCustomCategory(
        name: String,
        colorHex: Long,
        iconKey: String,
        isCreditCategory: Boolean = false
    ): AppCategory {
        ensureSeeded()
        val existing = dao.getAllCategoriesOnce()
        val nextSortOrder = (existing.maxOfOrNull { it.sortOrder } ?: 9) + 1
        val key = "CUSTOM_${System.currentTimeMillis()}"
        val category = AppCategory(
            key = key,
            displayName = name.trim().take(32),
            colorHex = colorHex,
            iconKey = iconKey,
            isCreditCategory = isCreditCategory,
            isBuiltIn = false,
            sortOrder = nextSortOrder
        )
        dao.upsert(category.toEntity())
        return category
    }

    suspend fun deleteCustomCategory(key: String) {
        ensureSeeded()
        transactionDao.reassignCategory(oldKey = key, newKey = "OTHER")
        recurringRuleDao.reassignCategory(oldKey = key, newKey = "OTHER")
        dao.deleteCustomByKey(key)
    }

    suspend fun resetBuiltInDefaults() {
        AppCategory.builtInDefaults().forEach { dao.upsert(it.toEntity()) }
        seeded = true
    }
}
