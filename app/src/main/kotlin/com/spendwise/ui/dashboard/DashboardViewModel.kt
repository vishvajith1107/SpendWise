package com.spendwise.ui.dashboard

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.data.repository.CategoryRepository
import com.spendwise.data.repository.PreferencesRepository
import com.spendwise.data.repository.RecurringRepository
import com.spendwise.data.repository.TransactionRepository
import com.spendwise.domain.budget.BudgetEngine
import com.spendwise.domain.budget.BudgetState
import com.spendwise.domain.model.AppCategory
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
    val categories: List<AppCategory> = emptyList(),
    val categoryUsageCounts: Map<String, Int> = emptyMap(),
    val uncategorizedUpiTransactions: List<Transaction> = emptyList(),
    val showUpiCategorizePrompt: Boolean = false,
    val showUpiCategorizeSheet: Boolean = false,
    val learnedMerchantRules: Map<String, String> = emptyMap(),
    val upcomingRecurringCount: Int = 0,
    val isLoading: Boolean = true,
    val showAddTransactionSheet: Boolean = false,
    val editingTransaction: Transaction? = null,
    val defaultIncludeCreditsInBudget: Boolean = false,
    val errorMessage: String? = null
)

private data class DashboardBaseData(
    val transactions: List<Transaction>,
    val prefs: com.spendwise.data.repository.UserPreferences,
    val rules: List<com.spendwise.domain.model.RecurringRule>,
    val categories: List<AppCategory>
)

private data class DashboardExtraDbData(
    val merchantRules: List<com.spendwise.data.db.MerchantCategoryRuleEntity>,
    val categoryUsageCounts: Map<String, Int>,
    val uncategorizedUpiTransactions: List<Transaction>
)

private data class DashboardUiControlData(
    val showAddSheet: Boolean,
    val editingTransaction: Transaction?,
    val sessionDismissedUpiPrompt: Boolean,
    val showUpiCategorizeSheet: Boolean
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val app: Application,
    private val transactionRepository: TransactionRepository,
    private val preferencesRepository: PreferencesRepository,
    private val recurringRepository: RecurringRepository,
    private val categoryRepository: CategoryRepository,
    private val budgetEngine: BudgetEngine
) : ViewModel() {

    private val _currentDate = MutableStateFlow(LocalDate.now())
    private val _showAddSheet = MutableStateFlow(false)
    private val _editingTransaction = MutableStateFlow<Transaction?>(null)
    private val _sessionDismissedUpiPrompt = MutableStateFlow(false)
    private val _showUpiCategorizeSheet = MutableStateFlow(false)
    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            recurringRepository.processDueAutoRules()
        }
        viewModelScope.launch {
            _currentDate
                .flatMapLatest { today ->
                    val baseFlow = combine(
                        transactionRepository.getTransactionsForDay(today),
                        transactionRepository.observeBudgetSignature(),
                        preferencesRepository.userPreferences,
                        recurringRepository.getAllRules(),
                        categoryRepository.getAllCategories()
                    ) { txs, _, prefs, rules, cats ->
                        DashboardBaseData(txs, prefs, rules, cats)
                    }

                    val extraDbFlow = combine(
                        categoryRepository.getMerchantRules(),
                        transactionRepository.getCategoryUsageCounts(),
                        transactionRepository.getUncategorizedAutoTransactions()
                    ) { rules, usageCounts, uncategorizedTxs ->
                        DashboardExtraDbData(rules, usageCounts, uncategorizedTxs)
                    }

                    val uiControlFlow = combine(
                        _showAddSheet,
                        _editingTransaction,
                        _sessionDismissedUpiPrompt,
                        _showUpiCategorizeSheet
                    ) { showSheet, editingTx, dismissedPrompt, showUpiSheet ->
                        DashboardUiControlData(showSheet, editingTx, dismissedPrompt, showUpiSheet)
                    }

                    combine(baseFlow, extraDbFlow, uiControlFlow) { base, extra, uiControl ->
                        val budgetState = budgetEngine.calculateBudgetState(base.prefs, today)
                        val upcomingDue = base.rules.count {
                            it.isActive && !it.nextDueDate.isAfter(today.plusDays(3))
                        }
                        val showPrompt = extra.uncategorizedUpiTransactions.isNotEmpty() &&
                            !uiControl.sessionDismissedUpiPrompt &&
                            !uiControl.showUpiCategorizeSheet

                        BudgetWidgetUpdater.update(app)
                        DashboardUiState(
                            budgetState = budgetState,
                            todayTransactions = base.transactions,
                            categories = base.categories,
                            categoryUsageCounts = extra.categoryUsageCounts,
                            uncategorizedUpiTransactions = extra.uncategorizedUpiTransactions,
                            showUpiCategorizePrompt = showPrompt,
                            showUpiCategorizeSheet = uiControl.showUpiCategorizeSheet,
                            learnedMerchantRules = extra.merchantRules.associate { it.normalizedMerchant to it.categoryKey },
                            upcomingRecurringCount = upcomingDue,
                            isLoading = false,
                            showAddTransactionSheet = uiControl.showAddSheet,
                            editingTransaction = uiControl.editingTransaction,
                            defaultIncludeCreditsInBudget = base.prefs.includeCreditsInBudget
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
            categoryRepository.learnMerchantCategory(transaction.merchant, transaction.effectiveCategoryKey)
            initializeBudgetStartDayIfNeeded(transaction)
            BudgetWidgetUpdater.update(app)
            hideAddSheet()
        }
    }

    fun updateTransaction(transaction: Transaction) {
        viewModelScope.launch {
            transactionRepository.updateTransaction(transaction)
            categoryRepository.learnMerchantCategory(transaction.merchant, transaction.effectiveCategoryKey)
            initializeBudgetStartDayIfNeeded(transaction)
            BudgetWidgetUpdater.update(app)
            stopEditing()
        }
    }

    private suspend fun initializeBudgetStartDayIfNeeded(transaction: Transaction) {
        val countsInBudget = !transaction.excludeFromBudget && (!transaction.isCredit || transaction.includeInBudget)
        if (!countsInBudget) return
        val prefs = preferencesRepository.userPreferences.first()
        if (prefs.budgetStartEpochDay <= 0L) {
            preferencesRepository.ensureBudgetStartDayInitialized(transaction.date.toEpochDay())
        }
    }

    fun deleteTransaction(transaction: Transaction) {
        viewModelScope.launch {
            transactionRepository.deleteTransaction(transaction)
            BudgetWidgetUpdater.update(app)
            stopEditing()
        }
    }

    fun dismissUpiPromptForSession() {
        _sessionDismissedUpiPrompt.value = true
    }

    fun openUpiCategorizeSheet() {
        _sessionDismissedUpiPrompt.value = true
        _showUpiCategorizeSheet.value = true
    }

    fun hideUpiCategorizeSheet() {
        _showUpiCategorizeSheet.value = false
    }

    fun categorizeTransaction(transaction: Transaction, categoryKey: String) {
        viewModelScope.launch {
            val (builtIn, customKey) = AppCategory.splitKey(categoryKey)
            val updated = transaction.copy(category = builtIn, customCategoryKey = customKey)
            transactionRepository.updateTransaction(updated)
            categoryRepository.learnMerchantCategory(transaction.merchant, categoryKey)
            initializeBudgetStartDayIfNeeded(updated)
            BudgetWidgetUpdater.update(app)
        }
    }
}
