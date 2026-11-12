package com.spendwise

import com.spendwise.data.repository.PreferencesRepository
import com.spendwise.data.repository.TransactionRepository
import com.spendwise.data.repository.UserPreferences
import com.spendwise.domain.budget.BudgetEngine
import com.spendwise.domain.model.BudgetPeriod
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class BudgetEngineTest {

    private val transactionRepository: TransactionRepository = mockk()
    private val preferencesRepository: PreferencesRepository = mockk()
    private val budgetEngine = BudgetEngine(transactionRepository, preferencesRepository)

    @org.junit.Before
    fun setupDefaults() {
        coEvery { transactionRepository.getTotalCreditedIncludedForDay(any()) } returns 0.0
        coEvery { transactionRepository.getTotalCreditedIncludedBetween(any(), any()) } returns 0.0
    }

    @Test
    fun `day 2 rolls over unspent balance from day 1 when budgetStartEpochDay is initialized`() = runTest {
        val day1 = LocalDate.of(2026, 11, 11)
        val day2 = LocalDate.of(2026, 11, 12)

        val prefs = UserPreferences(
            dailyBudget = 130.0,
            rolloverEnabled = true,
            budgetStartEpochDay = day1.toEpochDay()
        )

        coEvery { transactionRepository.getTotalSpentForDay(day2) } returns 0.0
        coEvery { transactionRepository.getTotalSpentBetween(day1, day2) } returns 24.0

        val state = budgetEngine.calculateBudgetState(prefs, today = day2)

        assertEquals(130.0, state.dailyBudget, 0.001)
        assertEquals(0.0, state.spentToday, 0.001)
        assertEquals(106.0, state.rolloverBalance, 0.001)
        assertEquals(236.0, state.totalAvailable, 0.001)
        assertEquals(236.0, state.remainingToday, 0.001)
    }

    @Test
    fun `uninitialized budgetStartEpochDay resolves to earliest transaction date and rolls over`() = runTest {
        val day1 = LocalDate.of(2026, 11, 11)
        val day2 = LocalDate.of(2026, 11, 12)

        val prefs = UserPreferences(
            dailyBudget = 130.0,
            rolloverEnabled = true,
            budgetStartEpochDay = 0L
        )

        coEvery { transactionRepository.getEarliestTransactionDate() } returns day1
        coEvery { preferencesRepository.ensureBudgetStartDayInitialized(day1.toEpochDay()) } returns day1.toEpochDay()
        coEvery { transactionRepository.getTotalSpentForDay(day2) } returns 0.0
        coEvery { transactionRepository.getTotalSpentBetween(day1, day2) } returns 24.0

        val state = budgetEngine.calculateBudgetState(prefs, today = day2)

        coVerify(exactly = 1) { preferencesRepository.ensureBudgetStartDayInitialized(day1.toEpochDay()) }
        assertEquals(106.0, state.rolloverBalance, 0.001)
        assertEquals(236.0, state.remainingToday, 0.001)
    }

    @Test
    fun `multi-day cumulative rollover computes accurately`() = runTest {
        val day1 = LocalDate.of(2026, 11, 11)
        val day3 = LocalDate.of(2026, 11, 13)

        // Day 1: 130 - 24 = +106; Day 2: 130 - 50 = +80 => total rollover = +186
        // Day 3 spent: 16 => remaining = 130 + 186 - 16 = 300
        val prefs = UserPreferences(
            dailyBudget = 130.0,
            rolloverEnabled = true,
            budgetStartEpochDay = day1.toEpochDay()
        )

        coEvery { transactionRepository.getTotalSpentForDay(day3) } returns 16.0
        coEvery { transactionRepository.getTotalSpentBetween(day1, day3) } returns (24.0 + 50.0)

        val state = budgetEngine.calculateBudgetState(prefs, today = day3)

        assertEquals(186.0, state.rolloverBalance, 0.001)
        assertEquals(316.0, state.totalAvailable, 0.001)
        assertEquals(300.0, state.remainingToday, 0.001)
    }

    @Test
    fun `overspending carries negative rollover deficit to next day`() = runTest {
        val day1 = LocalDate.of(2026, 11, 11)
        val day2 = LocalDate.of(2026, 11, 12)

        val prefs = UserPreferences(
            dailyBudget = 130.0,
            rolloverEnabled = true,
            budgetStartEpochDay = day1.toEpochDay()
        )

        coEvery { transactionRepository.getTotalSpentForDay(day2) } returns 0.0
        coEvery { transactionRepository.getTotalSpentBetween(day1, day2) } returns 180.0

        val state = budgetEngine.calculateBudgetState(prefs, today = day2)

        assertEquals(-50.0, state.rolloverBalance, 0.001)
        assertEquals(80.0, state.remainingToday, 0.001)
    }

    @Test
    fun `rollover disabled ignores previous days balance`() = runTest {
        val day1 = LocalDate.of(2026, 11, 11)
        val day2 = LocalDate.of(2026, 11, 12)

        val prefs = UserPreferences(
            dailyBudget = 130.0,
            rolloverEnabled = false,
            budgetStartEpochDay = day1.toEpochDay()
        )

        coEvery { transactionRepository.getTotalSpentForDay(day2) } returns 0.0

        val state = budgetEngine.calculateBudgetState(prefs, today = day2)

        assertEquals(0.0, state.rolloverBalance, 0.001)
        assertEquals(130.0, state.remainingToday, 0.001)
    }

    @Test
    fun `included credit today boosts totalAvailable and remainingToday`() = runTest {
        val day1 = LocalDate.of(2026, 11, 11)

        val prefs = UserPreferences(
            dailyBudget = 130.0,
            rolloverEnabled = true,
            budgetStartEpochDay = day1.toEpochDay(),
            includeCreditsInBudget = true
        )

        coEvery { transactionRepository.getTotalSpentForDay(day1) } returns 50.0
        coEvery { transactionRepository.getTotalCreditedIncludedForDay(day1) } returns 100.0

        val state = budgetEngine.calculateBudgetState(prefs, today = day1)

        assertEquals(100.0, state.creditedIncludedToday, 0.001)
        assertEquals(230.0, state.totalAvailable, 0.001)
        assertEquals(180.0, state.remainingToday, 0.001)
    }

    @Test
    fun `included credit on previous day carries forward into rollover`() = runTest {
        val day1 = LocalDate.of(2026, 11, 11)
        val day2 = LocalDate.of(2026, 11, 12)

        val prefs = UserPreferences(
            dailyBudget = 130.0,
            rolloverEnabled = true,
            budgetStartEpochDay = day1.toEpochDay()
        )

        coEvery { transactionRepository.getTotalSpentForDay(day2) } returns 0.0
        coEvery { transactionRepository.getTotalSpentBetween(day1, day2) } returns 30.0
        coEvery { transactionRepository.getTotalCreditedIncludedBetween(day1, day2) } returns 50.0

        val state = budgetEngine.calculateBudgetState(prefs, today = day2)

        // Day 1: 130 - 30 + 50 = +150 rollover; Day 2: 130 + 150 = 280
        assertEquals(150.0, state.rolloverBalance, 0.001)
        assertEquals(280.0, state.totalAvailable, 0.001)
        assertEquals(280.0, state.remainingToday, 0.001)
    }

    @Test
    fun `weekly budget mode computes remaining for current monday to sunday week`() = runTest {
        // Thursday 2026-11-12 -> Monday of that week is 2026-11-09
        val thursday = LocalDate.of(2026, 11, 12)
        val monday = LocalDate.of(2026, 11, 9)
        val tomorrow = thursday.plusDays(1)

        val prefs = UserPreferences(
            dailyBudget = 1000.0,
            budgetPeriod = BudgetPeriod.WEEKLY,
            rolloverEnabled = true,
            budgetStartEpochDay = monday.toEpochDay()
        )

        coEvery { transactionRepository.getTotalSpentForDay(thursday) } returns 120.0
        coEvery { transactionRepository.getTotalSpentBetween(monday, tomorrow) } returns 350.0

        val state = budgetEngine.calculateBudgetState(prefs, today = thursday)

        assertEquals(BudgetPeriod.WEEKLY, state.period)
        assertEquals(350.0, state.periodSpent, 0.001)
        assertEquals(1000.0, state.totalAvailable, 0.001)
        assertEquals(650.0, state.remainingToday, 0.001)
    }

    @Test
    fun `monthly budget mode carries unspent balance across months`() = runTest {
        val novStart = LocalDate.of(2026, 11, 1)
        val nov12 = LocalDate.of(2026, 11, 12)
        val octStart = LocalDate.of(2026, 10, 1)

        val prefs = UserPreferences(
            dailyBudget = 5000.0,
            budgetPeriod = BudgetPeriod.MONTHLY,
            rolloverEnabled = true,
            budgetStartEpochDay = octStart.toEpochDay()
        )

        coEvery { transactionRepository.getTotalSpentForDay(nov12) } returns 200.0
        coEvery { transactionRepository.getTotalSpentBetween(novStart, nov12.plusDays(1)) } returns 1200.0
        coEvery { transactionRepository.getTotalSpentBetween(octStart, novStart) } returns 4200.0

        val state = budgetEngine.calculateBudgetState(prefs, today = nov12)

        // Oct budget 5000 - spent 4200 = +800 rollover; Nov available = 5800; spent = 1200 -> 4600 left
        assertEquals(BudgetPeriod.MONTHLY, state.period)
        assertEquals(800.0, state.rolloverBalance, 0.001)
        assertEquals(5800.0, state.totalAvailable, 0.001)
        assertEquals(4600.0, state.remainingToday, 0.001)
    }

    @Test
    fun `no limit mode tracks month spend with zero budget limit`() = runTest {
        val novStart = LocalDate.of(2026, 11, 1)
        val nov12 = LocalDate.of(2026, 11, 12)

        val prefs = UserPreferences(
            dailyBudget = 130.0,
            budgetPeriod = BudgetPeriod.NO_LIMIT,
            budgetStartEpochDay = novStart.toEpochDay()
        )

        coEvery { transactionRepository.getTotalSpentForDay(nov12) } returns 95.0
        coEvery { transactionRepository.getTotalSpentBetween(novStart, nov12.plusDays(1)) } returns 2450.0

        val state = budgetEngine.calculateBudgetState(prefs, today = nov12)

        assertEquals(BudgetPeriod.NO_LIMIT, state.period)
        assertEquals(2450.0, state.periodSpent, 0.001)
        assertEquals(95.0, state.spentToday, 0.001)
        assertEquals(0.0, state.remainingToday, 0.001)
    }
}
