package com.spendwise

import com.spendwise.domain.categorizer.CategoryClassifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryClassifierTest {

    @Test
    fun `normalizeMerchant trims collapses whitespace and uppercases`() {
        assertEquals("CHAI POINT", CategoryClassifier.normalizeMerchant("  chai   point \n"))
    }

    @Test
    fun `isGenericMerchant detects placeholders and preserves real merchants`() {
        assertTrue(CategoryClassifier.isGenericMerchant("  "))
        assertTrue(CategoryClassifier.isGenericMerchant("Cash Expense"))
        assertTrue(CategoryClassifier.isGenericMerchant("UPI PAYMENT"))
        assertFalse(CategoryClassifier.isGenericMerchant("Local Bakery"))
    }

    @Test
    fun `resolveCategoryKey prioritizes learned rule over built-in keywords`() {
        val rules = mapOf(
            "SWIGGY" to "CUSTOM_OFFICE_LUNCH",
            "RAMU TEA STALL" to "FOOD"
        )

        assertEquals(
            "CUSTOM_OFFICE_LUNCH",
            CategoryClassifier.resolveCategoryKey(
                merchant = " swiggy ",
                learnedRules = rules
            )
        )
        assertEquals(
            "FOOD",
            CategoryClassifier.resolveCategoryKey(
                merchant = "Ramu Tea Stall",
                learnedRules = rules
            )
        )
    }

    @Test
    fun `resolveCategoryKey falls back to built-in classifier when no learned rule exists`() {
        assertEquals(
            "FOOD",
            CategoryClassifier.resolveCategoryKey(
                merchant = "Zomato Order",
                learnedRules = emptyMap()
            )
        )
    }
}
