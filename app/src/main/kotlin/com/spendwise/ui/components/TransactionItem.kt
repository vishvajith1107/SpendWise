package com.spendwise.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spendwise.domain.model.Transaction
import java.time.format.DateTimeFormatter

@Composable
fun TransactionItem(
    transaction: Transaction,
    currency: String,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val timeFormatter = remember { DateTimeFormatter.ofPattern("hh:mm a") }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val isDark = isSystemInDarkTheme()
    val creditColor = if (isDark) Color(0xFF81C784) else Color(0xFF2E7D32)
    val catColor = transaction.category.tintColor()
    val iconTint = if (transaction.isCredit) creditColor else catColor
    val containerColor = iconTint.copy(alpha = if (isDark) 0.22f else 0.13f)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = RoundedCornerShape(10.dp),
                color = containerColor
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = transaction.category.icon(),
                        contentDescription = transaction.category.displayName,
                        tint = iconTint,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = transaction.merchant,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge
                )
                val sourceTag = when {
                    transaction.bank == "Recurring" -> " · Recurring"
                    transaction.isManual -> " · Manual"
                    transaction.bank.isNotBlank() -> " · ${transaction.bank}"
                    else -> ""
                }
                val upiLabel = if (!transaction.upiRef.isNullOrBlank())
                    " · UPI:${transaction.upiRef.takeLast(4)}" else ""
                val creditTag = when {
                    transaction.isCredit && transaction.includeInBudget -> " · Added to limit"
                    transaction.isCredit -> " · Excluded"
                    transaction.excludeFromBudget -> " · History only"
                    else -> ""
                }
                Text(
                    text = "${transaction.category.displayName} · ${transaction.dateTime.format(timeFormatter)}$sourceTag$upiLabel$creditTag",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (transaction.isCredit && transaction.includeInBudget)
                        creditColor
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = if (transaction.isCredit)
                        "+$currency${"%.2f".format(transaction.amount)}"
                    else
                        "-$currency${"%.2f".format(transaction.amount)}",
                    fontWeight = FontWeight.Bold,
                    color = if (transaction.isCredit) creditColor else MaterialTheme.colorScheme.error,
                    fontSize = 15.sp
                )
            }

            IconButton(
                onClick = onClick,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "Edit",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }

            IconButton(
                onClick = { showDeleteDialog = true },
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    Icons.Default.DeleteOutline,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(19.dp)
                )
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Entry?") },
            text = {
                Text("Remove '${transaction.merchant}' ($currency${"%.2f".format(transaction.amount)}) from your spending history?")
            },
            confirmButton = {
                TextButton(onClick = { onDelete(); showDeleteDialog = false }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }
}
