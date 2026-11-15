package com.spendwise.ui.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.spendwise.domain.analytics.AnalyticsPeriod
import com.spendwise.domain.analytics.CategorySpend
import com.spendwise.domain.model.AppCategory
import com.spendwise.domain.model.Transaction
import com.spendwise.ui.components.TransactionItem
import com.spendwise.ui.components.iconByKey
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryTransactionsSheet(
    categorySpend: CategorySpend,
    allTransactions: List<Transaction>,
    categories: List<AppCategory>,
    currency: String,
    initialPeriod: AnalyticsPeriod = AnalyticsPeriod.SEVEN_DAYS,
    onDismiss: () -> Unit,
    onTransactionClick: (Transaction) -> Unit,
    onTransactionDelete: (Transaction) -> Unit
) {
    var selectedPeriod by remember(categorySpend.categoryKey) { mutableStateOf(initialPeriod) }
    val today = remember { LocalDate.now() }
    val yesterday = remember(today) { today.minusDays(1) }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("dd MMM yyyy") }
    val isDark = isSystemInDarkTheme()
    val creditColor = if (isDark) Color(0xFF81C784) else Color(0xFF2E7D32)
    val catColor = Color(categorySpend.colorHex)

    val startDate = remember(selectedPeriod, today) {
        when (selectedPeriod) {
            AnalyticsPeriod.SEVEN_DAYS -> today.minusDays(6)
            AnalyticsPeriod.THIRTY_DAYS -> today.minusDays(29)
            AnalyticsPeriod.THIS_MONTH -> today.withDayOfMonth(1)
            AnalyticsPeriod.ALL_TIME -> LocalDate.MIN
        }
    }

    val filteredTransactions = remember(allTransactions, categorySpend.categoryKey, startDate, today) {
        allTransactions.filter { tx ->
            tx.effectiveCategoryKey == categorySpend.categoryKey &&
                !tx.date.isBefore(startDate) &&
                !tx.date.isAfter(today)
        }.sortedWith(compareByDescending<Transaction> { it.date }.thenByDescending { it.dateTime })
    }

    val totalSpent = remember(filteredTransactions) {
        filteredTransactions.filter { !it.isCredit }.sumOf { it.amount }
    }
    val totalCredited = remember(filteredTransactions) {
        filteredTransactions.filter { it.isCredit }.sumOf { it.amount }
    }
    val count = filteredTransactions.size

    val dateGroups = remember(filteredTransactions) {
        filteredTransactions.groupBy { it.date }.entries.toList()
    }

    val rangeLabel = when (selectedPeriod) {
        AnalyticsPeriod.SEVEN_DAYS -> "${startDate.format(dateFormatter)} – ${today.format(dateFormatter)}"
        AnalyticsPeriod.THIRTY_DAYS -> "${startDate.format(dateFormatter)} – ${today.format(dateFormatter)}"
        AnalyticsPeriod.THIS_MONTH -> "${startDate.format(dateFormatter)} – ${today.format(dateFormatter)}"
        AnalyticsPeriod.ALL_TIME -> if (filteredTransactions.isNotEmpty()) {
            val earliest = filteredTransactions.minOf { it.date }
            "${earliest.format(dateFormatter)} – ${today.format(dateFormatter)}"
        } else {
            "All recorded transactions"
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null
                ) { /* Don't dismiss on outside tap */ },
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.92f)
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) { /* Consume taps inside sheet */ },
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp)
                        .padding(top = 16.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
            // Category Header
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
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(catColor.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            iconByKey(categorySpend.iconKey),
                            contentDescription = null,
                            tint = catColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column {
                        Text(
                            text = categorySpend.displayName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = rangeLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            // Time Period Selection Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AnalyticsPeriod.entries.forEach { period ->
                    FilterChip(
                        selected = selectedPeriod == period,
                        onClick = { selectedPeriod = period },
                        label = { Text(period.label) }
                    )
                }
            }

            // Summary Stats Card for Category
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "Total Spent",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "$currency${"%.2f".format(totalSpent)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    if (totalCredited > 0.0) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "Credited",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "+$currency${"%.2f".format(totalCredited)}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = creditColor
                            )
                        }
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "Activity",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "$count transaction${if (count == 1) "" else "s"}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Transaction List
            if (filteredTransactions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.ReceiptLong,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                            modifier = Modifier.size(44.dp)
                        )
                        Text(
                            "No transactions in this period",
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            "Select another time period above to view older entries.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    dateGroups.forEach { (date, groupTxs) ->
                        val daySpent = groupTxs.filter { !it.isCredit }.sumOf { it.amount }
                        val dayCredited = groupTxs.filter { it.isCredit }.sumOf { it.amount }
                        val dateHeader = when (date) {
                            today -> "Today · ${date.format(dateFormatter)}"
                            yesterday -> "Yesterday · ${date.format(dateFormatter)}"
                            else -> date.format(dateFormatter)
                        }

                        item(key = "hdr_${date.toEpochDay()}") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 6.dp, bottom = 2.dp, start = 4.dp, end = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    dateHeader,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    if (dayCredited > 0.0) {
                                        Text(
                                            "+$currency${"%.2f".format(dayCredited)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = creditColor
                                        )
                                    }
                                    if (daySpent > 0.0 || dayCredited == 0.0) {
                                        Text(
                                            "$currency${"%.2f".format(daySpent)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }

                        items(groupTxs, key = { it.id }) { tx ->
                            TransactionItem(
                                transaction = tx,
                                currency = currency,
                                category = categories.firstOrNull { it.key == tx.effectiveCategoryKey },
                                onClick = { onTransactionClick(tx) },
                                onDelete = { onTransactionDelete(tx) }
                            )
                        }
                    }
                }
            }
        }
    }
}
}
}
