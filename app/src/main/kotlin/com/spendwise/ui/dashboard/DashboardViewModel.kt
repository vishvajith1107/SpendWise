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
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

data class DashboardUiState(
    val budgetState: BudgetState? = null,
    val todayTransactions: List<Transaction> = emptyList(),
    val isLoading: Boolean = true,
    val showAddTransactionSheet: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val app: Application,
    private val transactionRepository: TransactionRepository,
    private val preferencesRepository: PreferencesRepository,
    private val budgetEngine: BudgetEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                transactionRepository.getTransactionsForDay(LocalDate.now()),
                preferencesRepository.userPreferences
            ) { transactions, prefs ->
                val budgetState = budgetEngine.calculateBudgetState(prefs)
                BudgetWidgetUpdater.update(app)
                DashboardUiState(
                    budgetState = budgetState,
                    todayTransactions = transactions,
                    isLoading = false
                )
            }.catch { e ->
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message) }
            }.collect { state ->
                _uiState.update { state }
            }
        }
    }

    fun showAddSheet() = _uiState.update { it.copy(showAddTransactionSheet = true) }
    fun hideAddSheet() = _uiState.update { it.copy(showAddTransactionSheet = false) }

    fun addManualTransaction(amount: Double, merchant: String) {
        viewModelScope.launch {
            val transaction = Transaction(
                amount = amount,
                merchant = merchant,
                description = "Manual: $merchant",
                dateTime = LocalDateTime.now(),
                isManual = true,
                bank = "Manual"
            )
            transactionRepository.insertTransaction(transaction)
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
