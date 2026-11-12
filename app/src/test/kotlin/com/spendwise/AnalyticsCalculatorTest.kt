package com.spendwise

import com.spendwise.domain.analytics.AnalyticsCalculator
import com.spendwise.domain.analytics.AnalyticsPeriod
import com.spendwise.domain.model.ExpenseCategory
import com.spendwise.domain.model.Transaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.LocalDate

class AnalyticsCalculatorTest {

    private val today = LocalDate.of(2026, 11, 12)

    private fun tx(
        daysAgo: Long,
        amount: Double,
        merchant: String = "Merchant",
        category: ExpenseCategory = ExpenseCategory.FOOD,
        bank: String = "HDFC",
        isCredit: Boolean = false
    ): Transaction {
        val d = today.minusDays(daysAgo)
        return Transaction(
            amount = amount,
            merchant = merchant,
            description = "",
            dateTime = d.atTime(12, 0),
            date = d,
            bank = bank,
            isCredit = isCredit,
            category = category
        )
    }

    @Test
    fun `computes totals, streak, peak day, categories and top merchants over 7D`() {
        val transactions = listOf(
            tx(daysAgo = 3, amount = 250.0, merchant = "Uber", category = ExpenseCategory.TRANSPORT, bank = "ICICI")
                .copy(excludeFromBudget = true),
            tx(daysAgo = 2, amount = 100.0, merchant = "Swiggy", category = ExpenseCategory.FOOD, bank = "HDFC"),
            tx(daysAgo = 1, amount = 50.0, merchant = "Swiggy", category = ExpenseCategory.FOOD, bank = "HDFC"),
            tx(daysAgo = 0, amount = 80.0, merchant = "Zepto", category = ExpenseCategory.GROCERIES, bank = "HDFC"),
            tx(daysAgo = 0, amount = 500.0, merchant = "Refund", category = ExpenseCategory.INCOME, isCredit = true),
            // Outside 7D window; excluded from SEVEN_DAYS
            tx(daysAgo = 15, amount = 999.0, merchant = "OldSpend", category = ExpenseCategory.SHOPPING)
        )

        val snap = AnalyticsCalculator.compute(
            allTransactions = transactions,
            period = AnalyticsPeriod.SEVEN_DAYS,
            dailyBudget = 200.0,
            today = today
        )

        assertEquals(7, snap.daysInPeriod)
        assertEquals(480.0, snap.totalSpent, 0.001)
        assertEquals(500.0, snap.totalCredited, 0.001)
        assertEquals(20.0, snap.netFlow, 0.001)
        assertEquals(480.0 / 7.0, snap.avgDailySpend, 0.001)
        // Day -3 spent 250 > 200 limit; remaining 6 days all <= 200
        assertEquals(6, snap.daysUnderLimit)
        assertEquals(3, snap.currentUnderLimitStreak)

        assertNotNull(snap.highestSpendDay)
        assertEquals(today.minusDays(3), snap.highestSpendDay!!.first)
        assertEquals(250.0, snap.highestSpendDay!!.second, 0.001)

        assertEquals("Uber", snap.topMerchants.first().merchant)
        assertEquals(ExpenseCategory.TRANSPORT, snap.categoryBreakdown.first().category)
        assertEquals(250.0f / 480.0f * 100f, snap.categoryBreakdown.first().percentage, 0.05f)
    }

    @Test
    fun `handles empty transaction list safely`() {
        val snap = AnalyticsCalculator.compute(
            allTransactions = emptyList(),
            period = AnalyticsPeriod.ALL_TIME,
            dailyBudget = 130.0,
            today = today
        )
        assertEquals(0.0, snap.totalSpent, 0.001)
        assertEquals(1, snap.daysInPeriod)
        assertEquals(1, snap.daysUnderLimit)
        assertEquals(null, snap.highestSpendDay)
        assertEquals(0, snap.categoryBreakdown.size)
    }
}
