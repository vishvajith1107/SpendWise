package com.spendwise.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    companion object {
        val KEY_DAILY_BUDGET = doublePreferencesKey("daily_budget")
        val KEY_ROLLOVER_ENABLED = booleanPreferencesKey("rollover_enabled")
        val KEY_BUDGET_START_EPOCH_DAY = longPreferencesKey("budget_start_epoch_day")
        val KEY_CURRENCY = stringPreferencesKey("currency")
    }

    val userPreferences: Flow<UserPreferences> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences())
            else throw exception
        }
        .map { prefs ->
            UserPreferences(
                dailyBudget = prefs[KEY_DAILY_BUDGET] ?: 500.0,
                rolloverEnabled = prefs[KEY_ROLLOVER_ENABLED] ?: true,
                budgetStartEpochDay = prefs[KEY_BUDGET_START_EPOCH_DAY]
                    ?: LocalDate.now().toEpochDay(),
                currency = prefs[KEY_CURRENCY] ?: "₹"
            )
        }

    suspend fun updateDailyBudget(budget: Double) {
        dataStore.edit { it[KEY_DAILY_BUDGET] = budget }
    }

    suspend fun updateRolloverEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_ROLLOVER_ENABLED] = enabled }
    }

    suspend fun updateBudgetStartDay(epochDay: Long) {
        dataStore.edit { it[KEY_BUDGET_START_EPOCH_DAY] = epochDay }
    }

    suspend fun updateCurrency(currency: String) {
        dataStore.edit { it[KEY_CURRENCY] = currency }
    }
}
