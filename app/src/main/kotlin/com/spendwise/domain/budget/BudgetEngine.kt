package com.spendwise.domain.budget

import com.spendwise.data.repository.PreferencesRepository
import com.spendwise.data.repository.TransactionRepository
import com.spendwise.data.repository.UserPreferences
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

data class BudgetState(
    val dailyBudget: Double,
    val spentToday: Double,
    val creditedIncludedToday: Double = 0.0,
    val rolloverBalance: Double,          // Positive = unspent/credited from previous days; negative = deficit
    val totalAvailable: Double,           // dailyBudget + rolloverBalance + creditedIncludedToday
    val remainingToday: Double,           // totalAvailable - spentToday
    val currency: String = "₹",
    val today: LocalDate = LocalDate.now()
)

@Singleton
class BudgetEngine @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val preferencesRepository: PreferencesRepository
) {
    /**
     * Calculates the current budget state for [today].
     *
     * Rollover logic:
     * - If budgetStartEpochDay has never been persisted (0L), initializes it permanently
     *   to the earliest transaction date (or today if none exist) so rollover accumulates across days.
     * - For all days in [startDay, today):
     *   rollover = (daysCount * dailyBudget) - totalSpentBetween(startDay, today) + totalCreditedIncludedBetween(startDay, today)
     * - Today's available = dailyBudget + rolloverBalance + creditedIncludedToday
     * - Remaining today = totalAvailable - spentToday
     */
    suspend fun calculateBudgetState(
        prefs: UserPreferences,
        today: LocalDate = LocalDate.now()
    ): BudgetState {
        val resolvedStartEpochDay = if (prefs.budgetStartEpochDay <= 0L) {
            val earliest = transactionRepository.getEarliestTransactionDate()
            val defaultStart = if (earliest != null && earliest.isBefore(today)) earliest else today
            preferencesRepository.ensureBudgetStartDayInitialized(defaultStart.toEpochDay())
        } else {
            prefs.budgetStartEpochDay
        }

        val budgetStartDay = LocalDate.ofEpochDay(resolvedStartEpochDay)
        val spentToday = transactionRepository.getTotalSpentForDay(today)
        val creditedIncludedToday = transactionRepository.getTotalCreditedIncludedForDay(today)

        val rolloverBalance = if (prefs.rolloverEnabled) {
            calculateRollover(prefs.dailyBudget, budgetStartDay, today)
        } else {
            0.0
        }

        val totalAvailable = prefs.dailyBudget + rolloverBalance + creditedIncludedToday
        val remainingToday = totalAvailable - spentToday

        return BudgetState(
            dailyBudget = prefs.dailyBudget,
            spentToday = spentToday,
            creditedIncludedToday = creditedIncludedToday,
            rolloverBalance = rolloverBalance,
            totalAvailable = totalAvailable,
            remainingToday = remainingToday,
            currency = prefs.currency,
            today = today
        )
    }

    private suspend fun calculateRollover(
        dailyBudget: Double,
        startDay: LocalDate,
        today: LocalDate
    ): Double {
        val daysCount = ChronoUnit.DAYS.between(startDay, today)
        if (daysCount <= 0L) return 0.0

        val totalSpentBeforeToday = transactionRepository.getTotalSpentBetween(startDay, today)
        val totalCreditedBeforeToday = transactionRepository.getTotalCreditedIncludedBetween(startDay, today)
        return (daysCount * dailyBudget) - totalSpentBeforeToday + totalCreditedBeforeToday
    }
}
