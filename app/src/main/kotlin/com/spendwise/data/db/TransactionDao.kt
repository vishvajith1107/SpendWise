package com.spendwise.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Query("SELECT * FROM transactions WHERE dateEpochDay = :epochDay ORDER BY dateTimeEpoch DESC")
    fun getTransactionsForDay(epochDay: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE dateEpochDay >= :fromEpochDay ORDER BY dateTimeEpoch DESC")
    fun getTransactionsSince(fromEpochDay: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY dateTimeEpoch DESC")
    fun getAllTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT SUM(amount) FROM transactions WHERE dateEpochDay = :epochDay AND isCredit = 0")
    suspend fun getTotalSpentForDay(epochDay: Long): Double?

    @Query("SELECT SUM(amount) FROM transactions WHERE dateEpochDay >= :fromEpochDay AND dateEpochDay < :toEpochDay AND isCredit = 0")
    suspend fun getTotalSpentBetween(fromEpochDay: Long, toEpochDay: Long): Double?

    @Query("SELECT SUM(amount) FROM transactions WHERE dateEpochDay = :epochDay AND isCredit = 1 AND includeInBudget = 1")
    suspend fun getTotalCreditedIncludedForDay(epochDay: Long): Double?

    @Query("SELECT SUM(amount) FROM transactions WHERE dateEpochDay >= :fromEpochDay AND dateEpochDay < :toEpochDay AND isCredit = 1 AND includeInBudget = 1")
    suspend fun getTotalCreditedIncludedBetween(fromEpochDay: Long, toEpochDay: Long): Double?

    @Query("SELECT EXISTS(SELECT 1 FROM transactions WHERE upiRef = :upiRef LIMIT 1)")
    suspend fun existsByUpiRef(upiRef: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM transactions WHERE dateEpochDay = :epochDay AND ABS(amount - :amount) < 0.001 AND merchant = :merchant LIMIT 1)")
    suspend fun existsBySignature(epochDay: Long, amount: Double, merchant: String): Boolean

    @Query("SELECT * FROM transactions ORDER BY dateTimeEpoch DESC")
    suspend fun getAllTransactionsOnce(): List<TransactionEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(transaction: TransactionEntity): Long

    @androidx.room.Update
    suspend fun update(transaction: TransactionEntity)

    @Delete
    suspend fun delete(transaction: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM transactions ORDER BY dateTimeEpoch DESC LIMIT 1")
    suspend fun getLatestTransaction(): TransactionEntity?

    @Query("SELECT MIN(dateEpochDay) FROM transactions")
    suspend fun getEarliestEpochDay(): Long?
}
