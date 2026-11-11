package com.spendwise.domain.model

enum class ExpenseCategory(
    val displayName: String,
    val colorHex: Long,
    val isCreditCategory: Boolean = false
) {
    FOOD("Food & Dining", 0xFFE65100),
    GROCERIES("Groceries", 0xFF2E7D32),
    TRANSPORT("Transport & Fuel", 0xFF1565C0),
    SHOPPING("Shopping", 0xFF6A1B9A),
    BILLS("Bills & Utilities", 0xFF00838F),
    ENTERTAINMENT("Entertainment", 0xFFC2185B),
    HEALTH("Health & Medical", 0xFFD32F2F),
    TRANSFER("UPI & Transfers", 0xFF455A64),
    INCOME("Income & Refunds", 0xFF1B5E20, isCreditCategory = true),
    OTHER("Other", 0xFF5D4037);

    companion object {
        fun fromStorageKey(raw: String?): ExpenseCategory =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: OTHER
    }
}
