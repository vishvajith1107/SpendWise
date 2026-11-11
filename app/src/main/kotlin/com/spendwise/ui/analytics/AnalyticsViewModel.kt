package com.spendwise.ui.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.data.repository.PreferencesRepository
import com.spendwise.data.repository.TransactionRepository
import com.spendwise.domain.analytics.AnalyticsCalculator
import com.spendwise.domain.analytics.AnalyticsPeriod
import com.spendwise.domain.analytics.AnalyticsSnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
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
    preferencesRepository: PreferencesRepository
) : ViewModel() {

    private val _selectedPeriod = MutableStateFlow(AnalyticsPeriod.SEVEN_DAYS)

    val uiState: StateFlow<AnalyticsUiState> = combine(
        transactionRepository.getAllTransactions(),
        preferencesRepository.userPreferences,
        _selectedPeriod
    ) { transactions, prefs, period ->
        val snapshot = AnalyticsCalculator.compute(
            allTransactions = transactions,
            period = period,
            dailyBudget = prefs.dailyBudget
        )
        AnalyticsUiState(
            snapshot = snapshot,
            currency = prefs.currency,
            dailyBudget = prefs.dailyBudget,
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
