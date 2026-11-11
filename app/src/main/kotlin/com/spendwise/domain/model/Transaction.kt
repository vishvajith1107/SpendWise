package com.spendwise.domain.model

import java.time.LocalDate
import java.time.LocalDateTime

data class Transaction(
    val id: Long = 0,
    val amount: Double,
    val merchant: String,
    val description: String,
    val dateTime: LocalDateTime,
    val date: LocalDate = dateTime.toLocalDate(),
    val upiRef: String? = null,
    val rawSms: String? = null,
    val isManual: Boolean = false,
    val bank: String = "ICICI"
)
