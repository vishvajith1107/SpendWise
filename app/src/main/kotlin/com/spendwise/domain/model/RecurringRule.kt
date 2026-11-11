package com.spendwise.domain.model

import java.time.LocalDate

enum class RecurringFrequency(val label: String) {
    DAILY("Daily"),
    WEEKLY("Weekly"),
    MONTHLY("Monthly");

    fun nextOccurrence(from: LocalDate): LocalDate = when (this) {
        DAILY -> from.plusDays(1)
        WEEKLY -> from.plusWeeks(1)
        MONTHLY -> from.plusMonths(1)
    }

    companion object {
        fun fromStorageKey(raw: String?): RecurringFrequency =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: MONTHLY
    }
}

data class RecurringRule(
    val id: Long = 0,
    val title: String,
    val amount: Double,
    val category: ExpenseCategory = ExpenseCategory.BILLS,
    val isCredit: Boolean = false,
    val includeInBudget: Boolean = false,
    val frequency: RecurringFrequency = RecurringFrequency.MONTHLY,
    val nextDueDate: LocalDate = LocalDate.now(),
    val autoLog: Boolean = true,
    val isActive: Boolean = true,
    val lastLoggedDate: LocalDate? = null
)
