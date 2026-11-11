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
    val dateTimeEpoch: Long,       // Stored as epoch seconds (UTC)
    val dateEpochDay: Long,        // LocalDate.toEpochDay() for fast daily queries
    val upiRef: String? = null,    // For deduplication - UNIQUE index
    val rawSms: String? = null,
    val isManual: Boolean = false,
    val bank: String = "ICICI"
)
