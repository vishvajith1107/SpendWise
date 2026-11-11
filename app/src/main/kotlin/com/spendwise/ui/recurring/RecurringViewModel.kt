package com.spendwise.ui.recurring

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.data.repository.PreferencesRepository
import com.spendwise.data.repository.RecurringRepository
import com.spendwise.domain.model.RecurringRule
import com.spendwise.ui.widget.BudgetWidgetUpdater
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RecurringUiState(
    val rules: List<RecurringRule> = emptyList(),
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
    preferencesRepository: PreferencesRepository
) : ViewModel() {

    private val _editingRule = MutableStateFlow<RecurringRule?>(null)
    private val _showAddSheet = MutableStateFlow(false)
    private val _statusMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<RecurringUiState> = combine(
        recurringRepository.getAllRules(),
        preferencesRepository.userPreferences,
        _editingRule,
        _showAddSheet,
        _statusMessage
    ) { rules, prefs, editing, showAdd, msg ->
        RecurringUiState(
            rules = rules,
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

    fun clearMessage() {
        _statusMessage.value = null
    }
}
