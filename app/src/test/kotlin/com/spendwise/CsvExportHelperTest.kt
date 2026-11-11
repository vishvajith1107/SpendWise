package com.spendwise

import com.spendwise.domain.export.CsvExportHelper
import com.spendwise.domain.model.ExpenseCategory
import com.spendwise.domain.model.Transaction
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class CsvExportHelperTest {

    @Test
    fun `escapes commas quotes and newlines cleanly in CSV output`() {
        val dt = LocalDateTime.of(2026, 11, 12, 9, 15, 30)
        val tx = Transaction(
            amount = 123.5,
            merchant = "Cafe \"Aroma\", BLR",
            description = "Morning coffee\nwith team",
            dateTime = dt,
            date = dt.toLocalDate(),
            upiRef = "123456",
            isManual = true,
            bank = "Manual",
            isCredit = false,
            includeInBudget = false,
            category = ExpenseCategory.FOOD
        )

        val csv = CsvExportHelper.generateCsv(listOf(tx))
        val lines = csv.trim().lines()
        assertTrue(lines.size == 2)
        assertTrue(lines[0].startsWith("Date,Time,Type,Merchant,Category,Amount"))
        assertTrue(lines[1].contains("\"Cafe \"\"Aroma\"\", BLR\""))
        assertTrue(lines[1].contains("Morning coffee with team"))
        assertTrue(lines[1].contains("Food & Dining"))
        assertTrue(lines[1].contains("123.50"))
    }
}
