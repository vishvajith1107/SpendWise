package com.spendwise

import com.spendwise.domain.parser.SmsParser
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime

class SmsParserTest {

    private val now = LocalDateTime.of(2026, 11, 11, 13, 28, 0)

    @Test
    fun `ICICI SMS 1 - 90 rupees to THE ULTIMATE EN`() {
        val body = "ICICI Bank Acct XX530 debited for Rs 90.00 on 10-Nov-26; THE ULTIMATE EN credited. UPI:631430990089. Call 18002662 for dispute. SMS BLOCK 530 to 9215676766."
        val tx = SmsParser.parse("AX-ICICIT-S", body, now)
        assertNotNull(tx)
        assertEquals(90.0, tx!!.amount, 0.001)
        assertEquals("THE ULTIMATE EN", tx.merchant)
        assertEquals("631430990089", tx.upiRef)
        assertEquals("ICICI", tx.bank)
    }

    @Test
    fun `ICICI SMS 2 - 4 rupees to ECHTO VENTURES`() {
        val body = "ICICI Bank Acct XX530 debited for Rs 4.00 on 11-Nov-26; ECHTO VENTURES  credited. UPI:668168555790. Call 18002662 for dispute. SMS BLOCK 530 to 9215676766."
        val tx = SmsParser.parse("AX-ICICIT-S", body, now)
        assertNotNull(tx)
        assertEquals(4.0, tx!!.amount, 0.001)
        assertEquals("ECHTO VENTURES", tx.merchant)
        assertEquals("668168555790", tx.upiRef)
        assertEquals("ICICI", tx.bank)
    }

    @Test
    fun `ICICI SMS 3 - 20 rupees to THE ULTIMATE EN`() {
        val body = "ICICI Bank Acct XX530 debited for Rs 20.00 on 11-Nov-26; THE ULTIMATE EN credited. UPI:668141780387. Call 18002662 for dispute. SMS BLOCK 530 to 9215676766."
        val tx = SmsParser.parse("AX-ICICIT-S", body, now)
        assertNotNull(tx)
        assertEquals(20.0, tx!!.amount, 0.001)
        assertEquals("THE ULTIMATE EN", tx.merchant)
        assertEquals("668141780387", tx.upiRef)
        assertEquals("ICICI", tx.bank)
    }

    @Test
    fun `OTP SMS is filtered out`() {
        val body = "Your OTP for ICICI NetBanking transaction is 749201. Valid for 10 minutes. Do not share with anyone."
        val tx = SmsParser.parse("AX-ICICIT-S", body, now)
        assertNull(tx)
    }

    @Test
    fun `Credit to own account SMS is filtered out`() {
        val body = "ICICI Bank Acct XX530 credited with Rs 5000.00 on 10-Nov-26. UPI:123456789."
        val tx = SmsParser.parse("AX-ICICIT-S", body, now)
        assertNull(tx)
    }

    @Test
    fun `HDFC debit SMS parses correctly`() {
        val body = "Rs.500.00 debited from HDFC Bank A/C *1234 on 11-11-26. Info: UPI-BIGBASKET-8765432109876. Avl Bal: Rs.4500.00."
        val tx = SmsParser.parse("VM-HDFCBK", body, now)
        assertNotNull(tx)
        assertEquals(500.0, tx!!.amount, 0.001)
        assertEquals("HDFC", tx.bank)
    }

    @Test
    fun `Amount with comma parses correctly`() {
        val body = "ICICI Bank Acct XX530 debited for Rs 1,499.00 on 11-Nov-26; SWIGGY credited. UPI:999888777666. Call 18002662 for dispute."
        val tx = SmsParser.parse("AX-ICICIT-S", body, now)
        assertNotNull(tx)
        assertEquals(1499.0, tx!!.amount, 0.001)
        assertEquals("SWIGGY", tx.merchant)
    }
}
