package com.spendwise.domain.parser

import com.spendwise.domain.model.Transaction
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Parses Indian bank debit & credit SMS messages into Transaction objects.
 *
 * Supports ICICI, HDFC, SBI, Axis, Kotak, and universal DLT Indian bank SMS headers
 * (including -S / -T suffixes like AX-ICICIT-S, VM-HDFCBK-S, AD-SBIINB-S).
 */
object SmsParser {

    private val parsers: List<BankSmsParser> = listOf(
        IciciSmsParser,
        HdfcSmsParser,
        SbiSmsParser,
        AxisSmsParser,
        KotakSmsParser,
        GenericDebitSmsParser,
        CreditSmsParser
    )

    fun parse(sender: String, body: String, receivedAt: LocalDateTime = LocalDateTime.now()): Transaction? {
        if (isOtpOrIrrelevant(body)) return null

        for (parser in parsers) {
            if (parser.canHandle(sender, body)) {
                val tx = parser.parse(body, receivedAt)
                if (tx != null && tx.amount > 0.0) return tx
            }
        }
        return null
    }

    private fun isOtpOrIrrelevant(body: String): Boolean {
        val lower = body.lowercase(Locale.ROOT)
        val hasSpend = ParserUtils.hasSpendIndicator(lower)
        val hasCredit = ParserUtils.hasCreditIndicator(lower)

        return when {
            // OTP & verification codes
            lower.contains("otp") -> true
            lower.contains("one time password") -> true
            lower.contains("verification code") -> true
            Regex("""\b\d{4,8}\b.{0,30}(?:valid|expire|minutes?|mins?)""").containsMatchIn(lower) -> true

            // Failed / declined / reversed alerts
            lower.contains("transaction failed") || lower.contains("payment failed") ||
                lower.contains("has failed") || lower.contains("declined") ||
                lower.contains("unsuccessful") || lower.contains("reversed") -> true

            // Promotional / spam / future conditional offers
            lower.contains("pre-approved") || lower.contains("loan offer") ||
                lower.contains("apply now") || lower.contains("emi offer") ||
                lower.contains("will be credited") || lower.contains("to be credited") -> true

            // Balance alerts / mini statements without spend or credit
            (lower.contains("balance is") || lower.contains("mini statement")) &&
                !hasSpend && !hasCredit -> true

            else -> false
        }
    }
}

interface BankSmsParser {
    fun canHandle(sender: String, body: String): Boolean
    fun parse(body: String, receivedAt: LocalDateTime): Transaction?
}

internal object ParserUtils {
    val GENERIC_REF_REGEX = Regex("""(?:UPI|Ref(?:\s*No)?|RRN|UTR)[:\-/\s]+(\d{8,16})""", RegexOption.IGNORE_CASE)

    // Matches DLT headers with or without -S / -T / -P suffix (e.g. AX-ICICIT-S, VM-HDFCBK-S, AD-SBIINB)
    private val DLT_SENDER_REGEX = Regex(
        """^[A-Z]{2}-[A-Z0-9]{4,8}(?:-[STPG])?$""",
        RegexOption.IGNORE_CASE
    )

    private val KNOWN_BANK_TOKENS = listOf(
        "BANK", "BNK", "ICICI", "HDFC", "SBI", "AXIS", "KOTAK", "IDFC", "INDUS",
        "PNB", "BOI", "BOB", "YES", "CANARA", "FED", "UNION", "RBL", "CITI",
        "HSBC", "SCB", "AU", "PAYTM", "AIRTEL", "JIO", "PAY"
    )

    private val DEBIT_KEYWORDS = listOf(
        "debited", "spent", "sent rs", "sent inr", "paid rs", "paid inr", "withdrawn", "deducted"
    )
    private val DEBIT_PHRASE_REGEX = Regex("""\bdebit\s+(?:of|for|rs|inr)\b""", RegexOption.IGNORE_CASE)

    private val CREDIT_KEYWORDS = listOf(
        "credited to your", "credited into your", "credited to a/c", "credited to acct",
        "credited to ac", "credited with", "credited by", "is credited", "has been credited",
        "-credited", "received rs", "received inr", "received ₹", "deposited in", "deposited to"
    )
    private val CREDIT_ACCT_FIRST_REGEX = Regex(
        """(?:acct|a/c|account)\s+[A-Za-z0-9*X]+\s+(?:is\s+|has\s+been\s+)?credited""",
        RegexOption.IGNORE_CASE
    )

    private val DATE_FORMATTERS = listOf(
        DateTimeFormatter.ofPattern("dd-MMM-yy", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("d-MMM-yy", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("dd/MM/yy", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.ENGLISH)
    )

    fun hasSpendIndicator(lowerBody: String): Boolean =
        DEBIT_KEYWORDS.any { lowerBody.contains(it) } || DEBIT_PHRASE_REGEX.containsMatchIn(lowerBody)

    fun hasCreditIndicator(lowerBody: String): Boolean =
        CREDIT_KEYWORDS.any { lowerBody.contains(it) } || CREDIT_ACCT_FIRST_REGEX.containsMatchIn(lowerBody)

    fun isLikelyBankSender(sender: String, body: String): Boolean {
        val upperSender = sender.uppercase(Locale.ROOT)
        val upperBody = body.uppercase(Locale.ROOT)
        return DLT_SENDER_REGEX.matches(sender) ||
            KNOWN_BANK_TOKENS.any { upperSender.contains(it) } ||
            listOf("ICICI", "HDFC", "SBI", "STATE BANK", "AXIS BANK", "KOTAK").any { upperBody.contains(it) }
    }

    fun detectBank(body: String): String {
        val upper = body.uppercase(Locale.ROOT)
        return when {
            upper.contains("ICICI") -> "ICICI"
            upper.contains("HDFC") -> "HDFC"
            upper.contains("SBI") || upper.contains("STATE BANK") -> "SBI"
            upper.contains("AXIS") -> "Axis"
            upper.contains("KOTAK") -> "Kotak"
            else -> "Bank"
        }
    }

    fun parseDate(raw: String, fallback: LocalDate): LocalDate =
        DATE_FORMATTERS.firstNotNullOfOrNull { fmt ->
            runCatching { LocalDate.parse(raw.trim(), fmt) }.getOrNull()
        } ?: fallback

    fun extractAmount(regex: Regex, body: String): Double? {
        val match = regex.find(body) ?: return null
        return match.groupValues.drop(1).firstOrNull { it.isNotBlank() }
            ?.replace(",", "")
            ?.toDoubleOrNull()
    }

    fun cleanMerchant(raw: String?, fallback: String = "Bank Debit"): String {
        val cleaned = raw
            ?.replace(Regex("""\s+"""), " ")
            ?.trim()
            ?.trimEnd('.', ',', ';', '-', '/')
            .orEmpty()
        return if (cleaned.isBlank()) fallback else cleaned.take(60)
    }
}

// ─── ICICI Bank Parser ────────────────────────────────────────────────────────
object IciciSmsParser : BankSmsParser {
    private val DEBIT_PATTERN_PRIMARY = Regex(
        """ICICI Bank Acct\s+[A-Za-z0-9*]+\s+debited\s+for\s+(?:Rs\.?|INR|₹)\s*([\d,]+\.?\d*)\s+on\s+([\d\w-]+);\s*(.+?)\s+credited\.?\s*UPI:(\d+)""",
        RegexOption.IGNORE_CASE
    )
    private val DEBIT_PATTERN_SECONDARY = Regex(
        """debited\s+(?:for|by|with)\s+(?:Rs\.?|INR|₹)\s*([\d,]+\.?\d*)\s+on\s+([\d\w-]+);?\s*(.+?)\s*(?:credited|\.)?\s*UPI:(\d+)""",
        RegexOption.IGNORE_CASE
    )
    private val CARD_SPENT_PATTERN = Regex(
        """(?:INR|Rs\.?|₹)\s*([\d,]+\.?\d*)\s+spent\s+on\s+ICICI\s+Bank\s+(?:Credit\s+|Debit\s+)?Card\s+[A-Za-z0-9*]+\s+on\s+([\d\w-]+)\s+(?:at|on)\s+(.+?)(?:\.|Avl\s+Lmt|$)""",
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
        val s = sender.uppercase(Locale.ROOT)
        val isIcici = s.contains("ICICI") || body.contains("ICICI Bank", ignoreCase = true)
        val isSpend = body.contains("debited", ignoreCase = true) || body.contains("spent", ignoreCase = true)
        return isIcici && isSpend
    }

    override fun parse(body: String, receivedAt: LocalDateTime): Transaction? {
        val match = DEBIT_PATTERN_PRIMARY.find(body) ?: DEBIT_PATTERN_SECONDARY.find(body)
        if (match != null) {
            val (amountStr, dateStr, merchant, upiRef) = match.destructured
            val amount = amountStr.replace(",", "").toDoubleOrNull() ?: return null
            val parsedDate = parseDate(dateStr, receivedAt.toLocalDate())
            val cleanedMerchant = ParserUtils.cleanMerchant(merchant)
            return Transaction(
                amount = amount,
                merchant = cleanedMerchant,
                description = "UPI debit - $cleanedMerchant",
                dateTime = parsedDate.atTime(receivedAt.toLocalTime()),
                upiRef = upiRef.trim(),
                rawSms = body,
                isManual = false,
                bank = "ICICI"
            )
        }

        val cardMatch = CARD_SPENT_PATTERN.find(body) ?: return null
        val (amountStr, dateStr, merchant) = cardMatch.destructured
        val amount = amountStr.replace(",", "").toDoubleOrNull() ?: return null
        val parsedDate = parseDate(dateStr, receivedAt.toLocalDate())
        val cleanedMerchant = ParserUtils.cleanMerchant(merchant)
        return Transaction(
            amount = amount,
            merchant = cleanedMerchant,
            description = "ICICI Card - $cleanedMerchant",
            dateTime = parsedDate.atTime(receivedAt.toLocalTime()),
            upiRef = null,
            rawSms = body,
            isManual = false,
            bank = "ICICI"
        )
    }

    private fun parseDate(raw: String, fallback: LocalDate): LocalDate =
        dateFormatters.firstNotNullOfOrNull { fmt ->
            runCatching { LocalDate.parse(raw.trim(), fmt) }.getOrNull()
        } ?: fallback
}

// ─── HDFC Bank Parser ─────────────────────────────────────────────────────────
object HdfcSmsParser : BankSmsParser {
    // Pattern 1: "Sent Rs.200.00 From HDFC Bank A/C *1234 To MERCHANT On 11/11/26 Ref 123456789012"
    private val SENT_PATTERN = Regex(
        """Sent\s+(?:Rs\.?|INR|₹)\s*([\d,]+\.?\d*)\s+From\s+HDFC[\s\S]+?\s+To\s+([^\n]+?)\s+On\s+""",
        RegexOption.IGNORE_CASE
    )
    // Pattern 2: "Rs.500.00 debited from HDFC Bank A/C *1234 on 11-11-26. Info: UPI-MERCHANT. Avl Bal:..."
    private val DEBIT_INFO_PATTERN = Regex(
        """(?:Rs\.?|INR|₹)\s*([\d,]+\.?\d*)\s+debited\s+from\s+HDFC[\s\S]+?Info:\s*([^\n]+?)(?:\.\s|\n|$)""",
        RegexOption.IGNORE_CASE
    )
    // Pattern 3: "Spent Rs.500.00 On HDFC Bank Card ... at MERCHANT on ..."
    private val SPENT_CARD_PATTERN = Regex(
        """Spent\s+(?:Rs\.?|INR|₹)\s*([\d,]+\.?\d*)\s+On\s+HDFC.+?\s+at\s+(.+?)\s+on\s+""",
        RegexOption.IGNORE_CASE
    )

    override fun canHandle(sender: String, body: String): Boolean {
        val s = sender.uppercase(Locale.ROOT)
        val isHdfc = s.contains("HDFC") || body.contains("HDFC", ignoreCase = true)
        val isSpend = body.contains("debited", ignoreCase = true) ||
            body.contains("sent rs", ignoreCase = true) ||
            body.contains("sent inr", ignoreCase = true) ||
            body.contains("spent", ignoreCase = true)
        return isHdfc && isSpend
    }

    override fun parse(body: String, receivedAt: LocalDateTime): Transaction? {
        val match = SENT_PATTERN.find(body)
            ?: DEBIT_INFO_PATTERN.find(body)
            ?: SPENT_CARD_PATTERN.find(body)
            ?: return null

        val amount = match.groupValues[1].replace(",", "").toDoubleOrNull() ?: return null
        val merchant = ParserUtils.cleanMerchant(match.groupValues[2])
        val upiRef = ParserUtils.GENERIC_REF_REGEX.find(body)?.groupValues?.getOrNull(1)

        return Transaction(
            amount = amount,
            merchant = merchant,
            description = "HDFC debit - $merchant",
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
    // Pattern 1: "Dear UPI user A/C X1234 debited by 45.0 on date 11Nov26 trf to MERCHANT Refno 432109876543"
    private val UPI_PATTERN = Regex(
        """debited\s+by\s+(?:Rs\.?|INR|₹)?\s*([\d,]+\.?\d*)\s+on\s+.*?(?:trf|transfer)\s+to\s+(.+?)\s+Ref\s*No\s*[:\s]*(\d+)""",
        RegexOption.IGNORE_CASE
    )
    // Pattern 2: "Your A/c XX1234 debited by Rs 500.00 on 11Nov26 transfer to MERCHANT Ref No 123456789012"
    private val STANDARD_PATTERN = Regex(
        """debited\s+(?:by|for)\s+(?:Rs\.?|INR|₹)?\s*([\d,]+\.?\d*).*?transfer\s+to\s+(.+?)\s+Ref\s+No\s+(\d+)""",
        RegexOption.IGNORE_CASE
    )

    override fun canHandle(sender: String, body: String): Boolean {
        val s = sender.uppercase(Locale.ROOT)
        val isSbi = s.contains("SBI") || body.contains("State Bank", ignoreCase = true) ||
            body.contains("SBI", ignoreCase = true)
        return isSbi && body.contains("debited", ignoreCase = true)
    }

    override fun parse(body: String, receivedAt: LocalDateTime): Transaction? {
        val match = UPI_PATTERN.find(body) ?: STANDARD_PATTERN.find(body) ?: return null
        val (amountStr, merchantRaw, refNo) = match.destructured
        val amount = amountStr.replace(",", "").toDoubleOrNull() ?: return null
        val merchant = ParserUtils.cleanMerchant(merchantRaw)
        return Transaction(
            amount = amount,
            merchant = merchant,
            description = "SBI debit - $merchant",
            dateTime = receivedAt,
            upiRef = refNo.trim(),
            rawSms = body,
            isManual = false,
            bank = "SBI"
        )
    }
}

// ─── Axis Bank Parser ─────────────────────────────────────────────────────────
object AxisSmsParser : BankSmsParser {
    // "INR 350.00 debited from A/c no. XX1234 on 11-11-26 ... UPI/P2M/123456789012/MERCHANT"
    private val UPI_SLASH_PATTERN = Regex(
        """(?:INR|Rs\.?|₹)\s*([\d,]+\.?\d*)\s+debited\s+from\s+A/c.+?UPI/(?:P2M|P2A)/(\d+)/([^\s.\n]+)""",
        RegexOption.IGNORE_CASE
    )
    private val CARD_PATTERN = Regex(
        """Spent\s+(?:INR|Rs\.?|₹)\s*([\d,]+\.?\d*)\s+on\s+Axis\s+Bank\s+Card.+?\s+at\s+(.+?)\s+on\s+""",
        RegexOption.IGNORE_CASE
    )

    override fun canHandle(sender: String, body: String): Boolean {
        val s = sender.uppercase(Locale.ROOT)
        val isAxis = s.contains("AXIS") || body.contains("Axis Bank", ignoreCase = true)
        val isSpend = body.contains("debited", ignoreCase = true) || body.contains("spent", ignoreCase = true)
        return isAxis && isSpend
    }

    override fun parse(body: String, receivedAt: LocalDateTime): Transaction? {
        val upiMatch = UPI_SLASH_PATTERN.find(body)
        if (upiMatch != null) {
            val (amountStr, ref, merchantRaw) = upiMatch.destructured
            val amount = amountStr.replace(",", "").toDoubleOrNull() ?: return null
            val merchant = ParserUtils.cleanMerchant(merchantRaw)
            return Transaction(
                amount = amount,
                merchant = merchant,
                description = "Axis UPI - $merchant",
                dateTime = receivedAt,
                upiRef = ref.trim(),
                rawSms = body,
                isManual = false,
                bank = "Axis"
            )
        }

        val cardMatch = CARD_PATTERN.find(body) ?: return null
        val (amountStr, merchantRaw) = cardMatch.destructured
        val amount = amountStr.replace(",", "").toDoubleOrNull() ?: return null
        val merchant = ParserUtils.cleanMerchant(merchantRaw)
        return Transaction(
            amount = amount,
            merchant = merchant,
            description = "Axis Card - $merchant",
            dateTime = receivedAt,
            upiRef = null,
            rawSms = body,
            isManual = false,
            bank = "Axis"
        )
    }
}

// ─── Kotak Bank Parser ────────────────────────────────────────────────────────
object KotakSmsParser : BankSmsParser {
    // "Sent Rs.120.00 from Kotak Bank AC X1234 to merchant@upi on 11-11-26.UPI Ref 123456789012"
    private val SENT_PATTERN = Regex(
        """Sent\s+(?:Rs\.?|INR|₹)\s*([\d,]+\.?\d*)\s+from\s+Kotak.+?\s+to\s+(.+?)\s+on\s+""",
        RegexOption.IGNORE_CASE
    )

    override fun canHandle(sender: String, body: String): Boolean {
        val s = sender.uppercase(Locale.ROOT)
        val isKotak = s.contains("KOTAK") || body.contains("Kotak", ignoreCase = true)
        val isSpend = body.contains("sent", ignoreCase = true) || body.contains("debited", ignoreCase = true)
        return isKotak && isSpend
    }

    override fun parse(body: String, receivedAt: LocalDateTime): Transaction? {
        val match = SENT_PATTERN.find(body) ?: return null
        val amount = match.groupValues[1].replace(",", "").toDoubleOrNull() ?: return null
        val merchant = ParserUtils.cleanMerchant(match.groupValues[2])
        val upiRef = ParserUtils.GENERIC_REF_REGEX.find(body)?.groupValues?.getOrNull(1)
        return Transaction(
            amount = amount,
            merchant = merchant,
            description = "Kotak debit - $merchant",
            dateTime = receivedAt,
            upiRef = upiRef,
            rawSms = body,
            isManual = false,
            bank = "Kotak"
        )
    }
}

// ─── Universal Indian Bank Fallback Parser ────────────────────────────────────
object GenericDebitSmsParser : BankSmsParser {
    private val AMOUNT_AFTER_KEYWORD = Regex(
        """(?:debited|debit|spent|sent|paid|deducted|withdrawn)\s+(?:by|for|with|of)?\s*(?:Rs\.?|INR|₹)\s*([\d,]+\.?\d*)""",
        RegexOption.IGNORE_CASE
    )
    private val AMOUNT_BEFORE_KEYWORD = Regex(
        """(?:Rs\.?|INR|₹)\s*([\d,]+\.?\d*)\s+(?:has\s+been\s+|is\s+)?(?:debited|spent|sent|paid|deducted|withdrawn)""",
        RegexOption.IGNORE_CASE
    )
    private val AMOUNT_ANY_CURRENCY = Regex(
        """(?:Rs\.?|INR|₹)\s*([\d,]+\.?\d*)""",
        RegexOption.IGNORE_CASE
    )
    private val MERCHANT_PATTERN = Regex(
        """(?:trf\s+to|transfer\s+to|to|at|towards|info[:\s-]+)\s+([A-Za-z0-9][A-Za-z0-9\s&'._/@-]{1,45}?)(?=\s+(?:on|ref|upi|avl|not|if|call|sms|date)\b|[.;\n]|$)""",
        RegexOption.IGNORE_CASE
    )

    // Matches DLT headers / bank senders with debit keywords
    override fun canHandle(sender: String, body: String): Boolean {
        val lowerBody = body.lowercase(Locale.ROOT)
        return ParserUtils.isLikelyBankSender(sender, body) && ParserUtils.hasSpendIndicator(lowerBody)
    }

    private val BOILERPLATE_MERCHANT_WORDS = setOf(
        "dispute", "block", "report", "stop", "sms", "know", "view", "download", "your", "call", "check", "update"
    )

    override fun parse(body: String, receivedAt: LocalDateTime): Transaction? {
        val amountMatch = AMOUNT_AFTER_KEYWORD.find(body)
            ?: AMOUNT_BEFORE_KEYWORD.find(body)
            ?: AMOUNT_ANY_CURRENCY.find(body)
            ?: return null
        val amount = amountMatch.groupValues[1].replace(",", "").toDoubleOrNull() ?: return null
        if (amount <= 0.0) return null

        val merchantRaw = MERCHANT_PATTERN.findAll(body)
            .map { it.groupValues[1].trim() }
            .firstOrNull { candidate ->
                val firstWord = candidate.lowercase(Locale.ROOT).split(" ").firstOrNull().orEmpty()
                candidate.isNotBlank() &&
                    !candidate.all { it.isDigit() } &&
                    firstWord !in BOILERPLATE_MERCHANT_WORDS
            }
        val merchant = ParserUtils.cleanMerchant(merchantRaw)
        val upiRef = ParserUtils.GENERIC_REF_REGEX.find(body)?.groupValues?.getOrNull(1)

        return Transaction(
            amount = amount,
            merchant = merchant,
            description = "Debit - $merchant",
            dateTime = receivedAt,
            upiRef = upiRef,
            rawSms = body,
            isManual = false,
            bank = ParserUtils.detectBank(body)
        )
    }
}

// ─── Bank Credit / Deposit SMS Parser ─────────────────────────────────────────
object CreditSmsParser : BankSmsParser {
    private val AMOUNT_AFTER_CREDIT = Regex(
        """(?:credited|deposited|received)\s+(?:by|for|with|of)?\s*(?:Rs\.?|INR|₹)\s*([\d,]+\.?\d*)""",
        RegexOption.IGNORE_CASE
    )
    private val AMOUNT_BEFORE_CREDIT = Regex(
        """(?:Rs\.?|INR|₹)\s*([\d,]+\.?\d*)\s+(?:has\s+been\s+|is\s+)?(?:credited|deposited)""",
        RegexOption.IGNORE_CASE
    )
    private val AMOUNT_ANY_CURRENCY = Regex(
        """(?:Rs\.?|INR|₹)\s*([\d,]+\.?\d*)""",
        RegexOption.IGNORE_CASE
    )
    private val UPI_SLASH_PATTERN = Regex(
        """UPI/(?:P2A|P2M)/(\d+)/([^\s.\n]+)""",
        RegexOption.IGNORE_CASE
    )
    private val SENDER_PATTERN = Regex(
        """(?:from\s+VPA|by\s+VPA|transfer\s+from|trf\s+from|from|by|info[:\s-]+)\s+([A-Za-z0-9][A-Za-z0-9\s&'._/@-]{1,45}?)(?=\s+(?:on|ref|upi|avl|not|if|call|sms|date|in)\b|[.;()\n]|$)""",
        RegexOption.IGNORE_CASE
    )
    private val DATE_IN_BODY_PATTERN = Regex("""\bon\s+(\d{1,2}-[A-Za-z]{3}-\d{2,4})\b""")

    private val BOILERPLATE_SENDER_WORDS = setOf(
        "dispute", "block", "report", "stop", "sms", "call", "your", "you",
        "vpa", "upi", "avl", "bal", "balance", "ref", "neft", "imps", "rtgs",
        "rs", "inr", "rupees", "bank", "a/c", "acct"
    )

    override fun canHandle(sender: String, body: String): Boolean {
        val lower = body.lowercase(Locale.ROOT)
        // Never treat a debit alert (e.g. ICICI "... <MERCHANT> credited.") as an incoming credit
        if (ParserUtils.hasSpendIndicator(lower)) return false
        return ParserUtils.isLikelyBankSender(sender, body) && ParserUtils.hasCreditIndicator(lower)
    }

    override fun parse(body: String, receivedAt: LocalDateTime): Transaction? {
        val amountMatch = AMOUNT_AFTER_CREDIT.find(body)
            ?: AMOUNT_BEFORE_CREDIT.find(body)
            ?: AMOUNT_ANY_CURRENCY.find(body)
            ?: return null
        val amount = amountMatch.groupValues[1].replace(",", "").toDoubleOrNull() ?: return null
        if (amount <= 0.0) return null

        val slashMatch = UPI_SLASH_PATTERN.find(body)
        val rawSource = slashMatch?.groupValues?.getOrNull(2)
            ?: SENDER_PATTERN.findAll(body)
                .map { cleanCreditSource(it.groupValues[1]) }
                .firstOrNull { candidate ->
                    val firstWord = candidate.lowercase(Locale.ROOT).split(" ").firstOrNull().orEmpty()
                    candidate.isNotBlank() &&
                        !candidate.all { ch -> ch.isDigit() } &&
                        firstWord !in BOILERPLATE_SENDER_WORDS
                }

        val senderName = ParserUtils.cleanMerchant(rawSource, fallback = "Bank Credit")
        val upiRef = slashMatch?.groupValues?.getOrNull(1)
            ?: ParserUtils.GENERIC_REF_REGEX.find(body)?.groupValues?.getOrNull(1)
        val bank = ParserUtils.detectBank(body)
        val parsedDate = DATE_IN_BODY_PATTERN.find(body)?.groupValues?.getOrNull(1)
            ?.let { ParserUtils.parseDate(it, receivedAt.toLocalDate()) }
            ?: receivedAt.toLocalDate()

        return Transaction(
            amount = amount,
            merchant = senderName,
            description = "$bank credit - $senderName",
            dateTime = parsedDate.atTime(receivedAt.toLocalTime()),
            upiRef = upiRef,
            rawSms = body,
            isManual = false,
            bank = bank,
            isCredit = true,
            includeInBudget = false
        )
    }

    private fun cleanCreditSource(raw: String): String {
        var cleaned = raw.trim()
        if (cleaned.startsWith("UPI-", ignoreCase = true)) {
            cleaned = cleaned.removePrefix("UPI-").removePrefix("upi-")
                .substringBeforeLast('-')
        }
        return cleaned.trim()
    }
}
