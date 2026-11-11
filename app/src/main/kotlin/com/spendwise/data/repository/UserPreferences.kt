package com.spendwise.data.repository

data class UserPreferences(
    val dailyBudget: Double = 500.0,       // Default ₹500/day
    val rolloverEnabled: Boolean = true,
    val budgetStartEpochDay: Long = 0L,    // The epoch day from which budget tracking began
    val currency: String = "₹",
    val includeCreditsInBudget: Boolean = false,
    val themeMode: String = "SYSTEM",      // SYSTEM, LIGHT, DARK
    val pinHash: String? = null
)
