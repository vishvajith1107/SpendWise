package com.spendwise.ui.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.data.repository.CategoryRepository
import com.spendwise.data.repository.PreferencesRepository
import com.spendwise.data.repository.TransactionRepository
import com.spendwise.domain.analytics.AnalyticsCalculator
import com.spendwise.domain.analytics.AnalyticsPeriod
import com.spendwise.domain.analytics.AnalyticsSnapshot
import com.spendwise.domain.model.BudgetPeriod
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import java.time.YearMonth
import javax.inject.Inject

data class AnalyticsUiState(
    val snapshot: AnalyticsSnapshot? = null,
    val currency: String = "₹",
    val dailyBudget: Double = 500.0,
    val selectedPeriod: AnalyticsPeriod = AnalyticsPeriod.SEVEN_DAYS,
    val isLoading: Boolean = true
)

@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    transactionRepository: TransactionRepository,
    preferencesRepository: PreferencesRepository,
    categoryRepository: CategoryRepository
) : ViewModel() {

    private val _selectedPeriod = MutableStateFlow(AnalyticsPeriod.SEVEN_DAYS)

    val uiState: StateFlow<AnalyticsUiState> = combine(
        transactionRepository.getAllTransactions(),
        preferencesRepository.userPreferences,
        categoryRepository.getAllCategories(),
        _selectedPeriod
    ) { transactions, prefs, categories, period ->
        val effectiveDailyLimit = when (prefs.budgetPeriod) {
            BudgetPeriod.DAILY -> prefs.dailyBudget
            BudgetPeriod.WEEKLY -> prefs.dailyBudget / 7.0
            BudgetPeriod.MONTHLY -> prefs.dailyBudget / YearMonth.now().lengthOfMonth().toDouble()
            BudgetPeriod.NO_LIMIT -> 0.0
        }
        val snapshot = AnalyticsCalculator.compute(
            allTransactions = transactions,
            period = period,
            dailyBudget = effectiveDailyLimit,
            categoriesByKey = categories.associateBy { it.key }
        )
        AnalyticsUiState(
            snapshot = snapshot,
            currency = prefs.currency,
            dailyBudget = effectiveDailyLimit,
            selectedPeriod = period,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AnalyticsUiState()
    )

    fun selectPeriod(period: AnalyticsPeriod) {
        _selectedPeriod.value = period
    }
}
