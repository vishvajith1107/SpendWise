package com.spendwise.domain.model

data class AppCategory(
    val key: String,
    val displayName: String,
    val colorHex: Long,
    val iconKey: String,
    val isCreditCategory: Boolean = false,
    val isBuiltIn: Boolean = false,
    val sortOrder: Int = 0
) {
    companion object {
        fun defaultIconKey(cat: ExpenseCategory): String = when (cat) {
            ExpenseCategory.FOOD -> "RESTAURANT"
            ExpenseCategory.GROCERIES -> "BASKET"
            ExpenseCategory.TRANSPORT -> "CAR"
            ExpenseCategory.SHOPPING -> "SHOPPING_BAG"
            ExpenseCategory.BILLS -> "RECEIPT"
            ExpenseCategory.ENTERTAINMENT -> "MOVIE"
            ExpenseCategory.HEALTH -> "HEALTH"
            ExpenseCategory.TRANSFER -> "TRANSFER"
            ExpenseCategory.INCOME -> "WALLET"
            ExpenseCategory.OTHER -> "CATEGORY"
        }

        fun fromBuiltIn(cat: ExpenseCategory, order: Int = cat.ordinal): AppCategory = AppCategory(
            key = cat.name,
            displayName = cat.displayName,
            colorHex = cat.colorHex,
            iconKey = defaultIconKey(cat),
            isCreditCategory = cat.isCreditCategory,
            isBuiltIn = true,
            sortOrder = order
        )

        fun builtInDefaults(): List<AppCategory> =
            ExpenseCategory.entries.mapIndexed { idx, cat -> fromBuiltIn(cat, idx) }

        fun resolve(key: String, byKey: Map<String, AppCategory>): AppCategory =
            byKey[key] ?: fromBuiltIn(ExpenseCategory.fromStorageKey(key))

        fun splitKey(rawKey: String?): Pair<ExpenseCategory, String?> {
            if (rawKey.isNullOrBlank()) return ExpenseCategory.OTHER to null
            val builtIn = ExpenseCategory.entries.firstOrNull { it.name.equals(rawKey, ignoreCase = true) }
            return if (builtIn != null) builtIn to null else ExpenseCategory.OTHER to rawKey
        }
    }
}
