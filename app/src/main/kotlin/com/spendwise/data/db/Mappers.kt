package com.spendwise.data.db

import com.spendwise.domain.model.AppCategory
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
    val (builtIn, customKey) = AppCategory.splitKey(category)
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
        category = builtIn,
        customCategoryKey = customKey
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
        category = effectiveCategoryKey
    )
}

fun RecurringRuleEntity.toDomain(): RecurringRule {
    val (builtIn, customKey) = AppCategory.splitKey(category)
    return RecurringRule(
        id = id,
        title = title,
        amount = amount,
        category = builtIn,
        customCategoryKey = customKey,
        isCredit = isCredit,
        includeInBudget = includeInBudget,
        frequency = RecurringFrequency.fromStorageKey(frequency),
        nextDueDate = LocalDate.ofEpochDay(nextDueEpochDay),
        autoLog = autoLog,
        isActive = isActive,
        lastLoggedDate = lastLoggedEpochDay?.let { LocalDate.ofEpochDay(it) }
    )
}

fun RecurringRule.toEntity(): RecurringRuleEntity =
    RecurringRuleEntity(
        id = id,
        title = title,
        amount = amount,
        category = effectiveCategoryKey,
        isCredit = isCredit,
        includeInBudget = includeInBudget,
        frequency = frequency.name,
        nextDueEpochDay = nextDueDate.toEpochDay(),
        autoLog = autoLog,
        isActive = isActive,
        lastLoggedEpochDay = lastLoggedDate?.toEpochDay()
    )

fun CategoryEntity.toDomain(): AppCategory =
    AppCategory(
        key = key,
        displayName = displayName,
        colorHex = colorHex,
        iconKey = iconKey,
        isCreditCategory = isCreditCategory,
        isBuiltIn = isBuiltIn,
        sortOrder = sortOrder
    )

fun AppCategory.toEntity(): CategoryEntity =
    CategoryEntity(
        key = key,
        displayName = displayName,
        colorHex = colorHex,
        iconKey = iconKey,
        isCreditCategory = isCreditCategory,
        isBuiltIn = isBuiltIn,
        sortOrder = sortOrder
    )
