package com.spendwise.ui.settings

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendwise.data.repository.PreferencesRepository
import com.spendwise.data.repository.UserPreferences
import com.spendwise.ui.widget.BudgetWidgetUpdater
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val app: Application,
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    val preferences: StateFlow<UserPreferences?> = preferencesRepository.userPreferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun updateDailyBudget(budget: Double) {
        viewModelScope.launch {
            preferencesRepository.updateDailyBudget(budget)
            BudgetWidgetUpdater.update(app)
        }
    }

    fun updateRolloverEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.updateRolloverEnabled(enabled)
            BudgetWidgetUpdater.update(app)
        }
    }

    fun resetBudgetStartDay() {
        viewModelScope.launch {
            preferencesRepository.updateBudgetStartDay(LocalDate.now().toEpochDay())
            BudgetWidgetUpdater.update(app)
        }
    }

    fun updateIncludeCreditsInBudget(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.updateIncludeCreditsInBudget(enabled)
            BudgetWidgetUpdater.update(app)
        }
    }
}
