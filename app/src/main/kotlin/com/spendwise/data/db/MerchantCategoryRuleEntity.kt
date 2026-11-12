package com.spendwise.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "merchant_category_rules")
data class MerchantCategoryRuleEntity(
    @PrimaryKey
    val normalizedMerchant: String,
    val categoryKey: String,
    val updatedAtEpochMs: Long = System.currentTimeMillis()
)
