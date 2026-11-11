package com.spendwise.ui.dashboard

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.data.repository.PreferencesRepository
import com.spendwise.data.repository.RecurringRepository
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
import javax.inject.Inject

data class DashboardUiState(
    val budgetState: BudgetState? = null,
    val todayTransactions: List<Transaction> = emptyList(),
    val upcomingRecurringCount: Int = 0,
    val isLoading: Boolean = true,
    val showAddTransactionSheet: Boolean = false,
    val editingTransaction: Transaction? = null,
    val defaultIncludeCreditsInBudget: Boolean = false,
    val errorMessage: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val app: Application,
    private val transactionRepository: TransactionRepository,
    private val preferencesRepository: PreferencesRepository,
    private val recurringRepository: RecurringRepository,
    private val budgetEngine: BudgetEngine
) : ViewModel() {

    private val _currentDate = MutableStateFlow(LocalDate.now())
    private val _showAddSheet = MutableStateFlow(false)
    private val _editingTransaction = MutableStateFlow<Transaction?>(null)
    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            recurringRepository.processDueAutoRules()
        }
        viewModelScope.launch {
            _currentDate
                .flatMapLatest { today ->
                    combine(
                        transactionRepository.getTransactionsForDay(today),
                        preferencesRepository.userPreferences,
                        recurringRepository.getAllRules(),
                        _showAddSheet,
                        _editingTransaction
                    ) { transactions, prefs, rules, showSheet, editingTx ->
                        val budgetState = budgetEngine.calculateBudgetState(prefs, today)
                        val upcomingDue = rules.count {
                            it.isActive && !it.nextDueDate.isAfter(today.plusDays(3))
                        }
                        BudgetWidgetUpdater.update(app)
                        DashboardUiState(
                            budgetState = budgetState,
                            todayTransactions = transactions,
                            upcomingRecurringCount = upcomingDue,
                            isLoading = false,
                            showAddTransactionSheet = showSheet,
                            editingTransaction = editingTx,
                            defaultIncludeCreditsInBudget = prefs.includeCreditsInBudget
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
        viewModelScope.launch {
            val logged = recurringRepository.processDueAutoRules(now)
            if (logged > 0) {
                BudgetWidgetUpdater.update(app)
            }
        }
    }

    fun showAddSheet() {
        _editingTransaction.value = null
        _showAddSheet.value = true
    }

    fun hideAddSheet() {
        _showAddSheet.value = false
    }

    fun startEditing(transaction: Transaction) {
        _showAddSheet.value = false
        _editingTransaction.value = transaction
    }

    fun stopEditing() {
        _editingTransaction.value = null
    }

    fun saveManualTransaction(transaction: Transaction) {
        viewModelScope.launch {
            transactionRepository.insertTransaction(transaction)
            extendBudgetStartDayIfNeeded(transaction.date)
            BudgetWidgetUpdater.update(app)
            hideAddSheet()
        }
    }

    fun updateTransaction(transaction: Transaction) {
        viewModelScope.launch {
            transactionRepository.updateTransaction(transaction)
            extendBudgetStartDayIfNeeded(transaction.date)
            BudgetWidgetUpdater.update(app)
            stopEditing()
        }
    }

    private suspend fun extendBudgetStartDayIfNeeded(date: LocalDate) {
        val prefs = preferencesRepository.userPreferences.first()
        if (prefs.budgetStartEpochDay == 0L || date.toEpochDay() < prefs.budgetStartEpochDay) {
            preferencesRepository.updateBudgetStartDay(date.toEpochDay())
        }
    }

    fun deleteTransaction(transaction: Transaction) {
        viewModelScope.launch {
            transactionRepository.deleteTransaction(transaction)
            BudgetWidgetUpdater.update(app)
            stopEditing()
        }
    }
}
