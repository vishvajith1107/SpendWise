package com.spendwise

import com.spendwise.domain.model.RecurringFrequency
import com.spendwise.domain.security.PinSecurityHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PinSecurityHelperTest {

    @Test
    fun `hashes and verifies 4 digit passcode without storing plaintext`() {
        val hash = PinSecurityHelper.hashPin("4829")
        assertNotEquals("4829", hash)
        assertEquals(64, hash.length)
        assertTrue(PinSecurityHelper.verifyPin("4829", hash))
        assertFalse(PinSecurityHelper.verifyPin("0000", hash))
        assertFalse(PinSecurityHelper.verifyPin("4829", null))
        assertFalse(PinSecurityHelper.verifyPin("4829", ""))
    }

    @Test
    fun `computes recurring next occurrence across month boundaries`() {
        val jan31 = LocalDate.of(2026, 1, 31)
        assertEquals(LocalDate.of(2026, 2, 1), RecurringFrequency.DAILY.nextOccurrence(jan31))
        assertEquals(LocalDate.of(2026, 2, 7), RecurringFrequency.WEEKLY.nextOccurrence(jan31))
        assertEquals(LocalDate.of(2026, 2, 28), RecurringFrequency.MONTHLY.nextOccurrence(jan31))
    }
}
