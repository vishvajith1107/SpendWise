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
    private val preferencesRepository: PreferencesRepository,
    private val transactionRepository: com.spendwise.data.repository.TransactionRepository,
    private val smsInboxImporter: com.spendwise.domain.parser.SmsInboxImporter,
    private val backupRestoreHelper: com.spendwise.domain.export.BackupRestoreHelper
) : ViewModel() {

    val preferences: StateFlow<UserPreferences?> = preferencesRepository.userPreferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _isBusy = kotlinx.coroutines.flow.MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> get() = _isBusy

    private val _statusMessage = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> get() = _statusMessage

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

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun reportStatus(message: String) {
        _statusMessage.value = message
    }

    fun updateCurrency(currency: String) {
        viewModelScope.launch {
            preferencesRepository.updateCurrency(currency)
            BudgetWidgetUpdater.update(app)
            _statusMessage.value = "Currency updated to $currency"
        }
    }

    fun updateThemeMode(mode: String) {
        viewModelScope.launch {
            preferencesRepository.updateThemeMode(mode)
        }
    }

    fun setPin(pin: String) {
        viewModelScope.launch {
            val hash = com.spendwise.domain.security.PinSecurityHelper.hashPin(pin)
            preferencesRepository.updatePinHash(hash)
            _statusMessage.value = "App PIN lock enabled"
        }
    }

    fun removePin() {
        viewModelScope.launch {
            preferencesRepository.updatePinHash(null)
            _statusMessage.value = "App PIN lock disabled"
        }
    }

    fun scanSmsInbox(countTowardsAllowance: Boolean = false) {
        if (_isBusy.value) return
        viewModelScope.launch {
            _isBusy.value = true
            try {
                val result = smsInboxImporter.importRecentSms(
                    daysBack = 30,
                    countTowardsAllowance = countTowardsAllowance
                )
                BudgetWidgetUpdater.update(app)
                val modeLabel = if (countTowardsAllowance) "counted in allowance" else "history only"
                _statusMessage.value = if (result.importedCount > 0) {
                    "Imported ${result.importedCount} transaction(s) ($modeLabel) from ${result.scannedCount} SMS"
                } else {
                    "Scanned ${result.scannedCount} SMS: no new transactions found (${result.skippedDuplicates} already saved)"
                }
            } catch (e: Exception) {
                _statusMessage.value = "SMS scan failed: ${e.message ?: "Unknown error"}"
            } finally {
                _isBusy.value = false
            }
        }
    }

    suspend fun buildCsvExport(): String = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val transactions = transactionRepository.getAllTransactionsSnapshot()
        com.spendwise.domain.export.CsvExportHelper.generateCsv(transactions)
    }

    suspend fun buildJsonBackup(): String = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        backupRestoreHelper.createJsonBackup()
    }

    fun restoreJsonBackup(jsonText: String) {
        if (_isBusy.value) return
        viewModelScope.launch {
            _isBusy.value = true
            try {
                val summary = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    backupRestoreHelper.restoreFromJson(jsonText)
                }
                BudgetWidgetUpdater.update(app)
                _statusMessage.value =
                    "Restored ${summary.transactionsImported} transaction(s) and ${summary.recurringImported} recurring rule(s)"
            } catch (e: Exception) {
                _statusMessage.value = "Invalid backup file: ${e.message ?: "Unable to parse JSON"}"
            } finally {
                _isBusy.value = false
            }
        }
    }
}
