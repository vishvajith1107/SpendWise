package com.spendwise.domain.export

import com.spendwise.domain.model.Transaction
import java.time.format.DateTimeFormatter
import java.util.Locale

object CsvExportHelper {
    private val DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE
    private val TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.US)

    private const val HEADER =
        "Date,Time,Type,Merchant,Category,Amount,IncludedInDailyLimit,Bank,Source,UPIReference,Description"

    fun generateCsv(transactions: List<Transaction>): String {
        val sb = StringBuilder()
        sb.appendLine(HEADER)
        for (tx in transactions) {
            val type = if (tx.isCredit) "Credit" else "Debit"
            val source = if (tx.isManual) "Manual" else "Auto/SMS"
            val fields = listOf(
                tx.date.format(DATE_FMT),
                tx.dateTime.format(TIME_FMT),
                type,
                tx.merchant,
                tx.category.displayName,
                String.format(Locale.US, "%.2f", tx.amount),
                if (tx.isCredit) tx.includeInBudget.toString() else "N/A",
                tx.bank,
                source,
                tx.upiRef.orEmpty(),
                tx.description
            )
            sb.appendLine(fields.joinToString(",") { escapeCsv(it) })
        }
        return sb.toString()
    }

    internal fun escapeCsv(value: String): String {
        val cleaned = value.replace("\r\n", " ").replace("\n", " ").replace("\r", " ")
        return if (cleaned.contains(',') || cleaned.contains('"') || cleaned.contains('\n')) {
            "\"${cleaned.replace("\"", "\"\"")}\""
        } else {
            cleaned
        }
    }
}
