package com.spendwise.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.ShoppingBasket
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.spendwise.domain.model.ExpenseCategory

fun ExpenseCategory.icon(): ImageVector = when (this) {
    ExpenseCategory.FOOD -> Icons.Default.Restaurant
    ExpenseCategory.GROCERIES -> Icons.Default.ShoppingBasket
    ExpenseCategory.TRANSPORT -> Icons.Default.DirectionsCar
    ExpenseCategory.SHOPPING -> Icons.Default.ShoppingBag
    ExpenseCategory.BILLS -> Icons.Default.Receipt
    ExpenseCategory.ENTERTAINMENT -> Icons.Default.Movie
    ExpenseCategory.HEALTH -> Icons.Default.MedicalServices
    ExpenseCategory.TRANSFER -> Icons.Default.SwapHoriz
    ExpenseCategory.INCOME -> Icons.Default.AccountBalanceWallet
    ExpenseCategory.OTHER -> Icons.Default.Category
}

fun ExpenseCategory.tintColor(): Color = Color(colorHex)
