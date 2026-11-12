package com.spendwise.data.db

import com.spendwise.domain.model.ExpenseCategory
import com.spendwise.domain.model.RecurringFrequency
import com.spendwise.domain.model.RecurringRule
import com.spendwise.domain.model.Transaction
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

fun TransactionEntity.toDomain(): Transaction {
    val zone = ZoneId.systemDefault()
    val dateTime = LocalDateTime.ofInstant(Instant.ofEpochSecond(dateTimeEpoch), zone)
    return Transaction(
        id = id,
        amount = amount,
        merchant = merchant,
        description = description,
        dateTime = dateTime,
        date = LocalDate.ofEpochDay(dateEpochDay),
        upiRef = upiRef,
        rawSms = rawSms,
        isManual = isManual,
        bank = bank,
        isCredit = isCredit,
        includeInBudget = includeInBudget,
        excludeFromBudget = excludeFromBudget,
        category = ExpenseCategory.fromStorageKey(category)
    )
}

fun Transaction.toEntity(): TransactionEntity {
    val zone = ZoneId.systemDefault()
    return TransactionEntity(
        id = id,
        amount = amount,
        merchant = merchant,
        description = description,
        dateTimeEpoch = dateTime.atZone(zone).toEpochSecond(),
        dateEpochDay = date.toEpochDay(),
        upiRef = upiRef,
        rawSms = rawSms,
        isManual = isManual,
        bank = bank,
        isCredit = isCredit,
        includeInBudget = includeInBudget,
        excludeFromBudget = excludeFromBudget,
        category = category.name
    )
}

fun RecurringRuleEntity.toDomain(): RecurringRule =
    RecurringRule(
        id = id,
        title = title,
        amount = amount,
        category = ExpenseCategory.fromStorageKey(category),
        isCredit = isCredit,
        includeInBudget = includeInBudget,
        frequency = RecurringFrequency.fromStorageKey(frequency),
        nextDueDate = LocalDate.ofEpochDay(nextDueEpochDay),
        autoLog = autoLog,
        isActive = isActive,
        lastLoggedDate = lastLoggedEpochDay?.let { LocalDate.ofEpochDay(it) }
    )

fun RecurringRule.toEntity(): RecurringRuleEntity =
    RecurringRuleEntity(
        id = id,
        title = title,
        amount = amount,
        category = category.name,
        isCredit = isCredit,
        includeInBudget = includeInBudget,
        frequency = frequency.name,
        nextDueEpochDay = nextDueDate.toEpochDay(),
        autoLog = autoLog,
        isActive = isActive,
        lastLoggedEpochDay = lastLoggedDate?.toEpochDay()
    )
