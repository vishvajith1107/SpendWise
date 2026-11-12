package com.spendwise.domain.budget

import com.spendwise.data.repository.PreferencesRepository
import com.spendwise.data.repository.TransactionRepository
import com.spendwise.data.repository.UserPreferences
import com.spendwise.domain.model.BudgetPeriod
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import javax.inject.Singleton

data class BudgetState(
    val dailyBudget: Double,
    val spentToday: Double,
    val creditedIncludedToday: Double = 0.0,
    val rolloverBalance: Double,          // Positive = unspent/credited from previous periods; negative = deficit
    val totalAvailable: Double,           // dailyBudget + rolloverBalance + periodCreditedIncluded
    val remainingToday: Double,           // totalAvailable - periodSpent
    val currency: String = "₹",
    val today: LocalDate = LocalDate.now(),
    val period: BudgetPeriod = BudgetPeriod.DAILY,
    val periodSpent: Double = spentToday,
    val periodCreditedIncluded: Double = creditedIncludedToday
)

@Singleton
class BudgetEngine @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val preferencesRepository: PreferencesRepository
) {
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
        val tomorrow = today.plusDays(1)

        return when (prefs.budgetPeriod) {
            BudgetPeriod.DAILY -> {
                val rolloverBalance = if (prefs.rolloverEnabled) {
                    calculateDailyRollover(prefs.dailyBudget, budgetStartDay, today)
                } else 0.0
                val totalAvailable = prefs.dailyBudget + rolloverBalance + creditedIncludedToday
                val remaining = totalAvailable - spentToday
                BudgetState(
                    dailyBudget = prefs.dailyBudget,
                    spentToday = spentToday,
                    creditedIncludedToday = creditedIncludedToday,
                    rolloverBalance = rolloverBalance,
                    totalAvailable = totalAvailable,
                    remainingToday = remaining,
                    currency = prefs.currency,
                    today = today,
                    period = BudgetPeriod.DAILY,
                    periodSpent = spentToday,
                    periodCreditedIncluded = creditedIncludedToday
                )
            }

            BudgetPeriod.WEEKLY -> {
                val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val periodSpent = transactionRepository.getTotalSpentBetween(weekStart, tomorrow)
                val periodCredited = transactionRepository.getTotalCreditedIncludedBetween(weekStart, tomorrow)
                val rolloverBalance = if (prefs.rolloverEnabled && budgetStartDay.isBefore(weekStart)) {
                    val startWeek = budgetStartDay.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                    val priorWeeks = ChronoUnit.WEEKS.between(startWeek, weekStart)
                    val priorSpent = transactionRepository.getTotalSpentBetween(budgetStartDay, weekStart)
                    val priorCredited = transactionRepository.getTotalCreditedIncludedBetween(budgetStartDay, weekStart)
                    (priorWeeks * prefs.dailyBudget) - priorSpent + priorCredited
                } else 0.0
                val totalAvailable = prefs.dailyBudget + rolloverBalance + periodCredited
                BudgetState(
                    dailyBudget = prefs.dailyBudget,
                    spentToday = spentToday,
                    creditedIncludedToday = creditedIncludedToday,
                    rolloverBalance = rolloverBalance,
                    totalAvailable = totalAvailable,
                    remainingToday = totalAvailable - periodSpent,
                    currency = prefs.currency,
                    today = today,
                    period = BudgetPeriod.WEEKLY,
                    periodSpent = periodSpent,
                    periodCreditedIncluded = periodCredited
                )
            }

            BudgetPeriod.MONTHLY -> {
                val monthStart = today.withDayOfMonth(1)
                val periodSpent = transactionRepository.getTotalSpentBetween(monthStart, tomorrow)
                val periodCredited = transactionRepository.getTotalCreditedIncludedBetween(monthStart, tomorrow)
                val rolloverBalance = if (prefs.rolloverEnabled && budgetStartDay.isBefore(monthStart)) {
                    val startMonth = budgetStartDay.withDayOfMonth(1)
                    val priorMonths = ChronoUnit.MONTHS.between(startMonth, monthStart)
                    val priorSpent = transactionRepository.getTotalSpentBetween(budgetStartDay, monthStart)
                    val priorCredited = transactionRepository.getTotalCreditedIncludedBetween(budgetStartDay, monthStart)
                    (priorMonths * prefs.dailyBudget) - priorSpent + priorCredited
                } else 0.0
                val totalAvailable = prefs.dailyBudget + rolloverBalance + periodCredited
                BudgetState(
                    dailyBudget = prefs.dailyBudget,
                    spentToday = spentToday,
                    creditedIncludedToday = creditedIncludedToday,
                    rolloverBalance = rolloverBalance,
                    totalAvailable = totalAvailable,
                    remainingToday = totalAvailable - periodSpent,
                    currency = prefs.currency,
                    today = today,
                    period = BudgetPeriod.MONTHLY,
                    periodSpent = periodSpent,
                    periodCreditedIncluded = periodCredited
                )
            }

            BudgetPeriod.NO_LIMIT -> {
                val monthStart = today.withDayOfMonth(1)
                val monthSpent = transactionRepository.getTotalSpentBetween(monthStart, tomorrow)
                val monthCredited = transactionRepository.getTotalCreditedIncludedBetween(monthStart, tomorrow)
                BudgetState(
                    dailyBudget = 0.0,
                    spentToday = spentToday,
                    creditedIncludedToday = creditedIncludedToday,
                    rolloverBalance = 0.0,
                    totalAvailable = 0.0,
                    remainingToday = 0.0,
                    currency = prefs.currency,
                    today = today,
                    period = BudgetPeriod.NO_LIMIT,
                    periodSpent = monthSpent,
                    periodCreditedIncluded = monthCredited
                )
            }
        }
    }

    private suspend fun calculateDailyRollover(
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
