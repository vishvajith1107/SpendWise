package com.spendwise.data.db

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
        bank = bank
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
        bank = bank
    )
}
