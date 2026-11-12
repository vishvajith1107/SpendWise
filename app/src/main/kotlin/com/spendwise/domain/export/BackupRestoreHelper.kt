package com.spendwise.domain.export

import com.spendwise.data.repository.PreferencesRepository
import com.spendwise.data.repository.RecurringRepository
import com.spendwise.data.repository.TransactionRepository
import com.spendwise.domain.model.ExpenseCategory
import com.spendwise.domain.model.RecurringFrequency
import com.spendwise.domain.model.RecurringRule
import com.spendwise.domain.model.Transaction
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

data class BackupRestoreSummary(
    val transactionsImported: Int,
    val transactionsSkipped: Int,
    val recurringImported: Int
)

@Singleton
class BackupRestoreHelper @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val recurringRepository: RecurringRepository,
    private val preferencesRepository: PreferencesRepository
) {
    suspend fun createJsonBackup(): String {
        val prefs = preferencesRepository.userPreferences.first()
        val transactions = transactionRepository.getAllTransactionsSnapshot()
        val rules = recurringRepository.getAllRulesSnapshot()
        val zone = ZoneId.systemDefault()

        val root = JSONObject().apply {
            put("schemaVersion", 1)
            put("app", "SpendWise")
            put("exportedAtEpochMs", System.currentTimeMillis())

            put("preferences", JSONObject().apply {
                put("dailyBudget", prefs.dailyBudget)
                put("rolloverEnabled", prefs.rolloverEnabled)
                put("budgetStartEpochDay", prefs.budgetStartEpochDay)
                put("currency", prefs.currency)
                put("includeCreditsInBudget", prefs.includeCreditsInBudget)
                put("themeMode", prefs.themeMode)
            })

            put("transactions", JSONArray().apply {
                for (tx in transactions) {
                    put(JSONObject().apply {
                        put("amount", tx.amount)
                        put("merchant", tx.merchant)
                        put("description", tx.description)
                        put("dateTimeEpochSec", tx.dateTime.atZone(zone).toEpochSecond())
                        put("dateEpochDay", tx.date.toEpochDay())
                        if (tx.upiRef != null) put("upiRef", tx.upiRef)
                        if (tx.rawSms != null) put("rawSms", tx.rawSms)
                        put("isManual", tx.isManual)
                        put("bank", tx.bank)
                        put("isCredit", tx.isCredit)
                        put("includeInBudget", tx.includeInBudget)
                        put("excludeFromBudget", tx.excludeFromBudget)
                        put("category", tx.category.name)
                    })
                }
            })

            put("recurringRules", JSONArray().apply {
                for (rule in rules) {
                    put(JSONObject().apply {
                        put("title", rule.title)
                        put("amount", rule.amount)
                        put("category", rule.category.name)
                        put("isCredit", rule.isCredit)
                        put("includeInBudget", rule.includeInBudget)
                        put("frequency", rule.frequency.name)
                        put("nextDueEpochDay", rule.nextDueDate.toEpochDay())
                        put("autoLog", rule.autoLog)
                        put("isActive", rule.isActive)
                        if (rule.lastLoggedDate != null) {
                            put("lastLoggedEpochDay", rule.lastLoggedDate.toEpochDay())
                        }
                    })
                }
            })
        }
        return root.toString(2)
    }

    suspend fun restoreFromJson(jsonText: String): BackupRestoreSummary {
        val root = JSONObject(jsonText)
        val zone = ZoneId.systemDefault()

        root.optJSONObject("preferences")?.let { p ->
            preferencesRepository.restorePreferences(
                dailyBudget = p.optDouble("dailyBudget", 500.0).takeIf { it > 0 } ?: 500.0,
                rolloverEnabled = p.optBoolean("rolloverEnabled", true),
                budgetStartEpochDay = p.optLong("budgetStartEpochDay", LocalDate.now().toEpochDay()),
                currency = p.optString("currency", "₹").ifBlank { "₹" },
                includeCreditsInBudget = p.optBoolean("includeCreditsInBudget", false)
            )
            p.optString("themeMode").takeIf { it.isNotBlank() }?.let {
                preferencesRepository.updateThemeMode(it)
            }
        }

        var txImported = 0
        var txSkipped = 0
        val txArray = root.optJSONArray("transactions") ?: JSONArray()
        for (i in 0 until txArray.length()) {
            val obj = txArray.getJSONObject(i)
            val epochSec = obj.optLong("dateTimeEpochSec", 0L)
            val epochDay = obj.optLong("dateEpochDay", LocalDate.now().toEpochDay())
            val date = LocalDate.ofEpochDay(epochDay)
            val dateTime = if (epochSec > 0L) {
                LocalDateTime.ofInstant(Instant.ofEpochSecond(epochSec), zone)
            } else {
                date.atStartOfDay()
            }
            val tx = Transaction(
                amount = obj.getDouble("amount"),
                merchant = obj.optString("merchant", "Transaction"),
                description = obj.optString("description", ""),
                dateTime = dateTime,
                date = date,
                upiRef = obj.optString("upiRef").takeIf { it.isNotBlank() },
                rawSms = obj.optString("rawSms").takeIf { it.isNotBlank() },
                isManual = obj.optBoolean("isManual", false),
                bank = obj.optString("bank", "Bank"),
                isCredit = obj.optBoolean("isCredit", false),
                includeInBudget = obj.optBoolean("includeInBudget", false),
                excludeFromBudget = obj.optBoolean("excludeFromBudget", false),
                category = ExpenseCategory.fromStorageKey(obj.optString("category"))
            )
            if (tx.amount > 0.0) {
                if (transactionRepository.importTransactionIfNew(tx)) txImported++
                else txSkipped++
            }
        }

        val existingRules = recurringRepository.getAllRulesSnapshot()
        var rulesImported = 0
        val ruleArray = root.optJSONArray("recurringRules") ?: JSONArray()
        for (i in 0 until ruleArray.length()) {
            val obj = ruleArray.getJSONObject(i)
            val title = obj.optString("title").trim()
            val amount = obj.optDouble("amount", 0.0)
            if (title.isBlank() || amount <= 0.0) continue

            val freq = RecurringFrequency.fromStorageKey(obj.optString("frequency"))
            val alreadyExists = existingRules.any {
                it.title.equals(title, ignoreCase = true) &&
                    kotlin.math.abs(it.amount - amount) < 0.001 &&
                    it.frequency == freq
            }
            if (!alreadyExists) {
                recurringRepository.saveRule(
                    RecurringRule(
                        title = title,
                        amount = amount,
                        category = ExpenseCategory.fromStorageKey(obj.optString("category")),
                        isCredit = obj.optBoolean("isCredit", false),
                        includeInBudget = obj.optBoolean("includeInBudget", false),
                        frequency = freq,
                        nextDueDate = LocalDate.ofEpochDay(obj.optLong("nextDueEpochDay", LocalDate.now().toEpochDay())),
                        autoLog = obj.optBoolean("autoLog", true),
                        isActive = obj.optBoolean("isActive", true),
                        lastLoggedDate = if (obj.has("lastLoggedEpochDay")) {
                            LocalDate.ofEpochDay(obj.getLong("lastLoggedEpochDay"))
                        } else null
                    )
                )
                rulesImported++
            }
        }

        return BackupRestoreSummary(
            transactionsImported = txImported,
            transactionsSkipped = txSkipped,
            recurringImported = rulesImported
        )
    }
}
