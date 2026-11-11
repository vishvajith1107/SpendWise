package com.spendwise.ui.dashboard

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.data.repository.PreferencesRepository
import com.spendwise.data.repository.TransactionRepository
import com.spendwise.domain.budget.BudgetEngine
import com.spendwise.domain.budget.BudgetState
import com.spendwise.domain.model.Transaction
import com.spendwise.ui.widget.BudgetWidgetUpdater
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import javax.inject.Inject

data class DashboardUiState(
    val budgetState: BudgetState? = null,
    val todayTransactions: List<Transaction> = emptyList(),
    val isLoading: Boolean = true,
    val showAddTransactionSheet: Boolean = false,
    val errorMessage: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val app: Application,
    private val transactionRepository: TransactionRepository,
    private val preferencesRepository: PreferencesRepository,
    private val budgetEngine: BudgetEngine
) : ViewModel() {

    private val _currentDate = MutableStateFlow(LocalDate.now())
    private val _showAddSheet = MutableStateFlow(false)
    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _currentDate
                .flatMapLatest { today ->
                    combine(
                        transactionRepository.getTransactionsForDay(today),
                        preferencesRepository.userPreferences,
                        _showAddSheet
                    ) { transactions, prefs, showSheet ->
                        val budgetState = budgetEngine.calculateBudgetState(prefs, today)
                        BudgetWidgetUpdater.update(app)
                        DashboardUiState(
                            budgetState = budgetState,
                            todayTransactions = transactions,
                            isLoading = false,
                            showAddTransactionSheet = showSheet
                        )
                    }
                }
                .catch { e ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = e.message) }
                }
                .collect { state ->
                    _uiState.value = state
                }
        }
    }

    fun refreshDateIfNeeded() {
        val now = LocalDate.now()
        if (_currentDate.value != now) {
            _currentDate.value = now
        }
    }

    fun showAddSheet() {
        _showAddSheet.value = true
    }

    fun hideAddSheet() {
        _showAddSheet.value = false
    }

    fun addManualTransaction(amount: Double, merchant: String, date: LocalDate = LocalDate.now()) {
        viewModelScope.launch {
            val now = LocalDateTime.now()
            val dateTime = if (date == now.toLocalDate()) now else date.atTime(LocalTime.NOON)
            val transaction = Transaction(
                amount = amount,
                merchant = merchant,
                description = "Manual: $merchant",
                dateTime = dateTime,
                date = date,
                isManual = true,
                bank = "Manual"
            )
            transactionRepository.insertTransaction(transaction)

            // If adding an expense for a past date (e.g., Yesterday), include that day in rollover
            val prefs = preferencesRepository.userPreferences.first()
            if (prefs.budgetStartEpochDay == 0L || date.toEpochDay() < prefs.budgetStartEpochDay) {
                preferencesRepository.updateBudgetStartDay(date.toEpochDay())
            }

            BudgetWidgetUpdater.update(app)
            hideAddSheet()
        }
    }

    fun deleteTransaction(transaction: Transaction) {
        viewModelScope.launch {
            transactionRepository.deleteTransaction(transaction)
            BudgetWidgetUpdater.update(app)
        }
    }
}
