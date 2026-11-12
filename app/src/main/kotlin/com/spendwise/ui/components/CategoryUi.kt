package com.spendwise.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.spendwise.domain.model.AppCategory
import com.spendwise.domain.model.ExpenseCategory

val CATEGORY_ICON_OPTIONS: List<Pair<String, ImageVector>> = listOf(
    "RESTAURANT" to Icons.Default.Restaurant,
    "COFFEE" to Icons.Default.LocalCafe,
    "BASKET" to Icons.Default.ShoppingBasket,
    "CAR" to Icons.Default.DirectionsCar,
    "FLIGHT" to Icons.Default.Flight,
    "SHOPPING_BAG" to Icons.Default.ShoppingBag,
    "RECEIPT" to Icons.Default.Receipt,
    "HOME" to Icons.Default.Home,
    "MOVIE" to Icons.Default.Movie,
    "HEALTH" to Icons.Default.MedicalServices,
    "FITNESS" to Icons.Default.FitnessCenter,
    "EDUCATION" to Icons.Default.School,
    "PETS" to Icons.Default.Pets,
    "GIFT" to Icons.Default.CardGiftcard,
    "TRANSFER" to Icons.Default.SwapHoriz,
    "WALLET" to Icons.Default.AccountBalanceWallet,
    "BANK" to Icons.Default.AccountBalance,
    "WORK" to Icons.Default.Work,
    "TOOLS" to Icons.Default.Build,
    "CATEGORY" to Icons.Default.Category
)

val CATEGORY_COLOR_OPTIONS: List<Long> = listOf(
    0xFFE65100,
    0xFF2E7D32,
    0xFF1565C0,
    0xFF6A1B9A,
    0xFF00838F,
    0xFFC2185B,
    0xFFD32F2F,
    0xFF455A64,
    0xFF1B5E20,
    0xFF5D4037,
    0xFFEF6C00,
    0xFF00897B
)

fun iconByKey(iconKey: String): ImageVector =
    CATEGORY_ICON_OPTIONS.firstOrNull { it.first == iconKey }?.second ?: Icons.Default.Category

fun AppCategory.icon(): ImageVector = iconByKey(iconKey)

fun AppCategory.tintColor(): Color = Color(colorHex)

fun ExpenseCategory.icon(): ImageVector = iconByKey(AppCategory.defaultIconKey(this))

fun ExpenseCategory.tintColor(): Color = Color(colorHex)

@Composable
fun CategoryEditorDialog(
    initialCategory: AppCategory? = null,
    onDismiss: () -> Unit,
    onSave: (name: String, colorHex: Long, iconKey: String) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val isEdit = initialCategory != null
    var name by remember(initialCategory) { mutableStateOf(initialCategory?.displayName ?: "") }
    var colorHex by remember(initialCategory) {
        mutableStateOf(initialCategory?.colorHex ?: CATEGORY_COLOR_OPTIONS.first())
    }
    var iconKey by remember(initialCategory) {
        mutableStateOf(initialCategory?.iconKey ?: CATEGORY_ICON_OPTIONS.first().first)
    }
    var showError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (isEdit) "Edit Category" else "New Category",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        showError = false
                    },
                    label = { Text("Category Name") },
                    placeholder = { Text("e.g. Fuel, Hostel, Pets") },
                    isError = showError,
                    supportingText = if (showError) ({ Text("Please enter a category name") }) else null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Color", style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(CATEGORY_COLOR_OPTIONS) { hex ->
                        val selected = hex == colorHex
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(hex))
                                .then(
                                    if (selected) {
                                        Modifier.border(2.5.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                    } else {
                                        Modifier
                                    }
                                )
                                .clickable { colorHex = hex },
                            contentAlignment = Alignment.Center
                        ) {
                            if (selected) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                Text("Icon", style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(CATEGORY_ICON_OPTIONS) { (key, vec) ->
                        val selected = key == iconKey
                        val activeColor = Color(colorHex)
                        Surface(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { iconKey = key },
                            shape = RoundedCornerShape(10.dp),
                            color = if (selected) activeColor.copy(alpha = 0.2f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = if (selected) {
                                androidx.compose.foundation.BorderStroke(1.5.dp, activeColor)
                            } else null
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    vec,
                                    contentDescription = key,
                                    tint = if (selected) activeColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = name.trim()
                    if (trimmed.isBlank()) {
                        showError = true
                    } else {
                        onSave(trimmed, colorHex, iconKey)
                    }
                }
            ) {
                Text(if (isEdit) "Save" else "Add")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (initialCategory != null && !initialCategory.isBuiltIn && onDelete != null) {
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Delete")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}
