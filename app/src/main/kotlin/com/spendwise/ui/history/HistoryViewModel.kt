package com.spendwise.ui.history

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.data.repository.CategoryRepository
import com.spendwise.data.repository.PreferencesRepository
import com.spendwise.data.repository.TransactionRepository
import com.spendwise.domain.export.CsvExportHelper
import com.spendwise.domain.model.AppCategory
import com.spendwise.domain.model.Transaction
import com.spendwise.ui.widget.BudgetWidgetUpdater
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

enum class HistoryTypeFilter(val label: String) {
    ALL("All"),
    DEBITS("Expenses"),
    CREDITS("Credits")
}

data class HistoryFilter(
    val searchQuery: String = "",
    val typeFilter: HistoryTypeFilter = HistoryTypeFilter.ALL,
    val categoryKey: String? = null
)

data class DailyTransactionGroup(
    val date: LocalDate,
    val totalSpent: Double,
    val totalCredited: Double = 0.0,
    val transactions: List<Transaction>
)

data class HistoryUiState(
    val groups: List<DailyTransactionGroup> = emptyList(),
    val filteredTransactions: List<Transaction> = emptyList(),
    val categories: List<AppCategory> = emptyList(),
    val totalSpentAllTime: Double = 0.0,
    val totalCreditedAllTime: Double = 0.0,
    val totalCount: Int = 0,
    val currency: String = "₹",
    val searchQuery: String = "",
    val typeFilter: HistoryTypeFilter = HistoryTypeFilter.ALL,
    val selectedCategoryKey: String? = null,
    val editingTransaction: Transaction? = null,
    val defaultIncludeCreditsInBudget: Boolean = false,
    val isLoading: Boolean = true
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val app: Application,
    private val transactionRepository: TransactionRepository,
    private val preferencesRepository: PreferencesRepository,
    private val categoryRepository: CategoryRepository
) : ViewModel() {

    private val _filter = MutableStateFlow(HistoryFilter())
    private val _editingTransaction = MutableStateFlow<Transaction?>(null)

    val uiState: StateFlow<HistoryUiState> = combine(
        transactionRepository.getAllTransactions(),
        preferencesRepository.userPreferences,
        categoryRepository.getAllCategories(),
        _filter,
        _editingTransaction
    ) { transactions, prefs, categories, filter, editingTx ->
        val categoryMap = categories.associateBy { it.key }
        val trimmedQuery = filter.searchQuery.trim()
        val filtered = transactions.filter { tx ->
            val matchesType = when (filter.typeFilter) {
                HistoryTypeFilter.ALL -> true
                HistoryTypeFilter.DEBITS -> !tx.isCredit
                HistoryTypeFilter.CREDITS -> tx.isCredit
            }
            val matchesCategory = filter.categoryKey == null || tx.effectiveCategoryKey == filter.categoryKey
            val resolvedCatName = categoryMap[tx.effectiveCategoryKey]?.displayName ?: tx.category.displayName
            val matchesSearch = trimmedQuery.isEmpty() ||
                tx.merchant.contains(trimmedQuery, ignoreCase = true) ||
                tx.description.contains(trimmedQuery, ignoreCase = true) ||
                resolvedCatName.contains(trimmedQuery, ignoreCase = true) ||
                tx.bank.contains(trimmedQuery, ignoreCase = true) ||
                (tx.upiRef?.contains(trimmedQuery, ignoreCase = true) == true) ||
                "%.2f".format(tx.amount).contains(trimmedQuery) ||
                (tx.isCredit && "credit".contains(trimmedQuery, ignoreCase = true))

            matchesType && matchesCategory && matchesSearch
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
            filteredTransactions = filtered,
            categories = categories,
            totalSpentAllTime = filtered.filter { !it.isCredit }.sumOf { it.amount },
            totalCreditedAllTime = filtered.filter { it.isCredit }.sumOf { it.amount },
            totalCount = filtered.size,
            currency = prefs.currency,
            searchQuery = filter.searchQuery,
            typeFilter = filter.typeFilter,
            selectedCategoryKey = filter.categoryKey,
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
        _filter.update { it.copy(searchQuery = query) }
    }

    fun selectTypeFilter(type: HistoryTypeFilter) {
        _filter.update { it.copy(typeFilter = type) }
    }

    fun selectCategory(categoryKey: String?) {
        _filter.update { it.copy(categoryKey = categoryKey) }
    }

    fun buildCsvContent(): String {
        val state = uiState.value
        val namesMap = state.categories.associate { it.key to it.displayName }
        return CsvExportHelper.generateCsv(state.filteredTransactions, namesMap)
    }

    fun createCustomCategory(
        name: String,
        colorHex: Long,
        iconKey: String,
        onCreated: (String) -> Unit
    ) {
        viewModelScope.launch {
            val created = categoryRepository.createCustomCategory(name, colorHex, iconKey)
            onCreated(created.key)
        }
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
