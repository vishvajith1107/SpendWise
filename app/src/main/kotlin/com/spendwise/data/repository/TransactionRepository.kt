package com.spendwise.data.repository

import com.spendwise.data.db.TransactionDao
import com.spendwise.data.db.toDomain
import com.spendwise.data.db.toEntity
import com.spendwise.domain.model.Transaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransactionRepository @Inject constructor(
    private val dao: TransactionDao
) {
    fun getTransactionsForDay(date: LocalDate): Flow<List<Transaction>> =
        dao.getTransactionsForDay(date.toEpochDay())
            .map { list -> list.map { it.toDomain() } }

    fun getTransactionsSince(date: LocalDate): Flow<List<Transaction>> =
        dao.getTransactionsSince(date.toEpochDay())
            .map { list -> list.map { it.toDomain() } }

    fun getAllTransactions(): Flow<List<Transaction>> =
        dao.getAllTransactions()
            .map { list -> list.map { it.toDomain() } }

    suspend fun getTotalSpentForDay(date: LocalDate): Double =
        dao.getTotalSpentForDay(date.toEpochDay()) ?: 0.0

    suspend fun getTotalSpentBetween(from: LocalDate, to: LocalDate): Double =
        dao.getTotalSpentBetween(from.toEpochDay(), to.toEpochDay()) ?: 0.0

    suspend fun getTotalCreditedIncludedForDay(date: LocalDate): Double =
        dao.getTotalCreditedIncludedForDay(date.toEpochDay()) ?: 0.0

    suspend fun getTotalCreditedIncludedBetween(from: LocalDate, to: LocalDate): Double =
        dao.getTotalCreditedIncludedBetween(from.toEpochDay(), to.toEpochDay()) ?: 0.0

    suspend fun getEarliestTransactionDate(): LocalDate? =
        dao.getEarliestEpochDay()?.let { LocalDate.ofEpochDay(it) }

    /**
     * Inserts a transaction. Returns false if duplicate UPI ref detected (deduplication).
     */
    suspend fun insertTransaction(transaction: Transaction): Boolean {
        if (transaction.upiRef != null && dao.existsByUpiRef(transaction.upiRef)) {
            return false // Duplicate – skip silently
        }
        val rowId = dao.insert(transaction.toEntity())
        return rowId != -1L
    }

    suspend fun updateTransaction(transaction: Transaction) {
        dao.update(transaction.toEntity())
    }

    suspend fun deleteTransaction(transaction: Transaction) {
        dao.deleteById(transaction.id)
    }
}
