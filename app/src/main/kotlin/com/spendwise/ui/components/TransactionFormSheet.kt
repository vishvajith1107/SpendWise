package com.spendwise.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.spendwise.domain.categorizer.CategoryClassifier
import com.spendwise.domain.model.ExpenseCategory
import com.spendwise.domain.model.Transaction
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionFormSheet(
    currency: String,
    initialTransaction: Transaction? = null,
    defaultIncludeCreditsInBudget: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (Transaction) -> Unit,
    onDelete: ((Transaction) -> Unit)? = null
) {
    val isEdit = initialTransaction != null
    val today = remember { LocalDate.now() }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("dd MMM yyyy") }

    var amountText by remember(initialTransaction) {
        mutableStateOf(initialTransaction?.let { formatAmountForInput(it.amount) } ?: "")
    }
    var merchant by remember(initialTransaction) {
        mutableStateOf(initialTransaction?.merchant ?: "")
    }
    var notes by remember(initialTransaction) {
        mutableStateOf(initialTransaction?.description ?: "")
    }
    var isCredit by remember(initialTransaction) {
        mutableStateOf(initialTransaction?.isCredit ?: false)
    }
    var category by remember(initialTransaction) {
        mutableStateOf(initialTransaction?.category ?: ExpenseCategory.OTHER)
    }
    var categoryManuallyPicked by remember(initialTransaction) {
        mutableStateOf(initialTransaction != null)
    }
    var includeInBudget by remember(initialTransaction) {
        mutableStateOf(
            if (initialTransaction != null && initialTransaction.isCredit) {
                initialTransaction.includeInBudget
            } else {
                defaultIncludeCreditsInBudget
            }
        )
    }
    var selectedDate by remember(initialTransaction) {
        mutableStateOf(initialTransaction?.date ?: today)
    }
    var amountError by remember { mutableStateOf(false) }
    var showRawSms by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isEdit) "Edit Transaction" else "Add Manual Entry",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                if (initialTransaction != null && !initialTransaction.isManual) {
                    AssistChip(
                        onClick = { showRawSms = !showRawSms },
                        label = {
                            Text(
                                "${initialTransaction.bank.ifBlank { "Bank" }} SMS",
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Sms,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }
            }

            // Transaction Type Selector (Expense vs Credit)
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = !isCredit,
                    onClick = {
                        isCredit = false
                        if (!categoryManuallyPicked) {
                            category = CategoryClassifier.classify(merchant, notes, isCredit = false)
                        } else if (category == ExpenseCategory.INCOME) {
                            category = CategoryClassifier.classify(merchant, notes, isCredit = false)
                        }
                    },
                    label = { Text("Expense (Debit)") },
                    leadingIcon = if (!isCredit) {
                        { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }
                    } else null
                )
                FilterChip(
                    selected = isCredit,
                    onClick = {
                        isCredit = true
                        if (!categoryManuallyPicked || category == ExpenseCategory.OTHER) {
                            category = ExpenseCategory.INCOME
                        }
                    },
                    label = { Text("Credited (Income)") },
                    leadingIcon = if (isCredit) {
                        { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }
                    } else null
                )
            }

            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it; amountError = false },
                label = { Text("Amount ($currency)") },
                isError = amountError,
                supportingText = if (amountError) ({ Text("Enter a valid amount greater than 0") }) else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Text(currency, modifier = Modifier.padding(start = 6.dp)) }
            )

            OutlinedTextField(
                value = merchant,
                onValueChange = {
                    merchant = it
                    if (!categoryManuallyPicked) {
                        category = CategoryClassifier.classify(it, notes, isCredit = isCredit)
                    }
                },
                label = {
                    Text(
                        if (isCredit) "Sender / Source Name (e.g. Refund, Rahul)"
                        else "Merchant / Name (e.g. Chai, Auto, Swiggy)"
                    )
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = {
                    Icon(
                        if (isEdit) Icons.Default.Edit else Icons.Default.ShoppingCart,
                        contentDescription = null
                    )
                }
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "Category",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ExpenseCategory.entries) { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = {
                                category = cat
                                categoryManuallyPicked = true
                            },
                            label = { Text(cat.displayName) },
                            leadingIcon = {
                                Icon(
                                    cat.icon(),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (category == cat) MaterialTheme.colorScheme.primary else cat.tintColor()
                                )
                            }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Description / Notes (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Description, contentDescription = null) }
            )

            if (isCredit) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Include in Daily Limit",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                if (includeInBudget) "Adds this credit to your available spending limit"
                                else "Recorded for history only without changing limit",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Switch(
                            checked = includeInBudget,
                            onCheckedChange = { includeInBudget = it }
                        )
                    }
                }
            }

            // Date selector
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Date: ${selectedDate.format(dateFormatter)}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { selectedDate = selectedDate.minusDays(1) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "Previous day", modifier = Modifier.size(18.dp))
                        }
                        IconButton(
                            onClick = {
                                if (selectedDate.isBefore(today)) {
                                    selectedDate = selectedDate.plusDays(1)
                                }
                            },
                            enabled = selectedDate.isBefore(today),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Next day", modifier = Modifier.size(18.dp))
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = selectedDate == today,
                        onClick = { selectedDate = today },
                        label = { Text("Today") }
                    )
                    FilterChip(
                        selected = selectedDate == today.minusDays(1),
                        onClick = { selectedDate = today.minusDays(1) },
                        label = { Text("Yesterday") }
                    )
                    if (selectedDate != today && selectedDate != today.minusDays(1)) {
                        FilterChip(
                            selected = true,
                            onClick = { },
                            label = { Text(selectedDate.format(dateFormatter)) }
                        )
                    }
                }
            }

            if (initialTransaction?.rawSms != null) {
                TextButton(
                    onClick = { showRawSms = !showRawSms },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                ) {
                    Icon(
                        if (showRawSms) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(if (showRawSms) "Hide Original SMS" else "View Original SMS")
                }

                if (showRawSms) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = initialTransaction.rawSms,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (isEdit && onDelete != null && initialTransaction != null) {
                    OutlinedButton(
                        onClick = { showDeleteConfirm = true },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.DeleteOutline, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Delete")
                    }
                }

                Button(
                    onClick = {
                        val parsedAmount = amountText.trim().replace(",", ".").toDoubleOrNull()
                        if (parsedAmount == null || parsedAmount <= 0.0) {
                            amountError = true
                            return@Button
                        }

                        val defaultMerchant = if (isCredit) {
                            if (isEdit) "Bank Credit" else "Manual Credit"
                        } else {
                            if (isEdit) "Cash Expense" else "Cash Expense"
                        }
                        val cleanMerchant = merchant.trim().ifBlank { defaultMerchant }.take(60)
                        val cleanNotes = notes.trim().ifBlank {
                            if (isCredit) "Credit: $cleanMerchant" else "Expense: $cleanMerchant"
                        }
                        val now = LocalDateTime.now()
                        val preservedTime = initialTransaction?.dateTime?.toLocalTime()
                            ?: if (selectedDate == today) now.toLocalTime() else LocalTime.NOON
                        val finalDateTime = selectedDate.atTime(preservedTime)

                        val saved = if (initialTransaction != null) {
                            initialTransaction.copy(
                                amount = parsedAmount,
                                merchant = cleanMerchant,
                                description = cleanNotes,
                                dateTime = finalDateTime,
                                date = selectedDate,
                                isCredit = isCredit,
                                includeInBudget = if (isCredit) includeInBudget else false,
                                category = category
                            )
                        } else {
                            Transaction(
                                amount = parsedAmount,
                                merchant = cleanMerchant,
                                description = cleanNotes,
                                dateTime = finalDateTime,
                                date = selectedDate,
                                isManual = true,
                                bank = "Manual",
                                isCredit = isCredit,
                                includeInBudget = if (isCredit) includeInBudget else false,
                                category = category
                            )
                        }
                        onSave(saved)
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(if (isEdit) Icons.Default.Save else Icons.Default.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (isEdit) "Save Changes" else if (isCredit) "Save Credit" else "Save Expense")
                }
            }
        }
    }

    if (showDeleteConfirm && initialTransaction != null && onDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Entry?") },
            text = {
                Text("Remove '${initialTransaction.merchant}' ($currency${"%.2f".format(initialTransaction.amount)}) from your history?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete(initialTransaction)
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

private fun formatAmountForInput(amount: Double): String {
    return if (amount % 1.0 == 0.0) {
        "%.0f".format(Locale.US, amount)
    } else {
        "%.2f".format(Locale.US, amount).trimEnd('0').trimEnd('.')
    }
}
