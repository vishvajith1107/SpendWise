package com.spendwise.ui.analytics

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.data.db.MerchantCategoryRuleEntity
import com.spendwise.data.repository.CategoryRepository
import com.spendwise.data.repository.PreferencesRepository
import com.spendwise.data.repository.TransactionRepository
import com.spendwise.data.repository.UserPreferences
import com.spendwise.domain.analytics.AnalyticsCalculator
import com.spendwise.domain.analytics.AnalyticsPeriod
import com.spendwise.domain.analytics.AnalyticsSnapshot
import com.spendwise.domain.model.AppCategory
import com.spendwise.domain.model.BudgetPeriod
import com.spendwise.domain.model.Transaction
import com.spendwise.ui.widget.BudgetWidgetUpdater
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.YearMonth
import javax.inject.Inject

data class AnalyticsUiState(
    val snapshot: AnalyticsSnapshot? = null,
    val currency: String = "₹",
    val dailyBudget: Double = 500.0,
    val selectedPeriod: AnalyticsPeriod = AnalyticsPeriod.SEVEN_DAYS,
    val allTransactions: List<Transaction> = emptyList(),
    val categories: List<AppCategory> = emptyList(),
    val categoryUsageCounts: Map<String, Int> = emptyMap(),
    val learnedMerchantRules: Map<String, String> = emptyMap(),
    val defaultIncludeCreditsInBudget: Boolean = false,
    val editingTransaction: Transaction? = null,
    val isLoading: Boolean = true
)

private data class AnalyticsCoreData(
    val transactions: List<Transaction>,
    val prefs: UserPreferences,
    val categories: List<AppCategory>,
    val merchantRules: List<MerchantCategoryRuleEntity>
)

@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val app: Application,
    private val transactionRepository: TransactionRepository,
    private val preferencesRepository: PreferencesRepository,
    private val categoryRepository: CategoryRepository
) : ViewModel() {

    private val _selectedPeriod = MutableStateFlow(AnalyticsPeriod.SEVEN_DAYS)
    private val _editingTransaction = MutableStateFlow<Transaction?>(null)

    val uiState: StateFlow<AnalyticsUiState> = combine(
        combine(
            transactionRepository.getAllTransactions(),
            preferencesRepository.userPreferences,
            categoryRepository.getAllCategories(),
            categoryRepository.getMerchantRules()
        ) { txs, prefs, cats, rules ->
            AnalyticsCoreData(txs, prefs, cats, rules)
        },
        _selectedPeriod,
        _editingTransaction
    ) { core, period, editingTx ->
        val effectiveDailyLimit = when (core.prefs.budgetPeriod) {
            BudgetPeriod.DAILY -> core.prefs.dailyBudget
            BudgetPeriod.WEEKLY -> core.prefs.dailyBudget / 7.0
            BudgetPeriod.MONTHLY -> core.prefs.dailyBudget / YearMonth.now().lengthOfMonth().toDouble()
            BudgetPeriod.NO_LIMIT -> 0.0
        }
        val snapshot = AnalyticsCalculator.compute(
            allTransactions = core.transactions,
            period = period,
            dailyBudget = effectiveDailyLimit,
            categoriesByKey = core.categories.associateBy { it.key }
        )
        val usageCounts = core.transactions.groupingBy { it.effectiveCategoryKey }.eachCount()
        AnalyticsUiState(
            snapshot = snapshot,
            currency = core.prefs.currency,
            dailyBudget = effectiveDailyLimit,
            selectedPeriod = period,
            allTransactions = core.transactions,
            categories = core.categories,
            categoryUsageCounts = usageCounts,
            learnedMerchantRules = core.merchantRules.associate { it.normalizedMerchant to it.categoryKey },
            defaultIncludeCreditsInBudget = core.prefs.includeCreditsInBudget,
            editingTransaction = editingTx,
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

    fun startEditing(transaction: Transaction) {
        _editingTransaction.value = transaction
    }

    fun stopEditing() {
        _editingTransaction.value = null
    }

    fun updateTransaction(transaction: Transaction) {
        viewModelScope.launch {
            transactionRepository.updateTransaction(transaction)
            categoryRepository.learnMerchantCategory(transaction.merchant, transaction.effectiveCategoryKey)
            val countsInBudget = !transaction.excludeFromBudget && (!transaction.isCredit || transaction.includeInBudget)
            if (countsInBudget) {
                val prefs = preferencesRepository.userPreferences.first()
                if (prefs.budgetStartEpochDay <= 0L) {
                    preferencesRepository.ensureBudgetStartDayInitialized(transaction.date.toEpochDay())
                }
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
}
