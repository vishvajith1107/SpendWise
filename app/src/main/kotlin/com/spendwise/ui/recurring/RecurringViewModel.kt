package com.spendwise.ui.recurring

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.data.repository.CategoryRepository
import com.spendwise.data.repository.PreferencesRepository
import com.spendwise.data.repository.RecurringRepository
import com.spendwise.domain.model.AppCategory
import com.spendwise.domain.model.RecurringRule
import com.spendwise.ui.widget.BudgetWidgetUpdater
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RecurringUiState(
    val rules: List<RecurringRule> = emptyList(),
    val categories: List<AppCategory> = emptyList(),
    val currency: String = "₹",
    val defaultIncludeCreditsInBudget: Boolean = false,
    val editingRule: RecurringRule? = null,
    val showAddSheet: Boolean = false,
    val statusMessage: String? = null
)

@HiltViewModel
class RecurringViewModel @Inject constructor(
    private val app: Application,
    private val recurringRepository: RecurringRepository,
    private val categoryRepository: CategoryRepository,
    preferencesRepository: PreferencesRepository
) : ViewModel() {

    private val _editingRule = MutableStateFlow<RecurringRule?>(null)
    private val _showAddSheet = MutableStateFlow(false)
    private val _statusMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<RecurringUiState> = combine(
        combine(
            recurringRepository.getAllRules(),
            categoryRepository.getAllCategories(),
            preferencesRepository.userPreferences
        ) { rules, cats, prefs -> Triple(rules, cats, prefs) },
        _editingRule,
        _showAddSheet,
        _statusMessage
    ) { data, editing, showAdd, msg ->
        val (rules, categories, prefs) = data
        RecurringUiState(
            rules = rules,
            categories = categories,
            currency = prefs.currency,
            defaultIncludeCreditsInBudget = prefs.includeCreditsInBudget,
            editingRule = editing,
            showAddSheet = showAdd,
            statusMessage = msg
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RecurringUiState()
    )

    fun openAddSheet() {
        _editingRule.value = null
        _showAddSheet.value = true
    }

    fun startEditing(rule: RecurringRule) {
        _showAddSheet.value = false
        _editingRule.value = rule
    }

    fun dismissSheet() {
        _showAddSheet.value = false
        _editingRule.value = null
    }

    fun saveRule(rule: RecurringRule) {
        viewModelScope.launch {
            recurringRepository.saveRule(rule)
            recurringRepository.processDueAutoRules()
            BudgetWidgetUpdater.update(app)
            dismissSheet()
        }
    }

    fun toggleActive(rule: RecurringRule, active: Boolean) {
        viewModelScope.launch {
            recurringRepository.toggleActive(rule, active)
        }
    }

    fun logNow(rule: RecurringRule) {
        viewModelScope.launch {
            recurringRepository.logRuleNow(rule)
            BudgetWidgetUpdater.update(app)
            _statusMessage.value = "Logged '${rule.title}' for today!"
        }
    }

    fun deleteRule(rule: RecurringRule) {
        viewModelScope.launch {
            recurringRepository.deleteRule(rule)
            dismissSheet()
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

    fun clearMessage() {
        _statusMessage.value = null
    }
}
