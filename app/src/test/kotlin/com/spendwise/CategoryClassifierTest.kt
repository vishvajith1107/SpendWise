package com.spendwise

import com.spendwise.domain.categorizer.CategoryClassifier
import com.spendwise.domain.model.ExpenseCategory
import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryClassifierTest {

    @Test
    fun `classifies Indian food delivery and coffee merchants`() {
        assertEquals(ExpenseCategory.FOOD, CategoryClassifier.classify("SWIGGY ORDER"))
        assertEquals(ExpenseCategory.FOOD, CategoryClassifier.classify("Third Wave Coffee"))
        assertEquals(ExpenseCategory.FOOD, CategoryClassifier.classify("Zomato Limited"))
    }

    @Test
    fun `classifies quick commerce and groceries`() {
        assertEquals(ExpenseCategory.GROCERIES, CategoryClassifier.classify("BLINKIT"))
        assertEquals(ExpenseCategory.GROCERIES, CategoryClassifier.classify("Zepto Now"))
        assertEquals(ExpenseCategory.GROCERIES, CategoryClassifier.classify("DMart Ready"))
    }

    @Test
    fun `word boundaries avoid false positives inside unrelated words`() {
        // "current" should not match "rent"; "steam" vs normal words; "vi" should not match "ravinder"
        assertEquals(ExpenseCategory.OTHER, CategoryClassifier.classify("Ravinder Enterprises"))
        assertEquals(ExpenseCategory.TRANSFER, CategoryClassifier.classify("ravinder@okicici"))
        assertEquals(ExpenseCategory.BILLS, CategoryClassifier.classify("Vi Prepaid Recharge"))
        assertEquals(ExpenseCategory.BILLS, CategoryClassifier.classify("House Rent Transfer"))
    }

    @Test
    fun `credits always map to INCOME`() {
        assertEquals(
            ExpenseCategory.INCOME,
            CategoryClassifier.classify("Swiggy Refund", isCredit = true)
        )
    }
}
