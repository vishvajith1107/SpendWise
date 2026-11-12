package com.spendwise

import com.spendwise.domain.model.AppCategory
import com.spendwise.domain.model.ExpenseCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppCategoryTest {

    @Test
    fun `builtInDefaults covers every ExpenseCategory in order`() {
        val defaults = AppCategory.builtInDefaults()
        assertEquals(ExpenseCategory.entries.size, defaults.size)
        ExpenseCategory.entries.forEachIndexed { idx, cat ->
            val item = defaults[idx]
            assertEquals(cat.name, item.key)
            assertEquals(cat.displayName, item.displayName)
            assertEquals(cat.colorHex, item.colorHex)
            assertEquals(idx, item.sortOrder)
            assertTrue(item.isBuiltIn)
        }
    }

    @Test
    fun `splitKey distinguishes built-in keys from custom keys`() {
        assertEquals(ExpenseCategory.FOOD to null, AppCategory.splitKey("FOOD"))
        assertEquals(ExpenseCategory.GROCERIES to null, AppCategory.splitKey("groceries"))
        assertEquals(ExpenseCategory.OTHER to null, AppCategory.splitKey(null))
        assertEquals(ExpenseCategory.OTHER to null, AppCategory.splitKey("   "))
        assertEquals(ExpenseCategory.OTHER to "CUSTOM_PET_CARE", AppCategory.splitKey("CUSTOM_PET_CARE"))
    }

    @Test
    fun `resolve prefers custom override map and falls back cleanly`() {
        val renamedFood = AppCategory(
            key = "FOOD",
            displayName = "Meals & Tea",
            colorHex = 0xFFFF5722,
            iconKey = "COFFEE",
            isBuiltIn = true
        )
        val petCare = AppCategory(
            key = "CUSTOM_PET_CARE",
            displayName = "Pet Care",
            colorHex = 0xFF009688,
            iconKey = "PETS",
            isBuiltIn = false
        )
        val byKey = mapOf(
            "FOOD" to renamedFood,
            "CUSTOM_PET_CARE" to petCare
        )

        assertEquals("Meals & Tea", AppCategory.resolve("FOOD", byKey).displayName)
        assertEquals("Pet Care", AppCategory.resolve("CUSTOM_PET_CARE", byKey).displayName)

        val fallback = AppCategory.resolve("UNKNOWN_OLD_KEY", byKey)
        assertEquals("OTHER", fallback.key)
        assertNull(AppCategory.splitKey("FOOD").second)
    }
}
