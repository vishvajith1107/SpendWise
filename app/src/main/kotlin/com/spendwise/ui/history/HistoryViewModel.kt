package com.spendwise.ui.history

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.data.repository.PreferencesRepository
import com.spendwise.data.repository.TransactionRepository
import com.spendwise.domain.model.Transaction
import com.spendwise.ui.widget.BudgetWidgetUpdater
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class DailyTransactionGroup(
    val date: LocalDate,
    val totalSpent: Double,
    val totalCredited: Double = 0.0,
    val transactions: List<Transaction>
)

data class HistoryUiState(
    val groups: List<DailyTransactionGroup> = emptyList(),
    val totalSpentAllTime: Double = 0.0,
    val totalCreditedAllTime: Double = 0.0,
    val totalCount: Int = 0,
    val currency: String = "₹",
    val searchQuery: String = "",
    val editingTransaction: Transaction? = null,
    val defaultIncludeCreditsInBudget: Boolean = false,
    val isLoading: Boolean = true
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val app: Application,
    private val transactionRepository: TransactionRepository,
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _editingTransaction = MutableStateFlow<Transaction?>(null)

    val uiState: StateFlow<HistoryUiState> = combine(
        transactionRepository.getAllTransactions(),
        preferencesRepository.userPreferences,
        _searchQuery,
        _editingTransaction
    ) { transactions, prefs, query, editingTx ->
        val trimmedQuery = query.trim()
        val filtered = if (trimmedQuery.isEmpty()) {
            transactions
        } else {
            transactions.filter { tx ->
                tx.merchant.contains(trimmedQuery, ignoreCase = true) ||
                    tx.description.contains(trimmedQuery, ignoreCase = true) ||
                    tx.bank.contains(trimmedQuery, ignoreCase = true) ||
                    (tx.upiRef?.contains(trimmedQuery, ignoreCase = true) == true) ||
                    "%.2f".format(tx.amount).contains(trimmedQuery) ||
                    (tx.isCredit && "credit".contains(trimmedQuery, ignoreCase = true))
            }
        }

        val groups = filtered
            .groupBy { it.date }
            .entries
            .sortedByDescending { it.key }
            .map { (date, list) ->
                DailyTransactionGroup(
                    date = date,
                    totalSpent = list.filter { !it.isCredit }.sumOf { it.amount },
                    totalCredited = list.filter { it.isCredit }.sumOf { it.amount },
                    transactions = list
                )
            }

        HistoryUiState(
            groups = groups,
            totalSpentAllTime = filtered.filter { !it.isCredit }.sumOf { it.amount },
            totalCreditedAllTime = filtered.filter { it.isCredit }.sumOf { it.amount },
            totalCount = filtered.size,
            currency = prefs.currency,
            searchQuery = query,
            editingTransaction = editingTx,
            defaultIncludeCreditsInBudget = prefs.includeCreditsInBudget,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HistoryUiState()
    )

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun startEditing(transaction: Transaction) {
        _editingTransaction.value = transaction
    }

    fun stopEditing() {
        _editingTransaction.value = null
    }

    fun updateTransaction(transaction: Transaction) {
        viewModelScope.launch {
            transactionRepository.updateTransaction(transaction)
            val prefs = preferencesRepository.userPreferences.first()
            if (prefs.budgetStartEpochDay == 0L || transaction.date.toEpochDay() < prefs.budgetStartEpochDay) {
                preferencesRepository.updateBudgetStartDay(transaction.date.toEpochDay())
            }
            BudgetWidgetUpdater.update(app)
            stopEditing()
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
