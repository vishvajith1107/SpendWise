package com.spendwise.domain.budget

import com.spendwise.data.repository.TransactionRepository
import com.spendwise.data.repository.UserPreferences
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

data class BudgetState(
    val dailyBudget: Double,
    val spentToday: Double,
    val rolloverBalance: Double,          // Positive = unspent from previous days
    val totalAvailable: Double,           // dailyBudget + rolloverBalance
    val remainingToday: Double,           // totalAvailable - spentToday
    val currency: String = "₹",
    val today: LocalDate = LocalDate.now()
)

@Singleton
class BudgetEngine @Inject constructor(
    private val transactionRepository: TransactionRepository
) {
    /**
     * Calculates the current budget state.
     *
     * Rollover logic:
     * - For each past day since [budgetStartDay], compute: unspent = dailyBudget - spent
     * - Sum all unspent amounts (can be negative if overspent on a day)
     * - Today's available = dailyBudget + cumulativeRollover
     * - remaining = todayAvailable - spentToday
     */
    suspend fun calculateBudgetState(prefs: UserPreferences): BudgetState {
        val today = LocalDate.now()
        val budgetStartDay = LocalDate.ofEpochDay(prefs.budgetStartEpochDay)

        val spentToday = transactionRepository.getTotalSpentForDay(today)

        val rolloverBalance = if (prefs.rolloverEnabled) {
            calculateRollover(prefs.dailyBudget, budgetStartDay, today)
        } else {
            0.0
        }

        val totalAvailable = prefs.dailyBudget + rolloverBalance
        val remainingToday = totalAvailable - spentToday

        return BudgetState(
            dailyBudget = prefs.dailyBudget,
            spentToday = spentToday,
            rolloverBalance = rolloverBalance,
            totalAvailable = totalAvailable,
            remainingToday = remainingToday,
            currency = prefs.currency,
            today = today
        )
    }

    /**
     * Calculates cumulative rollover from [startDay] up to (not including) today.
     * Each past day contributes: dailyBudget - amountSpentThatDay
     * This can be negative on overspent days, which correctly reduces future budget.
     */
    private suspend fun calculateRollover(
        dailyBudget: Double,
        startDay: LocalDate,
        today: LocalDate
    ): Double {
        if (!startDay.isBefore(today)) return 0.0

        var rollover = 0.0
        var cursor = startDay
        while (cursor.isBefore(today)) {
            val spent = transactionRepository.getTotalSpentForDay(cursor)
            rollover += (dailyBudget - spent)
            cursor = cursor.plusDays(1)
        }
        return rollover
    }
}
