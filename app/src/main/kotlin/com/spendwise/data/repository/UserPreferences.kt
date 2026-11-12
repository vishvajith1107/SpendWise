package com.spendwise.data.repository

import com.spendwise.domain.model.BudgetPeriod

data class UserPreferences(
    val dailyBudget: Double = 500.0,       // Limit amount for the selected BudgetPeriod
    val budgetPeriod: BudgetPeriod = BudgetPeriod.DAILY,
    val rolloverEnabled: Boolean = true,
    val budgetStartEpochDay: Long = 0L,    // The epoch day from which budget tracking began
    val currency: String = "₹",
    val includeCreditsInBudget: Boolean = false,
    val themeMode: String = "SYSTEM",      // SYSTEM, LIGHT, DARK
    val pinHash: String? = null,
    val biometricEnabled: Boolean = false,
    val onboardingCompleted: Boolean = false,
    val dataRetentionDays: Int = 30        // Default 1 month (0 = keep all)
)
