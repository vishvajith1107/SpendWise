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
        val KEY_INCLUDE_CREDITS_IN_BUDGET = booleanPreferencesKey("include_credits_in_budget")
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_PIN_HASH = stringPreferencesKey("pin_hash")
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
                // 0L signals uninitialized so BudgetEngine can resolve and persist start day once
                budgetStartEpochDay = prefs[KEY_BUDGET_START_EPOCH_DAY] ?: 0L,
                currency = prefs[KEY_CURRENCY] ?: "₹",
                includeCreditsInBudget = prefs[KEY_INCLUDE_CREDITS_IN_BUDGET] ?: false,
                themeMode = prefs[KEY_THEME_MODE] ?: "SYSTEM",
                pinHash = prefs[KEY_PIN_HASH]?.takeIf { it.isNotBlank() }
            )
        }

    /**
     * Persists [defaultEpochDay] as the budget start day if it has never been saved yet.
     * Returns the persisted epoch day.
     */
    suspend fun ensureBudgetStartDayInitialized(defaultEpochDay: Long): Long {
        var resolved = defaultEpochDay
        dataStore.edit { prefs ->
            val existing = prefs[KEY_BUDGET_START_EPOCH_DAY]
            if (existing == null || existing == 0L) {
                prefs[KEY_BUDGET_START_EPOCH_DAY] = defaultEpochDay
                resolved = defaultEpochDay
            } else {
                resolved = existing
            }
        }
        return resolved
    }

    suspend fun updateDailyBudget(budget: Double) {
        dataStore.edit { prefs ->
            prefs[KEY_DAILY_BUDGET] = budget
            if (prefs[KEY_BUDGET_START_EPOCH_DAY] == null || prefs[KEY_BUDGET_START_EPOCH_DAY] == 0L) {
                prefs[KEY_BUDGET_START_EPOCH_DAY] = LocalDate.now().toEpochDay()
            }
        }
    }

    suspend fun updateRolloverEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_ROLLOVER_ENABLED] = enabled
            if (prefs[KEY_BUDGET_START_EPOCH_DAY] == null || prefs[KEY_BUDGET_START_EPOCH_DAY] == 0L) {
                prefs[KEY_BUDGET_START_EPOCH_DAY] = LocalDate.now().toEpochDay()
            }
        }
    }

    suspend fun updateBudgetStartDay(epochDay: Long) {
        dataStore.edit { it[KEY_BUDGET_START_EPOCH_DAY] = epochDay }
    }

    suspend fun updateCurrency(currency: String) {
        dataStore.edit { it[KEY_CURRENCY] = currency }
    }

    suspend fun updateIncludeCreditsInBudget(enabled: Boolean) {
        dataStore.edit { it[KEY_INCLUDE_CREDITS_IN_BUDGET] = enabled }
    }

    suspend fun updateThemeMode(mode: String) {
        dataStore.edit { it[KEY_THEME_MODE] = mode }
    }

    suspend fun updatePinHash(hash: String?) {
        dataStore.edit { prefs ->
            if (hash.isNullOrBlank()) prefs.remove(KEY_PIN_HASH)
            else prefs[KEY_PIN_HASH] = hash
        }
    }

    suspend fun restorePreferences(
        dailyBudget: Double,
        rolloverEnabled: Boolean,
        budgetStartEpochDay: Long,
        currency: String,
        includeCreditsInBudget: Boolean
    ) {
        dataStore.edit { prefs ->
            prefs[KEY_DAILY_BUDGET] = dailyBudget
            prefs[KEY_ROLLOVER_ENABLED] = rolloverEnabled
            prefs[KEY_BUDGET_START_EPOCH_DAY] = budgetStartEpochDay
            prefs[KEY_CURRENCY] = currency
            prefs[KEY_INCLUDE_CREDITS_IN_BUDGET] = includeCreditsInBudget
        }
    }
}
