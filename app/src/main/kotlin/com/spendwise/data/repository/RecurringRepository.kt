package com.spendwise.data.repository

import com.spendwise.data.db.RecurringRuleDao
import com.spendwise.data.db.toDomain
import com.spendwise.data.db.toEntity
import com.spendwise.domain.model.RecurringRule
import com.spendwise.domain.model.Transaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecurringRepository @Inject constructor(
    private val dao: RecurringRuleDao,
    private val transactionRepository: TransactionRepository,
    private val preferencesRepository: PreferencesRepository
) {
    private val processMutex = Mutex()

    fun getAllRules(): Flow<List<RecurringRule>> =
        dao.getAllRules().map { list -> list.map { it.toDomain() } }

    suspend fun getAllRulesSnapshot(): List<RecurringRule> =
        dao.getAllRulesOnce().map { it.toDomain() }

    suspend fun saveRule(rule: RecurringRule): Long {
        return if (rule.id == 0L) {
            dao.insert(rule.toEntity())
        } else {
            dao.update(rule.toEntity())
            rule.id
        }
    }

    suspend fun deleteRule(rule: RecurringRule) {
        dao.deleteById(rule.id)
    }

    suspend fun toggleActive(rule: RecurringRule, active: Boolean) {
        dao.update(rule.copy(isActive = active).toEntity())
    }

    suspend fun logRuleNow(rule: RecurringRule, today: LocalDate = LocalDate.now()) {
        processMutex.withLock {
            val prefs = preferencesRepository.userPreferences.first()
            insertOccurrence(rule, today)
            val nextDue = if (!rule.nextDueDate.isAfter(today)) {
                rule.frequency.nextOccurrence(today)
            } else {
                rule.nextDueDate
            }
            dao.update(
                rule.copy(
                    nextDueDate = nextDue,
                    lastLoggedDate = today
                ).toEntity()
            )
            extendBudgetStartIfNeeded(today, prefs)
        }
    }

    /**
     * Auto-logs any active recurring rules whose nextDueDate <= today, advancing nextDueDate safely.
     * Bounded to at most 31 catch-up cycles per rule to prevent runaway loops on very old dates.
     */
    suspend fun processDueAutoRules(today: LocalDate = LocalDate.now()): Int = processMutex.withLock {
        val dueEntities = dao.getDueAutoRules(today.toEpochDay())
        if (dueEntities.isEmpty()) return@withLock 0

        val prefs = preferencesRepository.userPreferences.first()
        var loggedCount = 0
        var earliestDateLogged: LocalDate? = null

        for (entity in dueEntities) {
            val rule = entity.toDomain()
            var due = rule.nextDueDate
            var lastLogged = rule.lastLoggedDate
            var iterations = 0

            while (!due.isAfter(today) && iterations < 31) {
                val inserted = insertOccurrence(rule, due)
                if (inserted) {
                    loggedCount++
                    if (earliestDateLogged == null || due.isBefore(earliestDateLogged)) {
                        earliestDateLogged = due
                    }
                }
                lastLogged = due
                due = rule.frequency.nextOccurrence(due)
                iterations++
            }

            // If rule is still in the past after catch-up cap, advance past today
            while (!due.isAfter(today)) {
                due = rule.frequency.nextOccurrence(due)
            }

            dao.update(
                rule.copy(
                    nextDueDate = due,
                    lastLoggedDate = lastLogged
                ).toEntity()
            )
        }

        if (earliestDateLogged != null) {
            extendBudgetStartIfNeeded(earliestDateLogged, prefs)
        }

        return@withLock loggedCount
    }

    private suspend fun insertOccurrence(
        rule: RecurringRule,
        date: LocalDate
    ): Boolean {
        val tx = Transaction(
            amount = rule.amount,
            merchant = rule.title,
            description = "Recurring ${rule.frequency.label.lowercase()}: ${rule.title}",
            dateTime = date.atTime(LocalTime.of(9, 0)),
            date = date,
            isManual = false,
            bank = "Recurring",
            isCredit = rule.isCredit,
            includeInBudget = if (rule.isCredit) rule.includeInBudget else false,
            category = rule.category
        )
        return transactionRepository.importTransactionIfNew(tx)
    }

    private suspend fun extendBudgetStartIfNeeded(date: LocalDate, prefs: UserPreferences) {
        if (prefs.budgetStartEpochDay == 0L || date.toEpochDay() < prefs.budgetStartEpochDay) {
            preferencesRepository.updateBudgetStartDay(date.toEpochDay())
        }
    }
}
