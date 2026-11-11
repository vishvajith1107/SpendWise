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
    fun `HDFC Sent UPI SMS with -S DLT header parses correctly`() {
        val body = "Sent Rs.250.00 From HDFC Bank A/C *1234 To ZOMATO\nOn 11/11/26\nRef 631430990089"
        val tx = SmsParser.parse("JX-HDFCBK-S", body, now)
        assertNotNull(tx)
        assertEquals(250.0, tx!!.amount, 0.001)
        assertEquals("ZOMATO", tx.merchant)
        assertEquals("631430990089", tx.upiRef)
        assertEquals("HDFC", tx.bank)
    }

    @Test
    fun `HDFC debit Info SMS parses correctly`() {
        val body = "Rs.500.00 debited from HDFC Bank A/C *1234 on 11-11-26. Info: UPI-BIGBASKET-8765432109876. Avl Bal: Rs.4500.00."
        val tx = SmsParser.parse("VM-HDFCBK-S", body, now)
        assertNotNull(tx)
        assertEquals(500.0, tx!!.amount, 0.001)
        assertEquals("HDFC", tx.bank)
    }

    @Test
    fun `SBI UPI SMS without Rs keyword parses correctly`() {
        val body = "Dear UPI user A/C X1234 debited by 45.0 on date 11Nov26 trf to CHAI POINT Refno 432109876543. If not u? call 1800111109. -SBI"
        val tx = SmsParser.parse("VM-SBIINB-S", body, now)
        assertNotNull(tx)
        assertEquals(45.0, tx!!.amount, 0.001)
        assertEquals("CHAI POINT", tx.merchant)
        assertEquals("432109876543", tx.upiRef)
        assertEquals("SBI", tx.bank)
    }

    @Test
    fun `Axis UPI SMS with -S DLT header parses correctly`() {
        val body = "INR 350.00 debited from A/c no. XX1234 on 11-11-26 at 14:20:00. Info: UPI/P2M/432198765432/SWIGGY. Avl Bal: INR 4200.00-Axis Bank"
        val tx = SmsParser.parse("AD-AXISBK-S", body, now)
        assertNotNull(tx)
        assertEquals(350.0, tx!!.amount, 0.001)
        assertEquals("SWIGGY", tx.merchant)
        assertEquals("432198765432", tx.upiRef)
        assertEquals("Axis", tx.bank)
    }

    @Test
    fun `Kotak Sent UPI SMS parses correctly`() {
        val body = "Sent Rs.120.00 from Kotak Bank AC X1234 to uber@axisbank on 11-11-26.UPI Ref 556677889900."
        val tx = SmsParser.parse("VM-KOTAKB-S", body, now)
        assertNotNull(tx)
        assertEquals(120.0, tx!!.amount, 0.001)
        assertEquals("uber@axisbank", tx.merchant)
        assertEquals("556677889900", tx.upiRef)
        assertEquals("Kotak", tx.bank)
    }

    @Test
    fun `Generic DLT -S bank SMS parses without false boilerplate merchant`() {
        val body = "Rs 750.00 debited from IDFC FIRST Bank A/c XX9988 on 11-Nov-26 towards METRO RAIL. Ref: 778899001122. To dispute call 180010888."
        val tx = SmsParser.parse("AX-IDFCFB-S", body, now)
        assertNotNull(tx)
        assertEquals(750.0, tx!!.amount, 0.001)
        assertEquals("METRO RAIL", tx.merchant)
        assertEquals("778899001122", tx.upiRef)
    }

    @Test
    fun `OTP SMS is filtered out`() {
        val body = "Your OTP for ICICI NetBanking transaction is 749201. Valid for 10 minutes. Do not share with anyone."
        assertNull(SmsParser.parse("AX-ICICIT-S", body, now))
    }

    @Test
    fun `ICICI credit SMS parses as credit transaction`() {
        val body = "ICICI Bank Acct XX530 credited with Rs 5000.00 on 10-Nov-26. UPI:123456789."
        val tx = SmsParser.parse("AX-ICICIT-S", body, now)
        assertNotNull(tx)
        assertTrue(tx!!.isCredit)
        assertFalse(tx.includeInBudget)
        assertEquals(5000.0, tx.amount, 0.001)
        assertEquals("123456789", tx.upiRef)
        assertEquals("ICICI", tx.bank)
    }

    @Test
    fun `HDFC credit by VPA parses sender and ignores available balance`() {
        val body = "Rs.2500.00 credited to HDFC Bank A/C *1234 on 11-Nov-26 by VPA rahul@okicici (Ref No 631430990089). Avl Balance is Rs.15000.00."
        val tx = SmsParser.parse("VM-HDFCBK-S", body, now)
        assertNotNull(tx)
        assertTrue(tx!!.isCredit)
        assertEquals(2500.0, tx.amount, 0.001)
        assertEquals("rahul@okicici", tx.merchant)
        assertEquals("631430990089", tx.upiRef)
        assertEquals("HDFC", tx.bank)
    }

    @Test
    fun `SBI hyphen-credited SMS parses sender and amount`() {
        val body = "Dear SBI User, your A/c X1234-credited by Rs.500.00 on 11Nov26 transfer from ANITA Ref No 432109876543 -SBI"
        val tx = SmsParser.parse("AD-SBIINB-S", body, now)
        assertNotNull(tx)
        assertTrue(tx!!.isCredit)
        assertEquals(500.0, tx.amount, 0.001)
        assertEquals("ANITA", tx.merchant)
        assertEquals("432109876543", tx.upiRef)
        assertEquals("SBI", tx.bank)
    }

    @Test
    fun `Kotak Received credit SMS parses correctly`() {
        val body = "Received Rs.120.00 in your Kotak Bank AC X1234 from friend@okaxis on 11-11-26. UPI Ref: 556677889900."
        val tx = SmsParser.parse("VM-KOTAKB-S", body, now)
        assertNotNull(tx)
        assertTrue(tx!!.isCredit)
        assertEquals(120.0, tx.amount, 0.001)
        assertEquals("friend@okaxis", tx.merchant)
        assertEquals("556677889900", tx.upiRef)
        assertEquals("Kotak", tx.bank)
    }

    @Test
    fun `Axis UPI P2A credit SMS parses sender and ref`() {
        val body = "INR 350.00 credited to A/c no. XX1234 on 11-11-26. Info: UPI/P2A/432198765432/RAHUL. Avl Bal: INR 4550.00-Axis Bank"
        val tx = SmsParser.parse("AD-AXISBK-S", body, now)
        assertNotNull(tx)
        assertTrue(tx!!.isCredit)
        assertEquals(350.0, tx.amount, 0.001)
        assertEquals("RAHUL", tx.merchant)
        assertEquals("432198765432", tx.upiRef)
        assertEquals("Axis", tx.bank)
    }

    @Test
    fun `ICICI debit SMS containing merchant credited is NOT misclassified as credit`() {
        val body = "ICICI Bank Acct XX530 debited for Rs 20.00 on 11-Nov-26; THE ULTIMATE EN credited. UPI:668141780387."
        val tx = SmsParser.parse("AX-ICICIT-S", body, now)
        assertNotNull(tx)
        assertFalse(tx!!.isCredit)
        assertEquals(20.0, tx.amount, 0.001)
        assertEquals("THE ULTIMATE EN", tx.merchant)
    }

    @Test
    fun `Promotional will be credited offer SMS is filtered out`() {
        val body = "Rs.500 cashback will be credited to your HDFC Bank A/C if you apply now!"
        assertNull(SmsParser.parse("VM-HDFCBK-P", body, now))
    }

    @Test
    fun `Balance alert without debit or credit is filtered out`() {
        val body = "Available balance in your HDFC Bank A/c *1234 is Rs.12000.00."
        assertNull(SmsParser.parse("VM-HDFCBK-S", body, now))
    }

    @Test
    fun `Failed transaction SMS is filtered out`() {
        val body = "Your HDFC Bank UPI transaction of Rs. 400.00 has failed due to insufficient balance."
        assertNull(SmsParser.parse("VM-HDFCBK-S", body, now))
    }

    @Test
    fun `Promotional loan SMS is filtered out`() {
        val body = "Congrats! Pre-approved loan offer of Rs 500000 waiting on your Axis Bank A/c. Apply now!"
        assertNull(SmsParser.parse("AD-AXISBK-P", body, now))
    }
}
