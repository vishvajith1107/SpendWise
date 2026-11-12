package com.spendwise.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["upiRef"], unique = true, name = "index_upi_ref"),
        Index(value = ["dateEpochDay"], name = "index_date")
    ]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val amount: Double,
    val merchant: String,
    val description: String,
    val dateTimeEpoch: Long,
    val dateEpochDay: Long,
    val upiRef: String? = null,
    val rawSms: String? = null,
    val isManual: Boolean = false,
    val bank: String = "ICICI",
    val isCredit: Boolean = false,
    val includeInBudget: Boolean = false,
    val excludeFromBudget: Boolean = false,
    val category: String = "OTHER"
)
