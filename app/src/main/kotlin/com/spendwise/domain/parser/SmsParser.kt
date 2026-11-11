package com.spendwise.domain.parser

import com.spendwise.domain.model.Transaction
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Parses Indian bank debit SMS messages into Transaction objects.
 *
 * Supported formats (priority order):
 * 1. ICICI Bank: "ICICI Bank Acct XX530 debited for Rs 20.00 on 11-Nov-26; THE ULTIMATE EN credited. UPI:668141780387."
 * 2. Generic Indian bank debit patterns (SBI, HDFC, Axis, Kotak, etc.)
 */
object SmsParser {

    private val parsers: List<BankSmsParser> = listOf(
        IciciSmsParser,
        HdfcSmsParser,
        SbiSmsParser,
        GenericDebitSmsParser
    )

    /**
     * Attempts to parse an SMS from a bank sender into a Transaction.
     * Returns null if the SMS is not a recognized debit message.
     */
    fun parse(sender: String, body: String, receivedAt: LocalDateTime = LocalDateTime.now()): Transaction? {
        if (isOtpOrIrrelevant(body)) return null

        for (parser in parsers) {
            if (parser.canHandle(sender, body)) {
                val tx = parser.parse(body, receivedAt)
                if (tx != null) return tx
            }
        }
        return null
    }

    private fun isOtpOrIrrelevant(body: String): Boolean {
        val lower = body.lowercase(Locale.ROOT)
        return when {
            // OTP and authentication patterns
            lower.contains("otp") -> true
            lower.contains("one time password") -> true
            Regex("""\b\d{4,8}\b.{0,30}(valid|expire|minutes?)""").containsMatchIn(lower) -> true

            // Credits directly to user's account (not a spend)
            lower.contains("credited to your") -> true
            lower.contains("credited into your") -> true
            lower.contains("deposited to your") -> true
            lower.contains("received rs") && !lower.contains("debit") -> true

            // Balance alerts and account queries
            lower.contains("balance is") && !lower.contains("debit") -> true
            lower.contains("mini statement") -> true
            else -> false
        }
    }
}

interface BankSmsParser {
    fun canHandle(sender: String, body: String): Boolean
    fun parse(body: String, receivedAt: LocalDateTime): Transaction?
}

// ─── ICICI Bank Parser ────────────────────────────────────────────────────────
object IciciSmsParser : BankSmsParser {
    // Exact format from user's SMS:
    // "ICICI Bank Acct XX530 debited for Rs 20.00 on 11-Nov-26; THE ULTIMATE EN credited. UPI:668141780387."
    private val DEBIT_PATTERN_PRIMARY = Regex(
        """ICICI Bank Acct\s+[A-Za-z0-9*]+\s+debited\s+for\s+(?:Rs\.?|INR)\s*([\d,]+\.?\d*)\s+on\s+([\d\w-]+);\s*(.+?)\s+credited\.?\s*UPI:(\d+)""",
        RegexOption.IGNORE_CASE
    )

    private val DEBIT_PATTERN_SECONDARY = Regex(
        """debited\s+for\s+(?:Rs\.?|INR)\s*([\d,]+\.?\d*)\s+on\s+([\d\w-]+);?\s*(.+?)\s*(?:credited|\.)?\s*UPI:(\d+)""",
        RegexOption.IGNORE_CASE
    )

    private val dateFormatters = listOf(
        DateTimeFormatter.ofPattern("dd-MMM-yy", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("d-MMM-yy", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("dd/MM/yy", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.ENGLISH)
    )

    override fun canHandle(sender: String, body: String): Boolean {
        val s = sender.uppercase()
        val isIciciSender = s.contains("ICICI") || s == "AX-ICICIT-S" || s == "VM-ICICIB" || s.contains("ICICIB")
        val isIciciBody = body.contains("ICICI Bank", ignoreCase = true)
        val hasDebit = body.contains("debited", ignoreCase = true)
        return (isIciciSender || isIciciBody) && hasDebit
    }

    override fun parse(body: String, receivedAt: LocalDateTime): Transaction? {
        val match = DEBIT_PATTERN_PRIMARY.find(body) ?: DEBIT_PATTERN_SECONDARY.find(body) ?: return null
        val (amountStr, dateStr, merchant, upiRef) = match.destructured
        val amount = amountStr.replace(",", "").toDoubleOrNull() ?: return null

        val parsedDate = dateFormatters.firstNotNullOfOrNull { fmt ->
            runCatching { java.time.LocalDate.parse(dateStr.trim(), fmt) }.getOrNull()
        } ?: receivedAt.toLocalDate()

        val dateTime = parsedDate.atTime(receivedAt.toLocalTime())

        return Transaction(
            amount = amount,
            merchant = merchant.trim().take(60),
            description = "UPI debit - ${merchant.trim()}",
            dateTime = dateTime,
            upiRef = upiRef.trim(),
            rawSms = body,
            isManual = false,
            bank = "ICICI"
        )
    }
}

// ─── HDFC Bank Parser ─────────────────────────────────────────────────────────
object HdfcSmsParser : BankSmsParser {
    private val DEBIT_PATTERN = Regex(
        """Rs\.?\s*([\d,]+\.?\d*)\s+debited\s+from\s+HDFC.+?Info:\s*(.+?)\.\s""",
        RegexOption.IGNORE_CASE
    )
    private val UPI_REF_PATTERN = Regex("""UPI[:\-/](\d{10,})""", RegexOption.IGNORE_CASE)

    override fun canHandle(sender: String, body: String): Boolean =
        (sender.uppercase().contains("HDFC") || body.contains("HDFC Bank", ignoreCase = true)) &&
                body.contains("debited", ignoreCase = true)

    override fun parse(body: String, receivedAt: LocalDateTime): Transaction? {
        val match = DEBIT_PATTERN.find(body) ?: return null
        val (amountStr, info) = match.destructured
        val amount = amountStr.replace(",", "").toDoubleOrNull() ?: return null
        val upiRef = UPI_REF_PATTERN.find(body)?.groupValues?.getOrNull(1)
        return Transaction(
            amount = amount,
            merchant = info.trim().take(60),
            description = "HDFC debit - ${info.trim()}",
            dateTime = receivedAt,
            upiRef = upiRef,
            rawSms = body,
            isManual = false,
            bank = "HDFC"
        )
    }
}

// ─── SBI Parser ───────────────────────────────────────────────────────────────
object SbiSmsParser : BankSmsParser {
    private val DEBIT_PATTERN = Regex(
        """debited\s+by\s+Rs\.?\s*([\d,]+\.?\d*).*?transfer\s+to\s+(.+?)\s+Ref\s+No\s+(\d+)""",
        RegexOption.IGNORE_CASE
    )

    override fun canHandle(sender: String, body: String): Boolean =
        (sender.uppercase().contains("SBI") || body.contains("State Bank", ignoreCase = true)) &&
                body.contains("debited", ignoreCase = true)

    override fun parse(body: String, receivedAt: LocalDateTime): Transaction? {
        val match = DEBIT_PATTERN.find(body) ?: return null
        val (amountStr, merchant, refNo) = match.destructured
        val amount = amountStr.replace(",", "").toDoubleOrNull() ?: return null
        return Transaction(
            amount = amount,
            merchant = merchant.trim().take(60),
            description = "SBI debit - ${merchant.trim()}",
            dateTime = receivedAt,
            upiRef = refNo.trim(),
            rawSms = body,
            isManual = false,
            bank = "SBI"
        )
    }
}

// ─── Generic Indian Bank Parser (fallback) ────────────────────────────────────
object GenericDebitSmsParser : BankSmsParser {
    private val AMOUNT_PATTERN_1 = Regex(
        """(?:debited|debit)\s+(?:for\s+)?(?:Rs\.?|INR)\s*([\d,]+\.?\d*)""",
        RegexOption.IGNORE_CASE
    )
    private val AMOUNT_PATTERN_2 = Regex(
        """(?:Rs\.?|INR)\s*([\d,]+\.?\d*)\s+(?:has been\s+)?debited""",
        RegexOption.IGNORE_CASE
    )
    private val UPI_REF_PATTERN = Regex("""UPI[:\-/\s](\d{10,})""", RegexOption.IGNORE_CASE)
    private val MERCHANT_PATTERN = Regex(
        """(?:to|at|towards|for)\s+([A-Z][A-Za-z0-9\s&'.,-]{2,40})""",
        RegexOption.IGNORE_CASE
    )

    private val BANK_SENDER_REGEX = Regex(
        """^(AX|VM|VK|AD|TM|BP|BZ|CP|MD|JD)-[A-Z]{4,8}$""",
        RegexOption.IGNORE_CASE
    )

    override fun canHandle(sender: String, body: String): Boolean {
        val isLikelyBank = BANK_SENDER_REGEX.matches(sender) ||
                listOf("BANK", "PAY", "AXIS", "KOTAK", "PNB", "BOI", "YES", "CANARA").any {
                    sender.uppercase().contains(it)
                }
        val hasDebit = body.contains("debit", ignoreCase = true)
        return isLikelyBank && hasDebit
    }

    override fun parse(body: String, receivedAt: LocalDateTime): Transaction? {
        val amountMatch = AMOUNT_PATTERN_1.find(body) ?: AMOUNT_PATTERN_2.find(body) ?: return null
        val amount = amountMatch.groupValues[1].replace(",", "").toDoubleOrNull() ?: return null
        if (amount <= 0.0) return null

        val merchant = MERCHANT_PATTERN.find(body)?.groupValues?.getOrNull(1)?.trim() ?: "Bank Debit"
        val upiRef = UPI_REF_PATTERN.find(body)?.groupValues?.getOrNull(1)
        return Transaction(
            amount = amount,
            merchant = merchant.take(60),
            description = "Debit - $merchant",
            dateTime = receivedAt,
            upiRef = upiRef,
            rawSms = body,
            isManual = false,
            bank = "Other"
        )
    }
}
