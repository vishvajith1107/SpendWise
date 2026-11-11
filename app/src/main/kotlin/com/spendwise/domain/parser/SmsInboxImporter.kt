package com.spendwise.domain.parser

import android.content.Context
import android.provider.Telephony
import com.spendwise.data.repository.PreferencesRepository
import com.spendwise.data.repository.TransactionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

data class SmsImportResult(
    val scannedCount: Int,
    val parsedCount: Int,
    val importedCount: Int,
    val skippedDuplicates: Int
)

@Singleton
class SmsInboxImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val transactionRepository: TransactionRepository,
    private val preferencesRepository: PreferencesRepository
) {
    /**
     * Scans the device SMS inbox for the past [daysBack] days, parses bank debit/credit SMS,
     * and inserts any non-duplicate transactions into Room on their actual receipt dates.
     */
    suspend fun importRecentSms(daysBack: Int = 30): SmsImportResult = withContext(Dispatchers.IO) {
        val prefs = preferencesRepository.userPreferences.first()
        val zone = ZoneId.systemDefault()
        val cutoffMillis = LocalDate.now()
            .minusDays(daysBack.toLong())
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()

        var scannedCount = 0
        var parsedCount = 0
        var importedCount = 0
        var skippedDuplicates = 0
        var earliestImportedDate: LocalDate? = null

        val projection = arrayOf(
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE
        )
        val selection = "${Telephony.Sms.DATE} >= ?"
        val selectionArgs = arrayOf(cutoffMillis.toString())
        val sortOrder = "${Telephony.Sms.DATE} ASC"

        context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val addrIdx = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
            val bodyIdx = cursor.getColumnIndex(Telephony.Sms.BODY)
            val dateIdx = cursor.getColumnIndex(Telephony.Sms.DATE)

            while (cursor.moveToNext()) {
                scannedCount++
                val sender = if (addrIdx >= 0) cursor.getString(addrIdx).orEmpty() else ""
                val body = if (bodyIdx >= 0) cursor.getString(bodyIdx).orEmpty() else ""
                val timestamp = if (dateIdx >= 0) cursor.getLong(dateIdx) else System.currentTimeMillis()
                if (sender.isBlank() || body.isBlank()) continue

                val receivedAt = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), zone)
                val parsed = SmsParser.parse(sender, body, receivedAt) ?: continue
                parsedCount++

                val toInsert = if (parsed.isCredit) {
                    parsed.copy(includeInBudget = prefs.includeCreditsInBudget)
                } else {
                    parsed
                }

                if (transactionRepository.importTransactionIfNew(toInsert)) {
                    importedCount++
                    if (earliestImportedDate == null || toInsert.date.isBefore(earliestImportedDate)) {
                        earliestImportedDate = toInsert.date
                    }
                } else {
                    skippedDuplicates++
                }
            }
        }

        if (earliestImportedDate != null) {
            if (prefs.budgetStartEpochDay == 0L || earliestImportedDate!!.toEpochDay() < prefs.budgetStartEpochDay) {
                preferencesRepository.updateBudgetStartDay(earliestImportedDate!!.toEpochDay())
            }
        }

        SmsImportResult(
            scannedCount = scannedCount,
            parsedCount = parsedCount,
            importedCount = importedCount,
            skippedDuplicates = skippedDuplicates
        )
    }
}
