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
    val transactions: List<Transaction>
)

data class HistoryUiState(
    val groups: List<DailyTransactionGroup> = emptyList(),
    val totalSpentAllTime: Double = 0.0,
    val totalCount: Int = 0,
    val currency: String = "₹",
    val searchQuery: String = "",
    val isLoading: Boolean = true
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val app: Application,
    private val transactionRepository: TransactionRepository,
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")

    val uiState: StateFlow<HistoryUiState> = combine(
        transactionRepository.getAllTransactions(),
        preferencesRepository.userPreferences,
        _searchQuery
    ) { transactions, prefs, query ->
        val trimmedQuery = query.trim()
        val filtered = if (trimmedQuery.isEmpty()) {
            transactions
        } else {
            transactions.filter { tx ->
                tx.merchant.contains(trimmedQuery, ignoreCase = true) ||
                    tx.bank.contains(trimmedQuery, ignoreCase = true) ||
                    (tx.upiRef?.contains(trimmedQuery, ignoreCase = true) == true) ||
                    "%.2f".format(tx.amount).contains(trimmedQuery)
            }
        }

        val groups = filtered
            .groupBy { it.date }
            .entries
            .sortedByDescending { it.key }
            .map { (date, list) ->
                DailyTransactionGroup(
                    date = date,
                    totalSpent = list.sumOf { it.amount },
                    transactions = list
                )
            }

        HistoryUiState(
            groups = groups,
            totalSpentAllTime = filtered.sumOf { it.amount },
            totalCount = filtered.size,
            currency = prefs.currency,
            searchQuery = query,
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

    fun deleteTransaction(transaction: Transaction) {
        viewModelScope.launch {
            transactionRepository.deleteTransaction(transaction)
            BudgetWidgetUpdater.update(app)
        }
    }
}
