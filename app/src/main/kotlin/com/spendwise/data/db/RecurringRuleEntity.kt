package com.spendwise.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recurring_rules")
data class RecurringRuleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val amount: Double,
    val category: String,
    val isCredit: Boolean = false,
    val includeInBudget: Boolean = false,
    val frequency: String,
    val nextDueEpochDay: Long,
    val autoLog: Boolean = true,
    val isActive: Boolean = true,
    val lastLoggedEpochDay: Long? = null
)
