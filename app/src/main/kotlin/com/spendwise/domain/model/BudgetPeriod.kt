package com.spendwise.domain.model

enum class BudgetPeriod(val label: String, val remainingLabel: String) {
    DAILY("Daily", "Remaining Today"),
    WEEKLY("Weekly", "Remaining This Week"),
    MONTHLY("Monthly", "Remaining This Month"),
    NO_LIMIT("No Limit", "Total Spent");

    companion object {
        fun fromStorageKey(key: String?): BudgetPeriod =
            entries.firstOrNull { it.name.equals(key, ignoreCase = true) } ?: DAILY
    }
}
