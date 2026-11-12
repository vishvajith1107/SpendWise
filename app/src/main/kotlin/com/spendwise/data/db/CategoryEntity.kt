package com.spendwise.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey
    val key: String,
    val displayName: String,
    val colorHex: Long,
    val iconKey: String,
    val isCreditCategory: Boolean = false,
    val isBuiltIn: Boolean = false,
    val sortOrder: Int = 0
)
