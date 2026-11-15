package com.spendwise.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Rule
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spendwise.domain.model.AppCategory
import com.spendwise.domain.model.Transaction
import com.spendwise.ui.components.icon
import com.spendwise.ui.components.tintColor
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PendingUpiCategorizationSheet(
    pendingTransactions: List<Transaction>,
    categories: List<AppCategory>,
    categoryUsageCounts: Map<String, Int> = emptyMap(),
    currency: String,
    onDismiss: () -> Unit,
    onCategorize: (Transaction, String) -> Unit,
    onEditDetails: (Transaction) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { false }
    )
    val dateTimeFmt = remember { DateTimeFormatter.ofPattern("dd MMM, hh:mm a") }
    val isDark = isSystemInDarkTheme()
    val creditColor = if (isDark) Color(0xFF81C784) else Color(0xFF2E7D32)

    // Intercept back swipes so scrolling categories horizontally near screen edges never dismisses the sheet
    androidx.activity.compose.BackHandler(enabled = true) {
        // Locked: only close button or done dismisses
    }

    val sortedCategories = remember(categories, categoryUsageCounts) {
        if (categoryUsageCounts.isEmpty()) {
            categories
        } else {
            categories.sortedWith(
                compareByDescending<AppCategory> { categoryUsageCounts[it.key] ?: 0 }
                    .thenBy { it.displayName.lowercase(Locale.ROOT) }
            )
        }
    }

    ModalBottomSheet(
        onDismissRequest = { /* Locked: only explicit close button or done dismisses */ },
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Rule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column {
                        Text(
                            "Categorize UPI Transfers",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            if (pendingTransactions.isNotEmpty()) {
                                "${pendingTransactions.size} transfer${if (pendingTransactions.size > 1) "s" else ""} waiting for category"
                            } else {
                                "All caught up"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            if (pendingTransactions.isEmpty()) {
                // Done state
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 36.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(56.dp)
                    )
                    Text(
                        "All Caught Up!",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "All your automatic UPI transfers are categorized. Your charts and budgets are up to date.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onDismiss) {
                        Text("Done")
                    }
                }
            } else {
                Text(
                    "Tap a category below each transfer to assign it. SpendWise will also remember this category for future transactions from this merchant.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(pendingTransactions, key = { it.id }) { tx ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            ),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // Top row: Merchant & Amount
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            tx.merchant.ifBlank { "UPI Transfer" },
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            tx.dateTime.format(dateTimeFmt),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            "${if (tx.isCredit) "+" else "-"}$currency${"%.2f".format(tx.amount)}",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = if (tx.isCredit) creditColor else MaterialTheme.colorScheme.error,
                                            fontSize = 17.sp
                                        )
                                        if (!tx.upiRef.isNullOrBlank()) {
                                            Text(
                                                "UPI: ${tx.upiRef}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                            )
                                        }
                                    }
                                }

                                // Category selection chips (sorted most used to least used)
                                Text(
                                    "Assign Category:",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.SemiBold
                                )

                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(sortedCategories, key = { it.key }) { cat ->
                                        FilterChip(
                                            selected = false,
                                            onClick = { onCategorize(tx, cat.key) },
                                            label = { Text(cat.displayName) },
                                            leadingIcon = {
                                                Icon(
                                                    cat.icon(),
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp),
                                                    tint = cat.tintColor()
                                                )
                                            }
                                        )
                                    }
                                }

                                // Footer options
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(
                                        onClick = { onEditDetails(tx) },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Edit,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text("More Details / Notes", style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
