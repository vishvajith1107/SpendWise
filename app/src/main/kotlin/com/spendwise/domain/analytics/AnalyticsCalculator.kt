package com.spendwise.domain.analytics

import com.spendwise.domain.model.AppCategory
import com.spendwise.domain.model.ExpenseCategory
import com.spendwise.domain.model.Transaction
import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class AnalyticsPeriod(val label: String) {
    SEVEN_DAYS("7D"),
    THIRTY_DAYS("30D"),
    THIS_MONTH("This Month"),
    ALL_TIME("All Time")
}

data class CategorySpend(
    val category: ExpenseCategory,
    val amount: Double,
    val percentage: Float,
    val count: Int,
    val categoryKey: String = category.name,
    val displayName: String = category.displayName,
    val colorHex: Long = category.colorHex,
    val iconKey: String = AppCategory.defaultIconKey(category)
)

data class DailySpendPoint(
    val date: LocalDate,
    val spent: Double,
    val credited: Double,
    val isOverLimit: Boolean
)

data class MerchantStat(
    val merchant: String,
    val totalAmount: Double,
    val count: Int
)

data class BankStat(
    val bank: String,
    val totalSpent: Double,
    val count: Int
)

data class AnalyticsSnapshot(
    val period: AnalyticsPeriod,
    val totalSpent: Double,
    val totalCredited: Double,
    val netFlow: Double,
    val avgDailySpend: Double,
    val daysInPeriod: Int,
    val daysUnderLimit: Int,
    val currentUnderLimitStreak: Int,
    val highestSpendDay: Pair<LocalDate, Double>?,
    val categoryBreakdown: List<CategorySpend>,
    val dailySeries: List<DailySpendPoint>,
    val topMerchants: List<MerchantStat>,
    val bankBreakdown: List<BankStat>
)

object AnalyticsCalculator {

    fun compute(
        allTransactions: List<Transaction>,
        period: AnalyticsPeriod,
        dailyBudget: Double,
        today: LocalDate = LocalDate.now(),
        categoriesByKey: Map<String, AppCategory> = emptyMap()
    ): AnalyticsSnapshot {
        val startDate = when (period) {
            AnalyticsPeriod.SEVEN_DAYS -> today.minusDays(6)
            AnalyticsPeriod.THIRTY_DAYS -> today.minusDays(29)
            AnalyticsPeriod.THIS_MONTH -> today.withDayOfMonth(1)
            AnalyticsPeriod.ALL_TIME -> allTransactions.minOfOrNull { it.date } ?: today
        }
        val effectiveStart = if (startDate.isAfter(today)) today else startDate

        val scoped = allTransactions.filter { !it.date.isBefore(effectiveStart) && !it.date.isAfter(today) }
        val debits = scoped.filter { !it.isCredit }
        val credits = scoped.filter { it.isCredit }

        val totalSpent = debits.sumOf { it.amount }
        val totalCredited = credits.sumOf { it.amount }
        val netFlow = totalCredited - totalSpent

        val daysInPeriod = (ChronoUnit.DAYS.between(effectiveStart, today).toInt() + 1).coerceAtLeast(1)
        val avgDailySpend = totalSpent / daysInPeriod

        // Daily series across the window (for charts & streak)
        val byDate = scoped.groupBy { it.date }
        val dailySeries = (0 until daysInPeriod).map { offset ->
            val day = effectiveStart.plusDays(offset.toLong())
            val dayList = byDate[day].orEmpty()
            val spent = dayList.filter { !it.isCredit }.sumOf { it.amount }
            val credited = dayList.filter { it.isCredit }.sumOf { it.amount }
            DailySpendPoint(
                date = day,
                spent = spent,
                credited = credited,
                isOverLimit = dailyBudget > 0.0 && spent > dailyBudget
            )
        }

        val daysUnderLimit = dailySeries.count { !it.isOverLimit }

        // Current under-limit streak counting backwards from today across dailySeries
        var streak = 0
        for (point in dailySeries.asReversed()) {
            if (!point.isOverLimit) streak++ else break
        }

        val highestSpendDay = dailySeries
            .filter { it.spent > 0.0 }
            .maxByOrNull { it.spent }
            ?.let { it.date to it.spent }

        val categoryBreakdown = if (totalSpent > 0.0) {
            debits.groupBy { it.effectiveCategoryKey }
                .map { (key, list) ->
                    val resolved = AppCategory.resolve(key, categoriesByKey)
                    val sum = list.sumOf { it.amount }
                    CategorySpend(
                        category = ExpenseCategory.fromStorageKey(key),
                        amount = sum,
                        percentage = ((sum / totalSpent) * 100.0).toFloat(),
                        count = list.size,
                        categoryKey = resolved.key,
                        displayName = resolved.displayName,
                        colorHex = resolved.colorHex,
                        iconKey = resolved.iconKey
                    )
                }
                .sortedByDescending { it.amount }
        } else {
            emptyList()
        }

        val topMerchants = debits
            .groupBy { it.merchant }
            .map { (merchant, list) ->
                MerchantStat(
                    merchant = merchant,
                    totalAmount = list.sumOf { it.amount },
                    count = list.size
                )
            }
            .sortedByDescending { it.totalAmount }
            .take(6)

        val bankBreakdown = debits
            .groupBy { it.bank.ifBlank { "Other" } }
            .map { (bank, list) ->
                BankStat(
                    bank = bank,
                    totalSpent = list.sumOf { it.amount },
                    count = list.size
                )
            }
            .sortedByDescending { it.totalSpent }

        return AnalyticsSnapshot(
            period = period,
            totalSpent = totalSpent,
            totalCredited = totalCredited,
            netFlow = netFlow,
            avgDailySpend = avgDailySpend,
            daysInPeriod = daysInPeriod,
            daysUnderLimit = daysUnderLimit,
            currentUnderLimitStreak = streak,
            highestSpendDay = highestSpendDay,
            categoryBreakdown = categoryBreakdown,
            dailySeries = dailySeries,
            topMerchants = topMerchants,
            bankBreakdown = bankBreakdown
        )
    }
}
