package com.spendwise.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RecurringRuleDao {

    @Query("SELECT * FROM recurring_rules ORDER BY isActive DESC, nextDueEpochDay ASC, id DESC")
    fun getAllRules(): Flow<List<RecurringRuleEntity>>

    @Query("SELECT * FROM recurring_rules ORDER BY id ASC")
    suspend fun getAllRulesOnce(): List<RecurringRuleEntity>

    @Query("SELECT * FROM recurring_rules WHERE isActive = 1 AND autoLog = 1 AND nextDueEpochDay <= :todayEpochDay")
    suspend fun getDueAutoRules(todayEpochDay: Long): List<RecurringRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: RecurringRuleEntity): Long

    @Update
    suspend fun update(rule: RecurringRuleEntity)

    @Query("DELETE FROM recurring_rules WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE recurring_rules SET category = :newKey WHERE category = :oldKey")
    suspend fun reassignCategory(oldKey: String, newKey: String = "OTHER")
}
