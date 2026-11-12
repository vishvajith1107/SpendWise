package com.spendwise.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MerchantCategoryRuleDao {

    @Query("SELECT * FROM merchant_category_rules ORDER BY updatedAtEpochMs DESC")
    fun getAllRules(): Flow<List<MerchantCategoryRuleEntity>>

    @Query("SELECT * FROM merchant_category_rules")
    suspend fun getAllRulesOnce(): List<MerchantCategoryRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: MerchantCategoryRuleEntity)

    @Query("UPDATE merchant_category_rules SET categoryKey = :newKey WHERE categoryKey = :oldKey")
    suspend fun reassignCategory(oldKey: String, newKey: String = "OTHER")

    @Query("DELETE FROM merchant_category_rules WHERE normalizedMerchant = :normalized")
    suspend fun deleteByMerchant(normalized: String)
}
